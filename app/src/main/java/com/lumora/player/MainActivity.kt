@file:OptIn(androidx.media3.common.util.UnstableApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lumora.player

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {
    private val vm: AppVM by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(applicationContext)
        if (savedInstanceState == null) TvLink.init()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFE5173F), background = Color(0xFF0B0B0F), surface = Color(0xFF16161C),
                    onSurface = Color.White, onBackground = Color.White, surfaceVariant = Color(0xFF1C1C22)
                )
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Root(vm) }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (vm.playing != null && Build.VERSION.SDK_INT >= 26) {
            try {
                enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
            } catch (_: Exception) {
            }
        }
    }
}

@Composable
fun Root(vm: AppVM) {
    var tab by remember { mutableStateOf(0) }
    var castSheet by remember { mutableStateOf(false) }
    var form by remember { mutableStateOf<Source?>(null) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        if (vm.message.isNotEmpty()) {
            snack.showSnackbar(vm.message)
            vm.message = ""
        }
    }
    @Suppress("UNUSED_VARIABLE") val ver = TvLink.version.intValue

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            topBar = {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Lumora", fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.weight(1f))
                    val tv = TvLink.activeTv()
                    Text(
                        if (tv == null) "" else if (tv.online) "● " + tv.name else "○ " + tv.name,
                        color = if (tv?.online == true) Color(0xFF4ADE80) else Color(0xFFFBBF24), fontSize = 13.sp
                    )
                    IconButton(onClick = { castSheet = true }) { Icon(Icons.Default.Cast, "Cast") }
                    IconButton(onClick = { vm.showSettings = true }) { Icon(Icons.Default.Settings, "Settings") }
                }
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Default.Home, null) }, label = { Text("Home") })
                    NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Default.VideoLibrary, null) }, label = { Text("Catalog") })
                    NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Default.Star, null) }, label = { Text("Favorites") })
                    NavigationBarItem(tab == 3, { tab = 3 }, { Icon(Icons.Default.Search, null) }, label = { Text("Search") })
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    0 -> HomeTab(vm, onSeeAll = { tab = 1 })
                    1 -> CatalogTab(vm)
                    2 -> ItemList(vm, vm.favs.toList())
                    else -> SearchTab(vm)
                }
            }
        }

        if (vm.showSettings) SettingsScreen(vm, onEdit = { form = it })
        if (form != null) SourceForm(vm, form!!) { form = null }
        if (castSheet) CastSheet(vm) { castSheet = false }
        vm.seriesOpen?.let { SeriesDialog(vm, it) }
        vm.playing?.let { PlayerScreen(vm, it) }
    }
}

@Composable
fun SectionHeader(title: String, action: String?, onAction: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (action != null) TextButton(onClick = onAction) { Text(action, color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
fun TileCard(item: Item, w: Int, ratio: Float, progress: Float = -1f, onClick: () -> Unit) {
    Column(Modifier.width(w.dp).clickable { onClick() }) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1C1C22))
        ) {
            if (item.logo.startsWith("http")) AsyncImage(item.logo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(item.name.take(2).uppercase(), Modifier.align(Alignment.Center), color = Color.Gray, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (progress >= 0f) Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp).background(Color(0xFFE5173F))
            )
        }
        Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun HomeTab(vm: AppVM, onSeeAll: () -> Unit) {
    LaunchedEffect(vm.activeSrc) { vm.load("live"); vm.load("vod") }
    val cw = vm.continueWatching()
    val channels = vm.mostViewed().map { v -> v.item }.ifEmpty { (vm.catalog["live"] ?: emptyList()).take(12) }
    val movies = (vm.catalog["vod"] ?: emptyList()).sortedByDescending { m -> m.id.toIntOrNull() ?: 0 }.take(12)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        item {
            Text("Home", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp))
        }
        if (vm.sources.isEmpty()) item { Text("Add a source in Settings to start", Modifier.padding(16.dp), color = Color.Gray) }
        if (cw.isNotEmpty()) {
            item { SectionHeader("Continue watching", null) }
            item {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(cw) { p ->
                        TileCard(p.item, 140, 2f / 3f, if (p.dur > 0) p.pos.toFloat() / p.dur.toFloat() else 0f) { vm.open(p.item) }
                    }
                }
            }
        }
        if (channels.isNotEmpty()) {
            item { SectionHeader("Most viewed", "See all", onSeeAll) }
            item {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(channels) { ch -> TileCard(ch, 120, 1f) { vm.open(ch) } }
                }
            }
        }
        if (movies.isNotEmpty()) {
            item { SectionHeader("New releases (Movies)", "See all", onSeeAll) }
            item {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(movies) { m -> TileCard(m, 120, 2f / 3f) { vm.open(m) } }
                }
            }
        }
    }
}

