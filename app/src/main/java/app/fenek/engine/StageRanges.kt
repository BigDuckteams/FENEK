package app.fenek.engine

/** Share of the whole progress bar that every stage of a job takes. */
object StageRanges {

    private val plain = mapOf(
        Stage.PREPARING to (0f to 0.02f),
        Stage.DECODING to (0.02f to 0.12f),
        Stage.DIARIZING to (0.12f to 0.55f),
        Stage.TRANSCRIBING to (0.55f to 0.99f),
        Stage.SAVING to (0.99f to 1f),
    )

    /** With the language model the recognition ends earlier and its two stages follow. */
    private val withLlm = mapOf(
        Stage.PREPARING to (0f to 0.02f),
        Stage.DECODING to (0.02f to 0.08f),
        Stage.DIARIZING to (0.08f to 0.35f),
        Stage.TRANSCRIBING to (0.35f to 0.65f),
        Stage.CORRECTING to (0.65f to 0.88f),
        Stage.SUMMARIZING to (0.88f to 0.98f),
        Stage.SAVING to (0.98f to 1f),
    )

    fun of(withLlm: Boolean): Map<Stage, Pair<Float, Float>> = if (withLlm) this.withLlm else plain

    /** Overall progress of [fraction] (0..1) of [stage]. */
    fun overall(ranges: Map<Stage, Pair<Float, Float>>, stage: Stage, fraction: Float): Float {
        val (from, to) = ranges.getValue(stage)
        return from + (to - from) * fraction.coerceIn(0f, 1f)
    }
}
