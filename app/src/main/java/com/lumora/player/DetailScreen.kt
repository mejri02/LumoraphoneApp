@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.lumora.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage

@Composable
fun DetailScreen(vm: AppVM, item: Item) {
    val act = LocalContext.current as Activity
    val land = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    val req = vm.playing
    val full = req != null && (vm.full || land)
    val handler = remember { Handler(Looper.getMainLooper()) }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val playerH = screenW * 9f / 16f

    fun toggleFull() {
        if (full) {
            vm.full = false
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            handler.postDelayed({ act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }, 1500)
        } else {
            vm.full = true
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    DisposableEffect(full) {
        val w = act.window
        val c = WindowCompat.getInsetsController(w, w.decorView)
        if (full) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { }
    }

    DisposableEffect(Unit) {
        onDispose {
            handler.removeCallbacksAndMessages(null)
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            val w = act.window
            WindowCompat.getInsetsController(w, w.decorView).show(WindowInsetsCompat.Type.systemBars())
        }
    }

    BackHandler(enabled = !vm.pip) {
        if (full) toggleFull() else if (req != null) vm.minimize() else vm.closeDetail()
    }

    Box(Modifier.fillMaxSize().themeBackground().blockTouches()) {
        if (vm.pip && req != null) {
            PlayerPane(vm, req, full = true, pip = true, onToggleFull = {}, onBack = {}, modifier = Modifier.fillMaxSize())
        } else {
            if (!full) DetailContent(vm, item, if (req != null) statusTop + playerH else 0.dp, req != null)
            if (req != null) {
                if (!full) Box(Modifier.fillMaxWidth().height(statusTop).background(Color.Black).align(Alignment.TopStart))
                PlayerPane(
                    vm, req, full = full, pip = false,
                    onToggleFull = { toggleFull() },
                    onBack = { if (full) toggleFull() else vm.minimize() },
                    modifier = if (full) Modifier.fillMaxSize().align(Alignment.TopStart)
                    else Modifier.fillMaxWidth().padding(top = statusTop).height(playerH).align(Alignment.TopStart)
                )
            }
        }
    }
}

@Composable
private fun ActionCell(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clickable { onClick() }.padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = Color(0xFFB8BBC6))
    }
}

