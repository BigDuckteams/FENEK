package app.fenek.llm

import app.fenek.data.LlmItem
import app.fenek.data.Segment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The summary points and the tasks the language model found in a conversation. */
class WrittenHighlights(val summary: List<LlmItem>, val tasks: List<LlmItem>)

/**
 * Asks the language model for a short summary and for tasks and agreements. The answer is forced
 * into a JSON shape by a grammar, every item must point to an existing phrase, and anything else
 * is dropped. Long conversations are processed in windows and merged. `null` means the model gave
 * nothing usable and the rule-based highlights should be used.
 */
class HighlightsWriter(
    private val engine: LlmEngine,
    private val windowChars: Int = 3_000,
    private val timeoutMs: Long = 120_000,
    private val budget: LlmBudget? = null,
) {

    suspend fun write(segments: List<Segment>, speakerName: (Int) -> String): WrittenHighlights? {
        if (segments.size < MIN_PHRASES) return null
        val windows = windows(segments)
        val summary = ArrayList<LlmItem>()
        val tasks = ArrayList<LlmItem>()
        var answered = 0
        for (number in coverageOrder(windows.size)) {
            val window = windows[number]
            currentCoroutineContext().ensureActive()
            // Out of time: the windows that are left stay without a summary.
            val limit = if (budget == null) timeoutMs else budget.request(timeoutMs) ?: break
            val answer = try {
                engine.generate(
                    system = system(single = windows.size == 1),
                    user = window.joinToString("\n") { "${it + 1} [${speakerName(segments[it].speaker)}]: ${segments[it].text}" },
                    grammar = GRAMMAR,
                    maxTokens = MAX_TOKENS,
                    timeoutMs = limit,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: LlmException) {
                continue
            }
            val parsed = parse(answer, segments, window, speakerName) ?: continue
            answered++
            summary += parsed.summary
            tasks += parsed.tasks
        }
        if (answered == 0) return null
        val mergedSummary = spread(dedupe(summary.sortedBy { it.startMs }), MAX_SUMMARY)
        val mergedTasks = dedupe(tasks.sortedBy { it.startMs }).take(MAX_TASKS)
        // Nothing found means nothing usable: the rules may still find something.
        if (mergedSummary.isEmpty() && mergedTasks.isEmpty()) return null
        return WrittenHighlights(mergedSummary, mergedTasks)
    }

    /** Consecutive phrase ranges of about [windowChars] characters each. */
    internal fun windows(segments: List<Segment>): List<List<Int>> {
        val result = ArrayList<List<Int>>()
        var current = ArrayList<Int>()
        var chars = 0
        for (index in segments.indices) {
            val length = segments[index].text.length + 12
            if (current.isNotEmpty() && chars + length > windowChars) {
                result += current
                current = ArrayList()
                chars = 0
            }
            current += index
            chars += length
        }
        if (current.isNotEmpty()) result += current
        return result
    }

    /**
     * The order the windows are asked in: the first, the last, then the middle of every gap and so on.
     * When the time budget ends the stage early, the windows that were done are spread over the whole
     * conversation instead of being only its beginning.
     */
    internal fun coverageOrder(count: Int): List<Int> {
        if (count <= 2) return (0 until count).toList()
        val order = arrayListOf(0, count - 1)
        val gaps = ArrayDeque<IntRange>()
        gaps += 1..count - 2
        while (gaps.isNotEmpty()) {
            val gap = gaps.removeFirst()
            if (gap.isEmpty()) continue
            val middle = (gap.first + gap.last) / 2
            order += middle
            gaps += gap.first until middle
            gaps += middle + 1..gap.last
        }
        return order
    }

    /** Small models copy the "Спикер 1:" label of the line they took the sentence from. */
    internal fun withoutSpeakerLabel(text: String, speaker: String): String {
        var result = text.trim()
        for (label in listOf("[$speaker]:", "$speaker:", "[$speaker]")) {
            if (result.startsWith(label, ignoreCase = true)) {
                result = result.substring(label.length).trim()
                break
            }
        }
        return result
    }

    private fun parse(
        answer: String,
        segments: List<Segment>,
        window: List<Int>,
        speakerName: (Int) -> String,
    ): WrittenHighlights? {
        val root = try {
            Json.parseToJsonElement(answer.trim()).jsonObject
        } catch (e: Exception) {
            return null
        }
        fun items(key: String): List<LlmItem> = (root[key] as? JsonArray).orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val number = item["phrase"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val index = number - 1
            // Only the phrases of this window were shown to the model.
            if (index !in window) return@mapNotNull null
            val text = withoutSpeakerLabel(item["text"]?.jsonPrimitive?.contentOrNull.orEmpty(), speakerName(segments[index].speaker))
            if (text.isEmpty()) return@mapNotNull null
            LlmItem(segments[index].startMs, segments[index].speaker, text)
        }
        return WrittenHighlights(items("summary"), items("tasks"))
    }

    /** Drops items whose words mostly repeat an earlier item. */
    internal fun dedupe(items: List<LlmItem>): List<LlmItem> {
        val kept = ArrayList<LlmItem>()
        val keptWords = ArrayList<Set<String>>()
        for (item in items) {
            val words = CorrectionGuard.words(item.text).toSet()
            if (keptWords.none { overlap(it, words) > MAX_OVERLAP }) {
                kept += item
                keptWords += words
            }
        }
        return kept
    }

    private fun overlap(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return if (a == b) 1.0 else 0.0
        val common = a.count { it in b }
        return common.toDouble() / (a.size + b.size - common)
    }

    /** At most [limit] items, taken evenly across the conversation. */
    internal fun spread(items: List<LlmItem>, limit: Int): List<LlmItem> {
        if (items.size <= limit) return items
        return (0 until limit).map { items[it * items.size / limit] }
    }

    private fun system(single: Boolean): String =
        "Ты помощник, который кратко излагает разговор. Дан диалог: у каждой реплики есть номер и имя говорящего. " +
            "Верни JSON с двумя списками. summary — ключевые мысли разговора, своими словами, " +
            (if (single) "от 2 до 4 пунктов" else "1–2 пункта") +
            ", каждый — одно короткое предложение. tasks — задачи и договорённости: обещания, просьбы и планы, " +
            "не больше 6, вместе со сроком, если он назван; если задач нет, список пустой. " +
            "Не пересказывай диалог целиком и не копируй реплики подряд. " +
            "В каждом пункте phrase — номер реплики, из которой он взят. Пиши на языке диалога, ничего не выдумывай."

    companion object {
        const val MIN_PHRASES = 3
        const val MAX_SUMMARY = 5
        const val MAX_TASKS = 50
        /** Room for 4 summary points and 6 tasks of at most 140 characters each. */
        private const val MAX_TOKENS = 800
        private const val MAX_OVERLAP = 0.5

        /**
         * GBNF: an object with `summary` (1 to 4 items) and `tasks` (0 to 6 items), each item
         * `{phrase, text}` with a text of at most 140 characters. Everything is bounded: with open-ended
         * lists a small model keeps copying the dialogue until the token limit and the JSON is cut
         * off, and with open-ended whitespace it can emit blanks forever (see llama.cpp's json.gbnf).
         */
        val GRAMMAR = """
            root ::= "{" ws "\"summary\"" ws ":" ws summary ws "," ws "\"tasks\"" ws ":" ws tasks ws "}"
            summary ::= "[" ws item (ws "," ws item){0,3} ws "]"
            tasks ::= "[" ws (item (ws "," ws item){0,5})? ws "]"
            item ::= "{" ws "\"phrase\"" ws ":" ws number ws "," ws "\"text\"" ws ":" ws string ws "}"
            number ::= [0-9] [0-9]{0,5}
            string ::= "\"" char{1,140} "\""
            char ::= [^"\\\x7F\x00-\x1F] | "\\" (["\\/bfnrt] | "u" [0-9a-fA-F]{4})
            ws ::= | " " | "\n" [ \t]{0,20}
        """.trimIndent()
    }
}
