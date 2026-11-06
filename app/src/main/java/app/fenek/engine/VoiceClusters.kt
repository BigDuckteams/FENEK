package app.fenek.engine

/**
 * Groups the voice fingerprints of a whole recording into participants.
 *
 * Spectral clustering needs an affinity matrix, which is quadratic in the number of points, so
 * long recordings are clustered on an evenly spaced sample of at most [MAX_FIT] fingerprints
 * and the remaining ones join the nearest participant.
 */
object VoiceClusters {

    const val MAX_FIT = 700
    private const val MIN_SHARE = 0.04

    /**
     * @param numSpeakers the exact number of participants, or null to detect it
     * @param separation 0..1; for detection, higher values accept weaker evidence of one more voice
     * @return a participant index (0 until the number of participants) for every fingerprint
     */
    fun cluster(vectors: List<FloatArray>, numSpeakers: Int?, separation: Float): IntArray {
        val n = vectors.size
        if (n == 0) return IntArray(0)
        if (numSpeakers == 1 || n < 2) return IntArray(n)

        val stride = (n + MAX_FIT - 1) / MAX_FIT
        val fit = (0 until n step stride).map { vectors[it] }
        val spectrum = SpectralClustering.spectrum(fit, (SpectralClustering.MAX_SPEAKERS + 2).coerceAtLeast((numSpeakers ?: 0) + 2))
        val k = (numSpeakers ?: estimate(fit, spectrum, separation)).coerceIn(1, fit.size)
        if (k == 1) return IntArray(n)

        val fitLabels = SpectralClustering.labels(spectrum, k)
        if (fit.size == n) return Vectors.renumber(fitLabels)

        val dim = vectors[0].size
        val centroids = Array(k) { FloatArray(dim) }
        for ((i, label) in fitLabels.withIndex()) for (d in 0 until dim) centroids[label][d] += fit[i][d]
        for (c in 0 until k) centroids[c] = Vectors.normalize(centroids[c])
        val labels = IntArray(n)
        for (i in 0 until n) {
            var best = 0
            var bestSim = Double.NEGATIVE_INFINITY
            for (c in 0 until k) {
                val s = Vectors.cosine(vectors[i], centroids[c])
                if (s > bestSim) {
                    bestSim = s
                    best = c
                }
            }
            labels[i] = best
        }
        return Vectors.renumber(labels)
    }

    /**
     * Number of voices when the user did not say: the split with the best silhouette among
     * 2..[SpectralClustering.MAX_SPEAKERS] voices, or one voice if no split is convincing. A split
     * with a tiny group is ignored: that group is made of windows that straddle a change of speaker.
     */
    private fun estimate(points: List<FloatArray>, spectrum: SpectralClustering.Spectrum, separation: Float): Int {
        var best = silhouetteFloor(separation)
        var bestK = 1
        for (k in 2..minOf(SpectralClustering.MAX_SPEAKERS, points.size - 1)) {
            val labels = SpectralClustering.labels(spectrum, k)
            val counts = IntArray(k).also { c -> labels.forEach { c[it]++ } }
            if (counts.min() < MIN_SHARE * points.size) continue
            val silhouette = Clustering.silhouette(points, labels)
            if (silhouette > best) {
                best = silhouette
                bestK = k
            }
        }
        return bestK
    }

    /** Silhouette a split must beat: 0.25 when similar voices should be merged, 0.05 when they should be split. */
    fun silhouetteFloor(separation: Float): Double = 0.25 - 0.20 * separation.coerceIn(0f, 1f)
}
