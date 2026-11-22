package ch.kudoz.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ch.kudoz.app.data.Card
import ch.kudoz.app.data.Repo
import ch.kudoz.app.data.TransferTarget
import kotlinx.coroutines.launch

/** Aufgabe an eine andere Umgebung schicken (landet dort im Eingang). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SendTaskDialog(sourceWs: String, card: Card, onDismiss: () -> Unit, onSent: (String) -> Unit, onError: (Throwable) -> Unit) {
    var targets by remember { mutableStateOf<List<TransferTarget>?>(null) }
    var target by remember { mutableStateOf<TransferTarget?>(null) }
    var title by remember { mutableStateOf(card.title) }
    var message by remember { mutableStateOf("") }
    var withChecklist by remember { mutableStateOf(true) }
    var withDue by remember { mutableStateOf(true) }
    var withComments by remember { mutableStateOf(false) }
    var keep by remember { mutableStateOf("copy") }
    var feedback by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(sourceWs) { runCatching { targets = Repo.transferTargets(sourceWs) }.onFailure(onError) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aufgabe senden") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("An welche Umgebung?", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                val t = targets
                if (t == null) Text("Lädt …")
                else if (t.isEmpty()) Text("Keine anderen Umgebungen verfügbar.")
                else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    t.forEach { x -> FilterChip(selected = target?.id == x.id, onClick = { target = x }, label = { Text(x.name) }) }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(title, { title = it }, label = { Text("Titel") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(message, { message = it }, label = { Text("Nachricht (optional)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("Was geht mit?", fontWeight = FontWeight.SemiBold)
                CheckRow("Checkliste", withChecklist) { withChecklist = it }
                CheckRow("Fälligkeit", withDue) { withDue = it }
                CheckRow("Kommentare", withComments) { withComments = it }
                Spacer(Modifier.height(6.dp))
                Text("Und deine Aufgabe hier?", fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(keep == "copy", { keep = "copy" }); Text("Als Kopie behalten") }
                Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(keep == "done", { keep = "done" }); Text("Als erledigt markieren") }
                if (keep == "copy") CheckRow("Status-Rückmeldung erhalten", feedback) { feedback = it }
            }
        },
        confirmButton = {
            TextButton(enabled = target != null && title.isNotBlank() && !busy, onClick = {
                val tg = target ?: return@TextButton
                busy = true
                scope.launch {
                    runCatching {
                        Repo.transferSend(sourceWs, card, tg.id, title.trim(), card.description.orEmpty(), message.trim(),
                            withChecklist, withDue, withComments, keep, feedback)
                    }.onSuccess { onSent(tg.name) }.onFailure { busy = false; onError(it) }
                }
            }) { Text("Senden ⛵") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, onChange); Text(label) }
}
