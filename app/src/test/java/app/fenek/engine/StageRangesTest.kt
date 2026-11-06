package app.fenek.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StageRangesTest {

    private val order = listOf(
        Stage.PREPARING, Stage.DECODING, Stage.DIARIZING, Stage.TRANSCRIBING,
        Stage.CORRECTING, Stage.SUMMARIZING, Stage.SAVING,
    )

    private fun check(withLlm: Boolean) {
        val ranges = StageRanges.of(withLlm)
        val used = order.filter { it in ranges }
        assertEquals(0f, ranges.getValue(used.first()).first, 0f)
        assertEquals(1f, ranges.getValue(used.last()).second, 0f)
        // Every stage starts where the previous one ended, so progress never goes back or jumps.
        used.zipWithNext().forEach { (a, b) ->
            assertEquals("$a -> $b", ranges.getValue(a).second, ranges.getValue(b).first, 1e-6f)
        }
        for ((stage, range) in ranges) assertTrue("$stage", range.first < range.second)
    }

    @Test
    fun withoutTheLanguageModelTheRangesAreTheOriginalOnes() {
        check(withLlm = false)
        val ranges = StageRanges.of(false)
        assertEquals(0.02f to 0.12f, ranges[Stage.DECODING])
        assertEquals(0.12f to 0.55f, ranges[Stage.DIARIZING])
        assertEquals(0.55f to 0.99f, ranges[Stage.TRANSCRIBING])
        assertEquals(0.99f to 1f, ranges[Stage.SAVING])
        assertFalse(Stage.CORRECTING in ranges)
        assertFalse(Stage.SUMMARIZING in ranges)
    }

    @Test
    fun withTheLanguageModelTheTwoStagesFollowRecognition() {
        check(withLlm = true)
        val ranges = StageRanges.of(true)
        assertEquals(0.35f to 0.65f, ranges[Stage.TRANSCRIBING])
        assertEquals(0.65f to 0.88f, ranges[Stage.CORRECTING])
        assertEquals(0.88f to 0.98f, ranges[Stage.SUMMARIZING])
        assertEquals(0.98f to 1f, ranges[Stage.SAVING])
    }

    @Test
    fun overallProgressIsMonotonicAcrossAWholeJobInBothLayouts() {
        for (withLlm in listOf(false, true)) {
            val ranges = StageRanges.of(withLlm)
            var last = -1f
            for (stage in order.filter { it in ranges }) {
                for (step in 0..10) {
                    val value = StageRanges.overall(ranges, stage, step / 10f)
                    assertTrue("$withLlm $stage $step: $value < $last", value >= last - 1e-6f)
                    last = value
                }
            }
            assertEquals(1f, last, 1e-6f)
        }
    }

    @Test
    fun aSkippedStageDoesNotMakeProgressGoBackwards() {
        // Summary only: correction is not run, progress goes from the end of recognition to the summary.
        val ranges = StageRanges.of(true)
        val afterRecognition = StageRanges.overall(ranges, Stage.TRANSCRIBING, 1f)
        val summaryStart = StageRanges.overall(ranges, Stage.SUMMARIZING, 0f)
        assertTrue(summaryStart >= afterRecognition)
    }

    @Test
    fun fractionsOutsideZeroToOneAreClamped() {
        val ranges = StageRanges.of(true)
        assertEquals(0.65f, StageRanges.overall(ranges, Stage.CORRECTING, -3f), 1e-6f)
        assertEquals(0.88f, StageRanges.overall(ranges, Stage.CORRECTING, 7f), 1e-6f)
    }
}
