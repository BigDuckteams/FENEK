package app.fenek.engine

import app.fenek.audio.PcmFile
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig

/** Computes a voice "fingerprint" (speaker embedding) for a piece of audio. */
class SpeakerEmbedder(modelPath: String, numThreads: Int) : AutoCloseable {

    private val extractor = SpeakerEmbeddingExtractor(
        config = SpeakerEmbeddingExtractorConfig(model = modelPath, numThreads = numThreads),
    )

    /** L2-normalised embedding of 16 kHz [samples], or null if the audio is too short. */
    fun compute(samples: FloatArray): FloatArray? {
        val stream = extractor.createStream()
        try {
            stream.acceptWaveform(samples, PcmFile.SAMPLE_RATE)
            stream.inputFinished()
            if (!extractor.isReady(stream)) return null
            return Vectors.normalize(extractor.compute(stream))
        } finally {
            stream.release()
        }
    }

    override fun close() = extractor.release()
}
