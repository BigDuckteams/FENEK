package app.fenek.model

import app.fenek.data.AppSettings

/**
 * What is still to be downloaded for the full pipeline with the current settings: the models a job
 * cannot run without, and the language model, which is only recommended (a job runs without it).
 */
class DownloadPlan(
    /** Segmentation, voice-fingerprint and recognition models that are not ready. */
    val requiredMissing: List<ModelInfo>,
    /** The language model, when a language model mode is on and the model is not ready. */
    val llmMissing: ModelInfo?,
) {
    /** Everything that "download all" starts. */
    val all: List<ModelInfo> get() = requiredMissing + listOfNotNull(llmMissing)

    val isEmpty: Boolean get() = all.isEmpty()

    val totalBytes: Long get() = all.sumOf { it.totalBytes }
    val requiredBytes: Long get() = requiredMissing.sumOf { it.totalBytes }

    companion object {
        /** The models the pipeline needs for [settings]; [asrModelId] overrides the recognition model. */
        fun requiredModels(settings: AppSettings, asrModelId: String = settings.asrModelId): List<ModelInfo> = listOf(
            ModelCatalog.segmentation,
            ModelCatalog.embedding(settings.embeddingModelId),
            ModelCatalog.asr(asrModelId),
        )

        fun of(settings: AppSettings, asrModelId: String = settings.asrModelId, isReady: (ModelInfo) -> Boolean): DownloadPlan {
            val llm = ModelCatalog.llm(settings.llmModelId)
            return DownloadPlan(
                requiredMissing = requiredModels(settings, asrModelId).filterNot(isReady),
                llmMissing = llm.takeIf { settings.llmEnabled && !isReady(it) },
            )
        }
    }
}
