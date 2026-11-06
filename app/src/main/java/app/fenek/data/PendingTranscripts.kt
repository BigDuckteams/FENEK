package app.fenek.data

import java.io.File

/**
 * A copy of a transcript with the recognized text, kept while the language model works on it. If
 * the process dies in the model, [recover] turns the copy into a real transcript at the next
 * start; when the job ends in any other way the copy is deleted and never shows up.
 */
class PendingTranscripts(private val dir: File) {

    fun write(transcript: Transcript) {
        dir.mkdirs()
        val target = File(dir, "${transcript.id}.json")
        val temp = File(dir, "${transcript.id}.json.tmp")
        temp.writeText(TranscriptJson.encodeToString(Transcript.serializer(), transcript))
        if (!temp.renameTo(target)) {
            target.delete()
            check(temp.renameTo(target)) { "Cannot write $target" }
        }
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
        File(dir, "$id.json.tmp").delete()
    }

    /**
     * Reads and removes every pending copy. A copy replaces the transcript with the same id in
     * [existing] and takes over its favourite flag.
     *
     * @return the transcripts to store
     */
    fun recover(existing: List<Transcript>): List<Transcript> {
        val files = dir.listFiles().orEmpty()
        val recovered = files.filter { it.extension == "json" }.mapNotNull { file ->
            runCatching { TranscriptJson.decodeFromString(Transcript.serializer(), file.readText()) }.getOrNull()
        }.map { pending ->
            val previous = existing.firstOrNull { it.id == pending.id }
            if (previous != null) pending.copy(favorite = previous.favorite) else pending
        }
        files.forEach { it.delete() }
        return recovered
    }
}
