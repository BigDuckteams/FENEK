package app.fenek.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Mono 16-bit little-endian PCM stored on disk. Long recordings are never held in memory
 * as a whole: callers read the ranges they need.
 */
class PcmFile(val file: File, val sampleRate: Int = SAMPLE_RATE) {

    val numSamples: Long get() = file.length() / 2

    val durationSec: Double get() = numSamples.toDouble() / sampleRate

    fun read(start: Long, count: Int): FloatArray {
        val from = start.coerceIn(0, numSamples)
        val n = minOf(count.toLong(), numSamples - from).coerceAtLeast(0).toInt()
        if (n == 0) return FloatArray(0)
        val bytes = ByteArray(n * 2)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(from * 2)
            raf.readFully(bytes)
        }
        val shorts = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return FloatArray(n) { shorts.get(it) / 32768f }
    }

    fun readSeconds(startSec: Double, endSec: Double): FloatArray {
        val from = (startSec * sampleRate).toLong()
        val to = (endSec * sampleRate).toLong()
        return read(from, (to - from).coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
    }

    /** Sample index of the quietest short window between [from] and [to] (sample indices). */
    fun quietestPoint(from: Long, to: Long): Long {
        val start = from.coerceIn(0, numSamples)
        val end = to.coerceIn(start, numSamples)
        val samples = read(start, (end - start).toInt())
        return start + AudioMath.quietestIndex(samples, QUIET_WINDOW)
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val QUIET_WINDOW = SAMPLE_RATE / 5 // 200 ms
    }
}

object AudioMath {
    /** Centre of the [window]-long stretch of [samples] with the lowest energy. */
    fun quietestIndex(samples: FloatArray, window: Int): Int {
        if (samples.size <= window) return samples.size / 2
        var energy = 0.0
        for (i in 0 until window) energy += samples[i] * samples[i]
        var best = energy
        var bestStart = 0
        for (i in window until samples.size) {
            energy += samples[i] * samples[i] - samples[i - window] * samples[i - window]
            if (energy < best) {
                best = energy
                bestStart = i - window + 1
            }
        }
        return bestStart + window / 2
    }
}
