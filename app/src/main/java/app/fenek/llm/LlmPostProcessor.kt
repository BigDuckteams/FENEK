package app.fenek.llm

import app.fenek.data.Transcript
import kotlinx.coroutines.CancellationException

enum class LlmStage { CORRECTING, SUMMARIZING }

/** What the language model is asked to do in one job. */
class LlmPlan(
    val modelId: String,
    val modelPath: String,
    val modelBytes: Long,
    val correctText: Boolean,
    val writeHighlights: Boolean,
)

/** The model that cannot be used on this build: every load fails and the job goes on without it. */
object UnavailableLlmLoader : LlmLoader {
    override fun load(modelPath: String, threads: Int): LlmEngine =
        throw LlmException("Языковая модель недоступна в этой сборке")
}

/**
 * Runs the language model stages after recognition. It can only improve a transcript: when the
 * memory is short, the model does not load, a request fails or the model throws, the transcript
 * that came in is returned with whatever was finished so far. Cancellation is passed on.
 */
class LlmPostProcessor(
    private val loader: LlmLoader,
    private val marker: LlmCrashMarker,
    private val availableMemory: () -> Long,
    private val log: (message: String, error: Throwable?) -> Unit = { _, _ -> },
) {

    suspend fun process(
        transcript: Transcript,
        plan: LlmPlan,
        threads: Int,
        onProgress: (stage: LlmStage, fraction: Float, done: Int, total: Int) -> Unit = { _, _, _, _ -> },
    ): Transcript {
        if (!plan.correctText && !plan.writeHighlights) return transcript
        if (availableMemory() < plan.modelBytes + MEMORY_MARGIN) {
            log("Not enough free memory for the language model, skipping it", null)
            return transcript
        }
        marker.enter()
        try {
            val engine = try {
                loader.load(plan.modelPath, threads)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                log("The language model did not load", e)
                return transcript
            }
            engine.use {
                var current = transcript
                try {
                    if (plan.correctText) {
                        val result = TextCorrector(engine).correct(current.segments) { done, total ->
                            onProgress(LlmStage.CORRECTING, if (total == 0) 1f else done.toFloat() / total, done, total)
                        }
                        current = current.copy(
                            segments = result.segments,
                            textCorrected = result.applied,
                            llmModelId = if (result.applied) plan.modelId else current.llmModelId,
                        )
                    }
                    if (plan.writeHighlights) {
                        onProgress(LlmStage.SUMMARIZING, 0f, 0, 0)
                        val written = HighlightsWriter(engine).write(current.segments, current::speakerName)
                        if (written != null) {
                            current = current.copy(
                                llmModelId = plan.modelId,
                                llmSummary = written.summary.ifEmpty { null },
                                llmTasks = written.tasks.ifEmpty { null },
                            )
                        }
                        onProgress(LlmStage.SUMMARIZING, 1f, 0, 0)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    log("The language model stopped, keeping what is done", e)
                }
                return current
            }
        } finally {
            marker.leave()
        }
    }

    companion object {
        /** Weights are mapped from the file, but the context and the buffers need room too. */
        const val MEMORY_MARGIN = 400L * 1024 * 1024
    }
}
