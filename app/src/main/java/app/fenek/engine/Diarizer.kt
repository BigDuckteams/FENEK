package app.fenek.engine

import android.os.SystemClock
import android.util.Log
import app.fenek.audio.PcmFile
import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Speaker diarization ("who spoke when") for recordings of any length, in two steps.
 *
 * 1. Where is somebody speaking? The recording is cut at quiet moments into blocks of about ten
 *    minutes (which keeps memory bounded) and each block goes through the pyannote pipeline of
 *    sherpa-onnx. Only its speech stretches are used; the local speaker numbers it assigns are
 *    unreliable on recordings made at a distance (a phone on a table, a room with echo).
 * 2. Whose voice is it? The clean speech (one voice at a time) is cut into windows of two seconds,
 *    every window gets a voice fingerprint, and the fingerprints of the whole recording are
 *    clustered into participants. A window never depends on how the first step numbered voices.
 */
class Diarizer(
    segmentationModel: String,
    embeddingModel: String,
    numThreads: Int,
    windowShiftRatio: Float,
) : AutoCloseable {

    private val diarization = OfflineSpeakerDiarization(
        config = OfflineSpeakerDiarizationConfig(
            segmentation = OfflineSpeakerSegmentationModelConfig(
                pyannote = OfflineSpeakerSegmentationPyannoteModelConfig(
                    model = segmentationModel,
                    windowShiftRatio = windowShiftRatio,
                ),
                numThreads = numThreads,
            ),
            embedding = SpeakerEmbeddingExtractorConfig(model = embeddingModel, numThreads = numThreads),
            clustering = FastClusteringConfig(numClusters = -1, threshold = LOCAL_CLUSTER_THRESHOLD),
            minDurationOn = 0.2f,
            minDurationOff = 0.5f,
        ),
    )
    private val embedder = SpeakerEmbedder(embeddingModel, numThreads)

    private class Block(val start: Long, val end: Long)

    /**
     * @param numSpeakers the exact number of participants, if the user knows it
     * @param separation 0..1, higher values accept weaker evidence for one more participant
     *   when the number of participants is not given
     * @return speech segments with speakers numbered 0, 1, … in order of first appearance
     */
    suspend fun run(
        pcm: PcmFile,
        numSpeakers: Int?,
        separation: Float,
        onProgress: (Float) -> Unit,
    ): List<SpeakerSegment> {
        val started = SystemClock.elapsedRealtime()
        val segments = findSpeech(pcm) { onProgress(SPEECH_SHARE * it) }
        if (segments.isEmpty()) return emptyList()
        val speechMs = SystemClock.elapsedRealtime() - started

        val total = pcm.durationSec
        val runs = VoiceWindows.cleanRuns(segments, total)
        val candidates = VoiceWindows.windows(runs)
        val fingerprints = fingerprints(pcm, candidates) { onProgress(SPEECH_SHARE + (1 - SPEECH_SHARE) * it) }
        val windows = candidates.filterIndexed { i, _ -> fingerprints[i] != null }
        val vectors = fingerprints.filterNotNull()

        val labels = VoiceClusters.cluster(vectors, numSpeakers, separation)
        Log.i(
            TAG,
            "speech ${speechMs} ms (${segments.size} segments, ${segments.map { it.speaker }.distinct().size} local " +
                "speakers), ${windows.size} voice windows ${SystemClock.elapsedRealtime() - started - speechMs} ms -> " +
                "${labels.distinct().size} participants",
        )
        onProgress(1f)

        val result = VoiceLabels.assemble(segments, runs, windows, labels, total)
        val order = HashMap<Int, Int>()
        return result.map { it.copy(speaker = order.getOrPut(it.speaker) { order.size }) }
    }

    override fun close() {
        diarization.release()
        embedder.close()
    }

    /** Speech stretches of the whole recording; local speaker numbers are unique across blocks. */
    private suspend fun findSpeech(pcm: PcmFile, onProgress: (Float) -> Unit): List<SpeakerSegment> {
        val blocks = planBlocks(pcm)
        val segments = ArrayList<SpeakerSegment>()
        for ((index, block) in blocks.withIndex()) {
            currentCoroutineContext().ensureActive()
            val base = index.toFloat() / blocks.size
            val span = 1f / blocks.size
            onProgress(base + span * 0.02f)
            val samples = pcm.read(block.start, (block.end - block.start).toInt())
            val offset = block.start.toDouble() / SAMPLE_RATE
            // The callback only reports the embedding stage, which takes most of the time.
            val raw = diarization.processWithCallback(
                samples,
                callback = DiarizationProgress { done, total ->
                    if (total > 0) onProgress(base + span * (0.15f + 0.8f * done / total))
                },
            )
            raw.mapTo(segments) {
                SpeakerSegment(it.start + offset, it.end + offset, index * BLOCK_SPEAKER_STRIDE + it.speaker)
            }
            onProgress(base + span)
        }
        return segments
    }

    /** One voice fingerprint per window, or null where the audio was too short for one. */
    private suspend fun fingerprints(
        pcm: PcmFile,
        windows: List<VoiceWindows.Window>,
        onProgress: (Float) -> Unit,
    ): List<FloatArray?> {
        val result = ArrayList<FloatArray?>(windows.size)
        var chunkStart = Double.NEGATIVE_INFINITY
        var chunk = FloatArray(0)
        for ((i, window) in windows.withIndex()) {
            currentCoroutineContext().ensureActive()
            if (window.end > chunkStart + CHUNK_SECONDS || window.start < chunkStart) {
                chunkStart = window.start
                chunk = pcm.readSeconds(chunkStart, chunkStart + CHUNK_SECONDS)
            }
            val from = ((window.start - chunkStart) * SAMPLE_RATE).toInt().coerceIn(0, chunk.size)
            val to = ((window.end - chunkStart) * SAMPLE_RATE).toInt().coerceIn(from, chunk.size)
            result += if (to - from >= SAMPLE_RATE / 2) embedder.compute(chunk.copyOfRange(from, to)) else null
            if (i % PROGRESS_EVERY == 0) onProgress(i.toFloat() / windows.size)
        }
        return result
    }

    private fun planBlocks(pcm: PcmFile): List<Block> {
        val total = pcm.numSamples
        val size = BLOCK_SECONDS * SAMPLE_RATE
        val search = BLOCK_SEARCH_SECONDS * SAMPLE_RATE
        val blocks = ArrayList<Block>()
        var start = 0L
        while (total - start > size * 5 / 4) {
            val target = start + size
            val cut = pcm.quietestPoint(target - search, target + search)
            blocks += Block(start, cut)
            start = cut
        }
        blocks += Block(start, total)
        return blocks
    }

    companion object {
        private const val TAG = "Diarizer"
        private const val SAMPLE_RATE = PcmFile.SAMPLE_RATE
        private const val BLOCK_SECONDS = 600L
        private const val BLOCK_SEARCH_SECONDS = 20L
        private const val BLOCK_SPEAKER_STRIDE = 1000

        /**
         * Cosine distance threshold of the segmentation pipeline's own clustering. Only the
         * boundaries between speakers matter here, so it is strict: voices are rather split
         * than merged, and the final clustering joins the pieces.
         */
        private const val LOCAL_CLUSTER_THRESHOLD = 0.35f

        /** Share of the progress bar taken by finding the speech; the rest is voice fingerprints. */
        private const val SPEECH_SHARE = 0.6f
        private const val CHUNK_SECONDS = 120.0
        private const val PROGRESS_EVERY = 8
    }
}

/**
 * Progress callback for [OfflineSpeakerDiarization.processWithCallback]. The native code
 * looks up `invoke(IIJ)Ljava/lang/Integer;` by name. Kotlin 2 compiles lambdas through
 * invokedynamic, and the result has only the erased `invoke(Object, Object, Object)`, so
 * the callback must be a real class. R8 keeps it via proguard-rules.pro.
 */
class DiarizationProgress(private val onProgress: (done: Int, total: Int) -> Unit) : (Int, Int, Long) -> Int {
    override fun invoke(done: Int, total: Int, arg: Long): Int {
        onProgress(done, total)
        return 0
    }
}
