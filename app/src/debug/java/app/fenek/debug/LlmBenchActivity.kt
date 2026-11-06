package app.fenek.debug

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import app.fenek.appContainer
import app.fenek.data.Transcript
import app.fenek.data.TranscriptJson
import app.fenek.llm.HighlightsWriter
import app.fenek.llm.LlamaEngine
import app.fenek.llm.TextCorrector
import app.fenek.model.ModelCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Debug builds only. Runs the language model on the first stored transcript with very long time
 * limits, so that the answers can be judged without the speed of the device getting in the way:
 * the prompt, the grammar, the parsing and the guard. Results go to the log (tag LlmBench) and to
 * `files/bench/llm.txt`.
 *
 * adb shell am start -n app.fenek/.debug.LlmBenchActivity --ei threads 2 --es model qwen2.5-0.5b-instruct-q4
 */
class LlmBenchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val threads = intent.getIntExtra("threads", 2)
        val model = ModelCatalog.llm(intent.getStringExtra("model") ?: appContainer.settings.current.llmModelId)
        val report = StringBuilder()
        fun say(line: String) {
            Log.i(TAG, line)
            report.appendLine(line)
        }
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val stored = File(filesDir, "transcripts").listFiles { f -> f.extension == "json" }.orEmpty()
                    .map { TranscriptJson.decodeFromString(Transcript.serializer(), it.readText()) }
                    .maxByOrNull { it.createdAt } ?: error("no transcript to work on")
                // --ez strip true: lower case and no punctuation, like Zipformer or Whisper Base give.
                val transcript = if (intent.getBooleanExtra("strip", false)) {
                    stored.copy(segments = stored.segments.map { s ->
                        s.copy(text = s.text.lowercase().replace(Regex("[^\\p{L}\\p{N}\\s]"), "").replace(Regex("\\s+"), " ").trim())
                    })
                } else {
                    stored
                }
                val path = appContainer.models.path(model, model.files.first().name)
                say("model ${model.title}, $threads threads, ${transcript.segments.size} phrases")

                val loadStart = SystemClock.elapsedRealtime()
                val engine = LlamaEngine.loader(applicationContext).load(path, threads)
                say("loaded in ${SystemClock.elapsedRealtime() - loadStart} ms")
                engine.use {
                    val correctStart = SystemClock.elapsedRealtime()
                    val corrected = TextCorrector(engine, timeoutMs = 900_000).correct(transcript.segments) { done, total ->
                        say("correction progress $done/$total at ${SystemClock.elapsedRealtime() - correctStart} ms")
                    }
                    say(
                        "correction: changed=${corrected.changed} answeredBatches=${corrected.answered} " +
                            "failedBatches=${corrected.failedBatches} in ${SystemClock.elapsedRealtime() - correctStart} ms",
                    )
                    corrected.segments.forEachIndexed { i, s ->
                        val before = transcript.segments[i].text
                        say("  [$i] ${if (s.text == before) "same   " else "CHANGED"} | $before" + if (s.text != before) "  ->  ${s.text}" else "")
                    }

                    val highlightsStart = SystemClock.elapsedRealtime()
                    val written = HighlightsWriter(engine, timeoutMs = 900_000)
                        .write(corrected.segments) { id -> "Спикер ${id + 1}" }
                    say("highlights in ${SystemClock.elapsedRealtime() - highlightsStart} ms: " + if (written == null) "NOTHING USABLE" else "ok")
                    written?.summary?.forEach { say("  summary @${it.startMs}: ${it.text}") }
                    written?.tasks?.forEach { say("  task @${it.startMs} sp${it.speaker}: ${it.text}") }
                }
            } catch (e: Throwable) {
                say("FAILED: $e")
                Log.e(TAG, "failed", e)
            } finally {
                File(filesDir, "bench").apply { mkdirs() }.resolve("llm.txt").writeText(report.toString())
                File(filesDir, "bench/llm.done").writeText("done")
            }
        }
    }

    private companion object {
        const val TAG = "LlmBench"
    }
}
