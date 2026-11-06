package app.fenek.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Spectral clustering of voice fingerprints. Unlike k-means it does not split one talkative
 * person in two just because that person's windows are spread widely, and it does not give a
 * cluster to a handful of outliers (windows that straddle a change of speaker), which matters
 * when one participant speaks much more than the others.
 *
 * The affinity of two fingerprints is their cosine similarity (negative values count as 0). The
 * leading eigenvectors of the normalised affinity matrix are found by subspace iteration, which
 * is cheap for the few hundred fingerprints used for fitting.
 */
object SpectralClustering {

    /** Leading eigenvalues (descending) and, for every point, its coordinates in the eigenvector basis. */
    class Spectrum(val eigenvalues: DoubleArray, val rows: Array<DoubleArray>)

    private const val ITERATIONS = 80

    /**
     * @param points L2-normalised fingerprints
     * @param count number of leading eigenpairs to compute
     */
    fun spectrum(points: List<FloatArray>, count: Int): Spectrum {
        val n = points.size
        val p = count.coerceIn(1, n)
        val affinity = DoubleArray(n * n)
        val degree = DoubleArray(n)
        for (i in 0 until n) {
            for (j in i until n) {
                val a = max(0.0, dot(points[i], points[j]))
                affinity[i * n + j] = a
                affinity[j * n + i] = a
            }
        }
        for (i in 0 until n) {
            var s = 0.0
            for (j in 0 until n) s += affinity[i * n + j]
            degree[i] = max(s, 1e-9)
        }
        val norm = DoubleArray(n * n)
        for (i in 0 until n) for (j in 0 until n) {
            norm[i * n + j] = affinity[i * n + j] / sqrt(degree[i] * degree[j])
        }

        // Subspace iteration: columns of q converge to the leading eigenvectors.
        val random = Random(17)
        var q = Array(p) { DoubleArray(n) { random.nextDouble() - 0.5 } }
        orthonormalise(q)
        repeat(ITERATIONS) {
            q = Array(p) { c -> multiply(norm, n, q[c]) }
            orthonormalise(q)
        }
        // Rayleigh-Ritz: rotate the basis so that it consists of eigenvectors.
        val lq = Array(p) { c -> multiply(norm, n, q[c]) }
        val small = Array(p) { a -> DoubleArray(p) { b -> dot(q[a], lq[b]) } }
        val (values, vectors) = jacobi(small)
        val order = (0 until p).sortedByDescending { values[it] }
        val eigenvalues = DoubleArray(p) { values[order[it]] }
        val rows = Array(n) { i ->
            DoubleArray(p) { c ->
                var s = 0.0
                for (a in 0 until p) s += vectors[a][order[c]] * q[a][i]
                s
            }
        }
        return Spectrum(eigenvalues, rows)
    }

    /** Cluster label of every point for [k] speakers: k-means on the normalised leading eigenvectors. */
    fun labels(spectrum: Spectrum, k: Int): IntArray {
        val n = spectrum.rows.size
        val dims = k.coerceIn(1, spectrum.eigenvalues.size)
        if (dims == 1 || n <= 1) return IntArray(n)
        val rows = List(n) { i ->
            val v = FloatArray(dims) { spectrum.rows[i][it].toFloat() }
            Vectors.normalize(v)
        }
        val runs = (0 until RESTARTS).map { Clustering.kmeans(rows, dims, seed = it) }
        return runs.maxBy { labels -> cohesion(rows, labels, dims) }
    }

    /**
     * Number of speakers read off the largest gap between consecutive leading eigenvalues,
     * between [min] and [max] speakers.
     */
    fun estimateSpeakers(spectrum: Spectrum, min: Int = 1, max: Int = MAX_SPEAKERS): Int {
        val values = spectrum.eigenvalues
        val last = minOf(max, values.size - 1)
        if (last < min) return min.coerceAtMost(values.size)
        var best = min
        var bestGap = Double.NEGATIVE_INFINITY
        for (k in min..last) {
            val gap = values[k - 1] - values[k]
            if (gap > bestGap) {
                bestGap = gap
                best = k
            }
        }
        return best
    }

