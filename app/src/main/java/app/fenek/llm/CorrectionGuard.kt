package app.fenek.llm

import kotlin.math.max

/**
 * Decides whether the text that the language model returned for a phrase may replace the
 * recognized text. The model is only allowed to fix punctuation, casing and obvious slips; if it
 * rewrote the phrase, or swapped a word for a different one ("хорошо" → "правильно"), the
 * recognized text stays.
 */
object CorrectionGuard {

    /** Most of the words that may differ, as a share of the longer phrase. */
    const val MAX_CHANGED_SHARE = 0.30
    const val MIN_LENGTH_RATIO = 0.7
    const val MAX_LENGTH_RATIO = 1.4

    /** A replaced word must look like the original: at most this share of its letters differ. */
    const val MAX_TYPO_SHARE = 0.40

    private val word = Regex("[\\p{L}\\p{N}]+(?:['-][\\p{L}\\p{N}]+)*")
    private val digits = Regex("\\d+")

    fun accept(recognized: String, corrected: String): Boolean {
        if (corrected.isBlank()) return false
        val before = words(recognized)
        val after = words(corrected)
        if (before.isEmpty() || after.isEmpty()) return false
        if (after.size < before.size * MIN_LENGTH_RATIO || after.size > before.size * MAX_LENGTH_RATIO) return false
        if (!numbersKept(recognized, corrected)) return false
        val alignment = align(before, after)
        if (alignment.edits > MAX_CHANGED_SHARE * max(before.size, after.size)) return false
        // Fixing a typo is fine, putting another word in its place is not.
        return alignment.substitutions.all { (a, b) -> looksLikeTypo(a, b) }
    }

    /** Words without case and punctuation, "ё" written as "е". */
    internal fun words(text: String): List<String> =
        word.findAll(text.lowercase().replace('ё', 'е')).map { it.value }.toList()

    private fun numbersKept(recognized: String, corrected: String): Boolean {
        val after = digits.findAll(corrected).map { it.value }.toSet()
        return digits.findAll(recognized).all { it.value in after }
    }

    internal class Alignment(val edits: Int, val substitutions: List<Pair<String, String>>)

    /** Word-level Levenshtein alignment: the number of edits and the pairs of words that were swapped. */
    internal fun align(a: List<String>, b: List<String>): Alignment {
        val cost = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in 0..a.size) cost[i][0] = i
        for (j in 0..b.size) cost[0][j] = j
        for (i in 1..a.size) {
            for (j in 1..b.size) {
                val swap = if (a[i - 1] == b[j - 1]) 0 else 1
                cost[i][j] = minOf(cost[i - 1][j] + 1, cost[i][j - 1] + 1, cost[i - 1][j - 1] + swap)
            }
        }
        val swapped = ArrayList<Pair<String, String>>()
        var i = a.size
        var j = b.size
        while (i > 0 && j > 0) {
            val same = a[i - 1] == b[j - 1]
            when {
                cost[i][j] == cost[i - 1][j - 1] + (if (same) 0 else 1) -> {
                    if (!same) swapped += a[i - 1] to b[j - 1]
                    i--
                    j--
                }
                cost[i][j] == cost[i - 1][j] + 1 -> i--
                else -> j--
            }
        }
        return Alignment(cost[a.size][b.size], swapped)
    }

    private fun looksLikeTypo(a: String, b: String): Boolean =
        charDistance(a, b) <= MAX_TYPO_SHARE * max(a.length, b.length)

    private fun charDistance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val swap = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + swap)
            }
            val tmp = previous
            previous = current
            current = tmp
        }
        return previous[b.length]
    }

    /** Word-level Levenshtein distance. */
    internal fun distance(a: List<String>, b: List<String>): Int = align(a, b).edits
}
