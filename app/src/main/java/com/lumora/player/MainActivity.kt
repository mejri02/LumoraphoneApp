@file:OptIn(
    androidx.media3.common.util.UnstableApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.lumora.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.res.painterResource
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
import androidx.compose.ui.graphics.vector.ImageVector
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

    /** Pause / close buttons inside the picture-in-picture window. */
    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            when (i?.getStringExtra("cmd")) {
                "toggle" -> vm.togglePlay()
                "close" -> vm.closeDetail()
            }
        }
    }

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
        val prevHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                java.io.File(filesDir, "crash.log").writeText(
                    java.util.Date().toString() + " [" + t.name + "]\n" + android.util.Log.getStackTraceString(e)
                )
            } catch (_: Throwable) {
            }
            prevHandler?.uncaughtException(t, e)
        }
        Store.init(applicationContext)
        CatalogCache.init(applicationContext)
        TvLink.init()
        ContextCompat.registerReceiver(this, pipReceiver, IntentFilter(PIP_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
        setContent {
            val accent = Color(vm.accentInt())
            val spec = themeAt(vm.themeIdx, accent)
            // Status/navigation bar icons must follow the theme brightness, not the system setting.
            val view = LocalView.current
            SideEffect {
                val c = WindowCompat.getInsetsController(window, view)
                c.isAppearanceLightStatusBars = spec.isLight
                c.isAppearanceLightNavigationBars = spec.isLight
            }
            CompositionLocalProvider(LocalThemeSpec provides spec) {
                MaterialTheme(colorScheme = spec.scheme) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Root(vm) }
                }
            }
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(pipReceiver)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        TvLink.resume()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android 12+ enters picture-in-picture on its own (see pipParams); older versions are handled here.
        if (vm.playing != null && Build.VERSION.SDK_INT in 26..30) enterPip()
    }

    fun enterPip() {
        val p = pipParams() ?: return
        try {
            enterPictureInPictureMode(p)
        } catch (_: Exception) {
        }
    }

    fun refreshPip() {
        val p = pipParams() ?: return
        try {
            setPictureInPictureParams(p)
        } catch (_: Exception) {
        }
    }

    private fun pipParams(): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < 26) return null
        val playing = vm.isPlaying
        val pauseIcon = if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val actions = listOf(
            android.app.RemoteAction(
                android.graphics.drawable.Icon.createWithResource(this, pauseIcon),
                if (playing) "Pause" else "Play", "Play or pause",
                PendingIntent.getBroadcast(
                    this, 1, Intent(PIP_ACTION).setPackage(packageName).putExtra("cmd", "toggle"),
                    PendingIntent.FLAG_IMMUTABLE
                )
            ),
            android.app.RemoteAction(
                android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                "Close", "Close",
                PendingIntent.getBroadcast(
                    this, 2, Intent(PIP_ACTION).setPackage(packageName).putExtra("cmd", "close"),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
        )
        val b = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).setActions(actions)
        if (Build.VERSION.SDK_INT >= 31) b.setAutoEnterEnabled(vm.playing != null)
        return b.build()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        vm.pip = isInPictureInPictureMode
    }

    companion object {
        const val PIP_ACTION = "com.lumora.player.PIP_CONTROL"
    }
}

@Composable
fun TopBar(vm: AppVM, onRemote: () -> Unit, onCast: () -> Unit, onDownloads: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().background(if (LocalThemeSpec.current?.painted == true) Color.Transparent else MaterialTheme.colorScheme.background).statusBarsPadding()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrandMark(size = 34.dp)
        Spacer(Modifier.weight(1f))
        val tv = TvLink.activeTv()
        if (tv != null) IconButton(onClick = onRemote, modifier = Modifier.size(42.dp)) { Icon(Icons.Default.SettingsRemote, "Remote") }
        IconButton(onClick = onDownloads, modifier = Modifier.size(42.dp)) { Icon(Icons.Default.Download, "Downloads") }
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

/** Horizontal tab strip used by the layouts that do not show a bottom navigation bar. */
@Composable
fun TopTabs(selected: Int, items: List<Triple<String, ImageVector, Int>>, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { (label, icon, i) ->
            val on = selected == i
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(LocalThemeSpec.current?.corner?.times(0.7f) ?: 12.dp))
                    .background(
                        if (on) Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, accent2()))
                        else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                    )
                    .clickable { onSelect(i) }.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    label, fontSize = 12.sp,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

