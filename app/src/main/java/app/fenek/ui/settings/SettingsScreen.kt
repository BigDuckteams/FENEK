package app.fenek.ui.settings

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fenek.R
import app.fenek.appContainer
import app.fenek.data.AppSettings
import app.fenek.data.DiarizationQuality
import app.fenek.model.Languages
import app.fenek.model.ModelCatalog
import app.fenek.model.ModelState
import app.fenek.service.RecordTileService
import app.fenek.ui.brand.FennecMark
import app.fenek.ui.components.Choice
import app.fenek.ui.components.ChoiceField
import app.fenek.ui.components.SectionTitle
import app.fenek.ui.components.formatBytes
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenModels: () -> Unit) {
    val context = LocalContext.current
    val container = remember { context.appContainer }
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val version = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            SectionTitle("Распознавание речи")
            ListItem(
                headlineContent = { Text("Модель по умолчанию") },
                supportingContent = { Text(ModelCatalog.asr(settings.asrModelId).title) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(onClick = onOpenModels),
            )
            Text(
                "Язык речи для многоязычных моделей (Whisper)",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )
            ChoiceField(
                choices = Languages.whisper.map { (code, title) -> Choice(code, title) },
                selectedKey = settings.language,
                onSelect = { code -> container.settings.update { it.copy(language = code) } },
            )
            SwitchItem(
                title = "Очищать шумную запись",
                checked = settings.enhanceAudio,
                onChange = { on -> container.settings.update { it.copy(enhanceAudio = on) } },
            )
            Hint(
                "Убирает постоянный гул, приглушает шум в паузах и усиливает тихую запись. " +
                    "По умолчанию выключено: для чистой записи исходный звук обычно подходит лучше.",
            )

            SectionTitle("Языковая модель")
            val llm = ModelCatalog.llm(settings.llmModelId)
            val modelStates by container.models.states.collectAsStateWithLifecycle()
            val llmReady = modelStates[llm.id] is ModelState.Ready
            ListItem(
                headlineContent = { Text("Модель") },
                supportingContent = { Text(llm.title + " · " + if (llmReady) "скачана" else "не скачана") },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(onClick = onOpenModels),
            )
            SwitchItem(
                title = "Исправлять текст",
                checked = settings.correctText,
                onChange = { on -> container.settings.update { it.copy(correctText = on) } },
            )
            SwitchItem(
                title = "Выжимка и задачи",
                checked = settings.llmHighlights,
                onChange = { on -> container.settings.update { it.copy(llmHighlights = on) } },
            )
            if (!llmReady) {
                Hint("Модель не скачана: правка текста и выжимка языковой моделью не работают, выжимка строится по правилам.")
                OutlinedButton(onClick = { container.download(listOf(llm)) }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Скачать (${formatBytes(llm.totalBytes)})")
                }
            }
            Hint(
                "Правка улучшает пунктуацию и явные ошибки, но замедляет обработку; у моделей, которые сами " +
                    "ставят знаки (GigaAM), точность она не меняет. Когда правка выключена, текст остаётся " +
                    "ровно таким, как его распознала модель речи.",
            )

            SectionTitle("Определение говорящих")
            Text("Точность разметки", style = MaterialTheme.typography.bodyMedium)
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                val options = DiarizationQuality.entries
                options.forEachIndexed { index, quality ->
                    SegmentedButton(
                        selected = settings.diarizationQuality == quality,
                        onClick = { container.settings.update { it.copy(diarizationQuality = quality) } },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    ) {
                        Text(
                            when (quality) {
                                DiarizationQuality.FAST -> "Быстро"
                                DiarizationQuality.BALANCED -> "Баланс"
                                DiarizationQuality.PRECISE -> "Точно"
                            },
                        )
                    }
                }
            }
            Hint(
                "Насколько подробно анализируется запись при поиске смены говорящих. " +
                    "«Быстро» примерно вдвое быстрее «Баланса» — удобно для записей длиннее часа; " +
                    "«Точно» в 2,5 раза медленнее и лучше ловит короткие реплики.",
            )

            var separation by remember(settings.speakerSeparation) { mutableFloatStateOf(settings.speakerSeparation) }
            Text("Разделение голосов", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 16.dp))
            Slider(
                value = separation,
                onValueChange = { separation = it },
                onValueChangeFinished = { container.settings.update { it.copy(speakerSeparation = separation) } },
                valueRange = 0f..1f,
            )
            Row {
                Text("Объединять похожие", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                Text("Разделять сильнее", style = MaterialTheme.typography.labelSmall)
            }
            Hint(
                "Если одного человека приложение делит на нескольких участников — сдвиньте влево; " +
                    "если разных людей объединяет — вправо. Надёжнее всего указать число участников при запуске.",
            )

            SectionTitle("Производительность")
            val maxThreads = AppSettings.maxThreads()
            var threads by remember(settings.numThreads) { mutableFloatStateOf(settings.numThreads.toFloat()) }
            Text("Потоков процессора: ${threads.roundToInt()}", style = MaterialTheme.typography.bodyMedium)
            if (maxThreads > 1) {
                Slider(
                    value = threads,
                    onValueChange = { threads = it },
                    onValueChangeFinished = { container.settings.update { it.copy(numThreads = threads.roundToInt()) } },
                    valueRange = 1f..maxThreads.toFloat(),
                    steps = (maxThreads - 2).coerceAtLeast(0),
                )
            }
            Hint("Больше потоков — быстрее, но сильнее нагревается телефон и расходуется батарея.")

            SectionTitle("Быстрый запуск записи")
            Text(
                "Плитка «Запись FENEK» в шторке уведомлений и ярлык «Записать» на значке приложения " +
                    "открывают запись и сразу её начинают.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                OutlinedButton(onClick = { addTile(context) }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Добавить плитку в шторку")
                }
            } else {
                Hint("Откройте шторку, нажмите на карандаш и перетащите плитку «Запись FENEK» к остальным.")
            }

            SectionTitle("О приложении")
            Row(verticalAlignment = Alignment.CenterVertically) {
                FennecMark(Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Text("FENEK $version", style = MaterialTheme.typography.titleMedium)
            }
            Hint(
                "Работает полностью офлайн: записи и расшифровки не покидают устройство. " +
                    "Интернет нужен только для однократной загрузки моделей.\n\n" +
                    "Используются: sherpa-onnx и ONNX Runtime; GigaAM (Сбер), Whisper (OpenAI), " +
                    "Parakeet (NVIDIA), Zipformer; pyannote segmentation 3.0; 3D-Speaker ERes2Net.",
            )
        }
    }
}

/** Asks the system to put the recording tile into Quick Settings. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun addTile(context: Context) {
    val statusBar = context.getSystemService(StatusBarManager::class.java) ?: return
    statusBar.requestAddTileService(
        ComponentName(context, RecordTileService::class.java),
        context.getString(R.string.tile_record),
        android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_notification_mic),
        context.mainExecutor,
    ) { result ->
        val message = when (result) {
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "Плитка добавлена"
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "Плитка уже в шторке"
            else -> null
        }
        if (message != null) Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun SwitchItem(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}
