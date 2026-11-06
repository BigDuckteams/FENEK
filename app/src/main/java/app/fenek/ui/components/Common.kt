package app.fenek.ui.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fenek.data.TranscriptSearch
import app.fenek.data.defaultSpeakerName
import app.fenek.ui.theme.SpeakerColors

@Composable
fun SpeakerAvatar(speaker: Int, name: String, size: Dp = 32.dp) {
    val color = SpeakerColors.of(speaker)
    // "Спикер 2" → "2" (every default name starts with the same letter), "Анна" → "А".
    val label = if (name == defaultSpeakerName(speaker)) "${speaker + 1}" else name.trim().firstOrNull()?.uppercase() ?: "?"
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.45f).sp,
        )
    }
}

@Composable
fun Badge(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
fun formatBytes(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)

/** [text] with every occurrence of [query] marked by a [background] colour. */
fun highlight(text: String, query: String, background: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    for (range in TranscriptSearch.occurrences(text, query.trim())) {
        addStyle(SpanStyle(background = background), range.first, range.last + 1)
    }
}

/** File name of a content:// or file:// URI as the user sees it. */
fun Context.displayName(uri: Uri): String {
    val fromProvider = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
    return fromProvider?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: "Запись"
}