@Composable
fun SettingHeader(t: String) {
    Text(t, Modifier.padding(top = 14.dp, bottom = 2.dp), color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
fun ActionRow(title: String, sub: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }.padding(14.dp)
    ) {
        Text(title)
        Text(sub, fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
fun SettingsScreen(vm: AppVM, onEdit: (Source) -> Unit) {
    var pinDialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.showSettings = false }) { Icon(Icons.Default.ArrowBack, "Back") }
            Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { SettingHeader("Sources") }
            items(vm.sources.toList()) { s ->
                val on = s.id == vm.activeSrc
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (on) Color(0xFF2A1520) else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { vm.selectSource(s.id) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name.ifBlank { s.url }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(s.type.uppercase() + (if (on) " · active" else ""), fontSize = 12.sp, color = Color.Gray)
                    }
                    if (TvLink.paired()) IconButton(onClick = { vm.sendSourceToTv(s) }) { Icon(Icons.Default.Cast, "Send to TV") }
                    IconButton(onClick = { onEdit(s) }) { Icon(Icons.Default.Edit, "Edit") }
                }
            }
            item {
                Button(
                    onClick = { onEdit(Source("s" + System.currentTimeMillis().toString(36), "", "xtream", "")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("+ Add source") }
            }
            item { SettingHeader("Browsing") }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sort categories", Modifier.weight(1f))
                    FilterChip(vm.sort == "count", { vm.changeSort("count") }, { Text("Most items") })
                    Spacer(Modifier.width(6.dp))
                    FilterChip(vm.sort == "az", { vm.changeSort("az") }, { Text("A-Z") })
                }
            }
            item { SettingHeader("Playback") }
            item { SwitchRow("Resume where I stopped", vm.resumeOn) { v -> vm.setResume(v) } }
            item { SettingHeader("Parental control") }
            item {
                ActionRow(
                    if (vm.pin.isEmpty()) "Set adult PIN" else "Change or remove adult PIN",
                    "Adult categories ask for a 4-digit PIN"
                ) { pinDialog = true }
            }
            item { SettingHeader("Data") }
            item { ActionRow("Clear continue watching", "Removes saved progress") { vm.clearProgress() } }
            item { ActionRow("Clear favorites", "Removes all favorites") { vm.clearFavs() } }
            item { SettingHeader("About") }
            item { Text("Lumora 1.1.0", color = Color.Gray, modifier = Modifier.padding(4.dp)) }
        }
    }
    if (pinDialog) PinDialog(vm) { pinDialog = false }
}

@Composable
fun PinDialog(vm: AppVM, close: () -> Unit) {
    var p by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (vm.pin.isEmpty()) "Set adult PIN" else "Change adult PIN") },
        text = {
            OutlinedTextField(
                value = p,
                onValueChange = { v -> if (v.length <= 4 && v.all { c -> c.isDigit() }) { p = v } },
                label = { Text("4-digit PIN (empty removes it)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation()
            )
        },
        confirmButton = {
            Button(onClick = {
                if (p.isEmpty() || p.length == 4) {
                    vm.changePin(p)
                    vm.toast(if (p.isEmpty()) "PIN removed" else "PIN saved")
                    close()
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } }
    )
}

@Composable
fun PinUnlock(vm: AppVM, onOk: () -> Unit, close: () -> Unit) {
    var p by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Adult category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Enter your PIN to open this category", fontSize = 13.sp, color = Color.Gray)
                OutlinedTextField(
                    value = p,
                    onValueChange = { v -> p = v.take(4) },
                    singleLine = true,
                    isError = wrong,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation()
                )
                if (wrong) Text("Wrong PIN", color = Color(0xFFFF6B6B), fontSize = 13.sp)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (p == vm.pin) { vm.unlocked = true; onOk() } else { wrong = true; p = "" }
            }) { Text("Unlock") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } }
    )
}

