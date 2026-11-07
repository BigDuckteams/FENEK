package app.fenek.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class DownloaderReasonTest {

    @Test
    fun aMissingNetworkIsExplainedInRussian() {
        assertEquals("нет соединения с интернетом", Downloader.reason(UnknownHostException("Unable to resolve host \"hf-mirror.com\"")))
        assertEquals("нет соединения с интернетом", Downloader.reason(ConnectException("failed to connect")))
        assertEquals("нет соединения с интернетом", Downloader.reason(SocketTimeoutException("timeout")))
        assertEquals("нет соединения с интернетом", Downloader.reason(null))
    }

    @Test
    fun aFileOfAnotherSizeOnTheServerIsRefusedBeforeItIsDownloaded() {
        // 200: the whole file; 206: the rest after what is on disk.
        assertEquals(null, Downloader.sizeProblem(200, 0, 1_000, 1_000))
        assertEquals(null, Downloader.sizeProblem(206, 400, 600, 1_000))
        assertEquals(null, Downloader.sizeProblem(200, 0, -1, 1_000))
        assertEquals(null, Downloader.sizeProblem(200, 0, 5, 0))
        val message = Downloader.sizeProblem(200, 0, 652_282_294, 652_184_281)!!
        assertEquals(true, message.contains("652282294") && message.contains("652184281"))
        assertEquals(true, Downloader.sizeProblem(206, 400, 700, 1_000) != null)
    }

    @Test
    fun otherErrorsKeepTheirOwnText() {
        assertEquals("HTTP 404", Downloader.reason(DownloadException("HTTP 404")))
        assertEquals("неверный размер файла", Downloader.reason(DownloadException("неверный размер файла")))
    }
}
