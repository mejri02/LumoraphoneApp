@file:OptIn(
    androidx.media3.common.util.UnstableApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.lumora.player

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: AppVM by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(applicationContext)
        TvLink.init()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(vm.accentInt()), onPrimary = Color.White, secondary = Color(vm.accentInt()),
                    background = Color(0xFF090B11), surface = Color(0xFF131722),
                    onSurface = Color(0xFFEDEFF5), onBackground = Color(0xFFEDEFF5), surfaceVariant = Color(0xFF1B2030)
                )
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Root(vm) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        TvLink.resume()
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
    var remoteSheet by remember { mutableStateOf(false) }
    var form by remember { mutableStateOf<Source?>(null) }
    BackHandler(enabled = vm.playing == null && (vm.showSettings || vm.seriesOpen != null || castSheet || remoteSheet || form != null || tab != 0)) {
        when {
            form != null -> form = null
            remoteSheet -> remoteSheet = false
            castSheet -> castSheet = false
            vm.seriesOpen != null -> vm.seriesOpen = null
            vm.showSettings -> vm.showSettings = false
            else -> tab = 0
        }
    }
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
                    if (TvLink.paired()) IconButton(onClick = { remoteSheet = true }) { Icon(Icons.Default.SettingsRemote, "Remote") }
                    IconButton(onClick = { castSheet = true }) {
                        Icon(
                            Icons.Default.Cast, "Cast",
                            tint = when {
                                !vm.castOn -> Color.Gray
                                TvLink.paired() -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
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
                    2 -> Column(Modifier.fillMaxSize()) {
                        SortPills(vm)
                        PosterGrid(vm, vm.favs.toList(), Modifier.weight(1f))
                    }
                    else -> SearchTab(vm)
                }
            }
        }

        if (vm.showSettings) SettingsScreen(vm, onEdit = { form = it })
        if (form != null) SourceForm(vm, form!!) { form = null }
        if (castSheet) CastSheet(vm) { castSheet = false }
        if (remoteSheet) RemoteSheet { remoteSheet = false }
        vm.seriesOpen?.let { SeriesDialog(vm, it) }
        vm.actionItem?.let { ItemActions(vm, it) }
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

/** Poster card: artwork, rating badge, favorite button, progress bar, download badge and title. */
@Composable
fun PosterCard(
    item: Item,
    modifier: Modifier = Modifier,
    ratio: Float = 2f / 3f,
    progress: Float = -1f,
    fav: Boolean? = null,
    dl: DlInfo? = null,
    onFav: () -> Unit = {},
    onClick: () -> Unit,
    onLong: () -> Unit = {}
) {
    Column(modifier.combinedClickable(onClick = onClick, onLongClick = onLong)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1C1C22))
        ) {
            if (item.logo.startsWith("http")) {
                AsyncImage(
                    model = item.logo, contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    item.name.take(2).uppercase(), Modifier.align(Alignment.Center),
                    color = Color.Gray, fontSize = 22.sp, fontWeight = FontWeight.Bold
                )
            }
            if (item.rating > 0.0) {
                Text(
                    "★ " + "%.1f".format(item.rating),
                    Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(6.dp))
                        .background(Color(0xB3000000)).padding(horizontal = 6.dp, vertical = 2.dp),
                    color = Color(0xFFFBBF24), fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                )
            }
            if (fav != null) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(34.dp).clip(CircleShape)
                        .background(Color(0xB3000000)).clickable { onFav() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (fav) Icons.Default.Star else Icons.Default.StarBorder, "Favorite",
                        tint = if (fav) Color(0xFFFBBF24) else Color.White, modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (progress >= 0f) {
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            if (dl != null) DownloadBadge(dl, Modifier.align(Alignment.BottomEnd).padding(6.dp))
        }
        Text(
            item.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, lineHeight = 18.sp,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp)
        )
    }
}

