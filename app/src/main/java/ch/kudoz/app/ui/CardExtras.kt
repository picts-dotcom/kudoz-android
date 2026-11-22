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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.kudoz.app.data.FollowCard
import ch.kudoz.app.data.Profile
import ch.kudoz.app.data.Reaction
import ch.kudoz.app.data.Recurrence
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------------------------------------------------------------- Reaktionen

val REACTION_EMOJIS = listOf("👍", "❤️", "🎉", "😂", "🙌", "🔥", "👀", "🤔")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReactionsRow(reactions: List<Reaction>, me: String?, people: Map<String, Profile>, onToggle: (String, Boolean) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val grouped = reactions.groupBy { it.emoji }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        grouped.forEach { (emoji, list) ->
            val mine = list.any { it.userId == me }
            Text(
                "$emoji ${list.size}",
                Modifier.clip(RoundedCornerShape(50))
                    .background(if (mine) paletteColors("lavender").first else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onToggle(emoji, !mine) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                fontSize = 14.sp,
            )
        }
        Box {
            IconButton(onClick = { picking = !picking }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.AddReaction, "Reaktion", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (picking) {
        Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            REACTION_EMOJIS.forEach { e ->
                val mine = reactions.any { it.emoji == e && it.userId == me }
                Text(
                    e,
                    Modifier.clip(RoundedCornerShape(50))
                        .background(if (mine) paletteColors("lavender").first else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onToggle(e, !mine); picking = false }
                        .padding(8.dp),
                    fontSize = 20.sp,
                )
            }
        }
    }
    val names = reactions.mapNotNull { people[it.userId]?.label }.distinct()
    if (names.isNotEmpty()) Text(names.joinToString(", "), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ---------------------------------------------------------------- Personenwahl

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonPickerDialog(title: String, people: List<Profile>, selected: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = selected == null, onClick = { onPick(null) }, label = { Text("Niemand") })
                people.forEach { p ->
                    FilterChip(selected = selected == p.id, onClick = { onPick(p.id) }, label = { Text(p.label) }, leadingIcon = { Avatar(p, 20) })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schliessen") } },
    )
}

// ---------------------------------------------------------------- Erwähnungen

private fun fold(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

/** Kommentarfeld mit @-Erwähnungen (gleiche Regeln wie src/lib/mentions.ts). */
@Composable
fun MentionComposer(people: List<Profile>, onSend: (String, List<String>) -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue("")) }
    val picked = remember { mutableStateListOf<Profile>() }

    val caret = value.selection.start.coerceAtMost(value.text.length)
    val before = value.text.take(caret)
    val at = before.lastIndexOf('@')
    val query = if (at >= 0 && (at == 0 || before[at - 1].isWhitespace())) before.substring(at + 1).takeIf {
        it.length <= 30 && !it.contains('\n') && !it.contains('@') && !it.contains("  ")
    } else null
    val suggestions = query?.let { q ->
        val f = fold(q.trim())
        people.filter { p -> f.isEmpty() || fold(p.label).split(" ").any { it.startsWith(f) } || fold(p.label).startsWith(f) || fold(p.email).startsWith(f) }.take(6)
    }.orEmpty()

    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
        if (suggestions.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                suggestions.forEach { p ->
                    FilterChip(selected = false, onClick = {
                        val token = "@${p.label} "
                        val text = value.text.take(at) + token + value.text.drop(caret).removePrefix(" ")
                        value = TextFieldValue(text, TextRange(at + token.length))
                        if (picked.none { it.id == p.id }) picked += p
                    }, label = { Text(p.label) }, leadingIcon = { Avatar(p, 20) })
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value, { value = it }, Modifier.weight(1f),
                placeholder = { Text("Kommentar … (@ für Erwähnung)") }, maxLines = 4, shape = RoundedCornerShape(20.dp),
            )
            IconButton(enabled = value.text.isNotBlank(), onClick = {
                val text = value.text
                val mentions = picked.filter { text.contains("@${it.label}") }.map { it.id }
                onSend(text, mentions)
                value = TextFieldValue(""); picked.clear()
            }) { Icon(Icons.AutoMirrored.Filled.Send, "Senden") }
        }
    }
}

// ---------------------------------------------------------------- Folgeaufgaben

@Composable
fun FollowUpsSection(
    parent: FollowCard?,
    follows: List<FollowCard>,
    onOpen: (String) -> Unit,
    onUnlock: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Folgeaufgaben", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        TextButton(onClick = onAdd) { Text("+ Folgeaufgabe") }
    }
    parent?.let {
        Text(
            "↩ Folgt auf: ${it.title}" + if (it.done) " ✓" else "",
            Modifier.clickable { onOpen(it.id) }.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.primary,
        )
    }
    if (follows.isEmpty() && parent == null) Text(
        "Was kommt danach? Folgeaufgaben starten, sobald diese erledigt ist.",
        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    follows.forEach { f ->
        Row(Modifier.fillMaxWidth().clickable { onOpen(f.id) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(f.title + if (f.done) " ✓" else "", Modifier.weight(1f))
            if (f.followLocked) {
                Icon(Icons.Default.Lock, "Gesperrt", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { onUnlock(f.id) }) { Text("Freischalten") }
            }
        }
    }
}

// ---------------------------------------------------------------- Wiederkehrend

private val WEEKDAYS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
private val NTH = listOf("Erster", "Zweiter", "Dritter", "Vierter", "Letzter")
private val KINDS = listOf(
    "daily" to "Täglich (Schultage)", "weekly" to "Wöchentlich", "biweekly" to "Alle 2 Wochen",
    "monthly_day" to "Monatlich am Datum", "monthly_nth" to "Monatlich am Wochentag",
    "semester" to "Halbjährlich", "yearly" to "Jährlich", "custom_days" to "Alle X Tage", "custom_weeks" to "Alle X Wochen",
)

fun recurrenceText(r: Recurrence): String {
    val days = r.weekdays.sorted().joinToString(", ") { WEEKDAYS.getOrElse(it - 1) { "?" } }
    val base = when (r.kind) {
        "daily" -> "Jeden Schultag"
        "weekly" -> when {
            r.every <= 1 -> "Jede Woche am $days"
            r.every == 2 -> "Alle 2 Wochen am $days"
            else -> "Alle ${r.every} Wochen am $days"
        }
        "custom_days" -> "Alle ${r.every} Tage"
        "monthly_day" -> "Monatlich am ${r.monthDay}."
        "monthly_nth" -> "Monatlich: ${NTH.getOrElse((r.nth ?: 1) - 1) { "" }} ${WEEKDAYS.getOrElse((r.weekdays.firstOrNull() ?: 1) - 1) { "" }}"
        "semester" -> "Alle 6 Monate"
        "yearly" -> "Jährlich"
        else -> r.kind
    }
    return base + (if (r.endDate != null) " bis ${fmtDate(r.endDate)}" else "") + if (r.paused) " (pausiert)" else ""
}

private fun fmtDate(s: String) = runCatching { LocalDate.parse(s.take(10)).format(DateTimeFormatter.ofPattern("d. MMM yyyy", Locale.GERMAN)) }.getOrDefault(s)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecurrenceDialog(members: List<Profile> = emptyList(), current: Recurrence?, onSave: (Recurrence) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    val today = LocalDate.now()
    val initKind = current?.let {
        if (it.kind == "weekly" && it.every == 2) "biweekly" else if (it.kind == "weekly" && it.every > 2) "custom_weeks" else it.kind
    } ?: "weekly"
    var kind by remember { mutableStateOf(initKind) }
    var every by remember { mutableStateOf((current?.every ?: 3).toString()) }
    val weekdays = remember { mutableStateListOf<Int>().apply { addAll(current?.weekdays?.takeIf { it.isNotEmpty() } ?: listOf(today.dayOfWeek.value)) } }
    var monthDay by remember { mutableStateOf((current?.monthDay ?: today.dayOfMonth).toString()) }
    var nth by remember { mutableStateOf(current?.nth ?: 1) }
    var start by remember { mutableStateOf(current?.startDate ?: today.toString()) }
    var end by remember { mutableStateOf(current?.endDate) }
    var paused by remember { mutableStateOf(current?.paused ?: false) }
    var picking by remember { mutableStateOf<String?>(null) }
    val rotation = remember { mutableStateListOf<String>().apply { addAll(current?.rotation.orEmpty()) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔁 Wiederholen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    KINDS.forEach { (k, l) -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(l) }) }
                }
                Spacer(Modifier.height(10.dp))
                if (kind == "custom_days" || kind == "custom_weeks") {
                    OutlinedTextField(every, { v -> every = v.filter { it.isDigit() }.take(3) }, label = { Text(if (kind == "custom_days") "Alle … Tage" else "Alle … Wochen") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                }
                if (kind in listOf("weekly", "biweekly", "custom_weeks", "monthly_nth")) {
                    Text(if (kind == "monthly_nth") "Wochentag" else "An welchen Tagen?", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        WEEKDAYS.forEachIndexed { i, d ->
                            val n = i + 1
                            FilterChip(selected = n in weekdays, onClick = {
                                if (kind == "monthly_nth") { weekdays.clear(); weekdays += n }
                                else if (n in weekdays) weekdays.remove(n) else weekdays += n
                            }, label = { Text(d) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (kind == "monthly_nth") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        NTH.forEachIndexed { i, l -> FilterChip(selected = nth == i + 1, onClick = { nth = i + 1 }, label = { Text(l) }) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (kind == "monthly_day") {
                    OutlinedTextField(monthDay, { v -> monthDay = v.filter { it.isDigit() }.take(2) }, label = { Text("Am … des Monats") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { picking = "start" }) { Text("Ab ${fmtDate(start)}") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { picking = "end" }) { Text(end?.let { "Bis ${fmtDate(it)}" } ?: "Ohne Ende") }
                }
                if (end != null) TextButton(onClick = { end = null }) { Text("Ende entfernen") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pausiert", Modifier.weight(1f))
                    Switch(paused, { paused = it })
                }
                if (members.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Rotation (optional)", fontWeight = FontWeight.SemiBold)
                    Text("Wer ist der Reihe nach verantwortlich? Antippen in der gewünschten Reihenfolge.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        members.forEach { p ->
                            val idx = rotation.indexOf(p.id)
                            FilterChip(selected = idx >= 0, onClick = { if (idx >= 0) rotation.remove(p.id) else rotation += p.id },
                                label = { Text(if (idx >= 0) "${idx + 1}. ${p.label}" else p.label) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            val dbKind = if (kind == "biweekly" || kind == "custom_weeks") "weekly" else kind
            val dbEvery = when (kind) { "biweekly" -> 2; "custom_weeks", "custom_days" -> (every.toIntOrNull() ?: 1).coerceAtLeast(1); else -> 1 }
            val ok = !(dbKind == "weekly" && weekdays.isEmpty())
            TextButton(enabled = ok, onClick = {
                onSave(
                    Recurrence(
                        kind = dbKind, every = dbEvery,
                        weekdays = when (dbKind) { "monthly_nth" -> listOf(weekdays.firstOrNull() ?: 1); "weekly" -> weekdays.sorted(); else -> emptyList() },
                        monthDay = if (dbKind == "monthly_day") (monthDay.toIntOrNull() ?: 1).coerceIn(1, 31) else null,
                        nth = if (dbKind == "monthly_nth") nth else null,
                        startDate = start, endDate = end, paused = paused,
                        rotation = rotation.toList(), rotationIdx = if (rotation.toList() == current?.rotation) (current?.rotationIdx ?: 0) else 0,
                    ),
                )
            }) { Text("Speichern") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Entfernen", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )

    picking?.let { which ->
        val init = (if (which == "start") start else end ?: start).let { runCatching { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull() }
        val state = rememberDatePickerState(initialSelectedDateMillis = init)
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val d = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
                        if (which == "start") start = d else end = d
                    }
                    picking = null
                }) { Text("OK") }
            },
        ) { DatePicker(state) }
    }
}

@Composable
fun RecurrenceSection(rec: Recurrence?, isInstance: Boolean, onEdit: () -> Unit, onOpenTemplate: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Wiederholen", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (isInstance) TextButton(onClick = onOpenTemplate) { Text("Vorlage öffnen") }
        else TextButton(onClick = onEdit) { Text(if (rec == null) "Einrichten" else "Ändern") }
    }
    when {
        isInstance -> Text("🔁 Diese Aufgabe entsteht regelmässig aus einer Vorlage.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        rec != null -> Surface(color = paletteColors("sky").first, shape = RoundedCornerShape(12.dp)) {
            Text("🔁 ${recurrenceText(rec)}", Modifier.padding(10.dp), color = paletteColors("sky").second)
        }
        else -> Text("Wiederkehrende Aufgaben entstehen automatisch neu, z. B. jeden Montag.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