@Composable
private fun DetailContent(vm: AppVM, item: Item, topPad: Dp, playing: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    val meta = vm.meta
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val isSeries = item.kind == "series"
    val isLive = item.kind == "live"
    val seasons = remember(vm.episodes) { vm.episodes.map { it.season }.distinct() }
    var sel by remember(item.id, vm.episodes.size) { mutableIntStateOf(seasons.firstOrNull() ?: 1) }
    val curEp = vm.playing?.item
    LaunchedEffect(curEp?.id) {
        if (curEp != null && curEp.kind == "ep" && curEp.season > 0) sel = curEp.season
    }
    var moreText by remember(item.id) { mutableStateOf(false) }
    val poster = meta?.poster?.ifBlank { null } ?: item.logo
    val shown = remember(vm.episodes, sel) { vm.episodes.filter { it.season == sel } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = topPad, bottom = 28.dp + navBottom)) {
        // ---------- header ----------
        item {
            if (!playing) {
                Box(Modifier.fillMaxWidth()) {
                    val back = meta?.backdrop?.ifBlank { null } ?: poster
                    if (back.startsWith("http")) {
                        AsyncImage(
                            model = back, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(430.dp), alpha = 0.35f
                        )
                    }
                    Box(
                        Modifier.fillMaxWidth().height(430.dp)
                            .background(Brush.verticalGradient(listOf(Color(0x66000000), MaterialTheme.colorScheme.background)))
                    )
                    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.width(if (isLive) 170.dp else 200.dp).aspectRatio(if (isLive) 1f else 2f / 3f)
                                .clip(RoundedCornerShape(22.dp)).background(Color(0xFF1C1C26)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (poster.startsWith("http")) {
                                AsyncImage(
                                    model = poster, contentDescription = item.name,
                                    contentScale = if (isLive) ContentScale.Fit else ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().then(if (isLive) Modifier.padding(20.dp) else Modifier)
                                )
                            } else {
                                Text(item.name.take(2).uppercase(), color = Color.Gray, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Box(
                        Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp).size(42.dp).clip(CircleShape)
                            .background(Color(0x99000000)).clickable { vm.closeDetail() },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(60.dp).aspectRatio(if (isLive) 1f else 2f / 3f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1C1C26))) {
                        if (poster.startsWith("http")) AsyncImage(
                            model = poster, contentDescription = null,
                            contentScale = if (isLive) ContentScale.Fit else ContentScale.Crop, modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(vm.playing?.title ?: item.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        MetaLine(item, meta)
                    }
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable { vm.closeDetail() },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Close, "Close", modifier = Modifier.size(20.dp)) }
                }
            }
        }

        // ---------- title block (only before playing) ----------
        if (!playing) item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (item.group.isNotBlank()) Text(
                    item.group, fontSize = 12.sp, color = Color(0xFFB8BBC6), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 6.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(item.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 30.sp)
                Spacer(Modifier.height(6.dp))
                MetaLine(item, meta, center = true)
            }
        }

        // ---------- play + actions ----------
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                if (!playing) {
                    val target: Item? = if (isSeries) vm.nextEpisodeFor(item) else item
                    val prog = target?.let { t -> vm.progressOf(t) }
                    val label = when {
                        isSeries && target != null ->
                            (if (prog != null || vm.progress.any { p -> p.item.parent == item.id }) "Resume" else "Play") + "  S${target.season}E${target.num}"
                        prog != null && vm.resumeOn -> "Resume  " + fmtTime(prog)
                        else -> "Play"
                    }
                    val enabled = !isSeries || target != null
                    Box(
                        Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (enabled) Brush.horizontalGradient(listOf(primary, accent2())) else Brush.horizontalGradient(listOf(Color(0xFF2A2A33), Color(0xFF2A2A33))))
                            .clickable(enabled = enabled) {
                                if (isSeries) vm.nextEpisodeFor(item)?.let { e -> vm.playEpisode(item, e) } else vm.play(item)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSeries && vm.episodesLoading) {
                            CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    val fav = vm.isFav(item)
                    ActionCell(
                        if (fav) Icons.Default.Star else Icons.Default.StarBorder, if (fav) "Saved" else "Favorite",
                        if (fav) Color(0xFFFBBF24) else Color.White, Modifier.weight(1f)
                    ) { vm.toggleFav(item) }
                    ActionCell(Icons.Default.Cast, "Cast to TV", if (TvLink.paired()) primary else Color.White, Modifier.weight(1f)) {
                        vm.castItem(item)
                    }
                    if (item.kind == "vod" || item.kind == "ep") {
                        val d = vm.dlInfo[item.key]
                        ActionCell(
                            when {
                                d?.ready == true -> Icons.Default.Check
                                d?.paused == true -> Icons.Default.PlayArrow
                                d?.failed == true -> Icons.Default.Refresh
                                d != null -> Icons.Default.Pause
                                else -> Icons.Default.Download
                            },
                            when {
                                d == null -> "Download"
                                d.ready -> "Offline"
                                d.paused -> "Resume"
                                d.failed -> "Retry"
                                else -> "${d.percent}%"
                            },
                            if (d?.ready == true) Color(0xFF10B981) else Color.White, Modifier.weight(1f)
                        ) { vm.download(item) }
                    }
                }
            }
        }

        // ---------- description ----------
        val plot = meta?.plot.orEmpty()
        if (plot.isNotBlank()) item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text(
                    plot, fontSize = 14.sp, lineHeight = 21.sp, color = Color(0xFFC9CCD6),
                    maxLines = if (moreText) 50 else 4, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { moreText = !moreText }
                )
                if (!meta?.cast.isNullOrBlank()) Text(
                    "Cast: " + meta!!.cast, fontSize = 12.sp, color = Color(0xFF8E92A0), maxLines = if (moreText) 6 else 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp)
                )
                if (!meta?.director.isNullOrBlank()) Text(
                    "Director: " + meta!!.director, fontSize = 12.sp, color = Color(0xFF8E92A0), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // ---------- series: seasons + episodes ----------
        if (isSeries) {
            if (vm.episodesLoading) item {
                Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (vm.episodes.isEmpty()) item {
                Text("No episodes found", Modifier.padding(24.dp), color = Color.Gray)
            } else {
                item {
                    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Episodes", fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${vm.episodes.size} total", fontSize = 12.sp, color = Color.Gray)
                    }
                }
                if (seasons.size > 1) item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(seasons) { sn ->
                            val on = sn == sel
                            Text(
                                "Season $sn", fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (on) Color.White else Color(0xFFC9CCD6),
                                modifier = Modifier.clip(RoundedCornerShape(20.dp))
                                    .background(if (on) primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { sel = sn }.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
                items(shown) { e -> EpisodeRow(vm, item, e, curEp?.id == e.id) }
            }
        }

        // ---------- live: more channels in the same category ----------
        if (isLive && vm.playList.size > 1) {
            item {
                Text(
                    "More in " + item.group, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp)
                )
            }
            items(vm.playList) { c ->
                val cur = c.id == (vm.playing?.item?.id ?: item.id)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (cur) primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface)
                        .clickable { vm.zapTo(c) }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1C1C26)), contentAlignment = Alignment.Center) {
                        if (c.logo.startsWith("http")) AsyncImage(model = c.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(5.dp))
                        else Text(c.name.take(2).uppercase(), color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(c.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (cur) FontWeight.SemiBold else FontWeight.Normal)
                    if (cur) Icon(Icons.Default.PlayArrow, null, tint = primary)
                }
            }
        }
    }
}

