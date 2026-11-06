package app.fenek.llm

import app.fenek.data.SettingsRepository
import java.io.File

/**
 * A native crash inside the language model kills the process and cannot be caught. The marker file
 * exists exactly while the model is loaded, so finding it at startup means the previous run died
 * inside the model; the app then switches the language model modes off instead of crashing again.
 */
class LlmCrashMarker(private val file: File) {

    /** Called before the model is loaded. */
    fun enter() {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(System.currentTimeMillis().toString())
        }
    }

    /** Called after the model has been released. */
    fun leave() {
        runCatching { file.delete() }
    }

    /** The previous run ended while the model was loaded. */
    fun wasInterrupted(): Boolean = file.exists()

    /**
     * Call once at startup. If the previous run died inside the model, both language model modes
     * are switched off (the user can turn them on again) and the marker is cleared.
     *
     * @return true when the previous run died inside the model, so the user has to be told
     */
    fun recover(settings: SettingsRepository): Boolean {
        if (!wasInterrupted()) return false
        settings.update { it.copy(correctText = false, llmHighlights = false) }
        leave()
        return true
    }
}
