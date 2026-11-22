package ch.kudoz.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.kudoz.app.data.BoardColumn
import ch.kudoz.app.data.Card as KCard
import ch.kudoz.app.data.Profile
import ch.kudoz.app.data.Project
import ch.kudoz.app.data.Repo
import ch.kudoz.app.data.Workspace
import kotlinx.coroutines.launch

/**
 * Board fürs Handy: eine Spalte pro Seite (wischen oder Reiter antippen).
 * Langes Drücken auf eine Karte öffnet «Verschieben nach …».
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BoardScreen(
    ws: Workspace,
    project: Project,
    onBack: () -> Unit,
    onOpenCard: (String) -> Unit,
    onOpenArchive: () -> Unit,
    onOpenMilestones: () -> Unit,
    onError: (Throwable) -> Unit,
) {
    var columns by remember { mutableStateOf<List<BoardColumn>?>(null) }
    var cards by remember { mutableStateOf<List<KCard>>(emptyList()) }
    var people by remember { mutableStateOf<Map<String, Profile>>(emptyMap()) }
    var refreshing by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf<BoardColumn?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val (bg, ink) = paletteColors(project.color)

    suspend fun reload() {
        runCatching {
            val cols = Repo.columns(project.id)
            val cs = Repo.cards(project.id)
            people = Repo.profiles(cs.flatMap { c -> c.assignees.map { it.userId } }.toSet())
            columns = cols; cards = cs
        }.onFailure(onError)
    }
    LaunchedEffect(project.id) { reload() }

    val cols = columns
    val pager = rememberPagerState { cols?.size ?: 0 }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(project.name, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(ws.name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
            actions = {
                IconButton(onClick = { scope.launch { reload() } }) { Icon(Icons.Default.Refresh, "Neu laden") }
                IconButton(onClick = onOpenMilestones) { Icon(Icons.Default.Flag, "Meilensteine") }
                IconButton(onClick = onOpenArchive) { Icon(Icons.Default.Inventory2, "Archiv") }
                IconButton(onClick = { openWeb(context, "/w/${ws.id}/p/${project.id}") }) {
                    Icon(Icons.Default.OpenInBrowser, "Im Web öffnen")
                }
            },
        )
        if (cols == null) { SplashScreen(); return@Column }
        if (cols.isEmpty()) { EmptyHint("Dieses Projekt hat noch keine Spalten."); return@Column }

        PillTabs(
            cols.map { col -> "${col.name}  ${cards.count { it.columnId == col.id }}" },
            pager.currentPage.coerceAtMost(cols.lastIndex),
            { i -> scope.launch { pager.animateScrollToPage(i) } },
        )

        Box(Modifier.fillMaxSize()) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                val col = cols[page]
                val colCards = cards.filter { it.columnId == col.id }.sortedBy { it.position }
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { scope.launch { refreshing = true; reload(); refreshing = false } },
                ) {
                    val colBg = col.color?.let { paletteColors(it).first } ?: KudozMutedBg.copy(alpha = 0.7f)
                    LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = 10.dp).padding(top = 10.dp)
                            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(colBg),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (colCards.isEmpty()) item { EmptyHint("Keine Aufgaben in «${col.name}».") }
                        items(colCards, key = { it.id }) { card ->
                            CardRow(
                                card = card,
                                people = card.assignees.map { people[it.userId] },
                                columns = cols,
                                accent = ink,
                                onOpen = { onOpenCard(card.id) },
                                onMove = { target ->
                                    scope.launch {
                                        val pos = (cards.filter { it.columnId == target.id }.maxOfOrNull { it.position } ?: 0.0) + 1000.0
                                        runCatching { Repo.move(card.id, target.id, pos) }.onFailure(onError)
                                        reload()
                                    }
                                },
                                onArchive = {
                                    scope.launch {
                                        runCatching { Repo.setArchived(listOf(card.id), true) }.onFailure(onError)
                                        reload()
                                    }
                                },
                                onToggleDone = {
                                    scope.launch {
                                        runCatching { Repo.setDone(card.id, !card.done) }.onFailure(onError)
                                        reload()
                                    }
                                },
                            )
                        }
                    }
                }
            }
            FloatingActionButton(
                onClick = { adding = cols.getOrNull(pager.currentPage) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            ) { Icon(Icons.Default.Add, "Aufgabe hinzufügen") }
        }
    }

    adding?.let { col ->
        TextPrompt(
            title = "Neue Aufgabe in «${col.name}»",
            confirm = "Hinzufügen",
            onDismiss = { adding = null },
            onConfirm = { title ->
                adding = null
                scope.launch {
                    runCatching { Repo.addCard(project.id, col, title) }.onFailure(onError)
                    reload()
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardRow(
    card: KCard,
    people: List<Profile?>,
    columns: List<BoardColumn>,
    accent: androidx.compose.ui.graphics.Color,
    onOpen: () -> Unit,
    onMove: (BoardColumn) -> Unit,
    onArchive: () -> Unit,
    onToggleDone: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Box {
        KCard(modifier = Modifier.fillMaxWidth(), border = false, color = if (card.done) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f) else androidx.compose.ui.graphics.Color.White) {
            Row(Modifier.fillMaxWidth().combinedClickable(
                onClick = onOpen,
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true },
            ).padding(12.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        card.title,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (card.done) TextDecoration.LineThrough else null,
                        color = if (card.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    val due = dueInfo(card.dueDate)
                    if (due != null || card.waitingText != null || card.followLocked || people.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (card.followLocked) Icon(Icons.Default.Lock, "Wartet auf Vorgänger", Modifier.size(16.dp), tint = accent)
                            due?.let { (t, c) -> Chip("📅 $t", c) }
                            card.waitingText?.let { Chip("⏳ wartet", "peach") }
                            Spacer(Modifier.weight(1f))
                            if (people.isNotEmpty()) AvatarStack(people)
                        }
                    }
                }
                if (card.done) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.CheckCircle, "Erledigt", tint = paletteColors("mint").second)
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(if (card.done) "Wieder öffnen" else "Erledigt ✓") },
                onClick = { menu = false; onToggleDone() },
            )
            if (card.done) DropdownMenuItem(text = { Text("Archivieren") }, onClick = { menu = false; onArchive() })
            HorizontalDivider()
            Text("Verschieben nach …", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            columns.filter { it.id != card.columnId }.forEach { col ->
                DropdownMenuItem(text = { Text(col.name) }, onClick = { menu = false; onMove(col) })
            }
        }
    }
}

@Composable
fun TextPrompt(title: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit, initial: String = "") {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = false, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}


/** Archiv eines Projekts: archivierte Aufgaben ansehen und zurückholen (nichts geht verloren). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(project: Project, onBack: () -> Unit, onError: (Throwable) -> Unit) {
    var version by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Archiv", fontFamily = Fredoka, fontWeight = FontWeight.SemiBold)
                    Text(project.name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
        )
        LoadingList(
            load = { Repo.archived(project.id) },
            onError = onError,
            key = version,
            empty = "Noch nichts archiviert.",
        ) { c: ch.kudoz.app.data.ArchivedCard ->
            KCard(modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.title, fontWeight = FontWeight.SemiBold)
                        c.doneAt?.let { Text("erledigt ${relativeTime(it)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    IconButton(onClick = {
                        scope.launch { runCatching { Repo.setArchived(listOf(c.id), false) }.onFailure(onError); version++ }
                    }) { Icon(Icons.Default.Unarchive, "Zurückholen") }
                }
            }
        }
    }
}
