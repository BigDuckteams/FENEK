package app.fenek.llm

/**
 * How long the language model may work on one job. The model only polishes a transcript, so a slow
 * phone must not spend minutes on it. The budget grows with the recording but has a floor, so that a
 * short recording still gets the time of one ordinary request, and a ceiling. Every request gets what
 * is left, up to its own limit, and a stage ends when too little is left for a useful answer.
 * Loading the model is not counted: the budget starts when the model is ready.
 */
class LlmBudget(val totalMs: Long, private val now: () -> Long = { System.nanoTime() / 1_000_000 }) {

    private val deadline = now() + totalMs

    /** Milliseconds left; 0 when the budget is spent. */
    val remainingMs: Long get() = (deadline - now()).coerceAtLeast(0)

    /** The time limit of the next request: [limitMs] at most, what is left at most; null when the rest is not worth a request. */
    fun request(limitMs: Long): Long? {
        val left = remainingMs
        return if (left < MIN_REQUEST_MS) null else minOf(limitMs, left)
    }

    /** A budget for one stage, which may use [fraction] of what is left now (the rest stays for the next stage). */
    fun part(fraction: Double): LlmBudget = LlmBudget((remainingMs * fraction).toLong(), now)

    companion object {
        /** The time of one ordinary summary request, so that a short recording never gets less than that. */
        const val MIN_TOTAL_MS = 120_000L
        const val MAX_TOTAL_MS = 600_000L
        /** Prompt processing alone takes about this long on a mid-range phone. */
        const val MIN_REQUEST_MS = 15_000L
        private const val SHARE_OF_RECORDING = 0.5

        /** Half the length of the recording, between [MIN_TOTAL_MS] and [MAX_TOTAL_MS]. */
        fun forRecording(recordingMs: Long, now: () -> Long = { System.nanoTime() / 1_000_000 }): LlmBudget =
            LlmBudget((recordingMs * SHARE_OF_RECORDING).toLong().coerceIn(MIN_TOTAL_MS, MAX_TOTAL_MS), now)
    }
}