@Composable
fun SourceForm(vm: AppVM, start: Source, close: () -> Unit) {
    var name by remember { mutableStateOf(start.name) }
    var type by remember { mutableStateOf(start.type) }
    var url by remember { mutableStateOf(start.url) }
    var user by remember { mutableStateOf(start.user) }
    var pass by remember { mutableStateOf(start.pass) }
    var format by remember { mutableStateOf(start.format) }
    var menu by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    val exists = vm.sources.any { it.id == start.id }

    fun build() = Source(start.id, name.trim().ifBlank { url.trim() }, type, url.trim(), user.trim(), pass.trim(), format)

    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (exists) "Edit source" else "Add source") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box {
                    OutlinedButton(onClick = { menu = true }, Modifier.fillMaxWidth()) { Text(if (type == "xtream") "Xtream Codes" else "M3U playlist URL") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Xtream Codes") }, onClick = { type = "xtream"; menu = false })
                        DropdownMenuItem(text = { Text("M3U playlist URL") }, onClick = { type = "m3u"; menu = false })
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text(if (type == "xtream") "Server URL" else "Playlist URL") }, singleLine = true)
                if (type == "xtream") {
                    OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true)
                    OutlinedTextField(pass, { pass = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(format == "m3u8", { format = "m3u8" }, { Text("HLS (m3u8)") })
                        Spacer(Modifier.width(8.dp))
                        FilterChip(format == "ts", { format = "ts" }, { Text("MPEG-TS") })
                    }
                }
                if (testing) CircularProgressIndicator(Modifier.size(22.dp))
                if (result.isNotEmpty()) Text(result, color = if (result == "Connection OK") Color(0xFF4ADE80) else Color(0xFFFF6B6B), fontSize = 13.sp)
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    testing = true; result = ""
                    vm.test(build()) { err -> testing = false; result = err ?: "Connection OK" }
                }) { Text("Test") }
                Button(onClick = { vm.saveSource(build()); close() }) { Text("Save") }
            }
        },
        dismissButton = {
            Row {
                if (exists) TextButton(onClick = { vm.deleteSource(start.id); close() }) { Icon(Icons.Default.Delete, "Delete") }
                TextButton(onClick = close) { Text("Cancel") }
            }
        }
    )
}

@Composable
fun CatalogTab(vm: AppVM) {
    val kinds = listOf("live" to "Live", "vod" to "Movies", "series" to "Series")
    var k by remember { mutableStateOf(0) }
    var group by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<String?>(null) }
    val kind = kinds[k].first
    LaunchedEffect(kind, vm.activeSrc) { vm.load(kind) }
    val items = vm.catalog[kind] ?: emptyList()
    val groups = remember(items, vm.sort) {
        val counts = items.groupingBy { it.group }.eachCount()
        if (vm.sort == "az") counts.keys.sortedBy { g -> g.lowercase() }
        else counts.entries.sortedByDescending { e -> e.value }.map { e -> e.key }
    }
    Column(Modifier.fillMaxSize()) {
        TabRow(k) { kinds.forEachIndexed { i, p -> Tab(k == i, { k = i; group = null }, text = { Text(p.second) }) } }
        if (vm.loading[kind] == true) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if (vm.src() == null) Text("No sources yet", Modifier.padding(24.dp), color = Color.Gray)
        else {
            if (groups.size > 1) LazyRow(
                Modifier.padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)
            ) {
                item { FilterChip(group == null, { group = null }, { Text("All") }) }
                items(groups) { g ->
                    FilterChip(group == g, { if (vm.locked(g)) { pending = g } else { group = g } }, { Text(g, maxLines = 1) })
                }
            }
            ItemList(vm, if (group == null) items else items.filter { it.group == group })
        }
    }
    pending?.let { g -> PinUnlock(vm, { group = g; pending = null }) { pending = null } }
}

@Composable
fun ItemList(vm: AppVM, list: List<Item>) {
    if (list.isEmpty()) {
        Text("Nothing found", Modifier.padding(24.dp), color = Color.Gray)
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(list.take(3000)) { it ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { vm.open(it) }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF26262B))) {
                    if (it.logo.startsWith("http")) AsyncImage(it.logo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(it.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                    Text(it.group, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = Color.Gray)
                }
                if (it.kind != "series") IconButton(onClick = { vm.playHere(it) }) { Icon(Icons.Default.PlayArrow, "Play on phone") }
                IconButton(onClick = { vm.toggleFav(it) }) { Icon(if (vm.isFav(it)) Icons.Default.Star else Icons.Default.StarBorder, "Favorite", tint = if (vm.isFav(it)) Color(0xFFFBBF24) else Color.Gray) }
            }
        }
    }
}

