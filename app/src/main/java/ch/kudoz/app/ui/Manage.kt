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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.kudoz.app.data.BoardColumn
import ch.kudoz.app.data.Milestone
import ch.kudoz.app.data.MilestoneCard
import ch.kudoz.app.data.Project
import ch.kudoz.app.data.ProjectFull
import ch.kudoz.app.data.Repo
import ch.kudoz.app.data.Rewards
import ch.kudoz.app.data.Workspace
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

val PROJECT_COLORS = listOf("mint", "peach", "lavender", "sky", "lemon", "rose", "teal", "coral", "lilac", "lime", "ocean", "silver", "apricot", "berry", "pistachio", "aqua", "periwinkle", "blush", "sand", "mauve", "forest")
val PASTELS = listOf("mint", "peach", "lavender", "sky", "lemon", "rose")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(colors: List<String>, selected: String?, allowNone: Boolean = false, onPick: (String?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (allowNone) Box(
            Modifier.size(32.dp).clip(CircleShape).background(KudozMutedBg).clickable { onPick(null) },
            contentAlignment = Alignment.Center,
        ) { if (selected == null) Text("✓", fontWeight = FontWeight.Bold) else Text("–", color = KudozMuted) }
        colors.forEach { c ->
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(paletteColors(c).first).clickable { onPick(c) },
                contentAlignment = Alignment.Center,
            ) { if (selected == c) Text("✓", color = paletteColors(c).second, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, value: String?, onChange: (String?) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { picking = true }) { Text("📅 " + (dueInfo(value)?.first ?: label)) }
        if (value != null) TextButton(onClick = { onChange(null) }) { Text("Entfernen") }
    }
    if (picking) {
        val init = value?.let { runCatching { LocalDate.parse(it.take(10)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull() }
        val st = rememberDatePickerState(initialSelectedDateMillis = init ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                    picking = false
                }) { Text("OK") }
            },
        ) { DatePicker(st) }
    }
}

// ---------------------------------------------------------------- Projekte verwalten

private val STATUS_LABEL = mapOf("active" to "Aktiv", "paused" to "Pausiert", "archived" to "Archiviert")

