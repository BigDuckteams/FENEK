package app.fenek.engine

import android.content.Context
import android.net.Uri
import app.fenek.audio.AudioDecodeException
import app.fenek.audio.AudioDecoder
import app.fenek.audio.AudioEnhancer
import app.fenek.audio.PcmFile
import app.fenek.data.SettingsRepository
import app.fenek.data.Transcript
import app.fenek.model.ModelCatalog
import app.fenek.model.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Recognizes one existing phrase again without changing its speaker or timestamps. */
class FragmentRecognizer(
    private val context: Context,
    private val models: ModelManager,
    private val settings: SettingsRepository,
) {

    suspend fun recognize(transcript: Transcript, segmentIndex: Int): String = withContext(Dispatchers.IO) {
        val segment = transcript.segments.getOrNull(segmentIndex)
            ?: throw AudioDecodeException("Фраза не найдена")
        val audio = transcript.audio ?: throw AudioDecodeException("Исходная запись недоступна")
        if (audio.startsWith("/") && !File(audio).isFile) throw AudioDecodeException("Исходная запись удалена")

        val asrModel = ModelCatalog.asr(transcript.asrModelId)
        val spec = requireNotNull(asrModel.asr)
        if (!models.isReady(asrModel)) throw MissingModelsException(listOf(asrModel))

        val workDir = File(context.cacheDir, "work").apply { mkdirs() }
        val id = UUID.randomUUID().toString()
        val pcmFile = File(workDir, "fragment-$id.pcm")
        val enhancedFile = File(workDir, "fragment-$id.enhanced.pcm")
        try {
            val uri = if (audio.startsWith("/")) Uri.fromFile(File(audio)) else Uri.parse(audio)
            var pcm = AudioDecoder.decode(context, uri, pcmFile) {}
            val current = settings.current
            if (current.enhanceAudio) {
                AudioEnhancer.enhance(pcm, enhancedFile)
                pcm = PcmFile(enhancedFile)
            }
            val from = (segment.startMs / 1000.0 - PADDING_SECONDS).coerceAtLeast(0.0)
            val to = (segment.endMs / 1000.0 + PADDING_SECONDS).coerceAtMost(pcm.durationSec)
            val language = transcript.language ?: current.language
            val result = Transcriber(spec, models.dir(asrModel), language, current.numThreads).use {
                it.transcribe(pcm.readSeconds(from, to))
            }
            result.text.ifBlank { throw AudioDecodeException("В этом фрагменте не найдено речи") }
        } finally {
            pcmFile.delete()
            enhancedFile.delete()
        }
    }

    private companion object {
        const val PADDING_SECONDS = 0.25
    }
}
