package app.fenek.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * Lets a running job be paused without losing what it has done. The pipeline calls [checkpoint] at
 * safe points (between stages, between pieces of speaker detection, between phrases): it returns at
 * once, unless a pause was asked for, and then it waits there until [resume]. A job that is cancelled
 * while it waits stops at that point.
 *
 * @param onParked called on the job's thread when it has reached a checkpoint and stopped
 */
class PauseGate(private val onParked: () -> Unit = {}) {

    private val paused = MutableStateFlow(false)

    /** A pause was asked for and has not been undone yet. */
    val pauseRequested: Boolean get() = paused.value

    fun pause() {
        paused.value = true
    }

    fun resume() {
        paused.value = false
    }

    suspend fun checkpoint() {
        if (!paused.value) return
        onParked()
        paused.first { !it }
    }
}
