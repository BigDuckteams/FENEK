package app.fenek.llm

import app.fenek.data.Segment
import app.fenek.data.Transcript
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LlmPostProcessorTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val transcript = Transcript(
        id = "t1", title = "Встреча", createdAt = 0, durationMs = 20_000, sourceName = "m.m4a",
        asrModelId = "gigaam", asrModelTitle = "GigaAM", processingMs = 5_000,
        segments = listOf(
            Segment(0, 0, 4_000, "добрый день обсудим релиз"),
            Segment(1, 4_500, 8_000, "я подготовлю отчёт к пятнице"),
            Segment(0, 8_500, 12_000, "договорились жду отчёт"),
        ),
    )

    private fun plan(correct: Boolean = true, highlights: Boolean = true) =
        LlmPlan("qwen", "/models/qwen.gguf", modelBytes = 1_000, correctText = correct, writeHighlights = highlights)

    private val plenty: () -> Long = { Long.MAX_VALUE }

    private val numbered = Regex("^(\\d+): (.+)$")

    /** Answers a correction request or a highlights request, depending on the grammar. */
    private fun model(user: String, grammar: String?): String =
        if (grammar != null) {
            """{"summary":[{"phrase":1,"text":"Обсудили релиз."}],"tasks":[{"phrase":2,"text":"Подготовить отчёт к пятнице."}]}"""
        } else {
            user.lines().mapNotNull { numbered.matchEntire(it) }
                .joinToString("\n") { "${it.groupValues[1]}: ${it.groupValues[2].replaceFirstChar(Char::uppercase)}." }
        }

    @Test
    fun correctsTextAndWritesHighlightsAndReleasesTheModel() = runBlocking {
        var created: FakeLlmEngine? = null
        val file = File(folder.root, "llm.running")
        val marker = LlmCrashMarker(file)
        val events = ArrayList<String>()
        val loader = LlmLoader { _, threads ->
            events += "load(marker=${file.exists()},threads=$threads)"
            FakeLlmEngine { _, user, grammar -> model(user, grammar) }.also { created = it }
        }
        val result = LlmPostProcessor(loader, marker, plenty).process(transcript, plan(), threads = 3)

        assertEquals(listOf("load(marker=true,threads=3)"), events)
        assertTrue(created!!.closed)
        assertFalse(file.exists())
        assertTrue(result.textCorrected)
        assertEquals("qwen", result.llmModelId)
        assertEquals("Добрый день обсудим релиз.", result.segments[0].text)
        assertEquals("добрый день обсудим релиз", result.segments[0].rawText)
        assertEquals("Обсудили релиз.", result.llmSummary!!.single().text)
        assertEquals(4_500L, result.llmTasks!!.single().startMs)
        assertEquals(transcript.segments.map { it.startMs }, result.segments.map { it.startMs })
    }

    @Test
    fun onlyHighlightsLeavesThePhraseTextAsRecognized() = runBlocking {
        val loader = LlmLoader { _, _ -> FakeLlmEngine { _, user, grammar -> model(user, grammar) } }
        val result = LlmPostProcessor(loader, LlmCrashMarker(File(folder.root, "m")), plenty)
            .process(transcript, plan(correct = false), threads = 2)

        assertEquals(transcript.segments, result.segments)
        assertFalse(result.textCorrected)
        assertTrue(result.llmSummary != null)
    }

    @Test
    fun aModelThatThrowsLeavesTheRecognizedTranscriptAndIsStillReleased() = runBlocking {
        var created: FakeLlmEngine? = null
        val file = File(folder.root, "llm.running")
        val loader = LlmLoader { _, _ -> FakeLlmEngine { _, _, _ -> throw IllegalStateException("boom") }.also { created = it } }
        val result = LlmPostProcessor(loader, LlmCrashMarker(file), plenty).process(transcript, plan(), threads = 2)

        assertEquals(transcript.segments, result.segments)
        assertFalse(result.textCorrected)
        assertNull(result.llmSummary)
        assertTrue(created!!.closed)
        assertFalse(file.exists())
    }

    @Test
    fun aModelThatFailsHalfwayKeepsTheCorrectionAlreadyDone() = runBlocking {
        val loader = LlmLoader { _, _ ->
            FakeLlmEngine { _, user, grammar -> if (grammar != null) throw OutOfMemoryError("no memory") else model(user, grammar) }
        }
        val result = LlmPostProcessor(loader, LlmCrashMarker(File(folder.root, "m")), plenty).process(transcript, plan(), threads = 2)

        assertTrue(result.textCorrected)
        assertNull(result.llmSummary)
    }

    @Test
    fun aModelThatDoesNotLoadGivesTheTranscriptBackAndClearsTheMarker() = runBlocking {
        val file = File(folder.root, "llm.running")
        val loader = LlmLoader { _, _ -> throw LlmException("no model") }
        val result = LlmPostProcessor(loader, LlmCrashMarker(file), plenty).process(transcript, plan(), threads = 2)

        assertSame(transcript, result)
        assertFalse(file.exists())
    }

    @Test
    fun tooLittleMemorySkipsTheModelWithoutLoadingIt() = runBlocking {
        var loaded = false
        val file = File(folder.root, "llm.running")
        val loader = LlmLoader { _, _ -> loaded = true; FakeLlmEngine { _, _, _ -> "" } }
        val result = LlmPostProcessor(loader, LlmCrashMarker(file), availableMemory = { 1_000 }).process(transcript, plan(), threads = 2)

        assertSame(transcript, result)
        assertFalse(loaded)
        assertFalse(file.exists())
    }

    @Test
    fun bothModesOffDoNotTouchTheModel() = runBlocking {
        var loaded = false
        val loader = LlmLoader { _, _ -> loaded = true; FakeLlmEngine { _, _, _ -> "" } }
        val result = LlmPostProcessor(loader, LlmCrashMarker(File(folder.root, "m")), plenty)
            .process(transcript, plan(correct = false, highlights = false), threads = 2)

        assertSame(transcript, result)
        assertFalse(loaded)
    }

    @Test
    fun cancellationIsPassedOnAfterTheModelIsReleased() {
        var created: FakeLlmEngine? = null
        val file = File(folder.root, "llm.running")
        val loader = LlmLoader { _, _ -> FakeLlmEngine { _, _, _ -> throw CancellationException("cancelled") }.also { created = it } }
        try {
            runBlocking { LlmPostProcessor(loader, LlmCrashMarker(file), plenty).process(transcript, plan(), threads = 2) }
            fail("cancellation must propagate")
        } catch (e: CancellationException) {
            // expected
        }
        assertTrue(created!!.closed)
        assertFalse(file.exists())
    }

    @Test
    fun progressNamesTheStagesInOrder() = runBlocking {
        val loader = LlmLoader { _, _ -> FakeLlmEngine { _, user, grammar -> model(user, grammar) } }
        val seen = ArrayList<Pair<LlmStage, Float>>()
        LlmPostProcessor(loader, LlmCrashMarker(File(folder.root, "m")), plenty)
            .process(transcript, plan(), threads = 2) { stage, fraction, _, _ -> seen += stage to fraction }

        assertEquals(LlmStage.CORRECTING, seen.first().first)
        assertEquals(LlmStage.SUMMARIZING, seen.last().first)
        assertEquals(1f, seen.last().second, 0f)
        assertTrue(seen.map { it.second }.windowed(2).filter { it[0] != it[1] }.size > 1)
    }
}
