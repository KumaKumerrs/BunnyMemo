package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.audio.BunnyFeedbackManager
import com.example.data.PhotoAttachmentStore
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.MutedNavy
import com.example.ui.theme.OverdueTerracotta
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SoftIceSurface
import com.example.ui.theme.SubtleBorder
import kotlinx.coroutines.launch
import java.io.File

/**
 * Zero-permission Android Photo Picker attachment section for Task, Plan, and Event forms.
 */
@Composable
fun PhotoAttachmentPickerSection(
    imagePaths: List<String>,
    onAddPaths: (List<String>) -> Unit,
    onRemovePath: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val feedback = remember(context) { BunnyFeedbackManager.getInstance(context) }
    var isImporting by remember { mutableStateOf(false) }
    val remainingSlots = (PhotoAttachmentStore.MAX_PHOTOS_PER_ITEM - imagePaths.size).coerceAtLeast(0)

    val multiPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(PhotoAttachmentStore.MAX_PHOTOS_PER_ITEM)
    ) { uris ->
        if (uris.isNotEmpty()) {
            isImporting = true
            scope.launch {
                val imported = PhotoAttachmentStore.importPickedUris(
                    context = context,
                    uris = uris,
                    maxAllowed = remainingSlots
                )
                isImporting = false
                if (imported.isNotEmpty()) {
                    feedback.onPopOpen()
                    onAddPaths(imported)
                }
            }
        }
    }

    val singlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isImporting = true
            scope.launch {
                val imported = PhotoAttachmentStore.importPickedUris(
                    context = context,
                    uris = listOf(uri),
                    maxAllowed = remainingSlots
                )
                isImporting = false
                if (imported.isNotEmpty()) {
                    feedback.onPopOpen()
                    onAddPaths(imported)
                }
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("photo_attachment_picker_section"),
        shape = RoundedCornerShape(18.dp),
        color = SoftIceSurface,
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = null,
                        tint = DeepBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.photos_section_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MidnightNavy,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${imagePaths.size}/${PhotoAttachmentStore.MAX_PHOTOS_PER_ITEM} attached",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedNavy
                        )
                    }
                }

                if (remainingSlots > 0) {
                    OutlinedButton(
                        onClick = {
                            feedback.onLightTick()
                            val req = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            if (remainingSlots > 1) {
                                multiPickerLauncher.launch(req)
                            } else {
                                singlePickerLauncher.launch(req)
                            }
                        },
                        enabled = !isImporting,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, DeepBlue.copy(alpha = 0.4f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("add_photo_button")
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(15.dp),
                                strokeWidth = 2.dp,
                                color = DeepBlue
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.photos_importing))
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.AddPhotoAlternate,
                                contentDescription = null,
                                tint = DeepBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.photos_add_button),
                                color = DeepBlue,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            if (imagePaths.isEmpty()) {
                Text(
                    text = stringResource(R.string.photos_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedNavy
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(imagePaths, key = { it }) { path ->
                        Box(
                            modifier = Modifier
                                .size(82.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, SubtleBorder, RoundedCornerShape(14.dp))
                                .background(PureWhite)
                        ) {
                            AsyncImage(
                                model = File(path),
                                contentDescription = stringResource(R.string.photos_attached_desc),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                onClick = {
                                    feedback.onLightTick()
                                    onRemovePath(path)
                                },
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.62f),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.photos_remove_desc),
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full gallery card for ItemDetailScreen with instant "+ Add picture" and tap-to-zoom lightbox.
 */
@Composable
fun PhotoThumbnailGalleryCard(
    imagePaths: List<String>,
    onUpdatePhotos: (List<String>) -> Unit,
    onPhotoClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val feedback = remember(context) { BunnyFeedbackManager.getInstance(context) }
    var isImporting by remember { mutableStateOf(false) }
    val remainingSlots = (PhotoAttachmentStore.MAX_PHOTOS_PER_ITEM - imagePaths.size).coerceAtLeast(0)

    val multiPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(PhotoAttachmentStore.MAX_PHOTOS_PER_ITEM)
    ) { uris ->
        if (uris.isNotEmpty()) {
            isImporting = true
            scope.launch {
                val imported = PhotoAttachmentStore.importPickedUris(
                    context = context,
                    uris = uris,
                    maxAllowed = remainingSlots
                )
                isImporting = false
                if (imported.isNotEmpty()) {
                    feedback.onPopOpen()
                    onUpdatePhotos((imagePaths + imported).distinct().take(PhotoAttachmentStore.MAX_PHOTOS_PER_ITEM))
                }
            }
        }
    }

    val singlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isImporting = true
            scope.launch {
                val imported = PhotoAttachmentStore.importPickedUris(
                    context = context,
                    uris = listOf(uri),
                    maxAllowed = remainingSlots
                )
                isImporting = false
                if (imported.isNotEmpty()) {
                    feedback.onPopOpen()
                    onUpdatePhotos((imagePaths + imported).distinct().take(PhotoAttachmentStore.MAX_PHOTOS_PER_ITEM))
                }
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("detail_photos_card"),
        shape = RoundedCornerShape(24.dp),
        color = PureWhite,
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = null,
                        tint = DeepBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.photos_section_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MidnightNavy
                    )
                }

                if (remainingSlots > 0) {
                    OutlinedButton(
                        onClick = {
                            feedback.onLightTick()
                            val req = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            if (remainingSlots > 1) {
                                multiPickerLauncher.launch(req)
                            } else {
                                singlePickerLauncher.launch(req)
                            }
                        },
                        enabled = !isImporting,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, DeepBlue.copy(alpha = 0.4f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("detail_add_photo_button")
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = DeepBlue
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.AddPhotoAlternate,
                                contentDescription = null,
                                tint = DeepBlue,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.photos_add_button),
                            color = DeepBlue,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            if (imagePaths.isEmpty()) {
                Text(
                    text = stringResource(R.string.photos_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedNavy
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(imagePaths, key = { it }) { path ->
                        Box(
                            modifier = Modifier
                                .size(104.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, SubtleBorder, RoundedCornerShape(16.dp))
                                .background(SoftIceSurface)
                                .clickable(role = Role.Image) {
                                    feedback.onLightTick()
                                    onPhotoClick(path)
                                }
                        ) {
                            AsyncImage(
                                model = File(path),
                                contentDescription = stringResource(R.string.photos_attached_desc),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact horizontal row of rounded photo thumbnails shown on Task / Event / Plan cards.
 */
@Composable
fun MiniPhotoBadgeRow(
    imagePaths: List<String>,
    modifier: Modifier = Modifier
) {
    if (imagePaths.isEmpty()) return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        imagePaths.take(3).forEach { path ->
            AsyncImage(
                model = File(path),
                contentDescription = stringResource(R.string.photos_attached_desc),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .border(1.dp, SubtleBorder, RoundedCornerShape(9.dp))
                    .background(SoftIceSurface)
            )
        }
        if (imagePaths.size > 3) {
            Surface(
                shape = RoundedCornerShape(9.dp),
                color = SkyBlue.copy(alpha = 0.65f)
            ) {
                Text(
                    text = "+${imagePaths.size - 3}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MidnightNavy,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Full-screen lightbox dialog to inspect or remove an attached picture.
 */
@Composable
fun PhotoLightboxModal(
    imagePath: String,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(onClick = onDismiss)
                .padding(20.dp)
        ) {
            AsyncImage(
                model = File(imagePath),
                contentDescription = stringResource(R.string.photos_attached_desc),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .aspectRatio(0.85f, matchHeightConstraintsFirst = false)
                    .clip(RoundedCornerShape(18.dp))
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(OverdueTerracotta.copy(alpha = 0.85f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.photos_remove_desc),
                        tint = Color.White
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.close_button),
                        tint = Color.White
                    )
                }
            }
        }
    }
}
