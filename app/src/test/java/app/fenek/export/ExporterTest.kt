package app.fenek.export

import app.fenek.audio.AudioMath
import app.fenek.data.Segment
import app.fenek.data.Speaker
import app.fenek.data.Transcript
import app.fenek.engine.TextCleaner
import app.fenek.util.TimeFormat
import app.fenek.util.pluralRu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExporterTest {

    private val transcript = Transcript(
        id = "t1",
        title = "Интервью",
        createdAt = 0,
        durationMs = 75_000,
        sourceName = "interview.m4a",
        asrModelId = "gigaam-v3-rnnt-punct",
        asrModelTitle = "GigaAM v3 RNNT",
        speakers = listOf(Speaker(0, "Анна"), Speaker(1, "Спикер 2")),
        segments = listOf(
            Segment(0, 1_000, 2_500, "Добрый день!"),
            Segment(0, 2_600, 4_000, "Начнём."),
            Segment(1, 4_200, 7_950, "Здравствуйте."),
        ),
    )

    @Test
    fun plainTextGroupsPhrasesIntoTurns() {
        val text = Exporter.plainText(transcript)
        assertTrue(text, text.contains("[00:00:01] Анна:\nДобрый день! Начнём."))
        assertTrue(text, text.contains("[00:00:04] Спикер 2:\nЗдравствуйте."))
        assertTrue(text, text.contains("участников: 2"))
    }

    @Test
    fun srtHasNumberedCuesWithSpeakers() {
        val srt = Exporter.srt(transcript)
        assertTrue(srt, srt.startsWith("1\n00:00:01,000 --> 00:00:02,500\nАнна: Добрый день!\n\n2\n"))
        assertTrue(srt, srt.contains("3\n00:00:04,200 --> 00:00:07,950\nСпикер 2: Здравствуйте."))
    }

    @Test
    fun vttUsesVoiceTags() {
        val vtt = Exporter.vtt(transcript)
        assertTrue(vtt, vtt.startsWith("WEBVTT\n\n00:00:01.000 --> 00:00:02.500\n<v Анна>Добрый день!"))
    }

    @Test
    fun extendedJsonContainsUtterancesWithSpeakerNames() {
        val json = Exporter.extendedJson(transcript)
        assertTrue(json, json.contains("\"speaker\": \"Анна\""))
        assertTrue(json, json.contains("\"startTime\": \"00:00:04\""))
    }

    @Test
    fun markdownListsTasksQuestionsAndMarks() {
        val meeting = transcript.copy(
            segments = listOf(
                Segment(0, 1_000, 4_000, "Я подготовлю отчёт к пятнице."),
                Segment(1, 4_500, 8_000, "А кто проверит цифры в отчёте?"),
            ),
            marks = listOf(6_000),
        )
        val md = Exporter.markdown(meeting)
        assertTrue(md, md.contains("## Задачи и договорённости\n\n- [ ] Я подготовлю отчёт к пятнице. — Анна, срок: "))
        assertTrue(md, md.contains("## Вопросы\n\n- А кто проверит цифры в отчёте? — Спикер 2 `00:00:04`"))
        assertTrue(md, md.contains("**Отметки при записи:** `00:00:06`"))
        // The highlights come before the dialogue, which starts after the rule.
        assertTrue(md, md.indexOf("## Вопросы") < md.indexOf("\n---\n"))
    }

    @Test
    fun timeFormats() {
        assertEquals("01:15", TimeFormat.clock(75_000))
        assertEquals("1:02:05", TimeFormat.clock(3_725_000))
        assertEquals("00:01:15", TimeFormat.stamp(75_400))
        assertEquals("01:02:03,456", TimeFormat.subtitle(3_723_456, ','))
        assertEquals("2 мин 05 с", TimeFormat.duration(125_000))
    }

    @Test
    fun russianPlurals() {
        assertEquals("1 участник", pluralRu(1, "участник", "участника", "участников"))
        assertEquals("3 участника", pluralRu(3, "участник", "участника", "участников"))
        assertEquals("11 участников", pluralRu(11, "участник", "участника", "участников"))
        assertEquals("22 участника", pluralRu(22, "участник", "участника", "участников"))
    }

    @Test
    fun textCleaner() {
        assertEquals("Привет, как дела?", TextCleaner.clean("  Привет,   как дела? ", punctuated = true))
        assertEquals("Привет как дела", TextCleaner.clean("привет как дела", punctuated = false))
        assertEquals("", TextCleaner.clean("Продолжение следует...", punctuated = true, dropHallucinations = true))
        assertEquals("Продолжение следует.", TextCleaner.clean("Продолжение следует.", punctuated = true))
        assertEquals("", TextCleaner.clean(" ... ", punctuated = true))
    }

    @Test
    fun quietestIndexFindsThePause() {
        val samples = FloatArray(16_000) { if (it in 9_000 until 10_000) 0.001f else 0.5f }
        val index = AudioMath.quietestIndex(samples, 400)
        assertTrue("index=$index", index in 9_000 until 10_000)
    }

    @Test
    fun markdownUsesTheHighlightsWrittenByTheLanguageModel() {
        val withLlm = transcript.copy(
            segments = transcript.segments + Segment(1, 8_000, 12_000, "Я подготовлю отчёт к пятнице."),
            llmSummary = listOf(app.fenek.data.LlmItem(1_000, 0, "Поздоровались и начали разговор.")),
            llmTasks = listOf(app.fenek.data.LlmItem(8_000, 1, "Подготовить отчёт к пятнице.")),
        )
        val md = Exporter.markdown(withLlm)

        assertTrue(md, md.contains("## Коротко\n\n- Поздоровались и начали разговор. `00:00:01`"))
        assertTrue(md, md.contains("- [ ] Подготовить отчёт к пятнице. — Спикер 2"))
        assertTrue(md, md.contains("срок: "))
    }
}
