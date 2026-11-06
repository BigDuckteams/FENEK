package app.fenek.data

/**
 * Fixes the text or the speaker of a phrase; blank text deletes the phrase. An index that no
 * longer exists leaves the transcript as it is.
 *
 * Text typed by the user is final: the language model never touches it again. If the phrase had
 * been corrected, its recognized text stays restorable, and typing the recognized text back
 * counts as restoring it (the phrase is then no longer marked as corrected).
 */
fun Transcript.withSegmentUpdated(index: Int, text: String, speaker: Int): Transcript {
    if (index !in segments.indices) return this
    val updated = segments.toMutableList()
    if (text.isBlank()) {
        updated.removeAt(index)
    } else {
        val old = updated[index]
        val clean = text.trim()
        updated[index] = old.copy(text = clean, speaker = speaker, rawText = old.rawText?.takeIf { it != clean })
    }
    val known = if (speakers.none { it.id == speaker }) speakers + Speaker(speaker, defaultSpeakerName(speaker)) else speakers
    return copy(segments = updated, speakers = known)
}
