package ch.kudoz.app.ui

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.kudoz.app.BuildConfig
import ch.kudoz.app.data.AuthFlow
import ch.kudoz.app.data.FleetBounty
import ch.kudoz.app.data.InboxCount
import ch.kudoz.app.data.Milestone
import ch.kudoz.app.data.MilestoneCard
import ch.kudoz.app.data.MyCard
import ch.kudoz.app.data.Profile
import ch.kudoz.app.data.Project
import ch.kudoz.app.data.Repo
import ch.kudoz.app.data.Workspace
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// ---------------------------------------------------------------- Flotte

private enum class DueBucket(val label: String, val color: String) {
    OVERDUE("Überfällig", "rose"), TODAY("Heute", "lemon"), WEEK("Diese Woche", "sky"), LATER("Später", "mint"), NONE("Ohne Datum", "silver")
}

private fun dueBucket(due: String?): DueBucket {
    val d = runCatching { LocalDate.parse(due?.take(10)) }.getOrNull() ?: return DueBucket.NONE
    val diff = ChronoUnit.DAYS.between(LocalDate.now(), d)
    return when {
        diff < 0 -> DueBucket.OVERDUE
        diff == 0L -> DueBucket.TODAY
        diff <= 7 -> DueBucket.WEEK
        else -> DueBucket.LATER
    }
}

/** Flotte: alles Persönliche über alle Umgebungen – Aufgaben nach Fälligkeit, Eingänge, offene Bounties. */
@Composable
fun FleetScreen(
    onOpenCard: (MyCard) -> Unit,
    onOpenWorkspace: (Workspace, String) -> Unit,
    onError: (Throwable) -> Unit,
) {
    var cards by remember { mutableStateOf<List<MyCard>?>(null) }
    var wss by remember { mutableStateOf<List<Pair<Workspace, String>>>(emptyList()) }
    var inbox by remember { mutableStateOf<List<InboxCount>>(emptyList()) }
    var bounties by remember { mutableStateOf<List<FleetBounty>>(emptyList()) }
    LaunchedEffect(Unit) {
        runCatching {
            val w = Repo.workspaces()
            wss = w
            cards = Repo.myOpenCards()
            inbox = Repo.inboxCounts().filter { it.waiting > 0 }
            bounties = Repo.openBounties(w.map { it.first.id })
        }.onFailure(onError)
    }
    val list = cards ?: run { SplashScreen(); return }
    val wsById = wss.associateBy { it.first.id }
    val grouped = list.groupBy { dueBucket(it.dueDate) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("🚢 Flotte", fontSize = 24.sp, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold)
            Text("Alles für dich aus ${wss.size} Umgebungen.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        if (inbox.isNotEmpty()) {
            item { SectionTitle("📥 Wartet im Eingang") }
            items(inbox) { c ->
                val w = wsById[c.workspaceId] ?: return@items
                ColorTile(w.first.name, w.first.color, "${c.waiting} Aufgabe${if (c.waiting == 1) "" else "n"} warten auf Annahme") { onOpenWorkspace(w.first, w.second) }
            }
        }
        item { SectionTitle("✅ Meine Aufgaben  ${list.size}") }
        if (list.isEmpty()) item { EmptyHint("Keine offenen Aufgaben. 🎉") }
        DueBucket.entries.forEach { b ->
            val l = grouped[b].orEmpty().sortedBy { it.dueDate ?: "9999" }
            if (l.isNotEmpty()) {
                item { Chip("${b.label} · ${l.size}", b.color, Modifier.padding(top = 4.dp)) }
                items(l, key = { "c" + it.id }) { c ->
                    KCard(modifier = Modifier.fillMaxWidth(), radius = 16, onClick = { onOpenCard(c) }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.title, fontWeight = FontWeight.SemiBold)
                                val ws = c.projects?.workspaceId?.let { wsById[it]?.first?.name }
                                Text(listOfNotNull(ws, c.projects?.name).joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            dueInfo(c.dueDate)?.let { (t, col) -> Chip(t, col) }
                        }
                    }
                }
            }
        }
        if (bounties.isNotEmpty()) {
            item { SectionTitle("🪙 Offene Bounties & Hilfe") }
            items(bounties, key = { "b" + it.id }) { b ->
                val w = wsById[b.workspaceId]
                KCard(modifier = Modifier.fillMaxWidth(), color = KudozBountySoft, radius = 16, onClick = { w?.let { onOpenWorkspace(it.first, it.second) } }) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (b.kind == "bounty" && b.rewardImage != null) RewardImage(b.rewardImage, Modifier.size(40.dp))
                        else Text(if (b.kind == "bounty") "🪙" else "🤝", fontSize = 24.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b.title, fontWeight = FontWeight.SemiBold)
                            Text(w?.first?.name ?: "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(t: String) {
    Text(t, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(top = 10.dp))
}

// ---------------------------------------------------------------- Meilensteine

/** Meilensteine eines Projekts mit Fortschritt (erledigte zugeordnete Aufgaben). */
@Composable
fun MilestonesPanel(project: Project, onError: (Throwable) -> Unit) {
    var milestones by remember { mutableStateOf<List<Milestone>?>(null) }
    var cards by remember { mutableStateOf<List<MilestoneCard>>(emptyList()) }
    var version by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Milestone?>(null) }
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun act(b: suspend () -> Unit) = scope.launch { runCatching { b() }.onFailure(onError); version++ }
    LaunchedEffect(project.id, version) {
        runCatching { milestones = Repo.milestones(project.id); cards = Repo.milestoneCards(project.id) }.onFailure(onError)
    }
    val list = milestones ?: run { SplashScreen(); return }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (list.isEmpty()) item { EmptyHint("Dieses Projekt hat keine Meilensteine.") }
        items(list, key = { it.id }) { m ->
            val mine = cards.filter { it.milestoneId == m.id }
            val done = mine.count { it.done }
            val progress = if (m.done) 1f else if (mine.isEmpty()) 0f else done.toFloat() / mine.size
            KCard(modifier = Modifier.fillMaxWidth(), color = if (m.done) paletteColors("mint").first else androidx.compose.ui.graphics.Color.White) {
                Column(Modifier.padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = m.done, onCheckedChange = { v ->
                            scope.launch { runCatching { Repo.setMilestoneDone(m.id, v) }.onFailure(onError); version++ }
                        })
                        Column(Modifier.weight(1f)) {
                            Text("🚩 ${m.title}", fontWeight = FontWeight.Bold)
                            m.description?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        dueInfo(m.dueDate)?.let { (t, c) -> Chip(t, if (m.done) "mint" else c) }
                        androidx.compose.material3.IconButton(onClick = { editing = m }) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Edit, "Bearbeiten") }
                    }
                    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50)),
                            color = paletteColors(project.color).second,
                            trackColor = paletteColors(project.color).first,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("$done/${mine.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            icon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Add, null) }, text = { Text("Meilenstein") },
            containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }
    if (creating) MilestoneEditDialog(null, onDismiss = { creating = false }, onSave = { t, d, desc ->
        creating = false; act { Repo.addMilestone(project.id, t, d, desc, (list.maxOfOrNull { it.position } ?: 0.0) + 1.0) }
    }, onDelete = null)
    editing?.let { m ->
        MilestoneEditDialog(m, onDismiss = { editing = null }, onSave = { t, d, desc -> editing = null; act { Repo.updateMilestone(m.id, t, d, desc) } },
            onDelete = { editing = null; act { Repo.deleteMilestone(m.id) } })
    }
}


