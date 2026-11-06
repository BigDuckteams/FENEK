package app.fenek.model

import app.fenek.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPlanTest {

    private val settings = AppSettings(numThreads = 2)

    private fun plan(settings: AppSettings = this.settings, ready: Set<String> = emptySet(), asr: String = settings.asrModelId) =
        DownloadPlan.of(settings, asr) { it.id in ready }

    @Test
    fun onFirstLaunchEverythingIsMissingAndOneActionStartsAll() {
        val plan = plan()
        assertEquals(
            listOf(ModelCatalog.segmentation.id, ModelCatalog.eres2net.id, ModelCatalog.gigaAmRnnt.id),
            plan.requiredMissing.map { it.id },
        )
        assertEquals(ModelCatalog.defaultLlm, plan.llmMissing)
        assertEquals(4, plan.all.size)
        assertEquals(plan.all.sumOf { it.totalBytes }, plan.totalBytes)
        assertTrue(plan.totalBytes > plan.requiredBytes)
    }

    @Test
    fun theRequiredPartIsSmallEnoughToFetchFirst() {
        // About 265 MB without the language model.
        val plan = plan()
        assertTrue(plan.requiredBytes in 250_000_000..300_000_000)
        assertTrue(plan.llmMissing!!.totalBytes > 400_000_000)
    }

    @Test
    fun readyModelsAreLeftOut() {
        val plan = plan(ready = setOf(ModelCatalog.segmentation.id, ModelCatalog.eres2net.id))
        assertEquals(listOf(ModelCatalog.gigaAmRnnt.id), plan.requiredMissing.map { it.id })
    }

    @Test
    fun whenOnlyTheLanguageModelIsMissingTheRequiredPartIsEmpty() {
        val ready = ModelCatalog.all.map { it.id }.toSet() - ModelCatalog.defaultLlm.id
        val plan = plan(ready = ready)
        assertTrue(plan.requiredMissing.isEmpty())
        assertEquals(ModelCatalog.defaultLlm, plan.llmMissing)
        assertEquals(listOf(ModelCatalog.defaultLlm), plan.all)
    }

    @Test
    fun everythingReadyMeansNothingToDo() {
        val plan = plan(ready = ModelCatalog.all.map { it.id }.toSet())
        assertTrue(plan.isEmpty)
        assertEquals(0L, plan.totalBytes)
    }

    @Test
    fun withBothLanguageModelModesOffTheModelIsNotOffered() {
        val plan = plan(settings.copy(correctText = false, llmHighlights = false))
        assertNull(plan.llmMissing)
        assertEquals(3, plan.all.size)
    }

    @Test
    fun oneModeIsEnoughToOfferTheLanguageModel() {
        assertEquals(ModelCatalog.defaultLlm, plan(settings.copy(correctText = false, llmHighlights = true)).llmMissing)
        assertEquals(ModelCatalog.defaultLlm, plan(settings.copy(correctText = true, llmHighlights = false)).llmMissing)
    }

    @Test
    fun anotherRecognitionModelChangesTheRequiredSet() {
        val plan = plan(asr = ModelCatalog.whisperSmall.id)
        assertTrue(ModelCatalog.whisperSmall in plan.requiredMissing)
        assertTrue(ModelCatalog.gigaAmRnnt !in plan.requiredMissing)
    }

    @Test
    fun theChosenLanguageModelIsTheOneOffered() {
        val plan = plan(settings.copy(llmModelId = ModelCatalog.qwen05.id))
        assertEquals(ModelCatalog.qwen05, plan.llmMissing)
    }

    @Test
    fun aMissingLanguageModelNeverMakesTheRequiredListLonger() {
        val required = DownloadPlan.requiredModels(settings)
        assertEquals(3, required.size)
        assertTrue(required.none { it.kind == ModelKind.LLM })
    }
}
