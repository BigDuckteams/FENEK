package app.fenek.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptModelTest {

    /** A file as written by version 1.1.0, before the language model fields existed. */
    private val version110 = """
        {
          "id": "t1", "title": "Встреча", "createdAt": 1000, "durationMs": 60000,
          "sourceName": "meeting.m4a", "asrModelId": "gigaam-v3-rnnt-punct", "asrModelTitle": "GigaAM v3 RNNT",
          "processingMs": 15000,
          "speakers": [{"id": 0, "name": "Анна"}],
          "segments": [{"speaker": 0, "startMs": 500, "endMs": 2500, "text": "Добрый день!"}],
          "favorite": true
        }
    """.trimIndent()

    @Test
    fun oldFileOpensWithDefaultsForNewFields() {
        val t = TranscriptJson.decodeFromString(Transcript.serializer(), version110)
        assertEquals("Добрый день!", t.segments.single().text)
        assertNull(t.segments.single().rawText)
        assertNull(t.llmModelId)
        assertFalse(t.textCorrected)
        assertNull(t.llmSummary)
        assertNull(t.llmTasks)
        assertTrue(t.favorite)
    }

    @Test
    fun newFieldsSurviveARoundTrip() {
        val t = Transcript(
            id = "t2",
            title = "Звонок",
            createdAt = 0,
            durationMs = 120_000,
            sourceName = "call.wav",
            asrModelId = "m",
            asrModelTitle = "M",
            segments = listOf(Segment(0, 0, 1_000, "Привет, как дела?", rawText = "привет как дела")),
            llmModelId = "qwen",
            textCorrected = true,
            llmSummary = listOf(LlmItem(0, 0, "Поздоровались")),
            llmTasks = listOf(LlmItem(500, 1, "Прислать отчёт")),
        )
        val text = TranscriptJson.encodeToString(Transcript.serializer(), t)
        assertEquals(t, TranscriptJson.decodeFromString(Transcript.serializer(), text))
    }

    @Test
    fun fieldsFromNewerVersionsAreIgnored() {
        val withExtra = version110.replace("\"favorite\": true", "\"favorite\": true, \"somethingNew\": [1, 2]")
        assertEquals("t1", TranscriptJson.decodeFromString(Transcript.serializer(), withExtra).id)
    }

    private fun transcript(durationMs: Long, processingMs: Long) = Transcript(
        id = "x", title = "x", createdAt = 0, durationMs = durationMs, sourceName = "x",
        asrModelId = "m", asrModelTitle = "M", processingMs = processingMs,
    )

    @Test
    fun rtfIsProcessingTimeOverDuration() {
        assertEquals(0.25, transcript(durationMs = 120_000, processingMs = 30_000).rtf, 1e-9)
        assertEquals(1.5, transcript(durationMs = 60_000, processingMs = 90_000).rtf, 1e-9)
    }

    @Test
    fun rtfOfAnEmptyRecordingIsZero() {
        assertEquals(0.0, transcript(durationMs = 0, processingMs = 5_000).rtf, 0.0)
    }
}
