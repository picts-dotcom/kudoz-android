package ch.kudoz.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.kudoz.app.data.AuthFlow
import ch.kudoz.app.data.Notification
import ch.kudoz.app.data.Project
import ch.kudoz.app.data.Repo
import ch.kudoz.app.data.Workspace
import ch.kudoz.app.push.Push
import kotlinx.coroutines.launch

/** Einfacher Navigationsstapel – ohne Navigation-Bibliothek, damit Zurück immer vorhersehbar ist. */
sealed interface Screen {
    data object Workspaces : Screen
    data class Projects(val ws: Workspace) : Screen
    data class Board(val ws: Workspace, val project: Project) : Screen
    data class CardDetail(val ws: Workspace, val project: Project, val cardId: String) : Screen
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell() {
    val stack = remember { mutableStateListOf<Screen>(Screen.Workspaces) }
    var tab by remember { mutableIntStateOf(0) }
    var unread by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val top = stack.last()

    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun showError(e: Throwable) { scope.launch { snackbar.showSnackbar(friendlyError(e)) } }

    fun openLink(link: String) {
        val m = Regex("^/w/([^/?#]+)/p/([^/?#]+)(?:\\?card=([^&#]+))?").find(link)
        if (m == null) { openWeb(context, link); return }
        scope.launch {
            runCatching {
                val ws = Repo.workspace(m.groupValues[1])
                val pr = Repo.project(m.groupValues[2])
                val cardId = m.groupValues[3]
                stack.clear(); stack.add(Screen.Workspaces); stack.add(Screen.Projects(ws))
                stack.add(Screen.Board(ws, pr))
                if (cardId.isNotEmpty()) stack.add(Screen.CardDetail(ws, pr, cardId))
                tab = 0
            }.onFailure { showError(it) }
        }
    }

    LaunchedEffect(Unit) { runCatching { unread = Repo.notifications().count { it.readAt == null } } }
    val pendingLink by Push.pendingLink
    LaunchedEffect(pendingLink) {
        pendingLink?.let { Push.pendingLink.value = null; openLink(if (it.startsWith("/")) it else "/$it") }
    }

    BackHandler(enabled = tab == 1 || stack.size > 1) { if (tab == 1) tab = 0 else pop() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (tab == 0 && top !is Screen.Board && top !is Screen.CardDetail) {
                TopAppBar(
                    title = {
                        Text(
                            when (top) { is Screen.Projects -> top.ws.name; else -> "Kudoz" },
                            fontWeight = FontWeight.Black,
                        )
                    },
                    navigationIcon = {
                        if (stack.size > 1) IconButton(onClick = { pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                        }
                    },
                    actions = {
                        if (top is Screen.Projects) IconButton(onClick = { openWeb(context, "/w/${top.ws.id}") }) {
                            Icon(Icons.Default.OpenInBrowser, "Im Web öffnen")
                        }
                        if (top is Screen.Workspaces) IconButton(onClick = { scope.launch { AuthFlow.signOut() } }) {
                            Icon(Icons.AutoMirrored.Filled.Logout, "Abmelden")
                        }
                    },
                )
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Dashboard, null) }, label = { Text("Boards") },
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = {
                        BadgedBox(badge = { if (unread > 0) Badge { Text(if (unread > 99) "99+" else "$unread") } }) {
                            Icon(Icons.Default.Notifications, null)
                        }
                    },
                    label = { Text("Mitteilungen") },
                )
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (tab == 1) {
                NotificationsScreen(onUnread = { unread = it }, onError = ::showError, onOpenLink = ::openLink)
            } else when (top) {
                Screen.Workspaces -> WorkspacesScreen(onOpen = { push(Screen.Projects(it)) }, onError = ::showError)
                is Screen.Projects -> ProjectsScreen(top.ws, onOpen = { push(Screen.Board(top.ws, it)) }, onError = ::showError)
                is Screen.Board -> BoardScreen(
                    top.ws, top.project,
                    onBack = { pop() },
                    onOpenCard = { push(Screen.CardDetail(top.ws, top.project, it)) },
                    onError = ::showError,
                )
                is Screen.CardDetail -> CardDetailScreen(top.project, top.cardId, onBack = { pop() }, onError = ::showError)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> LoadingList(
    load: suspend () -> List<T>,
    onError: (Throwable) -> Unit,
    empty: String,
    key: Any? = Unit,
    header: (@Composable () -> Unit)? = null,
    row: @Composable (T) -> Unit,
) {
    var items by remember(key) { mutableStateOf<List<T>?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    suspend fun reload() { runCatching { load() }.onSuccess { items = it }.onFailure(onError) }
    LaunchedEffect(key) { reload() }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { scope.launch { refreshing = true; reload(); refreshing = false } },
        modifier = Modifier.fillMaxSize(),
    ) {
        val list = items
        if (list == null) SplashScreen()
        else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            header?.let { h -> item { h() } }
            if (list.isEmpty()) item { EmptyHint(empty) }
            items(list) { row(it) }
        }
    }
}

@Composable
fun ColorTile(name: String, color: String, sub: String?, onClick: () -> Unit) {
    val (bg, ink) = paletteColors(color)
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(bg), contentAlignment = Alignment.Center) {
                Text(name.take(1).uppercase(), color = ink, fontWeight = FontWeight.Black, fontSize = 20.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                sub?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

private val ROLE = mapOf("admin" to "Admin", "kern" to "Kern", "leitung" to "Leitung", "gast" to "Gast")

@Composable
fun WorkspacesScreen(onOpen: (Workspace) -> Unit, onError: (Throwable) -> Unit) {
    LoadingList(
        load = { Repo.workspaces() },
        onError = onError,
        empty = "Du bist noch in keiner Umgebung. Auf kudoz.ch kannst du eine erstellen oder beitreten.",
        header = {
            Text(
                "Hallo ${Repo.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() } ?: ""} 👋",
                fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp),
            )
        },
    ) { (ws, role) ->
        ColorTile(
            ws.name, ws.color,
            if (ws.privateOwner != null) "Kajüte · privat" else ROLE[role] ?: role,
        ) { onOpen(ws) }
    }
}

@Composable
fun ProjectsScreen(ws: Workspace, onOpen: (Project) -> Unit, onError: (Throwable) -> Unit) {
    LoadingList(
        load = { Repo.projects(ws.id) },
        onError = onError,
        key = ws.id,
        empty = "Noch keine Projekte in dieser Umgebung.",
    ) { p -> ColorTile(p.name, p.color, p.description) { onOpen(p) } }
}

@Composable
fun NotificationsScreen(onUnread: (Int) -> Unit, onError: (Throwable) -> Unit, onOpenLink: (String) -> Unit) {
    var version by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LoadingList<Notification>(
        load = { Repo.notifications().also { l -> onUnread(l.count { it.readAt == null }) } },
        onError = onError,
        key = version,
        empty = "Keine Mitteilungen.",
        header = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Mitteilungen", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    scope.launch { runCatching { Repo.markAllRead() }.onFailure(onError); version++ }
                }) { Icon(Icons.Default.DoneAll, "Alle gelesen") }
            }
        },
    ) { n ->
        val unread = n.readAt == null
        Card(
            onClick = {
                scope.launch {
                    if (unread) runCatching { Repo.markRead(n.id) }
                    version++
                }
                n.link?.let { onOpenLink(if (it.startsWith("/")) it else "/$it") }
            },
            colors = CardDefaults.cardColors(
                containerColor = if (unread) paletteColors("lavender").first.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (unread) Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary))
                Spacer(Modifier.width(if (unread) 10.dp else 0.dp))
                Column(Modifier.weight(1f)) {
                    Text(n.title, fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal)
                    Text(relativeTime(n.createdAt), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
