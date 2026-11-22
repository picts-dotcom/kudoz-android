package ch.kudoz.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.kudoz.app.data.Bucket
import ch.kudoz.app.data.MyCard
import ch.kudoz.app.data.MyDayLogic
import ch.kudoz.app.data.Plan
import ch.kudoz.app.data.Repo
import kotlinx.coroutines.launch

/** «Mein Tag»: eigene offene Aufgaben über alle Umgebungen, in Fächer verteilt. Nur für dich sichtbar. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MyDayScreen(onOpenCard: (MyCard) -> Unit, onError: (Throwable) -> Unit) {
    var cards by remember { mutableStateOf<List<MyCard>?>(null) }
    var plans by remember { mutableStateOf<List<Plan>>(emptyList()) }
    var tab by remember { mutableStateOf(Bucket.TODAY) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val today = remember { MyDayLogic.today() }

    suspend fun reload() {
        runCatching { val c = Repo.myOpenCards(); val p = Repo.plans(); cards = c; plans = p }.onFailure(onError)
    }
    LaunchedEffect(Unit) { reload() }

    val planByCard = plans.filter { it.cardId != null }.associateBy { it.cardId!! }
    val buckets = MyDayLogic.visibleBuckets(today)
    val grouped = (cards ?: emptyList()).groupBy { MyDayLogic.bucketOf(planByCard[it.id], today) }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Mein Tag",
            fontSize = 28.sp, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 18.dp, top = 16.dp, end = 18.dp),
        )
        Text(
            "Wann machst du was? Nur du siehst deine Planung.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        Spacer(Modifier.height(8.dp))
        PillTabs(
            buckets.map { b -> "${b.label}  ${grouped[b]?.size ?: 0}" },
            buckets.indexOf(tab).coerceAtLeast(0),
            { i -> tab = buckets[i] },
        )
        if (cards == null) { SplashScreen(); return@Column }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; reload(); refreshing = false } },
        ) {
            val list = (grouped[tab] ?: emptyList()).sortedWith(compareBy({ it.dueDate ?: "9999" }, { it.title }))
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (tab == Bucket.TODAY && list.size > MyDayLogic.TODAY_SOFT_LIMIT) item {
                    Surface(color = paletteColors("lemon").first, shape = RoundedCornerShape(14.dp)) {
                        Text(
                            "Viel für einen Tag: ${list.size} Aufgaben. Schieb ein paar auf morgen oder diese Woche.",
                            Modifier.padding(12.dp), color = paletteColors("lemon").second,
                        )
                    }
                }
                if (list.isEmpty()) item {
                    EmptyHint(
                        when (tab) {
                            Bucket.TODAY -> "Heute ist nichts geplant. 🎉\nHol dir etwas aus «Unverplant»."
                            Bucket.UNPLANNED -> "Alles verplant – stark!"
                            else -> "Hier ist noch nichts geplant."
                        },
                    )
                }
                items(list, key = { it.id }) { c ->
                    MyDayRow(
                        card = c,
                        plan = planByCard[c.id],
                        current = tab,
                        buckets = buckets,
                        onOpen = { onOpenCard(c) },
                        onPick = { b ->
                            scope.launch {
                                runCatching { Repo.savePlan(c.id, planByCard[c.id], MyDayLogic.targetFor(b, MyDayLogic.today())) }.onFailure(onError)
                                reload()
                            }
                        },
                        onDone = {
                            scope.launch {
                                runCatching { Repo.setDone(c.id, true) }.onFailure(onError)
                                reload()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MyDayRow(
    card: MyCard,
    plan: Plan?,
    current: Bucket,
    buckets: List<Bucket>,
    onOpen: () -> Unit,
    onPick: (Bucket) -> Unit,
    onDone: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val project = card.projects
    KCard(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(Modifier.padding(start = 6.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone, enabled = !card.followLocked) {
                Icon(
                    if (card.followLocked) Icons.Default.Lock else Icons.Default.CheckCircleOutline,
                    if (card.followLocked) "Wartet auf Vorgänger" else "Erledigt",
                    tint = paletteColors("mint").second,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(card.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    project?.let { Chip(it.name, it.color) }
                    dueInfo(card.dueDate)?.let { (t, c) -> Chip("📅 $t", c) }
                    MyDayLogic.postponedLabel(MyDayLogic.postponedDays(plan, MyDayLogic.today()))?.let { Chip(it, "peach") }
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Planen") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    Text("Wann mache ich das?", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    buckets.filter { it != Bucket.UNPLANNED && it != current }.forEach { b ->
                        DropdownMenuItem(text = { Text(b.label) }, onClick = { menu = false; onPick(b) })
                    }
                    if (current != Bucket.UNPLANNED) {
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Entplanen") }, onClick = { menu = false; onPick(Bucket.UNPLANNED) })
                    }
                }
            }
            Spacer(Modifier.width(2.dp))
        }
    }
}
