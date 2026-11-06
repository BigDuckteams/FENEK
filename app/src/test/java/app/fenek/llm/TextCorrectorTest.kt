package app.fenek.llm

import app.fenek.data.Segment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextCorrectorTest {

    private val numbered = Regex("^(\\d+): (.+)$")

    /** Answers like a well-behaved model: capital letter at the start and a full stop at the end. */
    private fun polite(user: String): String = user.lines().mapNotNull { numbered.matchEntire(it) }
        .joinToString("\n") { "${it.groupValues[1]}: ${it.groupValues[2].replaceFirstChar(Char::uppercase)}." }

    private val phrases = listOf(
        Segment(0, 0, 2_000, "добрый день как ваши дела"),
        Segment(1, 2_100, 4_000, "спасибо хорошо а у вас"),
        Segment(0, 4_100, 4_600, "да ладно"),
        Segment(1, 4_700, 7_000, "завтра пришлю отчёт к обеду"),
    )

    @Test
    fun correctsPhrasesAndKeepsStructure() = runBlocking {
        val engine = FakeLlmEngine { _, user, _ -> polite(user) }
        val result = TextCorrector(engine).correct(phrases)

        assertEquals(phrases.size, result.segments.size)
        assertEquals(phrases.map { it.speaker }, result.segments.map { it.speaker })
        assertEquals(phrases.map { it.startMs }, result.segments.map { it.startMs })
        assertEquals(phrases.map { it.endMs }, result.segments.map { it.endMs })
        assertEquals("Добрый день как ваши дела.", result.segments[0].text)
        assertEquals("добрый день как ваши дела", result.segments[0].rawText)
        assertTrue(result.applied)
        assertEquals(3, result.changed)
    }

    @Test
    fun phrasesOfTwoWordsOrFewerAreNotSent() = runBlocking {
        val engine = FakeLlmEngine { _, user, _ -> polite(user) }
        val result = TextCorrector(engine).correct(phrases)

        assertFalse(engine.requests.single().user.contains("да ладно"))
        assertEquals("да ладно", result.segments[2].text)
        assertNull(result.segments[2].rawText)
    }

    @Test
    fun neighboursAreGivenAsContextOnly() = runBlocking {
        val engine = FakeLlmEngine { _, user, _ -> polite(user) }
        TextCorrector(engine, maxBatchPhrases = 1).correct(phrases)

        val second = engine.requests[1].user
        assertTrue(second, second.contains("Контекст до (не исправлять): добрый день как ваши дела"))
        assertTrue(second, second.contains("1: спасибо хорошо а у вас"))
        // The context line is not a numbered line, so it cannot be mistaken for an answer.
        assertEquals(1, second.lines().count { numbered.matches(it) })
    }

    @Test
    fun aRewrittenPhraseKeepsItsRecognizedTextWhileOthersAreCorrected() = runBlocking {
        val engine = FakeLlmEngine { _, user, _ ->
            polite(user).lines().mapIndexed { i, l -> if (i == 1) "2: Сегодня я поехал на работу на машине." else l }.joinToString("\n")
        }
        val result = TextCorrector(engine).correct(phrases)

        assertEquals("Добрый день как ваши дела.", result.segments[0].text)
        assertEquals("спасибо хорошо а у вас", result.segments[1].text)
        assertNull(result.segments[1].rawText)
        assertEquals("Завтра пришлю отчёт к обеду.", result.segments[3].text)
    }

    @Test
    fun anUnparseableAnswerKeepsEverything() = runBlocking {
        val engine = FakeLlmEngine { _, _, _ -> "Конечно! Вот исправленный текст, как вы просили." }
        val result = TextCorrector(engine).correct(phrases)

        assertEquals(phrases, result.segments)
        assertFalse(result.applied)
        assertEquals(1, result.failedBatches)
    }

    @Test
    fun linesWithUnknownNumbersAreIgnored() = runBlocking {
        val engine = FakeLlmEngine { _, _, _ -> "9: Выдуманная строка без номера в запросе." }
        val result = TextCorrector(engine).correct(phrases)

        assertEquals(phrases, result.segments)
        assertEquals(1, result.failedBatches)
    }

    @Test
    fun aTimedOutBatchOnlyAffectsItself() = runBlocking {
        var calls = 0
        val engine = FakeLlmEngine { _, user, _ ->
            if (calls++ == 0) throw LlmTimeoutException("too slow") else polite(user)
        }
        val result = TextCorrector(engine, maxBatchPhrases = 1).correct(phrases)

        assertEquals(1, result.failedBatches)
        assertEquals("добрый день как ваши дела", result.segments[0].text)
        assertEquals("Спасибо хорошо а у вас.", result.segments[1].text)
        assertTrue(result.applied)
    }

    @Test
    fun batchesAreLimitedInPhrasesAndCharacters() {
        val many = (0 until 20).map { Segment(0, it * 1_000L, it * 1_000L + 900, "это пятая по счёту реплика номер $it") }
        val corrector = TextCorrector(FakeLlmEngine { _, _, _ -> "" }, maxBatchPhrases = 8, maxBatchChars = 1_800)
        val batches = corrector.batches(many, many.indices.toList())
        assertEquals(listOf(8, 8, 4), batches.map { it.size })
        assertEquals(many.indices.toList(), batches.flatten())

        val byChars = TextCorrector(FakeLlmEngine { _, _, _ -> "" }, maxBatchPhrases = 100, maxBatchChars = 80)
            .batches(many, many.indices.toList())
        assertTrue(byChars.all { batch -> batch.sumOf { many[it].text.length } <= 80 })
    }

    @Test
    fun theLengthOfAnAnswerIsLimitedInProportionToItsInput() = runBlocking {
        val engine = FakeLlmEngine { _, user, _ -> polite(user) }
        TextCorrector(engine).correct(phrases)
        val request = engine.requests.single()
        assertTrue(request.maxTokens in 20..400)
        assertEquals(60_000, request.timeoutMs)
    }

    @Test
    fun theSameInputGivesTheSameOutput() = runBlocking {
        val first = TextCorrector(FakeLlmEngine { _, user, _ -> polite(user) }).correct(phrases)
        val second = TextCorrector(FakeLlmEngine { _, user, _ -> polite(user) }).correct(phrases)
        assertEquals(first.segments, second.segments)
    }

    @Test
    fun progressCountsPhrasesThatWereSent() = runBlocking {
        val seen = ArrayList<Pair<Int, Int>>()
        TextCorrector(FakeLlmEngine { _, user, _ -> polite(user) }, maxBatchPhrases = 2)
            .correct(phrases) { done, total -> seen += done to total }
        assertEquals(listOf(0 to 3, 2 to 3, 3 to 3), seen)
    }

    @Test
    fun nothingToSendMeansNothingApplied() = runBlocking {
        val engine = FakeLlmEngine { _, _, _ -> error("must not be called") }
        val result = TextCorrector(engine).correct(listOf(Segment(0, 0, 500, "да")))
        assertFalse(result.applied)
        assertTrue(engine.requests.isEmpty())
    }

    @Test
    fun theInstructionShowsAnExampleAndHasNoPlaceholderTheModelCouldCopy() = runBlocking {
        val engine = FakeLlmEngine { _, user, _ -> polite(user) }
        TextCorrector(engine).correct(phrases)
        val system = engine.requests.single().system

        // A small model answered "N: ..." when the instruction said «N: текст».
        assertFalse(system, Regex("""(^|\s)N:""").containsMatchIn(system))
        assertTrue(system, system.contains("1: Привет, как дела?"))
        assertTrue(system, system.contains("1: привет как дела"))
    }
}
