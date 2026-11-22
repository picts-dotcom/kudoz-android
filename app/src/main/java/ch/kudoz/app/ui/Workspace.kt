package ch.kudoz.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.kudoz.app.data.Bounty
import ch.kudoz.app.data.Profile
import ch.kudoz.app.data.Project
import ch.kudoz.app.data.Repo
import ch.kudoz.app.data.Shoutout
import ch.kudoz.app.data.Workspace
import kotlinx.coroutines.launch

/** Umgebung mit Reitern: Projekte, Bounties, Shoutouts, Eingang, Mitglieder. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(ws: Workspace, role: String?, onOpenProject: (Project) -> Unit, onError: (Throwable) -> Unit) {
    var tab by remember(ws.id) { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        PillTabs(
            listOf("Projekte", "Bounties", "Shoutouts", "Eingang", "Mitglieder") + if (role == "admin") listOf("Einstellungen") else emptyList(),
            tab,
            { tab = it },
        )
        when (tab) {
            0 -> ProjectsScreen(ws, onOpen = onOpenProject, onError = onError)
            1 -> BountiesScreen(ws, role, onError)
            2 -> ShoutoutsScreen(ws, onError)
            3 -> InboxScreen(ws, onError)
            4 -> MembersScreen(ws, role, onError)
            else -> WorkspaceSettingsScreen(ws, onError)
        }
    }
}

// ---------------------------------------------------------------- Bounties

private val BOUNTY_FILTERS = listOf("open" to "Offen", "claimed" to "In Arbeit", "done" to "Erledigt")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BountiesScreen(ws: Workspace, role: String?, onError: (Throwable) -> Unit) {
    var filter by remember { mutableStateOf("open") }
    var version by remember { mutableIntStateOf(0) }
    var people by remember { mutableStateOf<Map<String, Profile>>(emptyMap()) }
    val scope = rememberCoroutineScope()
    val me = Repo.userId
    var all by remember { mutableStateOf<List<Bounty>>(emptyList()) }
    var creating by remember { mutableStateOf(false) }

    fun act(block: suspend () -> Unit) = scope.launch { runCatching { block() }.onFailure(onError); version++ }

    Box(Modifier.fillMaxSize()) {
        LoadingList(
            load = {
                Repo.bounties(ws.id).also { list ->
                    all = list
                    people = Repo.profiles(list.flatMap { b -> listOfNotNull(b.claimedBy, b.createdBy) + b.helpers.map { it.userId } }.toSet())
                }.filter { it.status == filter }
            },
            onError = onError,
            key = "$filter/$version",
            empty = if (filter == "open") "Gerade ist nichts offen." else "Hier ist noch nichts.",
            header = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BOUNTY_FILTERS.forEach { (k, label) ->
                        FilterChip(
                            selected = filter == k, onClick = { filter = k },
                            label = { Text("$label ${all.count { it.status == k }.takeIf { all.isNotEmpty() } ?: ""}") },
                        )
                    }
                }
            },
        ) { b ->
            val isBounty = b.kind == "bounty"
            val mine = b.createdBy == me
            val helping = b.helpers.any { it.userId == me }
            val admin = role == "admin"
            KCard(modifier = Modifier.fillMaxWidth(), radius = 16) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isBounty && b.rewardImage != null) RewardImage(b.rewardImage, Modifier.size(52.dp))
                        else Text(if (isBounty) "🪙" else "🤝", fontSize = 26.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                (if (isBounty) "Bounty" else "Hilfe gesucht") + " · von ${people[b.createdBy]?.label ?: "…"}",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        b.claimedBy?.let { Avatar(people[it], 30) }
                        if (!isBounty && b.helpers.isNotEmpty()) AvatarStack(b.helpers.map { people[it.userId] })
                    }
                    b.description?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(6.dp)); Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (isBounty && mine && b.status != "done") {
                        Spacer(Modifier.height(4.dp))
                        Text("Eigene Bounty – keine Belohnung für dich.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (b.status == "done") {
                        Spacer(Modifier.height(4.dp))
                        Text("Erledigt ✓", fontWeight = FontWeight.Bold, color = paletteColors("mint").second, fontSize = 13.sp)
                    }
                    val actions = buildList<Pair<String, suspend () -> Unit>> {
                        if (isBounty && b.status == "open") add("Übernehmen" to { Repo.bountyClaim(b.id, true) })
                        if (!isBounty && b.status != "done") add((if (helping) "Hilfe beenden" else "Ich helfe") to { Repo.bountyClaim(b.id, !helping) })
                        if (!isBounty && b.status != "done" && (helping || mine || admin)) add("Hilfe abschliessen" to { Repo.bountyComplete(b.id) })
                        if (isBounty && b.status == "claimed" && (b.claimedBy == me || mine || admin)) {
                            add("Freigeben" to { Repo.bountyClaim(b.id, false) })
                            add("Erledigt" to { Repo.bountyComplete(b.id) })
                        }
                    }
                    if (actions.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            actions.forEachIndexed { i, (label, run) ->
                                if (i == actions.lastIndex) Button(
                                    onClick = { act(run) },
                                    colors = ButtonDefaults.buttonColors(containerColor = paletteColors("lemon").second),
                                ) { if (label.startsWith("Erledigt") || label.endsWith("abschliessen")) Icon(Icons.Default.Check, null); Text(label) }
                                else OutlinedButton(onClick = { act(run) }) { Text(label) }
                            }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Ausschreiben") },
            containerColor = paletteColors("lemon").second,
            contentColor = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }
    if (creating) BountyDialog(ws.id, onDismiss = { creating = false }, onCreated = { creating = false; filter = "open"; version++ }, onError = onError)
}

// ---------------------------------------------------------------- Shoutouts

private val EMOJIS = listOf("🎉", "🙌", "💪", "🌟", "❤️", "🚀", "🧠", "☕")

@Composable
fun ShoutoutsScreen(ws: Workspace, onError: (Throwable) -> Unit) {
    var version by remember { mutableIntStateOf(0) }
    var people by remember { mutableStateOf<Map<String, Profile>>(emptyMap()) }
    var writing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val me = Repo.userId

    Box(Modifier.fillMaxSize()) {
        LoadingList(
            load = {
                Repo.shoutouts(ws.id).also { l -> people = Repo.profiles(l.flatMap { listOf(it.fromId, it.toId) }.toSet()) }
            },
            onError = onError,
            key = version,
            empty = "Noch keine Shoutouts. Sag jemandem Danke! 🙌",
        ) { s: Shoutout ->
            KCard(modifier = Modifier.fillMaxWidth(), color = paletteColors(ws.color).first, border = false, radius = 16) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Text(s.emoji, fontSize = 30.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${people[s.fromId]?.label ?: "…"} → ${people[s.toId]?.label ?: "…"}",
                            fontWeight = FontWeight.Bold,
                        )
                        s.message?.let { Text(it) }
                        Text(relativeTime(s.createdAt), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (s.fromId == me) IconButton(onClick = {
                        scope.launch { runCatching { Repo.deleteShoutout(s.id) }.onFailure(onError); version++ }
                    }) { Icon(Icons.Default.DeleteOutline, "Löschen") }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { writing = true },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Shoutout") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }

    if (writing) ShoutoutDialog(ws, onDismiss = { writing = false }, onSent = { writing = false; version++ }, onError = onError)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShoutoutDialog(ws: Workspace, onDismiss: () -> Unit, onSent: () -> Unit, onError: (Throwable) -> Unit) {
    var members by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var to by remember { mutableStateOf<String?>(null) }
    var emoji by remember { mutableStateOf(EMOJIS.first()) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(ws.id) {
        runCatching {
            val ids = Repo.members(ws.id).map { it.userId }.filter { it != Repo.userId }
            members = Repo.profiles(ids).values.sortedBy { it.label.lowercase() }
        }.onFailure(onError)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Shoutout senden") },
        text = {
            Column {
                Text("An wen?", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    members.forEach { p ->
                        FilterChip(selected = to == p.id, onClick = { to = p.id }, label = { Text(p.label) }, leadingIcon = { Avatar(p, 20) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    EMOJIS.forEach { e ->
                        Box(
                            Modifier.size(34.dp).clip(CircleShape)
                                .background(if (e == emoji) paletteColors("lemon").first else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 18.sp) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(message, { message = it }, placeholder = { Text("Danke für … (optional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = to != null, onClick = {
                val target = to ?: return@TextButton
                scope.launch {
                    runCatching { Repo.sendShoutout(ws.id, target, emoji, message) }
                        .onSuccess { onSent() }.onFailure(onError)
                }
            }) { Text("Senden") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
