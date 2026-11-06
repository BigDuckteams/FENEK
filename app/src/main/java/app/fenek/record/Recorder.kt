package app.fenek.record

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import app.fenek.service.RecordTileService
import app.fenek.service.RecordingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10

/** A finished recording kept in app storage until it is transcribed or deleted. */
data class Recording(val file: File, val createdAt: Long, val durationMs: Long, val marks: List<Long> = emptyList()) {
    val displayName: String get() = RecordingNames.displayName(createdAt)
}

sealed interface RecorderState {
    data object Idle : RecorderState
    data class Active(
        val file: File,
        val createdAt: Long,
        val paused: Boolean,
        val elapsedMs: Long,
        /** Recent input levels 0..1, oldest first, for the live waveform. */
        val levels: List<Float>,
        /** Moments the user marked as important, ms from the start. */
        val marks: List<Long> = emptyList(),
    ) : RecorderState
}

/**
 * Records a conversation from the microphone into AAC (ADTS). ADTS has no index that is
 * written at the end, so even if the process dies mid-recording, everything captured so far
 * stays playable and can be transcribed. All methods must be called on the main thread.
 */
class Recorder(private val context: Context, private val scope: CoroutineScope) {

    private val dir = File(context.filesDir, "recordings")
    private var recorder: MediaRecorder? = null
    private var ticker: Job? = null
    private var accumulatedMs = 0L
    private var resumedAt = 0L
    private val levels = ArrayDeque<Float>()

    private val _state = MutableStateFlow<RecorderState>(RecorderState.Idle)
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    private val _recordings = MutableStateFlow<List<Recording>>(emptyList())

    /** Finished recordings on disk, newest first, including already transcribed ones. */
    val recordings: StateFlow<List<Recording>> = _recordings.asStateFlow()

    init {
        refresh()
    }