/** Full-screen TV guide overlay (the guide itself lives in Guide.kt). */
@Composable
fun GuideScreen(vm: AppVM) {
    BackHandler { vm.closeGuide() }
    Column(Modifier.fillMaxSize().themeBackground().blockTouches().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.closeGuide() }) { Icon(Icons.Default.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text("TV Guide", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("beIN Sports · live schedule", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        GuideTab()
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

    val layout = LocalThemeSpec.current?.layout ?: UiLayout.CLASSIC
    val navItems = listOf(
        Triple("Home", Icons.Default.Home, 0), Triple("Catalog", Icons.Default.VideoLibrary, 1),
        Triple("Favorites", Icons.Default.Star, 2), Triple("Search", Icons.Default.Search, 3),
        Triple("Guide", Icons.Default.Event, 4)
    )
    val act = LocalContext.current as? MainActivity
    LaunchedEffect(vm.playing, vm.isPlaying) { act?.refreshPip() }
    val pipReq = vm.playing
    if (vm.pip && pipReq != null) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            PlayerPane(vm, pipReq, full = true, pip = true, onToggleFull = {}, onBack = {}, modifier = Modifier.fillMaxSize())
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        val themed = LocalThemeSpec.current?.painted == true
        Scaffold(
            modifier = Modifier.themeBackground(),
            containerColor = if (themed) Color.Transparent else MaterialTheme.colorScheme.background,
            topBar = {
                Column {
                    TopBar(vm, onRemote = { remoteSheet = true }, onCast = { castSheet = true }, onDownloads = { vm.showDownloads = true })
                    if (layout == UiLayout.CINEMA || layout == UiLayout.NEON) TopTabs(tab, navItems) { tab = it }
                }
            },
            bottomBar = {
                if (layout == UiLayout.CLASSIC || layout == UiLayout.GLASS) {
                    NavigationBar(containerColor = if (themed) MaterialTheme.colorScheme.surface.copy(alpha = 0.92f) else MaterialTheme.colorScheme.surface) {
                        navItems.forEach { (label, icon, i) ->
                            NavigationBarItem(
                                tab == i, { tab = i }, { Icon(icon, null) }, label = { Text(label, fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                            )
                        }
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    0 -> HomeTab(vm, onSeeAll = { tab = 1 }, onAddSource = { vm.showSettings = true })
                    1 -> CatalogTab(vm)
                    2 -> FavoritesTab(vm)
                    3 -> SearchTab(vm)
                    4 -> GuideTab()
                }
            }
        }

        if (vm.guideOpen) GuideScreen(vm)
        vm.openGroup?.let { CategoryScreen(vm, it) }
        if (!vm.minimized) vm.detail?.let { DetailScreen(vm, it) }
        if (vm.showSettings) SettingsScreen(vm, onEdit = { form = it })
        if (form != null) SourceForm(vm, form!!) { form = null }
        if (castSheet) CastSheet(vm) { castSheet = false }
        if (remoteSheet) RemoteSheet { remoteSheet = false }
        vm.actionItem?.let { ItemActions(vm, it) }
        if (vm.showDownloads) DownloadsScreen(vm)
        val bottomPad = if (layout == UiLayout.CINEMA || layout == UiLayout.NEON) 14.dp else 80.dp
        if (vm.minimized && vm.playing != null) MiniPlayer(vm, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 8.dp, end = 8.dp, bottom = bottomPad))
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = bottomPad))
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
    var sourcesOpen by rememberSaveable { mutableStateOf(false) }
    var themesOpen by rememberSaveable { mutableStateOf(false) }
    var listsOpen by rememberSaveable { mutableStateOf(false) }
    var browseOpen by rememberSaveable { mutableStateOf(false) }
    var playOpen by rememberSaveable { mutableStateOf(false) }
    var pending by remember { mutableStateOf<ConfirmReq?>(null) }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.backupTo(uri)
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.restoreFrom(uri)
    }
    BackHandler { vm.showSettings = false }
    Column(Modifier.fillMaxSize().themeBackground().blockTouches().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.showSettings = false }) { Icon(Icons.Default.ArrowBack, "Back") }
            Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                val active = vm.sources.firstOrNull { it.id == vm.activeSrc }
                CollapseHeader("Sources", active?.let { it.name.ifBlank { it.url } } ?: "No source", sourcesOpen) { sourcesOpen = !sourcesOpen }
            }
            if (sourcesOpen) items(vm.sources.toList()) { s ->
                val on = s.id == vm.activeSrc
                LaunchedEffect(s.id, s.url, s.user, s.pass, s.type) {
                    if (on) vm.refreshAccountInfo(s, force = true)
                }
                // Must be the exact key the view-model stores account info under (it also hashes mac and
                // proxy, so rebuilding it here by hand never matched and the expiry was never shown).
                val expiryKey = remember(s) { vm.cacheKey(s) }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant).background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f) else Color.Transparent)
                        .clickable { vm.selectSource(s.id) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name.ifBlank { s.url }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(s.type.uppercase() + (if (on) " · active" else ""), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (on && s.type == "xtream") {
                            Text(
                                vm.accountExpiry[expiryKey] ?: "Subscription info not loaded",
                                fontSize = 12.sp,
                                color = if ((vm.accountExpiry[expiryKey] ?: "").startsWith("Expired")) Color(0xFFFF6B6B) else Color.Gray
                            )
                            vm.accountStatus[expiryKey]?.let { status ->
                                Text("Status: $status", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (TvLink.paired()) IconButton(onClick = { vm.sendSourceToTv(s) }) { Icon(Icons.Default.Cast, "Send to TV") }
                    IconButton(onClick = { onEdit(s) }) { Icon(Icons.Default.Edit, "Edit") }
                }
            }
            if (sourcesOpen) item {
                Button(
                    onClick = { onEdit(Source("s" + System.currentTimeMillis().toString(36), "", "xtream", "")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("+ Add source") }
            }
            item {
                CollapseHeader("Lists", "Saved on this phone · " + (if (vm.refreshHours == 0) "manual refresh" else "refresh every ${vm.refreshHours} h"), listsOpen) { listsOpen = !listsOpen }
            }
            if (listsOpen) item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Refresh automatically", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(0 to "Manual", 3 to "3 h", 6 to "6 h", 12 to "12 h", 24 to "24 h")) { (h, label) ->
                            FilterChip(vm.refreshHours == h, { vm.changeRefresh(h) }, { Text(label) })
                        }
                    }
                }
            }
            if (listsOpen) item { ActionRow("Update lists now", "Download channels, movies and series again") { vm.refreshAll() } }
            if (listsOpen) item {
                ActionRow("Clear saved lists", "Frees space. Lists are downloaded again when needed") {
                    pending = ConfirmReq("Clear saved lists?", "Channels, movies and series are downloaded again when you open them.", "Clear") { vm.clearCache() }
                }
            }
            item {
                CollapseHeader("Browsing", if (vm.sort == "az") "Categories A-Z" else "Categories by most items", browseOpen) { browseOpen = !browseOpen }
            }
            if (browseOpen) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sort categories", Modifier.weight(1f))
                    FilterChip(vm.sort == "count", { vm.changeSort("count") }, { Text("Most items") })
                    Spacer(Modifier.width(6.dp))
                    FilterChip(vm.sort == "az", { vm.changeSort("az") }, { Text("A-Z") })
                }
            }
            if (browseOpen) item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Poster size", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(0 to "Compact", 1 to "Normal", 2 to "Large")) { (size, label) ->
                            FilterChip(vm.gridSize == size, { vm.changeGridSize(size) }, { Text(label) })
                        }
                    }
                }
            }
            item {
                CollapseHeader("Playback", (if (vm.resumeOn) "Resume on" else "Resume off") + " · " + (if (vm.castOn) "TV cast on" else "TV cast off"), playOpen) { playOpen = !playOpen }
            }
            if (playOpen) item { SwitchRow("Resume where I stopped", vm.resumeOn) { v -> vm.setResume(v) } }
            if (playOpen) item { SwitchRow("Play next episode automatically", vm.autoNext) { v -> vm.changeAutoNext(v) } }
            if (playOpen) item { SwitchRow("Keep the screen on while playing", vm.keepOn) { v -> vm.changeKeepOn(v) } }
            if (playOpen) item { SwitchRow("Play on TV when it is connected", vm.castOn) { v -> vm.changeCastOn(v) } }
            if (playOpen) item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Video size in the player", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("fit" to "Fit", "zoom" to "Zoom", "fill" to "Stretch")) { (mode, label) ->
                            FilterChip(vm.aspectMode == mode, {
                                while (vm.aspectMode != mode) vm.cycleAspect()
                            }, { Text(label) })
                        }
                    }
                }
            }
            if (playOpen) item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Default playback speed", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(0.75f, 1f, 1.25f, 1.5f, 2f)) { v ->
                            FilterChip(vm.speed == v, { vm.changeSpeed(v) }, {
                                Text(if (v == v.toInt().toFloat()) "${v.toInt()}x" else "${v}x")
                            })
                        }
                    }
                }
            }
            item {
                val cur = themeAt(vm.themeIdx, Color(vm.accentInt())).name
                CollapseHeader("Themes", cur, themesOpen) { themesOpen = !themesOpen }
            }
            if (themesOpen) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        item { ThemeThumb("Cinema Dark", null, Color(0xFF09090E), vm.themeIdx == 0) { vm.changeTheme(0) } }
                        item { ThemeThumb("Midnight Blue", null, Color(0xFF06111F), vm.themeIdx == 1) { vm.changeTheme(1) } }
                        item { ThemeThumb("Light Premium", null, Color(0xFFF7F8FC), vm.themeIdx == 2) { vm.changeTheme(2) } }
                        itemsIndexed(IMAGE_THEMES) { i, t ->
                            ThemeThumb(t.name, t.img, t.scheme.background, vm.themeIdx == BASE_THEMES + i) { vm.changeTheme(BASE_THEMES + i) }
                        }
                        itemsIndexed(NEW_THEMES) { i, t ->
                            ThemeThumb(
                                t.name, t.img, t.scheme.background,
                                vm.themeIdx == BASE_THEMES + IMAGE_THEMES.size + i,
                                gradient = t.gradient
                            ) { vm.changeTheme(BASE_THEMES + IMAGE_THEMES.size + i) }
                        }
                    }
                    Text("Themes also change the home-screen layout (Classic, Cinema, Glass or Neon). Your original theme remains available.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (themesOpen) item {
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
            item { SettingHeader("Subtitles") }
            item { Text("Load subtitle files (.srt or .vtt) directly from the player. No API key or online account is needed.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(4.dp)) }
            item { SettingHeader("Downloads (offline)") }
            item { SwitchRow("Download only on Wi-Fi", vm.wifiOnly) { v -> vm.changeWifiOnly(v) } }
            item { Text("Saves mobile data. A download waits (or pauses if the network drops) until Wi-Fi is back, then continues when you tap it.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
            if (vm.downloads.isEmpty()) item { Text("No downloads yet. Open a movie or episode and tap Download.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
            items(vm.downloads.toList()) { d -> DownloadRow(vm, d) }
            item { SettingHeader("Parental control") }
            item {
                ActionRow(
                    if (vm.pin.isEmpty()) "Set adult PIN" else "Change or remove adult PIN",
                    "Adult categories ask for a 4-digit PIN"
                ) { pinDialog = true }
            }
            item { SettingHeader("Data") }
            item {
                ActionRow("Clear continue watching", "Removes saved progress") {
                    pending = ConfirmReq("Clear continue watching?", "Saved positions for this account will be removed.", "Clear") { vm.clearProgress() }
                }
            }
            item {
                ActionRow("Clear favorites", "Removes all favorites") {
                    pending = ConfirmReq("Clear favorites?", "All favorites of this account will be removed.", "Clear") { vm.clearFavs() }
                }
            }
            item { SettingHeader("Backup") }
            item { ActionRow("Export backup", "Save sources, favorites, progress and settings as a JSON file") { exportPicker.launch("lumora-backup.json") } }
            item {
                ActionRow("Restore backup", "Replace this phone's Lumora data with a backup file") {
                    pending = ConfirmReq("Restore backup?", "Your current sources, favorites, progress and settings will be replaced by the backup file.", "Choose file") {
                        importPicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    }
                }
            }
            item { SettingHeader("About") }
            item {
                val ctx = LocalContext.current
                val version = remember {
                    try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName.orEmpty() } catch (_: Exception) { "" }
                }
                Column(Modifier.fillMaxWidth().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    BrandMark(size = 46.dp, subtitle = "IPTV PLAYER")
                    Spacer(Modifier.height(8.dp))
                    Text("Lumore $version", fontWeight = FontWeight.SemiBold)
                    Text("Designed & developed by Mejri02", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text(
                        "Your playlists and credentials stay on this device. No ads, no analytics.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp, start = 12.dp, end = 12.dp)
                    )
                }
            }
            item { ActionRow("Clear search history", "Forgets your recent searches") { vm.clearRecentSearches() } }
            item {
                val ctx = LocalContext.current
                val crash = remember { java.io.File(ctx.filesDir, "crash.log") }
                if (crash.exists() && crash.length() > 0L) ActionRow("Share last crash report", "Send the error details to the developer") {
                    try {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, "Lumora crash report")
                            .putExtra(Intent.EXTRA_TEXT, crash.readText().take(20_000))
                        ctx.startActivity(Intent.createChooser(send, "Share crash report"))
                    } catch (_: Exception) {
                        vm.toast("Could not open the share sheet")
                    }
                }
            }
        }
    }
    if (pinDialog) PinDialog(vm) { pinDialog = false }
    pending?.let { req -> ConfirmDialog(req.title, req.text, req.label, req.action) { pending = null } }
}

/** A destructive action waiting for the user's confirmation. */
class ConfirmReq(val title: String, val text: String, val label: String, val action: () -> Unit)

@Composable
fun ConfirmDialog(title: String, text: String, label: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(label, color = Color(0xFFFF6B6B)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Small preview card for a theme: its picture (or plain color) with the name under it. */
@Composable
fun ThemeThumb(name: String, img: Int?, color: Color, selected: Boolean, gradient: List<Color>? = null, onClick: () -> Unit) {
    val border = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Column(Modifier.width(84.dp).clickable { onClick() }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.62f).clip(RoundedCornerShape(12.dp)).background(color)
                .border(3.dp, border, RoundedCornerShape(12.dp))
        ) {
            if (img != null) {
                androidx.compose.foundation.Image(
                    painterResource(img), name, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }
        }
        Text(
            name, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** Tappable section header that expands or collapses its content. */
@Composable
fun CollapseHeader(title: String, summary: String, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).clickable { onToggle() }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            if (summary.isNotBlank()) Text(summary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
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
        Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text("Enter your PIN to open this category", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    var mac by remember { mutableStateOf(start.mac) }
    var proxy by remember { mutableStateOf(start.proxy.ifBlank { Stalker.DEFAULT_PROXY }) }
    var menu by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val exists = vm.sources.any { it.id == start.id }

    fun build() = Source(start.id, name.trim().ifBlank { url.trim() }, type, url.trim(), user.trim(), pass.trim(), format, mac.trim(), proxy.trim())

    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (exists) "Edit source" else "Add source") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box {
                    OutlinedButton(onClick = { menu = true }, Modifier.fillMaxWidth()) {
                        Text(when (type) { "xtream" -> "Xtream Codes"; "stalker" -> "Stalker Portal"; else -> "M3U playlist URL" })
                    }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Xtream Codes") }, onClick = { type = "xtream"; menu = false })
                        DropdownMenuItem(text = { Text("M3U playlist URL") }, onClick = { type = "m3u"; menu = false })
                        DropdownMenuItem(text = { Text("Stalker Portal") }, onClick = { type = "stalker"; menu = false })
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text(if (type == "m3u") "Playlist URL" else if (type == "stalker") "Portal URL" else "Server URL") }, singleLine = true)
                if (type == "stalker") {
                    OutlinedTextField(mac, { mac = it }, label = { Text("MAC address") }, singleLine = true)
                    OutlinedTextField(proxy, { proxy = it }, label = { Text("Cloudflare Cookie proxy URL") }, singleLine = true)
                }
                if (type == "xtream") {
                    OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true)
                    OutlinedTextField(
                        pass, { pass = it }, label = { Text("Password") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(format == "auto", { format = "auto" }, { Text("Auto") })
                        Spacer(Modifier.width(6.dp))
                        FilterChip(format == "m3u8", { format = "m3u8" }, { Text("HLS") })
                        Spacer(Modifier.width(6.dp))
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
                if (exists) TextButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete") }
                TextButton(onClick = close) { Text("Cancel") }
            }
        }
    )
    if (confirmDelete) ConfirmDialog(
        "Delete this source?",
        "Its saved lists, favorites and progress on this phone are removed too.",
        "Delete",
        { vm.deleteSource(start.id); close() },
        { confirmDelete = false }
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
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                tvs.forEach { t ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant).background(if (t.active) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f) else Color.Transparent)
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
                Text("or enter the code manually", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text("Link a TV first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        if (title.isNotEmpty()) Text("▶ $title", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
