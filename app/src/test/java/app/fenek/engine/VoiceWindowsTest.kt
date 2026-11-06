package app.fenek.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceWindowsTest {

    private fun seg(start: Double, end: Double, speaker: Int) = SpeakerSegment(start, end, speaker)

    @Test
    fun overlappingSpeechIsNotCleanSpeech() {
        val runs = VoiceWindows.cleanRuns(
            listOf(seg(0.0, 5.0, 0), seg(4.0, 8.0, 1)),
            total = 10.0,
        )
        assertEquals(listOf(0 to 4.0, 1 to 8.0), runs.map { it.local to it.end })
        assertEquals(0.0, runs[0].start, 0.011)
        // The overlap 4..5 belongs to nobody.
        assertEquals(5.0, runs[1].start, 0.011)
    }

    @Test
    fun shortGapOfTheSameVoiceIsBridged() {
        val bridged = VoiceWindows.cleanRuns(listOf(seg(0.0, 3.0, 0), seg(3.2, 6.0, 0)), total = 7.0)
        assertEquals(1, bridged.size)
        assertEquals(6.0, bridged[0].end, 0.011)

        val apart = VoiceWindows.cleanRuns(listOf(seg(0.0, 3.0, 0), seg(3.6, 6.0, 0)), total = 7.0)
        assertEquals(2, apart.size)
    }

    @Test
    fun aVoiceChangeWithoutAGapSplitsTheRun() {
        val runs = VoiceWindows.cleanRuns(listOf(seg(0.0, 3.0, 0), seg(3.0, 6.0, 1)), total = 7.0)
        assertEquals(listOf(0, 1), runs.map { it.local })
    }

    @Test
    fun longRunsGetOverlappingWindows() {
        val windows = VoiceWindows.windows(listOf(VoiceWindows.Run(10.0, 15.0, 0)))
        assertEquals(listOf(10.0, 11.0, 12.0, 13.0), windows.map { it.start })
        assertTrue(windows.all { it.duration == VoiceWindows.WINDOW_SECONDS })
        assertTrue(windows.all { it.run == 0 })
    }

    @Test
    fun theTailOfARunGetsItsOwnWindow() {
        val windows = VoiceWindows.windows(listOf(VoiceWindows.Run(0.0, 4.7, 0)))
        assertEquals(listOf(0.0, 1.0, 2.0), windows.take(3).map { it.start })
        assertEquals(4.7, windows.last().end, 1e-9)
        assertEquals(VoiceWindows.WINDOW_SECONDS, windows.last().duration, 1e-9)
    }

    @Test
    fun shortRunsGiveOneShortWindowOrNone() {
        val windows = VoiceWindows.windows(
            listOf(VoiceWindows.Run(0.0, 2.3, 0), VoiceWindows.Run(5.0, 5.5, 1), VoiceWindows.Run(8.0, 9.0, 2)),
        )
        assertEquals(2, windows.size)
        assertEquals(2.3, windows[0].duration, 1e-9)
        assertEquals(0, windows[0].run)
        assertEquals(2, windows[1].run)
    }
}
