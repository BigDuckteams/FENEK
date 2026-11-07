package app.fenek.ui.transcript

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.fenek.appContainer
import app.fenek.data.Speaker
import app.fenek.data.Transcript
import app.fenek.data.defaultSpeakerName
import app.fenek.data.withSegmentUpdated
import app.fenek.engine.JobRequest
import app.fenek.model.Languages
import app.fenek.service.Enqueued
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class TranscriptViewModel(app: Application, private val id: String) : AndroidViewModel(app) {

    private val repository = app.appContainer.transcripts
    private val jobs = app.appContainer.jobs

    val transcript: StateFlow<Transcript?> = repository.items
        .map { list -> list?.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.get(id))

    val player = AudioPlayer(app)
    private var loadedAudio: String? = null

    init {
        viewModelScope.launch {
            transcript.collect { t ->
                val audio = t?.audio
                if (audio != null && audio != loadedAudio) {
                    loadedAudio = audio
                    player.load(audio)
                }
            }
        }
    }

    fun rename(title: String) = edit { t -> t.copy(title = title.trim().ifBlank { t.title }) }

    fun toggleFavorite() = edit { t -> t.copy(favorite = !t.favorite) }

    fun renameSpeaker(speaker: Int, name: String) = edit { t ->
        val clean = name.trim().ifBlank { defaultSpeakerName(speaker) }
        val speakers = if (t.speakers.any { it.id == speaker }) {
            t.speakers.map { if (it.id == speaker) it.copy(name = clean) else it }
        } else {
            t.speakers + Speaker(speaker, clean)
        }
        t.copy(speakers = speakers)
    }

    /** Diarization split one person into two: give all phrases of [from] to [into]. */
    fun mergeSpeaker(from: Int, into: Int) = edit { t ->
        t.copy(
            segments = t.segments.map { if (it.speaker == from) it.copy(speaker = into) else it },
            speakers = t.speakers.filterNot { it.id == from },
        )
    }

    /** Fixes the text or the speaker of a phrase; an empty text deletes the phrase. */
    fun updateSegment(index: Int, text: String, speaker: Int) = edit { t -> t.withSegmentUpdated(index, text, speaker) }

    /**
     * Processes the same recording again with a given number of participants and replaces this
     * transcript. Returns an error for the user, or null when processing has started or was put
     * in the queue behind the running job.
     */
    fun recount(numSpeakers: Int?): String? {
        val t = transcript.value ?: return "Расшифровка не найдена"
        val audio = t.audio ?: return "Исходная запись недоступна"
        val isFile = audio.startsWith("/")
        if (isFile && !File(audio).isFile) return "Исходная запись удалена"
        player.pause()
        val result = jobs.enqueue(
            JobRequest(
                source = if (isFile) Uri.fromFile(File(audio)) else Uri.parse(audio),
                displayName = t.sourceName,
                copySource = false,
                ownsSource = t.audioOwned,
                numSpeakers = numSpeakers,
                asrModelId = t.asrModelId,
                language = t.language ?: Languages.AUTO,
                id = t.id,
                title = t.title,
            ),
        )
        return if (result == Enqueued.DUPLICATE) "Эта расшифровка уже в очереди" else null
    }

    fun delete(onDeleted: () -> Unit) {
        player.pause()
        viewModelScope.launch {
            repository.delete(id)
            onDeleted()
        }
    }

    override fun onCleared() {
        player.release()
    }

    private fun edit(transform: (Transcript) -> Transcript) {
        viewModelScope.launch { repository.update(id, transform) }
    }
}

/** Id for a participant the diarization missed. */
fun Transcript.nextSpeakerId(): Int =
    (speakers.map { it.id } + segments.map { it.speaker }).maxOrNull()?.plus(1) ?: 0
