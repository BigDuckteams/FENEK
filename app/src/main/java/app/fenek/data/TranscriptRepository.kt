package app.fenek.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import app.fenek.record.RecordingMarks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Transcripts stored as one JSON file each in the app's private storage. */
class TranscriptRepository(
    private val context: Context,
    scope: CoroutineScope,
    private val pending: PendingTranscripts,
) {

    private val dir = File(context.filesDir, "transcripts")
    private val json = TranscriptJson
    private val mutex = Mutex()

    /** All transcripts, newest first; null until loaded from disk. */
    private val _items = MutableStateFlow<List<Transcript>?>(null)
    val items: StateFlow<List<Transcript>?> = _items.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                dir.mkdirs()
                val loaded = dir.listFiles { file -> file.extension == "json" }.orEmpty().mapNotNull { file ->
                    runCatching { json.decodeFromString(Transcript.serializer(), file.readText()) }.getOrNull()
                }
                // A job that died inside the language model left its recognized transcript behind.
                val recovered = pending.recover(loaded)
                recovered.forEach { runCatching { write(it) } }
                val all = loaded.filterNot { old -> recovered.any { it.id == old.id } } + recovered
                // One transcript per id: two files with the same id (a copied file) would crash the list.
                _items.value = all.sortedByDescending { it.createdAt }.distinctBy { it.id }
            }
        }
    }

    fun get(id: String): Transcript? = _items.value?.firstOrNull { it.id == id }

    suspend fun save(transcript: Transcript) = withContext(Dispatchers.IO) {
        mutex.withLock {
            write(transcript)
            _items.update { list ->
                (list.orEmpty().filterNot { it.id == transcript.id } + transcript).sortedByDescending { it.createdAt }
            }
        }
    }

    suspend fun update(id: String, transform: (Transcript) -> Transcript) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = _items.value?.firstOrNull { it.id == id } ?: return@withLock
            val updated = transform(current)
            if (updated == current) return@withLock
            write(updated)
            _items.update { list -> list?.map { if (it.id == id) updated else it } }
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val transcript = _items.value?.firstOrNull { it.id == id }
            File(dir, "$id.json").delete()
            val audio = transcript?.audio
            if (transcript != null && audio != null) {
                val usedElsewhere = _items.value.orEmpty().any { it.id != id && it.audio == audio }
                if (transcript.audioOwned) {
                    File(audio).delete()
                    RecordingMarks.delete(File(audio))
                } else if (!usedElsewhere) {
                    runCatching {
                        context.contentResolver.releasePersistableUriPermission(
                            Uri.parse(audio), Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                }
            }
            _items.update { list -> list?.filterNot { it.id == id } }
        }
    }

    private fun write(transcript: Transcript) {
        dir.mkdirs()
        val target = File(dir, "${transcript.id}.json")
        val temp = File(dir, "${transcript.id}.json.tmp")
        temp.writeText(json.encodeToString(Transcript.serializer(), transcript))
        if (!temp.renameTo(target)) {
            target.delete()
            check(temp.renameTo(target)) { "Cannot write $target" }
        }
    }
}
