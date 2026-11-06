package app.fenek.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class VoiceClustersTest {

    private val random = Random(11)
    private val dim = 24

    private fun voice(speaker: Int, noise: Float = 0.4f): FloatArray {
        val v = FloatArray(dim) { (random.nextFloat() * 2 - 1) * noise }
        v[speaker] += 1f
        return Vectors.normalize(v)
    }

    private fun conversation(vararg counts: Int): Pair<List<FloatArray>, IntArray> {
        val truth = counts.flatMapIndexed { speaker, n -> List(n) { speaker } }
        val order = truth.indices.shuffled(random)
        return order.map { voice(truth[it]) } to IntArray(truth.size) { truth[order[it]] }
    }

    /** Whether two labelings split the points the same way (cluster names do not matter). */
    private fun sameGrouping(labels: IntArray, truth: IntArray): Boolean {
        val mapping = HashMap<Int, Int>()
        return labels.indices.all { mapping.getOrPut(labels[it]) { truth[it] } == truth[it] } &&
            labels.distinct().size == truth.distinct().size
    }

    @Test
    fun theGivenNumberOfParticipantsIsUsed() {
        val (points, truth) = conversation(80, 30, 25)
        val labels = VoiceClusters.cluster(points, numSpeakers = 3, separation = 0.5f)
        assertTrue(sameGrouping(labels, truth))
        assertEquals(3, VoiceClusters.cluster(points, 3, 0.5f).distinct().size)
    }

    @Test
    fun oneParticipantIsOneCluster() {
        val (points, _) = conversation(60)
        assertEquals(1, VoiceClusters.cluster(points, numSpeakers = 1, separation = 0.5f).distinct().size)
        assertEquals(1, VoiceClusters.cluster(points, numSpeakers = null, separation = 0.5f).distinct().size)
    }

    @Test
    fun twoAndThreeVoicesAreFoundWithoutBeingTold() {
        val (two, twoTruth) = conversation(70, 55)
        assertTrue(sameGrouping(VoiceClusters.cluster(two, null, 0.5f), twoTruth))
        val (three, threeTruth) = conversation(60, 45, 35)
        assertTrue(sameGrouping(VoiceClusters.cluster(three, null, 0.5f), threeTruth))
    }

    @Test
    fun longRecordingsAreClusteredOnASampleAndEveryWindowIsLabelled() {
        val (points, truth) = conversation(1100, 600)
        assertTrue(points.size > VoiceClusters.MAX_FIT)
        val labels = VoiceClusters.cluster(points, numSpeakers = 2, separation = 0.5f)
        assertEquals(points.size, labels.size)
        assertTrue(sameGrouping(labels, truth))
    }

    @Test
    fun moreParticipantsThanWindowsIsHandled() {
        val (points, _) = conversation(2)
        assertEquals(2, VoiceClusters.cluster(points, numSpeakers = 5, separation = 0.5f).size)
        assertEquals(0, VoiceClusters.cluster(emptyList(), numSpeakers = 2, separation = 0.5f).size)
    }

    @Test
    fun theSeparationSliderMovesTheDetectionFloor() {
        assertTrue(VoiceClusters.silhouetteFloor(1f) < VoiceClusters.silhouetteFloor(0f))
    }
}
