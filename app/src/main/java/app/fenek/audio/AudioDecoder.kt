package app.fenek.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioDecodeException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Decodes any audio (or the audio track of a video) supported by the platform codecs —
 * MP3, AAC/M4A, OGG/Opus, FLAC, WAV, AMR, 3GP… — into 16 kHz mono 16-bit PCM on disk.
 */
object AudioDecoder {

    private const val TAG = "AudioDecoder"
    private const val TIMEOUT_US = 10_000L

    suspend fun decode(
        context: Context,
        uri: Uri,
        output: File,
        onProgress: (Float) -> Unit,
    ): PcmFile = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            try {
                extractor.setDataSource(context, uri, null)
            } catch (e: Exception) {
                throw AudioDecodeException("Не удалось открыть файл", e)
            }
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw AudioDecodeException("В файле нет звуковой дорожки")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L

            val decoder = try {
                MediaCodec.createDecoderByType(mime).also { it.configure(format, null, null, 0) }
            } catch (e: Exception) {
                throw AudioDecodeException("Формат $mime не поддерживается устройством", e)
            }
            codec = decoder
            decoder.start()

            val sink = PcmSink(output)
            sink.use {
                var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                var encoding = AudioFormat.ENCODING_PCM_16BIT
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                var lastReported = -1f
                var idleRounds = 0
                var rounds = 0
                var waitNs = 0L
                var readNs = 0L
                var sinkNs = 0L
                val startNs = System.nanoTime()

                while (!outputDone) {
                    coroutineContext.ensureActive()
                    rounds++
                    var progressed = false
                    // Hand the codec every compressed frame it can take right now…
                    while (!inputDone) {
                        val inIndex = decoder.dequeueInputBuffer(0)
                        if (inIndex < 0) break
                        val buffer = decoder.getInputBuffer(inIndex)!!
                        val readStart = System.nanoTime()
                        val size = extractor.readSampleData(buffer, 0)
                        readNs += System.nanoTime() - readStart
                        if (size < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime.coerceAtLeast(0), 0)
                            extractor.advance()
                        }
                        progressed = true
                    }
                    // …then take all decoded PCM it has ready; wait briefly only when stalled.
                    while (!outputDone) {
                        val waitStart = System.nanoTime()
                        val outIndex = decoder.dequeueOutputBuffer(info, if (progressed) 0 else TIMEOUT_US)
                        waitNs += System.nanoTime() - waitStart
                        if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) break
                        progressed = true
                        if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            val outFormat = decoder.outputFormat
                            sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            encoding = if (outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            } else {
                                AudioFormat.ENCODING_PCM_16BIT
                            }
                            continue
                        }
                        if (outIndex < 0) continue
                        if (info.size > 0) {
                            val buffer = decoder.getOutputBuffer(outIndex)!!
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val sinkStart = System.nanoTime()
                            sink.write(buffer.slice().order(ByteOrder.nativeOrder()), encoding, channels, sampleRate)
                            sinkNs += System.nanoTime() - sinkStart
                            if (durationUs > 0) {
                                val fraction = (info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f)
                                if (fraction - lastReported >= 0.01f) {
                                    lastReported = fraction
                                    onProgress(fraction)
                                }
                            }
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                    // Some codecs never signal the end of the stream: give up after ~5 s of silence.
                    idleRounds = if (progressed) 0 else idleRounds + 1
                    if (inputDone && idleRounds > 500) break
                }
                sink.finish()
                Log.i(
                    TAG,
                    "${decoder.name} $mime: ${(System.nanoTime() - startNs) / 1_000_000} ms, rounds=$rounds, " +
                        "codec wait=${waitNs / 1_000_000} ms, read=${readNs / 1_000_000} ms, pcm=${sinkNs / 1_000_000} ms",
                )
            }
            onProgress(1f)
            PcmFile(output)
        } catch (e: Exception) {
            output.delete()
            throw e
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    /** Downmixes, resamples to 16 kHz and writes 16-bit PCM. */
    private class PcmSink(file: File) : AutoCloseable {
        private val out = BufferedOutputStream(FileOutputStream(file), 1 shl 16)
        private var resampler: Resampler? = null
        private var resamplerRate = 0
        private var mono = FloatArray(8192)
        private var bytes = ByteArray(8192)

        private val writeSamples: (FloatArray, Int) -> Unit = { data, count ->
            if (bytes.size < count * 2) bytes = ByteArray(count * 2)
            for (i in 0 until count) {
                val v = (data[i] * 32767f).toInt().coerceIn(-32768, 32767)
                bytes[2 * i] = v.toByte()
                bytes[2 * i + 1] = (v shr 8).toByte()
            }
            out.write(bytes, 0, count * 2)
        }

        fun write(buffer: ByteBuffer, encoding: Int, channels: Int, sampleRate: Int) {
            val ch = channels.coerceAtLeast(1)
            val frames = toMono(buffer, encoding, ch)
            if (frames == 0) return
            if (resampler == null || resamplerRate != sampleRate) {
                resampler?.flush(writeSamples)
                resampler = Resampler(sampleRate, PcmFile.SAMPLE_RATE)
                resamplerRate = sampleRate
            }
            resampler!!.process(mono, frames, writeSamples)
        }

        fun finish() {
            resampler?.flush(writeSamples)
            out.flush()
        }

        override fun close() = out.close()

        private fun toMono(buffer: ByteBuffer, encoding: Int, ch: Int): Int {
            val bytesPerSample = when (encoding) {
                AudioFormat.ENCODING_PCM_8BIT -> 1
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
                AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT -> 4
                else -> 2
            }
            val frames = buffer.remaining() / (bytesPerSample * ch)
            if (mono.size < frames) mono = FloatArray(frames)
            val scale = 1f / ch
            when (encoding) {
                AudioFormat.ENCODING_PCM_FLOAT -> {
                    val f = buffer.asFloatBuffer()
                    for (i in 0 until frames) {
                        var sum = 0f
                        for (c in 0 until ch) sum += f.get(i * ch + c)
                        mono[i] = sum * scale
                    }
                }
                AudioFormat.ENCODING_PCM_32BIT -> {
                    val s = buffer.asIntBuffer()
                    for (i in 0 until frames) {
                        var sum = 0f
                        for (c in 0 until ch) sum += s.get(i * ch + c) / 2147483648f
                        mono[i] = sum * scale
                    }
                }
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                    val base = buffer.position()
                    for (i in 0 until frames) {
                        var sum = 0f
                        for (c in 0 until ch) {
                            val p = base + (i * ch + c) * 3
                            val v = (buffer.get(p).toInt() and 0xFF) or
                                ((buffer.get(p + 1).toInt() and 0xFF) shl 8) or
                                (buffer.get(p + 2).toInt() shl 16)
                            sum += v / 8388608f
                        }
                        mono[i] = sum * scale
                    }
                }
                AudioFormat.ENCODING_PCM_8BIT -> {
                    val base = buffer.position()
                    for (i in 0 until frames) {
                        var sum = 0f
                        for (c in 0 until ch) sum += ((buffer.get(base + i * ch + c).toInt() and 0xFF) - 128) / 128f
                        mono[i] = sum * scale
                    }
                }
                else -> {
                    val s = buffer.asShortBuffer()
                    for (i in 0 until frames) {
                        var sum = 0f
                        for (c in 0 until ch) sum += s.get(i * ch + c) / 32768f
                        mono[i] = sum * scale
                    }
                }
            }
            return frames
        }
    }
}
