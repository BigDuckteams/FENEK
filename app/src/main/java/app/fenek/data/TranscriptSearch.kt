package app.fenek.data

data class SearchHit(val transcript: Transcript, val snippet: String?, val matches: Int)

/** Case-insensitive full-text search over titles, participant names and phrases. */
object TranscriptSearch {

    fun search(transcripts: List<Transcript>, query: String): List<SearchHit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return transcripts.mapNotNull { t ->
            val inTitle = t.title.contains(q, ignoreCase = true)
            val inNames = t.activeSpeakers().any { it.name.contains(q, ignoreCase = true) }
            val phrases = t.segments.filter { it.text.contains(q, ignoreCase = true) }
            if (!inTitle && !inNames && phrases.isEmpty()) return@mapNotNull null
            val snippet = phrases.firstOrNull()?.let { "${t.speakerName(it.speaker)}: ${snippet(it.text, q)}" }
            SearchHit(t, snippet, phrases.size)
        }.sortedWith(compareByDescending<SearchHit> { it.matches }.thenByDescending { it.transcript.createdAt })
    }

    /** Indices of segments of [transcript] that contain [query]. */
    fun matchingSegments(transcript: Transcript, query: String): List<Int> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return transcript.segments.indices.filter { transcript.segments[it].text.contains(q, ignoreCase = true) }
    }

    /** Character ranges of all case-insensitive occurrences of [query] in [text]. */
    fun occurrences(text: String, query: String): List<IntRange> {
        if (query.isEmpty()) return emptyList()
        val result = ArrayList<IntRange>()
        var from = 0
        while (true) {
            val i = text.indexOf(query, from, ignoreCase = true)
            if (i < 0) break
            result += i until i + query.length
            from = i + query.length
        }
        return result
    }

    /** About [radius] characters around the first match, cut at word boundaries. */
    fun snippet(text: String, query: String, radius: Int = 50): String {
        val i = text.indexOf(query, ignoreCase = true)
        if (i < 0) return text.take(radius * 2)
        var start = (i - radius).coerceAtLeast(0)
        var end = (i + query.length + radius).coerceAtMost(text.length)
        if (start > 0 && !text[start - 1].isWhitespace()) {
            val space = text.indexOf(' ', start)
            if (space in start until i) start = space + 1
        }
        if (end < text.length && !text[end].isWhitespace()) {
            val space = text.lastIndexOf(' ', end - 1)
            if (space >= i + query.length) end = space
        }
        return (if (start > 0) "…" else "") + text.substring(start, end) + (if (end < text.length) "…" else "")
    }
}
