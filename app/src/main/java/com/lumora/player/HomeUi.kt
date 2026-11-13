@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lumora.player

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ======================= HOME =======================

@Composable
fun HomeTab(vm: AppVM, onSeeAll: () -> Unit, onAddSource: () -> Unit) {
    LaunchedEffect(vm.activeSrc) {
        vm.load("live")
        vm.load("vod")
        vm.load("series")
    }
    when (currentLayout()) {
        UiLayout.CINEMA -> HomeCinema(vm, onSeeAll, onAddSource)
        UiLayout.GLASS -> HomeGlass(vm, onSeeAll, onAddSource)
        UiLayout.NEON -> HomeNeon(vm, onSeeAll, onAddSource)
        else -> HomeClassic(vm, onSeeAll, onAddSource)
    }
}

private data class HomeData(
    val cw: List<Progress>,
    val live: List<Item>,
    val vod: List<Item>,
    val ser: List<Item>,
    val channels: List<Item>,
    val movies: List<Item>,
    val shows: List<Item>
)

private fun homeData(vm: AppVM): HomeData {
    val live = vm.catalog["live"].orEmpty()
    val vod = vm.catalog["vod"].orEmpty()
    val ser = vm.catalog["series"].orEmpty()
    return HomeData(
        cw = vm.continueWatching(),
        live = live, vod = vod, ser = ser,
        channels = vm.mostViewed().map { it.item }.ifEmpty { live.take(12) },
        movies = vod.sortedByDescending { it.added }.take(14),
        shows = ser.sortedByDescending { it.added }.take(14)
    )
}

@Composable
private fun HomeClassic(vm: AppVM, onSeeAll: () -> Unit, onAddSource: () -> Unit) {
    val cw = vm.continueWatching()
    val live = vm.catalog["live"]
    val vod = vm.catalog["vod"]
    val ser = vm.catalog["series"]
    val channels = vm.mostViewed().map { v -> v.item }.ifEmpty { (live ?: emptyList()).take(12) }
    val movies = remember(vod) { (vod ?: emptyList()).sortedByDescending { m -> m.added }.take(14) }
    val shows = remember(ser) { (ser ?: emptyList()).sortedByDescending { m -> m.added }.take(14) }
    val busy = vm.isLoading("live") || vm.isLoading("vod") || vm.isLoading("series")

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp)) }
        if (vm.sources.isEmpty()) item {
            Column(
                Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), accent2().copy(alpha = 0.25f))))
                    .padding(22.dp)
            ) {
                Text("Welcome to Lumore", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Add your Xtream or M3U source to start watching.", Modifier.padding(top = 6.dp, bottom = 14.dp), color = Color(0xFFD5D7E0))
                Button(onClick = onAddSource) { Text("Add source") }
            }
        }
        if (cw.isNotEmpty()) {
            item { SectionHeader("Continue watching", null) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(cw) { p ->
                        PosterCard(
                            item = p.item,
                            modifier = Modifier.width(128.dp),
                            ratio = 2f / 3f,
                            progress = if (p.dur > 0) p.pos.toFloat() / p.dur.toFloat() else 0f,
                            onClick = { vm.open(p.item, auto = true) },
                            onLong = { vm.actionItem = p.item }
                        )
                    }
                }
            }
        }
        if (channels.isNotEmpty()) {
            item { SectionHeader("Channels", "See all", onSeeAll) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(channels) { ch ->
                        PosterCard(
                            item = ch, modifier = Modifier.width(104.dp), ratio = 1f, fav = vm.isFav(ch),
                            onClick = { vm.open(ch) }, onLong = { vm.actionItem = ch }
                        )
                    }
                }
            }
        }
        if (movies.isNotEmpty()) {
            item { SectionHeader("New movies", "See all", onSeeAll) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(movies) { m ->
                        PosterCard(
                            item = m, modifier = Modifier.width(116.dp), ratio = 2f / 3f,
                            fav = vm.isFav(m), dl = vm.dlInfo[m.key],
                            onClick = { vm.open(m) }, onLong = { vm.actionItem = m }
                        )
                    }
                }
            }
        }
        if (shows.isNotEmpty()) {
            item { SectionHeader("New series", "See all", onSeeAll) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(shows) { m ->
                        PosterCard(
                            item = m, modifier = Modifier.width(116.dp), ratio = 2f / 3f, fav = vm.isFav(m),
                            onClick = { vm.open(m) }, onLong = { vm.actionItem = m }
                        )
                    }
                }
            }
        }
    }
}

