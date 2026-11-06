package app.fenek.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Transcript(
    val id: String,
    val title: String,
    val createdAt: Long,
    val durationMs: Long,
    val sourceName: String,
    /** The recording for playback: a content:// URI or an absolute file path. */
    val audio: String? = null,
    /** The recording was copied into app storage and is deleted together with the transcript. */
    val audioOwned: Boolean = false,
    val asrModelId: String,
    val asrModelTitle: String,
    /** Recognition language for multilingual models, null otherwise. */
    val language: String? = null,
    val processingMs: Long = 0,
    val speakers: List<Speaker> = emptyList(),
    val segments: List<Segment> = emptyList(),
    /** Start of the recording, when known (recordings made in the app). */
    val recordedAt: Long? = null,
    /** Moments marked during the recording, ms from its start. */
    val marks: List<Long> = emptyList(),
    val favorite: Boolean = false,
    /** The language model that corrected the text or wrote the highlights, if any did. */
    val llmModelId: String? = null,
    /** The phrases went through the language model (changed ones keep their recognized text). */
    val textCorrected: Boolean = false,
    /** Summary points written by the language model; null when it did not write them. */
    val llmSummary: List<LlmItem>? = null,
    /** Tasks and agreements written by the language model; null when it did not write them. */
    val llmTasks: List<LlmItem>? = null,
) {
    /** When the conversation took place: "завтра" in it is counted from this day. */
    val conversationAt: Long get() = recordedAt ?: createdAt

    /** Real-time factor: processing time divided by the length of the recording (lower is faster). */
    val rtf: Double get() = if (durationMs > 0) processingMs.toDouble() / durationMs else 0.0

    fun speakerName(id: Int): String = speakers.firstOrNull { it.id == id }?.name ?: defaultSpeakerName(id)

    /** Speakers that actually have at least one segment, in order of first appearance. */
    fun activeSpeakers(): List<Speaker> =
        segments.map { it.speaker }.distinct().map { id -> Speaker(id, speakerName(id)) }

    /** Consecutive segments of the same speaker grouped into turns of the dialogue. */
    fun turns(): List<Turn> {
        val turns = ArrayList<Turn>()
        var current = ArrayList<IndexedSegment>()
        segments.forEachIndexed { index, segment ->
            if (current.isNotEmpty() && current.last().segment.speaker != segment.speaker) {
                turns += Turn(current)
                current = ArrayList()
            }
            current += IndexedSegment(index, segment)
        }
        if (current.isNotEmpty()) turns += Turn(current)
        return turns
    }
}

@Serializable
data class Speaker(val id: Int, val name: String)

@Serializable
data class Segment(
    val speaker: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    /** The text as recognized, kept only when the language model changed [text]. */
    val rawText: String? = null,
)

/**
 * A sentence the language model picked for the summary or the task list. It is placed by time
 * and speaker, not by phrase index, so it stays valid when the user deletes or edits phrases.
 */
@Serializable
data class LlmItem(val startMs: Long, val speaker: Int, val text: String)

/** How transcripts are written to disk and read back; unknown fields of newer versions are ignored. */
val TranscriptJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

data class IndexedSegment(val index: Int, val segment: Segment)

data class Turn(val segments: List<IndexedSegment>) {
    val speaker: Int get() = segments.first().segment.speaker
    val startMs: Long get() = segments.first().segment.startMs
    val endMs: Long get() = segments.last().segment.endMs
}

fun defaultSpeakerName(id: Int): String = "Спикер ${id + 1}"
