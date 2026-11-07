package app.fenek.engine

import app.fenek.audio.PcmFile
import app.fenek.model.AsrArchitecture
import app.fenek.model.AsrSpec
import app.fenek.model.Languages
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.io.File

data class RecognizedText(val text: String, val language: String?)

/** Offline speech recognition of short (≤ 25-30 s) single-speaker pieces of audio. */
class Transcriber(
    private val spec: AsrSpec,
    modelDir: File,
    language: String,
    numThreads: Int,
) : AutoCloseable {

    private val recognizer = OfflineRecognizer(config = buildConfig(spec, modelDir, language, numThreads))

    fun transcribe(samples: FloatArray): RecognizedText {
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(samples, PcmFile.SAMPLE_RATE)
            recognizer.decode(stream)
            val result = recognizer.getResult(stream)
            // Filled from native code, which may leave it unset for some model types.
            @Suppress("USELESS_CAST")
            val language = (result.lang as String?)?.trim()?.removePrefix("<|")?.removeSuffix("|>")?.ifBlank { null }
            val text = TextCleaner.clean(
                raw = result.text,
                punctuated = spec.punctuation,
                dropHallucinations = spec.architecture == AsrArchitecture.WHISPER,
            )
            return RecognizedText(text, language)
        } finally {
            stream.release()
        }
    }

    private var closed = false

    /** Safe to call twice: a paused job closes the recognizer early and the usual cleanup closes it again. */
    override fun close() {
        if (closed) return
        closed = true
        recognizer.release()
    }

    companion object {
        fun buildConfig(spec: AsrSpec, dir: File, language: String, numThreads: Int): OfflineRecognizerConfig {
            fun path(name: String) = File(dir, name).absolutePath
            val tokens = path(spec.tokens)
            val model = when (spec.architecture) {
                AsrArchitecture.WHISPER -> OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = path(spec.encoder),
                        decoder = path(spec.decoder),
                        // An empty language makes multilingual Whisper detect it itself.
                        language = if (language == Languages.AUTO) "" else language,
                        task = "transcribe",
                    ),
                    tokens = tokens,
                    modelType = "whisper",
                    numThreads = numThreads,
                )
                AsrArchitecture.NEMO_CTC -> OfflineModelConfig(
                    nemo = OfflineNemoEncDecCtcModelConfig(model = path(spec.model)),
                    tokens = tokens,
                    numThreads = numThreads,
                )
                AsrArchitecture.NEMO_TRANSDUCER -> OfflineModelConfig(
                    transducer = OfflineTransducerModelConfig(
                        encoder = path(spec.encoder),
                        decoder = path(spec.decoder),
                        joiner = path(spec.joiner),
                    ),
                    tokens = tokens,
                    modelType = "nemo_transducer",
                    numThreads = numThreads,
                )
                AsrArchitecture.TRANSDUCER -> OfflineModelConfig(
                    transducer = OfflineTransducerModelConfig(
                        encoder = path(spec.encoder),
                        decoder = path(spec.decoder),
                        joiner = path(spec.joiner),
                    ),
                    tokens = tokens,
                    modelType = "transducer",
                    numThreads = numThreads,
                )
            }
            return OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = PcmFile.SAMPLE_RATE, featureDim = 80),
                modelConfig = model,
                decodingMethod = "greedy_search",
            )
        }
    }
}

/** Normalises recogniser output and drops the classic Whisper hallucinations on silence. */
object TextCleaner {

    private val whitespace = Regex("\\s+")

    private val hallucinations = listOf(
        "продолжение следует",
        "субтитры создавал",
        "субтитры сделал",
        "субтитры делал",
        "редактор субтитров",
        "корректор а.",
        "спасибо за просмотр",
        "подписывайтесь на канал",
        "thanks for watching",
        "thank you for watching",
        "subtitles by",
    )

    fun clean(raw: String, punctuated: Boolean, dropHallucinations: Boolean = false): String {
        var text = raw.replace(whitespace, " ").trim()
        if (text.isEmpty()) return text
        val lower = text.lowercase()
        if (dropHallucinations && hallucinations.any { lower.startsWith(it) }) return ""
        if (text.all { !it.isLetterOrDigit() }) return ""
        if (!punctuated) text = text.replaceFirstChar { it.titlecase() }
        return text
    }
}