@Composable
fun DownloadBadge(dl: DlInfo, modifier: Modifier) {
    val bg = when {
        dl.ready -> Color(0xFF10B981)
        dl.failed -> Color(0xFFEF4444)
        else -> Color(0xB3000000)
    }
    val label = when {
        dl.ready -> "✓ Offline"
        dl.failed -> "Failed"
        else -> "${dl.percent}%"
    }
    Text(
        label,
        modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 6.dp, vertical = 2.dp),
        color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun SortPills(vm: AppVM) {
    LazyRow(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(ITEM_SORTS) { (key, label) ->
            val on = vm.itemSort == key
            Box(
                Modifier.clip(RoundedCornerShape(14.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary else Color(0xFF2A2D36))
                    .clickable { vm.changeItemSort(key) }
                    .padding(horizontal = 22.dp, vertical = 12.dp)
            ) {
                Text(
                    label,
                    color = if (on) Color.White else Color(0xFFD0D3DC),
                    fontSize = 15.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun PosterGrid(vm: AppVM, list: List<Item>, modifier: Modifier = Modifier.fillMaxSize()) {
    if (list.isEmpty()) {
        Text("Nothing found", Modifier.padding(24.dp), color = Color.Gray)
        return
    }
    val sorted = remember(list, vm.itemSort) { vm.sortItems(list).take(3000) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(sorted, key = { it.key }) { it ->
            PosterCard(
                item = it,
                modifier = Modifier.fillMaxWidth(),
                ratio = if (it.kind == "live") 1f else 2f / 3f,
                fav = vm.isFav(it),
                dl = vm.dlInfo[it.key],
                onFav = { vm.toggleFav(it) },
                onClick = { vm.open(it) },
                onLong = { vm.actionItem = it }
            )
        }
    }
}

@Composable
fun HomeTab(vm: AppVM, onSeeAll: () -> Unit) {
    LaunchedEffect(vm.activeSrc) {
        vm.load("live")
        vm.load("vod")
    }
    val cw = vm.continueWatching()
    val channels = vm.mostViewed().map { v -> v.item }.ifEmpty { (vm.catalog["live"] ?: emptyList()).take(12) }
    val movies = (vm.catalog["vod"] ?: emptyList()).sortedByDescending { m -> m.added }.take(12)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text("Home", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp))
        }
        if (vm.sources.isEmpty()) item { Text("Add a source in Settings to start", Modifier.padding(16.dp), color = Color.Gray) }
        if (cw.isNotEmpty()) {
            item { SectionHeader("Continue watching", null) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(cw) { p ->
                        PosterCard(
                            item = p.item,
                            modifier = Modifier.width(140.dp),
                            ratio = 2f / 3f,
                            progress = if (p.dur > 0) p.pos.toFloat() / p.dur.toFloat() else 0f,
                            fav = vm.isFav(p.item),
                            onFav = { vm.toggleFav(p.item) },
                            onClick = { vm.open(p.item) },
                            onLong = { vm.actionItem = p.item }
                        )
                    }
                }
            }
        }
        if (channels.isNotEmpty()) {
            item { SectionHeader("Most viewed", "See all", onSeeAll) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(channels) { ch ->
                        PosterCard(
                            item = ch, modifier = Modifier.width(120.dp), ratio = 1f,
                            fav = vm.isFav(ch), onFav = { vm.toggleFav(ch) },
                            onClick = { vm.open(ch) }, onLong = { vm.actionItem = ch }
                        )
                    }
                }
            }
        }
        if (movies.isNotEmpty()) {
            item { SectionHeader("New releases (Movies)", "See all", onSeeAll) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(movies) { m ->
                        PosterCard(
                            item = m, modifier = Modifier.width(120.dp), ratio = 2f / 3f,
                            fav = vm.isFav(m), dl = vm.dlInfo[m.key], onFav = { vm.toggleFav(m) },
                            onClick = { vm.open(m) }, onLong = { vm.actionItem = m }
                        )
                    }
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
            contentPadding = PaddingValues(16.dp),
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
            item { SwitchRow("Play on TV when it is connected", vm.castOn) { v -> vm.changeCastOn(v) } }
            item { SettingHeader("Appearance") }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Accent color", Modifier.weight(1f))
                    vm.accents.forEachIndexed { i, c ->
                        Box(
                            Modifier.padding(start = 8.dp).size(if (i == vm.accentIdx) 30.dp else 24.dp)
                                .clip(CircleShape).background(Color(c)).clickable { vm.changeAccent(i) }
                        )
                    }
                }
            }
            item { SettingHeader("Downloads (offline)") }
            if (vm.downloads.isEmpty()) item { Text("No downloads yet. Use the long-press menu on a movie or episode.", color = Color.Gray, fontSize = 13.sp) }
            items(vm.downloads.toList()) { d -> DownloadRow(vm, d) }
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
            item { Text("Lumora 1.4.0", color = Color.Gray, modifier = Modifier.padding(4.dp)) }
        }
    }
    if (pinDialog) PinDialog(vm) { pinDialog = false }
}

@Composable
fun DownloadRow(vm: AppVM, d: Download) {
    val info = vm.dlInfo[d.item.key]
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(d.item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    info?.status ?: "Preparing…",
                    fontSize = 12.sp,
                    color = if (info?.failed == true) Color(0xFFFF6B6B) else Color.Gray
                )
            }
            if (info?.ready == true) IconButton(onClick = { vm.playOffline(d) }) { Icon(Icons.Default.PlayArrow, "Play") }
            IconButton(onClick = { vm.deleteDownload(d) }) { Icon(Icons.Default.Delete, "Delete") }
        }
        if (info != null && !info.ready && !info.failed) {
            Spacer(Modifier.height(8.dp))
            if (info.total > 0) {
                LinearProgressIndicator(progress = { info.percent / 100f }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(
                "${info.percent}% · ${fmtBytes(info.done)} / ${fmtBytes(info.total)}",
                fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
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
                onValueChange = { v -> if (v.length <= 4 && v.all { c -> c.isDigit() }) p = v },
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
                if (p == vm.pin) {
                    vm.unlocked = true
                    onOk()
                } else {
                    wrong = true
                    p = ""
                }
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
                    OutlinedButton(onClick = { menu = true }, Modifier.fillMaxWidth()) {
                        Text(if (type == "xtream") "Xtream Codes" else "M3U playlist URL")
                    }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Xtream Codes") }, onClick = { type = "xtream"; menu = false })
                        DropdownMenuItem(text = { Text("M3U playlist URL") }, onClick = { type = "m3u"; menu = false })
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text(if (type == "xtream") "Server URL" else "Playlist URL") }, singleLine = true)
                if (type == "xtream") {
                    OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true)
                    OutlinedTextField(
                        pass, { pass = it }, label = { Text("Password") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(format == "m3u8", { format = "m3u8" }, { Text("HLS (m3u8)") })
                        Spacer(Modifier.width(8.dp))
                        FilterChip(format == "ts", { format = "ts" }, { Text("MPEG-TS") })
                    }
                }
                if (testing) CircularProgressIndicator(Modifier.size(22.dp))
                if (result.isNotEmpty()) {
                    Text(
                        result,
                        color = if (result == "Connection OK") Color(0xFF4ADE80) else Color(0xFFFF6B6B),
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    testing = true
                    result = ""
                    vm.test(build()) { err ->
                        testing = false
                        result = err ?: "Connection OK"
                    }
                }) { Text("Test") }
                Button(enabled = !testing, onClick = {
                    testing = true
                    result = ""
                    val cand = build()
                    vm.test(cand) { err ->
                        testing = false
                        if (err == null) {
                            vm.saveSource(cand)
                            close()
                        } else {
                            result = err
                        }
                    }
                }) { Text(if (type == "xtream") "Login" else "Save") }
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
    var k by remember { mutableStateOf(1) }
    var group by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<String?>(null) }
    var gq by remember { mutableStateOf("") }
    val kind = kinds[k].first
    LaunchedEffect(kind, vm.activeSrc) { vm.load(kind) }
    val all = vm.catalog[kind] ?: emptyList()
    val groups = remember(all, vm.sort) {
        val counts = all.groupingBy { it.group }.eachCount()
        if (vm.sort == "az") counts.keys.sortedBy { g -> g.lowercase() }
        else counts.entries.sortedByDescending { e -> e.value }.map { e -> e.key }
    }
    val shown = groups.filter { g -> g.contains(gq.trim(), ignoreCase = true) }
    Column(Modifier.fillMaxSize()) {
        TabRow(k) {
            kinds.forEachIndexed { i, p ->
                Tab(k == i, { k = i; group = null; gq = "" }, text = { Text(p.second) })
            }
        }
        if (vm.loading[kind] == true) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (vm.src() == null) {
            Text("No sources yet", Modifier.padding(24.dp), color = Color.Gray)
        } else {
            Text(
                (group ?: kinds[k].second).uppercase(),
                Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp),
                fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            SortPills(vm)
            OutlinedTextField(
                value = gq,
                onValueChange = { v -> gq = v },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                placeholder = { Text("Search categories") }
            )
            if (shown.isNotEmpty()) {
                LazyRow(
                    Modifier.padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    item { FilterChip(group == null, { group = null }, { Text("All") }) }
                    items(shown) { g ->
                        FilterChip(
                            group == g,
                            { if (vm.locked(g)) pending = g else group = g },
                            { Text(g, maxLines = 1) }
                        )
                    }
                }
            }
            PosterGrid(vm, if (group == null) all else all.filter { it.group == group }, Modifier.weight(1f))
        }
    }
    pending?.let { g -> PinUnlock(vm, { group = g; pending = null }) { pending = null } }
}

