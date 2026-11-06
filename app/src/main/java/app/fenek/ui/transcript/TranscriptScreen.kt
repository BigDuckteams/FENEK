package app.fenek.ui.transcript

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.fenek.analytics.ActionItem
import app.fenek.analytics.ConversationAnalytics
import app.fenek.analytics.ConversationHighlights
import app.fenek.analytics.ConversationStats
import app.fenek.analytics.Highlights
import app.fenek.analytics.Sentence
import app.fenek.data.Transcript
import app.fenek.data.TranscriptSearch
import app.fenek.data.Turn
import app.fenek.export.ExportFormat
import app.fenek.model.Languages
import app.fenek.ui.components.SpeakerAvatar
import app.fenek.ui.components.highlight
import app.fenek.ui.theme.SpeakerColors
import app.fenek.util.TimeFormat
import app.fenek.util.pluralRu
import kotlinx.coroutines.launch
import java.io.File
import java.time.ZoneId
import kotlin.math.roundToInt

private sealed interface TranscriptDialog {
    data object Rename : TranscriptDialog
    data object Delete : TranscriptDialog
    data object Recount : TranscriptDialog
    data class EditSpeaker(val speaker: Int) : TranscriptDialog
    data class EditSegment(val index: Int) : TranscriptDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptScreen(id: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: TranscriptViewModel = viewModel(key = id) {
        TranscriptViewModel(context.applicationContext as Application, id)
    }
    val transcript by vm.transcript.collectAsStateWithLifecycle()
    val player by vm.player.state.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<TranscriptDialog?>(null) }
    var exportMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var matchIndex by rememberSaveable { mutableIntStateOf(0) }
    var follow by rememberSaveable { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val t = transcript

    val turns = remember(t) { t?.turns().orEmpty() }
    val stats = remember(t) { t?.let(ConversationAnalytics::compute) }
    val highlights = remember(t) { t?.let { ConversationHighlights.compute(it) } }
    val showInsights = t != null && highlights != null && (!highlights.isEmpty || t.marks.isNotEmpty())
    val showStats = stats != null && stats.speakers.isNotEmpty()
    val turnOfSegment = remember(turns) {
        HashMap<Int, Int>().apply { turns.forEachIndexed { ti, turn -> turn.segments.forEach { put(it.index, ti) } } }
    }
    // Every mark belongs to the turn that was going on when it was made.
    val marksOfTurn = remember(t, turns) {
        t?.marks.orEmpty().groupBy { mark -> turns.indexOfLast { it.startMs <= mark }.coerceAtLeast(0) }
    }
    val matches = remember(t, query) { t?.let { TranscriptSearch.matchingSegments(it, query) }.orEmpty() }
    val currentMatch = matches.getOrNull(matchIndex.coerceIn(0, (matches.size - 1).coerceAtLeast(0)))
    // List items before the first turn: the header, the highlights and the statistics.
    val firstTurnItem = 1 + (if (showInsights) 1 else 0) + (if (showStats) 1 else 0)

    LaunchedEffect(searchOpen) { if (searchOpen) runCatching { focus.requestFocus() } }
    LaunchedEffect(currentMatch, searchOpen) {
        val segment = currentMatch ?: return@LaunchedEffect
        if (!searchOpen) return@LaunchedEffect
        turnOfSegment[segment]?.let { listState.animateScrollToItem(firstTurnItem + it) }
    }
    // Karaoke: keep the phrase being played on screen.
    val playingTurn by remember(turns) {
        derivedStateOf {
            if (!player.playing) -1 else turns.indexOfFirst { player.positionMs in it.startMs until it.endMs }
        }
    }
    LaunchedEffect(playingTurn, follow) {
        if (!follow || playingTurn < 0) return@LaunchedEffect
        val item = firstTurnItem + playingTurn
        val visible = listState.layoutInfo.visibleItemsInfo
        val fullyVisible = visible.any { it.index == item && it.offset >= 0 && it.offset + it.size <= listState.layoutInfo.viewportEndOffset }
        if (!fullyVisible) listState.animateScrollToItem(item)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searchOpen) {
                        TextField(
                            value = query,
                            onValueChange = {
                                query = it
                                matchIndex = 0
                            },
                            placeholder = { Text("Поиск в расшифровке") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focus),
                        )
                    } else {
                        Text(t?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (t != null && searchOpen) {
                        if (query.isNotBlank()) {
                            Text(
                                if (matches.isEmpty()) "0" else "${matchIndex + 1}/${matches.size}",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        IconButton(onClick = { if (matches.isNotEmpty()) matchIndex = (matchIndex - 1 + matches.size) % matches.size }) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Предыдущее совпадение")
                        }
                        IconButton(onClick = { if (matches.isNotEmpty()) matchIndex = (matchIndex + 1) % matches.size }) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Следующее совпадение")
                        }
                        IconButton(onClick = {
                            searchOpen = false
                            query = ""
                        }) { Icon(Icons.Outlined.Close, contentDescription = "Закрыть поиск") }
                    } else if (t != null) {
                    IconButton(onClick = vm::toggleFavorite) {
                        Icon(
                            if (t.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (t.favorite) "Убрать из избранного" else "Добавить в избранное",
                            tint = if (t.favorite) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                    IconButton(onClick = { searchOpen = true }) {
                        Icon(Icons.Outlined.Search, contentDescription = "Поиск")
                    }
                    Box {
                        IconButton(onClick = { exportMenu = true }) {
                            Icon(Icons.Outlined.Share, contentDescription = "Экспорт")
                        }
                        DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                            ExportFormat.entries.forEach { format ->
                                DropdownMenuItem(
                                    text = { Text(format.title) },
                                    onClick = {
                                        exportMenu = false
                                        Sharing.share(context, t, format)
                                    },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Скопировать текст") },
                                leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                                onClick = {
                                    exportMenu = false
                                    Sharing.copyText(context, t)
                                },
                            )
                            val ownAudio = t.audio?.takeIf { t.audioOwned }?.let(::File)?.takeIf { it.isFile }
                            if (ownAudio != null) {
                                DropdownMenuItem(
                                    text = { Text("Аудиозапись") },
                                    leadingIcon = { Icon(Icons.Outlined.AudioFile, contentDescription = null) },
                                    onClick = {
                                        exportMenu = false
                                        scope.launch { Sharing.shareAudio(context, ownAudio, t.title) }
                                    },
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { moreMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "Ещё")
                        }
                        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Переименовать") },
                                leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null) },
                                onClick = {
                                    moreMenu = false
                                    dialog = TranscriptDialog.Rename
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Пересчитать участников") },
                                leadingIcon = { Icon(Icons.Outlined.Groups, contentDescription = null) },
                                onClick = {
                                    moreMenu = false
                                    dialog = TranscriptDialog.Recount
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Удалить") },
                                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                onClick = {
                                    moreMenu = false
                                    dialog = TranscriptDialog.Delete
                                },
                            )
                        }
                    }
                    }
                },
            )
        },
        bottomBar = {
            if (t?.audio != null) {
                PlayerBar(
                    state = player,
                    follow = follow,
                    onToggle = vm.player::toggle,
                    onSeek = vm.player::seekTo,
                    onSpeed = vm.player::cycleSpeed,
                    onFollow = { follow = it },
                )
            }
        },
    ) { padding ->
        if (t == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Расшифровка не найдена")
            }
        } else LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "header") {
                HeaderCard(t, onSpeakerClick = { dialog = TranscriptDialog.EditSpeaker(it) })
            }
            if (showInsights) {
                item(key = "insights") {
                    InsightsCard(
                        t = t,
                        highlights = highlights,
                        onPlay = vm.player::playFrom,
                        onCalendar = { addToCalendar(context, t, it) },
                    )
                }
            }
            if (showStats) {
                item(key = "stats") {
                    StatsCard(
                        t = t,
                        stats = stats,
                        positionMs = player.positionMs.takeIf { player.ready && (player.playing || it > 0) },
                        onSeek = vm.player::playFrom,
                        onTopic = { topic ->
                            searchOpen = true
                            query = topic
                            matchIndex = 0
                        },
                    )
                }
            }
            if (turns.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "В записи не найдено речи.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            itemsIndexed(turns, key = { _, turn -> turn.segments.first().index }) { index, turn ->
                TurnItem(
                    transcript = t,
                    turn = turn,
                    marks = marksOfTurn[index].orEmpty(),
                    player = player,
                    query = if (searchOpen) query else "",
                    currentMatch = if (searchOpen) currentMatch else null,
                    onPlay = vm.player::playFrom,
                    onEdit = { dialog = TranscriptDialog.EditSegment(it) },
                )
            }
        }
    }

    if (t != null) {
        when (val d = dialog) {
            TranscriptDialog.Rename -> RenameDialog(
                title = t.title,
                onDismiss = { dialog = null },
                onConfirm = {
                    vm.rename(it)
                    dialog = null
                },
            )
            TranscriptDialog.Recount -> RecountDialog(
                onDismiss = { dialog = null },
                onConfirm = { numSpeakers ->
                    dialog = null
                    val error = vm.recount(numSpeakers)
                    if (error == null) onBack() else Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                },
            )
            TranscriptDialog.Delete -> DeleteDialog(
                onDismiss = { dialog = null },
                onConfirm = {
                    dialog = null
                    vm.delete(onBack)
                },
            )
            is TranscriptDialog.EditSpeaker -> SpeakerDialog(
                transcript = t,
                speaker = d.speaker,
                onDismiss = { dialog = null },
                onRename = { name ->
                    vm.renameSpeaker(d.speaker, name)
                    dialog = null
                },
                onMerge = { into ->
                    vm.mergeSpeaker(d.speaker, into)
                    dialog = null
                },
            )
            is TranscriptDialog.EditSegment -> t.segments.getOrNull(d.index)?.let { segment ->
                SegmentDialog(
                    transcript = t,
                    segment = segment,
                    onDismiss = { dialog = null },
                    onSave = { text, speaker ->
                        vm.updateSegment(d.index, text, speaker)
                        dialog = null
                    },
                )
            }
            null -> Unit
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeaderCard(t: Transcript, onSpeakerClick: (Int) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            InfoRow(Icons.Outlined.Event, TimeFormat.date(t.conversationAt))
            InfoRow(
                Icons.Outlined.Schedule,
                "Длительность ${TimeFormat.clock(t.durationMs)} · обработано за ${TimeFormat.duration(t.processingMs)}",
            )
            InfoRow(
                Icons.Outlined.GraphicEq,
                t.asrModelTitle + (t.language?.let { " · " + (Languages.title(it) ?: it) } ?: ""),
            )
            Text(
                "Участники",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                t.activeSpeakers().forEach { speaker ->
                    val color = SpeakerColors.of(speaker.id)
                    AssistChip(
                        onClick = { onSpeakerClick(speaker.id) },
                        label = { Text(speaker.name) },
                        leadingIcon = {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(color),
                            )
                        },
                    )
                }
            }
            Text(
                "Нажмите на участника, чтобы указать имя или объединить его с другим. " +
                    "Нажмите на фразу, чтобы прослушать её; удерживайте, чтобы исправить.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsCard(
    t: Transcript,
    stats: ConversationStats,
    positionMs: Long?,
    onSeek: (Long) -> Unit,
    onTopic: (String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Ход разговора", style = MaterialTheme.typography.titleSmall)
            Timeline(
                t,
                positionMs,
                onSeek,
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .height(30.dp),
            )
            Row(Modifier.padding(top = 2.dp, bottom = 14.dp)) {
                Text("00:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(
                    TimeFormat.clock(t.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("Кто сколько говорил", style = MaterialTheme.typography.titleSmall)
            Row(
                Modifier
                    .padding(vertical = 10.dp)
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp)),
            ) {
                stats.speakers.forEach { s ->
                    Box(
                        Modifier
                            .weight(s.share.coerceAtLeast(0.005f))
                            .fillMaxHeight()
                            .background(SpeakerColors.of(s.speaker)),
                    )
                }
            }
            stats.speakers.forEach { s ->
                Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(SpeakerColors.of(s.speaker)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        t.speakerName(s.speaker),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${TimeFormat.clock(s.talkMs)} · ${(s.share * 100).roundToInt()} % · " +
                            pluralRu(s.turns, "реплика", "реплики", "реплик"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val facts = buildList {
                stats.chattiest?.let { add("Больше всех говорит: ${t.speakerName(it.speaker)}") }
                stats.fastest?.let { add("Самый быстрый темп: ${t.speakerName(it.speaker)}, ${it.wordsPerMinute} слов/мин") }
                stats.speakers.maxByOrNull { it.longestTurnMs }?.takeIf { it.longestTurnMs >= 20_000 }?.let {
                    add("Самый длинный монолог: ${t.speakerName(it.speaker)}, ${TimeFormat.clock(it.longestTurnMs)}")
                }
                add("Всего: " + pluralRu(stats.words, "слово", "слова", "слов") + ", " + pluralRu(stats.turns, "реплика", "реплики", "реплик"))
            }
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                facts.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            if (stats.topics.isNotEmpty()) {
                Text(
                    "О чём говорили",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    stats.topics.forEach { topic ->
                        SuggestionChip(onClick = { onTopic(topic) }, label = { Text(topic) })
                    }
                }
            }
        }
    }
}

/** The whole conversation as a strip: who spoke when, with the marks above it. A tap plays from there. */
@Composable
private fun Timeline(t: Transcript, positionMs: Long?, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    val duration = t.durationMs.coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val markColor = MaterialTheme.colorScheme.onSurface
    val cursorColor = MaterialTheme.colorScheme.primary
    val colors = t.segments.map { SpeakerColors.of(it.speaker) }
    val seek by rememberUpdatedState(onSeek)
    Canvas(
        modifier.pointerInput(duration) {
            detectTapGestures { offset ->
                seek((offset.x / size.width * duration).toLong().coerceIn(0, duration))
            }
        },
    ) {
        val top = 9.dp.toPx()
        val bottom = size.height - 3.dp.toPx()
        fun x(ms: Long) = ms.toFloat() / duration * size.width
        clipPath(Path().apply { addRoundRect(RoundRect(0f, top, size.width, bottom, CornerRadius(4.dp.toPx()))) }) {
            drawRect(track, topLeft = Offset(0f, top), size = Size(size.width, bottom - top))
            t.segments.forEachIndexed { i, s ->
                val from = x(s.startMs)
                drawRect(colors[i], topLeft = Offset(from, top), size = Size((x(s.endMs) - from).coerceAtLeast(1f), bottom - top))
            }
        }
        val half = 4.dp.toPx()
        t.marks.forEach { mark ->
            val mx = x(mark)
            val triangle = Path().apply {
                moveTo(mx - half, 0f)
                lineTo(mx + half, 0f)
                lineTo(mx, 7.dp.toPx())
                close()
            }
            drawPath(triangle, markColor)
        }
        if (positionMs != null) {
            val px = x(positionMs)
            drawLine(cursorColor, Offset(px, top - 2.dp.toPx()), Offset(px, size.height), strokeWidth = 2.dp.toPx())
        }
    }
}

/** "Главное": key sentences, tasks with deadlines, questions and the marks made while recording. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InsightsCard(t: Transcript, highlights: Highlights, onPlay: (Long) -> Unit, onCalendar: (ActionItem) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text("Главное", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
            if (t.marks.isNotEmpty()) {
                InsightHeader(Icons.Outlined.Bookmark, "Отметки при записи", t.marks.size)
                FlowRow(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    t.marks.forEach { mark ->
                        AssistChip(
                            // People press the button right after the words that mattered.
                            onClick = { onPlay((mark - MARK_LEAD_MS).coerceAtLeast(0)) },
                            label = { Text(TimeFormat.clock(mark)) },
                            leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                    }
                }
            }
            if (highlights.summary.isNotEmpty()) {
                InsightList(Icons.Outlined.Summarize, "Коротко", highlights.summary) { MomentRow(t, it, onPlay) }
            }
            if (highlights.actions.isNotEmpty()) {
                InsightList(Icons.Outlined.TaskAlt, "Задачи и договорённости", highlights.actions) { item ->
                    MomentRow(t, item.sentence, onPlay) {
                        val deadline = item.deadline
                        if (deadline?.date != null) {
                            AssistChip(
                                onClick = { onCalendar(item) },
                                label = { Text(deadline.label()) },
                                leadingIcon = {
                                    Icon(Icons.Outlined.EditCalendar, contentDescription = "Добавить в календарь", modifier = Modifier.size(18.dp))
                                },
                            )
                        } else if (deadline != null) {
                            Text(
                                "Срок: ${deadline.phrase}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
            if (highlights.questions.isNotEmpty()) {
                InsightList(Icons.AutoMirrored.Outlined.HelpOutline, "Вопросы", highlights.questions) { MomentRow(t, it, onPlay) }
            }
            if (!highlights.isEmpty) {
                Text(
                    "Подобрано автоматически по тексту. Нажмите на пункт, чтобы прослушать это место; " +
                        "на срок — чтобы добавить задачу в календарь.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun InsightHeader(icon: ImageVector, title: String, count: Int) {
    Row(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A titled list that shows its first items until expanded. */
@Composable
private fun <T> InsightList(icon: ImageVector, title: String, items: List<T>, row: @Composable (T) -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    InsightHeader(icon, title, items.size)
    (if (expanded) items else items.take(COLLAPSED_ITEMS)).forEach { row(it) }
    if (items.size > COLLAPSED_ITEMS) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(start = 4.dp)) {
            Text(if (expanded) "Свернуть" else "Показать все (${items.size})")
        }
    }
}

/** A sentence with who said it and when; a tap plays it. */
@Composable
private fun MomentRow(t: Transcript, sentence: Sentence, onPlay: (Long) -> Unit, extra: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onPlay(sentence.startMs) }
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Box(
            Modifier
                .padding(top = 7.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(SpeakerColors.of(sentence.speaker)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(sentence.text, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${t.speakerName(sentence.speaker)} · ${TimeFormat.clock(sentence.startMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            extra()
        }
    }
}

/** Opens the calendar app with an event for the task on its deadline. */
private fun addToCalendar(context: Context, t: Transcript, item: ActionItem) {
    val deadline = item.deadline ?: return
    val date = deadline.date ?: return
    val start = deadline.time?.let { date.atTime(it) } ?: date.atStartOfDay()
    val begin = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, item.sentence.text)
        .putExtra(CalendarContract.Events.DESCRIPTION, "Из разговора «${t.title}», ${TimeFormat.clock(item.sentence.startMs)}")
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
    if (deadline.time == null) {
        intent.putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
    } else {
        intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 30 * 60_000L)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Не найдено приложение календаря", Toast.LENGTH_SHORT).show()
    }
}

private const val COLLAPSED_ITEMS = 3
private const val MARK_LEAD_MS = 5_000L

@Composable
private fun InfoRow(icon: ImageVector, text: String) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TurnItem(
    transcript: Transcript,
    turn: Turn,
    marks: List<Long>,
    player: PlayerState,
    query: String,
    currentMatch: Int?,
    onPlay: (Long) -> Unit,
    onEdit: (Int) -> Unit,
) {
    val name = transcript.speakerName(turn.speaker)
    val color = SpeakerColors.of(turn.speaker)
    val highlightColor = MaterialTheme.colorScheme.tertiaryContainer
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        SpeakerAvatar(turn.speaker, name, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${TimeFormat.clock(turn.startMs)} – ${TimeFormat.clock(turn.endMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (marks.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Outlined.Bookmark,
                        contentDescription = "Отметка",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        marks.joinToString(", ") { TimeFormat.clock(it) },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            turn.segments.forEach { (index, segment) ->
                val playing = player.playing && player.positionMs in segment.startMs until segment.endMs
                val isCurrentMatch = index == currentMatch
                Text(
                    highlight(segment.text, query, highlightColor),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (playing) color.copy(alpha = 0.16f) else Color.Transparent)
                        .then(
                            if (isCurrentMatch) {
                                Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary), RoundedCornerShape(8.dp))
                            } else {
                                Modifier
                            },
                        )
                        .combinedClickable(
                            onClick = { onPlay(segment.startMs) },
                            onLongClick = { onEdit(index) },
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun PlayerBar(
    state: PlayerState,
    follow: Boolean,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeed: () -> Unit,
    onFollow: (Boolean) -> Unit,
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val error = state.error
            if (error != null) {
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
                return@Row
            }
            IconButton(onClick = onToggle, enabled = state.ready) {
                Icon(
                    if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.playing) "Пауза" else "Воспроизвести",
                )
            }
            Text(TimeFormat.clock(state.positionMs), style = MaterialTheme.typography.labelMedium)
            var dragging by remember { mutableStateOf<Float?>(null) }
            Slider(
                value = dragging ?: state.positionMs.toFloat(),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { onSeek(it.toLong()) }
                    dragging = null
                },
                valueRange = 0f..state.durationMs.coerceAtLeast(1).toFloat(),
                enabled = state.ready,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
            )
            TextButton(onClick = onSpeed, enabled = state.ready) {
                Text(speedLabel(state.speed), style = MaterialTheme.typography.labelLarge)
            }
            IconToggleButton(checked = follow, onCheckedChange = onFollow) {
                Icon(
                    Icons.Outlined.Subtitles,
                    contentDescription = if (follow) "Не следить за звуком" else "Следить за звуком",
                    tint = if (follow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun speedLabel(speed: Float): String =
    (if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().replace('.', ',')) + "×"
