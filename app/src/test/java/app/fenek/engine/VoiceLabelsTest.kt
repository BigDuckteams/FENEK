package app.fenek.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceLabelsTest {

    private fun run(start: Double, end: Double, local: Int) = VoiceWindows.Run(start, end, local)
    private fun window(start: Double, end: Double, run: Int) = VoiceWindows.Window(start, end, run)
    private fun seg(start: Double, end: Double, speaker: Int) = SpeakerSegment(start, end, speaker)

    @Test
    fun aSingleDisagreeingWindowTakesTheVoiceOfItsNeighbours() {
        val windows = List(5) { window(it.toDouble(), it + 2.0, 0) }
        assertArrayEquals(intArrayOf(0, 0, 0, 0, 0), VoiceLabels.smooth(windows, intArrayOf(0, 0, 1, 0, 0)))
        // Across two stretches of speech nothing is smoothed.
        val two = listOf(window(0.0, 2.0, 0), window(3.0, 5.0, 1), window(4.0, 6.0, 1))
        assertArrayEquals(intArrayOf(0, 1, 1), VoiceLabels.smooth(two, intArrayOf(0, 1, 1)))
    }

    @Test
    fun aChangeOfVoiceInsideARunIsPlacedHalfwayBetweenWindows() {
        val runs = listOf(run(0.0, 6.0, 0))
        val windows = listOf(window(0.0, 2.0, 0), window(1.0, 3.0, 0), window(3.0, 5.0, 0), window(4.0, 6.0, 0))
        val stretches = VoiceLabels.windowStretches(runs, windows, intArrayOf(0, 0, 1, 1))
        assertEquals(listOf(0 to 0.0, 1 to 3.0), stretches.map { it.speaker to it.start })
        assertEquals(3.0, stretches[0].end, 1e-9)
        assertEquals(6.0, stretches[1].end, 1e-9)
    }

    @Test
    fun paintedStretchesDoNotOverlapAndExactOnesWin() {
        val result = VoiceLabels.paint(
            total = 10.0,
            coarse = listOf(seg(0.0, 10.0, 7)),
            exact = listOf(seg(2.0, 4.0, 1), seg(4.0, 6.0, 2)),
        )
        assertEquals(listOf(7 to 0.0, 1 to 2.0, 2 to 4.0, 7 to 6.0), result.map { it.speaker to it.start })
        assertEquals(10.0, result.last().end, 0.02)
    }

    @Test
    fun shortPhrasesTakeTheVoiceTheirLocalSpeakerMostlyHad() {
        // Local speaker 5 is voice 1 by its windows; its short phrase at 8..8.6 has none of its own.
        val segments = listOf(seg(0.0, 6.0, 5), seg(7.0, 7.2, 9), seg(8.0, 8.6, 5))
        val runs = listOf(run(0.0, 6.0, 5), run(8.0, 8.6, 5))
        val windows = listOf(window(0.0, 2.0, 0), window(1.0, 3.0, 0), window(2.0, 4.0, 0), window(4.0, 6.0, 0))
        val result = VoiceLabels.assemble(segments, runs, windows, intArrayOf(1, 1, 1, 1), total = 10.0)
        assertEquals(listOf(1, 1, 1), result.map { it.speaker })
        assertEquals(8.6, result.last().end, 0.02)
        // Local speaker 9 never had a window: it takes the nearest labelled stretch (voice 1).
        assertEquals(7.0, result[1].start, 0.02)
    }

    @Test
    fun withoutAnyWindowEverythingIsOneVoice() {
        val result = VoiceLabels.assemble(
            segments = listOf(seg(0.0, 1.0, 3), seg(2.0, 2.5, 4)),
            runs = emptyList(),
            windows = emptyList(),
            labels = IntArray(0),
            total = 5.0,
        )
        assertEquals(setOf(0), result.map { it.speaker }.toSet())
        assertEquals(2, result.size)
    }
}