@Composable
fun SearchTab(vm: AppVM) {
    var q by remember { mutableStateOf("") }
    LaunchedEffect(vm.activeSrc) { listOf("live", "vod", "series").forEach { vm.load(it) } }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            q, { q = it }, Modifier.fillMaxWidth().padding(10.dp), singleLine = true,
            placeholder = { Text("Search channels, movies and series") }
        )
        SortPills(vm)
        val everything = (vm.catalog["live"] ?: emptyList()) + (vm.catalog["vod"] ?: emptyList()) + (vm.catalog["series"] ?: emptyList())
        val res = remember(q, everything.size) {
            if (q.trim().length < 2) emptyList() else everything.filter { it.name.contains(q.trim(), true) }.take(200)
        }
        if (q.trim().length < 2) Text("Type to search", Modifier.padding(24.dp), color = Color.Gray)
        else PosterGrid(vm, res, Modifier.weight(1f))
    }
}

@Composable
fun SeriesDialog(vm: AppVM, serie: Item) {
    AlertDialog(
        onDismissRequest = { vm.seriesOpen = null },
        title = { Text(serie.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            if (vm.episodesLoading) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(Modifier.height(380.dp)) {
                    items(vm.episodes) { e ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                vm.seriesOpen = null
                                val s = vm.srcOf(e.srcId) ?: vm.src()
                                if (s != null) {
                                    vm.playOrCast(PlayReq(Xtream.resolve(s, e), serie.name + " · " + e.name, "ep", e, s))
                                }
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("S${e.season}E${e.num}", Modifier.width(64.dp), color = Color.Gray, fontSize = 12.sp)
                            Text(e.name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = { vm.download(e.copy(name = serie.name + " · " + e.name)) }) {
                                Icon(Icons.Default.Download, "Download")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.seriesOpen = null }) { Text("Close") } }
    )
}

@Composable
fun ActionLine(t: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, Modifier.fillMaxWidth()) { Text(t, Modifier.fillMaxWidth()) }
}

/** Long-press menu on any poster. */
@Composable
fun ItemActions(vm: AppVM, item: Item) {
    val close = { vm.actionItem = null }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (item.kind == "series") {
                    ActionLine("Open episodes") { close(); vm.open(item) }
                } else {
                    ActionLine("Play on this phone") { close(); vm.playHere(item) }
                }
                if (item.kind == "vod") ActionLine("Download for offline") { close(); vm.download(item) }
                ActionLine(if (vm.isFav(item)) "Remove from favorites" else "Add to favorites") {
                    vm.toggleFav(item)
                    close()
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("Close") } }
    )
}

