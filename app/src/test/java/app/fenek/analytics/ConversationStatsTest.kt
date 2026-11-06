package app.fenek.analytics

import app.fenek.data.Segment
import app.fenek.data.Transcript
import app.fenek.record.RecordingNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ConversationStatsTest {

    private fun transcript(vararg segments: Segment) = Transcript(
        id = "t",
        title = "t",
        createdAt = 0,
        durationMs = 60_000,
        sourceName = "t",
        asrModelId = "m",
        asrModelTitle = "m",
        segments = segments.toList(),
    )

    @Test
    fun talkTimeSharesTurnsAndRate() {
        val stats = ConversationAnalytics.compute(
            transcript(
                Segment(0, 0, 30_000, "раз два три четыре пять шесть"),
                Segment(1, 30_000, 40_000, "семь восемь"),
                Segment(0, 40_000, 50_000, "девять десять"),
            ),
        )
        val first = stats.speakers.first()
        assertEquals(0, first.speaker)
        assertEquals(40_000, first.talkMs)
        assertEquals(0.8f, first.share, 1e-4f)
        assertEquals(2, first.turns)
        assertEquals(8, first.words)
        assertEquals(12, first.wordsPerMinute)
        assertEquals(30_000, first.longestTurnMs)
        assertEquals(3, stats.turns)
        assertEquals(10, stats.words)
        assertEquals(0, stats.chattiest?.speaker)
    }

    @Test
    fun monologueHasNoChattiestOrFastest() {
        val stats = ConversationAnalytics.compute(transcript(Segment(0, 0, 5_000, "только я")))
        assertNull(stats.chattiest)
        assertNull(stats.fastest)
    }

    @Test
    fun topicsGroupWordFormsAndSkipStopWords() {
        val topics = ConversationAnalytics.topics(
            listOf(
                "Давайте обсудим продажи и презентацию.",
                "Продажи выросли, презентацию перенесли.",
                "По продажам отчёт пришлю, это очень важно.",
            ),
        )
        assertEquals("продажи", topics.first())
        assertTrue(topics.toString(), "презентацию" in topics)
        assertTrue(topics.toString(), "давайте" !in topics && "очень" !in topics)
    }

    @Test
    fun wordCountHandlesHyphensAndPunctuation() {
        assertEquals(4, ConversationAnalytics.countWords("Кто-нибудь, ну-ка: скажи что-то!"))
    }

    @Test
    fun recordingNames() {
        assertEquals(1_793_000_000_000L, RecordingNames.createdAt(File(RecordingNames.fileName(1_793_000_000_000L))))
        assertNull(RecordingNames.createdAt(File("other.aac")))
        assertTrue(RecordingNames.displayName(0).startsWith("Запись "))
        assertEquals(0f, RecordingNames.level(0), 0f)
        assertEquals(1f, RecordingNames.level(32767), 1e-4f)
        assertEquals(0.5f, RecordingNames.level(1036), 0.01f)
    }
}