// ---------------------------------------------------------------- Einstellungen

private val AVATAR_COLORS = listOf("lavender", "mint", "peach", "sky", "lemon", "rose", "teal", "coral", "lilac", "lime", "ocean", "apricot", "berry", "pistachio")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onSuperadmin: () -> Unit, onError: (Throwable) -> Unit) {
    var superadmin by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { superadmin = Repo.isSuperadmin() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<Profile?>(null) }
    var name by remember { mutableStateOf("") }
    var initials by remember { mutableStateOf("") }
    var bg by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching {
            val p = Repo.myProfile(); profile = p
            name = p?.displayName.orEmpty(); initials = p?.initials.orEmpty(); bg = p?.initialsBg
        }.onFailure(onError)
    }
    val p = profile ?: run { SplashScreen(); return }
    val preview = p.copy(displayName = name, initials = initials.trim().uppercase().take(3).ifBlank { null }, initialsBg = bg, initialsInk = null)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(preview, 56)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(preview.label, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(p.email, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
        }
        item {
            OutlinedTextField(name, { name = it; saved = false }, label = { Text("Anzeigename") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(initials, { initials = it.take(3); saved = false }, label = { Text("Kürzel (max. 3)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            Text("Avatarfarbe", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AVATAR_COLORS.forEach { c ->
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(paletteColors(c).first)
                            .clickable { bg = c; saved = false },
                        contentAlignment = Alignment.Center,
                    ) { if (bg == c) Text("✓", color = paletteColors(c).second, fontWeight = FontWeight.Bold) }
                }
            }
        }
        item {
            Button(enabled = name.isNotBlank() && !saved, onClick = {
                scope.launch {
                    runCatching { Repo.updateProfile(name, initials.trim().uppercase().take(3).ifBlank { null }, bg) }
                        .onSuccess { saved = true }.onFailure(onError)
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(if (saved) "Gespeichert ✓" else "Speichern") }
        }
        item { HorizontalDivider() }
        item {
            OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }, modifier = Modifier.fillMaxWidth()) { Text("🔔 Benachrichtigungen einstellen") }
        }
        if (superadmin) item {
            OutlinedButton(onClick = onSuperadmin, modifier = Modifier.fillMaxWidth()) { Text("🛡️ Superadmin") }
        }
        item {
            OutlinedButton(onClick = { openWeb(context, "/profil") }, modifier = Modifier.fillMaxWidth()) { Text("Weitere Einstellungen im Web") }
        }
        item {
            OutlinedButton(onClick = { scope.launch { AuthFlow.signOut() } }, modifier = Modifier.fillMaxWidth()) {
                Text("Abmelden", color = MaterialTheme.colorScheme.error)
            }
        }
        item {
            Text(
                "Kudoz für Android · Version ${BuildConfig.VERSION_NAME}",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}
