package app.fenek.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fenek.AppRequest
import app.fenek.appContainer
import app.fenek.data.Transcript
import app.fenek.data.TranscriptSearch
import app.fenek.engine.JobRequest
import app.fenek.model.DownloadPlan
import app.fenek.model.ModelInfo
import app.fenek.model.ModelState
import app.fenek.record.RecorderState
import app.fenek.record.Recording
import app.fenek.service.JobState
import app.fenek.service.statusText
import app.fenek.ui.brand.FennecMark
import app.fenek.ui.components.SectionTitle
import app.fenek.ui.components.SpeakerAvatar
import app.fenek.ui.components.displayName
import app.fenek.ui.components.formatBytes
import app.fenek.ui.components.highlight
import app.fenek.ui.transcript.Sharing
import app.fenek.util.TimeFormat
import app.fenek.util.pluralRu
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private val AUDIO_MIME_TYPES = arrayOf("audio/*", "video/*", "application/ogg")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    requests: MutableStateFlow<AppRequest?>,
    onOpenTranscript: (String) -> Unit,
    onOpenRecorder: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember { context.appContainer }
    val transcripts by container.transcripts.items.collectAsStateWithLifecycle()
    val jobState by container.jobs.state.collectAsStateWithLifecycle()
    val modelStates by container.models.states.collectAsStateWithLifecycle()
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val recorderState by container.recorder.state.collectAsStateWithLifecycle()
    val recordings by container.recorder.recordings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<PendingAudio?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var recordingToDelete by remember { mutableStateOf<Recording?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // Keep access to the original file for playback; otherwise it is copied.
            val persisted = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.isSuccess
            pending = PendingAudio(uri, context.displayName(uri), copy = !persisted)
        }
    }

    // The previous run died inside the language model: it was switched off, say so once. The effect
    // must not depend on the flag it resets, or the snackbar would be cancelled as soon as it appears.
    LaunchedEffect(Unit) {
        container.llmCrashNotice.collect { crashed ->
            if (crashed) {
                container.llmCrashNotice.value = false
                snackbar.showSnackbar(
                    "Языковая модель отключена: прошлый запуск завершился сбоем в ней. Включить её снова можно в настройках.",
                    duration = SnackbarDuration.Long,
                )
            }
        }
    }

    val request by requests.collectAsStateWithLifecycle()
    LaunchedEffect(request) {
        when (val r = request) {
            is AppRequest.Transcribe -> {
                requests.value = null
                val audio = r.audio
                pending = PendingAudio(
                    uri = audio.uri,
                    name = audio.displayName ?: context.displayName(audio.uri),
                    copy = !audio.owned,
                    owned = audio.owned,
                )
            }
            AppRequest.PickFile -> {
                requests.value = null
                picker.launch(AUDIO_MIME_TYPES)
            }
            else -> Unit
        }
    }

    // Cards inserted above the first visible item would otherwise appear off-screen.
    val listState = rememberLazyListState()
    val jobVisible = jobState is JobState.Running || jobState is JobState.Failed
    val recordingNow = recorderState is RecorderState.Active
    LaunchedEffect(jobVisible, recordingNow) {
        if (jobVisible || recordingNow) listState.animateScrollToItem(0)
    }

    val openTranscript by rememberUpdatedState(onOpenTranscript)
    LaunchedEffect(Unit) {
        container.jobs.state.collect { state ->
            if (state is JobState.Done) {
                container.jobs.acknowledge()
                container.recorder.refresh()
                launch { listState.animateScrollToItem(0) }
                launch {
                    val result = snackbar.showSnackbar(
                        message = "Расшифровка «${state.title}» готова",
                        actionLabel = "Открыть",
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) openTranscript(state.transcriptId)
                }
            }
        }
    }

    pending?.let { audio ->
        NewTranscriptionSheet(
            audio = audio,
            settings = settings,
            modelStates = modelStates,
            onDownload = { container.download(it) },
            onDismiss = { pending = null },
            onStart = { numSpeakers, asrModelId, language ->
                pending = null
                container.settings.update { it.copy(asrModelId = asrModelId, language = language) }
                val started = container.jobs.start(
                    JobRequest(
                        source = audio.uri,
                        displayName = audio.name,
                        copySource = audio.copy,
                        ownsSource = audio.owned,
                        numSpeakers = numSpeakers,
                        asrModelId = asrModelId,
                        language = language,
                    ),
                )
                if (!started) {
                    scope.launch { snackbar.showSnackbar("Дождитесь окончания текущей расшифровки") }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { BrandTitle() },
                actions = {
                    IconButton(onClick = {
                        searching = !searching
                        if (!searching) query = ""
                    }) {
                        Icon(
                            if (searching) Icons.Outlined.Close else Icons.Outlined.Search,
                            contentDescription = if (searching) "Закрыть поиск" else "Поиск",
                        )
                    }
                    IconButton(onClick = onOpenModels) { Icon(Icons.Outlined.Memory, contentDescription = "Модели") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Настройки") }
                },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExtendedFloatingActionButton(
                    onClick = { picker.launch(AUDIO_MIME_TYPES) },
                    icon = { Icon(Icons.Outlined.AudioFile, contentDescription = null) },
                    text = { Text("Из файла") },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                ExtendedFloatingActionButton(
                    onClick = onOpenRecorder,
                    icon = { Icon(Icons.Filled.Mic, contentDescription = null) },
                    text = { Text("Записать") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val plan = remember(settings, modelStates) { DownloadPlan.of(settings) { modelStates[it.id] is ModelState.Ready } }
        val runningSource = (jobState as? JobState.Running)?.request?.source?.path
        val untranscribed = recordings.filter { recording ->
            val path = recording.file.absolutePath
            path != runningSource && transcripts.orEmpty().none { it.audio == path }
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 160.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (searching) {
                item(key = "search") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Слово, фраза или имя участника") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            (recorderState as? RecorderState.Active)?.let { active ->
                item(key = "recording") { RecordingBanner(active, onOpen = onOpenRecorder) }
            }
            if (!plan.isEmpty) {
                item(key = "setup") {
                    ModelSetupCard(
                        plan = plan,
                        states = modelStates,
                        onDownload = { container.download(plan.all) },
                        onChooseModels = onOpenModels,
                    )
                }
            }
            when (val state = jobState) {
                is JobState.Running -> item(key = "job") {
                    JobProgressCard(state, onCancel = { container.jobs.cancel() })
                }
                is JobState.Failed -> item(key = "job") {
                    JobFailedCard(state, onDismiss = { container.jobs.acknowledge() }, onOpenModels = onOpenModels)
                }
                else -> Unit
            }
            if (untranscribed.isNotEmpty() && query.isBlank()) {
                item(key = "recordings-title") { SectionTitle("Записи без расшифровки") }
                items(untranscribed, key = { "rec-" + it.file.name }) { recording ->
                    RecordingCard(
                        recording = recording,
                        onTranscribe = {
                            pending = PendingAudio(Uri.fromFile(recording.file), recording.displayName, copy = false, owned = true)
                        },
                        onShare = {
                            scope.launch { Sharing.shareAudio(context, recording.file, recording.displayName.substringBeforeLast('.')) }
                        },
                        onDelete = { recordingToDelete = recording },
                    )
                }
            }
            val list = transcripts
            when {
                list == null -> item(key = "loading") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }
                query.isNotBlank() -> {
                    val hits = TranscriptSearch.search(list, query)
                    item(key = "results-title") {
                        SectionTitle(
                            if (hits.isEmpty()) "Ничего не найдено" else "Найдено: " + pluralRu(hits.size, "расшифровка", "расшифровки", "расшифровок"),
                        )
                    }
                    items(hits, key = { it.transcript.id }) { hit ->
                        TranscriptCard(hit.transcript, snippet = hit.snippet, query = query, onClick = { onOpenTranscript(hit.transcript.id) })
                    }
                }
                list.isEmpty() && untranscribed.isEmpty() && recorderState !is RecorderState.Active ->
                    item(key = "empty") { EmptyState() }
                list.isNotEmpty() -> {
                    val favorites = list.count { it.favorite }
                    val shown = if (favoritesOnly && favorites > 0) list.filter { it.favorite } else list
                    item(key = "title") { SectionTitle("Расшифровки") }
                    if (favorites > 0) {
                        item(key = "filter") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = !favoritesOnly,
                                    onClick = { favoritesOnly = false },
                                    label = { Text("Все · ${list.size}") },
                                )
                                FilterChip(
                                    selected = favoritesOnly,
                                    onClick = { favoritesOnly = true },
                                    label = { Text("Избранное · $favorites") },
                                    leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                )
                            }
                        }
                    }
                    items(shown, key = { it.id }) { transcript ->
                        TranscriptCard(transcript, snippet = null, query = "", onClick = { onOpenTranscript(transcript.id) })
                    }
                }
            }
        }
    }

    recordingToDelete?.let { recording ->
        AlertDialog(
            onDismissRequest = { recordingToDelete = null },
            title = { Text("Удалить запись?") },
            text = { Text("«${recording.displayName.substringBeforeLast('.')}» будет удалена без возможности восстановления.") },
            confirmButton = {
                TextButton(onClick = {
                    container.recorder.delete(recording)
                    recordingToDelete = null
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { recordingToDelete = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun BrandTitle() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FennecMark(Modifier.size(28.dp))
        Spacer(Modifier.width(10.dp))
        Text("FENEK", fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
    }
}

@Composable
private fun RecordingBanner(active: RecorderState.Active, onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                (if (active.paused) "Запись на паузе · " else "Идёт запись · ") + TimeFormat.clock(active.elapsedMs),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            Text("Открыть", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun RecordingCard(recording: Recording, onTranscribe: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        recording.displayName.substringBeforeLast('.'),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        TimeFormat.clock(recording.durationMs) +
                            (if (recording.marks.isEmpty()) "" else " · " + pluralRu(recording.marks.size, "отметка", "отметки", "отметок")) +
                            " · ещё не расшифрована",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDelete) { Text("Удалить") }
                TextButton(onClick = onShare) { Text("Поделиться") }
                TextButton(onClick = onTranscribe) { Text("Расшифровать") }
            }
        }
    }
}

@Composable
private fun ModelSetupCard(
    plan: DownloadPlan,
    states: Map<String, ModelState>,
    onDownload: () -> Unit,
    onChooseModels: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FennecMark(Modifier.size(36.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    if (plan.requiredMissing.isEmpty()) "Скачайте языковую модель" else "Скачайте модели для работы офлайн",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                if (plan.requiredMissing.isEmpty()) {
                    "Языковая модель исправляет пунктуацию и явные ошибки и пишет выжимку с задачами. " +
                        "Без неё расшифровка работает, а выжимка строится по правилам."
                } else {
                    "Распознавание речи, определение говорящих и языковая модель работают прямо на телефоне — " +
                        "записи никуда не отправляются. Интернет нужен один раз, чтобы скачать модели."
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            val missing = plan.all
            missing.forEach { model ->
                val state = states[model.id]
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(model.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        when (state) {
                            is ModelState.Downloading -> "${(state.fraction * 100).toInt()} %"
                            is ModelState.Failed -> "ошибка"
                            else -> formatBytes(model.totalBytes)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state is ModelState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val downloading = missing.mapNotNull { states[it.id] as? ModelState.Downloading }
            if (downloading.isNotEmpty()) {
                val bytes = downloading.sumOf { it.bytes }
                val total = downloading.sumOf { it.total }.coerceAtLeast(1)
                LinearProgressIndicator(
                    progress = { bytes.toFloat() / total },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }
            missing.firstNotNullOfOrNull { states[it.id] as? ModelState.Failed }?.let {
                Text(
                    it.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                val failed = missing.any { states[it.id] is ModelState.Failed }
                Button(onClick = onDownload, enabled = downloading.size < missing.size) {
                    Text(
                        when {
                            failed -> "Повторить (${formatBytes(plan.totalBytes)})"
                            plan.requiredMissing.isEmpty() -> "Скачать языковую модель (${formatBytes(plan.totalBytes)})"
                            else -> "Скачать (${formatBytes(plan.totalBytes)})"
                        },
                    )
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onChooseModels) { Text("Выбрать модели") }
            }
        }
    }
}

@Composable
private fun JobProgressCard(state: JobState.Running, onCancel: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        state.request.displayName.substringBeforeLast('.'),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(state.progress.statusText(), style = MaterialTheme.typography.bodyMedium)
                }
            }
            LinearProgressIndicator(
                progress = { state.progress.overall },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${(state.progress.overall * 100).toInt()} % · можно свернуть приложение",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCancel) { Text("Отменить") }
            }
        }
    }
}

@Composable
private fun JobFailedCard(state: JobState.Failed, onDismiss: () -> Unit, onOpenModels: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Не удалось расшифровать «${state.fileName}»",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Text(
                state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onOpenModels) { Text("Модели") }
                TextButton(onClick = onDismiss) { Text("Закрыть") }
            }
        }
    }
}

@Composable
private fun TranscriptCard(transcript: Transcript, snippet: String?, query: String, onClick: () -> Unit) {
    val speakers = transcript.activeSpeakers()
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    highlight(transcript.title, query, MaterialTheme.colorScheme.tertiaryContainer),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (transcript.favorite) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "В избранном",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                "${TimeFormat.shortDate(transcript.conversationAt)} · ${TimeFormat.clock(transcript.durationMs)} · " +
                    pluralRu(speakers.size, "участник", "участника", "участников"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (snippet != null) {
                Text(
                    highlight(snippet, query, MaterialTheme.colorScheme.tertiaryContainer),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                transcript.segments.take(2).forEach { segment ->
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                        SpeakerAvatar(segment.speaker, transcript.speakerName(segment.speaker), size = 22.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            segment.text,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (transcript.segments.isEmpty()) {
                    Text(
                        "Речь не обнаружена",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FennecMark(Modifier.size(96.dp))
        Text(
            "Пока нет расшифровок",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            "Запишите разговор или выберите аудиофайл: речь будет распознана, реплики разделены " +
                "по участникам, у каждой появятся временные метки. Файл можно также отправить " +
                "в приложение через «Поделиться».",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
}