    const val MAX_SPEAKERS = 6
    private const val RESTARTS = 8

    private fun cohesion(rows: List<FloatArray>, labels: IntArray, k: Int): Double {
        val centers = Array(k) { FloatArray(rows[0].size) }
        for (i in rows.indices) for (d in rows[i].indices) centers[labels[i]][d] += rows[i][d]
        for (c in 0 until k) centers[c] = Vectors.normalize(centers[c])
        var s = 0.0
        for (i in rows.indices) s += dot(rows[i], centers[labels[i]])
        return s
    }

    private fun dot(a: FloatArray, b: FloatArray): Double {
        var s = 0.0
        for (i in a.indices) s += a[i] * b[i]
        return s
    }

    private fun dot(a: DoubleArray, b: DoubleArray): Double {
        var s = 0.0
        for (i in a.indices) s += a[i] * b[i]
        return s
    }

    private fun multiply(matrix: DoubleArray, n: Int, v: DoubleArray): DoubleArray {
        val out = DoubleArray(n)
        for (i in 0 until n) {
            var s = 0.0
            val base = i * n
            for (j in 0 until n) s += matrix[base + j] * v[j]
            out[i] = s
        }
        return out
    }

    /** Modified Gram-Schmidt on the vectors in [q] (in place). */
    private fun orthonormalise(q: Array<DoubleArray>) {
        for (c in q.indices) {
            for (b in 0 until c) {
                val proj = dot(q[c], q[b])
                for (i in q[c].indices) q[c][i] -= proj * q[b][i]
            }
            val norm = sqrt(dot(q[c], q[c]))
            if (norm < 1e-12) {
                // A degenerate direction: restart it from something orthogonal to the others.
                val random = Random(c + 101)
                for (i in q[c].indices) q[c][i] = random.nextDouble() - 0.5
                for (b in 0 until c) {
                    val proj = dot(q[c], q[b])
                    for (i in q[c].indices) q[c][i] -= proj * q[b][i]
                }
                val again = sqrt(dot(q[c], q[c]))
                for (i in q[c].indices) q[c][i] /= again
            } else {
                for (i in q[c].indices) q[c][i] /= norm
            }
        }
    }

    /** Eigenvalues and eigenvectors (as columns: vectors[row][column]) of a small symmetric matrix. */
    private fun jacobi(matrix: Array<DoubleArray>): Pair<DoubleArray, Array<DoubleArray>> {
        val n = matrix.size
        val a = Array(n) { matrix[it].copyOf() }
        val v = Array(n) { i -> DoubleArray(n) { j -> if (i == j) 1.0 else 0.0 } }
        repeat(60) {
            var off = 0.0
            for (i in 0 until n) for (j in i + 1 until n) off += a[i][j] * a[i][j]
            if (off < 1e-20) return DoubleArray(n) { a[it][it] } to v
            for (p in 0 until n) for (q in p + 1 until n) {
                if (abs(a[p][q]) < 1e-15) continue
                val theta = (a[q][q] - a[p][p]) / (2 * a[p][q])
                val t = (if (theta >= 0) 1.0 else -1.0) / (abs(theta) + sqrt(theta * theta + 1))
                val c = 1 / sqrt(t * t + 1)
                val s = t * c
                for (k in 0 until n) {
                    val akp = a[k][p]
                    val akq = a[k][q]
                    a[k][p] = c * akp - s * akq
                    a[k][q] = s * akp + c * akq
                }
                for (k in 0 until n) {
                    val apk = a[p][k]
                    val aqk = a[q][k]
                    a[p][k] = c * apk - s * aqk
                    a[q][k] = s * apk + c * aqk
                }
                for (k in 0 until n) {
                    val vkp = v[k][p]
                    val vkq = v[k][q]
                    v[k][p] = c * vkp - s * vkq
                    v[k][q] = s * vkp + c * vkq
                }
            }
        }
        return DoubleArray(n) { a[it][it] } to v
    }
}