@Composable
fun CastSheet(vm: AppVM, close: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val ver = TvLink.version.intValue
    var code by remember { mutableStateOf("") }
    var nm by remember { mutableStateOf("") }
    val tvs = TvLink.list()
    val scope = rememberCoroutineScope()
    fun linkAndVerify(input: String, name: String): Boolean {
        val id = TvLink.add(input, name)
        if (id.isEmpty()) return false
        vm.toast("Linking… waiting for the TV to answer")
        scope.launch {
            vm.toast(
                when (TvLink.verify(id)) {
                    "ok" -> "TV connected ✓"
                    else -> "TV did not answer. Open the TV app on the link screen and check that the phone and TV are on the same Wi-Fi."
                }
            )
        }
        return true
    }
    val scan = rememberLauncherForActivityResult(ScanContract()) { r ->
        val txt = r.contents
        if (txt != null && !linkAndVerify(txt, nm)) vm.toast("Not a Lumora TV code")
    }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Lumora TV") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SwitchRow("Play on TV when it is connected", vm.castOn) { v -> vm.changeCastOn(v) }
                Text(
                    if (vm.castOn) "Movies and channels you open are sent to the TV."
                    else "Cast is off. Everything plays on this phone. You can still cast from the player.",
                    fontSize = 12.sp, color = Color.Gray
                )
                tvs.forEach { t ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (t.active) Color(0xFF2A1520) else Color(0xFF1C1C22))
                            .clickable { TvLink.select(t.id) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(t.name)
                            Text(
                                (if (t.online) "Online" else "Offline") + " · " + t.label,
                                fontSize = 11.sp, color = if (t.online) Color(0xFF4ADE80) else Color.Gray
                            )
                        }
                        IconButton(onClick = { TvLink.remove(t.id) }) { Icon(Icons.Default.Delete, "Remove") }
                    }
                }
                Button(onClick = {
                    scan.launch(
                        ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setPrompt("Scan the QR code shown on your TV")
                            .setBeepEnabled(false).setOrientationLocked(false)
                    )
                }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.QrCodeScanner, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Pair with QR code")
                }
                Text("or enter the code manually", fontSize = 12.sp, color = Color.Gray)
                OutlinedTextField(code, { code = it }, label = { Text("TV code") }, singleLine = true, placeholder = { Text("a3cc-db44-0851-2719") })
                OutlinedTextField(nm, { nm = it }, label = { Text("TV name (optional)") }, singleLine = true)
                OutlinedButton(onClick = {
                    if (!linkAndVerify(code, nm)) vm.toast("Invalid TV code") else {
                        code = ""
                        nm = ""
                    }
                }, Modifier.fillMaxWidth()) { Text("Link with TV code") }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("Close") } }
    )
}