    /** Starts recording; returns an error message for the user, or null on success. */
    fun start(): String? {
        if (recorder != null) return null
        dir.mkdirs()
        if (dir.usableSpace < MIN_FREE_SPACE) return "Недостаточно свободного места для записи"
        val createdAt = System.currentTimeMillis()
        val file = File(dir, RecordingNames.fileName(createdAt))
        val mr = newMediaRecorder()
        try {
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioChannels(1)
            mr.setAudioSamplingRate(SAMPLE_RATE)
            mr.setAudioEncodingBitRate(BIT_RATE)
            mr.setOutputFile(file.absolutePath)
            mr.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "MediaRecorder error $what/$extra")
                stop()
            }
            mr.prepare()
            mr.start()
        } catch (e: Exception) {
            Log.w(TAG, "Cannot start recording", e)
            mr.release()
            file.delete()
            return "Не удалось включить микрофон. Возможно, он занят другим приложением."
        }
        recorder = mr
        accumulatedMs = 0
        resumedAt = SystemClock.elapsedRealtime()
        levels.clear()
        _state.value = RecorderState.Active(file, createdAt, paused = false, elapsedMs = 0, levels = emptyList())
        startTicker()
        // Keeps the microphone available while the screen is off or the app is in the background.
        RecordingService.start(context)
        RecordTileService.refresh(context)
        return null
    }

    fun pause() {
        val active = _state.value as? RecorderState.Active ?: return
        if (active.paused) return
        runCatching { recorder?.pause() }.onFailure { Log.w(TAG, "pause failed", it) }
        accumulatedMs += SystemClock.elapsedRealtime() - resumedAt
        _state.value = active.copy(paused = true, elapsedMs = accumulatedMs)
    }

    fun resume() {
        val active = _state.value as? RecorderState.Active ?: return
        if (!active.paused) return
        runCatching { recorder?.resume() }.onFailure { Log.w(TAG, "resume failed", it) }
        resumedAt = SystemClock.elapsedRealtime()
        _state.value = active.copy(paused = false)
    }

    /** Remembers the current moment as important; returns its position, or null if not recording. */
    fun mark(): Long? {
        val active = _state.value as? RecorderState.Active ?: return null
        val at = elapsedMs(active)
        // A double tap is one mark.
        if (active.marks.lastOrNull()?.let { at - it < MIN_MARK_GAP_MS } == true) return null
        RecordingMarks.append(active.file, at)
        _state.value = active.copy(marks = active.marks + at, elapsedMs = at)
        return at
    }

    /** Finishes the recording; returns it, or null if nothing usable was recorded. */
    fun stop(): Recording? {
        val active = _state.value as? RecorderState.Active ?: return null
        val mr = recorder ?: return null
        val elapsed = elapsedMs(active)
        stopTicker()
        // stop() throws when no audio was captured at all.
        val stopped = runCatching { mr.stop() }.isSuccess
        mr.release()
        recorder = null
        _state.value = RecorderState.Idle
        RecordTileService.refresh(context)
        val ok = stopped && active.file.length() > 0 && elapsed >= MIN_DURATION_MS
        if (!ok) {
            active.file.delete()
            RecordingMarks.delete(active.file)
        }
        refresh()
        return if (ok) Recording(active.file, active.createdAt, elapsed, active.marks) else null
    }

    /** Stops and throws the recording away. */
    fun discard() {
        val active = _state.value as? RecorderState.Active ?: return
        stop()
        active.file.delete()
        RecordingMarks.delete(active.file)
        refresh()
    }

    fun delete(recording: Recording) {
        recording.file.delete()
        RecordingMarks.delete(recording.file)
        refresh()
    }

    /** Re-reads the recordings folder, e.g. after a transcript took ownership of a file. */
    fun refresh() {
        scope.launch(Dispatchers.IO) {
            val activeFile = (_state.value as? RecorderState.Active)?.file
            _recordings.value = dir.listFiles().orEmpty()
                .filter { it.isFile && it != activeFile }
                .mapNotNull { file ->
                    val createdAt = RecordingNames.createdAt(file) ?: return@mapNotNull null
                    Recording(file, createdAt, durationOf(file), RecordingMarks.read(file))
                }
                .sortedByDescending { it.createdAt }
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch(Dispatchers.Main.immediate) {
            while (isActive) {
                val active = _state.value as? RecorderState.Active ?: break
                if (!active.paused) {
                    val amplitude = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                    levels.addLast(RecordingNames.level(amplitude))
                    while (levels.size > MAX_LEVELS) levels.removeFirst()
                    _state.value = active.copy(elapsedMs = elapsedMs(active), levels = levels.toList())
                }
                delay(TICK_MS)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun elapsedMs(state: RecorderState.Active): Long =
        if (state.paused) accumulatedMs else accumulatedMs + SystemClock.elapsedRealtime() - resumedAt

    private fun durationOf(file: File): Long = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        }
    }.getOrDefault(0L)

    private fun newMediaRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

    private companion object {
        const val TAG = "Recorder"
        const val SAMPLE_RATE = 44_100
        const val BIT_RATE = 64_000 // ≈ 29 MB per hour, plenty for speech
        const val TICK_MS = 100L
        const val MAX_LEVELS = 150
        const val MIN_DURATION_MS = 500L
        const val MIN_MARK_GAP_MS = 1_000L
        const val MIN_FREE_SPACE = 50L * 1024 * 1024
    }
}

/** Marks of a recording live next to it, in "rec_….aac.marks": one position in ms per line. */
object RecordingMarks {

    private const val SUFFIX = ".marks"

    fun file(audio: File): File = File(audio.path + SUFFIX)

    fun read(audio: File): List<Long> = runCatching {
        file(audio).takeIf { it.isFile }?.readLines()?.mapNotNull { it.trim().toLongOrNull() }?.sorted()
    }.getOrNull().orEmpty()

    fun append(audio: File, positionMs: Long) {
        runCatching { file(audio).appendText("$positionMs\n") }.onFailure { Log.w("RecordingMarks", "Cannot save a mark", it) }
    }

    fun delete(audio: File) {
        file(audio).delete()
    }
}

object RecordingNames {

    private const val PREFIX = "rec_"
    private const val EXTENSION = ".aac"

    fun fileName(createdAt: Long): String = "$PREFIX$createdAt$EXTENSION"

    fun createdAt(file: File): Long? =
        file.name.takeIf { it.startsWith(PREFIX) && it.endsWith(EXTENSION) }
            ?.removePrefix(PREFIX)?.removeSuffix(EXTENSION)?.toLongOrNull()

    /** "Запись 27.10.2026 18:30.aac": becomes the transcript title "Запись 27.10.2026 18:30". */
    fun displayName(createdAt: Long): String =
        "Запись " + SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.forLanguageTag("ru")).format(Date(createdAt)) + EXTENSION

    /** Microphone amplitude (0..32767) → level 0..1 on a 60 dB scale. */
    fun level(amplitude: Int): Float {
        if (amplitude <= 0) return 0f
        val db = 20 * log10(amplitude / 32767.0)
        return ((db + 60) / 60).toFloat().coerceIn(0f, 1f)
    }
}
