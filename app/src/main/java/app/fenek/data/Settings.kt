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
    /** The language model for text correction and for the summary. */
    val llmModelId: String = ModelCatalog.defaultLlm.id,
    /**
     * "Исправлять текст": the language model fixes punctuation, casing and obvious recognition errors.
     * Off by default: on the models that already punctuate (GigaAM) it did not change the word error
     * rate and only made processing slower, see docs/TEST_RESULTS.md.
     */
    val correctText: Boolean = false,
    /** "Выжимка и задачи": the language model writes the summary and the task list. */
    val llmHighlights: Boolean = true,
    /** Recognition language for models that support a choice. */
    val language: String = Languages.AUTO,
    /** Optional rumble/noise cleanup; off by default to keep clean-record recognition unchanged. */
    val enhanceAudio: Boolean = false,
    val diarizationQuality: DiarizationQuality = DiarizationQuality.BALANCED,
    /** 0 = merge similar voices, 1 = split them into separate speakers. */
    val speakerSeparation: Float = 0.5f,
    val numThreads: Int = defaultThreads(),
) {
    /** At least one language model mode is on, so the model is worth loading. */
    val llmEnabled: Boolean get() = correctText || llmHighlights

    companion object {
        fun defaultThreads(): Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
        fun maxThreads(): Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
    }
}

/** Where the settings live: shared preferences on a phone, a map in tests. */
interface SettingsStorage {
    fun getString(key: String): String?
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getFloat(key: String, default: Float): Float
    fun getInt(key: String, default: Int): Int

    /** Writes all values at once; each value is a String, Boolean, Float or Int. */
    fun write(values: Map<String, Any>)
}

private class PreferencesStorage(context: Context) : SettingsStorage {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    override fun getFloat(key: String, default: Float): Float = prefs.getFloat(key, default)
    override fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)

    override fun write(values: Map<String, Any>) {
        val editor = prefs.edit()
        for ((key, value) in values) {
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                is Int -> editor.putInt(key, value)
            }
        }
        editor.apply()
    }
}

class SettingsRepository(private val storage: SettingsStorage) {

    constructor(context: Context) : this(PreferencesStorage(context))

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    val current: AppSettings get() = _state.value

    fun update(transform: (AppSettings) -> AppSettings) {
        synchronized(this) {
            val updated = transform(_state.value)
            storage.write(
                mapOf(
                    KEY_ASR to updated.asrModelId,
                    KEY_EMBEDDING to updated.embeddingModelId,
                    KEY_LLM to updated.llmModelId,
                    KEY_CORRECT_TEXT to updated.correctText,
                    KEY_LLM_HIGHLIGHTS to updated.llmHighlights,
                    KEY_LANGUAGE to updated.language,
                    KEY_ENHANCE_AUDIO to updated.enhanceAudio,
                    KEY_QUALITY to updated.diarizationQuality.name,
                    KEY_SEPARATION to updated.speakerSeparation,
                    KEY_THREADS to updated.numThreads,
                ),
            )
            _state.value = updated
        }
    }

    private fun load(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            asrModelId = ModelCatalog.asr(storage.getString(KEY_ASR) ?: defaults.asrModelId).id,
            embeddingModelId = ModelCatalog.embedding(storage.getString(KEY_EMBEDDING) ?: defaults.embeddingModelId).id,
            llmModelId = ModelCatalog.llm(storage.getString(KEY_LLM) ?: defaults.llmModelId).id,
            correctText = storage.getBoolean(KEY_CORRECT_TEXT, defaults.correctText),
            llmHighlights = storage.getBoolean(KEY_LLM_HIGHLIGHTS, defaults.llmHighlights),
            language = storage.getString(KEY_LANGUAGE) ?: defaults.language,
            enhanceAudio = storage.getBoolean(KEY_ENHANCE_AUDIO, defaults.enhanceAudio),
            diarizationQuality = storage.getString(KEY_QUALITY)
                ?.let { name -> DiarizationQuality.entries.firstOrNull { it.name == name } }
                ?: defaults.diarizationQuality,
            speakerSeparation = storage.getFloat(KEY_SEPARATION, defaults.speakerSeparation),
            numThreads = storage.getInt(KEY_THREADS, defaults.numThreads).coerceIn(1, AppSettings.maxThreads()),
        )
    }

    companion object {
        const val KEY_ASR = "asr_model"
        const val KEY_EMBEDDING = "embedding_model"
        const val KEY_LLM = "llm_model"
        const val KEY_CORRECT_TEXT = "llm_correct_text"
        const val KEY_LLM_HIGHLIGHTS = "llm_highlights"
        const val KEY_LANGUAGE = "language"
        const val KEY_ENHANCE_AUDIO = "enhance_audio"
        const val KEY_QUALITY = "diarization_quality"
        const val KEY_SEPARATION = "speaker_separation"
        const val KEY_THREADS = "threads"
    }
}