// ======================= CATALOG (categories) =======================

data class GroupInfo(val name: String, val count: Int, val logo: String)

fun buildGroups(all: List<Item>, sort: String): List<GroupInfo> {
    val counts = LinkedHashMap<String, Int>()
    val logos = HashMap<String, String>()
    for (it in all) {
        val g = groupName(it.group)
        counts[g] = (counts[g] ?: 0) + 1
        if (!logos.containsKey(g) && it.logo.startsWith("http")) logos[g] = it.logo
    }
    val list = counts.map { e -> GroupInfo(e.key, e.value, logos[e.key] ?: "") }
    return if (sort == "az") list.sortedBy { g -> g.name.lowercase() } else list.sortedByDescending { g -> g.count }
}

private val tilePalette = listOf(
    0xFFE5173F to 0xFFFF7A59, 0xFF8B5CF6 to 0xFF3B82F6, 0xFF10B981 to 0xFF06B6D4,
    0xFFF59E0B to 0xFFEF4444, 0xFFEC4899 to 0xFF8B5CF6, 0xFF3B82F6 to 0xFF22D3EE
)

@Composable
private fun GroupCard(g: GroupInfo, kind: String, icon: ImageVector, all: Boolean, locked: Boolean, onClick: () -> Unit) {
    val pal = tilePalette[(g.name.hashCode() and 0x7fffffff) % tilePalette.size]
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface).clickable { onClick() }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(58.dp).clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(Color(pal.first), Color(pal.second)))),
            contentAlignment = Alignment.Center
        ) {
            if (!all && !locked && g.logo.isNotEmpty()) {
                AsyncImage(
                    model = g.logo, contentDescription = null,
                    contentScale = if (kind == "live") ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().then(if (kind == "live") Modifier.padding(8.dp) else Modifier)
                )
                Box(Modifier.fillMaxSize().background(Color(0x22000000)))
            } else {
                Icon(if (locked) Icons.Default.Lock else icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (all) "All titles" else g.name.ifBlank { "Uncategorized" }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text("${g.count} items", fontSize = 13.sp, color = Color(0xFF8E92A0), modifier = Modifier.padding(top = 2.dp))
        }
        Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF6B6F7C))
    }
}

