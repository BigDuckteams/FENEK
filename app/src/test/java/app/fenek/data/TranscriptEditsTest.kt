package app.fenek.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptEditsTest {

    private val transcript = Transcript(
        id = "t", title = "Встреча", createdAt = 0, durationMs = 30_000, sourceName = "m.m4a",
        asrModelId = "m", asrModelTitle = "M",
        speakers = listOf(Speaker(0, "Анна"), Speaker(1, "Иван")),
        segments = listOf(
            Segment(0, 0, 2_000, "Привет, как дела?", rawText = "привет как дела"),
            Segment(1, 2_500, 4_000, "Хорошо, спасибо."),
        ),
    )

    @Test
    fun aManualEditOfACorrectedPhraseKeepsTheRecognizedTextRestorable() {
        val edited = transcript.withSegmentUpdated(0, "  Привет! Как твои дела?  ", 0)

        assertEquals("Привет! Как твои дела?", edited.segments[0].text)
        assertEquals("привет как дела", edited.segments[0].rawText)
    }

    @Test
    fun typingTheRecognizedTextBackRestoresThePhrase() {
        val restored = transcript.withSegmentUpdated(0, "привет как дела", 0)

        assertEquals("привет как дела", restored.segments[0].text)
        assertNull(restored.segments[0].rawText)
    }

    @Test
    fun savingACorrectedPhraseWithoutChangesKeepsItCorrected() {
        val same = transcript.withSegmentUpdated(0, "Привет, как дела?", 0)
        assertEquals(transcript.segments[0], same.segments[0])
    }

    @Test
    fun aPhraseThatWasNeverCorrectedGetsNoRecognizedText() {
        val edited = transcript.withSegmentUpdated(1, "Хорошо, спасибо, а у вас?", 1)

        assertEquals("Хорошо, спасибо, а у вас?", edited.segments[1].text)
        assertNull(edited.segments[1].rawText)
    }

    @Test
    fun blankTextDeletesThePhrase() {
        val edited = transcript.withSegmentUpdated(0, "   ", 0)
        assertEquals(listOf("Хорошо, спасибо."), edited.segments.map { it.text })
    }

    @Test
    fun changingTheSpeakerMovesThePhraseAndRegistersANewParticipant() {
        val moved = transcript.withSegmentUpdated(1, "Хорошо, спасибо.", 2)

        assertEquals(2, moved.segments[1].speaker)
        assertEquals("Спикер 3", moved.speakerName(2))
        assertTrue(moved.speakers.any { it.id == 2 })
    }

    @Test
    fun aStaleIndexLeavesTheTranscriptUntouched() {
        assertSame(transcript, transcript.withSegmentUpdated(7, "текст", 0))
        assertSame(transcript, transcript.withSegmentUpdated(-1, "текст", 0))
    }

    @Test
    fun otherPhrasesAreNotTouched() {
        val edited = transcript.withSegmentUpdated(1, "Другое.", 1)
        assertEquals(transcript.segments[0], edited.segments[0])
    }
}
