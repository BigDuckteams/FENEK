package app.fenek.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class ResamplerTest {

    private fun tone(rate: Int, seconds: Double, frequency: Double, amplitude: Double = 0.5) =
        FloatArray((rate * seconds).toInt()) { (amplitude * sin(2 * PI * frequency * it / rate)).toFloat() }

    /** Feeds [input] in uneven chunks, as a decoder would. */
    private fun resample(input: FloatArray, inRate: Int, outRate: Int): FloatArray {
        val out = ArrayList<Float>()
        val sink: (FloatArray, Int) -> Unit = { buffer, count -> for (i in 0 until count) out += buffer[i] }
        val resampler = Resampler(inRate, outRate)
        val sizes = intArrayOf(1000, 37, 4096, 333, 1)
        var position = 0
        var k = 0
        while (position < input.size) {
            val n = min(sizes[k++ % sizes.size], input.size - position)
            resampler.process(input.copyOfRange(position, position + n), n, sink)
            position += n
        }
        resampler.flush(sink)
        return out.toFloatArray()
    }

    private fun assertMatchesTone(out: FloatArray, rate: Int, frequency: Double, tolerance: Double) {
        for (i in 200 until out.size - 200) {
            val expected = 0.5 * sin(2 * PI * frequency * i / rate)
            assertEquals("sample $i", expected, out[i].toDouble(), tolerance)
        }
    }

    @Test
    fun downsampling48kKeepsSpeechBandTone() {
        val out = resample(tone(48_000, 1.0, 1000.0), 48_000, 16_000)
        assertEquals(16_000, out.size)
        assertMatchesTone(out, 16_000, 1000.0, 0.01)
    }

    @Test
    fun downsampling44kHasExpectedLength() {
        val out = resample(tone(44_100, 2.0, 440.0), 44_100, 16_000)
        assertEquals(32_000, out.size)
        assertMatchesTone(out, 16_000, 440.0, 0.01)
    }

    @Test
    fun toneAboveNewNyquistIsFilteredInsteadOfAliased() {
        val out = resample(tone(48_000, 1.0, 12_000.0), 48_000, 16_000)
        val body = out.copyOfRange(200, out.size - 200)
        val rms = sqrt(body.map { it.toDouble() * it }.average())
        assertTrue("rms=$rms", rms < 0.005)
    }

    @Test
    fun upsampling8kDoublesLength() {
        val out = resample(tone(8_000, 1.0, 500.0), 8_000, 16_000)
        assertEquals(16_000, out.size)
        assertMatchesTone(out, 16_000, 500.0, 0.01)
    }

    @Test
    fun sameRatePassesSamplesThrough() {
        val input = tone(16_000, 0.1, 300.0)
        assertArrayEquals(input, resample(input, 16_000, 16_000), 0f)
    }
}
