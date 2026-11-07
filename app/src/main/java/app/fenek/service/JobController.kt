package app.fenek.service

import android.content.Context
import app.fenek.audio.AudioDecodeException
import app.fenek.data.PendingTranscripts
import app.fenek.data.SettingsRepository
import app.fenek.data.TranscriptRepository
import app.fenek.engine.JobProgress
import app.fenek.engine.JobRequest
import app.fenek.engine.MissingModelsException
import app.fenek.engine.PauseGate
import app.fenek.engine.Stage
import app.fenek.engine.TranscriptionPipeline
import app.fenek.llm.LlmPostProcessor
import app.fenek.model.ModelManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the processor is doing now. */
sealed interface JobState {
    data object Idle : JobState

    /** [pausing]: a pause was asked for and the job is still working towards the next safe point. */
    data class Running(val request: JobRequest, val progress: JobProgress, val pausing: Boolean = false) : JobState
}

/** A job that waits for its turn or is paused. */
data class QueuedJob(val request: JobRequest, val paused: Boolean, val progress: Float)

/** A finished transcription the user has not been told about yet. */
data class JobResult(val transcriptId: String, val title: String)

/** A failed transcription that stays on the home screen until it is dismissed. */
data class JobFailure(val id: String, val fileName: String, val message: String)

enum class Enqueued { STARTED, QUEUED, DUPLICATE }

/**
 * Runs transcriptions one at a time in the application scope, independent of the UI. Jobs that are
 * started while another one runs wait in a queue and start by themselves. A running job can be
 * paused (it stops at the next safe point and keeps what it has done) and another one started
 * instead; a paused job stays in the queue until the user resumes or removes it.
 */
