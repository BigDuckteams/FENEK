package app.fenek.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnBuilderTest {

    private fun seg(start: Double, end: Double, speaker: Int) = SpeakerSegment(start, end, speaker)

    private fun assertTurn(expected: SpeakerSegment, actual: SpeakerSegment) {
        assertEquals("speaker", expected.speaker, actual.speaker)
        assertEquals("start", expected.start, actual.start, 0.03)
        assertEquals("end", expected.end, actual.end, 0.03)
    }

    @Test
    fun sequentialTurnsArePreserved() {
        val turns = TurnBuilder.build(listOf(seg(0.0, 5.0, 0), seg(5.2, 9.0, 1), seg(9.5, 12.0, 0)))
        assertEquals(3, turns.size)
        assertTurn(seg(0.0, 5.0, 0), turns[0])
        assertTurn(seg(5.2, 9.0, 1), turns[1])
        assertTurn(seg(9.5, 12.0, 0), turns[2])
    }

    @Test
    fun overlapStaysWithCurrentSpeaker() {
        val turns = TurnBuilder.build(listOf(seg(0.0, 10.0, 0), seg(8.0, 12.0, 1)))
        assertEquals(2, turns.size)
        assertTurn(seg(0.0, 10.0, 0), turns[0])
        assertTurn(seg(10.0, 12.0, 1), turns[1])
    }

    @Test
    fun backchannelInsideAnotherTurnIsDropped() {
        val turns = TurnBuilder.build(listOf(seg(0.0, 10.0, 0), seg(4.0, 4.5, 1)))
        assertEquals(1, turns.size)
        assertTurn(seg(0.0, 10.0, 0), turns[0])
    }

    @Test
    fun shortFlickerIsAbsorbed() {
        val turns = TurnBuilder.build(listOf(seg(0.0, 5.0, 0), seg(5.0, 5.1, 1), seg(5.1, 8.0, 0)))
        assertEquals(1, turns.size)
        assertTurn(seg(0.0, 8.0, 0), turns[0])
    }

    @Test
    fun isolatedShortReplyIsKept() {
        val turns = TurnBuilder.build(listOf(seg(0.0, 5.0, 0), seg(6.0, 6.25, 1), seg(7.5, 9.0, 0)))
        assertEquals(3, turns.size)
        assertEquals(1, turns[1].speaker)
    }

    @Test
    fun shortPausesAreBridgedLongOnesAreNot() {
        val bridged = TurnBuilder.build(listOf(seg(0.0, 3.0, 0), seg(3.5, 6.0, 0)))
        assertEquals(1, bridged.size)
        assertTurn(seg(0.0, 6.0, 0), bridged[0])

        val separate = TurnBuilder.build(listOf(seg(0.0, 3.0, 0), seg(5.0, 6.0, 0)))
        assertEquals(2, separate.size)
    }

    @Test
    fun longTurnsAreSplitIntoContiguousChunks() {
        val chunks = TurnBuilder.splitLongTurns(listOf(seg(0.0, 60.0, 2)), 25.0) { _, to -> to - 1.0 }
        assertTrue(chunks.size >= 3)
        assertEquals(0.0, chunks.first().start, 1e-9)
        assertEquals(60.0, chunks.last().end, 1e-9)
        chunks.zipWithNext { a, b -> assertEquals(a.end, b.start, 1e-9) }
        chunks.forEach {
            assertTrue(it.duration <= 25.0 + 1e-9)
            assertEquals(2, it.speaker)
        }
    }

    @Test
    fun emptyInputGivesNoTurns() {
        assertTrue(TurnBuilder.build(emptyList()).isEmpty())
    }
}
