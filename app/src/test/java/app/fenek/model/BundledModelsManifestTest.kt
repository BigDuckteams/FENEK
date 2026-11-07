package app.fenek.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** The "fat" build downloads what `bundled-models.json` lists; it must be what the app expects to find. */
class BundledModelsManifestTest {

    @Test
    fun theManifestListsTheBundledSetOfTheCatalogWithTheSameFilesSizesAndMirrors() {
        val listed = Json.parseToJsonElement(File("bundled-models.json").readText())
            .jsonObject.getValue("models").jsonArray.map { it.jsonObject }

        assertEquals(
            ModelCatalog.bundledSet.map { it.id }.sorted(),
            listed.map { it.getValue("id").jsonPrimitive.content }.sorted(),
        )
        for (entry in listed) {
            val id = entry.getValue("id").jsonPrimitive.content
            val model = ModelCatalog.all.first { it.id == id }
            val files = entry.getValue("files").jsonArray.map { it.jsonObject }

            assertEquals(id, model.files.map { it.name }, files.map { it.getValue("name").jsonPrimitive.content })
            for ((file, remote) in files.zip(model.files)) {
                assertEquals(remote.name, remote.sizeBytes, file.getValue("bytes").jsonPrimitive.long)
                assertEquals(remote.name, remote.urls, file.getValue("urls").jsonArray.map { it.jsonPrimitive.content })
            }
        }
    }
}
