package app.fenek.engine

import kotlin.math.max
import kotlin.random.Random

/** Building blocks for grouping unit-length voice fingerprints (cosine similarity). */
object Clustering {

    /** Spherical k-means with deterministic k-means++ seeding. */
    fun kmeans(e: List<FloatArray>, k: Int, iterations: Int = 25, seed: Int = 7): IntArray {
        val n = e.size
        val random = Random(seed)
        val centers = ArrayList<FloatArray>()
        centers += e[random.nextInt(n)]
        while (centers.size < k) {
            val dist = DoubleArray(n) { i -> centers.minOf { 1.0 - dot(e[i], it) }.coerceAtLeast(0.0) }
            val total = dist.sum()
            var chosen = n - 1
            if (total <= 0) {
                chosen = random.nextInt(n)
            } else {
                var pick = random.nextDouble() * total
                for (i in 0 until n) {
                    pick -= dist[i]
                    if (pick <= 0) {
                        chosen = i
                        break
                    }
                }
            }
            centers += e[chosen]
        }
        val labels = IntArray(n)
        repeat(iterations) {
            var changed = false
            for (i in 0 until n) {
                var best = 0
                var bestSim = Double.NEGATIVE_INFINITY
                for (c in 0 until k) {
                    val s = dot(e[i], centers[c])
                    if (s > bestSim) {
                        bestSim = s
                        best = c
                    }
                }
                if (labels[i] != best) changed = true
                labels[i] = best
            }
            for (c in 0 until k) {
                val sum = FloatArray(e[0].size)
                var count = 0
                for (i in 0 until n) if (labels[i] == c) {
                    for (d in sum.indices) sum[d] += e[i][d]
                    count++
                }
                // An empty group restarts from the point worst explained by its center.
                centers[c] = if (count > 0) Vectors.normalize(sum) else e[(0 until n).minBy { dot(e[it], centers[labels[it]]) }]
            }
            if (!changed && it > 0) return labels
        }
        return labels
    }

    /** Mean silhouette with cosine distance; large inputs are evaluated on an even subsample. */
    fun silhouette(e: List<FloatArray>, labels: IntArray, maxPoints: Int = 800): Double {
        val k = (labels.maxOrNull() ?: 0) + 1
        if (k < 2) return -1.0
        val step = max(1, e.size / maxPoints)
        val idx = e.indices.filter { it % step == 0 }
        var total = 0.0
        for (i in idx) {
            val sum = DoubleArray(k)
            val count = IntArray(k)
            for (j in idx) {
                if (i == j) continue
                sum[labels[j]] += 1.0 - dot(e[i], e[j])
                count[labels[j]]++
            }
            val own = labels[i]
            if (count[own] == 0) continue
            val a = sum[own] / count[own]
            val b = (0 until k).filter { it != own && count[it] > 0 }.minOfOrNull { sum[it] / count[it] } ?: continue
            total += (b - a) / max(a, b).coerceAtLeast(1e-9)
        }
        return total / idx.size
    }

    /** Majority vote over each element and its neighbours within the same group (a stretch of speech). */
    fun smooth(labels: IntArray, groups: IntArray): IntArray = IntArray(labels.size) { i ->
        val prev = labels.getOrNull(i - 1)?.takeIf { groups[i - 1] == groups[i] }
        val next = labels.getOrNull(i + 1)?.takeIf { groups[i + 1] == groups[i] }
        if (prev != null && prev == next && prev != labels[i]) prev else labels[i]
    }

    private fun dot(a: FloatArray, b: FloatArray): Double {
        var s = 0.0
        for (i in a.indices) s += a[i] * b[i]
        return s
    }
}
