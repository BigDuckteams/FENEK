package app.fenek.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeFormat {

    private val russian: Locale = Locale.forLanguageTag("ru")

    /** 75_000 → "01:15", 3_725_000 → "1:02:05". */
    fun clock(ms: Long, forceHours: Boolean = false): String {
        val totalSeconds = ms.coerceAtLeast(0) / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0 || forceHours) {
            String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.ROOT, "%02d:%02d", m, s)
        }
    }

    /** Timestamp used in text exports: always with hours, "00:01:15". */
    fun stamp(ms: Long): String {
        val totalSeconds = ms.coerceAtLeast(0) / 1000
        return String.format(
            Locale.ROOT, "%02d:%02d:%02d",
            totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60,
        )
    }

    /** "00:01:15,250" (SubRip) or "00:01:15.250" (WebVTT). */
    fun subtitle(ms: Long, separator: Char): String {
        val v = ms.coerceAtLeast(0)
        return String.format(
            Locale.ROOT, "%02d:%02d:%02d%c%03d",
            v / 3_600_000, (v % 3_600_000) / 60_000, (v % 60_000) / 1000, separator, v % 1000,
        )
    }

    fun date(epochMs: Long): String = SimpleDateFormat("d MMMM yyyy, HH:mm", russian).format(Date(epochMs))

    fun shortDate(epochMs: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm", russian).format(Date(epochMs))

    /** "2 мин 05 с", "1 ч 02 мин". */
    fun duration(ms: Long): String {
        val totalSeconds = (ms.coerceAtLeast(0) + 500) / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return when {
            h > 0 -> String.format(Locale.ROOT, "%d ч %02d мин", h, m)
            m > 0 -> String.format(Locale.ROOT, "%d мин %02d с", m, s)
            else -> "$s с"
        }
    }
}
