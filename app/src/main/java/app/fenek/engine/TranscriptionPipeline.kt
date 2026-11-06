package app.fenek.engine

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import app.fenek.audio.AudioDecodeException
import app.fenek.audio.AudioDecoder
import app.fenek.audio.PcmFile
import app.fenek.data.AppSettings
import app.fenek.data.Segment
import app.fenek.data.Speaker
import app.fenek.data.Transcript
import app.fenek.data.defaultSpeakerName
import app.fenek.model.Languages
import app.fenek.model.ModelCatalog
import app.fenek.model.ModelInfo
import app.fenek.model.ModelManager
import app.fenek.record.RecordingMarks
import app.fenek.record.RecordingNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.roundToLong

data class JobRequest(
    val source: Uri,
    val displayName: String,
    /** The source is only readable temporarily (shared from another app), so it is copied first. */
    val copySource: Boolean,
    /** The source is a recording made by the app: the transcript takes it over. */
    val ownsSource: Boolean = false,
    /** Exact number of participants, or null to detect it. */
    val numSpeakers: Int?,
    val asrModelId: String,
    val language: String,
    val id: String = UUID.randomUUID().toString(),
    /** Keeps the title of a transcript that is being redone. */
    val title: String? = null,
)

enum class Stage { PREPARING, DECODING, DIARIZING, TRANSCRIBING, SAVING }

data class JobProgress(
    val stage: Stage,
    val stageFraction: Float,
    val overall: Float,
    val chunksDone: Int = 0,
    val chunksTotal: Int = 0,
)

class MissingModelsException(val models: List<ModelInfo>) :
    Exception("Сначала скачайте модели: " + models.joinToString { it.title })

/**
 * Recording → structured dialogue:
 * decode to 16 kHz mono → diarization (who spoke when) → clean speaker turns →
 * speech recognition of every turn → transcript with speakers and timestamps.
 */
