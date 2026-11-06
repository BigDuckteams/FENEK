package app.fenek.model

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class DownloadException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Resumable HTTP download of a single file with mirror fallback. Call from an IO dispatcher. */
internal object Downloader {

    private const val MAX_REDIRECTS = 8
    private const val REPORT_STEP = 256 * 1024L

    /**
     * Downloads [file] to [target] through a ".part" file which survives interruptions and
     * is resumed with an HTTP Range request next time. [onBytes] receives the bytes on disk.
     */
    suspend fun download(file: RemoteFile, target: File, onBytes: (Long) -> Unit) {
        val partial = File(target.parentFile, target.name + ".part")
        var lastError: Exception? = null
        for (url in file.urls) {
            currentCoroutineContext().ensureActive()
            try {
                fetch(url, partial, file.sizeBytes, onBytes)
                if (file.sizeBytes > 0 && partial.length() != file.sizeBytes) {
                    partial.delete()
                    throw DownloadException("неверный размер файла")
                }
                target.delete()
                if (!partial.renameTo(target)) throw DownloadException("не удалось сохранить файл")
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A cancelled download surfaces as an IOException from the closed socket.
                currentCoroutineContext().ensureActive()
                lastError = e
            }
        }
        throw DownloadException(
            "Не удалось скачать ${file.name}: ${lastError?.message ?: "нет соединения"}",
            lastError,
        )
    }

    private suspend fun fetch(startUrl: String, partial: File, expected: Long, onBytes: (Long) -> Unit) {
        var existing = if (partial.exists()) partial.length() else 0L
        if (expected > 0 && existing > expected) {
            partial.delete()
            existing = 0
        }
        if (expected > 0 && existing == expected) return

        var url = URL(startUrl)
        var redirects = 0
        var restarted = false
        while (true) {
            val connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 20_000
            connection.readTimeout = 60_000
            connection.setRequestProperty("User-Agent", "Fenek/1.0 (Android)")
            if (existing > 0) connection.setRequestProperty("Range", "bytes=$existing-")
            // Closing the connection unblocks a pending read when the coroutine is cancelled.
            val cancelHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { connection.disconnect() }
            try {
                val code = connection.responseCode
                when {
                    code in 300..399 -> {
                        val location = connection.getHeaderField("Location")
                            ?: throw DownloadException("перенаправление без адреса")
                        if (++redirects > MAX_REDIRECTS) throw DownloadException("слишком много перенаправлений")
                        url = URL(url, location)
                    }
                    code == 416 && !restarted -> {
                        // The partial file does not match the server copy: start from scratch.
                        partial.delete()
                        existing = 0
                        restarted = true
                    }
                    code == 200 || code == 206 -> {
                        val append = code == 206 && existing > 0
                        var written = if (append) existing else 0L
                        connection.inputStream.use { input ->
                            FileOutputStream(partial, append).use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var reported = written
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    output.write(buffer, 0, n)
                                    written += n
                                    if (written - reported >= REPORT_STEP) {
                                        reported = written
                                        onBytes(written)
                                    }
                                }
                            }
                        }
                        onBytes(written)
                        return
                    }
                    else -> throw DownloadException("HTTP $code")
                }
            } finally {
                cancelHandle?.dispose()
                connection.disconnect()
            }
        }
    }
}
