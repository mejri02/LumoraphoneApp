@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.lumora.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.windowInsetsPadding

@Composable
fun accent2(): Color = androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.primary, Color(0xFF8B5CF6), 0.55f)

@Composable
fun sectionGradient(): Brush = Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, accent2()))

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(4.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(sectionGradient()))
        Spacer(Modifier.width(10.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (action != null && onAction != null) TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
            Text(action, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    sub: String = "",
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: ImageVector = Icons.Default.LiveTv,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(76.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), accent2().copy(alpha = 0.18f)))),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp)) }
        Spacer(Modifier.height(16.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        if (sub.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 19.sp)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            Button(onClick = onAction, shape = RoundedCornerShape(24.dp)) { Text(actionLabel) }
        }
    }
}

/** Rounded poster with an optional progress line, favourite star and download badge. */
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
    val radius = LocalThemeSpec.current?.corner ?: 16.dp
    Column(
        modifier
            .clip(RoundedCornerShape(radius))
            .combinedClickable(onClick = onClick, onLongClick = onLong)
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(radius))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (item.logo.startsWith("http")) {
                AsyncImage(
                    model = item.logo, contentDescription = item.name, contentScale = ContentScale.Crop,
                    placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(item.name.take(2).uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (item.rating > 0.0) {
                Text(
                    "★ %.1f".format(item.rating),
                    color = Color(0xFFFBBF24), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart).padding(7.dp)
                        .clip(RoundedCornerShape(8.dp)).background(Color(0xAA000000)).padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
            if (fav) Icon(
                Icons.Default.Star, "Favourite", tint = Color(0xFFFBBF24),
                modifier = Modifier.align(Alignment.TopEnd).padding(7.dp).size(16.dp)
                    .clip(CircleShape).background(Color(0x99000000)).padding(2.dp)
            )
            if (dl != null) DownloadBadge(dl, Modifier.align(Alignment.BottomEnd).padding(6.dp))
            if (progress >= 0f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x66000000))) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                }
            }
        }
        Text(
            item.name, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2,
            overflow = TextOverflow.Ellipsis, lineHeight = 15.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 2.dp, end = 2.dp)
        )
    }
}

/** Landscape "hero" tile used by the widescreen and dashboard home layouts. */
@Composable
fun WideCard(
    item: Item,
    modifier: Modifier = Modifier,
    progress: Float = -1f,
    kicker: String? = null,
    onClick: () -> Unit
) {
    val radius = LocalThemeSpec.current?.corner ?: 16.dp
    Box(
        modifier.width(264.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(radius))
            .background(MaterialTheme.colorScheme.surfaceVariant).clickable { onClick() }
    ) {
        if (item.logo.startsWith("http")) AsyncImage(
            model = item.logo, contentDescription = item.name, contentScale = ContentScale.Crop,
            placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxSize()
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)))))
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            if (kicker != null) Text(kicker.uppercase(), color = Color(0xFF9BE7FF), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Text(item.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (progress >= 0f) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x66FFFFFF))) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                }
            }
        }
    }
}

/** A compact horizontal row: small artwork, title, subtitle and optional progress. */
@Composable
fun MediaRow(
    item: Item,
    subtitle: String = "",
    progress: Float = -1f,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(LocalThemeSpec.current?.corner ?: 16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) 0.72f else 0.55f))
            .clickable { onClick() }.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(58.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (item.logo.startsWith("http")) AsyncImage(model = item.logo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Text(item.name.take(1).uppercase(), modifier = Modifier.align(Alignment.Center), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            if (progress >= 0f) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(progress = progress.coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)))
            }
        }
        if (trailing != null) { Spacer(Modifier.width(6.dp)); trailing() }
        else Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun DownloadBadge(dl: DlInfo, modifier: Modifier = Modifier) {
    val (icon, tint) = when {
        dl.failed -> Icons.Default.ErrorOutline to Color(0xFFE5484D)
        dl.ready -> Icons.Default.OfflinePin to Color(0xFF10B981)
        dl.paused -> Icons.Default.PauseCircle to Color(0xFFB8BBC6)
        else -> Icons.Default.Downloading to Color.White
    }
    Box(
        modifier.size(22.dp).clip(CircleShape).background(Color(0xB3000000)),
        contentAlignment = Alignment.Center
    ) { Icon(icon, dl.status, tint = tint, modifier = Modifier.size(14.dp)) }
}

