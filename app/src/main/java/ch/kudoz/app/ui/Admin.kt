package ch.kudoz.app.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.kudoz.app.data.DirectoryShip
import ch.kudoz.app.data.DutySettings
import ch.kudoz.app.data.Invitation
import ch.kudoz.app.data.JoinRequest
import ch.kudoz.app.data.Member
import ch.kudoz.app.data.PendingInvite
import ch.kudoz.app.data.Profile
import ch.kudoz.app.data.ProjectFull
import ch.kudoz.app.data.Repo
import ch.kudoz.app.data.SearchHit
import ch.kudoz.app.data.SuperAdminRow
import ch.kudoz.app.data.SuperWs
import ch.kudoz.app.data.Visibility
import ch.kudoz.app.data.Workspace
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val ROLE_NAMES = linkedMapOf("admin" to "Admin", "kern" to "Kern", "gast" to "Gast")

// ---------------------------------------------------------------- Einladungen & neue Umgebung

@Composable
fun InvitationsBlock(invitations: List<Invitation>, onAccept: (Invitation) -> Unit, onDecline: (Invitation) -> Unit) {
    if (invitations.isEmpty()) return
    Text("📨 Einladungen", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
    invitations.forEach { inv ->
        KCard(Modifier.fillMaxWidth().padding(top = 6.dp), color = paletteColors(inv.color).first, border = false) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(inv.workspaceName, fontWeight = FontWeight.Bold, color = paletteColors(inv.color).second)
                    Text("als ${ROLE_NAMES[inv.role] ?: inv.role}", fontSize = 12.sp, color = paletteColors(inv.color).second)
                }
                TextButton(onClick = { onDecline(inv) }) { Text("Ablehnen") }
                Button(onClick = { onAccept(inv) }) { Text("Annehmen") }
            }
        }
    }
}

@Composable
fun NewWorkspaceDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("lavender") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neue Umgebung") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Name, z. B. «Team D8»") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                ColorPicker(PROJECT_COLORS, color) { color = it ?: "lavender" }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name, color) }) { Text("Erstellen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

// ---------------------------------------------------------------- Andere Schiffe

@Composable
fun DirectoryScreen(onError: (Throwable) -> Unit) {
    var version by remember { mutableIntStateOf(0) }
    var asking by remember { mutableStateOf<DirectoryShip?>(null) }
    val scope = rememberCoroutineScope()
    LoadingList(
        load = { Repo.directory() },
        onError = onError,
        key = version,
        empty = "Keine sichtbaren Umgebungen.",
        header = { Text("Umgebungen, die sich zeigen. Du kannst um Aufnahme bitten.", fontSize = 13.sp, color = KudozMuted) },
    ) { s: DirectoryShip ->
        KCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Chip(s.name, s.color)
                    Spacer(Modifier.weight(1f))
                    when (s.requestStatus) {
                        "pending" -> Text("Anfrage offen", fontSize = 12.sp, color = KudozMuted)
                        "declined" -> Text("Abgelehnt", fontSize = 12.sp, color = KudozMuted)
                        else -> if (s.allowJoin) TextButton(onClick = { asking = s }) { Text("Beitreten anfragen") }
                    }
                }
                s.description?.takeIf { it.isNotBlank() }?.let { Spacer(Modifier.height(6.dp)); Text(it, fontSize = 14.sp) }
            }
        }
    }
    asking?.let { s ->
        TextPrompt("Nachricht an «${s.name}»", "Anfrage senden", { asking = null }, { msg ->
            asking = null
            scope.launch { runCatching { Repo.requestJoin(s.id, msg) }.onFailure(onError); version++ }
        })
    }
}

// ---------------------------------------------------------------- Suche

