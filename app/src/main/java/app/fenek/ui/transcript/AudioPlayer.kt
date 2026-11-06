package app.fenek.ui.transcript

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerState(
    val ready: Boolean = false,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val error: String? = null,
    val speed: Float = 1f,
)

/** Plays the original recording so that any phrase can be listened to. Main thread only. */
class AudioPlayer(private val context: Context) {

    private companion object {
        val SPEEDS = listOf(1f, 1.25f, 1.5f, 2f, 0.75f)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null
    private var ticker: Job? = null
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    /** [source] is a content:// URI or an absolute file path. */
    fun load(source: String) {
        releasePlayer()
        val mp = MediaPlayer()
        try {
            if (source.startsWith("/")) mp.setDataSource(source) else mp.setDataSource(context, Uri.parse(source))
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            mp.setOnPreparedListener {
                _state.update { s -> PlayerState(ready = true, durationMs = it.duration.toLong(), speed = s.speed) }
            }
            mp.setOnCompletionListener {
                stopTicker()
                _state.update { s -> s.copy(playing = false, positionMs = s.durationMs) }
            }
            mp.setOnErrorListener { _, _, _ ->
                stopTicker()
                _state.value = PlayerState(error = "Не удалось воспроизвести запись")
                true
            }
            mp.prepareAsync()
            player = mp
            _state.value = PlayerState()
        } catch (e: Exception) {
            mp.release()
            _state.value = PlayerState(error = "Исходная запись недоступна (файл перемещён или удалён)")
        }
    }

    fun playFrom(positionMs: Long) {
        val mp = player ?: return
        if (!_state.value.ready) return
        mp.seekTo(positionMs, MediaPlayer.SEEK_CLOSEST)
        mp.start()
        applySpeed(mp)
        _state.update { it.copy(playing = true, positionMs = positionMs) }
        startTicker()
    }

    /** 1× → 1.25× → 1.5× → 2× → 0.75× → 1×. */
    fun cycleSpeed() {
        val next = SPEEDS[(SPEEDS.indexOf(_state.value.speed) + 1) % SPEEDS.size]
        _state.update { it.copy(speed = next) }
        // Changing the speed of a paused MediaPlayer starts it on some devices.
        player?.let { if (it.isPlaying) applySpeed(it) }
    }

    private fun applySpeed(mp: MediaPlayer) {
        runCatching { mp.playbackParams = mp.playbackParams.setSpeed(_state.value.speed) }
    }

    fun toggle() {
        val mp = player ?: return
        if (!_state.value.ready) return
        if (mp.isPlaying) {
            mp.pause()
            stopTicker()
            _state.update { it.copy(playing = false, positionMs = mp.currentPosition.toLong()) }
        } else {
            val position = _state.value.positionMs
            if (position >= _state.value.durationMs) mp.seekTo(0)
            mp.start()
            applySpeed(mp)
            _state.update { it.copy(playing = true) }
            startTicker()
        }
    }

    fun seekTo(positionMs: Long) {
        val mp = player ?: return
        if (!_state.value.ready) return
        mp.seekTo(positionMs, MediaPlayer.SEEK_CLOSEST)
        _state.update { it.copy(positionMs = positionMs) }
    }

    fun pause() {
        val mp = player ?: return
        if (_state.value.ready && mp.isPlaying) toggle()
    }

    fun release() {
        releasePlayer()
        scope.cancel()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val mp = player ?: break
                _state.update { it.copy(positionMs = mp.currentPosition.toLong()) }
                delay(200)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun releasePlayer() {
        stopTicker()
        player?.release()
        player = null
    }
}
