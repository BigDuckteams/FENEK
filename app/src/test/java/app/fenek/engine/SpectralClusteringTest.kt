package app.fenek.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SpectralClusteringTest {

    private val random = Random(5)
    private val dim = 24

    /** A voice: a direction plus noise, as the windows of one speaker scatter around it. */
    private fun voice(speaker: Int, noise: Float = 0.45f): FloatArray {
        val v = FloatArray(dim) { (random.nextFloat() * 2 - 1) * noise }
        v[speaker] += 1f
        return Vectors.normalize(v)
    }

    private fun samples(vararg counts: Int): Pair<List<FloatArray>, IntArray> {
        val truth = counts.flatMapIndexed { speaker, n -> List(n) { speaker } }.toIntArray()
        val order = truth.indices.shuffled(random)
        return order.map { voice(truth[it]) } to IntArray(truth.size) { truth[order[it]] }
    }

    /** Share of points whose cluster agrees with the best one-to-one matching of clusters to speakers. */
    private fun accuracy(labels: IntArray, truth: IntArray, k: Int): Double {
        val matrix = Array(k) { IntArray(k) }
        for (i in labels.indices) matrix[labels[i]][truth[i]]++
        var best = 0
        fun search(row: Int, used: Int, sum: Int) {
            if (row == k) {
                best = maxOf(best, sum)
                return
            }
            for (c in 0 until k) if (used and (1 shl c) == 0) search(row + 1, used or (1 shl c), sum + matrix[row][c])
        }
        search(0, 0, 0)
        return best.toDouble() / labels.size
    }

    @Test
    fun separatesSpeakersOfVeryDifferentTalkativeness() {
        // One person speaks ten times more than the others.
        val (points, truth) = samples(150, 18, 14, 22)
        val spectrum = SpectralClustering.spectrum(points, 8)
        val labels = SpectralClustering.labels(spectrum, 4)
        assertTrue(accuracy(labels, truth, 4) > 0.95)
    }

    @Test
    fun eigenvaluesAreSortedAndBounded() {
        val (points, _) = samples(40, 40, 40)
        val values = SpectralClustering.spectrum(points, 6).eigenvalues
        for (i in 1 until values.size) assertTrue(values[i - 1] >= values[i] - 1e-9)
        assertEquals(1.0, values[0], 1e-6)
        assertTrue(values.all { it <= 1.0 + 1e-6 && it >= -1.0 - 1e-6 })
    }

    @Test
    fun threeClearVoicesGiveThreeLargeEigenvalues() {
        val (points, _) = samples(60, 50, 40)
        val values = SpectralClustering.spectrum(points, 8).eigenvalues
        // Three groups: three large eigenvalues, then a clear drop.
        assertTrue(values[2] > 0.5)
        assertTrue(values[3] < values[2] - 0.3)
        assertEquals(3, SpectralClustering.estimateSpeakers(SpectralClustering.spectrum(points, 8), min = 2))
    }

    @Test
    fun twoSpeakersAreSplitCleanly() {
        val (points, truth) = samples(70, 55)
        val labels = SpectralClustering.labels(SpectralClustering.spectrum(points, 6), 2)
        assertEquals(1.0, accuracy(labels, truth, 2), 1e-9)
    }
}
