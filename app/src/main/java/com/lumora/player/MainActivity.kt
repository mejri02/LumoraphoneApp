@file:OptIn(
    androidx.media3.common.util.UnstableApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.lumora.player

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: AppVM by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
        Store.init(applicationContext)
        CatalogCache.init(applicationContext)
        TvLink.init()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(vm.accentInt()), onPrimary = Color.White, secondary = Color(vm.accentInt()),
                    background = Color(0xFF09090E), surface = Color(0xFF13131A),
                    onSurface = Color(0xFFEDEFF5), onBackground = Color(0xFFEDEFF5), surfaceVariant = Color(0xFF1D1D27)
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

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        vm.pip = isInPictureInPictureMode
    }
}

@Composable
fun TopBar(vm: AppVM, onRemote: () -> Unit, onCast: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Brush.linearGradient(listOf(primary, accent2()))),
            contentAlignment = Alignment.Center
        ) { Text("L", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp) }
        Spacer(Modifier.width(10.dp))
        Text("Lumora", fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        val tv = TvLink.activeTv()
        if (tv != null) IconButton(onClick = onRemote, modifier = Modifier.size(42.dp)) { Icon(Icons.Default.SettingsRemote, "Remote") }
        IconButton(onClick = onCast, modifier = Modifier.size(42.dp)) {
            Box {
                Icon(
                    Icons.Default.Cast, "Cast",
                    tint = when {
                        !vm.castOn -> Color.Gray
                        tv != null -> primary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                if (tv != null) Box(
                    Modifier.align(Alignment.TopEnd).size(8.dp).clip(CircleShape)
                        .background(if (tv.online) Color(0xFF4ADE80) else Color(0xFFFBBF24))
                )
            }
        }
        IconButton(onClick = { vm.showSettings = true }, modifier = Modifier.size(42.dp)) { Icon(Icons.Default.Settings, "Settings") }
    }
}

@Composable
fun Root(vm: AppVM) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var castSheet by remember { mutableStateOf(false) }
    var remoteSheet by remember { mutableStateOf(false) }
    var form by remember { mutableStateOf<Source?>(null) }
    BackHandler(enabled = tab != 0) { tab = 0 }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        if (vm.message.isNotEmpty()) {
            val m = vm.message
            vm.message = ""
            snack.showSnackbar(m)
        }
    }
    @Suppress("UNUSED_VARIABLE") val ver = TvLink.version.intValue

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = { TopBar(vm, onRemote = { remoteSheet = true }, onCast = { castSheet = true }) },
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF0F0F15)) {
                    val navItems = listOf(
                        Triple("Home", Icons.Default.Home, 0), Triple("Catalog", Icons.Default.VideoLibrary, 1),
                        Triple("Favorites", Icons.Default.Star, 2), Triple("Search", Icons.Default.Search, 3)
                    )
                    navItems.forEach { (label, icon, i) ->
                        NavigationBarItem(
                            tab == i, { tab = i }, { Icon(icon, null) }, label = { Text(label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                        )
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    0 -> HomeTab(vm, onSeeAll = { tab = 1 }, onAddSource = { vm.showSettings = true })
                    1 -> CatalogTab(vm)
                    2 -> FavoritesTab(vm)
                    else -> SearchTab(vm)
                }
            }
        }

        vm.openGroup?.let { CategoryScreen(vm, it) }
        vm.detail?.let { DetailScreen(vm, it) }
        if (vm.showSettings) SettingsScreen(vm, onEdit = { form = it })
        if (form != null) SourceForm(vm, form!!) { form = null }
        if (castSheet) CastSheet(vm) { castSheet = false }
        if (remoteSheet) RemoteSheet { remoteSheet = false }
        vm.actionItem?.let { ItemActions(vm, it) }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 76.dp))
    }
}

@Composable
fun ItemActions(vm: AppVM, item: Item) {
    val close = { vm.actionItem = null }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ActionLine("Open details") { close(); vm.open(item) }
                if (item.kind != "series") ActionLine("Play now") { close(); vm.open(item, auto = true) }
                if (item.kind == "vod") ActionLine("Download for offline") { close(); vm.download(item) }
                ActionLine(if (vm.isFav(item)) "Remove from favorites" else "Add to favorites") {
                    vm.toggleFav(item)
                    close()
                }
                if (vm.progress.any { p -> p.item.key == item.key }) ActionLine("Remove from continue watching") {
                    vm.removeProgress(item)
                    close()
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("Close") } }
    )
}

@Composable
fun SettingsScreen(vm: AppVM, onEdit: (Source) -> Unit) {
    var pinDialog by remember { mutableStateOf(false) }
    BackHandler { vm.showSettings = false }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).blockTouches().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.showSettings = false }) { Icon(Icons.Default.ArrowBack, "Back") }
            Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { SettingHeader("Sources") }
            items(vm.sources.toList()) { s ->
                val on = s.id == vm.activeSrc
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
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
            item { SettingHeader("Lists (saved on this phone)") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Refresh automatically", color = Color(0xFFC9CCD6))
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(0 to "Manual", 3 to "3 h", 6 to "6 h", 12 to "12 h", 24 to "24 h")) { (h, label) ->
                            FilterChip(vm.refreshHours == h, { vm.changeRefresh(h) }, { Text(label) })
                        }
                    }
                }
            }
            item { ActionRow("Update lists now", "Download channels, movies and series again") { vm.refreshAll() } }
            item { ActionRow("Clear saved lists", "Frees space. Lists are downloaded again when needed") { vm.clearCache() } }
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
            if (vm.downloads.isEmpty()) item { Text("No downloads yet. Open a movie or episode and tap Download.", color = Color.Gray, fontSize = 13.sp) }
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
            item { Text("Lumora 1.5.0", color = Color.Gray, modifier = Modifier.padding(4.dp)) }
        }
    }
    if (pinDialog) PinDialog(vm) { pinDialog = false }
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
fun ActionLine(t: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, Modifier.fillMaxWidth()) { Text(t, Modifier.fillMaxWidth()) }
}

/** Long-press menu on any poster. */

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
