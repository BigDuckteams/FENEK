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
    fun otherErrorsKeepTheirOwnText() {
        assertEquals("HTTP 404", Downloader.reason(DownloadException("HTTP 404")))
        assertEquals("неверный размер файла", Downloader.reason(DownloadException("неверный размер файла")))
    }
}