class JobController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settings: SettingsRepository,
    private val models: ModelManager,
    private val transcripts: TranscriptRepository,
    private val pending: PendingTranscripts,
    private val llm: LlmPostProcessor?,
) {

    private class Run(val gate: PauseGate) {
        var job: Job? = null
    }

    private val lock = Any()
    private val order = JobQueue<JobRequest>(idOf = { it.id }, sourceOf = { it.source.toString() })
    private val runs = HashMap<String, Run>()
    private val progressOf = HashMap<String, JobProgress>()

    private val _state = MutableStateFlow<JobState>(JobState.Idle)
    val state: StateFlow<JobState> = _state.asStateFlow()

    private val _queue = MutableStateFlow<List<QueuedJob>>(emptyList())

    /** Jobs that wait for their turn, then the paused ones. */
    val queue: StateFlow<List<QueuedJob>> = _queue.asStateFlow()

    private val _results = MutableStateFlow<List<JobResult>>(emptyList())

    /** Finished jobs the home screen has not announced yet; [acknowledgeResult] removes one. */
    val results: StateFlow<List<JobResult>> = _results.asStateFlow()

    private val _failures = MutableStateFlow<List<JobFailure>>(emptyList())
    val failures: StateFlow<List<JobFailure>> = _failures.asStateFlow()

    /** Starts the job at once if nothing is running, otherwise puts it in the queue. */
    fun enqueue(request: JobRequest): Enqueued {
        val result = synchronized(lock) {
            if (order.add(request) == null) return Enqueued.DUPLICATE
            // A new attempt replaces the failure card of the previous one.
            dismissFailure(request.id)
            pump()
            publish()
            if (order.running()?.item?.id == request.id) Enqueued.STARTED else Enqueued.QUEUED
        }
        WorkService.start(context)
        return result
    }

    /** Pauses the running job at its next safe point; the next waiting job starts then. */
    fun pause() {
        synchronized(lock) {
            val id = order.running()?.item?.id ?: return
            runs[id]?.gate?.pause()
            publish()
        }
    }

    /** Puts a paused job first in line; it continues as soon as nothing else is running. */
    fun resume(id: String) {
        synchronized(lock) {
            if (!order.resume(id)) return
            pump()
            publish()
        }
        WorkService.start(context)
    }

    /** Resumes the first paused job (the notification action). */
    fun resumeFirstPaused() {
        val id = synchronized(lock) { _queue.value.firstOrNull { it.paused }?.request?.id } ?: return
        resume(id)
    }

    /** Makes a waiting or paused job run now; the running one is paused and stays in the queue. */
    fun startNow(id: String) {
        synchronized(lock) {
            if (!order.runNow(id)) return
            val running = order.running()?.item?.id
            if (running != null) runs[running]?.gate?.pause() else pump()
            publish()
        }
        WorkService.start(context)
    }

    /** Stops the running job for good; the next one starts. */
    fun cancel() {
        synchronized(lock) {
            val id = order.running()?.item?.id ?: return
            runs[id]?.job?.cancel()
        }
    }

    /** Removes a waiting or paused job from the queue (a paused one loses what it has done). */
    fun remove(id: String) {
        synchronized(lock) {
            val run = runs[id]
            if (run != null) run.job?.cancel() else order.remove(id)
            publish()
        }
    }

    /** Stops everything: the running job and the whole queue. */
    fun cancelAll() {
        synchronized(lock) {
            order.entries.map { it.item.id }.forEach { id ->
                val run = runs[id]
                if (run != null) run.job?.cancel() else order.remove(id)
            }
            publish()
        }
    }

    fun acknowledgeResult(transcriptId: String) {
        _results.update { list -> list.filterNot { it.transcriptId == transcriptId } }
    }

    fun dismissFailure(id: String) {
        _failures.update { list -> list.filterNot { it.id == id } }
    }

    /** Starts the next waiting job when nothing is running. Call with [lock] held. */
    private fun pump() {
        if (order.running() != null) return
        val next = order.next() ?: return
        val request = next.item
        order.start(request.id)
        val run = runs[request.id]
        if (run != null) {
            run.gate.resume()
        } else {
            progressOf[request.id] = JobProgress(Stage.PREPARING, 0f, 0f)
            launch(request)
        }
    }

    private fun launch(request: JobRequest) {
        val gate = PauseGate(onParked = { onParked(request.id) })
        val run = Run(gate)
        runs[request.id] = run
        run.job = scope.launch(Dispatchers.IO) {
            try {
                val pipeline = TranscriptionPipeline(context, models, settings.current, llm)
                val transcript = pipeline.run(
                    request,
                    onProgress = { progress -> onProgress(request.id, progress) },
                    keepPending = pending::write,
                    gate = gate,
                )
                // Redoing a transcript keeps it in the favourites.
                val previous = transcripts.get(transcript.id)
                transcripts.save(if (previous != null) transcript.copy(favorite = previous.favorite) else transcript)
                _results.update { list -> list.filterNot { it.transcriptId == transcript.id } + JobResult(transcript.id, transcript.title) }
                Notifications.showFinished(context, transcript)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val message = describe(e)
                _failures.update { list -> list.filterNot { it.id == request.id } + JobFailure(request.id, request.displayName, message) }
                Notifications.showFailed(context, request.id, request.displayName, message)
            } finally {
                // The recoverable copy is only for a process that died: any normal end drops it.
                pending.delete(request.id)
                finished(request.id)
            }
        }
    }

    private fun onProgress(id: String, progress: JobProgress) {
        synchronized(lock) {
            progressOf[id] = progress
            if (order.running()?.item?.id == id) {
                _state.update { (it as? JobState.Running)?.copy(progress = progress) ?: it }
            }
        }
    }

    /** The job has reached a checkpoint and waits: it leaves the processor to the next one. */
    private fun onParked(id: String) {
        synchronized(lock) {
            if (order.running()?.item?.id != id) return
            order.park(id, progressOf[id]?.overall ?: 0f)
            pump()
            publish()
        }
    }

    private fun finished(id: String) {
        synchronized(lock) {
            runs.remove(id)
            progressOf.remove(id)
            order.remove(id)
            pump()
            publish()
        }
    }

    /** Copies the queue into the observable state. Call with [lock] held. */
    private fun publish() {
        val running = order.running()
        _state.value = if (running == null) {
            JobState.Idle
        } else {
            val id = running.item.id
            JobState.Running(
                request = running.item,
                progress = progressOf[id] ?: JobProgress(Stage.PREPARING, 0f, 0f),
                pausing = runs[id]?.gate?.pauseRequested == true,
            )
        }
        _queue.value = order.entries
            .filter { it.status != JobQueue.Status.RUNNING }
            .map { QueuedJob(it.item, paused = it.status == JobQueue.Status.PAUSED, progress = it.progress) }
    }

    private fun describe(e: Throwable): String = when (e) {
        is MissingModelsException, is AudioDecodeException -> e.message.orEmpty()
        is OutOfMemoryError -> "Не хватило памяти. Выберите модель поменьше или закройте другие приложения."
        else -> "Ошибка обработки: ${e.message ?: e.javaClass.simpleName}"
    }
}
