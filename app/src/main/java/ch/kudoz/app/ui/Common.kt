package ch.kudoz.app.ui

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.kudoz.app.BuildConfig
import ch.kudoz.app.data.Profile
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

fun openWeb(context: Context, path: String) {
    CustomTabsIntent.Builder().setShowTitle(true).build()
        .launchUrl(context, Uri.parse(BuildConfig.WEB_URL + path))
}

/** Fehlermeldungen von Postgres (RLS, RPC-Prüfungen) verständlich machen. */
fun friendlyError(e: Throwable): String {
    val m = e.message.orEmpty()
    return when {
        m.contains("permission", true) || m.contains("row-level security", true) || m.contains("not allowed", true) ->
            "Dafür hast du in dieser Umgebung keine Berechtigung."
        m.contains("Unable to resolve host", true) || m.contains("timeout", true) ->
            "Keine Verbindung. Prüfe das Internet und versuch es nochmals."
        else -> m.lineSequence().firstOrNull { it.isNotBlank() }?.take(160) ?: "Etwas hat nicht geklappt."
    }
}

@Composable
fun SplashScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun LoginScreen(message: String?, busy: Boolean, onLogin: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("lavender" to 64, "lemon" to 46, "mint" to 34).forEach { (c, h) ->
                Box(Modifier.size(22.dp, h.dp).clip(RoundedCornerShape(6.dp)).background(paletteColors(c).first))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Kudoz", fontSize = 40.sp, fontWeight = FontWeight.Black)
        Text("Gemeinsam planen, Kudos sammeln.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(36.dp))
        if (busy) CircularProgressIndicator()
        else Button(onClick = onLogin, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Mit Google anmelden", fontSize = 16.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Nur für Konten mit @ruggenacher.ch",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        message?.let {
            Spacer(Modifier.height(20.dp))
            Surface(color = paletteColors("peach").first, shape = RoundedCornerShape(16.dp)) {
                Text(it, Modifier.padding(16.dp), color = paletteColors("peach").second, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun Avatar(p: Profile?, size: Int = 28) {
    val (bg, ink) = paletteColors(p?.initialsBg ?: "lavender")
    val inkColor = p?.initialsInk?.let { paletteColors(it).second } ?: ink
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(p?.shortLabel ?: "?", color = inkColor, fontSize = (size * 0.4f).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AvatarStack(people: List<Profile?>, size: Int = 24) {
    Row {
        people.take(4).forEachIndexed { i, p ->
            Box(Modifier.offset(x = (-6 * i).dp)) { Avatar(p, size) }
        }
    }
}

@Composable
fun Chip(text: String, color: String, modifier: Modifier = Modifier) {
    val (bg, ink) = paletteColors(color)
    Text(
        text,
        modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 3.dp),
        color = ink,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

/** Fälligkeit als kurzer Text und Farbe wie im Web (überfällig rosa, heute/morgen zitrone). */
fun dueInfo(date: String?): Pair<String, String>? {
    val d = runCatching { LocalDate.parse(date?.take(10)) }.getOrNull() ?: return null
    val days = ChronoUnit.DAYS.between(LocalDate.now(), d)
    val label = when (days) {
        0L -> "heute"
        1L -> "morgen"
        -1L -> "gestern"
        else -> d.format(DateTimeFormatter.ofPattern("EE d. MMM", Locale.GERMAN))
    }
    val color = when {
        days < 0 -> "rose"
        days <= 1 -> "lemon"
        else -> "sky"
    }
    return label to color
}

fun relativeTime(iso: String): String {
    val t = runCatching { OffsetDateTime.parse(iso) }.getOrNull() ?: return ""
    val min = ChronoUnit.MINUTES.between(t, OffsetDateTime.now())
    return when {
        min < 1 -> "gerade eben"
        min < 60 -> "vor $min Min."
        min < 60 * 24 -> "vor ${min / 60} Std."
        min < 60 * 24 * 7 -> "vor ${min / (60 * 24)} T."
        else -> t.format(DateTimeFormatter.ofPattern("d. MMM", Locale.GERMAN))
    }
}

@Composable
fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

val Ink = Color(0xFF262738)
