package app.fenek.audio

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.min

/** Conservative cleanup for noisy recordings, applied only when the user enables it. */
object AudioEnhancer {

    /** Processes the file in bounded chunks; normalization uses one gain for the whole recording. */
    fun enhance(source: PcmFile, output: File) {
        val gain = normalizationGain(peak(source))
        val filter = Filter()
        var bytes = ByteArray(CHUNK_SAMPLES * 2)
        BufferedOutputStream(FileOutputStream(output), CHUNK_SAMPLES * 2).use { out ->
            var position = 0L
            while (position < source.numSamples) {
                val count = min(CHUNK_SAMPLES.toLong(), source.numSamples - position).toInt()
                val samples = filter.process(source.read(position, count))
                if (bytes.size < count * 2) bytes = ByteArray(count * 2)
                for (i in 0 until count) {
                    val value = (samples[i] * gain * 32767f).toInt().coerceIn(-32768, 32767)
                    bytes[i * 2] = value.toByte()
                    bytes[i * 2 + 1] = (value shr 8).toByte()
                }
                out.write(bytes, 0, count * 2)
                position += count
            }
        }
    }

    internal fun normalizationGain(peak: Float): Float = when {
        peak <= 0f -> 1f
        peak <= QUIET_PEAK -> (TARGET_PEAK / peak).coerceAtMost(MAX_GAIN)
        else -> 1f
    }

    private fun peak(source: PcmFile): Float {
        var result = 0f
        var position = 0L
        while (position < source.numSamples) {
            val count = min(CHUNK_SAMPLES.toLong(), source.numSamples - position).toInt()
            for (sample in source.read(position, count)) result = maxOf(result, abs(sample))
            position += count
        }
        return result
    }

    /** DC/rumble removal followed by a smooth downward expander for noise in pauses. */
    internal class Filter {
        private var previousDcInput = 0f
        private var previousDcOutput = 0f
        private var previousHighInput = 0f
        private var previousHighOutput = 0f
        private var envelope = 0f
        private var noiseFloor = INITIAL_NOISE_FLOOR
        private var gateGain = 1f

        fun process(input: FloatArray): FloatArray {
            val output = FloatArray(input.size)
            for (i in input.indices) {
                val dc = input[i] - previousDcInput + DC_POLE * previousDcOutput
                previousDcInput = input[i]
                previousDcOutput = dc
                val high = dc - previousHighInput + HIGH_PASS_POLE * previousHighOutput
                previousHighInput = dc
                previousHighOutput = high

                val level = abs(high)
                envelope = maxOf(level, envelope * ENVELOPE_DECAY)
                noiseFloor = if (level < noiseFloor) {
                    level
                } else {
                    noiseFloor + (level - noiseFloor) * NOISE_RISE
                }
                val threshold = (noiseFloor * NOISE_THRESHOLD_RATIO).coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
                val target = if (envelope < threshold) PAUSE_GAIN else 1f
                val smoothing = if (target < gateGain) GATE_RELEASE else GATE_ATTACK
                gateGain += (target - gateGain) * smoothing
                output[i] = high * gateGain
            }
            return output
        }
    }

    private const val CHUNK_SAMPLES = 1 shl 18
    private const val QUIET_PEAK = 0.35f
    private const val TARGET_PEAK = 0.7f
    private const val MAX_GAIN = 3f
    private const val DC_POLE = 0.995f
    private const val HIGH_PASS_POLE = 0.98f
    private const val ENVELOPE_DECAY = 0.9995f
    private const val INITIAL_NOISE_FLOOR = 0.005f
    private const val NOISE_RISE = 0.00002f
    private const val NOISE_THRESHOLD_RATIO = 2.5f
    private const val MIN_THRESHOLD = 0.001f
    private const val MAX_THRESHOLD = 0.04f
    private const val PAUSE_GAIN = 0.2f
    private const val GATE_ATTACK = 0.1f
    private const val GATE_RELEASE = 0.005f
}
