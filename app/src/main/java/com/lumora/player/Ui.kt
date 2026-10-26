@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lumora.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun accent2(): Color = lerp(MaterialTheme.colorScheme.primary, Color(0xFF8B5CF6), 0.55f)

@Composable
fun SectionHeader(title: String, action: String?, onAction: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(4.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, accent2()))))
        Spacer(Modifier.width(10.dp))
        Text(title, Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.Bold)
        if (action != null) TextButton(onClick = onAction) { Text(action, color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
fun EmptyState(title: String, sub: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(sub, fontSize = 13.sp, color = Color.Gray, textAlign = TextAlign.Center)
    }
}

/** Poster card: artwork, rating badge, favourite mark, progress bar, download badge and title. */
@Composable
fun PosterCard(
    item: Item,
    modifier: Modifier = Modifier,
    ratio: Float = 2f / 3f,
    progress: Float = -1f,
    fav: Boolean = false,
    dl: DlInfo? = null,
    onClick: () -> Unit,
    onLong: () -> Unit = {}
) {
    val live = item.kind == "live"
    Column(modifier.combinedClickable(onClick = onClick, onLongClick = onLong)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(16.dp)).background(Color(0xFF1A1A23))
        ) {
            if (item.logo.startsWith("http")) {
                AsyncImage(
                    model = item.logo, contentDescription = item.name,
                    modifier = Modifier.fillMaxSize().then(if (live) Modifier.padding(14.dp) else Modifier),
                    contentScale = if (live) ContentScale.Fit else ContentScale.Crop
                )
            } else {
                Text(
                    item.name.take(2).uppercase(), Modifier.align(Alignment.Center),
                    color = Color.Gray, fontSize = 22.sp, fontWeight = FontWeight.Bold
                )
            }
            if (!live && item.rating > 0.0) {
                Text(
                    "★ " + "%.1f".format(item.rating),
                    Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(8.dp))
                        .background(Color(0xB3000000)).padding(horizontal = 6.dp, vertical = 2.dp),
                    color = Color(0xFFFBBF24), fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                )
            }
            if (fav) {
                Icon(
                    Icons.Default.Star, null, tint = Color(0xFFFBBF24),
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp)
                )
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
            item.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, lineHeight = 17.sp,
            modifier = Modifier.padding(top = 7.dp, start = 2.dp, end = 2.dp)
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
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(ITEM_SORTS) { (key, label) ->
            val on = vm.itemSort == key
            Text(
                label,
                modifier = Modifier.clip(RoundedCornerShape(20.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { vm.changeItemSort(key) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                color = if (on) Color.White else Color(0xFFC9CCD6),
                fontSize = 13.sp,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun PosterGrid(vm: AppVM, list: List<Item>, modifier: Modifier = Modifier.fillMaxSize()) {
    if (list.isEmpty()) {
        EmptyState("Nothing here", "No titles found")
        return
    }
    val sorted = remember(list, vm.itemSort) { vm.sortItems(list).distinctBy { it.key }.take(3000) }
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        modifier = modifier,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp + nav),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(sorted, key = { it.key }) { it ->
            PosterCard(
                item = it,
                modifier = Modifier.fillMaxWidth(),
                ratio = if (it.kind == "live") 1f else 2f / 3f,
                fav = vm.isFav(it),
                dl = vm.dlInfo[it.key],
                onClick = { vm.open(it) },
                onLong = { vm.actionItem = it }
            )
        }
    }
}

/** Stops taps from reaching the screen below an overlay. */
@Composable
fun Modifier.blockTouches(): Modifier =
    this.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
