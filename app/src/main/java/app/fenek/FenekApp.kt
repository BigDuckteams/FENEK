package app.fenek

import android.app.Application
import android.content.Context
import app.fenek.data.SettingsRepository
import app.fenek.data.TranscriptRepository
import app.fenek.model.ModelCatalog
import app.fenek.model.ModelInfo
import app.fenek.model.ModelManager
import app.fenek.record.Recorder
import app.fenek.service.JobController
import app.fenek.service.Notifications
import app.fenek.service.WorkService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
    val transcripts = TranscriptRepository(context, scope)
    val jobs = JobController(context, scope, settings, models, transcripts)
    val recorder = Recorder(context, scope)

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
