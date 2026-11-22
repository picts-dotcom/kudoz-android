package ch.kudoz.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import ch.kudoz.app.data.Card
import ch.kudoz.app.data.Profile
import ch.kudoz.app.data.Project
import ch.kudoz.app.data.Repo
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Titel, Beschreibung und Fälligkeit einer Aufgabe bearbeiten. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardEditDialog(card: Card, onDismiss: () -> Unit, onSaved: () -> Unit, onError: (Throwable) -> Unit) {
    var title by remember { mutableStateOf(card.title) }
    var description by remember { mutableStateOf(card.description.orEmpty()) }
    var due by remember { mutableStateOf(card.dueDate?.take(10)) }
    var picking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aufgabe bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(title, { title = it }, label = { Text("Titel") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(description, { description = it }, label = { Text("Beschreibung") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { picking = true }) { Text("📅 " + (dueInfo(due)?.first ?: "Fälligkeit wählen")) }
                    if (due != null) TextButton(onClick = { due = null }) { Text("Entfernen") }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && !busy, onClick = {
                busy = true
                scope.launch {
                    runCatching { Repo.updateCard(card.id, title, description, due) }
                        .onSuccess { onSaved() }.onFailure { busy = false; onError(it) }
                }
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )

    if (picking) {
        val initial = due?.let { runCatching { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull() }
        val state = rememberDatePickerState(initialSelectedDateMillis = initial ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { due = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Abbrechen") } },
        ) { DatePicker(state) }
    }
}

/** Person als verantwortlich hinzufügen (Mitglieder der Umgebung). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssigneePicker(card: Card, workspaceId: String, onDismiss: () -> Unit, onChanged: () -> Unit, onError: (Throwable) -> Unit) {
    var people by remember { mutableStateOf<List<Profile>>(emptyList()) }
    val current = card.assignees.map { it.userId }.toSet()
    val scope = rememberCoroutineScope()
    LaunchedEffect(workspaceId) {
        runCatching {
            people = Repo.profiles(Repo.memberList(workspaceId).map { it.userId }).values.sortedBy { it.label.lowercase() }
        }.onFailure(onError)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wer ist verantwortlich?") },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                people.forEach { p ->
                    val on = p.id in current
                    FilterChip(
                        selected = on,
                        onClick = {
                            scope.launch {
                                runCatching { if (on) Repo.removeAssignee(card, p.id) else Repo.addAssignee(card, p.id) }
                                    .onSuccess { onChanged() }.onFailure(onError)
                            }
                        },
                        label = { Text(p.label) },
                        leadingIcon = { Avatar(p, 20) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fertig") } },
    )
}

/** Bounty oder Hilferuf ausschreiben – Belohnung entsteht wie im Web zufällig. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BountyDialog(
    workspaceId: String,
    fixedProject: Project? = null,
    card: Card? = null,
    initialKind: String = "bounty",
    onDismiss: () -> Unit,
    onCreated: () -> Unit,
    onError: (Throwable) -> Unit,
) {
    var kind by remember { mutableStateOf(initialKind) }
    var title by remember { mutableStateOf(card?.title.orEmpty()) }
    var description by remember { mutableStateOf("") }
    var projects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var project by remember { mutableStateOf(fixedProject) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(workspaceId) { if (fixedProject == null) runCatching { projects = Repo.projects(workspaceId) }.onFailure(onError) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (kind == "bounty") "Bounty ausschreiben 🪙" else "Hilfe suchen 🤝") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("bounty" to "Bounty", "hilfe" to "Hilfe").forEachIndexed { i, (k, l) ->
                        SegmentedButton(selected = kind == k, onClick = { kind = k }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(l) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(title, { title = it }, label = { Text("Was ist zu tun?") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(description, { description = it }, label = { Text("Details (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                if (fixedProject == null && projects.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Projekt (optional)", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        projects.forEach { p -> FilterChip(selected = project?.id == p.id, onClick = { project = if (project?.id == p.id) null else p }, label = { Text(p.name) }) }
                    }
                }
                if (kind == "bounty") {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "🎲 Die Belohnung ist ein zufälliger Gegenstand. Eine Belohnung aus deiner Schatzkammer wählst du im Web.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && !busy, onClick = {
                busy = true
                scope.launch {
                    runCatching { Repo.createBounty(workspaceId, project?.id, card?.id, kind, title, description) }
                        .onSuccess { onCreated() }.onFailure { busy = false; onError(it) }
                }
            }) { Text("Ausschreiben") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

