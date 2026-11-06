package app.fenek.service

import android.content.Context
import app.fenek.audio.AudioDecodeException
import app.fenek.data.SettingsRepository
import app.fenek.data.TranscriptRepository
import app.fenek.engine.JobProgress
import app.fenek.engine.JobRequest
import app.fenek.engine.MissingModelsException
import app.fenek.engine.Stage
import app.fenek.engine.TranscriptionPipeline
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

sealed interface JobState {
    data object Idle : JobState
    data class Running(val request: JobRequest, val progress: JobProgress) : JobState
    data class Done(val transcriptId: String, val title: String) : JobState
    data class Failed(val fileName: String, val message: String) : JobState
}

/** Runs one transcription at a time in the application scope, independent of the UI. */
class JobController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settings: SettingsRepository,
    private val models: ModelManager,
    private val transcripts: TranscriptRepository,
) {

    private val _state = MutableStateFlow<JobState>(JobState.Idle)
    val state: StateFlow<JobState> = _state.asStateFlow()
    private var job: Job? = null

    /** Returns false if another transcription is still running. */
    fun start(request: JobRequest): Boolean {
        synchronized(this) {
            if (_state.value is JobState.Running) return false
            _state.value = JobState.Running(request, JobProgress(Stage.PREPARING, 0f, 0f))
            WorkService.start(context)
            job = scope.launch(Dispatchers.Default) {
                try {
                    val pipeline = TranscriptionPipeline(context, models, settings.current)
                    val transcript = pipeline.run(request) { progress ->
                        _state.update { if (it is JobState.Running) it.copy(progress = progress) else it }
                    }
                    // Redoing a transcript keeps it in the favourites.
                    val previous = transcripts.get(transcript.id)
                    transcripts.save(if (previous != null) transcript.copy(favorite = previous.favorite) else transcript)
                    _state.value = JobState.Done(transcript.id, transcript.title)
                    Notifications.showFinished(context, transcript)
                } catch (e: CancellationException) {
                    _state.value = JobState.Idle
                    throw e
                } catch (e: Throwable) {
                    val message = describe(e)
                    _state.value = JobState.Failed(request.displayName, message)
                    Notifications.showFailed(context, request.displayName, message)
                }
            }
            return true
        }
    }

    fun cancel() {
        job?.cancel()
    }

    /** Clears a finished or failed state once the UI has shown it. */
    fun acknowledge() {
        _state.update { if (it is JobState.Done || it is JobState.Failed) JobState.Idle else it }
    }

    private fun describe(e: Throwable): String = when (e) {
        is MissingModelsException, is AudioDecodeException -> e.message.orEmpty()
        is OutOfMemoryError -> "Не хватило памяти. Выберите модель поменьше или закройте другие приложения."
        else -> "Ошибка обработки: ${e.message ?: e.javaClass.simpleName}"
    }
}
