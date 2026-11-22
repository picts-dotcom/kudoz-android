package ch.kudoz.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.kudoz.app.data.Repo
import ch.kudoz.app.data.Rewards

/** Bild einer Belohnung aus dem lokalen Katalog; grau, wenn noch nicht gesammelt. */
@Composable
fun RewardImage(id: Int, modifier: Modifier = Modifier, collected: Boolean = true) {
    val context = LocalContext.current
    val bmp = remember(id) { Rewards.image(context, id) }
    if (bmp == null) {
        Box(modifier.clip(RoundedCornerShape(12.dp)).background(paletteColors("lemon").first), contentAlignment = Alignment.Center) { Text("🪙") }
        return
    }
    Image(
        bmp.asImageBitmap(),
        contentDescription = Rewards.label(context, id),
        contentScale = ContentScale.Crop,
        colorFilter = if (collected) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
        modifier = modifier.clip(RoundedCornerShape(12.dp)).alpha(if (collected) 1f else 0.25f),
    )
}

/** «Meine Schatzkammer»: persönliche Sammlung aus allen Umgebungen, 10 Kategorien à 20. */
@Composable
fun TreasuryScreen(onError: (Throwable) -> Unit) {
    val context = LocalContext.current
    var counts by remember { mutableStateOf<Map<Int, Int>?>(null) }
    var detail by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) { runCatching { counts = Repo.treasury() }.onFailure(onError) }
    val c = counts ?: run { SplashScreen(); return }
    val categories = remember { Rewards.categories(context) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💎 Meine Sammlung", fontSize = 22.sp, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Chip("${c.size} von 200", "lemon")
            }
        }
        categories.forEach { cat ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(cat.label, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Text("${cat.items.count { c.containsKey(it) }} / 20", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
            items(cat.items, key = { it }) { id ->
                val n = c[id] ?: 0
                Box(Modifier.aspectRatio(1f).clickable { detail = id }) {
                    RewardImage(id, Modifier.fillMaxWidth().aspectRatio(1f), collected = n > 0)
                    if (n > 1) Text(
                        "×$n",
                        Modifier.align(Alignment.TopEnd).padding(3.dp).clip(RoundedCornerShape(50))
                            .background(paletteColors("apricot").first).padding(horizontal = 6.dp),
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = paletteColors("apricot").second,
                    )
                }
            }
        }
    }

    detail?.let { id ->
        val n = c[id] ?: 0
        AlertDialog(
            onDismissRequest = { detail = null },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("Schliessen") } },
            title = { Text(Rewards.label(context, id)) },
            text = {
                androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RewardImage(id, Modifier.fillMaxWidth().aspectRatio(1f), collected = n > 0)
                    Text(
                        if (n == 0) "Noch nicht gesammelt." else if (n == 1) "1 Exemplar" else "$n Exemplare",
                        Modifier.padding(top = 10.dp),
                    )
                }
            },
        )
    }
}
