package app.fenek.engine

import kotlin.math.ceil
import kotlin.math.roundToInt

/** A stretch of speech attributed to one speaker; times in seconds. */
data class SpeakerSegment(val start: Double, val end: Double, val speaker: Int) {
    val duration: Double get() = end - start
}

/** Turns raw diarization output into a clean sequence of speaker turns. */
object TurnBuilder {

    private const val FRAME = 0.02

    private class Run(var speaker: Int, val start: Int, var end: Int) {
        val length: Int get() = end - start
    }

    /**
     * Converts possibly overlapping diarization segments into non-overlapping turns:
     *  - where voices overlap, the speaker who already holds the floor keeps it;
     *  - flickers shorter than [minFlip] seconds between speakers go to a neighbour;
     *  - pauses shorter than [maxPause] seconds inside one speaker's speech are bridged.
     */
    fun build(
        segments: List<SpeakerSegment>,
        maxPause: Double = 1.0,
        minFlip: Double = 0.3,
    ): List<SpeakerSegment> {
        val sorted = segments.filter { it.end > it.start }.sortedBy { it.start }
        if (sorted.isEmpty()) return emptyList()
        val frames = ceil(sorted.maxOf { it.end } / FRAME).toInt() + 1
        val labels = IntArray(frames) { -1 }
        val active = ArrayList<SpeakerSegment>()
        var next = 0
        for (f in 0 until frames) {
            val t = (f + 0.5) * FRAME
            while (next < sorted.size && sorted[next].start <= t) active += sorted[next++]
            active.removeAll { it.end <= t }
            if (active.isEmpty()) continue
            val previous = if (f > 0) labels[f - 1] else -1
            labels[f] = if (active.any { it.speaker == previous }) previous else active.minBy { it.start }.speaker
        }

        val runs = toRuns(labels)
        absorbFlickers(runs, (minFlip / FRAME).roundToInt())
        bridgePauses(runs, (maxPause / FRAME).roundToInt())
        return runs.filter { it.speaker >= 0 }.map { SpeakerSegment(it.start * FRAME, it.end * FRAME, it.speaker) }
    }

    /**
     * Splits turns longer than [maxLength] seconds (recognition models accept ~25-30 s)
     * at the quietest moment in the last 40 % of each window, found by [quietPoint].
     */
    fun splitLongTurns(
        turns: List<SpeakerSegment>,
        maxLength: Double,
        quietPoint: (from: Double, to: Double) -> Double,
    ): List<SpeakerSegment> {
        val result = ArrayList<SpeakerSegment>()
        for (turn in turns) {
            var start = turn.start
            while (turn.end - start > maxLength) {
                val lo = start + maxLength * 0.6
                val hi = start + maxLength
                val cut = quietPoint(lo, hi).coerceIn(lo, hi)
                result += SpeakerSegment(start, cut, turn.speaker)
                start = cut
            }
            result += SpeakerSegment(start, turn.end, turn.speaker)
        }
        return result
    }

    private fun toRuns(labels: IntArray): MutableList<Run> {
        val runs = ArrayList<Run>()
        var start = 0
        for (i in 1..labels.size) {
            if (i == labels.size || labels[i] != labels[start]) {
                runs += Run(labels[start], start, i)
                start = i
            }
        }
        return runs
    }

    private fun absorbFlickers(runs: MutableList<Run>, minFrames: Int) {
        while (true) {
            var shortest = -1
            for (i in runs.indices) {
                val run = runs[i]
                if (run.speaker < 0 || run.length >= minFrames) continue
                val touchesSpeech = runs.getOrNull(i - 1)?.speaker?.let { it >= 0 } == true ||
                    runs.getOrNull(i + 1)?.speaker?.let { it >= 0 } == true
                if (touchesSpeech && (shortest < 0 || run.length < runs[shortest].length)) shortest = i
            }
            if (shortest < 0) return
            val left = runs.getOrNull(shortest - 1)?.takeIf { it.speaker >= 0 }
            val right = runs.getOrNull(shortest + 1)?.takeIf { it.speaker >= 0 }
            runs[shortest].speaker = when {
                left == null -> right!!.speaker
                right == null -> left.speaker
                left.speaker == right.speaker -> left.speaker
                left.length >= right.length -> left.speaker
                else -> right.speaker
            }
            coalesce(runs)
        }
    }

    private fun bridgePauses(runs: MutableList<Run>, maxFrames: Int) {
        for (i in 1 until runs.size - 1) {
            val run = runs[i]
            if (run.speaker < 0 && run.length < maxFrames && runs[i - 1].speaker >= 0 &&
                runs[i - 1].speaker == runs[i + 1].speaker
            ) {
                run.speaker = runs[i - 1].speaker
            }
        }
        coalesce(runs)
    }

    private fun coalesce(runs: MutableList<Run>) {
        var i = 0
        while (i < runs.size - 1) {
            if (runs[i].speaker == runs[i + 1].speaker) {
                runs[i].end = runs[i + 1].end
                runs.removeAt(i + 1)
            } else {
                i++
            }
        }
    }
}
