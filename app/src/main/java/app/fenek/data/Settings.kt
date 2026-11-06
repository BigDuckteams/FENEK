package app.fenek.data

import android.content.Context
import app.fenek.model.Languages
import app.fenek.model.ModelCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Step of the pyannote sliding window: a smaller step is more precise but slower. */
enum class DiarizationQuality(val windowShiftRatio: Float) {
    FAST(0.5f),
    BALANCED(0.25f),
    PRECISE(0.1f),
}

data class AppSettings(
    val asrModelId: String = ModelCatalog.defaultAsr.id,
    val embeddingModelId: String = ModelCatalog.defaultEmbedding.id,
    /** Recognition language for models that support a choice. */
    val language: String = Languages.AUTO,
    val diarizationQuality: DiarizationQuality = DiarizationQuality.BALANCED,
    /** 0 = merge similar voices, 1 = split them into separate speakers. */
    val speakerSeparation: Float = 0.5f,
    val numThreads: Int = defaultThreads(),
) {
    companion object {
        fun defaultThreads(): Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
        fun maxThreads(): Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
    }
}

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    val current: AppSettings get() = _state.value

    fun update(transform: (AppSettings) -> AppSettings) {
        synchronized(this) {
            val updated = transform(_state.value)
            prefs.edit()
                .putString(KEY_ASR, updated.asrModelId)
                .putString(KEY_EMBEDDING, updated.embeddingModelId)
                .putString(KEY_LANGUAGE, updated.language)
                .putString(KEY_QUALITY, updated.diarizationQuality.name)
                .putFloat(KEY_SEPARATION, updated.speakerSeparation)
                .putInt(KEY_THREADS, updated.numThreads)
                .apply()
            _state.value = updated
        }
    }

    private fun load(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            asrModelId = ModelCatalog.asr(prefs.getString(KEY_ASR, null) ?: defaults.asrModelId).id,
            embeddingModelId = ModelCatalog.embedding(prefs.getString(KEY_EMBEDDING, null) ?: defaults.embeddingModelId).id,
            language = prefs.getString(KEY_LANGUAGE, null) ?: defaults.language,
            diarizationQuality = prefs.getString(KEY_QUALITY, null)
                ?.let { name -> DiarizationQuality.entries.firstOrNull { it.name == name } }
                ?: defaults.diarizationQuality,
            speakerSeparation = prefs.getFloat(KEY_SEPARATION, defaults.speakerSeparation),
            numThreads = prefs.getInt(KEY_THREADS, defaults.numThreads).coerceIn(1, AppSettings.maxThreads()),
        )
    }

    private companion object {
        const val KEY_ASR = "asr_model"
        const val KEY_EMBEDDING = "embedding_model"
        const val KEY_LANGUAGE = "language"
        const val KEY_QUALITY = "diarization_quality"
        const val KEY_SEPARATION = "speaker_separation"
        const val KEY_THREADS = "threads"
    }
}
