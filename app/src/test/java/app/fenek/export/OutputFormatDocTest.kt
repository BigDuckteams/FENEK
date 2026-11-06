package app.fenek.export

import app.fenek.data.Segment
import app.fenek.data.Transcript
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The example in docs/OUTPUT_FORMAT.md must be what the exporter really writes. */
class OutputFormatDocTest {

    private fun doc(): String {
        // Unit tests run in the app module; the documentation is one level up.
        val file = listOf(File("../docs/OUTPUT_FORMAT.md"), File("docs/OUTPUT_FORMAT.md")).first { it.isFile }
        return file.readText(Charsets.UTF_8)
    }

    @Test
    fun theExampleInTheDocumentIsWhatTheExporterWrites() {
        val example = Regex("```json\\s*(\\{.*?\\})\\s*```", RegexOption.DOT_MATCHES_ALL).find(doc())?.groupValues?.get(1)
        assertTrue("no json example in the document", example != null)

        val transcript = Transcript(
            id = "t", title = "Пример", createdAt = 0, durationMs = 5_000, sourceName = "example.wav",
            asrModelId = "m", asrModelTitle = "M",
            segments = listOf(Segment(0, 1_250, 4_380, "Добрый день!")),
        )
        assertEquals(Json.parseToJsonElement(example!!), Json.parseToJsonElement(Exporter.taskJson(transcript)))
    }

    @Test
    fun theDocumentNamesBothFormatsOfTheMenu() {
        val text = doc()
        assertTrue(text.contains(ExportFormat.JSON.title))
        assertTrue(text.contains(ExportFormat.EXTENDED_JSON.title))
        assertTrue(text.contains("-extended.json"))
    }
}
