package app.fenek.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PendingTranscriptsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val dir get() = File(folder.root, "pending")

    private fun transcript(id: String, favorite: Boolean = false, text: String = "Привет, как дела?") = Transcript(
        id = id, title = id, createdAt = 1, durationMs = 5_000, sourceName = "$id.m4a",
        asrModelId = "m", asrModelTitle = "M", favorite = favorite,
        segments = listOf(Segment(0, 0, 1_000, text)),
    )

    @Test
    fun aPendingCopyBecomesATranscriptAfterACrash() {
        PendingTranscripts(dir).write(transcript("a"))

        // A new process: nothing deleted the copy.
        val recovered = PendingTranscripts(dir).recover(existing = emptyList())

        assertEquals(listOf("a"), recovered.map { it.id })
        assertEquals("Привет, как дела?", recovered.single().segments.single().text)
    }

    @Test
    fun recoveringRemovesTheFilesSoItHappensOnce() {
        PendingTranscripts(dir).write(transcript("a"))
        PendingTranscripts(dir).recover(emptyList())

        assertTrue(PendingTranscripts(dir).recover(emptyList()).isEmpty())
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun aRedoneTranscriptKeepsTheFavouriteFlagOfTheOneItReplaces() {
        PendingTranscripts(dir).write(transcript("a", favorite = false, text = "Новый текст."))
        val old = transcript("a", favorite = true, text = "Старый текст.")

        val recovered = PendingTranscripts(dir).recover(existing = listOf(old, transcript("b")))

        assertEquals(1, recovered.size)
        assertTrue(recovered.single().favorite)
        assertEquals("Новый текст.", recovered.single().segments.single().text)
    }

    @Test
    fun aJobThatEndedNormallyLeavesNothingToRecover() {
        val pending = PendingTranscripts(dir)
        pending.write(transcript("a"))
        pending.delete("a")

        assertTrue(PendingTranscripts(dir).recover(emptyList()).isEmpty())
    }

    @Test
    fun aCancelledJobLeavesNothingToRecover() {
        // The job controller deletes the copy in its finally block, whatever way the job ended.
        val pending = PendingTranscripts(dir)
        pending.write(transcript("a"))
        pending.delete("a")
        pending.delete("a")

        assertFalse(File(dir, "a.json").exists())
    }

    @Test
    fun anUnreadableCopyIsIgnoredAndRemoved() {
        dir.mkdirs()
        File(dir, "broken.json").writeText("{ not json")
        PendingTranscripts(dir).write(transcript("ok"))

        val recovered = PendingTranscripts(dir).recover(emptyList())

        assertEquals(listOf("ok"), recovered.map { it.id })
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun anAbsentFolderIsFine() {
        assertTrue(PendingTranscripts(File(folder.root, "never-created")).recover(emptyList()).isEmpty())
    }
}
