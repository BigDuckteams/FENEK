package app.fenek.ui.record

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fenek.appContainer
import app.fenek.record.RecorderState
import app.fenek.record.Recording
import app.fenek.ui.brand.FennecMark
import app.fenek.util.TimeFormat
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(autoStart: Boolean, onBack: () -> Unit, onFinished: (Recording) -> Unit) {
    val context = LocalContext.current
    val recorder = remember { context.appContainer.recorder }
    val state by recorder.state.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<String?>(null) }
    var micDenied by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    fun startRecording() {
        micDenied = false
        error = recorder.start()
    }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) startRecording() else micDenied = true
    }

    fun onRecordClick() {
        val wanted = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = wanted.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) startRecording() else permissions.launch(missing.toTypedArray())
    }

    // Quick Settings tile and launcher shortcut: start right away, once per visit.
    var autoStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (autoStart && !autoStarted) {
            autoStarted = true
            if (recorder.state.value is RecorderState.Idle) onRecordClick()
        }
    }

    val active = state as? RecorderState.Active
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Запись разговора") },
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
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val level = active?.takeUnless { it.paused }?.levels?.lastOrNull() ?: 0f
            LevelIndicator(
                level = level,
                recording = active != null && !active.paused,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(170.dp),
            )
            Text(
                TimeFormat.clock(active?.elapsedMs ?: 0, forceHours = (active?.elapsedMs ?: 0) >= 3_600_000),
                style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                when {
                    active == null -> "Готово к записи"
                    active.paused -> "Пауза"
                    else -> "Идёт запись"
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (active != null && !active.paused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Waveform(
                levels = active?.levels.orEmpty(),
                color = if (active?.paused == false) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .padding(vertical = 16.dp),
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            if (micDenied) {
                Text(
                    "Без доступа к микрофону записать разговор не получится. " +
                        "Разрешите его в настройках приложения.",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Открыть настройки") }
            }
            Spacer(Modifier.weight(1f))
            if (active == null) {
                LargeFloatingActionButton(
                    onClick = ::onRecordClick,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = "Начать запись", modifier = Modifier.size(40.dp))
                }
                Text(
                    "Положите телефон между собеседниками. Запись продолжится, если выключить экран " +
                        "или свернуть приложение. Звук сохраняется только на телефоне.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
            } else {
                MarkButton(marks = active.marks, onMark = { recorder.mark() })
                Spacer(Modifier.height(20.dp))
                Controls(
                    paused = active.paused,
                    onDiscard = { confirmDiscard = true },
                    onToggle = { if (active.paused) recorder.resume() else recorder.pause() },
                    onDone = {
                        val recording = recorder.stop()
                        if (recording != null) onFinished(recording) else error = "Запись слишком короткая"
                    },
                )
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Удалить запись?") },
            text = { Text("Записанный звук будет потерян.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    recorder.discard()
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Отмена") } },
        )
    }
}

/** Marks the current moment; marks show up in the transcript and on its timeline. */
@Composable
private fun MarkButton(marks: List<Long>, onMark: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedButton(onClick = {
            onMark()
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }) {
            Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (marks.isEmpty()) "Отметить момент" else "Отметить момент · ${marks.size}")
        }
        Text(
            if (marks.isEmpty()) {
                "Отмеченные места будут видны в расшифровке"
            } else {
                marks.takeLast(4).joinToString("  ·  ") { TimeFormat.clock(it) }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun Controls(paused: Boolean, onDiscard: () -> Unit, onToggle: () -> Unit, onDone: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FilledTonalIconButton(onClick = onDiscard, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Outlined.Delete, contentDescription = "Удалить запись")
            }
            Text("Удалить", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LargeFloatingActionButton(onClick = onToggle, shape = CircleShape) {
                Icon(
                    if (paused) Icons.Filled.Mic else Icons.Filled.Pause,
                    contentDescription = if (paused) "Продолжить" else "Пауза",
                    modifier = Modifier.size(36.dp),
                )
            }
            Text(
                if (paused) "Продолжить" else "Пауза",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FilledTonalIconButton(onClick = onDone, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Check, contentDescription = "Остановить и сохранить")
            }
            Text("Готово", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Microphone icon with a ring that grows with the input level. */
@Composable
private fun LevelIndicator(level: Float, recording: Boolean, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = if (recording) level else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "level",
    )
    val ring = MaterialTheme.colorScheme.primary
    val base = MaterialTheme.colorScheme.primaryContainer
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val outer = size.minDimension / 2
            val inner = outer * 0.55f
            drawCircle(ring.copy(alpha = 0.10f), radius = inner + (outer - inner) * animated)
            drawCircle(ring.copy(alpha = 0.16f), radius = inner + (outer - inner) * animated * 0.6f)
            drawCircle(base, radius = inner)
        }
        // The mascot listens: its ears rise with the loudness.
        FennecMark(Modifier.size(78.dp), earTilt = if (recording) 0.35f + 0.65f * animated else 0f)
    }
}

/** Scrolling bars of recent input levels, newest on the right. */
@Composable
private fun Waveform(levels: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val bar = 4.dp.toPx()
        val gap = 3.dp.toPx()
        val step = bar + gap
        val capacity = ((size.width + gap) / step).toInt()
        val visible = levels.takeLast(capacity)
        val centerY = size.height / 2
        // A faint baseline where there is no sound yet.
        for (i in 0 until capacity - visible.size) {
            drawCircle(color.copy(alpha = 0.3f), radius = bar / 3, center = Offset(i * step + bar / 2, centerY))
        }
        visible.forEachIndexed { i, level ->
            val x = (capacity - visible.size + i) * step
            val h = max(bar, level * size.height)
            drawRoundRect(
                color = color,
                topLeft = Offset(x, centerY - h / 2),
                size = Size(bar, h),
                cornerRadius = CornerRadius(bar / 2, bar / 2),
            )
        }
    }
}