@Composable
fun SearchTab(vm: AppVM) {
    var q by remember { mutableStateOf("") }
    LaunchedEffect(vm.activeSrc) { listOf("live", "vod", "series").forEach { vm.load(it) } }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(10.dp), singleLine = true, placeholder = { Text("Search channels, movies and series") })
        val all = (vm.catalog["live"] ?: emptyList()) + (vm.catalog["vod"] ?: emptyList()) + (vm.catalog["series"] ?: emptyList())
        val res = remember(q, all.size) { if (q.trim().length < 2) emptyList() else all.filter { it.name.contains(q.trim(), true) }.take(200) }
        if (q.trim().length < 2) Text("Type to search", Modifier.padding(24.dp), color = Color.Gray) else ItemList(vm, res)
    }
}

@Composable
fun SeriesDialog(vm: AppVM, serie: Item) {
    AlertDialog(
        onDismissRequest = { vm.seriesOpen = null },
        title = { Text(serie.name, maxLines = 2) },
        text = {
            if (vm.episodesLoading) CircularProgressIndicator()
            else LazyColumn(Modifier.height(380.dp)) {
                items(vm.episodes) { e ->
                    Row(Modifier.fillMaxWidth().clickable {
                        vm.seriesOpen = null
                        val s = vm.srcOf(e.srcId) ?: vm.src()
                        if (s != null) {
                            val url = Xtream.resolve(s, e)
                            val req = PlayReq(url, serie.name + " · " + e.name, "ep", e, s)
                            if (TvLink.connected()) vm.cast(req, 0, false) else vm.playing = req
                        }
                    }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("S${e.season}E${e.num}", Modifier.width(64.dp), color = Color.Gray, fontSize = 12.sp)
                        Text(e.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.seriesOpen = null }) { Text("Close") } }
    )
}

@Composable
fun CastSheet(vm: AppVM, close: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val ver = TvLink.version.intValue
    var code by remember { mutableStateOf("") }
    var nm by remember { mutableStateOf("") }
    val tvs = TvLink.list()
    val act = TvLink.activeTv()
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Lumora TV devices") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tvs.forEach { t ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (t.active) Color(0xFF2A1520) else Color(0xFF1C1C22))
                            .clickable { TvLink.select(t.id) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(t.name)
                            Text((if (t.online) "Online" else "Offline") + " · " + t.label, fontSize = 11.sp, color = if (t.online) Color(0xFF4ADE80) else Color.Gray)
                        }
                        IconButton(onClick = { TvLink.remove(t.id) }) { Icon(Icons.Default.Delete, "Remove") }
                    }
                }
                OutlinedTextField(code, { code = it }, label = { Text("TV code") }, singleLine = true, placeholder = { Text("a3cc-db44-0851-2719") })
                OutlinedTextField(nm, { nm = it }, label = { Text("TV name (optional)") }, singleLine = true)
                Button(onClick = {
                    val id = TvLink.add(code, nm)
                    if (id.isEmpty()) vm.toast("Invalid TV code") else { code = ""; nm = ""; vm.toast("TV linked") }
                }, Modifier.fillMaxWidth()) { Text("Link with TV code") }
                if (act != null && act.online) Remote(act)
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("Close") } }
    )
}

@Composable
fun Remote(tv: TvLink.TvView) {
    fun key(k: String) = TvLink.send("key", org.json.JSONObject().put("key", k))
    val st = tv.status
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        val title = st?.optString("title").orEmpty()
        if (title.isNotEmpty()) Text("▶ $title", fontSize = 12.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row { RB("▲") { key("up") } }
        Row { RB("◀") { key("left") }; RB("OK") { key("ok") }; RB("▶") { key("right") } }
        Row { RB("▼") { key("down") } }
        Row { RB("Back") { key("back") }; RB("⏯") { TvLink.send("toggle") }; RB("⏹") { TvLink.send("stop") }; RB("CH+") { key("chup") }; RB("CH-") { key("chdown") } }
    }
}

@Composable
fun RB(t: String, f: () -> Unit) {
    Button(onClick = f, modifier = Modifier.padding(2.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26262B))) { Text(t, fontSize = 13.sp) }
}
