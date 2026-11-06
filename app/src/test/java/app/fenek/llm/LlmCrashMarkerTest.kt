package app.fenek.llm

import app.fenek.data.InMemorySettingsStorage
import app.fenek.data.SettingsRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LlmCrashMarkerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun marker() = LlmCrashMarker(File(folder.root, "state/llm.running"))

    @Test
    fun theMarkerExistsExactlyWhileTheModelIsLoaded() {
        val marker = marker()
        assertFalse(marker.wasInterrupted())
        marker.enter()
        assertTrue(marker.wasInterrupted())
        marker.leave()
        assertFalse(marker.wasInterrupted())
    }

    @Test
    fun aMarkerLeftByADeadProcessSwitchesBothModesOffAndIsReported() {
        val storage = InMemorySettingsStorage()
        val settings = SettingsRepository(storage)
        settings.update { it.copy(correctText = true, llmHighlights = true) }
        val dead = marker().also { it.enter() }

        // The next start of the app.
        val restarted = LlmCrashMarker(File(folder.root, "state/llm.running"))
        assertTrue(restarted.recover(settings))

        assertFalse(settings.current.correctText)
        assertFalse(settings.current.llmHighlights)
        assertFalse(SettingsRepository(storage).current.correctText)
        assertFalse(dead.wasInterrupted())
        // Reported once: the marker is gone.
        assertFalse(restarted.recover(settings))
    }

    @Test
    fun theModesCanBeTurnedOnAgainAfterTheNotice() {
        val settings = SettingsRepository(InMemorySettingsStorage())
        marker().also { it.enter() }.recover(settings)

        settings.update { it.copy(correctText = true) }
        assertTrue(settings.current.correctText)
    }

    @Test
    fun aNormalRunLeavesTheSettingsAlone() {
        val settings = SettingsRepository(InMemorySettingsStorage())
        assertFalse(marker().recover(settings))
        // The defaults: the summary on, the correction off.
        assertFalse(settings.current.correctText)
        assertTrue(settings.current.llmHighlights)
    }
}
