package com.example.audio

import android.content.Context
import android.media.AudioAttributes
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
 * Coordinates with [BunnyFeedbackManager] so BGM pauses cleanly during mic capture/playback
 * and emits [TaskAudioBunnyEvent] so the bottom-left Month Bunny can listen and respond.
 */
class TaskAudioMemoManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val feedbackManager = BunnyFeedbackManager.getInstance(appContext)
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
    private var recordingFile: File? = null
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

        val outFile = File(memoDir, "memo_${itemId.take(12)}_${UUID.randomUUID().toString().take(8)}.m4a")
        val newRecorder = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(appContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
        }.getOrElse { err ->
            Log.w(TAG, "MediaRecorder unavailable: ${err.message}")
            _playbackState.value = _playbackState.value.copy(
                errorMessage = "Audio recording is unavailable on this device."
            )
            return
        }

        val started = runCatching {
            feedbackManager.pauseBgmForVoiceMemo()
            newRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(22_050)
                setAudioEncodingBitRate(64_000)
                setMaxDuration(MAX_DURATION_MS)
                setOutputFile(outFile.absolutePath)
                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        scope.launch { stopRecording() }
                    }
                }
                prepare()
                start()
            }
            true
        }.getOrElse { err ->
            Log.w(TAG, "Failed starting audio memo recording: ${err.message}")
            runCatching { newRecorder.release() }
            runCatching { outFile.delete() }
            feedbackManager.resumeBgmAfterVoiceMemo()
            _playbackState.value = _playbackState.value.copy(
                errorMessage = "Could not access microphone right now. Please check device audio settings."
            )
            false
        }

        if (!started) return

        if (!previousMemoPath.isNullOrBlank()) {
            deleteMemoFile(previousMemoPath)
        }

        recorder = newRecorder
        recordingFile = outFile
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

    fun stopRecording() {
        val activeItemId = _playbackState.value.recordingItemId ?: return
        val activeRecorder = recorder
        val outFile = recordingFile
        val title = recordingItemTitle
        val completed = recordingTaskCompleted
        val callback = onRecordingSavedCallback
        val elapsedSec = (((System.currentTimeMillis() - recordingStartedAtMs) + 450L) / 1000L)
            .toInt()
            .coerceIn(1, MAX_DURATION_SEC)

        recordingTickerJob?.cancel()
        recordingTickerJob = null
        recorder = null
        recordingFile = null
        onRecordingSavedCallback = null

        val stopOk = runCatching {
            activeRecorder?.stop()
            true
        }.getOrDefault(false)
        runCatching { activeRecorder?.release() }

        feedbackManager.resumeBgmAfterVoiceMemo()

        if (!stopOk || outFile == null || !outFile.exists() || outFile.length() <= 128L) {
            runCatching { outFile?.delete() }
            _playbackState.value = TaskAudioPlaybackState(
                errorMessage = "Voice memo was too short to save — hold a tiny moment longer!"
            )
            return
        }

        _playbackState.value = TaskAudioPlaybackState()
        callback?.invoke(outFile.absolutePath, elapsedSec)
        _bunnyEvent.value = TaskAudioBunnyEvent(
            eventId = System.nanoTime(),
            itemId = activeItemId,
            itemTitle = title,
            durationSeconds = elapsedSec,
            isTaskCompleted = completed,
            isNewRecording = true
        )
    }

    fun cancelRecording() {
        recordingTickerJob?.cancel()
        recordingTickerJob = null
        val activeRecorder = recorder
        val outFile = recordingFile
        recorder = null
        recordingFile = null
        onRecordingSavedCallback = null
        runCatching { activeRecorder?.stop() }
        runCatching { activeRecorder?.release() }
        runCatching { outFile?.delete() }
        feedbackManager.resumeBgmAfterVoiceMemo()
        _playbackState.value = TaskAudioPlaybackState()
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

        val file = File(filePath)
        if (!file.exists() || file.length() <= 0L) {
            _playbackState.value = _playbackState.value.copy(
                errorMessage = "Voice memo file could not be found."
            )
            return
        }

        val mp = MediaPlayer()
        val prepared = runCatching {
            feedbackManager.pauseBgmForVoiceMemo()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            val actualDurSec = (mp.duration / 1000).coerceAtLeast(knownDurationSeconds.coerceAtLeast(1))
            playingItemTitle = itemTitle
            playingTaskCompleted = isTaskCompleted
            playingDurationSec = actualDurSec
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
            actualDurSec
        }.getOrElse { err ->
            Log.w(TAG, "Failed playing audio memo: ${err.message}")
            runCatching { mp.release() }
            feedbackManager.resumeBgmAfterVoiceMemo()
            _playbackState.value = _playbackState.value.copy(
                errorMessage = "Could not play voice memo on this device."
            )
            return
        }

        player = mp
        _playbackState.value = TaskAudioPlaybackState(
            playingItemId = itemId,
            playbackPositionSeconds = 0,
            playbackDurationSeconds = prepared,
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
                    .coerceIn(0, prepared)
                if (_playbackState.value.playbackPositionSeconds != posSec) {
                    _playbackState.value = _playbackState.value.copy(playbackPositionSeconds = posSec)
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
        runCatching { mp?.stop() }
        runCatching { mp?.release() }
        feedbackManager.resumeBgmAfterVoiceMemo()
        _playbackState.value = TaskAudioPlaybackState()

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
