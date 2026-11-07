package app.fenek.model

import android.content.Context
import app.fenek.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

sealed interface ModelState {
    data object Missing : ModelState
    /** [fromApk]: the files are copied out of the APK (the "fat" build), no network is used. */
    data class Downloading(val bytes: Long, val total: Long, val fromApk: Boolean = false) : ModelState {
        val fraction: Float get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
    }
    data object Ready : ModelState
    data class Failed(val message: String) : ModelState
}

class ModelManager(context: Context, private val scope: CoroutineScope) {

    private val root = File(context.filesDir, "models")
    private val jobs = HashMap<String, Job>()

    /** Models inside the APK, in the "fat" build only: "download" then copies them instead of using the network. */
    private val bundled: BundledModels? = if (BuildConfig.BUNDLED_MODELS) BundledModels(context.assets) else null

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
            setState(model, ModelState.Downloading(bytesOnDisk(model), model.totalBytes, fromApk = bundled?.has(model) == true))
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
            val fromApk = bundled?.has(model) == true
            var done = 0L
            for (file in model.files) {
                val target = File(dir, file.name)
                if (!(target.isFile && target.length() == file.sizeBytes)) {
                    val before = done
                    if (bundled?.has(model, file) == true) {
                        install(bundled, model, file, target) { bytes ->
                            setState(model, ModelState.Downloading(before + bytes, total, fromApk))
                        }
                    } else {
                        Downloader.download(file, target) { bytes ->
                            setState(model, ModelState.Downloading(before + bytes, total, fromApk))
                        }
                    }
                }
                done += file.sizeBytes
                setState(model, ModelState.Downloading(done, total, fromApk))
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

    /** Copies a file out of the APK; the exact size is checked like that of a download. */
    private suspend fun install(source: BundledModels, model: ModelInfo, file: RemoteFile, target: File, onBytes: (Long) -> Unit) {
        val partial = File(target.path + ".part")
        partial.delete()
        source.open(model, file).use { input ->
            partial.outputStream().use { output ->
                val buffer = ByteArray(COPY_BUFFER)
                var copied = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    copied += read
                    onBytes(copied)
                }
            }
        }
        if (partial.length() != file.sizeBytes) {
            partial.delete()
            throw DownloadException("Встроенный файл ${file.name} повреждён")
        }
        if (!partial.renameTo(target)) throw DownloadException("Не удалось сохранить ${file.name}")
    }

    private fun setState(model: ModelInfo, state: ModelState) {
        _states.update { it + (model.id to state) }
    }

    private companion object {
        const val FREE_SPACE_RESERVE = 100L * 1024 * 1024
        const val COPY_BUFFER = 1 shl 20
    }
}
