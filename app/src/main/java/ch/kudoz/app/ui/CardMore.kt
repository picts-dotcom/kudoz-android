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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.kudoz.app.data.BoardColumn
import ch.kudoz.app.data.Card
import ch.kudoz.app.data.CardDocument
import ch.kudoz.app.data.Milestone
import ch.kudoz.app.data.Profile
import ch.kudoz.app.data.ProjectFull
import ch.kudoz.app.data.Repo
import ch.kudoz.app.data.Workspace
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- Dokumente (nur Links)

@Composable
fun DocumentsSection(docs: List<CardDocument>, me: String?, onAdd: () -> Unit, onDelete: (String) -> Unit) {
    val uri = LocalUriHandler.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Dokumente", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        TextButton(onClick = onAdd) { Text("+ Link") }
    }
    if (docs.isEmpty()) Text("Links zu Drive-Dateien, Webseiten usw.", fontSize = 13.sp, color = KudozMuted)
    docs.forEach { d ->
        Row(Modifier.fillMaxWidth().clickable { runCatching { uri.openUri(d.url) } }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Link, null, tint = KudozPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(d.title ?: d.url.removePrefix("https://").take(60), color = KudozPrimary, fontWeight = FontWeight.SemiBold)
                d.note?.let { Text(it, fontSize = 12.sp, color = KudozMuted) }
            }
            if (d.createdBy == me) IconButton(onClick = { onDelete(d.id) }) { Icon(Icons.Default.Close, "Entfernen", tint = KudozMuted) }
        }
    }
}

@Composable
fun DocumentDialog(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link hinzufügen") },
        text = {
            Column {
                OutlinedTextField(url, { url = it }, label = { Text("Adresse (https://…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(title, { title = it }, label = { Text("Titel (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(note, { note = it }, label = { Text("Notiz (optional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = url.contains('.'), onClick = { onSave(url, title, note) }) { Text("Hinzufügen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

// ---------------------------------------------------------------- Helfende & Fokus

@Composable
fun HelpersSection(helpers: List<String>, people: Map<String, Profile>, me: String?, onToggleMe: (Boolean) -> Unit, onFocus: () -> Unit) {
    val helping = me in helpers
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Helfen", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        TextButton(onClick = onFocus) { Text("🎯 Fokus-Stupser") }
        TextButton(onClick = { onToggleMe(!helping) }) { Text(if (helping) "Hilfe beenden" else "🙋 Ich helfe") }
    }
    if (helpers.isEmpty()) Text("Noch niemand hilft mit.", fontSize = 13.sp, color = KudozMuted)
    else Row(verticalAlignment = Alignment.CenterVertically) {
        AvatarStack(helpers.map { people[it] })
        Spacer(Modifier.width(10.dp))
        Text(helpers.mapNotNull { people[it]?.label }.joinToString(", "), fontSize = 13.sp)
    }
}

// ---------------------------------------------------------------- Wartet auf Aufgabe

@Composable
fun WaitForDialog(card: Card, onDismiss: () -> Unit, onPick: (String?) -> Unit, onError: (Throwable) -> Unit) {
    var others by remember { mutableStateOf<List<Card>?>(null) }
    LaunchedEffect(card.id) { runCatching { others = Repo.cards(card.projectId).filter { it.id != card.id && !it.done } }.onFailure(onError) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Worauf wartet diese Aufgabe?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val l = others
                if (l == null) Text("Lädt …")
                else if (l.isEmpty()) Text("Keine anderen offenen Aufgaben im Projekt.")
                l?.forEach { c ->
                    Text(c.title, Modifier.fillMaxWidth().clickable { onPick(c.id) }.padding(vertical = 10.dp), fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(null) }) { Text("Nicht mehr warten") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

// ---------------------------------------------------------------- Meilenstein der Karte

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MilestonePickDialog(projectId: String, current: String?, onDismiss: () -> Unit, onPick: (String?) -> Unit, onError: (Throwable) -> Unit) {
    var list by remember { mutableStateOf<List<Milestone>>(emptyList()) }
    LaunchedEffect(projectId) { runCatching { list = Repo.milestones(projectId) }.onFailure(onError) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zu welchem Meilenstein?") },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = current == null, onClick = { onPick(null) }, label = { Text("Keiner") })
                list.forEach { m -> FilterChip(selected = current == m.id, onClick = { onPick(m.id) }, label = { Text("🚩 ${m.title}") }) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schliessen") } },
    )
}

// ---------------------------------------------------------------- Kajüte: An Bord bringen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BringAboardDialog(card: Card, onDismiss: () -> Unit, onDone: () -> Unit, onError: (Throwable) -> Unit) {
    var wss by remember { mutableStateOf<List<Workspace>>(emptyList()) }
    var ws by remember { mutableStateOf<Workspace?>(null) }
    var projects by remember { mutableStateOf<List<ProjectFull>>(emptyList()) }
    var project by remember { mutableStateOf<ProjectFull?>(null) }
    var columns by remember { mutableStateOf<List<BoardColumn>>(emptyList()) }
    var column by remember { mutableStateOf<BoardColumn?>(null) }
    var keepNotes by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { runCatching { wss = Repo.workspaces().filter { it.first.privateOwner == null && it.second != "gast" }.map { it.first } }.onFailure(onError) }
    LaunchedEffect(ws?.id) { ws?.let { w -> runCatching { projects = Repo.projectsAll(w.id).filter { it.status == "active" && !it.archived } }.onFailure(onError) } }
    LaunchedEffect(project?.id) { project?.let { p -> runCatching { columns = Repo.columns(p.id); column = columns.firstOrNull { !it.isDone } }.onFailure(onError) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⚓ An Bord bringen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Umgebung", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { wss.forEach { w -> FilterChip(ws?.id == w.id, { ws = w; project = null }, { Text(w.name) }) } }
                if (projects.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp)); Text("Projekt", fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { projects.forEach { p -> FilterChip(project?.id == p.id, { project = p }, { Text(p.name) }) } }
                }
                if (columns.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp)); Text("Spalte", fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { columns.forEach { c -> FilterChip(column?.id == c.id, { column = c }, { Text(c.name) }) } }
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(keepNotes, { keepNotes = it }); Text("Notizen und Kommentare mitnehmen") }
            }
        },
        confirmButton = {
            TextButton(enabled = project != null && !busy, onClick = {
                val p = project ?: return@TextButton
                busy = true
                scope.launch { runCatching { Repo.bringAboard(card.id, p.id, column?.id, keepNotes) }.onSuccess { onDone() }.onFailure { busy = false; onError(it) } }
            }) { Text("An Bord bringen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

