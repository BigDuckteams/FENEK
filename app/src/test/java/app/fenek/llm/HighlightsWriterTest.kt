package app.fenek.llm

import app.fenek.data.LlmItem
import app.fenek.data.Segment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightsWriterTest {

    private val dialogue = listOf(
        Segment(0, 0, 4_000, "Добрый день, обсудим релиз."),
        Segment(1, 4_500, 8_000, "Я подготовлю отчёт к пятнице."),
        Segment(0, 8_500, 12_000, "Договорились, жду отчёт."),
        Segment(1, 12_500, 15_000, "Тогда релиз во вторник."),
    )

    private fun writer(engine: LlmEngine, windowChars: Int = 3_000) = HighlightsWriter(engine, windowChars)

    private val names: (Int) -> String = { "Спикер ${it + 1}" }

    @Test
    fun readsSummaryAndTasksAndPlacesThemByPhraseNumber() = runBlocking {
        val engine = FakeLlmEngine { _, _, _ ->
            """{"summary":[{"phrase":1,"text":"Обсудили релиз."}],"tasks":[{"phrase":2,"text":"Подготовить отчёт к пятнице."}]}"""
        }
        val result = writer(engine).write(dialogue, names)!!

        assertEquals(listOf(LlmItem(0, 0, "Обсудили релиз.")), result.summary)
        assertEquals(listOf(LlmItem(4_500, 1, "Подготовить отчёт к пятнице.")), result.tasks)
    }

    @Test
    fun theRequestListsPhrasesWithNumbersAndSpeakersAndUsesTheGrammar() = runBlocking {
        val engine = FakeLlmEngine { _, _, _ -> """{"summary":[],"tasks":[]}""" }
        writer(engine).write(dialogue, names)

        val request = engine.requests.single()
        assertTrue(request.user, request.user.startsWith("1 [Спикер 1]: Добрый день, обсудим релиз."))
        assertTrue(request.user.contains("2 [Спикер 2]: Я подготовлю отчёт к пятнице."))
        assertEquals(HighlightsWriter.GRAMMAR, request.grammar)
        assertTrue(request.maxTokens > 0)
    }

    @Test
    fun itemsThatPointToMissingPhrasesAreDropped() = runBlocking {
        val engine = FakeLlmEngine { _, _, _ ->
            """{"summary":[{"phrase":99,"text":"Выдуманный пункт."},{"phrase":0,"text":"Нулевая реплика."},
                {"phrase":3,"text":"Договорились об отчёте."}],"tasks":[{"phrase":-1,"text":"Задача"}]}"""
        }
        val result = writer(engine).write(dialogue, names)!!

        assertEquals(listOf("Договорились об отчёте."), result.summary.map { it.text })
        assertTrue(result.tasks.isEmpty())
    }

    @Test
    fun emptyOrBrokenAnswersGiveNothingSoTheRulesTakeOver() = runBlocking {
        assertNull(writer(FakeLlmEngine { _, _, _ -> """{"summary":[],"tasks":[]}""" }).write(dialogue, names))
        assertNull(writer(FakeLlmEngine { _, _, _ -> "не JSON вообще" }).write(dialogue, names))
        assertNull(writer(FakeLlmEngine { _, _, _ -> throw LlmTimeoutException("slow") }).write(dialogue, names))
        assertNull(writer(FakeLlmEngine { _, _, _ -> error("must not be called") }).write(dialogue.take(2), names))
    }

    @Test
    fun aLongConversationIsProcessedInWindowsAndMerged() = runBlocking {
        val long = (0 until 40).map { Segment(it % 2, it * 5_000L, it * 5_000L + 4_000, "Реплика номер $it про разные дела и планы.") }
        val engine = FakeLlmEngine { _, user, _ ->
            val first = user.lineSequence().first().substringBefore(' ').toInt()
            // Different words per window, as real answers have, so that nothing is taken for a repeat.
            val word = "${'а' + first % 26}${'а' + first / 26}ов"
            """{"summary":[{"phrase":$first,"text":"Пункт $word."}],"tasks":[{"phrase":$first,"text":"Задача $word."}]}"""
        }
        val windowWriter = writer(engine, windowChars = 300)
        val windows = windowWriter.windows(long)
        assertTrue(windows.size > 5)
        assertEquals(long.indices.toList(), windows.flatten())

        val result = windowWriter.write(long, names)!!
        assertEquals(windows.size, engine.requests.size)
        assertTrue(result.summary.size <= HighlightsWriter.MAX_SUMMARY)
        assertEquals(result.summary.sortedBy { it.startMs }, result.summary)
        assertEquals(windows.size, result.tasks.size)
    }

    @Test
    fun anItemFromAnotherWindowIsRejected() = runBlocking {
        val long = (0 until 20).map { Segment(0, it * 5_000L, it * 5_000L + 4_000, "Реплика номер $it про разные дела и планы.") }
        // The model always points to phrase 1, which is only inside the first window.
        val engine = FakeLlmEngine { _, _, _ -> """{"summary":[{"phrase":1,"text":"Про первую реплику."}],"tasks":[]}""" }
        val result = writer(engine, windowChars = 200).write(long, names)!!

        assertEquals(1, result.summary.size)
        assertTrue(engine.requests.size > 1)
    }

    @Test
    fun nearDuplicatesAreRemovedAndSummaryIsSpreadOverTheConversation() {
        val w = writer(FakeLlmEngine { _, _, _ -> "" })
        val items = listOf(
            LlmItem(0, 0, "Обсудили сроки релиза"),
            LlmItem(1, 0, "Обсудили сроки релиза."),
            LlmItem(2, 1, "Решили нанять тестировщика"),
        )
        assertEquals(2, w.dedupe(items).size)

        val many = (0 until 12).map { LlmItem(it * 1_000L, 0, "пункт $it") }
        val spread = w.spread(many, 5)
        assertEquals(5, spread.size)
        assertEquals(0L, spread.first().startMs)
        assertNotNull(spread.last())
        assertTrue(spread.last().startMs > 6_000)
    }

    @Test
    fun theGrammarDescribesBothLists() {
        val grammar = HighlightsWriter.GRAMMAR
        assertTrue(grammar.contains("\\\"summary\\\""))
        assertTrue(grammar.contains("\\\"tasks\\\""))
        assertTrue(grammar.contains("\\\"phrase\\\""))
        assertTrue(grammar.lines().first().startsWith("root ::="))
    }

    @Test
    fun theSpeakerLabelACopyingModelLeavesInTheTextIsRemoved() = runBlocking {
        val engine = FakeLlmEngine { _, _, _ ->
            """{"summary":[{"phrase":1,"text":"Спикер 1: Обсудили релиз."}],"tasks":[{"phrase":2,"text":"[Спикер 2]: Подготовить отчёт."}]}"""
        }
        val result = writer(engine).write(dialogue, names)!!

        assertEquals("Обсудили релиз.", result.summary.single().text)
        assertEquals("Подготовить отчёт.", result.tasks.single().text)
    }

    @Test
    fun aLabelThatIsNotTheSpeakersIsKept() {
        val w = writer(FakeLlmEngine { _, _, _ -> "" })
        assertEquals("Внимание: релиз во вторник.", w.withoutSpeakerLabel("Внимание: релиз во вторник.", "Спикер 1"))
    }
}
