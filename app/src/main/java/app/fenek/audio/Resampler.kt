package app.fenek.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Streaming band-limited resampler for mono float audio.
 *
 * Every output sample is a windowed-sinc interpolation of the input. The fractional
 * position is quantised to [PHASES] steps, so all kernels are precomputed and each output
 * sample costs one multiply-add per tap. When downsampling, the kernel also acts as the
 * anti-aliasing low-pass filter.
 */
class Resampler(private val inRate: Int, private val outRate: Int) {

    private val passthrough = inRate == outRate
    private val halfTaps: Int
    private val taps: Int
    private val kernels: FloatArray

    // Input history. history[0] holds absolute input sample [historyStart]. The stream is
    // treated as preceded by halfTaps zeros, so historyStart starts negative.
    private var history = FloatArray(16_384)
    private var historyLen = 0
    private var historyStart = 0L
    private var inputCount = 0L
    private var nextOut = 0L
    private val outBuffer = FloatArray(4096)

    init {
        require(inRate > 0 && outRate > 0) { "Invalid sample rates: $inRate -> $outRate" }
        // Cut-off relative to the input Nyquist frequency.
        val cutoff = 0.95 * min(1.0, outRate.toDouble() / inRate)
        halfTaps = if (passthrough) 0 else ceil(ZERO_CROSSINGS / cutoff).toInt()
        taps = 2 * halfTaps
        kernels = FloatArray((PHASES + 1) * taps)
        for (phase in 0..PHASES) {
            val frac = phase.toDouble() / PHASES
            var sum = 0.0
            val row = phase * taps
            for (j in 0 until taps) {
                // Distance (in input samples) between the output position and tap j.
                val d = frac + (halfTaps - 1 - j)
                val value = if (abs(d) >= halfTaps) 0.0 else cutoff * sinc(cutoff * d) * blackman(d / halfTaps)
                kernels[row + j] = value.toFloat()
                sum += value
            }
            // Unity gain at DC for every phase.
            for (j in 0 until taps) kernels[row + j] = (kernels[row + j] / sum).toFloat()
        }
        if (!passthrough) {
            historyLen = halfTaps
            historyStart = -halfTaps.toLong()
        }
    }

    /** Feeds [length] samples of [input]; every output sample that can be computed is passed to [sink]. */
    fun process(input: FloatArray, length: Int, sink: (FloatArray, Int) -> Unit) {
        if (length <= 0) return
        if (passthrough) {
            sink(input, length)
            return
        }
        append(input, 0, length)
        inputCount += length
        emit(sink, flushing = false)
    }

    /** Emits the remaining output at the end of the stream. */
    fun flush(sink: (FloatArray, Int) -> Unit) {
        if (passthrough) return
        val zeros = FloatArray(halfTaps + 1)
        append(zeros, 0, zeros.size)
        emit(sink, flushing = true)
    }

    private fun emit(sink: (FloatArray, Int) -> Unit, flushing: Boolean) {
        val lastAvailable = historyStart + historyLen - 1
        // Number of output samples covering the whole input.
        val totalOut = ceilDiv(inputCount * outRate, inRate.toLong())
        var n = 0
        while (true) {
            if (flushing && nextOut >= totalOut) break
            val num = nextOut * inRate
            val ip = num / outRate
            val phase = (((num % outRate) * PHASES + outRate / 2) / outRate).toInt()
            if (ip + halfTaps > lastAvailable) break
            val first = (ip - halfTaps + 1 - historyStart).toInt()
            val row = phase * taps
            var acc = 0f
            for (j in 0 until taps) acc += kernels[row + j] * history[first + j]
            outBuffer[n++] = acc
            if (n == outBuffer.size) {
                sink(outBuffer, n)
                n = 0
            }
            nextOut++
        }
        if (n > 0) sink(outBuffer, n)
        // Drop input that no future output sample needs.
        val needed = (nextOut * inRate) / outRate - halfTaps + 1
        val drop = (needed - historyStart).toInt().coerceIn(0, historyLen)
        if (drop > history.size / 2) {
            System.arraycopy(history, drop, history, 0, historyLen - drop)
            historyLen -= drop
            historyStart += drop
        }
    }

    private fun append(src: FloatArray, offset: Int, length: Int) {
        if (historyLen + length > history.size) {
            var size = history.size
            while (size < historyLen + length) size *= 2
            history = history.copyOf(size)
        }
        System.arraycopy(src, offset, history, historyLen, length)
        historyLen += length
    }

    private companion object {
        const val PHASES = 256
        const val ZERO_CROSSINGS = 10

        fun sinc(x: Double): Double = if (abs(x) < 1e-9) 1.0 else sin(PI * x) / (PI * x)

        fun blackman(u: Double): Double = 0.42 + 0.5 * cos(PI * u) + 0.08 * cos(2 * PI * u)

        fun ceilDiv(a: Long, b: Long): Long = (a + b - 1) / b
    }
}