/** Projekte der Umgebung: öffnen, anlegen, bearbeiten, sortieren, pausieren, archivieren, löschen. */
@Composable
fun ProjectsManageScreen(ws: Workspace, onOpen: (Project) -> Unit, onError: (Throwable) -> Unit) {
    var version by remember { mutableIntStateOf(0) }
    var list by remember { mutableStateOf<List<ProjectFull>?>(null) }
    var editing by remember { mutableStateOf<ProjectFull?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ProjectFull?>(null) }
    var showInactive by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun act(b: suspend () -> Unit) = scope.launch { runCatching { b() }.onFailure(onError); version++ }
    LaunchedEffect(ws.id, version) { runCatching { list = Repo.projectsAll(ws.id).filter { !it.isBoardDuties } }.onFailure(onError) }
    val all = list ?: run { SplashScreen(); return }
    val active = all.filter { it.status == "active" && !it.archived }
    val inactive = all.filter { it.status != "active" || it.archived }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (active.isEmpty()) item { EmptyHint("Noch keine aktiven Projekte.") }
            items(active, key = { it.id }) { p ->
                ProjectRow(p, onOpen = { onOpen(p.toProject()) }, menu = { close ->
                    DropdownMenuItem(text = { Text("Bearbeiten") }, onClick = { close(); editing = p })
                    val i = active.indexOf(p)
                    if (i > 0) DropdownMenuItem(text = { Text("Nach oben") }, onClick = {
                        close(); act { Repo.reorderProjects(ws.id, active.map { it.id }.toMutableList().apply { add(i - 1, removeAt(i)) }) }
                    })
                    if (i < active.lastIndex) DropdownMenuItem(text = { Text("Nach unten") }, onClick = {
                        close(); act { Repo.reorderProjects(ws.id, active.map { it.id }.toMutableList().apply { add(i + 1, removeAt(i)) }) }
                    })
                    DropdownMenuItem(text = { Text("Pausieren") }, onClick = { close(); act { Repo.setProjectStatus(p.id, "paused") } })
                    DropdownMenuItem(text = { Text("Archivieren") }, onClick = { close(); act { Repo.setProjectStatus(p.id, "archived") } })
                    DropdownMenuItem(text = { Text("Löschen", color = MaterialTheme.colorScheme.error) }, onClick = { close(); deleting = p })
                })
            }
            if (inactive.isNotEmpty()) item {
                TextButton(onClick = { showInactive = !showInactive }) {
                    Text((if (showInactive) "▾ " else "▸ ") + "Pausiert & archiviert (${inactive.size})", color = KudozMuted)
                }
            }
            if (showInactive) items(inactive, key = { "i" + it.id }) { p ->
                ProjectRow(p, onOpen = { onOpen(p.toProject()) }, menu = { close ->
                    DropdownMenuItem(text = { Text("Reaktivieren") }, onClick = { close(); act { Repo.setProjectStatus(p.id, "active") } })
                    DropdownMenuItem(text = { Text("Löschen", color = MaterialTheme.colorScheme.error) }, onClick = { close(); deleting = p })
                })
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            icon = { Icon(Icons.Default.Add, null) }, text = { Text("Projekt") },
            containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }

    if (creating) ProjectDialog(null, onDismiss = { creating = false }) { name, desc, color ->
        creating = false; act { Repo.createProject(ws.id, name, color, desc) }
    }
    editing?.let { p ->
        ProjectDialog(p, onDismiss = { editing = null }) { name, desc, color -> editing = null; act { Repo.updateProject(p.id, name, desc, color) } }
    }
    deleting?.let { p ->
        ConfirmDialog("«${p.name}» löschen?", "Das Projekt und alle Aufgaben darin werden endgültig gelöscht. Das lässt sich nicht rückgängig machen.", "Endgültig löschen",
            onConfirm = { deleting = null; act { Repo.deleteProject(p.id) } }, onDismiss = { deleting = null })
    }
}

private fun ProjectFull.toProject() = Project(id = id, name = name, color = color, icon = icon, description = description, position = position, archived = archived, workspaceId = workspaceId)

@Composable
private fun ProjectRow(p: ProjectFull, onOpen: () -> Unit, menu: @Composable (close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val (bg, ink) = paletteColors(p.color)
    KCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(bg), contentAlignment = Alignment.Center) {
                Text(p.name.take(1).uppercase(), color = ink, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                p.description?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 13.sp, color = KudozMuted, maxLines = 1) }
            }
            if (p.status != "active" || p.archived) Chip(STATUS_LABEL[if (p.archived) "archived" else p.status] ?: p.status, "silver")
            Box {
                IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, "Mehr") }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) { menu { open = false } }
            }
        }
    }
}

