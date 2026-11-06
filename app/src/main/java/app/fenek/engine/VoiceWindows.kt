package app.fenek.engine

import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Cuts the speech found by the segmentation model into short windows of one voice. Every window
 * gets its own voice fingerprint, and the fingerprints of the whole recording are clustered
 * together, so the speaker of a phrase never depends on how the segmentation model happened to
 * number its local speakers.
 */
object VoiceWindows {

    /** Length of a window: short enough to follow a conversation, long enough for a reliable voice. */
    const val WINDOW_SECONDS = 2.0
    const val HOP_SECONDS = 1.0

    private const val MIN_RUN_SECONDS = 0.8
    private const val BRIDGE_SECONDS = 0.3
    private const val FRAME = 0.01

    /** A stretch of speech in which exactly one local speaker is active. */
    class Run(val start: Double, val end: Double, val local: Int) {
        val duration: Double get() = end - start
    }

    class Window(val start: Double, val end: Double, val run: Int) {
        val duration: Double get() = end - start
    }

    /**
     * Stretches of [segments] where only one local speaker talks. Gaps of up to 0.3 s between
     * neighbouring stretches of the same local speaker are bridged.
     */
    fun cleanRuns(segments: List<SpeakerSegment>, total: Double): List<Run> {
        val frames = ceil(total / FRAME).toInt() + 2
        val count = IntArray(frames)
        val who = IntArray(frames) { -1 }
        for (s in segments) {
            val from = (s.start / FRAME).roundToInt().coerceIn(0, frames)
            val to = (s.end / FRAME).roundToInt().coerceIn(from, frames)
            for (f in from until to) {
                count[f]++
                who[f] = s.speaker
            }
        }
        val runs = ArrayList<Run>()
        var i = 0
        while (i < frames) {
            if (count[i] != 1) {
                i++
                continue
            }
            var j = i
            while (j < frames && count[j] == 1 && who[j] == who[i]) j++
            val start = i * FRAME
            val end = j * FRAME
            val last = runs.lastOrNull()
            if (last != null && last.local == who[i] && start - last.end <= BRIDGE_SECONDS) {
                runs[runs.size - 1] = Run(last.start, end, last.local)
            } else {
                runs += Run(start, end, who[i])
            }
            i = j
        }
        return runs
    }

    /** Windows of [WINDOW_SECONDS] with a step of [HOP_SECONDS]; a short run gives one shorter window. */
    fun windows(runs: List<Run>): List<Window> {
        val windows = ArrayList<Window>()
        for ((index, run) in runs.withIndex()) {
            if (run.duration < MIN_RUN_SECONDS) continue
            if (run.duration <= WINDOW_SECONDS * 1.25) {
                windows += Window(run.start, run.end, index)
                continue
            }
            var start = run.start
            while (start + WINDOW_SECONDS <= run.end + 1e-9) {
                windows += Window(start, start + WINDOW_SECONDS, index)
                start += HOP_SECONDS
            }
            // The tail that did not fit gets a window of its own, aligned to the end of the run.
            if (windows.last().end < run.end - HOP_SECONDS * 0.5) {
                windows += Window(run.end - WINDOW_SECONDS, run.end, index)
            }
        }
        return windows
    }
}
