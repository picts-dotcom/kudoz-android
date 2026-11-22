package ch.kudoz.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.kudoz.app.data.BoardColumn
import ch.kudoz.app.data.InboxItem
import ch.kudoz.app.data.Member
import ch.kudoz.app.data.Profile
import ch.kudoz.app.data.Project
import ch.kudoz.app.data.Repo
import ch.kudoz.app.data.Workspace
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

// ---------------------------------------------------------------- Eingang

/** Aufgaben, die andere Umgebungen hierher schicken: annehmen, Rückfrage, ablehnen. */
@Composable
fun InboxScreen(ws: Workspace, onError: (Throwable) -> Unit) {
    var version by remember { mutableIntStateOf(0) }
    var action by remember { mutableStateOf<Pair<InboxItem, String>?>(null) }
    val scope = rememberCoroutineScope()

    LoadingList(
        load = { Repo.inbox(ws.id) },
        onError = onError,
        key = version,
        empty = "Der Eingang ist leer.\nHier landen Aufgaben, die dir andere Teams schicken.",
    ) { it: InboxItem ->
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Chip(it.sourceName ?: "Andere Umgebung", it.sourceColor ?: "sky")
                    Spacer(Modifier.width(8.dp))
                    Avatar(Profile(id = "", displayName = it.senderName, initials = it.senderInitials, initialsBg = it.senderBg, initialsInk = it.senderInk), 22)
                    Spacer(Modifier.width(6.dp))
                    Text(it.senderName ?: "", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(relativeTime(it.createdAt), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Text(it.title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                it.message?.takeIf { m -> m.isNotBlank() }?.let { m ->
                    Spacer(Modifier.height(6.dp))
                    Surface(color = paletteColors("sky").first, shape = RoundedCornerShape(14.dp)) {
                        Text("«$m»", Modifier.padding(10.dp), color = paletteColors("sky").second)
                    }
                }
                it.description?.takeIf { d -> d.isNotBlank() }?.let { d -> Spacer(Modifier.height(6.dp)); Text(d) }
                dueInfo(it.dueDate)?.let { (t, c) -> Spacer(Modifier.height(6.dp)); Chip("📅 fällig $t", c) }
                val checks = (it.checklist as? JsonArray).orEmpty().mapNotNull { e ->
                    val o = e as? JsonObject ?: return@mapNotNull null
                    (o["body"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null) to (o["done"]?.jsonPrimitive?.booleanOrNull ?: false)
                }
                if (checks.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    checks.forEach { (b, d) -> Text((if (d) "☑ " else "☐ ") + b, fontSize = 14.sp) }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { action = it to "accept" }) { Text("Annehmen") }
                    OutlinedButton(onClick = { action = it to "ask" }) { Text("Rückfrage") }
                    TextButton(onClick = { action = it to "decline" }) { Text("Ablehnen") }
                }
            }
        }
    }

    action?.let { (item, kind) ->
        if (kind == "accept") AcceptDialog(ws, item, onDone = { action = null; version++ }, onDismiss = { action = null }, onError = onError)
        else TextPrompt(
            title = if (kind == "ask") "Rückfrage an ${item.senderName ?: "die andere Umgebung"}" else "Warum lehnst du ab?",
            confirm = if (kind == "ask") "Senden" else "Ablehnen",
            onDismiss = { action = null },
            onConfirm = { text ->
                action = null
                scope.launch {
                    runCatching {
                        if (kind == "ask") Repo.transferAsk(item.id, text) else Repo.transferDecline(item.id, text)
                    }.onFailure(onError)
                    version++
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AcceptDialog(ws: Workspace, item: InboxItem, onDone: () -> Unit, onDismiss: () -> Unit, onError: (Throwable) -> Unit) {
    var projects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var project by remember { mutableStateOf<Project?>(null) }
    var columns by remember { mutableStateOf<List<BoardColumn>>(emptyList()) }
    var column by remember { mutableStateOf<BoardColumn?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(ws.id) { runCatching { projects = Repo.projects(ws.id) }.onFailure(onError) }
    LaunchedEffect(project?.id) {
        val p = project ?: return@LaunchedEffect
        runCatching { columns = Repo.columns(p.id); column = columns.firstOrNull { !it.isDone } }.onFailure(onError)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("«${item.title}» annehmen") },
        text = {
            Column {
                Text("In welches Projekt?", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    projects.forEach { p -> FilterChip(selected = project?.id == p.id, onClick = { project = p }, label = { Text(p.name) }) }
                }
                if (columns.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Spalte", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        columns.forEach { c -> FilterChip(selected = column?.id == c.id, onClick = { column = c }, label = { Text(c.name) }) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = project != null && !busy, onClick = {
                val p = project ?: return@TextButton
                busy = true
                scope.launch {
                    runCatching { Repo.transferAccept(item.id, p.id, column?.id) }.onSuccess { onDone() }.onFailure { busy = false; onError(it) }
                }
            }) { Text("Annehmen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

// ---------------------------------------------------------------- Mitglieder

private val ROLE_LABEL = linkedMapOf("admin" to "Admin", "kern" to "Kern", "leitung" to "Leitung", "gast" to "Gast")
private val ROLE_COLOR = mapOf("admin" to "lavender", "kern" to "mint", "leitung" to "sky", "gast" to "silver")

/** Mitglieder mit Rollen. Admins können Rollen ändern; Einladen und Entfernen bleibt im Web. */
@Composable
fun MembersScreen(ws: Workspace, role: String?, onError: (Throwable) -> Unit) {
    var version by remember { mutableIntStateOf(0) }
    var people by remember { mutableStateOf<Map<String, Profile>>(emptyMap()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val me = Repo.userId
    val isAdmin = role == "admin"

    LoadingList(
        load = {
            Repo.memberList(ws.id).also { l -> people = Repo.profiles(l.map { it.userId }) }
                .sortedWith(compareBy({ ROLE_LABEL.keys.indexOf(it.role) }, { people[it.userId]?.label?.lowercase() ?: "" }))
        },
        onError = onError,
        key = version,
        empty = "Keine Mitglieder gefunden.",
        header = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Einladen, Gast-Zugriffe und Entfernen", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { openWeb(context, "/w/${ws.id}/mitglieder") }) { Text("Im Web") }
            }
        },
    ) { m: Member ->
        var menu by remember { mutableStateOf(false) }
        val p = people[m.userId]
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(p, 36)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p?.label ?: "…", fontWeight = FontWeight.SemiBold)
                    Text(p?.email ?: "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.foundation.layout.Box {
                    Chip(
                        ROLE_LABEL[m.role] ?: m.role,
                        ROLE_COLOR[m.role] ?: "silver",
                        if (isAdmin && m.userId != me) Modifier.clickable { menu = true } else Modifier,
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        ROLE_LABEL.filterKeys { it != m.role && it != "leitung" }.forEach { (k, label) ->
                            DropdownMenuItem(text = { Text("Zu $label machen") }, onClick = {
                                menu = false
                                scope.launch { runCatching { Repo.setRole(m.id, k) }.onFailure(onError); version++ }
                            })
                        }
                    }
                }
            }
        }
    }
}

