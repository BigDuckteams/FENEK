package app.fenek.llm

import app.fenek.data.Segment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * @property segments the phrases in their original order; only the text of changed phrases differs
 * @property changed how many phrases got a new text
 * @property answered how many batches the model answered in a usable way
 */
class CorrectionResult(val segments: List<Segment>, val changed: Int, val answered: Int, val failedBatches: Int) {
    /** The phrases really went through the model (at least one usable answer). */
    val applied: Boolean get() = answered > 0
}

/**
 * Restores punctuation and casing and fixes obvious recognition slips with a language model.
 * It never adds, removes or reorders phrases and never touches speakers or times; every answer
 * passes [CorrectionGuard], otherwise the recognized text is kept.
 */
class TextCorrector(
    private val engine: LlmEngine,
    private val maxBatchPhrases: Int = 8,
    private val maxBatchChars: Int = 1_800,
    private val timeoutMs: Long = 60_000,
    private val budget: LlmBudget? = null,
) {

    suspend fun correct(segments: List<Segment>, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): CorrectionResult {
        val candidates = segments.indices.filter { CorrectionGuard.words(segments[it].text).size >= MIN_WORDS }
        val result = segments.toMutableList()
        var changed = 0
        var answered = 0
        var failed = 0
        var done = 0
        onProgress(0, candidates.size)
        for (batch in batches(segments, candidates)) {
            currentCoroutineContext().ensureActive()
            // Out of time: the phrases that are left keep their recognized text.
            val limit = if (budget == null) timeoutMs else budget.request(timeoutMs) ?: break
            val answer = try {
                engine.generate(
                    system = SYSTEM,
                    user = prompt(segments, batch),
                    maxTokens = maxTokens(batch.sumOf { segments[it].text.length }),
                    timeoutMs = limit,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: LlmException) {
                failed++
                done += batch.size
                onProgress(done, candidates.size)
                continue
            }
            val parsed = parse(answer, batch.size)
            if (parsed.isEmpty()) {
                failed++
            } else {
                answered++
                for ((position, text) in parsed) {
                    val index = batch[position]
                    val recognized = segments[index].text
                    if (text != recognized && CorrectionGuard.accept(recognized, text)) {
                        result[index] = segments[index].copy(text = text, rawText = recognized)
                        changed++
                    }
                }
            }
            done += batch.size
            onProgress(done, candidates.size)
        }
        return CorrectionResult(result, changed, answered, failed)
    }

    /** Groups the candidate phrases into requests of limited size, keeping their order. */
    internal fun batches(segments: List<Segment>, candidates: List<Int>): List<List<Int>> {
        val result = ArrayList<List<Int>>()
        var current = ArrayList<Int>()
        var chars = 0
        for (index in candidates) {
            val length = segments[index].text.length
            if (current.isNotEmpty() && (current.size >= maxBatchPhrases || chars + length > maxBatchChars)) {
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

    private fun prompt(segments: List<Segment>, batch: List<Int>): String = buildString {
        segments.getOrNull(batch.first() - 1)?.let { appendLine("Контекст до (не исправлять): ${it.text}") }
        batch.forEachIndexed { position, index -> appendLine("${position + 1}: ${segments[index].text}") }
        segments.getOrNull(batch.last() + 1)?.let { appendLine("Контекст после (не исправлять): ${it.text}") }
    }.trimEnd()

    /** Number of generated tokens allowed for [chars] characters of input. */
    private fun maxTokens(chars: Int): Int = ((chars / CHARS_PER_TOKEN + 1) * 1.5).toInt() + 16

    private companion object {
        /** Phrases of two words or fewer are not sent to the model. */
        const val MIN_WORDS = 3
        /** Pessimistic for Russian: a token is about 2 to 3 characters. */
        const val CHARS_PER_TOKEN = 2.0
        val line = Regex("^\\s*(\\d+)\\s*[:.)]\\s*(.+?)\\s*$")

        // An example instead of a placeholder: a small model copies "N:" letter by letter. The steps are
        // spelled out because a bigger one, asked only to "fix punctuation", returned its input as it was.
        const val SYSTEM = "Ты редактор расшифровок речи. Строки ниже получены распознаванием и могут быть без знаков " +
            "препинания и заглавных букв. Исправь каждую строку: начни с заглавной буквы, поставь запятые, " +
            "а в конце — точку, вопросительный или восклицательный знак, имена собственные напиши с заглавной буквы. " +
            "Явные ошибки распознавания исправь. Слова не заменяй другими, не добавляй и не убирай, не перефразируй, " +
            "числа не меняй, строки не объединяй и не разделяй. Язык текста сохраняй. " +
            "Строки «Контекст» нужны только для понимания, их не повторяй. " +
            "Отвечай только пронумерованными строками, номера те же, что на входе, без пояснений.\n\n" +
            "Пример входа:\n1: привет как дела\n2: нормально а у тебя иван\n" +
            "Пример ответа:\n1: Привет, как дела?\n2: Нормально, а у тебя, Иван?"

        /** Answer lines as (zero-based position in the batch → text); lines with unknown numbers are dropped. */
        fun parse(answer: String, size: Int): Map<Int, String> {
            val result = LinkedHashMap<Int, String>()
            for (raw in answer.lineSequence()) {
                val match = line.matchEntire(raw) ?: continue
                val position = match.groupValues[1].toInt() - 1
                if (position in 0 until size && position !in result) result[position] = match.groupValues[2]
            }
            return result
        }
    }
}