@Composable
fun CatalogTab(vm: AppVM) {
    val kinds = listOf(
        Triple("live", "Live TV", Icons.Default.LiveTv),
        Triple("vod", "Movies", Icons.Default.Movie),
        Triple("series", "Series", Icons.Default.Subscriptions)
    )
    var k by rememberSaveable { mutableIntStateOf(1) }
    var gq by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<String?>(null) }
    val kind = kinds[k].first
    LaunchedEffect(kind, vm.activeSrc) { vm.load(kind) }
    val all = vm.catalog[kind] ?: emptyList()
    val groups = remember(all, vm.sort) { buildGroups(all, vm.sort) }
    val shown = remember(groups, gq) { if (gq.isBlank()) groups else groups.filter { g -> g.name.contains(gq.trim(), ignoreCase = true) } }
    val loading = vm.isLoading(kind)
    val updated = vm.updatedAt[kind] ?: 0L

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Catalog", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        loading && all.isNotEmpty() -> "Updating…"
                        updated > 0L -> "Updated " + DateUtils.getRelativeTimeSpanString(updated, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                        else -> " "
                    }, fontSize = 12.sp, color = Color(0xFF8E92A0)
                )
            }
            IconButton(onClick = { vm.load(kind, force = true) }) {
                if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, "Refresh")
            }
        }
        // kind switcher
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface).padding(4.dp)
        ) {
            kinds.forEachIndexed { i, p ->
                val on = i == k
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .background(if (on) Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, accent2())) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
                        .clickable { k = i; gq = "" }.padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(p.third, null, tint = if (on) Color.White else Color(0xFF8E92A0), modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(p.second, fontSize = 13.sp, color = if (on) Color.White else Color(0xFFB8BBC6), fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
        OutlinedTextField(
            value = gq, onValueChange = { v -> gq = v }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(18.dp),
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (gq.isNotEmpty()) IconButton(onClick = { gq = "" }) { Icon(Icons.Default.Close, "Clear") } },
            placeholder = { Text("Search categories") },
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = Color.Transparent
            )
        )
        Spacer(Modifier.height(6.dp))
        when {
            vm.src() == null -> EmptyState("No sources yet", "Open Settings and add a source")
            loading && all.isEmpty() -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            all.isEmpty() -> EmptyState("Nothing here yet", "Could not load this list", "Retry", onAction = { vm.load(kind, true) })
            else -> {
                val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp + nav)) {
                    if (gq.isBlank()) item {
                        GroupCard(GroupInfo("", all.size, ""), kind, kinds[k].third, true, false) { vm.openGroup = GroupRef(kind, "") }
                    }
                    items(shown) { g ->
                        val lk = vm.locked(g.name)
                        GroupCard(g, kind, kinds[k].third, false, lk) {
                            if (lk) pending = g.name else vm.openGroup = GroupRef(kind, g.name)
                        }
                    }
                }
            }
        }
    }
    pending?.let { g -> PinUnlock(vm, { vm.openGroup = GroupRef(kind, g); pending = null }) { pending = null } }
}

@Composable
fun CategoryScreen(vm: AppVM, ref: GroupRef) {
    BackHandler { vm.openGroup = null }
    val all = vm.catalog[ref.kind] ?: emptyList()
    var q by remember(ref) { mutableStateOf("") }
    var channelSort by rememberSaveable(ref.kind, ref.name) { mutableStateOf("name") }
    var favoritesOnly by rememberSaveable(ref.kind, ref.name) { mutableStateOf(false) }
    val base = remember(all, ref) { if (ref.name.isEmpty()) all else all.filter { groupName(it.group) == ref.name } }
    val searched = remember(base, q) { if (q.isBlank()) base else base.filter { it.name.contains(q.trim(), ignoreCase = true) } }
    val favoriteFiltered = remember(searched, favoritesOnly, vm.favs.toList()) {
        if (!favoritesOnly) searched else searched.filter { item -> vm.isFav(item) }
    }
    val list = remember(favoriteFiltered, channelSort) {
        when (channelSort) {
            "category" -> favoriteFiltered.sortedWith(compareBy<Item> { it.group.lowercase() }.thenBy { it.name.lowercase() })
            else -> favoriteFiltered.sortedBy { it.name.lowercase() }
        }
    }
    val title = if (ref.name.isEmpty()) when (ref.kind) { "live" -> "All channels"; "vod" -> "All movies"; else -> "All series" } else ref.name
    Column(Modifier.fillMaxSize().themeBackground().blockTouches().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.openGroup = null }) { Icon(Icons.Default.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${base.size} items", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(18.dp),
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Default.Close, "Clear") } },
            placeholder = { Text("Search in this category") },
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = Color.Transparent
            )
        )
        if (ref.kind == "live") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(selected = channelSort == "name", onClick = { channelSort = "name" }, label = { Text("Name A–Z") })
                FilterChip(selected = channelSort == "category", onClick = { channelSort = "category" }, label = { Text("Category") })
                FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = !favoritesOnly }, label = { Text("★ Favorites") })
            }
        } else {
            SortPills(vm)
        }
        if (list.isEmpty()) {
            EmptyState(if (favoritesOnly) "No favorite channels" else "No channels found", if (favoritesOnly) "Mark channels as favorites to find them here" else "Try another search or category")
        } else {
            PosterGrid(vm, list, Modifier.weight(1f))
        }
    }
}

// ======================= FAVORITES / SEARCH =======================

