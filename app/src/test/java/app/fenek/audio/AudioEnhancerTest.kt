package app.fenek.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class AudioEnhancerTest {

    @Test
    fun quietRecordingIsNormalizedWithALimitedGain() {
        assertEquals(3f, AudioEnhancer.normalizationGain(0.1f), 0f)
        assertEquals(2f, AudioEnhancer.normalizationGain(0.35f), 0.0001f)
        assertEquals(1f, AudioEnhancer.normalizationGain(0.5f), 0f)
        assertEquals(1f, AudioEnhancer.normalizationGain(0f), 0f)
    }

    @Test
    fun dcOffsetIsRemovedWithoutClipping() {
        val input = FloatArray(16_000) { 0.4f + 0.2f * sin(2 * PI * 200 * it / 16_000).toFloat() }
        val output = AudioEnhancer.Filter().process(input)
        val body = output.copyOfRange(2_000, output.size)

        assertTrue(body.all { it.isFinite() && abs(it) <= 0.25f })
        assertTrue(abs(body.average()) < 0.002)
    }

    @Test
    fun lowLevelNoiseInPausesIsAttenuated() {
        val input = FloatArray(16_000) { if (it % 2 == 0) 0.004f else -0.004f }
        val output = AudioEnhancer.Filter().process(input)
        val from = 8_000
        val inputRms = rms(input, from)
        val outputRms = rms(output, from)

        assertTrue("input=$inputRms output=$outputRms", outputRms < inputRms * 0.5f)
    }

    @Test
    fun speechBandToneKeepsItsLevel() {
        val input = FloatArray(16_000) { (0.2f * sin(2 * PI * 300 * it / 16_000)).toFloat() }
        val output = AudioEnhancer.Filter().process(input)
        val ratio = rms(output, 8_000) / rms(input, 8_000)

        assertTrue("ratio=$ratio", ratio > 0.85f)
    }

    @Test
    fun arbitraryChunksMatchOnePass() {
        val input = FloatArray(10_000) { (sin(it * 0.03) * 0.1).toFloat() }
        val whole = AudioEnhancer.Filter().process(input)
        val chunked = ArrayList<Float>()
        val filter = AudioEnhancer.Filter()
        var position = 0
        val sizes = intArrayOf(1, 17, 512, 3, 1_000)
        var step = 0
        while (position < input.size) {
            val end = minOf(input.size, position + sizes[step++ % sizes.size])
            chunked += filter.process(input.copyOfRange(position, end)).toList()
            position = end
        }

        assertArrayEquals(whole, chunked.toFloatArray(), 0f)
    }

    private fun rms(samples: FloatArray, from: Int): Double {
        var sum = 0.0
        for (i in from until samples.size) sum += samples[i] * samples[i]
        return sqrt(sum / (samples.size - from))
    }
}
