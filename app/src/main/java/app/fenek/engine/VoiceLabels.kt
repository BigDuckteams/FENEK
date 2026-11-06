package app.fenek.engine

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Turns the participant of every voice window into labelled speech stretches covering all the
 * speech the segmentation model found.
 */
object VoiceLabels {

    private const val FRAME = 0.01

    /**
     * @param segments speech found by the segmentation model; `speaker` is its local speaker
     * @param runs clean runs the windows were cut from
     * @param windows voice windows in order, [labels] holds their participants
     * @return non-overlapping stretches with participants; windows decide where they exist, the
     *   rest of the speech (short phrases, overlapping voices) takes the participant that its
     *   local speaker mostly was, or the nearest labelled stretch in time
     */
    fun assemble(
        segments: List<SpeakerSegment>,
        runs: List<VoiceWindows.Run>,
        windows: List<VoiceWindows.Window>,
        labels: IntArray,
        total: Double,
    ): List<SpeakerSegment> {
        val smoothed = smooth(windows, labels)
        val stretches = windowStretches(runs, windows, smoothed)

        val localVotes = HashMap<Int, HashMap<Int, Double>>()
        for ((i, window) in windows.withIndex()) {
            val local = runs[window.run].local
            val votes = localVotes.getOrPut(local) { HashMap() }
            votes[smoothed[i]] = (votes[smoothed[i]] ?: 0.0) + window.duration
        }
        val localLabel = localVotes.mapValues { (_, votes) -> votes.maxBy { it.value }.key }

        val covered = windows.map { it.run }.toSet()
        val coarse = ArrayList<SpeakerSegment>()
        for ((index, run) in runs.withIndex()) {
            if (index in covered) continue
            coarse += SpeakerSegment(run.start, run.end, localLabel[run.local] ?: nearest(stretches, (run.start + run.end) / 2))
        }
        for (s in segments) {
            coarse += SpeakerSegment(s.start, s.end, localLabel[s.speaker] ?: nearest(stretches, (s.start + s.end) / 2))
        }
        return paint(total, coarse, stretches)
    }

    /** A single window that disagrees with both neighbours of its run takes their participant. */
    internal fun smooth(windows: List<VoiceWindows.Window>, labels: IntArray): IntArray {
        val groups = IntArray(windows.size) { windows[it].run }
        return Clustering.smooth(labels, groups)
    }

    /** Neighbouring windows of one run share their overlap halfway; equal participants are joined. */
    internal fun windowStretches(
        runs: List<VoiceWindows.Run>,
        windows: List<VoiceWindows.Window>,
        labels: IntArray,
    ): List<SpeakerSegment> {
        val byRun = HashMap<Int, MutableList<Int>>()
        for ((i, w) in windows.withIndex()) byRun.getOrPut(w.run) { ArrayList() } += i
        val result = ArrayList<SpeakerSegment>()
        for ((index, run) in runs.withIndex()) {
            val own = byRun[index] ?: continue
            for ((j, w) in own.withIndex()) {
                val window = windows[w]
                val from = if (j == 0) run.start else (windows[own[j - 1]].end + window.start) / 2
                val to = if (j == own.lastIndex) run.end else (window.end + windows[own[j + 1]].start) / 2
                val last = result.lastOrNull()
                if (last != null && last.speaker == labels[w] && abs(from - last.end) < 1e-6) {
                    result[result.size - 1] = last.copy(end = to)
                } else {
                    result += SpeakerSegment(from, to, labels[w])
                }
            }
        }
        return result
    }

    private fun nearest(stretches: List<SpeakerSegment>, time: Double): Int {
        if (stretches.isEmpty()) return 0
        return stretches.minBy {
            if (time in it.start..it.end) 0.0 else minOf(abs(it.start - time), abs(it.end - time))
        }.speaker
    }

    /** Draws [coarse] and then [exact] stretches on a 10 ms timeline: later drawings win. */
    internal fun paint(total: Double, coarse: List<SpeakerSegment>, exact: List<SpeakerSegment>): List<SpeakerSegment> {
        val frames = ceil(total / FRAME).toInt() + 2
        val timeline = IntArray(frames) { -1 }
        for (layer in listOf(coarse.sortedBy { it.start }, exact.sortedBy { it.start })) {
            for (s in layer) {
                val from = (s.start / FRAME).roundToInt().coerceIn(0, frames)
                val to = (s.end / FRAME).roundToInt().coerceIn(from, frames)
                for (f in from until to) timeline[f] = s.speaker
            }
        }
        val result = ArrayList<SpeakerSegment>()
        var i = 0
        while (i < frames) {
            if (timeline[i] < 0) {
                i++
                continue
            }
            var j = i
            while (j < frames && timeline[j] == timeline[i]) j++
            result += SpeakerSegment(i * FRAME, j * FRAME, timeline[i])
            i = j
        }
        return result
    }
}
