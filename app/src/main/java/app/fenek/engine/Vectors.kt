package app.fenek.engine

import kotlin.math.sqrt

/** Small helpers for voice fingerprints (embeddings). */
object Vectors {

    /** Renumbers arbitrary labels to 0, 1, … in order of first occurrence. */
    fun renumber(labels: IntArray): IntArray {
        val mapping = HashMap<Int, Int>()
        return IntArray(labels.size) { i -> mapping.getOrPut(labels[i]) { mapping.size } }
    }

    fun cosine(a: FloatArray, b: FloatArray): Double {
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        return if (na == 0.0 || nb == 0.0) 0.0 else dot / sqrt(na * nb)
    }

    fun normalize(v: FloatArray): FloatArray {
        var norm = 0.0
        for (x in v) norm += x * x
        norm = sqrt(norm)
        if (norm == 0.0) return v
        return FloatArray(v.size) { (v[it] / norm).toFloat() }
    }
}
