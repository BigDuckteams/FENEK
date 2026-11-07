package app.fenek.llm

import app.fenek.data.Segment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmBudgetTest {

    private var clock = 0L

    private fun budget(totalMs: Long) = LlmBudget(totalMs) { clock }

    @Test
    fun theBudgetFollowsTheRecordingWithinTheLimits() {
        fun total(recordingMs: Long) = LlmBudget.forRecording(recordingMs) { clock }.totalMs
        assertEquals(LlmBudget.MIN_TOTAL_MS, total(30_000))
        assertEquals(300_000L, total(600_000))
        assertEquals(LlmBudget.MAX_TOTAL_MS, total(3_600_000))
    }

    @Test
    fun aRequestGetsWhatIsLeftAndNothingWhenTooLittleIs() {
        val budget = budget(100_000)
        assertEquals(60_000L, budget.request(60_000))
        clock = 70_000
        assertEquals(30_000L, budget.request(60_000))
        clock = 90_000
        assertNull(budget.request(60_000))
    }

    @Test
    fun aStagePartLeavesTheRestForTheNextStage() {
        val budget = budget(100_000)
        val part = budget.part(0.5)
        clock = 50_000
        assertEquals(0L, part.remainingMs)
        assertEquals(50_000L, budget.remainingMs)
    }

    @Test
    fun windowsAreAskedFirstLastAndThenTheMiddleOfEveryGap() {
        val writer = HighlightsWriter(FakeLlmEngine { _, _, _ -> "" })
        assertEquals(listOf(0, 4, 2, 1, 3), writer.coverageOrder(5))
        assertEquals(listOf(0, 1), writer.coverageOrder(2))
        assertEquals(emptyList<Int>(), writer.coverageOrder(0))
        assertEquals((0 until 17).toList(), writer.coverageOrder(17).sorted())
    }

    @Test
    fun theSummaryStopsWhenTheBudgetIsSpentAndKeepsWhatItHas() = runBlocking {
        val long = (0 until 40).map { Segment(it % 2, it * 5_000L, it * 5_000L + 4_000, "Реплика номер $it про разные дела и планы.") }
        // Every request takes 45 s of the 100 s budget: after two of them 10 s are left, less than a request needs.
        val engine = FakeLlmEngine { _, user, _ ->
            clock += 45_000
            val first = user.lineSequence().first().substringBefore(' ').toInt()
            """{"summary":[{"phrase":$first,"text":"Пункт $first."}],"tasks":[]}"""
        }
        val result = HighlightsWriter(engine, windowChars = 300, budget = budget(100_000)).write(long) { "Спикер ${it + 1}" }

        assertEquals(listOf(100_000L, 55_000L), engine.requests.map { it.timeoutMs })
        // The first and the last window were asked, so the summary covers the whole conversation.
        assertTrue(engine.requests.first().user.startsWith("1 ["))
        assertTrue(engine.requests.last().user.lines().last().startsWith("40 ["))
        assertNotNull(result)
        assertEquals(2, result!!.summary.size)
    }
}