@Composable
fun Pill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Text(
        text, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    )
}

@Composable
fun SortPills(vm: AppVM) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items = ITEM_SORTS) { (key, label) ->
            Pill(label, vm.itemSort == key) { vm.changeItemSort(key) }
        }
    }
}

@Composable
fun PosterGrid(vm: AppVM, list: List<Item>, modifier: Modifier = Modifier) {
    val minTile = when (vm.gridSize) {
        0 -> 86.dp
        2 -> 132.dp
        else -> 104.dp
    }
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val sorted = remember(list, vm.itemSort) { vm.sortItems(list).distinctBy { it.key }.take(3000) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minTile),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 20.dp + nav),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(sorted, key = { it.key }) { item ->
            val prog = vm.progress.firstOrNull { it.item.key == item.key }
            PosterCard(
                item = item,
                progress = if (prog != null && prog.dur > 0) (prog.pos.toFloat() / prog.dur.toFloat()).coerceIn(0f, 1f) else -1f,
                fav = vm.isFav(item),
                dl = vm.dlInfo[item.key],
                onClick = { vm.open(item, auto = true) },
                onLong = { vm.actionItem = item }
            )
        }
    }
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, fontSize = 14.sp) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingIcon = {
            if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") }) { Icon(Icons.Default.Close, "Clear") }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun StatTile(label: String, value: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Column(
        modifier.clip(RoundedCornerShape(LocalThemeSpec.current?.corner ?: 16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(14.dp)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(10.dp))
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** A widescreen hero panel for the Cinema home layout. */
@Composable
fun HeroBanner(
    item: Item,
    kicker: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onPlay: () -> Unit,
    onDetails: () -> Unit
) {
    val radius = LocalThemeSpec.current?.corner ?: 16.dp
    Box(modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(radius)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        val art = item.logo
        if (art.startsWith("http")) {
            AsyncImage(
                model = art, contentDescription = item.name, contentScale = ContentScale.Crop,
                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0x22000000), Color(0x66000000), Color(0xF2000000)))
            )
        )
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(18.dp)) {
            Text(kicker.uppercase(), color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(6.dp))
            Text(item.name, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, lineHeight = 31.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = Color(0xFFD5D9E4), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.height(42.dp).clip(RoundedCornerShape(23.dp)).background(sectionGradient())
                        .clickable { onPlay() }.padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Play", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = onDetails, shape = RoundedCornerShape(23.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66FFFFFF))) {
                    Text("Details", fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun GlassSurface(modifier: Modifier = Modifier, shape: RoundedCornerShape = RoundedCornerShape(22.dp), content: @Composable ColumnScope.() -> Unit) {
    val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
    Surface(
        modifier = modifier,
        shape = shape,
        color = if (light) Color(0xCCFFFFFF) else Color(0x99141422),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (light) Color(0x66FFFFFF) else Color(0x22FFFFFF))
    ) { Column(Modifier.padding(14.dp), content = content) }
}

/** The Lumore word mark: gradient tile with an L, used on the home bar and in About. */
@Composable
fun BrandMark(size: Dp = 34.dp, showName: Boolean = true, subtitle: String? = null, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(size).clip(RoundedCornerShape((size.value * 0.3f).dp)).background(sectionGradient()),
            contentAlignment = Alignment.Center
        ) {
            Text("L", color = Color.White, fontWeight = FontWeight.Black, fontSize = (size.value * 0.52f).sp)
        }
        if (showName) {
            Spacer(Modifier.width(9.dp))
            Column {
                Text(
                    "LUMORE", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp,
                    fontWeight = FontWeight.Black, letterSpacing = 2.6.sp
                )
                Text(
                    subtitle ?: "IPTV PLAYER", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 8.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/** A small rounded label used for categories, live badges and metadata. */
@Composable
fun Tag(text: String, color: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier) {
    Text(
        text, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    )
}

@Composable
fun Modifier.blockTouches(): Modifier {
    val src = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = src, indication = null) {}
}
