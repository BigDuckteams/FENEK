package app.fenek.ui.transcript

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.fenek.data.Segment
import app.fenek.data.Transcript
import app.fenek.data.defaultSpeakerName
import app.fenek.util.TimeFormat

@Composable
fun RenameDialog(title: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf(title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Название") },
        text = {
            OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecountDialog(onDismiss: () -> Unit, onConfirm: (numSpeakers: Int?) -> Unit) {
    var count by rememberSaveable { mutableStateOf<Int?>(2) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Пересчитать участников") },
        text = {
            Column {
                Text(
                    "Запись будет обработана заново с указанным числом участников. " +
                        "Ручные правки в этой расшифровке пропадут.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    FilterChip(selected = count == null, onClick = { count = null }, label = { Text("Определить") })
                    for (n in 1..6) {
                        FilterChip(selected = count == n, onClick = { count = n }, label = { Text("$n") })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(count) }) { Text("Пересчитать") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun DeleteDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить расшифровку?") },
        text = { Text("Текст расшифровки будет удалён. Исходная запись в памяти телефона останется, если она не была скопирована в приложение.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeakerDialog(
    transcript: Transcript,
    speaker: Int,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onMerge: (into: Int) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(transcript.speakerName(speaker)) }
    val others = transcript.activeSpeakers().filter { it.id != speaker }
    val phrases = transcript.segments.count { it.speaker == speaker }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Участник") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Имя") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Фраз в разговоре: $phrases",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (others.isNotEmpty()) {
                    Text(
                        "Если это тот же человек, что и другой участник, объедините их:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        others.forEach { other ->
                            OutlinedButton(onClick = { onMerge(other.id) }) { Text("→ ${other.name}") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onRename(name) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SegmentDialog(
    transcript: Transcript,
    segment: Segment,
    onDismiss: () -> Unit,
    onSave: (text: String, speaker: Int) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(segment.text) }
    var speaker by rememberSaveable { mutableIntStateOf(segment.speaker) }
    val newSpeaker = transcript.nextSpeakerId()
    val choices = transcript.activeSpeakers().map { it.id to it.name } + (newSpeaker to "Новый участник")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Фраза · ${TimeFormat.clock(segment.startMs)}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                )
                // The language model changed this phrase: the recognized text can be brought back.
                segment.rawText?.let { recognized ->
                    Text(
                        "Распознано: $recognized",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    TextButton(onClick = { text = recognized }) { Text("Вернуть распознанный текст") }
                }
                Text(
                    "Кто говорит",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    choices.forEach { (id, title) ->
                        FilterChip(
                            selected = speaker == id,
                            onClick = { speaker = id },
                            label = { Text(if (id == newSpeaker) "$title (${defaultSpeakerName(id)})" else title) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onSave("", speaker) }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = { onSave(text, speaker) }) { Text("Сохранить") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
