package app.fenek.analytics

import app.fenek.data.Segment
import app.fenek.data.Transcript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class HighlightsTest {

    /** A Wednesday. */
    private val today = LocalDate.of(2026, 10, 28)

    /** One phrase every 5 seconds. */
    private fun transcript(vararg lines: Pair<Int, String>) = Transcript(
        id = "t",
        title = "Встреча",
        createdAt = 0,
        durationMs = lines.size * 5_000L,
        sourceName = "meeting.m4a",
        asrModelId = "gigaam",
        asrModelTitle = "GigaAM",
        segments = lines.mapIndexed { i, (speaker, text) -> Segment(speaker, i * 5_000L, i * 5_000L + 4_500, text) },
    )

    @Test
    fun splitsPhrasesIntoSentencesPlacedInTime() {
        val sentences = ConversationHighlights.sentences(transcript(0 to "Привет. Как дела у команды?", 1 to "Хорошо"))
        assertEquals(listOf("Привет.", "Как дела у команды?", "Хорошо"), sentences.map { it.text })
        assertEquals(0L, sentences[0].startMs)
        assertTrue(sentences[1].startMs in 1..4_000)
        assertEquals(5_000L, sentences[2].startMs)
        assertEquals(1, sentences[2].speaker)
    }

    @Test
    fun findsPromisesRequestsAndPlans() {
        listOf(
            "Я подготовлю отчёт к пятнице.",
            "Нужно отправить договор клиенту.",
            "Пришлите, пожалуйста, презентацию до вечера.",
            "Давайте перенесём встречу на четверг.",
            "Давайте закроем этот вопрос сегодня.",
            "Не забудь позвонить бухгалтеру.",
            "Сможешь проверить расчёты завтра?",
            "I will send the report tomorrow.",
        ).forEach { assertTrue(it, ConversationHighlights.isAction(it)) }
    }

    @Test
    fun ignoresSmallTalkNegationsAndPlainQuestions() {
        listOf(
            "Погода сегодня отличная.",
            "Не нужно ничего менять.",
            "Нужно ли это вообще?",
            "Давайте посмотрим, что получится.",
            "Мы пришли в офис рано утром.",
            "Сделали всё ещё вчера.",
        ).forEach { assertFalse(it, ConversationHighlights.isAction(it)) }
    }

    @Test
    fun shortAgreementTurnsTheProposalIntoATask() {
        val t = transcript(0 to "Отчёт по продажам хорошо бы к понедельнику.", 1 to "Договорились.")
        val actions = ConversationHighlights.compute(t, today).actions
        assertEquals(listOf("Отчёт по продажам хорошо бы к понедельнику."), actions.map { it.sentence.text })
        assertEquals(LocalDate.of(2026, 11, 2), actions.single().deadline?.date)
    }

    @Test
    fun resolvesDeadlinesFromTheDayOfTheConversation() {
        fun date(text: String) = Deadlines.find(text, today)?.date
        assertEquals(LocalDate.of(2026, 10, 29), date("сделаю завтра"))
        assertEquals(LocalDate.of(2026, 10, 30), date("пришлю к пятнице"))
        assertEquals(LocalDate.of(2026, 11, 2), date("созвонимся в понедельник"))
        assertEquals(LocalDate.of(2026, 11, 6), date("встретимся в следующую пятницу"))
        assertEquals(LocalDate.of(2026, 11, 4), date("отправлю через неделю"))
        assertEquals(LocalDate.of(2026, 10, 30), date("подготовлю через пару дней"))
        assertEquals(LocalDate.of(2026, 10, 30), date("закончим до конца недели"))
        assertEquals(LocalDate.of(2026, 10, 31), date("закроем до конца месяца"))
        assertEquals(LocalDate.of(2026, 11, 2), date("вернёмся к этому на следующей неделе"))
        assertEquals(LocalDate.of(2026, 11, 5), date("оплачу до 5 ноября"))
        assertEquals(LocalDate.of(2027, 3, 3), date("сдадим к 3 марта"))
        assertNull(Deadlines.find("просто поговорили о погоде", today))
    }

    @Test
    fun keepsTimeAndWording() {
        val deadline = Deadlines.find("Скину завтра к 15:30, хорошо?", today)!!
        assertEquals("завтра к 15:30", deadline.phrase)
        assertEquals(LocalDate.of(2026, 10, 29), deadline.date)
        assertEquals(LocalTime.of(15, 30), deadline.time)
        assertEquals(LocalTime.of(19, 0), Deadlines.find("позвоню в 7 вечера", today)!!.time)
        // A vague deadline is kept as said, without a date.
        val vague = Deadlines.find("пришлю через несколько дней", today)!!
        assertNull(vague.date)
        assertEquals("через несколько дней", vague.label())
    }

    @Test
    fun collectsRealQuestionsOnly() {
        val t = transcript(0 to "Да? Когда будет готов макет сайта?", 1 to "Думаю, к среде. Что?")
        assertEquals(listOf("Когда будет готов макет сайта?"), ConversationHighlights.compute(t, today).questions.map { it.text })
    }

    @Test
    fun summaryPrefersSentencesAboutRecurringSubjects() {
        val t = transcript(
            0 to "Добрый день, коллеги.",
            1 to "Здравствуйте.",
            0 to "Сегодня обсуждаем бюджет рекламной кампании на ноябрь.",
            1 to "Бюджет кампании я предлагаю увеличить на двадцать процентов.",
            0 to "Хорошо, а что с подрядчиком?",
            1 to "Подрядчик готов запустить кампанию через неделю, если бюджет утвердят.",
            0 to "Кстати, вчера был отличный футбол.",
            1 to "Да, я видел.",
            0 to "Итак, договорились: бюджет кампании увеличиваем, подрядчик запускает рекламу.",
        )
        val summary = ConversationHighlights.compute(t, today).summary
        assertEquals(2, summary.size)
        assertTrue(summary.all { "бюджет" in it.text.lowercase() })
        assertEquals(summary.sortedBy { it.startMs }, summary)
        assertTrue(summary.last().text.startsWith("Итак, договорились"))
    }

    @Test
    fun shortConversationHasNoSummary() {
        val t = transcript(0 to "Привет, как дела у проекта?", 1 to "Всё идёт по плану, релиз в пятницу.")
        assertTrue(ConversationHighlights.compute(t, today).summary.isEmpty())
    }
}