@Composable
fun RemoteSheet(close: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val ver = TvLink.version.intValue
    val tv = TvLink.activeTv()
    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (tv == null) "Remote" else "Remote · " + tv.name) },
        text = {
            if (tv == null) {
                Text("Link a TV first.", color = Color.Gray)
            } else {
                Column {
                    Text(
                        if (tv.online) "Online" else "TV is offline",
                        fontSize = 12.sp, color = if (tv.online) Color(0xFF4ADE80) else Color(0xFFFBBF24)
                    )
                    Spacer(Modifier.height(8.dp))
                    Remote(tv)
                }
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
        Row { RB("Back") { key("back") }; RB("Info") { key("info") }; RB("⏯") { TvLink.send("toggle") }; RB("⏹") { TvLink.send("stop") } }
        Row { RB("⏪") { key("rw") }; RB("⏩") { key("ff") }; RB("CH+") { key("chup") }; RB("CH-") { key("chdown") } }
        Row { RB("1") { key("1") }; RB("2") { key("2") }; RB("3") { key("3") }; RB("4") { key("4") }; RB("5") { key("5") } }
        Row { RB("6") { key("6") }; RB("7") { key("7") }; RB("8") { key("8") }; RB("9") { key("9") }; RB("0") { key("0") } }
    }
}

@Composable
fun RB(t: String, f: () -> Unit) {
    Button(
        onClick = f, modifier = Modifier.padding(2.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26262B))
    ) { Text(t, fontSize = 13.sp) }
}