@Composable
private fun ProjectDialog(p: ProjectFull?, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf(p?.name.orEmpty()) }
    var desc by remember { mutableStateOf(p?.description.orEmpty()) }
    var color by remember { mutableStateOf(p?.color ?: "mint") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (p == null) "Neues Projekt" else "Projekt bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(desc, { desc = it }, label = { Text("Beschreibung (optional)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                ColorPicker(PROJECT_COLORS, color) { color = it ?: "mint" }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, desc, color) }) { Text(if (p == null) "Anlegen" else "Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

// ---------------------------------------------------------------- Spalten verwalten

@Composable
fun ColumnsDialog(projectId: String, columns: List<BoardColumn>, onDismiss: () -> Unit, onChanged: () -> Unit, onError: (Throwable) -> Unit) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<BoardColumn?>(null) }
    var deleting by remember { mutableStateOf<BoardColumn?>(null) }
    fun act(b: suspend () -> Unit) = scope.launch { runCatching { b() }.onFailure(onError); onChanged() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Spalten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                columns.forEachIndexed { i, c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).clip(CircleShape).background(c.color?.let { paletteColors(it).first } ?: KudozMutedBg))
                        Spacer(Modifier.width(8.dp))
                        Text(c.name + if (c.isDone) " ✓" else "", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        IconButton(enabled = i > 0, onClick = { act { Repo.swapColumns(c, columns[i - 1]) } }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Nach links") }
                        IconButton(enabled = i < columns.lastIndex, onClick = { act { Repo.swapColumns(c, columns[i + 1]) } }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Nach rechts") }
                        IconButton(onClick = { editing = c }) { Icon(Icons.Default.Edit, "Bearbeiten") }
                    }
                }
                TextButton(onClick = { act { Repo.addColumn(projectId, "Neue Spalte", (columns.maxOfOrNull { it.position } ?: 0.0) + 1.0) } }) { Text("+ Spalte hinzufügen") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fertig") } },
    )
    editing?.let { c ->
        var name by remember(c.id) { mutableStateOf(c.name) }
        var color by remember(c.id) { mutableStateOf(c.color) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Spalte bearbeiten") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    ColorPicker(PASTELS, color, allowNone = true) { color = it }
                }
            },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { editing = null; act { Repo.updateColumn(c.id, name, color) } }) { Text("Speichern") } },
            dismissButton = { TextButton(onClick = { editing = null; deleting = c }) { Text("Löschen", color = MaterialTheme.colorScheme.error) } },
        )
    }
    deleting?.let { c ->
        ConfirmDialog("Spalte «${c.name}» löschen?", "Aufgaben in dieser Spalte werden dabei mitgelöscht.", "Löschen",
            onConfirm = { deleting = null; act { Repo.deleteColumn(c.id) } }, onDismiss = { deleting = null })
    }
}

// ---------------------------------------------------------------- Meilensteine bearbeiten

@Composable
fun MilestoneEditDialog(m: Milestone?, onDismiss: () -> Unit, onSave: (String, String?, String) -> Unit, onDelete: (() -> Unit)?) {
    var title by remember { mutableStateOf(m?.title.orEmpty()) }
    var due by remember { mutableStateOf(m?.dueDate?.take(10)) }
    var desc by remember { mutableStateOf(m?.description.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (m == null) "Neuer Meilenstein" else "Meilenstein bearbeiten") },
        text = {
            Column {
                OutlinedTextField(title, { title = it }, label = { Text("Titel") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(desc, { desc = it }, label = { Text("Beschreibung (optional)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                DateField("Datum wählen", due) { due = it }
            }
        },
        confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { onSave(title, due, desc) }) { Text("Speichern") } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
}

// ---------------------------------------------------------------- Belohnung aus der Schatzkammer

/** Doppelte Gegenstände aus der eigenen Sammlung (das letzte Exemplar bleibt geschützt). */
@Composable
fun TreasuryPicker(selected: Int?, onPick: (Int) -> Unit, onError: (Throwable) -> Unit) {
    val context = LocalContext.current
    var counts by remember { mutableStateOf<Map<Int, Int>?>(null) }
    LaunchedEffect(Unit) { runCatching { counts = Repo.treasury() }.onFailure(onError) }
    val c = counts ?: run { Text("Sammlung lädt …"); return }
    val dups = c.filterValues { it > 1 }.keys.sorted()
    if (dups.isEmpty()) { Text("Du hast noch keine doppelten Gegenstände zum Verschenken.", fontSize = 13.sp, color = KudozMuted); return }
    LazyVerticalGrid(GridCells.Fixed(4), Modifier.height(220.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(dups) { id ->
            Box(
                Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (selected == id) KudozBounty else Color.Transparent).padding(3.dp).clickable { onPick(id) },
            ) {
                RewardImage(id, Modifier.fillMaxSize())
                Text("×${c[id]}", Modifier.align(Alignment.TopEnd).padding(2.dp).clip(RoundedCornerShape(50)).background(KudozBountySoft).padding(horizontal = 4.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = KudozBountyInk)
            }
        }
    }
    selected?.let { Text("Gewählt: ${Rewards.label(context, it)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}

