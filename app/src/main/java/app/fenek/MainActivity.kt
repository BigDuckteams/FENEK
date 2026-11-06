package app.fenek

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import app.fenek.record.RecordingNames
import app.fenek.ui.FenekNavHost
import app.fenek.ui.theme.FenekTheme
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/** An audio file to transcribe: shared from another app, opened with the app, or just recorded. */
data class IncomingAudio(
    val uri: Uri,
    val displayName: String? = null,
    /** A recording made by the app itself: it belongs to the transcript and is not copied. */
    val owned: Boolean = false,
)

/** Something the outside world (notifications, shortcuts, other apps) asks the UI to do. */
sealed interface AppRequest {
    data class Transcribe(val audio: IncomingAudio) : AppRequest
    data class OpenTranscript(val id: String) : AppRequest
    /** Opens the recorder; [autoStart] also starts recording (tile, launcher shortcut). */
    data class Record(val autoStart: Boolean = false) : AppRequest
    data object PickFile : AppRequest
}

class MainActivity : ComponentActivity() {

    private val requests = MutableStateFlow<AppRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            FenekTheme {
                FenekNavHost(requests = requests)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val request = when (intent.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                ?.let { AppRequest.Transcribe(IncomingAudio(it)) }
            Intent.ACTION_VIEW -> intent.data?.let { AppRequest.Transcribe(IncomingAudio(it)) }
            ACTION_RECORD -> AppRequest.Record(autoStart = true)
            ACTION_OPEN_FILE -> AppRequest.PickFile
            else -> null
        }
            ?: intent.getStringExtra(EXTRA_TRANSCRIPT_ID)?.let { AppRequest.OpenTranscript(it) }
            ?: intent.getStringExtra(EXTRA_OPEN_RECORDER)?.let { AppRequest.Record() }
            ?: intent.getStringExtra(EXTRA_RECORDING_PATH)?.let(::ownRecording)
        if (request != null) requests.value = request
    }

    /** Accepts only files from the app's own recordings folder. */
    private fun ownRecording(path: String): AppRequest? {
        val file = File(path).canonicalFile
        val folder = File(filesDir, "recordings").canonicalFile
        if (file.parentFile != folder || !file.isFile) return null
        val createdAt = RecordingNames.createdAt(file) ?: return null
        return AppRequest.Transcribe(IncomingAudio(Uri.fromFile(file), RecordingNames.displayName(createdAt), owned = true))
    }

    companion object {
        const val EXTRA_TRANSCRIPT_ID = "app.fenek.extra.TRANSCRIPT_ID"
        const val EXTRA_OPEN_RECORDER = "app.fenek.extra.OPEN_RECORDER"
        const val EXTRA_RECORDING_PATH = "app.fenek.extra.RECORDING_PATH"
        const val ACTION_RECORD = "app.fenek.action.RECORD"
        const val ACTION_OPEN_FILE = "app.fenek.action.OPEN_FILE"
    }
}
