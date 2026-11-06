package app.fenek

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.util.Log
import app.fenek.data.PendingTranscripts
import app.fenek.data.SettingsRepository
import app.fenek.data.TranscriptRepository
import app.fenek.model.ModelCatalog
import app.fenek.model.ModelInfo
import app.fenek.model.ModelManager
import app.fenek.record.Recorder
import app.fenek.llm.LlmCrashMarker
import app.fenek.llm.LlmPostProcessor
import app.fenek.llm.LlamaEngine
import app.fenek.service.JobController
import app.fenek.service.Notifications
import app.fenek.service.WorkService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class FenekApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
    }
}

/** Application-wide singletons. */
class AppContainer(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsRepository(context)
    val models = ModelManager(context, scope)
    private val pending = PendingTranscripts(File(context.filesDir, "pending"))
    val transcripts = TranscriptRepository(context, scope, pending)
    private val crashMarker = LlmCrashMarker(File(context.filesDir, "llm.running"))

    /** The previous run died inside the language model: it was switched off and the user has to be told. */
    val llmCrashNotice = MutableStateFlow(false)

    private val llm = LlmPostProcessor(
        loader = LlamaEngine.loader(context),
        marker = crashMarker,
        availableMemory = {
            val info = ActivityManager.MemoryInfo()
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
            info.availMem
        },
        log = { message, error -> Log.w("Llm", message, error) },
    )
    val jobs = JobController(context, scope, settings, models, transcripts, pending, llm)
    val recorder = Recorder(context, scope)

    init {
        llmCrashNotice.value = crashMarker.recover(settings)
    }

    /** Models needed to process a recording with the current settings. */
    fun requiredModels(asrModelId: String = settings.current.asrModelId): List<ModelInfo> = listOf(
        ModelCatalog.segmentation,
        ModelCatalog.embedding(settings.current.embeddingModelId),
        ModelCatalog.asr(asrModelId),
    )

    fun download(models: List<ModelInfo>) {
        val missing = models.filterNot { this.models.isReady(it) }
        if (missing.isEmpty()) return
        missing.forEach { this.models.download(it) }
        WorkService.start(context)
    }
}

val Context.appContainer: AppContainer get() = (applicationContext as FenekApp).container
