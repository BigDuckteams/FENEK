package app.fenek.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptSearchTest {

    private fun transcript(id: String, title: String, createdAt: Long, vararg texts: String) = Transcript(
        id = id,
        title = title,
        createdAt = createdAt,
        durationMs = 1_000,
        sourceName = title,
        asrModelId = "m",
        asrModelTitle = "m",
        speakers = listOf(Speaker(0, "Анна")),
        segments = texts.mapIndexed { i, text -> Segment(i % 2, i * 1_000L, i * 1_000L + 900, text) },
    )

    private val meeting = transcript("a", "Планёрка", 1, "Обсудим отчёт по продажам.", "Отчёт пришлю вечером.", "Хорошо.")
    private val call = transcript("b", "Звонок маме", 2, "Привет! Как дела?")
    private val interview = transcript("c", "Интервью", 3, "Расскажите про ваш отчёт.")

    @Test
    fun findsPhrasesCaseInsensitiveAndRanksByMatches() {
        val hits = TranscriptSearch.search(listOf(meeting, call, interview), "ОТЧЁТ")
        assertEquals(listOf("a", "c"), hits.map { it.transcript.id })
        assertEquals(2, hits.first().matches)
        assertTrue(hits.first().snippet!!.startsWith("Анна: "))
    }

    @Test
    fun findsTitlesAndParticipantNames() {
        assertEquals(listOf("b"), TranscriptSearch.search(listOf(meeting, call), "маме").map { it.transcript.id })
        // "Анна" speaks in every transcript, the newest comes first.
        assertEquals(listOf("b", "a"), TranscriptSearch.search(listOf(meeting, call), "анна").map { it.transcript.id })
        assertTrue(TranscriptSearch.search(listOf(meeting), "  ").isEmpty())
    }

    @Test
    fun matchingSegmentsAndOccurrences() {
        assertEquals(listOf(0, 1), TranscriptSearch.matchingSegments(meeting, "отчёт"))
        assertEquals(listOf(0..1, 4..5, 7..8), TranscriptSearch.occurrences("да, да-да", "да"))
        assertEquals(
            "…и вот здесь главное слово, а…",
            TranscriptSearch.snippet(
                "Сначала очень длинное вступление и вот здесь главное слово, а потом длинное продолжение",
                "главное",
                radius = 12,
            ),
        )
        assertEquals("…и тут ключ, и…", TranscriptSearch.snippet("Начало строки и тут ключ, и хвост строки", "ключ", radius = 8))
    }
}
