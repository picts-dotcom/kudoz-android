package ch.kudoz.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.kudoz.app.data.BoardColumn
import ch.kudoz.app.data.Card
import ch.kudoz.app.data.ChecklistItem
import ch.kudoz.app.data.Comment
import ch.kudoz.app.data.Profile
import ch.kudoz.app.data.Project
import ch.kudoz.app.data.Repo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(project: Project, cardId: String, onBack: () -> Unit, onError: (Throwable) -> Unit) {
    var card by remember { mutableStateOf<Card?>(null) }
    var column by remember { mutableStateOf<BoardColumn?>(null) }
    var checklist by remember { mutableStateOf<List<ChecklistItem>>(emptyList()) }
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var people by remember { mutableStateOf<Map<String, Profile>>(emptyMap()) }
    var draft by remember { mutableStateOf("") }
    var newItem by remember { mutableStateOf<Boolean>(false) }
    var waitingPrompt by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        runCatching {
            val c = Repo.card(cardId)
            val cl = Repo.checklist(cardId)
            val cm = Repo.comments(cardId)
            column = Repo.columns(c.projectId).firstOrNull { it.id == c.columnId }
            people = Repo.profiles(c.assignees.map { it.userId } + cm.map { it.authorId } + cl.mapNotNull { it.assignedTo })
            card = c; checklist = cl; comments = cm
        }.onFailure(onError)
    }
    fun act(block: suspend () -> Unit) = scope.launch { runCatching { block() }.onFailure(onError); reload() }

    LaunchedEffect(cardId) { reload() }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = { Text(project.name, fontSize = 16.sp) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
        )
        val c = card ?: run { SplashScreen(); return@Column }
        val me = Repo.userId
        val mine = c.assignees.any { it.userId == me }

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    column?.let { Chip(it.name, it.color ?: project.color) }
                    dueInfo(c.dueDate)?.let { (t, col) -> Chip("📅 $t", col) }
                    if (c.done) Chip("✓ erledigt", "mint")
                }
                Spacer(Modifier.height(10.dp))
                Text(c.title, fontSize = 24.sp, fontWeight = FontWeight.Black)
                c.description?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                c.waitingText?.let {
                    Spacer(Modifier.height(10.dp))
                    Surface(color = paletteColors("peach").first, shape = RoundedCornerShape(12.dp)) {
                        Text("⏳ Wartet: $it", Modifier.padding(12.dp), color = paletteColors("peach").second)
                    }
                }
                if (c.followLocked) {
                    Spacer(Modifier.height(10.dp))
                    Surface(color = paletteColors("silver").first, shape = RoundedCornerShape(12.dp)) {
                        Text("🔒 Diese Folgeaufgabe startet erst, wenn die vorherige erledigt ist.", Modifier.padding(12.dp))
                    }
                }
            }
            item {
                Text("Verantwortlich", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                if (c.assignees.isEmpty()) Text("Noch niemand", color = MaterialTheme.colorScheme.onSurfaceVariant)
                c.assignees.forEach { a ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                        Avatar(people[a.userId]); Spacer(Modifier.width(10.dp)); Text(people[a.userId]?.label ?: "…")
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { act { Repo.claim(c.id, !mine) } }) {
                        Icon(Icons.Default.PanTool, null); Spacer(Modifier.width(6.dp))
                        Text(if (mine) "Abgeben" else "Übernehmen")
                    }
                    Button(
                        onClick = { act { Repo.setDone(c.id, !c.done) } },
                        colors = if (c.done) ButtonDefaults.buttonColors() else
                            ButtonDefaults.buttonColors(containerColor = paletteColors("mint").second),
                    ) {
                        Icon(Icons.Default.Check, null); Spacer(Modifier.width(6.dp))
                        Text(if (c.done) "Wieder öffnen" else "Erledigt")
                    }
                }
                TextButton(onClick = {
                    if (c.waitingText != null) act { Repo.setWaiting(c.id, "") } else waitingPrompt = true
                }) { Text(if (c.waitingText != null) "Warten beenden" else "⏳ Auf etwas warten …") }
            }
            item {
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val done = checklist.count { it.done }
                    Text(
                        "Checkliste" + if (checklist.isNotEmpty()) "  $done/${checklist.size}" else "",
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { newItem = true }) { Icon(Icons.Default.Add, "Punkt hinzufügen") }
                }
            }
            items(checklist, key = { it.id }) { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = item.done, onCheckedChange = { v -> act { Repo.toggleChecklist(item.id, v) } })
                    Text(item.body, Modifier.weight(1f))
                    item.assignedTo?.let { Avatar(people[it], 22) }
                }
            }
            item {
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Kommentare", fontWeight = FontWeight.Bold)
                if (comments.isEmpty()) Text("Noch keine Kommentare.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(comments, key = { it.id }) { cm ->
                Row(verticalAlignment = Alignment.Top) {
                    Avatar(people[cm.authorId])
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Row {
                            Text(people[cm.authorId]?.label ?: "…", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(relativeTime(cm.createdAt), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(cm.body)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                draft, { draft = it }, Modifier.weight(1f),
                placeholder = { Text("Kommentar schreiben …") }, maxLines = 4, shape = RoundedCornerShape(20.dp),
            )
            IconButton(enabled = draft.isNotBlank(), onClick = {
                val body = draft; draft = ""
                act { Repo.addComment(c.id, body) }
            }) { Icon(Icons.AutoMirrored.Filled.Send, "Senden") }
        }

        if (newItem) TextPrompt("Neuer Checklisten-Punkt", "Hinzufügen", { newItem = false }, { body ->
            newItem = false
            act { Repo.addChecklistItem(c, body, (checklist.maxOfOrNull { it.position } ?: 0.0) + 1.0) }
        })
        if (waitingPrompt) TextPrompt("Worauf wartest du?", "Speichern", { waitingPrompt = false }, { t ->
            waitingPrompt = false
            act { Repo.setWaiting(c.id, t) }
        })
    }
}
