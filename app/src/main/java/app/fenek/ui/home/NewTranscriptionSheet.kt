package app.fenek.ui.home

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.fenek.data.AppSettings
import app.fenek.model.DownloadPlan
import app.fenek.model.Languages
import app.fenek.model.ModelCatalog
import app.fenek.model.ModelInfo
import app.fenek.model.ModelState
import app.fenek.ui.components.Choice
import app.fenek.ui.components.ChoiceField
import app.fenek.ui.components.SectionTitle
import app.fenek.ui.components.formatBytes

/** A recording chosen by the user and waiting for the transcription options. */
data class PendingAudio(
    val uri: Uri,
    val name: String,
    /** Only temporarily readable: copy it into app storage first. */
    val copy: Boolean,
    /** Recorded by the app: deleted together with its transcript. */
    val owned: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewTranscriptionSheet(
    audio: PendingAudio,
    settings: AppSettings,
    modelStates: Map<String, ModelState>,
    onDownload: (List<ModelInfo>) -> Unit,
    onDismiss: () -> Unit,
    onStart: (numSpeakers: Int?, asrModelId: String, language: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var numSpeakers by rememberSaveable { mutableStateOf<Int?>(null) }
    var asrModelId by rememberSaveable { mutableStateOf(settings.asrModelId) }
    var language by rememberSaveable { mutableStateOf(settings.language) }
    val model = ModelCatalog.asr(asrModelId)
    val plan = DownloadPlan.of(settings, asrModelId) { modelStates[it.id] is ModelState.Ready }
    // Only the required models block a job; the language model is offered next to them.
    val missing = plan.all

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("Новая расшифровка", style = MaterialTheme.typography.headlineSmall)
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AudioFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(audio.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            SectionTitle("Сколько человек в разговоре?")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = numSpeakers == null,
                    onClick = { numSpeakers = null },
                    label = { Text("Определить") },
                )
                for (n in 1..6) {
                    FilterChip(
                        selected = numSpeakers == n,
                        onClick = { numSpeakers = n },
                        label = { Text("$n") },
                    )
                }
            }
            Text(
                "Если число участников известно, укажите его — так разделение по голосам надёжнее.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle("Модель распознавания")
            ChoiceField(
                choices = ModelCatalog.asrModels.map { m ->
                    val status = if (modelStates[m.id] is ModelState.Ready) "скачана" else "не скачана"
                    Choice(m.id, m.title, "${m.languages} · $status")
                },
                selectedKey = asrModelId,
                onSelect = { asrModelId = it },
            )

            if (model.asr?.selectableLanguage == true) {
                SectionTitle("Язык речи")
                ChoiceField(
                    choices = Languages.whisper.map { (code, title) -> Choice(code, title) },
                    selectedKey = language,
                    onSelect = { language = it },
                )
            }

            if (missing.isNotEmpty()) {
                MissingModels(plan, modelStates, onDownload = { onDownload(plan.all) })
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Отмена") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onStart(numSpeakers, asrModelId, language) },
                    enabled = plan.requiredMissing.isEmpty(),
                ) { Text("Расшифровать") }
            }
        }
    }
}

@Composable
private fun MissingModels(
    plan: DownloadPlan,
    states: Map<String, ModelState>,
    onDownload: () -> Unit,
) {
    val missing = plan.all
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            val downloading = missing.mapNotNull { states[it.id] as? ModelState.Downloading }
            Text(
                (if (plan.requiredMissing.isEmpty()) "Рекомендуем скачать: " else "Нужно скачать: ") +
                    missing.joinToString { it.title },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (plan.requiredMissing.isNotEmpty() && plan.llmMissing != null) {
                Text(
                    "Языковая модель нужна для правки текста и выжимки; без неё расшифровка всё равно работает.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (downloading.isNotEmpty()) {
                val bytes = downloading.sumOf { it.bytes }
                val total = downloading.sumOf { it.total }.coerceAtLeast(1)
                LinearProgressIndicator(
                    progress = { bytes.toFloat() / total },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
                Text(
                    "${formatBytes(bytes)} из ${formatBytes(total)}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                OutlinedButton(onClick = onDownload, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Скачать (${formatBytes(plan.totalBytes)})")
                }
            }
            states.filterKeys { id -> missing.any { it.id == id } }.values
                .filterIsInstance<ModelState.Failed>()
                .firstOrNull()
                ?.let {
                    Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
        }
    }
}
