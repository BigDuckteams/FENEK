package app.fenek.ui.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fenek.appContainer
import app.fenek.model.ModelCatalog
import app.fenek.model.ModelInfo
import app.fenek.model.ModelKind
import app.fenek.model.ModelState
import app.fenek.ui.components.Badge
import app.fenek.ui.components.SectionTitle
import app.fenek.ui.components.formatBytes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember { context.appContainer }
    val states by container.models.states.collectAsStateWithLifecycle()
    val settings by container.settings.state.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<ModelInfo?>(null) }

    @Composable
    fun ModelItem(model: ModelInfo) {
        val selectable = model.kind != ModelKind.SEGMENTATION
        val selected = when (model.kind) {
            ModelKind.ASR -> settings.asrModelId == model.id
            ModelKind.EMBEDDING -> settings.embeddingModelId == model.id
            ModelKind.LLM -> settings.llmModelId == model.id
            ModelKind.SEGMENTATION -> true
        }
        ModelCard(
            model = model,
            state = states[model.id] ?: ModelState.Missing,
            selectable = selectable,
            selected = selected,
            onSelect = {
                container.settings.update {
                    when (model.kind) {
                        ModelKind.ASR -> it.copy(asrModelId = model.id)
                        ModelKind.EMBEDDING -> it.copy(embeddingModelId = model.id)
                        ModelKind.LLM -> it.copy(llmModelId = model.id)
                        ModelKind.SEGMENTATION -> it
                    }
                }
            },
            onDownload = { container.download(listOf(model)) },
            onCancel = { container.models.cancel(model) },
            onDelete = { toDelete = model },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Модели") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Все вычисления выполняются на телефоне, без интернета. Модели скачиваются " +
                        "один раз и хранятся во внутренней памяти приложения. Выбранная модель " +
                        "используется для новых расшифровок.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { SectionTitle("Распознавание речи") }
            items(ModelCatalog.asrModels, key = { it.id }) { ModelItem(it) }
            item { SectionTitle("Определение говорящих") }
            item(key = ModelCatalog.segmentation.id) { ModelItem(ModelCatalog.segmentation) }
            items(ModelCatalog.embeddingModels, key = { it.id }) { ModelItem(it) }
            item { SectionTitle("Языковая модель") }
            items(ModelCatalog.llmModels, key = { it.id }) { ModelItem(it) }
            item {
                val used = ModelCatalog.all.filter { states[it.id] is ModelState.Ready }.sumOf { it.totalBytes }
                Text(
                    "Занято моделями: ${formatBytes(used)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

    toDelete?.let { model ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить «${model.title}»?") },
            text = { Text("Освободится ${formatBytes(model.totalBytes)}. Модель можно будет скачать снова.") },
            confirmButton = {
                TextButton(onClick = {
                    container.models.delete(model)
                    toDelete = null
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelCard(
    model: ModelInfo,
    state: ModelState,
    selectable: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    OutlinedCard(onClick = onSelect, enabled = selectable, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectable) {
                    RadioButton(selected = selected, onClick = onSelect)
                } else {
                    Spacer(Modifier.width(8.dp))
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(model.title, style = MaterialTheme.typography.titleMedium)
                        if (model.recommended) {
                            Spacer(Modifier.width(8.dp))
                            Badge("рекомендуется")
                        }
                    }
                    val punctuation = when (model.asr?.punctuation) {
                        true -> " · с пунктуацией"
                        false -> " · без пунктуации"
                        null -> ""
                    }
                    Text(
                        "${model.languages} · ${formatBytes(model.totalBytes)}$punctuation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(Modifier.padding(start = 8.dp)) {
                Text(model.summary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                when (state) {
                    is ModelState.Ready -> Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Скачана", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = onDelete) { Text("Удалить") }
                    }
                    is ModelState.Downloading -> Column(Modifier.padding(top = 8.dp)) {
                        LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${formatBytes(state.bytes)} из ${formatBytes(state.total)}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onCancel) { Text("Отмена") }
                        }
                    }
                    is ModelState.Failed -> Column(Modifier.padding(top = 8.dp)) {
                        Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = onDownload, modifier = Modifier.padding(top = 4.dp)) { Text("Повторить") }
                    }
                    ModelState.Missing -> OutlinedButton(onClick = onDownload, modifier = Modifier.padding(top = 8.dp)) {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Скачать")
                    }
                }
                Text(
                    "Лицензия: ${model.license}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
