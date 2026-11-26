package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Local storage manager for pictures attached to Tasks, Plans, and Events.
 *
 * Uses the zero-permission Android Photo Picker (`PickMultipleVisualMedia`) and copies
 * EXIF-corrected, downscaled JPEG images into app-private storage (`filesDir/planner_photos/`)
 * so photos remain permanently viewable without requesting storage permissions.
 */
object PhotoAttachmentStore {
    const val MAX_PHOTOS_PER_ITEM = 4
    private const val TAG = "PhotoAttachmentStore"
    private const val MAX_DIMENSION_PX = 1440
    private const val JPEG_QUALITY = 85
    private const val DIR_NAME = "planner_photos"
    private const val DELIMITER = "|"

    fun parsePaths(csv: String?): List<String> {
        if (csv.isNullOrBlank()) return emptyList()
        return csv.split(DELIMITER)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_PHOTOS_PER_ITEM)
    }

    fun encodePaths(paths: List<String>): String? {
        val cleaned = paths
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_PHOTOS_PER_ITEM)
        return if (cleaned.isEmpty()) null else cleaned.joinToString(DELIMITER)
    }

    private fun photoDir(context: Context): File {
        val dir = File(context.applicationContext.filesDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun importPickedUris(
        context: Context,
        uris: List<Uri>,
        maxAllowed: Int = MAX_PHOTOS_PER_ITEM
    ): List<String> = withContext(Dispatchers.IO) {
        if (uris.isEmpty() || maxAllowed <= 0) return@withContext emptyList()
        val appContext = context.applicationContext
        val dir = photoDir(appContext)
        val savedPaths = mutableListOf<String>()

        for (uri in uris.take(maxAllowed)) {
            val saved = saveSingleUri(appContext, uri, dir)
            if (saved != null) {
                savedPaths.add(saved)
            }
        }
        savedPaths
    }

    private fun readExifOrientation(context: Context, uri: Uri): Int {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }

    private fun saveSingleUri(context: Context, uri: Uri, dir: File): String? {
        return try {
            val resolver = context.contentResolver
            val orientation = readExifOrientation(context, uri)
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, boundsOpts)
            }

            val srcW = boundsOpts.outWidth
            val srcH = boundsOpts.outHeight
            if (srcW <= 0 || srcH <= 0) return null

            var sampleSize = 1
            val longest = max(srcW, srcH)
            while (longest / (sampleSize * 2) >= MAX_DIMENSION_PX) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOpts)
            } ?: return null

            val oriented = applyExifOrientation(decoded, orientation)
            if (oriented !== decoded) {
                decoded.recycle()
            }
            val finalBitmap = scaleDownIfNeeded(oriented, MAX_DIMENSION_PX)
            val fileName = "photo_${UUID.randomUUID().toString().replace("-", "").take(16)}.jpg"
            val targetFile = File(dir, fileName)
            FileOutputStream(targetFile).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            if (finalBitmap !== oriented) {
                finalBitmap.recycle()
            }
            oriented.recycle()
            targetFile.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "Failed importing picked photo: ${e.message}")
            null
        }
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val longest = max(w, h)
        if (longest <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / longest.toFloat()
        val targetW = (w * ratio).roundToInt().coerceAtLeast(1)
        val targetH = (h * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    fun deletePhotoFiles(paths: List<String>) {
        paths.forEach { path ->
            runCatching {
                val f = File(path)
                if (f.exists() && f.parentFile?.name == DIR_NAME) {
                    f.delete()
                }
            }
        }
    }

    /**
     * Garbage-collects orphaned photo files left behind by cancelled editor dialogs or crashes.
     * Skips files younger than [gracePeriodMs] to avoid racing with an open editor dialog.
     */
    suspend fun cleanupOrphanedPhotos(
        context: Context,
        referencedPaths: Set<String>,
        gracePeriodMs: Long = 10 * 60_000L
    ) = withContext(Dispatchers.IO) {
        val dir = File(context.applicationContext.filesDir, DIR_NAME)
        if (!dir.exists() || !dir.isDirectory) return@withContext
        val cutoff = System.currentTimeMillis() - gracePeriodMs
        dir.listFiles()?.forEach { file ->
            if (file.isFile &&
                file.absolutePath !in referencedPaths &&
                file.lastModified() < cutoff
            ) {
                runCatching { file.delete() }
            }
        }
    }
}
