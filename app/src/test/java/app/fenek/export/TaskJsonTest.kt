package app.fenek.export

import app.fenek.data.LlmItem
import app.fenek.data.Segment
import app.fenek.data.Speaker
import app.fenek.data.Transcript
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskJsonTest {

    private fun transcript(vararg segments: Segment, speakers: List<Speaker> = emptyList()) = Transcript(
        id = "t", title = "Диалог", createdAt = 0, durationMs = 20_000, sourceName = "cats.wav",
        asrModelId = "m", asrModelTitle = "GigaAM v3 RNNT", processingMs = 5_000,
        speakers = speakers, segments = segments.toList(),
    )

    private fun parse(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    @Test
    fun theExampleFromTheTaskIsReproducedExactly() {
        val json = Exporter.taskJson(transcript(Segment(0, 1_250, 4_380, "Добрый день!")))
        val expected = Json.parseToJsonElement(
            """{"transcript":[{"speaker_id":"speaker_1","start_ms":1250,"end_ms":4380,"text":"Добрый день!"}]}""",
        )
        assertEquals(expected, Json.parseToJsonElement(json))
    }

    @Test
    fun onlyTheRequiredKeysAreWritten() {
        val root = parse(Exporter.taskJson(transcript(Segment(0, 0, 1_000, "Привет."), Segment(1, 1_100, 2_000, "Здравствуйте."))))
        assertEquals(setOf("transcript"), root.keys)
        for (item in root.getValue("transcript").jsonArray) {
            assertEquals(setOf("speaker_id", "start_ms", "end_ms", "text"), item.jsonObject.keys)
        }
    }

    @Test
    fun timesAreIntegersAndSpeakerIdsAreStrings() {
        val item = parse(Exporter.taskJson(transcript(Segment(2, 1_250, 4_380, "Мяу.")))).getValue("transcript").jsonArray.single().jsonObject
        val start = item.getValue("start_ms").jsonPrimitive
        val end = item.getValue("end_ms").jsonPrimitive
        assertFalse(start.isString)
        assertFalse(end.isString)
        assertEquals("1250", start.content)
        assertEquals("4380", end.content)
        assertEquals(4_380L, end.long)
        assertTrue(item.getValue("speaker_id").jsonPrimitive.isString)
        assertEquals("speaker_3", item.getValue("speaker_id").jsonPrimitive.content)
    }

    @Test
    fun speakerIdsFollowTheParticipantNumberAndIgnoreRenaming() {
        val renamed = transcript(
            Segment(0, 0, 1_000, "Раз."), Segment(1, 1_500, 2_500, "Два."), Segment(0, 3_000, 4_000, "Три."),
            speakers = listOf(Speaker(0, "Мурка"), Speaker(1, "Барсик")),
        )
        val ids = parse(Exporter.taskJson(renamed)).getValue("transcript").jsonArray.map { it.jsonObject.getValue("speaker_id").jsonPrimitive.content }
        assertEquals(listOf("speaker_1", "speaker_2", "speaker_1"), ids)
        assertFalse(Exporter.taskJson(renamed).contains("Мурка"))
    }

    @Test
    fun everyPhraseIsItsOwnEntryInTimeOrder() {
        val json = parse(
            Exporter.taskJson(
                transcript(Segment(0, 5_000, 6_000, "Позже."), Segment(0, 1_000, 2_000, "Раньше."), Segment(0, 2_000, 3_000, "Потом.")),
            ),
        )
        val items = json.getValue("transcript").jsonArray.map { it.jsonObject }
        assertEquals(listOf("Раньше.", "Потом.", "Позже."), items.map { it.getValue("text").jsonPrimitive.content })
        assertEquals(listOf(1_000L, 2_000L, 5_000L), items.map { it.getValue("start_ms").jsonPrimitive.long })
    }

    @Test
    fun endIsNeverBeforeStart() {
        val item = parse(Exporter.taskJson(transcript(Segment(0, 3_000, 2_000, "Странно.")))).getValue("transcript").jsonArray.single().jsonObject
        assertEquals(3_000L, item.getValue("end_ms").jsonPrimitive.long)
    }

    @Test
    fun anEmptyTranscriptGivesAnEmptyList() {
        val root = parse(Exporter.taskJson(transcript()))
        assertEquals(JsonArray(emptyList()), root.getValue("transcript"))
    }

    @Test
    fun cyrillicIsWrittenAsIsAndQuotesAreEscapedWithoutAByteOrderMark() {
        val text = "Он сказал: \"Мяу\" и ушёл."
        val json = Exporter.taskJson(transcript(Segment(0, 0, 1_000, text)))
        assertTrue(json, json.startsWith("{"))
        assertTrue(json, json.contains("ушёл"))
        // Cyrillic is not turned into \uXXXX escapes, quotes are escaped.
        assertFalse(json, json.contains("\\u04"))
        assertTrue(json, json.contains("\\\"Мяу\\\""))
        assertEquals(text, parse(json).getValue("transcript").jsonArray.single().jsonObject.getValue("text").jsonPrimitive.content)
    }

    @Test
    fun theCorrectedTextIsExported() {
        val corrected = Segment(0, 0, 2_000, "Привет, как дела?", rawText = "привет как дела")
        val item = parse(Exporter.taskJson(transcript(corrected))).getValue("transcript").jsonArray.single().jsonObject
        assertEquals("Привет, как дела?", item.getValue("text").jsonPrimitive.content)
        assertFalse(item.containsKey("rawText"))
    }

    @Test
    fun theJsonFormatIsTheTaskFormatAndTheOldOneIsSeparate() {
        val t = transcript(Segment(0, 0, 1_000, "Привет."))
        assertEquals(Exporter.taskJson(t), Exporter.render(t, ExportFormat.JSON))
        assertEquals(Exporter.extendedJson(t), Exporter.render(t, ExportFormat.EXTENDED_JSON))
        assertEquals(6, ExportFormat.entries.size)
        assertEquals("JSON расшифровки (.json)", ExportFormat.JSON.title)
        assertEquals("Расширенный JSON (.json)", ExportFormat.EXTENDED_JSON.title)
    }

    @Test
    fun extendedJsonKeepsItsFieldsAndAddsProcessingTimeRtfLanguageModelAndRecognizedText() {
        val t = transcript(Segment(0, 1_000, 3_000, "Привет, как дела?", rawText = "привет как дела"))
            .copy(llmModelId = "qwen2.5-1.5b-instruct-q4", textCorrected = true, llmSummary = listOf(LlmItem(1_000, 0, "Поздоровались.")))
        val root = parse(Exporter.extendedJson(t))

        assertEquals("Диалог", root.getValue("title").jsonPrimitive.content)
        assertEquals(20.0, root.getValue("durationSeconds").jsonPrimitive.content.toDouble(), 0.0)
        assertEquals(5.0, root.getValue("processingSeconds").jsonPrimitive.content.toDouble(), 0.0)
        assertEquals(0.25, root.getValue("rtf").jsonPrimitive.content.toDouble(), 1e-9)
        assertEquals("Qwen2.5 1.5B Instruct", root.getValue("languageModel").jsonPrimitive.content)
        val utterance = root.getValue("utterances").jsonArray.single().jsonObject
        assertEquals("привет как дела", utterance.getValue("rawText").jsonPrimitive.content)
        assertEquals("Привет, как дела?", utterance.getValue("text").jsonPrimitive.content)
        assertEquals(1, utterance.getValue("speakerId").jsonPrimitive.long)
    }

    @Test
    fun extendedJsonHasNoLanguageModelWhenItDidNothing() {
        val t = transcript(Segment(0, 0, 1_000, "Привет."))
        assertTrue(parse(Exporter.extendedJson(t)).getValue("languageModel") is kotlinx.serialization.json.JsonNull)
        assertNull(t.llmModelId)
        assertTrue(parse(Exporter.extendedJson(t)).getValue("rtf") is JsonPrimitive)
    }
}
