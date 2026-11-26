package com.example.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.SoundPool
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.OpenableColumns
import android.util.Log
import com.example.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class BgmTrackStyle(val id: String) {
    PAPER_MORNING("paper_morning"),
    MEADOW_8BIT("meadow_8bit"),
    CUSTOM_FILE("custom_file");

    companion object {
        fun fromRaw(raw: String?): BgmTrackStyle =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PAPER_MORNING
    }
}

data class FeedbackPreferences(
    val hapticsEnabled: Boolean = true,
    val sfxEnabled: Boolean = true,
    val bgmEnabled: Boolean = true,
    val bgmVolume: Float = 0.28f,
    val bgmTrackStyle: BgmTrackStyle = BgmTrackStyle.PAPER_MORNING,
    val customBgmName: String? = null
)

enum class BunnyHaptic {
    TASK_COMPLETE,
    TASK_UNCHECK,
    CELEBRATE,
    REMINDER_DISMISS,
    REMINDER_SNOOZE,
    SAVE_SUCCESS,
    DELETE_ITEM,
    POP_OPEN,
    LIGHT_TICK,
    BUNNY_PAT,
    CHEEK_PINCH
}

/**
 * Unified tactile haptic, cute 8-bit SFX, and subtle looping 8-bit BGM manager.
 *
 * Reliability design:
 * - Synthesizes chiptune waveforms once off the main thread and caches standard `.wav` files
 *   inside `cacheDir/bunny_audio/`.
 * - Uses [SoundPool] (`USAGE_MEDIA`) for instant low-latency SFX mixing, with a minimum-buffer-clamped
 *   [AudioTrack] fallback so short chimes never fail AudioTrack buffer validation on emulators.
 * - Uses [MediaPlayer] (`USAGE_MEDIA`, `isLooping = true`) for seamless 8-bit lullaby BGM playback
 *   across devices and emulators, pausing cleanly on background pause and resuming on foreground resume.
 */
class BunnyFeedbackManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable !is CancellationException) {
            Log.w(TAG, "Audio feedback background warning: ${throwable.message}")
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)

    private val _preferences = MutableStateFlow(loadPreferences())
    val preferences: StateFlow<FeedbackPreferences> = _preferences.asStateFlow()

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    private val audioCacheDir: File by lazy {
        File(appContext.cacheDir, "bunny_audio").apply { mkdirs() }
    }

    private val persistentAudioDir: File by lazy {
        File(appContext.filesDir, "bunny_audio").apply { mkdirs() }
    }

    private fun customBgmFile(): File = File(persistentAudioDir, CUSTOM_BGM_FILENAME)

    private val sfxCache = ConcurrentHashMap<BunnySfx, ShortArray>()
    private val sfxSoundIds = ConcurrentHashMap<BunnySfx, Int>()
    private val voiceCache = ConcurrentHashMap<BunnyVoiceMood, ShortArray>()
    private val voiceSoundIds = ConcurrentHashMap<BunnyVoiceMood, Int>()
    private val loadedSoundIds = ConcurrentHashMap.newKeySet<Int>()
    private val activeSfxCount = AtomicInteger(0)
    private val lastVoiceStreamId = AtomicInteger(0)

    private val soundPool: SoundPool? = runCatching {
        SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
            .apply {
                setOnLoadCompleteListener { _, sampleId, status ->
                    if (status == 0) {
                        loadedSoundIds.add(sampleId)
                    } else {
                        Log.w(TAG, "SoundPool load failed for sampleId=$sampleId status=$status")
                    }
                }
            }
    }.onFailure {
        Log.w(TAG, "SoundPool init unavailable: ${it.message}")
    }.getOrNull()

    private val bgmLock = Any()
    private val bgmPrepMutex = Mutex()
    private val bgmGeneration = AtomicLong(0L)
    private var bgmPlayer: MediaPlayer? = null

    @Volatile
    private var isForeground: Boolean = false

    @Volatile
    private var isPreparingBgm: Boolean = false

    @Volatile
    private var bgmPausedForMemo: Boolean = false

    init {
        // Synthesize and pre-load cached .wav SFX & 8-bit bunny voice clips asynchronously
        scope.launch(Dispatchers.IO) {
            BunnySfx.entries.forEach { sfx ->
                runCatching {
                    val wavFile = File(audioCacheDir, "sfx_${sfx.name.lowercase()}_v2.wav")
                    if (!wavFile.exists() || wavFile.length() <= 44L) {
                        val pcm = sfxCache.computeIfAbsent(sfx) { ChiptuneSynthesizer.synthesizeSfx(it) }
                        ChiptuneSynthesizer.writeWavToFile(pcm, wavFile)
                    }
                    soundPool?.let { pool ->
                        val sid = pool.load(wavFile.absolutePath, 1)
                        if (sid > 0) {
                            sfxSoundIds[sfx] = sid
                        }
                    }
                }.onFailure { err ->
                    Log.w(TAG, "Failed preparing SFX ${sfx.name}: ${err.message}")
                }
            }
            BunnyVoiceMood.entries.forEach { mood ->
                runCatching {
                    val wavFile = File(audioCacheDir, "bunny_voice_${mood.name.lowercase()}_v1.wav")
                    if (!wavFile.exists() || wavFile.length() <= 44L) {
                        val pcm = voiceCache.computeIfAbsent(mood) { ChiptuneSynthesizer.synthesizeBunnyVoice(it) }
                        ChiptuneSynthesizer.writeWavToFile(pcm, wavFile)
                    }
                    soundPool?.let { pool ->
                        val sid = pool.load(wavFile.absolutePath, 1)
                        if (sid > 0) {
                            voiceSoundIds[mood] = sid
                        }
                    }
                }.onFailure { err ->
                    Log.w(TAG, "Failed preparing bunny voice ${mood.name}: ${err.message}")
                }
            }
        }
    }

    private fun loadPreferences(): FeedbackPreferences {
        val rawStyle = BgmTrackStyle.fromRaw(prefs.getString(KEY_BGM_STYLE, BgmTrackStyle.PAPER_MORNING.name))
        val customName = prefs.getString(KEY_CUSTOM_BGM_NAME, null)?.takeIf { it.isNotBlank() }
        val hasCustomFile = runCatching { customBgmFile().let { it.exists() && it.length() > 0L } }.getOrDefault(false)
        val effectiveStyle = if (rawStyle == BgmTrackStyle.CUSTOM_FILE && !hasCustomFile) {
            BgmTrackStyle.PAPER_MORNING
        } else {
            rawStyle
        }
        return FeedbackPreferences(
            hapticsEnabled = prefs.getBoolean(KEY_HAPTICS, true),
            sfxEnabled = prefs.getBoolean(KEY_SFX, true),
            bgmEnabled = prefs.getBoolean(KEY_BGM, true),
            bgmVolume = prefs.getFloat(KEY_BGM_VOLUME, DEFAULT_BGM_VOLUME)
                .coerceIn(MIN_BGM_VOLUME, MAX_BGM_VOLUME),
            bgmTrackStyle = effectiveStyle,
            customBgmName = if (hasCustomFile) (customName ?: "Custom BGM") else null
        )
    }

    fun setHapticsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTICS, enabled).apply()
        _preferences.value = _preferences.value.copy(hapticsEnabled = enabled)
        if (enabled) triggerHaptic(BunnyHaptic.LIGHT_TICK)
    }

    fun setSfxEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SFX, enabled).apply()
        _preferences.value = _preferences.value.copy(sfxEnabled = enabled)
        if (enabled) playSfx(BunnySfx.POP)
    }

    fun setBgmEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BGM, enabled).apply()
        _preferences.value = _preferences.value.copy(bgmEnabled = enabled)
        if (enabled) {
            // Treat explicit user toggle inside app UI as foreground active so playback begins immediately
            isForeground = true
            ensureBgmPlayingAsync()
        } else {
            pauseBgmInternal()
        }
    }

    fun setBgmTrackStyle(style: BgmTrackStyle) {
        val hasCustom = customBgmFile().let { it.exists() && it.length() > 0L }
        val target = if (style == BgmTrackStyle.CUSTOM_FILE && !hasCustom) {
            BgmTrackStyle.PAPER_MORNING
        } else {
            style
        }
        if (_preferences.value.bgmTrackStyle == target) return
        prefs.edit().putString(KEY_BGM_STYLE, target.name).apply()
        _preferences.value = _preferences.value.copy(bgmTrackStyle = target)
        triggerHaptic(BunnyHaptic.LIGHT_TICK)
        releaseBgmPlayer()
        if (_preferences.value.bgmEnabled) {
            isForeground = true
            ensureBgmPlayingAsync()
        }
    }

    fun importCustomBgm(uri: Uri, onResult: (Result<String>) -> Unit = {}) {
        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                val resolver = appContext.contentResolver
                val displayName = resolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }?.trim()?.take(80)?.ifBlank { null } ?: "Custom BGM"

                val tmpFile = File(persistentAudioDir, "$CUSTOM_BGM_FILENAME.tmp")
                var totalBytes = 0L
                val maxBytes = 40L * 1024L * 1024L
                resolver.openInputStream(uri)?.use { input ->
                    tmpFile.outputStream().use { out ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            totalBytes += read
                            if (totalBytes > maxBytes) {
                                throw IllegalArgumentException("Audio file exceeds 40 MB limit.")
                            }
                            out.write(buffer, 0, read)
                        }
                        out.flush()
                    }
                } ?: throw IllegalStateException("Could not open selected audio file.")

                if (totalBytes <= 0L) {
                    tmpFile.delete()
                    throw IllegalArgumentException("Selected audio file is empty.")
                }

                // Validate MediaPlayer can decode this audio file before activating it
                val probe = MediaPlayer()
                try {
                    probe.setDataSource(tmpFile.absolutePath)
                    probe.prepare()
                } finally {
                    runCatching { probe.release() }
                }

                val dest = customBgmFile()
                if (!tmpFile.renameTo(dest)) {
                    tmpFile.copyTo(dest, overwrite = true)
                    tmpFile.delete()
                }

                prefs.edit()
                    .putString(KEY_CUSTOM_BGM_NAME, displayName)
                    .putString(KEY_BGM_STYLE, BgmTrackStyle.CUSTOM_FILE.name)
                    .putBoolean(KEY_BGM, true)
                    .apply()

                _preferences.value = _preferences.value.copy(
                    bgmEnabled = true,
                    bgmTrackStyle = BgmTrackStyle.CUSTOM_FILE,
                    customBgmName = displayName
                )
                releaseBgmPlayer()
                isForeground = true
                ensureBgmPlayingAsync()
                displayName
            }
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    triggerHaptic(BunnyHaptic.SAVE_SUCCESS)
                }
                onResult(result)
            }
        }
    }

    fun clearCustomBgm() {
        runCatching { customBgmFile().delete() }
        val nextStyle = if (_preferences.value.bgmTrackStyle == BgmTrackStyle.CUSTOM_FILE) {
            BgmTrackStyle.PAPER_MORNING
        } else {
            _preferences.value.bgmTrackStyle
        }
        prefs.edit()
            .remove(KEY_CUSTOM_BGM_NAME)
            .putString(KEY_BGM_STYLE, nextStyle.name)
            .apply()
        val wasCustom = _preferences.value.bgmTrackStyle == BgmTrackStyle.CUSTOM_FILE
        _preferences.value = _preferences.value.copy(
            bgmTrackStyle = nextStyle,
            customBgmName = null
        )
        if (wasCustom) {
            releaseBgmPlayer()
            if (_preferences.value.bgmEnabled && isForeground) {
                ensureBgmPlayingAsync()
            }
        }
    }

    fun toggleBgm() {
        val next = !_preferences.value.bgmEnabled
        setBgmEnabled(next)
        triggerHaptic(BunnyHaptic.LIGHT_TICK)
    }

    fun setBgmVolume(volume: Float) {
        val clamped = volume.coerceIn(MIN_BGM_VOLUME, MAX_BGM_VOLUME)
        prefs.edit().putFloat(KEY_BGM_VOLUME, clamped).apply()
        _preferences.value = _preferences.value.copy(bgmVolume = clamped)
        synchronized(bgmLock) {
            runCatching { bgmPlayer?.setVolume(clamped, clamped) }
        }
    }

    fun onForegroundResume() {
        isForeground = true
        if (_preferences.value.bgmEnabled && !bgmPausedForMemo) {
            ensureBgmPlayingAsync()
        }
    }

    fun onForegroundPause() {
        isForeground = false
        pauseBgmInternal()
    }

    fun pauseBgmForVoiceMemo() {
        bgmPausedForMemo = true
        pauseBgmInternal()
    }

    fun resumeBgmAfterVoiceMemo() {
        if (!bgmPausedForMemo) return
        bgmPausedForMemo = false
        if (_preferences.value.bgmEnabled && isForeground) {
            ensureBgmPlayingAsync()
        }
    }

    // ---- High-level semantic feedback helpers ----

    fun onTaskToggled(completed: Boolean) {
        if (completed) {
            triggerHaptic(BunnyHaptic.TASK_COMPLETE)
            playSfx(BunnySfx.COMPLETE)
        } else {
            triggerHaptic(BunnyHaptic.TASK_UNCHECK)
            playSfx(BunnySfx.UNCHECK)
        }
    }

    fun onCelebrateBloom() {
        triggerHaptic(BunnyHaptic.CELEBRATE)
        playSfx(BunnySfx.CELEBRATE)
    }

    fun onReminderDismissed() {
        triggerHaptic(BunnyHaptic.REMINDER_DISMISS)
        playSfx(BunnySfx.DISMISS)
    }

    fun onReminderSnoozed() {
        triggerHaptic(BunnyHaptic.REMINDER_SNOOZE)
        playSfx(BunnySfx.SNOOZE)
    }

    fun onSaveSuccess() {
        triggerHaptic(BunnyHaptic.SAVE_SUCCESS)
        playSfx(BunnySfx.SAVE)
    }

    fun onDeleteAction() {
        triggerHaptic(BunnyHaptic.DELETE_ITEM)
        playSfx(BunnySfx.DELETE)
    }

    fun onPopOpen() {
        triggerHaptic(BunnyHaptic.POP_OPEN)
        playSfx(BunnySfx.POP)
    }

    fun onTabSwitch() {
        triggerHaptic(BunnyHaptic.LIGHT_TICK)
        playSfx(BunnySfx.TICK)
    }

    fun onLightTick() {
        triggerHaptic(BunnyHaptic.LIGHT_TICK)
    }

    fun onTestFeedback() {
        triggerHaptic(BunnyHaptic.CELEBRATE)
        playSfx(BunnySfx.CELEBRATE)
        if (_preferences.value.bgmEnabled) {
            isForeground = true
            ensureBgmPlayingAsync()
        }
    }

    fun onBunnyPat() {
        triggerHaptic(BunnyHaptic.BUNNY_PAT)
        playBunnyVoice(BunnyVoiceMood.HAPPY_PAT)
    }

    fun onBunnyCheekPinch() {
        triggerHaptic(BunnyHaptic.CHEEK_PINCH)
        playBunnyVoice(BunnyVoiceMood.PINCH_CHEEK)
    }

    fun onHabitToggled(completed: Boolean) {
        if (completed) {
            triggerHaptic(BunnyHaptic.TASK_COMPLETE)
            playSfx(BunnySfx.HABIT_CHECK)
        } else {
            triggerHaptic(BunnyHaptic.TASK_UNCHECK)
            playSfx(BunnySfx.UNCHECK)
        }
    }

    fun onHabitRewardEarned(allDoneToday: Boolean, isStreakMilestone: Boolean) {
        triggerHaptic(BunnyHaptic.CELEBRATE)
        when {
            allDoneToday && isStreakMilestone -> {
                playSfx(BunnySfx.HABIT_ALL_DONE_REWARD)
                scope.launch {
                    delay(140L)
                    playSfx(BunnySfx.HABIT_STREAK_FANFARE)
                }
            }
            allDoneToday -> playSfx(BunnySfx.HABIT_ALL_DONE_REWARD)
            isStreakMilestone -> playSfx(BunnySfx.HABIT_STREAK_FANFARE)
            else -> playSfx(BunnySfx.HABIT_CHECK)
        }
    }

    fun playBunnyVoice(mood: BunnyVoiceMood) {
        if (!_preferences.value.sfxEnabled) return

        val pool = soundPool
        val sid = voiceSoundIds[mood]
        if (pool != null && sid != null && loadedSoundIds.contains(sid)) {
            val prevStream = lastVoiceStreamId.getAndSet(0)
            if (prevStream != 0) {
                runCatching { pool.stop(prevStream) }
            }
            val streamId = runCatching {
                pool.play(sid, VOICE_VOLUME, VOICE_VOLUME, 2, 0, 1.0f)
            }.getOrDefault(0)
            if (streamId != 0) {
                lastVoiceStreamId.set(streamId)
                return
            }
        }

        // Safe fallback via AudioTrack
        playPcmFallbackAsync {
            voiceCache.computeIfAbsent(mood) { ChiptuneSynthesizer.synthesizeBunnyVoice(it) }
        }
    }

    // ---- Tactile Haptics Implementation ----

    fun triggerHaptic(type: BunnyHaptic) {
        if (!_preferences.value.hapticsEnabled) return
        val vib = vibrator ?: return
        runCatching {
            if (!vib.hasVibrator()) return
            val hasAmp = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vib.hasAmplitudeControl()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect: VibrationEffect = when (type) {
                    BunnyHaptic.TASK_COMPLETE -> {
                        if (hasAmp) {
                            VibrationEffect.createWaveform(
                                longArrayOf(0L, 14L, 28L, 22L),
                                intArrayOf(0, 95, 0, 165),
                                -1
                            )
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                        } else {
                            VibrationEffect.createOneShot(20L, VibrationEffect.DEFAULT_AMPLITUDE)
                        }
                    }

                    BunnyHaptic.TASK_UNCHECK -> {
                        if (hasAmp) {
                            VibrationEffect.createOneShot(10L, 65)
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                        } else {
                            VibrationEffect.createOneShot(10L, VibrationEffect.DEFAULT_AMPLITUDE)
                        }
                    }

                    BunnyHaptic.CELEBRATE -> {
                        if (hasAmp) {
                            VibrationEffect.createWaveform(
                                longArrayOf(0L, 16L, 36L, 20L, 42L, 32L),
                                intArrayOf(0, 90, 0, 140, 0, 210),
                                -1
                            )
                        } else {
                            VibrationEffect.createWaveform(
                                longArrayOf(0L, 18L, 40L, 24L, 40L, 34L),
                                -1
                            )
                        }
                    }

                    BunnyHaptic.REMINDER_DISMISS -> {
                        if (hasAmp) {
                            VibrationEffect.createOneShot(18L, 115)
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                        } else {
                            VibrationEffect.createOneShot(18L, VibrationEffect.DEFAULT_AMPLITUDE)
                        }
                    }

                    BunnyHaptic.REMINDER_SNOOZE -> {
                        if (hasAmp) {
                            VibrationEffect.createWaveform(
                                longArrayOf(0L, 12L, 32L, 14L),
                                intArrayOf(0, 85, 0, 110),
                                -1
                            )
                        } else {
                            VibrationEffect.createOneShot(16L, VibrationEffect.DEFAULT_AMPLITUDE)
                        }
                    }

                    BunnyHaptic.SAVE_SUCCESS -> {
                        if (hasAmp) {
                            VibrationEffect.createOneShot(16L, 130)
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                        } else {
                            VibrationEffect.createOneShot(16L, VibrationEffect.DEFAULT_AMPLITUDE)
                        }
                    }

                    BunnyHaptic.DELETE_ITEM -> {
                        if (hasAmp) {
                            VibrationEffect.createOneShot(24L, 155)
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                        } else {
                            VibrationEffect.createOneShot(24L, VibrationEffect.DEFAULT_AMPLITUDE)
                        }
                    }

                    BunnyHaptic.POP_OPEN -> {
                        if (hasAmp) {
                            VibrationEffect.createOneShot(12L, 85)
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                        } else {
                            VibrationEffect.createOneShot(12L, VibrationEffect.DEFAULT_AMPLITUDE)
                        }
                    }

                    BunnyHaptic.LIGHT_TICK -> {
                        if (hasAmp) {
                            VibrationEffect.createOneShot(8L, 55)
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                        } else {
                            VibrationEffect.createOneShot(8L, VibrationEffect.DEFAULT_AMPLITUDE)
                        }
                    }

                    BunnyHaptic.BUNNY_PAT -> {
                        if (hasAmp) {
                            VibrationEffect.createWaveform(
                                longArrayOf(0L, 14L, 26L, 16L, 26L, 12L),
                                intArrayOf(0, 75, 0, 115, 0, 80),
                                -1
                            )
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                        } else {
                            VibrationEffect.createOneShot(18L, VibrationEffect.DEFAULT_AMPLITUDE)
                        }
                    }

                    BunnyHaptic.CHEEK_PINCH -> {
                        if (hasAmp) {
                            VibrationEffect.createWaveform(
                                longArrayOf(0L, 22L, 30L, 18L),
                                intArrayOf(0, 145, 0, 100),
                                -1
                            )
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                        } else {
                            VibrationEffect.createOneShot(22L, VibrationEffect.DEFAULT_AMPLITUDE)
                        }
                    }
                }
                vib.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(14L)
            }
        }
    }

    // ---- Cute 8-Bit Sound Effects Implementation ----

    fun playSfx(sfx: BunnySfx) {
        if (!_preferences.value.sfxEnabled) return

        // Fast zero-lag path: SoundPool preloaded WAV
        val pool = soundPool
        val sid = sfxSoundIds[sfx]
        if (pool != null && sid != null && loadedSoundIds.contains(sid)) {
            val streamId = runCatching {
                pool.play(sid, SFX_VOLUME, SFX_VOLUME, 1, 0, 1.0f)
            }.getOrDefault(0)
            if (streamId != 0) return
        }

        // Safe fallback path via AudioTrack with buffer size >= getMinBufferSize
        playPcmFallbackAsync {
            sfxCache.computeIfAbsent(sfx) { ChiptuneSynthesizer.synthesizeSfx(it) }
        }
    }

    private fun playPcmFallbackAsync(loadPcm: () -> ShortArray) {
        if (activeSfxCount.get() >= MAX_CONCURRENT_SFX) return
        scope.launch(Dispatchers.IO) {
            if (activeSfxCount.incrementAndGet() > MAX_CONCURRENT_SFX) {
                activeSfxCount.decrementAndGet()
                return@launch
            }
            var track: AudioTrack? = null
            try {
                val pcm = loadPcm()
                if (pcm.isEmpty()) return@launch
                val minBuf = AudioTrack.getMinBufferSize(
                    ChiptuneSynthesizer.SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(4096)
                val pcmBytes = pcm.size * 2
                val bufferBytes = maxOf(pcmBytes, minBuf)
                val paddedSamples = bufferBytes / 2
                val payload = if (paddedSamples > pcm.size) {
                    ShortArray(paddedSamples).also { pcm.copyInto(it) }
                } else {
                    pcm
                }

                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(ChiptuneSynthesizer.SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferBytes)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                if (track.state != AudioTrack.STATE_INITIALIZED) {
                    Log.w(TAG, "Fallback AudioTrack uninitialized")
                    return@launch
                }
                track.write(payload, 0, payload.size)
                track.setVolume(VOICE_VOLUME)
                track.play()

                val durationMs = ((pcm.size * 1_000L) / ChiptuneSynthesizer.SAMPLE_RATE) + 60L
                delay(durationMs)
            } catch (err: Throwable) {
                Log.w(TAG, "AudioTrack fallback failed: ${err.message}")
            } finally {
                runCatching {
                    track?.stop()
                    track?.release()
                }
                activeSfxCount.decrementAndGet()
            }
        }
    }

    // ---- Subtle Looping BGM Implementation (MediaPlayer + Bundled Paper Morning MP3 / Cached WAV) ----

    private sealed interface BgmSource {
        data class FilePath(val file: File) : BgmSource
        data class RawRes(val resId: Int) : BgmSource
    }

    private fun resolveBgmSourceLocked(): BgmSource {
        val style = _preferences.value.bgmTrackStyle
        if (style == BgmTrackStyle.CUSTOM_FILE) {
            val custom = customBgmFile()
            if (custom.exists() && custom.length() > 0L) {
                return BgmSource.FilePath(custom)
            }
        }
        return when (style) {
            BgmTrackStyle.MEADOW_8BIT -> {
                val f = File(audioCacheDir, MEADOW_BGM_WAV_FILENAME)
                if (!f.exists() || f.length() <= 44L) {
                    val pcm = ChiptuneSynthesizer.synthesizeMeadow8BitBgmLoop()
                    ChiptuneSynthesizer.writeWavToFile(pcm, f)
                }
                BgmSource.FilePath(f)
            }
            BgmTrackStyle.PAPER_MORNING, BgmTrackStyle.CUSTOM_FILE -> {
                BgmSource.RawRes(R.raw.bgm_paper_morning)
            }
        }
    }

    private fun configureRawResourceDataSource(player: MediaPlayer, resId: Int) {
        val usedFd = runCatching {
            appContext.resources.openRawResourceFd(resId)?.use { afd ->
                player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                true
            } ?: false
        }.getOrDefault(false)

        if (usedFd) return

        // Fallback if APK packaging ever compresses the raw asset: cache once and stream from file
        val cachedRaw = File(audioCacheDir, PAPER_MORNING_CACHED_MP3_FILENAME)
        if (!cachedRaw.exists() || cachedRaw.length() <= 0L) {
            val tmpFile = File(audioCacheDir, "$PAPER_MORNING_CACHED_MP3_FILENAME.tmp")
            appContext.resources.openRawResource(resId).use { input ->
                tmpFile.outputStream().use { out ->
                    input.copyTo(out)
                    out.flush()
                }
            }
            if (!tmpFile.renameTo(cachedRaw)) {
                tmpFile.copyTo(cachedRaw, overwrite = true)
                tmpFile.delete()
            }
        }
        player.setDataSource(cachedRaw.absolutePath)
    }

    private fun releaseBgmPlayer() {
        bgmGeneration.incrementAndGet()
        synchronized(bgmLock) {
            runCatching {
                bgmPlayer?.stop()
            }
            runCatching {
                bgmPlayer?.release()
            }
            bgmPlayer = null
            isPreparingBgm = false
        }
    }

    private fun ensureBgmPlayingAsync() {
        synchronized(bgmLock) {
            val existing = bgmPlayer
            if (existing != null) {
                val vol = _preferences.value.bgmVolume
                runCatching {
                    existing.setVolume(vol, vol)
                    if (!existing.isPlaying) {
                        existing.start()
                    }
                    return
                }.onFailure { err ->
                    Log.w(TAG, "Existing BGM MediaPlayer resume failed: ${err.message}")
                    runCatching { existing.release() }
                    bgmPlayer = null
                }
            }
            if (isPreparingBgm) return
            isPreparingBgm = true
        }

        val targetGeneration = bgmGeneration.get()
        scope.launch(Dispatchers.IO) {
            bgmPrepMutex.withLock {
                try {
                    if (bgmGeneration.get() != targetGeneration || !isForeground || !_preferences.value.bgmEnabled || bgmPausedForMemo) {
                        return@withLock
                    }
                    val alreadyPlaying = synchronized(bgmLock) { bgmPlayer != null }
                    if (alreadyPlaying) return@withLock

                    val bgmSource = resolveBgmSourceLocked()
                    if (bgmGeneration.get() != targetGeneration || !isForeground || !_preferences.value.bgmEnabled || bgmPausedForMemo) {
                        return@withLock
                    }

                    val player = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        when (bgmSource) {
                            is BgmSource.RawRes -> configureRawResourceDataSource(this, bgmSource.resId)
                            is BgmSource.FilePath -> setDataSource(bgmSource.file.absolutePath)
                        }
                        isLooping = true
                        prepare()
                        val vol = _preferences.value.bgmVolume
                        setVolume(vol, vol)
                        setOnErrorListener { mp, what, extra ->
                            Log.w(TAG, "BGM MediaPlayer error what=$what extra=$extra")
                            synchronized(bgmLock) {
                                if (bgmPlayer === mp) bgmPlayer = null
                            }
                            runCatching { mp.release() }
                            true
                        }
                    }

                    synchronized(bgmLock) {
                        if (bgmGeneration.get() != targetGeneration || !isForeground || !_preferences.value.bgmEnabled || bgmPausedForMemo) {
                            runCatching { player.release() }
                            return@synchronized
                        }
                        runCatching { bgmPlayer?.release() }
                        val vol = _preferences.value.bgmVolume
                        player.setVolume(vol, vol)
                        player.start()
                        bgmPlayer = player
                    }
                } catch (err: Throwable) {
                    if (err is CancellationException) throw err
                    Log.w(TAG, "Failed starting BGM: ${err.message}")
                    if (_preferences.value.bgmTrackStyle == BgmTrackStyle.CUSTOM_FILE) {
                        // Gracefully fall back to built-in Paper Morning if custom audio fails to play
                        prefs.edit().putString(KEY_BGM_STYLE, BgmTrackStyle.PAPER_MORNING.name).apply()
                        _preferences.value = _preferences.value.copy(bgmTrackStyle = BgmTrackStyle.PAPER_MORNING)
                    }
                } finally {
                    isPreparingBgm = false
                }
            }
        }
    }

    private fun pauseBgmInternal() {
        synchronized(bgmLock) {
            val player = bgmPlayer ?: return
            runCatching {
                if (player.isPlaying) {
                    player.pause()
                }
            }
        }
    }

    companion object {
        private const val TAG = "BunnyFeedbackManager"
        private const val PREFS_NAME = "bunny_memo_feedback_prefs_v1"
        private const val PAPER_MORNING_CACHED_MP3_FILENAME = "bgm_paper_morning_raw.mp3"
        private const val MEADOW_BGM_WAV_FILENAME = "bgm_meadow_v2.wav"
        private const val CUSTOM_BGM_FILENAME = "custom_bgm_track"
        private const val KEY_HAPTICS = "haptics_enabled"
        private const val KEY_SFX = "sfx_enabled"
        private const val KEY_BGM = "bgm_enabled"
        private const val KEY_BGM_VOLUME = "bgm_volume"
        private const val KEY_BGM_STYLE = "bgm_track_style"
        private const val KEY_CUSTOM_BGM_NAME = "custom_bgm_name"
        const val PAPER_MORNING_YOUTUBE_URL = "https://www.youtube.com/watch?v=1prKLIpC8-g"

        private const val DEFAULT_BGM_VOLUME = 0.28f
        const val MIN_BGM_VOLUME = 0.05f
        const val MAX_BGM_VOLUME = 0.70f
        private const val SFX_VOLUME = 0.65f
        private const val VOICE_VOLUME = 0.76f
        private const val MAX_CONCURRENT_SFX = 3

        @Volatile
        private var instance: BunnyFeedbackManager? = null

        fun getInstance(context: Context): BunnyFeedbackManager {
            return instance ?: synchronized(this) {
                instance ?: BunnyFeedbackManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
