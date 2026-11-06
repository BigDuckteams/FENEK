package app.fenek.data

import app.fenek.model.ModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryTest {

    @Test
    fun firstRunUsesTheDefaults() {
        val settings = SettingsRepository(InMemorySettingsStorage()).current
        assertEquals(ModelCatalog.defaultAsr.id, settings.asrModelId)
        assertEquals(ModelCatalog.defaultLlm.id, settings.llmModelId)
        // Only the summary is on: the correction did not change WER and slowed processing down.
        assertFalse(settings.correctText)
        assertTrue(settings.llmHighlights)
        assertTrue(settings.llmEnabled)
        assertEquals(DiarizationQuality.BALANCED, settings.diarizationQuality)
        assertEquals(0.5f, settings.speakerSeparation, 0f)
    }

    @Test
    fun changesSurviveARestart() {
        val storage = InMemorySettingsStorage()
        SettingsRepository(storage).update { it.copy(correctText = true, llmHighlights = false, llmModelId = ModelCatalog.qwen05.id) }

        val reopened = SettingsRepository(storage).current
        assertTrue(reopened.correctText)
        assertFalse(reopened.llmHighlights)
        assertEquals(ModelCatalog.qwen05.id, reopened.llmModelId)
    }

    @Test
    fun theLanguageModelIsOffOnlyWhenBothModesAreOff() {
        assertTrue(AppSettings(correctText = false, llmHighlights = true).llmEnabled)
        assertTrue(AppSettings(correctText = true, llmHighlights = false).llmEnabled)
        assertFalse(AppSettings(correctText = false, llmHighlights = false).llmEnabled)
    }

    @Test
    fun anUnknownSavedLanguageModelFallsBackToTheDefault() {
        val storage = InMemorySettingsStorage(hashMapOf(SettingsRepository.KEY_LLM to "removed-model"))
        assertEquals(ModelCatalog.defaultLlm.id, SettingsRepository(storage).current.llmModelId)
    }

    @Test
    fun unknownRecognitionModelAndPrecisionFallBackToo() {
        val storage = InMemorySettingsStorage(
            hashMapOf(SettingsRepository.KEY_ASR to "gone", SettingsRepository.KEY_QUALITY to "ULTRA"),
        )
        val settings = SettingsRepository(storage).current
        assertEquals(ModelCatalog.defaultAsr.id, settings.asrModelId)
        assertEquals(DiarizationQuality.BALANCED, settings.diarizationQuality)
    }

    @Test
    fun threadCountIsLimitedToWhatTheDeviceHas() {
        val storage = InMemorySettingsStorage(hashMapOf(SettingsRepository.KEY_THREADS to 999))
        assertEquals(AppSettings.maxThreads(), SettingsRepository(storage).current.numThreads)
    }
}