@Composable
fun FavoritesTab(vm: AppVM) {
    Column(Modifier.fillMaxSize()) {
        Text("Favorites", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
        if (vm.favs.isEmpty()) {
            EmptyState("No favorites yet", "Open any title and tap the star to keep it here")
        } else {
            SortPills(vm)
            PosterGrid(vm, vm.favs.toList(), Modifier.weight(1f))
        }
    }
}

@Composable
fun SearchTab(vm: AppVM) {
    var q by remember { mutableStateOf("") }
    LaunchedEffect(vm.activeSrc) { listOf("live", "vod", "series").forEach { vm.load(it) } }
    val live = vm.catalog["live"]
    val vod = vm.catalog["vod"]
    val ser = vm.catalog["series"]
    var tab by remember { mutableStateOf("all") }
    // Search keys are computed once per catalog, off the main thread.
    val index by produceState<List<Triple<String, Item, String>>>(emptyList(), live, vod, ser) {
        value = withContext(Dispatchers.Default) {
            val out = ArrayList<Triple<String, Item, String>>()
            for ((kind, list) in listOf("vod" to vod, "series" to ser, "live" to live))
                list?.forEach { out.add(Triple(kind, it, searchKey(it.name))) }
            out
        }
    }
    // Wait 300 ms after the last keystroke before searching.
    var dq by remember { mutableStateOf("") }
    LaunchedEffect(q) { delay(300); dq = q }
    val found by produceState<Map<String, List<Item>>>(emptyMap(), dq, index, vm.unlocked) {
        val t = searchKey(dq)
        value = if (t.length < 2) emptyMap() else withContext(Dispatchers.Default) {
            index.filter { it.third.contains(t) && !vm.locked(it.second.group) }
                .groupBy({ it.first }, { it.second })
        }
    }
    val tabs = buildList {
        add(Triple("all", "All", found.values.sumOf { it.size }))
        add(Triple("vod", "Movies", found["vod"]?.size ?: 0))
        add(Triple("series", "Series", found["series"]?.size ?: 0))
        if ((found["live"]?.size ?: 0) > 0) add(Triple("live", "Live", found["live"]?.size ?: 0))
    }
    var limit by remember { mutableStateOf(300) }
    LaunchedEffect(dq, tab) { limit = 300 }
    val res = remember(found, tab, limit) {
        (if (tab == "all") (found["vod"].orEmpty() + found["series"].orEmpty() + found["live"].orEmpty()) else found[tab].orEmpty()).take(limit)
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            q, { q = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), singleLine = true,
            shape = RoundedCornerShape(18.dp),
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Default.Close, "Clear") } },
            placeholder = { Text("Search channels, movies and series") },
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = Color.Transparent
            )
        )
        if (q.isBlank() && vm.recentSearches.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recent", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                vm.recentSearches.forEach { r ->
                    AssistChip(onClick = { q = r }, label = { Text(r, fontSize = 12.sp) })
                }
                TextButton(onClick = { vm.clearRecentSearches() }) { Text("Clear", fontSize = 12.sp) }
            }
        }
        if (q.trim().length < 2) EmptyState("Search everything", "Type at least 2 letters")
        else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                tabs.forEach { (k, label, n) ->
                    val sel = tab == k
                    Column(Modifier.clickable { tab = k }.padding(vertical = 8.dp)) {
                        Text(
                            "$label ($n)", fontWeight = FontWeight.Bold,
                            color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                        )
                        Box(Modifier.padding(top = 4.dp).height(3.dp).fillMaxWidth().background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent))
                    }
                }
            }
            val total = if (tab == "all") found.values.sumOf { it.size } else found[tab]?.size ?: 0
            if (res.isEmpty()) EmptyState("No results", "Try another word")
            else {
                val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp + nav), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(res, key = { it.key }) { it -> SearchRow(vm, it) { vm.addRecentSearch(dq) } }
                    if (total > res.size) item {
                        TextButton(onClick = { limit += 300 }, modifier = Modifier.fillMaxWidth()) { Text("Show more (${total - res.size})") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchRow(vm: AppVM, it: Item, onOpen: () -> Unit = { vm.open(it) }) {
    val live = it.kind == "live"
    Row(Modifier.fillMaxWidth().clickable { onOpen() }, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.width(if (live) 64.dp else 72.dp).aspectRatio(if (live) 1f else 2f / 3f)
                .clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (it.logo.startsWith("http")) {
                AsyncImage(
                    model = it.logo, contentDescription = it.name, modifier = Modifier.fillMaxSize(),
                    contentScale = if (live) ContentScale.Fit else ContentScale.Crop
                )
            } else {
                Text(it.name.take(2).uppercase(), Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(it.name, maxLines = 3, overflow = TextOverflow.Ellipsis, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                when (it.kind) { "vod" -> "Movie"; "series" -> "Series"; else -> "Live" },
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// ======================= CINEMA / GLASS / NEON HOME LAYOUTS =======================

@Composable
private fun WideRail(vm: AppVM, items: List<Item>, kicker: String? = null) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items, key = { it.key }) { item ->
            WideCard(
                item = item,
                kicker = kicker ?: if (item.kind == "live") "LIVE" else null,
                progress = vm.progress.firstOrNull { it.item.key == item.key }
                    ?.let { p -> if (p.dur > 0) p.pos.toFloat() / p.dur.toFloat() else -1f } ?: -1f,
                onClick = { vm.open(item, auto = true) }
            )
        }
    }
}

/** Wide 16:9 artwork, a hero banner and generous rails. */
@Composable
private fun HomeCinema(vm: AppVM, onSeeAll: () -> Unit, onAddSource: () -> Unit) {
    val h = homeData(vm)
    val hero = h.movies.firstOrNull() ?: h.shows.firstOrNull() ?: h.channels.firstOrNull()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        if (vm.sources.isEmpty()) item {
            EmptyState("Welcome to Lumore", "Add your Xtream or M3U source to start watching.", "Add source", onAddSource)
        }
        if (hero != null) item {
            Box(Modifier.padding(16.dp)) {
                HeroBanner(
                    item = hero,
                    kicker = if (hero.kind == "live") "Live on Lumore" else "Featured tonight",
                    subtitle = listOf(hero.group, hero.plot).filter { it.isNotBlank() }.joinToString("  ·  "),
                    onPlay = { vm.open(hero, auto = true) },
                    onDetails = { vm.open(hero, auto = false) }
                )
            }
        }
        if (h.cw.isNotEmpty()) {
            item { SectionHeader("Pick up where you left off", "All", onSeeAll) }
            item { WideRail(vm, h.cw.map { it.item }) }
        }
        if (h.movies.isNotEmpty()) {
            item { SectionHeader("Latest movies", "All", onSeeAll) }
            item { WideRail(vm, h.movies) }
        }
        if (h.shows.isNotEmpty()) {
            item { SectionHeader("Series to binge", "All", onSeeAll) }
            item { WideRail(vm, h.shows) }
        }
        if (h.channels.isNotEmpty()) {
            item { SectionHeader("Live channels", "All", onSeeAll) }
            item { WideRail(vm, h.channels) }
        }
    }
}

/** Bright, list-first layout with soft glass cards. */
@Composable
private fun HomeGlass(vm: AppVM, onSeeAll: () -> Unit, onAddSource: () -> Unit) {
    val h = homeData(vm)
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GlassSurface(Modifier.fillMaxWidth(), RoundedCornerShape(LocalThemeSpec.current?.corner ?: 24.dp)) {
                Text(greeting, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("What are we watching?", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                Text(
                    "${vm.catalog["live"].orEmpty().size} channels · ${vm.catalog["vod"].orEmpty().size} movies · ${vm.catalog["series"].orEmpty().size} series",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        if (vm.sources.isEmpty()) item {
            EmptyState("No sources yet", "Connect an Xtream account or a playlist.", "Add source", onAddSource)
        }
        if (h.cw.isNotEmpty()) {
            item { SectionHeader("Continue watching") }
            items(h.cw.take(6)) { p ->
                MediaRow(
                    item = p.item,
                    subtitle = if (p.dur > 0) "${((p.pos * 100) / p.dur).coerceIn(0L, 100L)}% watched" else "In progress",
                    progress = if (p.dur > 0) p.pos.toFloat() / p.dur.toFloat() else -1f,
                    onClick = { vm.open(p.item, auto = true) }
                )
            }
        }
        if (h.channels.isNotEmpty()) {
            item { SectionHeader("Live now", "All", onSeeAll) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(h.channels.take(14), key = { it.key }) { ch ->
                        Column(Modifier.width(92.dp).clip(RoundedCornerShape(16.dp)).clickable { vm.open(ch) }) {
                            Box(
                                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                if (ch.logo.startsWith("http")) AsyncImage(
                                    model = ch.logo, contentDescription = ch.name, contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize().padding(6.dp)
                                )
                            }
                            Text(ch.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
                        }
                    }
                }
            }
        }
        if (h.movies.isNotEmpty()) {
            item { SectionHeader("Fresh movies", "All", onSeeAll) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(h.movies, key = { it.key }) { m ->
                        PosterCard(m, Modifier.width(112.dp), fav = vm.isFav(m), onClick = { vm.open(m) }, onLong = { vm.actionItem = m })
                    }
                }
            }
        }
        if (h.shows.isNotEmpty()) {
            item { SectionHeader("Series", "All", onSeeAll) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(h.shows, key = { it.key }) { m ->
                        PosterCard(m, Modifier.width(112.dp), fav = vm.isFav(m), onClick = { vm.open(m) }, onLong = { vm.actionItem = m })
                    }
                }
            }
        }
    }
}

/** Dark dashboard with big numbers and a live mosaic. */
@Composable
private fun HomeNeon(vm: AppVM, onSeeAll: () -> Unit, onAddSource: () -> Unit) {
    val h = homeData(vm)
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text("TONIGHT ON LUMORE", color = primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("Your library", fontSize = 30.sp, fontWeight = FontWeight.Black)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Channels", vm.catalog["live"].orEmpty().size.toString(), Icons.Default.LiveTv, primary, Modifier.weight(1f)) { onSeeAll() }
                StatTile("Movies", vm.catalog["vod"].orEmpty().size.toString(), Icons.Default.Movie, secondary, Modifier.weight(1f)) { onSeeAll() }
                StatTile("Series", vm.catalog["series"].orEmpty().size.toString(), Icons.Default.Subscriptions, Color(0xFF22D3EE), Modifier.weight(1f)) { onSeeAll() }
            }
        }
        if (vm.sources.isEmpty()) item {
            EmptyState("Welcome to Lumore", "Add your first source to light up the dashboard.", "Add source", onAddSource)
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NeonAction("TV Guide", Icons.Default.CalendarMonth) { vm.openGuide() }
                NeonAction("Downloads", Icons.Default.Download) { vm.showDownloads = true }
                NeonAction("Favorites", Icons.Default.Star) { onSeeAll() }
            }
        }
        if (h.channels.isNotEmpty()) {
            item { SectionHeader("Always on", "All", onSeeAll) }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    h.channels.take(9).chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { ch ->
                                Box(
                                    Modifier.weight(1f).aspectRatio(1.25f).clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .border(1.dp, primary.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                        .clickable { vm.open(ch) }
                                ) {
                                    if (ch.logo.startsWith("http")) AsyncImage(
                                        model = ch.logo, contentDescription = ch.name, contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize().padding(8.dp)
                                    ) else Text(ch.name.take(2), Modifier.align(Alignment.Center), fontWeight = FontWeight.Bold)
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        if (h.movies.isNotEmpty()) {
            item { SectionHeader("Blockbusters", "All", onSeeAll) }
            item { WideRail(vm, h.movies) }
        }
        if (h.shows.isNotEmpty()) {
            item { SectionHeader("Series", "All", onSeeAll) }
            item { WideRail(vm, h.shows) }
        }
    }
}

@Composable
private fun NeonAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
