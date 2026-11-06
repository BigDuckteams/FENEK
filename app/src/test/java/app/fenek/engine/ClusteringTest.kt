package app.fenek.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ClusteringTest {

    private val random = Random(3)

    private fun voice(axis: Int, dim: Int = 16): FloatArray {
        val v = FloatArray(dim) { (random.nextFloat() * 2 - 1) * 0.15f }
        v[axis] += 1f
        return Vectors.normalize(v)
    }

    @Test
    fun kmeansSeparatesTwoVoices() {
        val points = listOf(voice(0), voice(1), voice(0), voice(0), voice(1), voice(1))
        val labels = Clustering.kmeans(points, 2)
        assertEquals(labels[0], labels[2])
        assertEquals(labels[0], labels[3])
        assertEquals(labels[1], labels[4])
        assertTrue(labels[0] != labels[1])
    }

    @Test
    fun silhouetteIsHighForSeparatedGroupsAndLowForAMixedOne() {
        val points = List(30) { voice(it % 2) }
        val right = IntArray(30) { it % 2 }
        val wrong = IntArray(30) { if (it < 15) 0 else 1 }
        assertTrue(Clustering.silhouette(points, right) > 0.7)
        assertTrue(Clustering.silhouette(points, wrong) < 0.2)
        assertEquals(-1.0, Clustering.silhouette(points, IntArray(30)), 1e-9)
    }

    @Test
    fun smoothingFixesIsolatedFlipsWithinARegion() {
        val labels = intArrayOf(0, 0, 1, 0, 0, 1, 1, 0, 1)
        val regions = intArrayOf(0, 0, 0, 0, 0, 0, 0, 1, 1)
        assertArrayEquals(intArrayOf(0, 0, 0, 0, 0, 1, 1, 0, 1), Clustering.smooth(labels, regions))
    }

    @Test
    fun renumbersByFirstOccurrence() {
        assertArrayEquals(intArrayOf(0, 1, 0, 2), Vectors.renumber(intArrayOf(7, 3, 7, 5)))
    }

    @Test
    fun cosineOfOrthogonalAndEqualVectors() {
        assertEquals(0.0, Vectors.cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 1e-9)
        assertEquals(1.0, Vectors.cosine(floatArrayOf(3f, 4f), floatArrayOf(3f, 4f)), 1e-9)
    }
}
