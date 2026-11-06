package app.fenek.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {

    @Test
    fun idsAreUnique() {
        val ids = ModelCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun everyFileHasAnExactSizeAndAtLeastOneSource() {
        for (model in ModelCatalog.all) {
            assertTrue(model.id, model.files.isNotEmpty())
            for (file in model.files) {
                assertTrue("${model.id}/${file.name} size", file.sizeBytes > 0)
                assertTrue("${model.id}/${file.name} urls", file.urls.isNotEmpty())
                assertTrue(file.urls.all { it.startsWith("https://") })
            }
        }
    }

    @Test
    fun theCatalogHasSixRecognitionModelsOneSegmentationOneVoiceModelAndLanguageModels() {
        assertEquals(6, ModelCatalog.asrModels.size)
        assertEquals(ModelKind.SEGMENTATION, ModelCatalog.segmentation.kind)
        assertEquals(1, ModelCatalog.embeddingModels.size)
        assertTrue(ModelCatalog.llmModels.isNotEmpty())
        assertTrue(ModelCatalog.llmModels.all { it.kind == ModelKind.LLM })
    }

    @Test
    fun languageModelsAreSingleGgufFiles() {
        for (model in ModelCatalog.llmModels) {
            assertEquals(model.id, 1, model.files.size)
            assertTrue(model.files.single().name.endsWith(".gguf"))
        }
    }

    @Test
    fun defaultsAreInTheCatalog() {
        assertEquals("gigaam-v3-rnnt-punct", ModelCatalog.defaultAsr.id)
        assertTrue(ModelCatalog.defaultLlm in ModelCatalog.llmModels)
        assertTrue(ModelCatalog.defaultLlm.recommended)
    }

    @Test
    fun unknownIdsFallBackToTheDefaultOfTheirKind() {
        assertSame(ModelCatalog.defaultAsr, ModelCatalog.asr("nope"))
        assertSame(ModelCatalog.defaultEmbedding, ModelCatalog.embedding("nope"))
        assertSame(ModelCatalog.defaultLlm, ModelCatalog.llm("nope"))
    }

    @Test
    fun onlyAsrModelsCarryARecognitionSpec() {
        assertTrue(ModelCatalog.asrModels.all { it.asr != null })
        assertTrue((ModelCatalog.llmModels + ModelCatalog.segmentation + ModelCatalog.embeddingModels).all { it.asr == null })
        assertNotNull(ModelCatalog.llm(ModelCatalog.qwen15.id))
        assertFalse(ModelCatalog.qwen05.recommended)
    }
}
