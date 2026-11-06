package app.fenek.ui.transcript

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import app.fenek.data.Transcript
import app.fenek.export.ExportFormat
import app.fenek.export.Exporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object Sharing {

    private val unsafeFileChars = Regex("[\\\\/:*?\"<>|\\s]+")

    fun share(context: Context, transcript: Transcript, format: ExportFormat) {
        val file = exportFile(context, transcript.title, format.extension)
        file.writeText(Exporter.render(transcript, format))
        send(context, file, format.mimeType, transcript.title, "Отправить расшифровку")
    }

    /** Shares a recording under a readable name instead of the internal one. */
    suspend fun shareAudio(context: Context, audio: File, title: String) {
        val copy = withContext(Dispatchers.IO) {
            exportFile(context, title, audio.extension.ifBlank { "aac" }).also { audio.copyTo(it, overwrite = true) }
        }
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(copy.extension.lowercase()) ?: "audio/*"
        send(context, copy, mime, title, "Отправить запись")
    }

    fun copyText(context: Context, transcript: Transcript) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(transcript.title, Exporter.plainText(transcript)))
        // Android 13+ shows its own confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, "Текст скопирован", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportFile(context: Context, title: String, extension: String): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val baseName = title.replace(unsafeFileChars, "_").trim('_').take(80).ifBlank { "fenek" }
        return File(dir, "$baseName.$extension")
    }

    private fun send(context: Context, file: File, mimeType: String, title: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            clipData = ClipData.newRawUri(title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, chooserTitle))
    }
}