class TranscriptionPipeline(
    private val context: Context,
    private val models: ModelManager,
    private val settings: AppSettings,
) {

    suspend fun run(request: JobRequest, onProgress: (JobProgress) -> Unit): Transcript {
        val startedAt = System.currentTimeMillis()
        val asrModel = ModelCatalog.asr(request.asrModelId)
        val spec = requireNotNull(asrModel.asr)
        val embeddingModel = ModelCatalog.embedding(settings.embeddingModelId)
        val missing = listOf(asrModel, ModelCatalog.segmentation, embeddingModel).filterNot { models.isReady(it) }
        if (missing.isNotEmpty()) throw MissingModelsException(missing)

        fun report(stage: Stage, fraction: Float, done: Int = 0, total: Int = 0) {
            val (from, to) = STAGE_RANGES.getValue(stage)
            val f = fraction.coerceIn(0f, 1f)
            onProgress(JobProgress(stage, f, from + (to - from) * f, done, total))
        }

        val workDir = File(context.cacheDir, "work").apply { mkdirs() }
        val pcmFile = File(workDir, "${request.id}.pcm")
        var copiedAudio: File? = null
        try {
            report(Stage.PREPARING, 0f)
            val decodeUri: Uri
            val audioRef: String
            if (request.copySource) {
                val copy = copyToStorage(request)
                copiedAudio = copy
                decodeUri = Uri.fromFile(copy)
                audioRef = copy.absolutePath
            } else {
                decodeUri = request.source
                // App recordings are referenced by path: playback, deletion and the list of
                // untranscribed recordings all work with plain files.
                audioRef = request.source.takeIf { it.scheme == "file" }?.path ?: request.source.toString()
            }

            val pcm = timed("decoding") {
                AudioDecoder.decode(context, decodeUri, pcmFile) { report(Stage.DECODING, it) }
            }
            if (pcm.durationSec < MIN_RECORDING_SECONDS) throw AudioDecodeException("Запись пустая или слишком короткая")

            report(Stage.DIARIZING, 0f)
            val diarized = timed("diarization") {
                Diarizer(
                    segmentationModel = models.path(ModelCatalog.segmentation, ModelCatalog.segmentation.files.first().name),
                    embeddingModel = models.path(embeddingModel, embeddingModel.files.first().name),
                    numThreads = settings.numThreads,
                    windowShiftRatio = settings.diarizationQuality.windowShiftRatio,
                ).use { diarizer ->
                    diarizer.run(pcm, request.numSpeakers, settings.speakerSeparation) { report(Stage.DIARIZING, it) }
                }
            }

            val turns = TurnBuilder.build(diarized)
            val chunks = TurnBuilder.splitLongTurns(turns, MAX_CHUNK_SECONDS) { from, to ->
                pcm.quietestPoint(
                    (from * PcmFile.SAMPLE_RATE).toLong(),
                    (to * PcmFile.SAMPLE_RATE).toLong(),
                ).toDouble() / PcmFile.SAMPLE_RATE
            }.filter { it.duration >= MIN_CHUNK_SECONDS }

            val segments = ArrayList<Segment>()
            var language = request.language.takeIf { spec.selectableLanguage && it != Languages.AUTO }
            val totalSpeech = chunks.sumOf { it.duration }.coerceAtLeast(1e-3)
            var doneSpeech = 0.0
            report(Stage.TRANSCRIBING, 0f, 0, chunks.size)
            val recognitionStart = SystemClock.elapsedRealtime()
            var transcriber = timed("loading ${asrModel.id}") {
                Transcriber(spec, models.dir(asrModel), request.language, settings.numThreads)
            }
            try {
                for ((index, chunk) in chunks.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    val from = (chunk.start - PADDING_SECONDS).coerceAtLeast(0.0)
                    val to = (chunk.end + PADDING_SECONDS).coerceAtMost(pcm.durationSec)
                    val result = transcriber.transcribe(pcm.readSeconds(from, to))
                    if (result.text.isNotEmpty()) {
                        segments += Segment(chunk.speaker, ms(chunk.start), ms(chunk.end), result.text)
                    }
                    // Whisper in "auto" mode: keep the language detected on the first long phrase,
                    // otherwise short replies are sometimes recognised as another language.
                    if (language == null && spec.selectableLanguage && chunk.duration >= LANGUAGE_PROBE_SECONDS &&
                        result.language != null
                    ) {
                        language = result.language
                        transcriber.close()
                        transcriber = Transcriber(spec, models.dir(asrModel), result.language, settings.numThreads)
                    }
                    doneSpeech += chunk.duration
                    report(Stage.TRANSCRIBING, (doneSpeech / totalSpeech).toFloat(), index + 1, chunks.size)
                }
            } finally {
                transcriber.close()
            }
            Log.i(
                TAG,
                "recognition: ${SystemClock.elapsedRealtime() - recognitionStart} ms for ${chunks.size} phrases, " +
                    "%.1f s of speech".format(totalSpeech),
            )

            report(Stage.SAVING, 0f)
            // Number the speakers by their first recognised phrase.
            val order = LinkedHashMap<Int, Int>()
            val numbered = segments.map { it.copy(speaker = order.getOrPut(it.speaker) { order.size }) }
            val durationMs = (pcm.durationSec * 1000).roundToLong()
            // Recordings made in the app know when they started and what was marked during them.
            val recording = request.source.takeIf { it.scheme == "file" }?.path?.let(::File)
            return Transcript(
                id = request.id,
                title = request.title ?: request.displayName.substringBeforeLast('.').ifBlank { request.displayName },
                createdAt = System.currentTimeMillis(),
                durationMs = durationMs,
                sourceName = request.displayName,
                audio = audioRef,
                audioOwned = request.copySource || request.ownsSource,
                asrModelId = asrModel.id,
                asrModelTitle = asrModel.title,
                language = if (spec.selectableLanguage) language else null,
                processingMs = System.currentTimeMillis() - startedAt,
                speakers = order.values.map { Speaker(it, defaultSpeakerName(it)) },
                segments = numbered,
                recordedAt = recording?.let(RecordingNames::createdAt),
                marks = recording?.let(RecordingMarks::read).orEmpty().filter { it <= durationMs },
            )
        } catch (e: Throwable) {
            copiedAudio?.delete()
            throw e
        } finally {
            pcmFile.delete()
        }
    }

    private suspend fun copyToStorage(request: JobRequest): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "audio").apply { mkdirs() }
        val extension = request.displayName.substringAfterLast('.', "")
            .lowercase()
            .takeIf { it.length in 1..5 && it.all(Char::isLetterOrDigit) }
            ?: "audio"
        val target = File(dir, "${request.id}.$extension")
        val input = context.contentResolver.openInputStream(request.source)
            ?: throw AudioDecodeException("Не удалось прочитать файл")
        input.use { source -> target.outputStream().use { source.copyTo(it, 1 shl 16) } }
        target
    }

    private fun ms(seconds: Double): Long = (seconds * 1000).roundToLong()

    private inline fun <T> timed(stage: String, block: () -> T): T {
        val start = SystemClock.elapsedRealtime()
        return block().also { Log.i(TAG, "$stage: ${SystemClock.elapsedRealtime() - start} ms") }
    }

    private companion object {
        const val TAG = "Pipeline"

        /** Recognition models work best on phrases of up to 25 s (GigaAM's limit, Whisper's is 30 s). */
        const val MAX_CHUNK_SECONDS = 25.0
        const val MIN_CHUNK_SECONDS = 0.25
        const val PADDING_SECONDS = 0.1
        const val MIN_RECORDING_SECONDS = 0.3
        const val LANGUAGE_PROBE_SECONDS = 3.0

        val STAGE_RANGES = mapOf(
            Stage.PREPARING to (0f to 0.02f),
            Stage.DECODING to (0.02f to 0.12f),
            Stage.DIARIZING to (0.12f to 0.55f),
            Stage.TRANSCRIBING to (0.55f to 0.99f),
            Stage.SAVING to (0.99f to 1f),
        )
    }
}
