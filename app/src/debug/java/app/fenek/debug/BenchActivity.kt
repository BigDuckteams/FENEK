package app.fenek.debug

import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import app.fenek.audio.AudioDecoder
import app.fenek.data.AppSettings
import app.fenek.engine.Diarizer
import app.fenek.engine.TurnBuilder
import app.fenek.appContainer
import app.fenek.model.ModelCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Debug builds only. Runs the speaker pipeline (not the recognition) on a WAV file and writes the
 * speaker turns to `files/bench/<name>.turns.txt` as "start end speaker" lines, so that the error
 * rate can be measured against a reference annotation.
 *
 * adb shell am start -n app.fenek/.debug.BenchActivity --es wav files/bench/in/x.wav --ei speakers 4
 * (`speakers` omitted or 0: detect the number of participants; relative paths are in the app's files)
 */
class BenchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val wav = intent.getStringExtra("wav")?.let { File(it).takeIf(File::isAbsolute) ?: File(filesDir, it) }
        if (wav == null || !wav.isFile) {
            Log.e(TAG, "no input file: ${intent.getStringExtra("wav")}")
            finish()
            return
        }
        val speakers = intent.getIntExtra("speakers", 0).takeIf { it > 0 }
        val shift = intent.getFloatExtra("shift", 0.25f)
        val separation = intent.getFloatExtra("separation", 0.5f)
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val models = applicationContext.appContainer.models
                val segmentation = ModelCatalog.segmentation
                val embedding = ModelCatalog.defaultEmbedding
                val pcmFile = File(cacheDir, "bench.pcm")
                val started = SystemClock.elapsedRealtime()
                val pcm = AudioDecoder.decode(applicationContext, Uri.fromFile(wav), pcmFile) {}
                val segments = Diarizer(
                    segmentationModel = models.path(segmentation, segmentation.files.first().name),
                    embeddingModel = models.path(embedding, embedding.files.first().name),
                    numThreads = AppSettings.defaultThreads(),
                    windowShiftRatio = shift,
                ).use { it.run(pcm, speakers, separation) {} }
                val turns = TurnBuilder.build(segments)
                val out = File(filesDir, "bench/${wav.nameWithoutExtension}.turns.txt")
                out.parentFile?.mkdirs()
                out.writeText(turns.joinToString("\n") { "%.3f %.3f %d".format(java.util.Locale.US, it.start, it.end, it.speaker) })
                pcmFile.delete()
                Log.i(
                    TAG,
                    "done ${wav.name}: ${turns.size} turns, ${turns.map { it.speaker }.distinct().size} speakers, " +
                        "${SystemClock.elapsedRealtime() - started} ms for %.0f s of audio".format(pcm.durationSec),
                )
            } catch (e: Throwable) {
                Log.e(TAG, "failed", e)
            } finally {
                File(filesDir, "bench/${wav.nameWithoutExtension}.done").writeText("done")
                finish()
            }
        }
    }

    private companion object {
        const val TAG = "Bench"
    }
}