@Composable
fun SearchScreen(onOpen: (SearchHit, ProjectFull) -> Unit, onError: (Throwable) -> Unit) {
    var query by remember { mutableStateOf("") }
    var projects by remember { mutableStateOf<List<ProjectFull>>(emptyList()) }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    LaunchedEffect(Unit) { runCatching { projects = Repo.allMyProjects(Repo.workspaces().map { it.first.id }) }.onFailure(onError) }
    LaunchedEffect(query, projects) {
        if (query.trim().length < 2) { hits = emptyList(); return@LaunchedEffect }
        delay(250)
        runCatching { hits = Repo.search(query, projects.map { it.id }) }.onFailure(onError)
    }
    val byId = projects.associateBy { it.id }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            OutlinedTextField(query, { query = it }, placeholder = { Text("Aufgabe suchen …") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        if (query.trim().length >= 2 && hits.isEmpty()) item { EmptyHint("Nichts gefunden.") }
        items(hits, key = { it.id }) { h ->
            val p = byId[h.projectId]
            KCard(Modifier.fillMaxWidth(), onClick = { p?.let { onOpen(h, it) } }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(h.title + if (h.done) " ✓" else "", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    p?.let { Chip(it.name, it.color) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Mitglieder verwalten (Admin)

@Composable
fun InviteDialog(onDismiss: () -> Unit, onInvite: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("kern") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Person einladen") },
        text = {
            Column {
                OutlinedTextField(email, { email = it }, label = { Text("E-Mail (@ruggenacher.ch)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ROLE_NAMES.forEach { (k, l) -> FilterChip(role == k, { role = k }, { Text(l) }) }
                }
            }
        },
        confirmButton = { TextButton(enabled = email.contains('@'), onClick = { onInvite(email, role) }) { Text("Einladen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
fun PendingInvitesBlock(list: List<PendingInvite>, onDelete: (PendingInvite) -> Unit) {
    if (list.isEmpty()) return
    Text("Offene Einladungen", fontWeight = FontWeight.Bold)
    list.forEach { i ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(i.email, Modifier.weight(1f), fontSize = 14.sp)
            Chip(ROLE_NAMES[i.role] ?: i.role, "silver")
            IconButton(onClick = { onDelete(i) }) { Icon(Icons.Default.Close, "Zurückziehen", tint = KudozMuted) }
        }
    }
}

@Composable
fun MemberActions(m: Member, name: String, isAdmin: Boolean, me: String?, onRemove: () -> Unit, onAppoint: () -> Unit, onGuestAccess: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    if (m.userId == me) return
    val canAppoint = !isAdmin && m.role == "gast"
    if (!isAdmin && !canAppoint) return
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, "Mehr zu $name") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (m.role == "gast") DropdownMenuItem(text = { Text("Projektzugänge …") }, onClick = { open = false; onGuestAccess() })
            if (canAppoint) DropdownMenuItem(text = { Text("Als Kern bestimmen") }, onClick = { open = false; onAppoint() })
            if (isAdmin) DropdownMenuItem(text = { Text("Aus Umgebung entfernen", color = MaterialTheme.colorScheme.error) }, onClick = { open = false; onRemove() })
        }
    }
}

@Composable
fun GuestAccessDialog(ws: Workspace, userId: String, name: String, onDismiss: () -> Unit, onError: (Throwable) -> Unit) {
    var projects by remember { mutableStateOf<List<ProjectFull>>(emptyList()) }
    var access by remember { mutableStateOf<Set<String>>(emptySet()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(userId) {
        runCatching {
            projects = Repo.projectsAll(ws.id).filter { !it.archived }
            access = Repo.guestProjects(userId, projects.map { it.id })
        }.onFailure(onError)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Projekte für $name") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Gäste sehen nur die angehakten Projekte.", fontSize = 13.sp, color = KudozMuted)
                projects.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(p.id in access, { on ->
                            access = if (on) access + p.id else access - p.id
                            scope.launch { runCatching { Repo.setGuestAccess(p.id, userId, on) }.onFailure { access = if (on) access - p.id else access + p.id; onError(it) } }
                        })
                        Text(p.name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fertig") } },
    )
}

// ---------------------------------------------------------------- Einstellungen: Sichtbarkeit, Anfragen, Dienstplan, Gefahrenzone

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisibilityBlock(ws: Workspace, members: List<Profile>, onError: (Throwable) -> Unit) {
    var v by remember { mutableStateOf<Visibility?>(null) }
    var saved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(ws.id) { runCatching { v = Repo.visibility(ws.id) }.onFailure(onError) }
    val cur = v ?: return
    Text("Sichtbarkeit für andere", fontWeight = FontWeight.Bold, fontSize = 18.sp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Bei «Andere Schiffe» zeigen", Modifier.weight(1f)); Switch(cur.visible, { v = cur.copy(visible = it); saved = false })
    }
    if (cur.visible) {
        OutlinedTextField(cur.description.orEmpty(), { v = cur.copy(description = it); saved = false }, label = { Text("Kurzbeschreibung") }, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Beitrittsanfragen erlauben", Modifier.weight(1f)); Switch(cur.allowJoin, { v = cur.copy(allowJoin = it); saved = false })
        }
        Text("Welche Rollen sind sichtbar?", fontSize = 13.sp, color = KudozMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ROLE_NAMES.forEach { (k, l) ->
                FilterChip(k in cur.showRoles, { v = cur.copy(showRoles = if (k in cur.showRoles) cur.showRoles - k else cur.showRoles + k); saved = false }, { Text(l) })
            }
        }
        if (members.isNotEmpty()) {
            Text("Wer erhält Anfragen und Aufgaben anderer Umgebungen?", fontSize = 13.sp, color = KudozMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                members.forEach { p ->
                    FilterChip(p.id in cur.inboxUserIds, {
                        v = cur.copy(inboxUserIds = if (p.id in cur.inboxUserIds) cur.inboxUserIds - p.id else cur.inboxUserIds + p.id); saved = false
                    }, { Text(p.label) })
                }
            }
        }
    }
    Button(enabled = !saved, onClick = {
        scope.launch { runCatching { Repo.setVisibility(ws.id, cur) }.onSuccess { saved = true }.onFailure(onError) }
    }, modifier = Modifier.fillMaxWidth()) { Text(if (saved) "Gespeichert ✓" else "Sichtbarkeit speichern") }
}

@Composable
fun JoinRequestsBlock(ws: Workspace, onError: (Throwable) -> Unit) {
    var list by remember { mutableStateOf<List<JoinRequest>>(emptyList()) }
    var people by remember { mutableStateOf<Map<String, Profile>>(emptyMap()) }
    var version by remember { mutableIntStateOf(0) }
    var declining by remember { mutableStateOf<JoinRequest?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(ws.id, version) {
        runCatching { list = Repo.joinRequests(ws.id); people = Repo.profiles(list.map { it.userId }) }.onFailure(onError)
    }
    if (list.isEmpty()) return
    Text("Beitrittsanfragen", fontWeight = FontWeight.Bold, fontSize = 18.sp)
    list.forEach { r ->
        var role by remember(r.id) { mutableStateOf("gast") }
        KCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(people[r.userId], 28); Spacer(Modifier.width(8.dp))
                    Text(people[r.userId]?.label ?: "…", fontWeight = FontWeight.Bold)
                }
                r.message?.takeIf { it.isNotBlank() }?.let { Text("«$it»", fontSize = 14.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ROLE_NAMES.forEach { (k, l) -> FilterChip(role == k, { role = k }, { Text(l) }) } }
                Row {
                    TextButton(onClick = { declining = r }) { Text("Ablehnen") }
                    Button(onClick = { scope.launch { runCatching { Repo.decideJoin(r.id, true, role, "") }.onFailure(onError); version++ } }) { Text("Aufnehmen") }
                }
            }
        }
    }
    declining?.let { r ->
        TextPrompt("Begründung (optional sichtbar für die Person)", "Ablehnen", { declining = null }, { reason ->
            declining = null; scope.launch { runCatching { Repo.decideJoin(r.id, false, "gast", reason) }.onFailure(onError); version++ }
        })
    }
}

@Composable
fun DutyBlock(ws: Workspace, onError: (Throwable) -> Unit) {
    var d by remember { mutableStateOf<DutySettings?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(ws.id) { runCatching { d = Repo.duty(ws.id) }.onFailure(onError) }
    val cur = d ?: return
    fun save(n: DutySettings) { d = n; scope.launch { runCatching { Repo.setDuty(ws.id, n) }.onFailure { d = cur; onError(it) } } }
    Text("Dienstplan", fontWeight = FontWeight.Bold, fontSize = 18.sp)
    Row(verticalAlignment = Alignment.CenterVertically) { Text("Board «Dienste» verwenden", Modifier.weight(1f)); Switch(cur.enabled, { save(cur.copy(enabled = it)) }) }
    if (cur.enabled) Row(verticalAlignment = Alignment.CenterVertically) { Text("Gäste sehen den Dienstplan", Modifier.weight(1f)); Switch(cur.guestsSee, { save(cur.copy(guestsSee = it)) }) }
}

private val WS_ICONS = listOf("sparkles", "rocket", "star", "heart", "users", "school", "laptop", "book-open", "trophy", "lightbulb", "ship", "anchor", "compass", "flag", "puzzle", "palette")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconAndDangerBlock(ws: Workspace, onGone: () -> Unit, onError: (Throwable) -> Unit) {
    var icon by remember { mutableStateOf(ws.icon) }
    var confirm by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Text("Symbol", fontWeight = FontWeight.Bold, fontSize = 18.sp)
    Text("Das Symbol erscheint im Web. Weitere 150 Symbole gibt es dort.", fontSize = 12.sp, color = KudozMuted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        WS_ICONS.forEach { i ->
            FilterChip(icon == i, { icon = i; scope.launch { runCatching { Repo.setWorkspaceIcon(ws.id, i) }.onFailure(onError) } }, { Text(i) })
        }
    }
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Text("Gefahrenzone", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.error)
    OutlinedButton(onClick = { confirm = "leave" }, modifier = Modifier.fillMaxWidth()) { Text("Umgebung verlassen") }
    OutlinedButton(onClick = { confirm = "delete" }, modifier = Modifier.fillMaxWidth()) { Text("Umgebung löschen", color = MaterialTheme.colorScheme.error) }
    confirm?.let { kind ->
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (kind == "delete") "«${ws.name}» löschen?" else "«${ws.name}» verlassen?") },
            text = {
                Column {
                    Text(
                        if (kind == "delete") "Alle Projekte, Aufgaben und Bounties dieser Umgebung werden endgültig gelöscht. Tippe zur Bestätigung den Namen ein."
                        else "Du verlierst den Zugriff, bis dich jemand wieder einlädt.",
                    )
                    if (kind == "delete") OutlinedTextField(typed, { typed = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            },
            confirmButton = {
                TextButton(enabled = kind != "delete" || typed.trim() == ws.name.trim(), onClick = {
                    confirm = null
                    scope.launch {
                        runCatching { if (kind == "delete") Repo.deleteWorkspace(ws.id) else Repo.leaveWorkspace(ws.id) }
                            .onSuccess { onGone() }.onFailure(onError)
                    }
                }) { Text(if (kind == "delete") "Endgültig löschen" else "Verlassen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Abbrechen") } },
        )
    }
}

// ---------------------------------------------------------------- Superadmin

@Composable
fun SuperadminScreen(onError: (Throwable) -> Unit) {
    var version by remember { mutableIntStateOf(0) }
    var wss by remember { mutableStateOf<List<SuperWs>?>(null) }
    var admins by remember { mutableStateOf<List<SuperAdminRow>>(emptyList()) }
    var setAdminFor by remember { mutableStateOf<SuperWs?>(null) }
    var deleting by remember { mutableStateOf<SuperWs?>(null) }
    var adding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun act(b: suspend () -> Unit) = scope.launch { runCatching { b() }.onFailure(onError); version++ }
    LaunchedEffect(version) { runCatching { wss = Repo.superWorkspaces(); admins = Repo.superList() }.onFailure(onError) }
    val list = wss ?: run { SplashScreen(); return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("🛡️ Superadmin", fontSize = 24.sp, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold) }
        item { Text("Alle Umgebungen (${list.size})", fontWeight = FontWeight.Bold) }
        items(list, key = { it.id }) { w ->
            KCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Chip(w.name, w.color); if (w.isDemo) { Spacer(Modifier.width(6.dp)); Chip("Demo", "silver") } }
                        Text("${w.memberCount} Mitglieder · Admin: ${w.admins.joinToString(", ").ifBlank { "–" }}", fontSize = 12.sp, color = KudozMuted)
                    }
                    var open by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, "Mehr") }
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            DropdownMenuItem(text = { Text("Admin festlegen …") }, onClick = { open = false; setAdminFor = w })
                            DropdownMenuItem(text = { Text("Löschen", color = MaterialTheme.colorScheme.error) }, onClick = { open = false; deleting = w })
                        }
                    }
                }
            }
        }
        item { HorizontalDivider() }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Superadmins", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { adding = true }) { Text("+ Hinzufügen") }
            }
        }
        items(admins, key = { it.email }) { a ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.email, Modifier.weight(1f))
                if (a.permanent) Chip("fest", "lavender")
                else IconButton(onClick = { act { Repo.superRemove(a.email) } }) { Icon(Icons.Default.Close, "Entfernen", tint = KudozMuted) }
            }
        }
    }
    setAdminFor?.let { w ->
        TextPrompt("Admin für «${w.name}» (E-Mail)", "Festlegen", { setAdminFor = null }, { email -> setAdminFor = null; act { Repo.superSetAdmin(w.id, email) } })
    }
    if (adding) TextPrompt("Superadmin hinzufügen (E-Mail)", "Hinzufügen", { adding = false }, { email -> adding = false; act { Repo.superAdd(email) } })
    deleting?.let { w ->
        ConfirmDialog("«${w.name}» löschen?", "Die Umgebung wird mit allen Inhalten endgültig gelöscht.", "Endgültig löschen",
            onConfirm = { deleting = null; act { Repo.superDeleteWorkspace(w.id) } }, onDismiss = { deleting = null })
    }
}

