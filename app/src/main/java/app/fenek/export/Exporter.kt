package app.fenek.export

import app.fenek.analytics.ConversationAnalytics
import app.fenek.analytics.ConversationHighlights
import app.fenek.data.Transcript
import app.fenek.model.Languages
import app.fenek.util.TimeFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

enum class ExportFormat(val extension: String, val mimeType: String, val title: String) {
    TXT("txt", "text/plain", "Текст диалога (.txt)"),
    MD("md", "text/markdown", "Заметка Markdown (.md)"),
    SRT("srt", "application/x-subrip", "Субтитры SRT (.srt)"),
    VTT("vtt", "text/vtt", "Субтитры WebVTT (.vtt)"),
    JSON("json", "application/json", "Структурированный JSON (.json)"),
}

/** Renders a transcript in the export formats. */
object Exporter {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun render(transcript: Transcript, format: ExportFormat): String = when (format) {
        ExportFormat.TXT -> plainText(transcript)
        ExportFormat.MD -> markdown(transcript)
        ExportFormat.SRT -> srt(transcript)
        ExportFormat.VTT -> vtt(transcript)
        ExportFormat.JSON -> json(transcript)
    }

    /** A note for Obsidian, Notion and the like: summary, tasks and questions on top, then the dialogue. */
    fun markdown(t: Transcript): String = buildString {
        val stats = ConversationAnalytics.compute(t)
        append("# ").appendLine(t.title)
        appendLine()
        append('*').append(TimeFormat.date(t.conversationAt))
        append(" · ").append(TimeFormat.clock(t.durationMs))
        append(" · участников: ").append(stats.speakers.size).appendLine('*')
        appendLine()
        if (stats.speakers.isNotEmpty()) {
            appendLine("| Участник | Говорил(а) | Доля | Реплик |")
            appendLine("|---|---|---|---|")
            for (s in stats.speakers) {
                append("| ").append(t.speakerName(s.speaker).replace("|", "/"))
                append(" | ").append(TimeFormat.clock(s.talkMs))
                append(" | ").append((s.share * 100).roundToInt()).append(" %")
                append(" | ").append(s.turns).appendLine(" |")
            }
            appendLine()
        }
        if (stats.topics.isNotEmpty()) {
            append("**О чём говорили:** ").appendLine(stats.topics.joinToString(", "))
            appendLine()
        }
        val highlights = ConversationHighlights.compute(t)
        if (highlights.summary.isNotEmpty()) {
            appendLine("## Коротко")
            appendLine()
            highlights.summary.forEach { appendLine("- ${it.text} `${TimeFormat.stamp(it.startMs)}`") }
            appendLine()
        }
        if (highlights.actions.isNotEmpty()) {
            appendLine("## Задачи и договорённости")
            appendLine()
            for (action in highlights.actions) {
                append("- [ ] ").append(action.sentence.text).append(" — ").append(t.speakerName(action.sentence.speaker))
                action.deadline?.let { append(", срок: ").append(it.label()) }
                append(" `").append(TimeFormat.stamp(action.sentence.startMs)).appendLine('`')
            }
            appendLine()
        }
        if (highlights.questions.isNotEmpty()) {
            appendLine("## Вопросы")
            appendLine()
            highlights.questions.forEach {
                appendLine("- ${it.text} — ${t.speakerName(it.speaker)} `${TimeFormat.stamp(it.startMs)}`")
            }
            appendLine()
        }
        if (t.marks.isNotEmpty()) {
            append("**Отметки при записи:** ").appendLine(t.marks.joinToString(", ") { "`${TimeFormat.stamp(it)}`" })
            appendLine()
        }
        appendLine("---")
        for (turn in t.turns()) {
            appendLine()
            append("**").append(t.speakerName(turn.speaker)).append("** `")
            append(TimeFormat.stamp(turn.startMs)).appendLine('`')
            appendLine(turn.segments.joinToString(" ") { it.segment.text })
        }
    }

    fun plainText(t: Transcript): String = buildString {
        appendLine(t.title)
        val speakers = t.activeSpeakers()
        append(TimeFormat.date(t.createdAt))
        append(" · длительность ").append(TimeFormat.clock(t.durationMs))
        append(" · участников: ").append(speakers.size)
        appendLine()
        if (speakers.isNotEmpty()) appendLine("Участники: " + speakers.joinToString { it.name })
        for (turn in t.turns()) {
            appendLine()
            append('[').append(TimeFormat.stamp(turn.startMs)).append("] ")
            append(t.speakerName(turn.speaker)).appendLine(':')
            appendLine(turn.segments.joinToString(" ") { it.segment.text })
        }
    }

    fun srt(t: Transcript): String = buildString {
        t.segments.forEachIndexed { index, segment ->
            appendLine(index + 1)
            append(TimeFormat.subtitle(segment.startMs, ',')).append(" --> ")
            appendLine(TimeFormat.subtitle(segment.endMs, ','))
            append(t.speakerName(segment.speaker)).append(": ").appendLine(segment.text)
            appendLine()
        }
    }

    fun vtt(t: Transcript): String = buildString {
        appendLine("WEBVTT")
        appendLine()
        for (segment in t.segments) {
            append(TimeFormat.subtitle(segment.startMs, '.')).append(" --> ")
            appendLine(TimeFormat.subtitle(segment.endMs, '.'))
            append("<v ").append(t.speakerName(segment.speaker).replace(">", "")).append('>')
            appendLine(segment.text)
            appendLine()
        }
    }

    fun json(t: Transcript): String = json.encodeToString(
        ExportDocument.serializer(),
        ExportDocument(
            title = t.title,
            created = TimeFormat.shortDate(t.createdAt),
            durationSeconds = t.durationMs / 1000.0,
            source = t.sourceName,
            model = t.asrModelTitle,
            language = t.language?.let { Languages.title(it) ?: it },
            speakers = t.activeSpeakers().map { ExportSpeaker(it.id + 1, it.name) },
            marks = t.marks.map { it / 1000.0 },
            utterances = t.segments.map { s ->
                ExportUtterance(
                    speakerId = s.speaker + 1,
                    speaker = t.speakerName(s.speaker),
                    start = s.startMs / 1000.0,
                    end = s.endMs / 1000.0,
                    startTime = TimeFormat.stamp(s.startMs),
                    text = s.text,
                )
            },
        ),
    )

    @Serializable
    private data class ExportDocument(
        val title: String,
        val created: String,
        val durationSeconds: Double,
        val source: String,
        val model: String,
        val language: String?,
        val speakers: List<ExportSpeaker>,
        /** Moments marked during the recording, seconds. */
        val marks: List<Double>,
        val utterances: List<ExportUtterance>,
    )

    @Serializable
    private data class ExportSpeaker(val id: Int, val name: String)

    @Serializable
    private data class ExportUtterance(
        val speakerId: Int,
        val speaker: String,
        val start: Double,
        val end: Double,
        val startTime: String,
        val text: String,
    )
}
