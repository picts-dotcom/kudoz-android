package ch.kudoz.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.kudoz.app.data.Repo
import ch.kudoz.app.data.Workspace
import kotlinx.coroutines.launch

private val WS_COLORS = listOf("mint", "peach", "lavender", "sky", "lemon", "rose", "teal", "coral", "lilac", "lime", "ocean", "silver", "apricot", "berry", "pistachio", "aqua", "periwinkle", "blush", "sand", "mauve", "forest")

// Gleiche Beschriftungen wie src/lib/guest-permissions.ts und src/lib/workspace-permissions.ts.
private val GUEST_LABELS = linkedMapOf(
    "claim_tasks" to "Aufgaben und Checklistenpunkte übernehmen",
    "complete_tasks" to "Aufgaben und Checklistenpunkte abhaken",
    "comment_tasks" to "Kommentare schreiben",
    "move_tasks" to "Aufgaben auf dem Board verschieben",
    "invite_guests" to "Andere Gäste ins Projekt einladen",
)
private val KERN_LABELS = linkedMapOf(
    "invite_guests" to "Gäste einladen und Projektzugänge verwalten",
    "invite_kern" to "Kernmitarbeitende einladen",
    "appoint_kern" to "Gäste zu Kernmitarbeitenden bestimmen",
    "create_projects" to "Projekte erstellen",
    "edit_projects" to "Projekte bearbeiten und sortieren",
    "archive_projects" to "Projekte archivieren, pausieren und reaktivieren",
    "delete_projects" to "Projekte löschen",
)
private val ARCHIVE_DAYS = listOf(0 to "Nie", 7 to "7 Tage", 14 to "14 Tage", 30 to "30 Tage", 60 to "60 Tage")

/** Einstellungen der Umgebung (nur Admin): Name, Farbe, Aufräumen, Rechte von Gästen und Kern. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkspaceSettingsScreen(ws: Workspace, onGone: () -> Unit, onError: (Throwable) -> Unit) {
    var memberProfiles by remember { mutableStateOf<List<ch.kudoz.app.data.Profile>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var loaded by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(ws.name) }
    var color by remember { mutableStateOf(ws.color) }
    var days by remember { mutableStateOf(14) }
    var saved by remember { mutableStateOf(false) }
    var guest by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var kern by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    LaunchedEffect(ws.id) {
        runCatching {
            val row = Repo.workspaceRow(ws.id)
            name = row.name; color = row.color; days = row.autoArchiveDays
            guest = Repo.permissions("workspace_guest_permissions", ws.id)
            kern = Repo.permissions("workspace_kern_permissions", ws.id)
            memberProfiles = Repo.profiles(Repo.memberList(ws.id).map { it.userId }).values.sortedBy { it.label.lowercase() }
            loaded = true
        }.onFailure(onError)
    }
    if (!loaded) { SplashScreen(); return }

    fun toggle(table: String, current: Map<String, Boolean>, labels: Map<String, String>, key: String, on: Boolean, set: (Map<String, Boolean>) -> Unit) {
        val next = labels.keys.associateWith { current[it] ?: false } + (key to on)
        set(next)
        scope.launch { runCatching { Repo.setPermissions(table, ws.id, next) }.onFailure { set(current); onError(it) } }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Allgemein", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        item { OutlinedTextField(name, { name = it; saved = false }, label = { Text("Name der Umgebung") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WS_COLORS.forEach { c ->
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(paletteColors(c).first).clickable { color = c; saved = false },
                        contentAlignment = Alignment.Center,
                    ) { if (color == c) Text("✓", color = paletteColors(c).second, fontWeight = FontWeight.Bold) }
                }
            }
        }
        item {
            Text("Erledigtes automatisch archivieren nach", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ARCHIVE_DAYS.forEach { (d, l) -> FilterChip(selected = days == d, onClick = { days = d; saved = false }, label = { Text(l) }) }
            }
        }
        item {
            Button(enabled = name.isNotBlank() && !saved, onClick = {
                scope.launch { runCatching { Repo.updateWorkspace(ws.id, name, color, days) }.onSuccess { saved = true }.onFailure(onError) }
            }, modifier = Modifier.fillMaxWidth()) { Text(if (saved) "Gespeichert ✓" else "Speichern") }
        }
        item { HorizontalDivider() }
        item { Text("Was Gäste dürfen", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        GUEST_LABELS.forEach { (k, l) ->
            item(key = "g$k") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(l, Modifier.weight(1f))
                    Switch(guest[k] ?: false, { on -> toggle("workspace_guest_permissions", guest, GUEST_LABELS, k, on) { guest = it } })
                }
            }
        }
        item { HorizontalDivider() }
        item { Text("Was Kernmitarbeitende dürfen", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        KERN_LABELS.forEach { (k, l) ->
            item(key = "k$k") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(l, Modifier.weight(1f))
                    Switch(kern[k] ?: false, { on -> toggle("workspace_kern_permissions", kern, KERN_LABELS, k, on) { kern = it } })
                }
            }
        }
        item { HorizontalDivider() }
        item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { VisibilityBlock(ws, memberProfiles, onError) } }
        item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { JoinRequestsBlock(ws, onError) } }
        item { HorizontalDivider() }
        item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { DutyBlock(ws, onError) } }
        item { HorizontalDivider() }
        item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { IconAndDangerBlock(ws, onGone, onError) } }
    }
}