@Composable
private fun MetaLine(item: Item, meta: Meta?, center: Boolean = false) {
    val parts = ArrayList<String>()
    val r = meta?.rating?.takeIf { it > 0.0 } ?: item.rating.takeIf { it > 0.0 }
    if (r != null) parts.add("★ " + "%.1f".format(r))
    meta?.year?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
    meta?.duration?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
    meta?.genre?.takeIf { it.isNotBlank() }?.let { parts.add(it.split(",", "/").first().trim()) }
    if (item.kind == "live") parts.add("Live")
    if (parts.isEmpty()) return
    Text(
        parts.joinToString("  ·  "), fontSize = 13.sp, color = Color(0xFFB8BBC6), maxLines = 1, overflow = TextOverflow.Ellipsis,
        textAlign = if (center) TextAlign.Center else TextAlign.Start, modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun EpisodeRow(vm: AppVM, serie: Item, e: Item, current: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    val prog = vm.progress.firstOrNull { p -> p.item.key == e.key }
    val frac = if (prog != null && prog.dur > 0) (prog.pos.toFloat() / prog.dur.toFloat()).coerceIn(0f, 1f) else 0f
    val dl = vm.dlInfo[e.key]
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clip(RoundedCornerShape(16.dp))
            .background(if (current) primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface)
            .then(if (current) Modifier.border(1.dp, primary.copy(alpha = 0.6f), RoundedCornerShape(16.dp)) else Modifier)
            .clickable { vm.playEpisode(serie, e) }.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(120.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(11.dp)).background(Color(0xFF1C1C26))) {
            if (e.logo.startsWith("http")) AsyncImage(model = e.logo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color(0x40000000)), contentAlignment = Alignment.Center) {
                Icon(if (current) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            if (frac > 0f) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(frac).height(3.dp).background(primary))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "EPISODE ${e.num}" + (if (current) "  ·  NOW PLAYING" else ""), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = primary, letterSpacing = 0.6.sp
            )
            Text(e.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            val sub = e.info.ifBlank { e.plot }
            if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, color = Color(0xFF8E92A0), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        IconButton(onClick = { vm.download(e.copy(name = serie.name + " · S" + e.season + "E" + e.num)) }) {
            when {
                dl?.ready == true -> Icon(Icons.Default.Check, "Offline", tint = Color(0xFF10B981))
                dl?.paused == true -> Icon(Icons.Default.PlayArrow, "Resume", tint = Color(0xFFB8BBC6))
                dl != null && !dl.failed -> Text("${dl.percent}%", fontSize = 11.sp, color = Color.Gray)
                else -> Icon(Icons.Default.Download, "Download", tint = Color(0xFFB8BBC6))
            }
        }
    }
}
