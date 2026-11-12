package com.lumora.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/** Small bar shown at the bottom while a video is minimized. Tap it to open the video again. */
@Composable
fun MiniPlayer(vm: AppVM, modifier: Modifier = Modifier) {
    val req = vm.playing ?: return
    val primary = MaterialTheme.colorScheme.primary
    var pos by remember(req.url) { mutableLongStateOf(0L) }
    var dur by remember(req.url) { mutableLongStateOf(0L) }
    LaunchedEffect(req.url) {
        while (true) {
            vm.exo?.let { p ->
                pos = p.currentPosition
                dur = p.duration.coerceAtLeast(0L)
            }
            delay(500)
        }
    }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xF21C1C26)).clickable { vm.expand() }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(56.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF09090E))) {
                val logo = req.item?.logo.orEmpty()
                if (logo.startsWith("http")) AsyncImage(model = logo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(req.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(
                    if (req.kind == "live") "● LIVE" else fmtTime(pos) + " / " + fmtTime(dur),
                    fontSize = 11.sp, color = Color(0xFFB8BBC6)
                )
            }
            IconButton(onClick = { vm.togglePlay() }) {
                Icon(if (vm.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause", tint = Color.White)
            }
            IconButton(onClick = { vm.closeDetail() }) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
        }
        if (req.kind != "live" && dur > 0L) {
            LinearProgressIndicator(
                progress = { (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = primary,
                trackColor = Color(0x33FFFFFF)
            )
        }
    }
}

/** Full list of downloaded movies and episodes, opened from the top bar. */
@Composable
fun DownloadsScreen(vm: AppVM) {
    BackHandler { vm.showDownloads = false }
    Column(Modifier.fillMaxSize().themeBackground().blockTouches().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.showDownloads = false }) { Icon(Icons.Default.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text("Downloads", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (vm.downloads.isNotEmpty()) {
                    val used = vm.downloads.sumOf { d -> vm.dlInfo[d.item.key]?.done ?: 0L }
                    Text("${vm.downloads.size} item(s) · ${fmtBytes(used)} used", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
        if (vm.downloads.isEmpty()) {
            EmptyState("No downloads yet", "Open a movie or episode and tap Download.")
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(vm.downloads.toList()) { d -> DownloadRow(vm, d) }
            }
        }
    }
}

@Composable
fun DownloadRow(vm: AppVM, d: Download) {
    val info = vm.dlInfo[d.item.key]
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) ConfirmDialog(
        "Delete download?", "\"" + d.item.name + "\" will be removed from this phone.", "Delete",
        { vm.deleteDownload(d) }, { confirmDelete = false }
    )
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)
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
            when {
                info?.ready == true -> IconButton(onClick = { vm.playOffline(d) }) { Icon(Icons.Default.PlayArrow, "Play") }
                info?.failed == true -> IconButton(onClick = { vm.download(d.item) }) { Icon(Icons.Default.Refresh, "Retry") }
                info != null -> IconButton(onClick = { vm.download(d.item) }) {
                    Icon(if (info.paused) Icons.Default.PlayArrow else Icons.Default.Pause, if (info.paused) "Resume" else "Pause")
                }
            }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete") }
        }
        if (info != null && !info.ready && !info.failed) {
            Spacer(Modifier.height(8.dp))
            if (info.total > 0L) {
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
