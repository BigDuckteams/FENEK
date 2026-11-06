package app.fenek.model

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

sealed interface ModelState {
    data object Missing : ModelState
    data class Downloading(val bytes: Long, val total: Long) : ModelState {
        val fraction: Float get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
    }
    data object Ready : ModelState
    data class Failed(val message: String) : ModelState
}

class ModelManager(context: Context, private val scope: CoroutineScope) {

    private val root = File(context.filesDir, "models")
    private val jobs = HashMap<String, Job>()

    init {
        // Models that left the catalog (such as the old speaker model) only take up space.
        root.listFiles()
            ?.filter { it.isDirectory && ModelCatalog.all.none { model -> model.id == it.name } }
            ?.forEach { it.deleteRecursively() }
    }

    private val _states = MutableStateFlow(
        ModelCatalog.all.associate { it.id to if (isReady(it)) ModelState.Ready else ModelState.Missing },
    )
    val states: StateFlow<Map<String, ModelState>> = _states.asStateFlow()

    fun dir(model: ModelInfo): File = File(root, model.id)

    fun path(model: ModelInfo, name: String): String = File(dir(model), name).absolutePath

    fun isReady(model: ModelInfo): Boolean = model.files.all { file ->
        val f = File(dir(model), file.name)
        f.isFile && (file.sizeBytes <= 0 || f.length() == file.sizeBytes)
    }

    fun state(model: ModelInfo): ModelState = _states.value[model.id] ?: ModelState.Missing

    /** Bytes already on disk for [model], including partial downloads. */
    fun bytesOnDisk(model: ModelInfo): Long =
        dir(model).listFiles()?.sumOf { it.length() } ?: 0L

    fun download(model: ModelInfo) {
        synchronized(jobs) {
            if (jobs[model.id]?.isActive == true) return
            if (isReady(model)) {
                setState(model, ModelState.Ready)
                return
            }
            setState(model, ModelState.Downloading(bytesOnDisk(model), model.totalBytes))
            jobs[model.id] = scope.launch(Dispatchers.IO) { runDownload(model) }
        }
    }

    fun cancel(model: ModelInfo) {
        synchronized(jobs) { jobs[model.id] }?.cancel()
    }

    fun delete(model: ModelInfo) {
        cancel(model)
        dir(model).deleteRecursively()
        setState(model, ModelState.Missing)
    }

    private suspend fun runDownload(model: ModelInfo) {
        val dir = dir(model).apply { mkdirs() }
        val total = model.totalBytes
        try {
            val remaining = model.files.sumOf { file ->
                val target = File(dir, file.name)
                val partial = File(dir, file.name + ".part")
                when {
                    target.isFile && target.length() == file.sizeBytes -> 0L
                    partial.isFile -> (file.sizeBytes - partial.length()).coerceAtLeast(0)
                    else -> file.sizeBytes
                }
            }
            if (dir.usableSpace < remaining + FREE_SPACE_RESERVE) {
                throw DownloadException("Недостаточно места: нужно ещё ${remaining / 1_000_000 + 1} МБ")
            }
            var done = 0L
            for (file in model.files) {
                val target = File(dir, file.name)
                if (!(target.isFile && target.length() == file.sizeBytes)) {
                    val before = done
                    Downloader.download(file, target) { bytes ->
                        setState(model, ModelState.Downloading(before + bytes, total))
                    }
                }
                done += file.sizeBytes
                setState(model, ModelState.Downloading(done, total))
            }
            setState(model, if (isReady(model)) ModelState.Ready else ModelState.Failed("Файлы модели повреждены"))
        } catch (e: CancellationException) {
            // Partial files stay on disk, the next download resumes them.
            setState(model, ModelState.Missing)
            throw e
        } catch (e: Exception) {
            setState(model, ModelState.Failed(e.message ?: e.javaClass.simpleName))
        } finally {
            val self = currentCoroutineContext()[Job]
            synchronized(jobs) { if (jobs[model.id] === self) jobs.remove(model.id) }
        }
    }

    private fun setState(model: ModelInfo, state: ModelState) {
        _states.update { it + (model.id to state) }
    }

    private companion object {
        const val FREE_SPACE_RESERVE = 100L * 1024 * 1024
    }
}
