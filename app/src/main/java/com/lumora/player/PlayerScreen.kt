@file:OptIn(androidx.media3.common.util.UnstableApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lumora.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

fun fmtTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val t = ms / 1000
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun CtrlBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, size: Int = 44, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(size.dp)) {
        Icon(icon, desc, tint = if (enabled) Color.White else Color(0x55FFFFFF), modifier = Modifier.size((size * 0.62f).dp))
    }
}

/**
 * Video surface with our own controls. The player itself lives in AppVM, so this pane can
 * leave the screen (minimize) or move into picture-in-picture without stopping the video.
 */
@Composable
fun PlayerPane(
    vm: AppVM,
    req: PlayReq,
    full: Boolean,
    pip: Boolean,
    onToggleFull: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val exo = vm.exo ?: return
    val ctx = LocalContext.current
    val live = req.kind == "live"
    val isPlaying = vm.isPlaying
    val buffering = vm.buffering
    val errorMsg = vm.playerError
    var show by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    var resize by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var speed by remember { mutableFloatStateOf(1f) }
    var pos by remember(req.url) { mutableLongStateOf(0L) }
    var dur by remember(req.url) { mutableLongStateOf(0L) }
    var drag by remember { mutableStateOf<Float?>(null) }
    var hint by remember { mutableStateOf("") }

    LaunchedEffect(exo) {
        while (true) {
            pos = exo.currentPosition
            dur = exo.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    LaunchedEffect(show, isPlaying, tick, drag) {
        if (show && isPlaying && drag == null) {
            delay(4000)
            show = false
        }
    }

    LaunchedEffect(hint) {
        if (hint.isNotEmpty()) {
            delay(700)
            hint = ""
        }
    }

    fun seekBy(ms: Long) {
        val t = (exo.currentPosition + ms).coerceAtLeast(0L)
        val d = exo.duration
        exo.seekTo(if (d > 0) t.coerceAtMost(d) else t)
        hint = if (ms > 0) "+${ms / 1000}s" else "${ms / 1000}s"
        tick++
    }

    Box(modifier.background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                PlayerView(c).apply {
                    this.player = exo
                    useController = false
                    keepScreenOn = true
                    resizeMode = resize
                }
            },
            update = {
                it.player = exo
                it.resizeMode = resize
            }
        )

        // gestures layer: tap = show/hide controls, double tap sides = seek
        if (!pip) Box(
            Modifier.fillMaxSize().pointerInput(live) {
                detectTapGestures(
                    onTap = { show = !show; tick++ },
                    onDoubleTap = { o ->
                        if (!live) seekBy(if (o.x < size.width / 2f) -10_000L else 10_000L)
                    }
                )
            }
        )

        if (buffering && errorMsg.isEmpty()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(40.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
        }

        if (hint.isNotEmpty()) {
            Text(
                hint, Modifier.align(Alignment.Center).clip(RoundedCornerShape(20.dp)).background(Color(0x99000000)).padding(horizontal = 16.dp, vertical = 8.dp),
                color = Color.White, fontWeight = FontWeight.SemiBold
            )
        }

        if (errorMsg.isNotEmpty()) {
            Column(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(16.dp)).background(Color(0xCC000000)).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(errorMsg, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { vm.retryPlayback() }) { Text("Retry") }
            }
        }

        if (show && !pip) {
            // top shade + bar
            Row(
                Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                    .then(if (full) Modifier.displayCutoutPadding() else Modifier)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CtrlBtn(Icons.Default.ArrowBack, "Minimize", 44) { onBack() }
                Text(
                    req.title, Modifier.weight(1f), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp, fontWeight = FontWeight.Medium
                )
                CtrlBtn(Icons.Default.Cast, "Cast", 40) {
                    vm.cast(req, if (live) 0L else exo.currentPosition / 1000, true)
                }
                CtrlBtn(Icons.Default.PictureInPictureAlt, "PiP", 40) {
                    (ctx as? MainActivity)?.enterPip()
                }
            }

            // centre controls
            Row(
                Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (full) 28.dp else 12.dp)
            ) {
                CtrlBtn(Icons.Default.SkipPrevious, "Previous", 46, vm.canStep(-1)) { vm.step(-1) }
                if (!live) CtrlBtn(Icons.Default.Replay10, "Back 10", 46) { seekBy(-10_000L) }
                Box(
                    Modifier.size(if (full) 68.dp else 58.dp).clip(CircleShape).background(Color(0xE6FFFFFF))
                        .clickable {
                            vm.togglePlay()
                            tick++
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause",
                        tint = Color.Black, modifier = Modifier.size(34.dp)
                    )
                }
                if (!live) CtrlBtn(Icons.Default.Forward10, "Forward 10", 46) { seekBy(10_000L) }
                CtrlBtn(Icons.Default.SkipNext, "Next", 46, vm.canStep(1)) { vm.step(1) }
            }

            // bottom bar
            Column(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                    .then(if (full) Modifier.displayCutoutPadding().navigationBarsPadding() else Modifier)
                    .padding(horizontal = 12.dp)
            ) {
                if (!live) {
                    Slider(
                        value = drag ?: (if (dur > 0) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f),
                        onValueChange = { drag = it },
                        onValueChangeFinished = {
                            drag?.let { exo.seekTo((it * dur).toLong()) }
                            drag = null
                            tick++
                        },
                        modifier = Modifier.fillMaxWidth().height(22.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color(0x55FFFFFF)
                        )
                    )
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (live) {
                        Text(
                            "● LIVE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFE5173F)).padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    } else {
                        val shownPos = drag?.let { (it * dur).toLong() } ?: pos
                        Text(fmtTime(shownPos) + " / " + fmtTime(dur), color = Color.White, fontSize = 12.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    if (!live) TextButton(
                        onClick = {
                            speed = when (speed) { 1f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2f; 2f -> 0.75f; else -> 1f }
                            exo.setPlaybackSpeed(speed)
                            tick++
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) { Text(speed.toString().removeSuffix(".0") + "x", color = Color.White, fontSize = 13.sp) }
                    CtrlBtn(Icons.Default.AspectRatio, "Aspect", 40) {
                        resize = when (resize) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        tick++
                    }
                    CtrlBtn(if (full) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, "Fullscreen", 40) { onToggleFull() }
                }
            }
        }
    }
}
