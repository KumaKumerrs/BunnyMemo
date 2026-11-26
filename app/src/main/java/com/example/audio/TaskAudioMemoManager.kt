package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class TaskAudioPlaybackState(
    val recordingItemId: String? = null,
    val recordingElapsedSeconds: Int = 0,
    val playingItemId: String? = null,
    val playbackPositionSeconds: Int = 0,
    val playbackDurationSeconds: Int = 0,
    val isListeningForTaskTitle: String? = null,
    val errorMessage: String? = null
)

data class TaskAudioBunnyEvent(
    val eventId: Long,
    val itemId: String,
    val itemTitle: String,
    val durationSeconds: Int,
    val isTaskCompleted: Boolean,
    val isNewRecording: Boolean
)

/**
 * Device-local manager for recording and playing short (.m4a) audio memos attached to tasks.
 *
 * Reliability design:
 * - Offloads MediaRecorder / MediaPlayer prepare, stop, and release off the Main thread onto [Dispatchers.IO].
 * - Records into a temporary `.tmp.m4a` file first and only replaces/deletes the previous memo
 *   after the new recording stops cleanly and validates (> 128 bytes), preventing accidental data loss.
 * - Acquires transient AudioFocus during recording & playback and stops cleanly on background pause.
 */
class TaskAudioMemoManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val feedbackManager = BunnyFeedbackManager.getInstance(appContext)
    private val audioManager: AudioManager? =
        appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable !is CancellationException) {
            Log.w(TAG, "TaskAudioMemoManager warning: ${throwable.message}")
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + exceptionHandler)

    private val memoDir: File by lazy {
        File(appContext.filesDir, "task_audio_memos").apply { mkdirs() }
    }

    private val _playbackState = MutableStateFlow(TaskAudioPlaybackState())
    val playbackState: StateFlow<TaskAudioPlaybackState> = _playbackState.asStateFlow()

    private val _bunnyEvent = MutableStateFlow<TaskAudioBunnyEvent?>(null)
    val bunnyEvent: StateFlow<TaskAudioBunnyEvent?> = _bunnyEvent.asStateFlow()

    private var recorder: MediaRecorder? = null
    private var recordingTempFile: File? = null
    private var recordingTargetFile: File? = null
    private var pendingPreviousMemoPath: String? = null
    private var recordingItemTitle: String = ""
    private var recordingTaskCompleted: Boolean = false
    private var recordingStartedAtMs: Long = 0L
    private var recordingTickerJob: Job? = null
    private var onRecordingSavedCallback: ((String, Int) -> Unit)? = null

    private var player: MediaPlayer? = null
    private var playingItemTitle: String = ""
    private var playingTaskCompleted: Boolean = false
    private var playingDurationSec: Int = 0
    private var playbackTickerJob: Job? = null
    private var activeFocusRequest: AudioFocusRequest? = null

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
        ) {
            scope.launch {
                if (_playbackState.value.playingItemId != null) {
                    stopPlayback(emitFeedback = false)
                }
            }
        }
    }

    /**
     * Called from Activity lifecycle `ON_PAUSE` so microphone capture and voice playback
     * never stay orphaned in the background.
     */
    fun onAppBackgrounded() {
        if (_playbackState.value.recordingItemId != null) {
            stopRecording()
        }
        if (_playbackState.value.playingItemId != null) {
            stopPlayback(emitFeedback = false)
        }
    }

    fun startRecording(
        itemId: String,
        itemTitle: String,
        isTaskCompleted: Boolean,
        previousMemoPath: String? = null,
        onSaved: (path: String, durationSeconds: Int) -> Unit
    ) {
        stopPlayback(emitFeedback = false)
        if (_playbackState.value.recordingItemId != null) {
            cancelRecording()
        }

        val baseName = "memo_${itemId.take(12)}_${UUID.randomUUID().toString().take(8)}"
        val finalFile = File(memoDir, "$baseName.m4a")
        val tmpFile = File(memoDir, "$baseName.tmp.m4a")

        scope.launch {
            feedbackManager.pauseBgmForVoiceMemo()
            requestTransientAudioFocus()

            val startedRecorder = withContext(Dispatchers.IO) {
                val newRecorder = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        MediaRecorder(appContext)
                    } else {
                        @Suppress("DEPRECATION")
                        MediaRecorder()
                    }
                }.getOrElse { err ->
                    Log.w(TAG, "MediaRecorder unavailable: ${err.message}")
                    return@withContext null
                }

                runCatching {
                    newRecorder.apply {
                        setAudioSource(MediaRecorder.AudioSource.MIC)
                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        setAudioSamplingRate(22_050)
                        setAudioEncodingBitRate(64_000)
                        setMaxDuration(MAX_DURATION_MS)
                        setOutputFile(tmpFile.absolutePath)
                        setOnInfoListener { _, what, _ ->
                            if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                                scope.launch { stopRecording() }
                            }
                        }
                        prepare()
                        start()
                    }
                    newRecorder
                }.getOrElse { err ->
                    Log.w(TAG, "Failed starting audio memo recording: ${err.message}")
                    runCatching { newRecorder.release() }
                    runCatching { tmpFile.delete() }
                    null
                }
            }

            if (startedRecorder == null) {
                abandonTransientAudioFocus()
                feedbackManager.resumeBgmAfterVoiceMemo()
                _playbackState.value = _playbackState.value.copy(
                    errorMessage = "Could not access microphone right now. Please check device audio settings."
                )
                return@launch
            }

            // Note: previousMemoPath is preserved until stopRecording() succeeds & validates > 128 bytes!
            recorder = startedRecorder
            recordingTempFile = tmpFile
            recordingTargetFile = finalFile
            pendingPreviousMemoPath = previousMemoPath
            recordingItemTitle = itemTitle
            recordingTaskCompleted = isTaskCompleted
            recordingStartedAtMs = System.currentTimeMillis()
            onRecordingSavedCallback = onSaved

            feedbackManager.onLightTick()
            _playbackState.value = TaskAudioPlaybackState(
                recordingItemId = itemId,
                recordingElapsedSeconds = 0,
                isListeningForTaskTitle = itemTitle,
                errorMessage = null
            )

            recordingTickerJob?.cancel()
            recordingTickerJob = scope.launch {
                while (isActive && _playbackState.value.recordingItemId == itemId) {
                    delay(250L)
                    val elapsedSec = ((System.currentTimeMillis() - recordingStartedAtMs) / 1000L)
                        .toInt()
                        .coerceIn(0, MAX_DURATION_SEC)
                    if (_playbackState.value.recordingElapsedSeconds != elapsedSec) {
                        _playbackState.value = _playbackState.value.copy(recordingElapsedSeconds = elapsedSec)
                    }
                    if (elapsedSec >= MAX_DURATION_SEC) {
                        stopRecording()
                        break
                    }
                }
            }
        }
    }

    fun stopRecording() {
        val activeItemId = _playbackState.value.recordingItemId ?: return
        val activeRecorder = recorder
        val tmpFile = recordingTempFile
        val finalFile = recordingTargetFile
        val oldPathToDelete = pendingPreviousMemoPath
        val title = recordingItemTitle
        val completed = recordingTaskCompleted
        val callback = onRecordingSavedCallback
        val elapsedSec = (((System.currentTimeMillis() - recordingStartedAtMs) + 450L) / 1000L)
            .toInt()
            .coerceIn(1, MAX_DURATION_SEC)

        recordingTickerJob?.cancel()
        recordingTickerJob = null
        recorder = null
        recordingTempFile = null
        recordingTargetFile = null
        pendingPreviousMemoPath = null
        onRecordingSavedCallback = null
        _playbackState.value = TaskAudioPlaybackState()

        scope.launch {
            val savedPath = withContext(Dispatchers.IO) {
                val stopOk = runCatching {
                    activeRecorder?.stop()
                    true
                }.getOrDefault(false)
                runCatching { activeRecorder?.release() }

                if (!stopOk || tmpFile == null || finalFile == null || !tmpFile.exists() || tmpFile.length() <= 128L) {
                    runCatching { tmpFile?.delete() }
                    return@withContext null
                }

                val promoted = if (tmpFile.renameTo(finalFile)) {
                    true
                } else {
                    runCatching {
                        tmpFile.copyTo(finalFile, overwrite = true)
                        tmpFile.delete()
                        true
                    }.getOrDefault(false)
                }

                if (!promoted || !finalFile.exists() || finalFile.length() <= 128L) {
                    runCatching { tmpFile.delete() }
                    runCatching { finalFile.delete() }
                    return@withContext null
                }

                // Safe to remove previous voice memo ONLY after the new file is durably saved
                if (!oldPathToDelete.isNullOrBlank() && oldPathToDelete != finalFile.absolutePath) {
                    deleteMemoFile(oldPathToDelete)
                }
                finalFile.absolutePath
            }

            abandonTransientAudioFocus()
            feedbackManager.resumeBgmAfterVoiceMemo()

            if (savedPath == null) {
                _playbackState.value = TaskAudioPlaybackState(
                    errorMessage = "Voice memo was too short to save — hold a tiny moment longer!"
                )
                return@launch
            }

            callback?.invoke(savedPath, elapsedSec)
            _bunnyEvent.value = TaskAudioBunnyEvent(
                eventId = System.nanoTime(),
                itemId = activeItemId,
                itemTitle = title,
                durationSeconds = elapsedSec,
                isTaskCompleted = completed,
                isNewRecording = true
            )
        }
    }

    fun cancelRecording() {
        recordingTickerJob?.cancel()
        recordingTickerJob = null
        val activeRecorder = recorder
        val tmpFile = recordingTempFile
        recorder = null
        recordingTempFile = null
        recordingTargetFile = null
        pendingPreviousMemoPath = null
        onRecordingSavedCallback = null
        _playbackState.value = TaskAudioPlaybackState()

        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching { activeRecorder?.stop() }
                runCatching { activeRecorder?.release() }
                runCatching { tmpFile?.delete() }
            }
            abandonTransientAudioFocus()
            feedbackManager.resumeBgmAfterVoiceMemo()
        }
    }

    fun togglePlayback(
        itemId: String,
        itemTitle: String,
        isTaskCompleted: Boolean,
        filePath: String,
        knownDurationSeconds: Int
    ) {
        if (_playbackState.value.playingItemId == itemId) {
            stopPlayback(emitFeedback = true)
            return
        }

        if (_playbackState.value.recordingItemId != null) {
            stopRecording()
        }
        stopPlayback(emitFeedback = false)

        scope.launch {
            val file = File(filePath)
            val existsAndValid = withContext(Dispatchers.IO) { file.exists() && file.length() > 0L }
            if (!existsAndValid) {
                _playbackState.value = _playbackState.value.copy(
                    errorMessage = "Voice memo file could not be found."
                )
                return@launch
            }

            feedbackManager.pauseBgmForVoiceMemo()
            requestTransientAudioFocus()

            val preparedPair = withContext(Dispatchers.IO) {
                val mp = MediaPlayer()
                runCatching {
                    mp.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    mp.setDataSource(file.absolutePath)
                    mp.prepare()
                    val actualDurSec = (mp.duration / 1000).coerceAtLeast(knownDurationSeconds.coerceAtLeast(1))
                    mp.setOnCompletionListener {
                        scope.launch {
                            completePlayback(itemId = itemId, naturalFinish = true)
                        }
                    }
                    mp.setOnErrorListener { _, _, _ ->
                        scope.launch {
                            stopPlayback(emitFeedback = false)
                            _playbackState.value = _playbackState.value.copy(
                                errorMessage = "Could not play this voice memo."
                            )
                        }
                        true
                    }
                    mp.start()
                    mp to actualDurSec
                }.getOrElse { err ->
                    Log.w(TAG, "Failed playing audio memo: ${err.message}")
                    runCatching { mp.release() }
                    null
                }
            }

            if (preparedPair == null) {
                abandonTransientAudioFocus()
                feedbackManager.resumeBgmAfterVoiceMemo()
                _playbackState.value = _playbackState.value.copy(
                    errorMessage = "Could not play voice memo on this device."
                )
                return@launch
            }

            val (mp, actualDurSec) = preparedPair
            playingItemTitle = itemTitle
            playingTaskCompleted = isTaskCompleted
            playingDurationSec = actualDurSec
            player = mp
            _playbackState.value = TaskAudioPlaybackState(
                playingItemId = itemId,
                playbackPositionSeconds = 0,
                playbackDurationSeconds = actualDurSec,
                isListeningForTaskTitle = itemTitle,
                errorMessage = null
            )

            playbackTickerJob?.cancel()
            playbackTickerJob = scope.launch {
                while (isActive && _playbackState.value.playingItemId == itemId) {
                    delay(200L)
                    val currentPlayer = player ?: break
                    val posSec = runCatching { currentPlayer.currentPosition / 1000 }
                        .getOrDefault(_playbackState.value.playbackPositionSeconds)
                        .coerceIn(0, actualDurSec)
                    if (_playbackState.value.playbackPositionSeconds != posSec) {
                        _playbackState.value = _playbackState.value.copy(playbackPositionSeconds = posSec)
                    }
                }
            }
        }
    }

    fun stopPlayback(emitFeedback: Boolean = false) {
        val activeId = _playbackState.value.playingItemId ?: return
        completePlayback(itemId = activeId, naturalFinish = emitFeedback)
    }

    private fun completePlayback(itemId: String, naturalFinish: Boolean) {
        if (_playbackState.value.playingItemId != itemId) return
        val listenedSec = if (naturalFinish) {
            playingDurationSec.coerceAtLeast(1)
        } else {
            _playbackState.value.playbackPositionSeconds
        }
        val title = playingItemTitle
        val completed = playingTaskCompleted
        val memoDuration = playingDurationSec.coerceAtLeast(1)

        playbackTickerJob?.cancel()
        playbackTickerJob = null
        val mp = player
        player = null
        _playbackState.value = TaskAudioPlaybackState()

        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching { mp?.stop() }
                runCatching { mp?.release() }
            }
            abandonTransientAudioFocus()
            feedbackManager.resumeBgmAfterVoiceMemo()
        }

        if (naturalFinish || listenedSec >= 1) {
            _bunnyEvent.value = TaskAudioBunnyEvent(
                eventId = System.nanoTime(),
                itemId = itemId,
                itemTitle = title,
                durationSeconds = memoDuration,
                isTaskCompleted = completed,
                isNewRecording = false
            )
        }
    }

    fun clearError() {
        if (_playbackState.value.errorMessage != null) {
            _playbackState.value = _playbackState.value.copy(errorMessage = null)
        }
    }

    private fun requestTransientAudioFocus() {
        val am = audioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(audioFocusListener)
                    .build()
                activeFocusRequest = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    audioFocusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
        }
    }

    private fun abandonTransientAudioFocus() {
        val am = audioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activeFocusRequest?.let { am.abandonAudioFocusRequest(it) }
                activeFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(audioFocusListener)
            }
        }
    }

    companion object {
        private const val TAG = "TaskAudioMemoManager"
        const val MAX_DURATION_SEC = 60
        private const val MAX_DURATION_MS = MAX_DURATION_SEC * 1000

        @Volatile
        private var instance: TaskAudioMemoManager? = null

        fun getInstance(context: Context): TaskAudioMemoManager {
            return instance ?: synchronized(this) {
                instance ?: TaskAudioMemoManager(context.applicationContext).also { instance = it }
            }
        }

        fun formatSeconds(seconds: Int): String {
            val safe = seconds.coerceAtLeast(0)
            val mins = safe / 60
            val secs = safe % 60
            return "%d:%02d".format(mins, secs)
        }

        fun deleteMemoFile(path: String?) {
            if (path.isNullOrBlank()) return
            runCatching {
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }
}
