@file:OptIn(androidx.media3.common.util.UnstableApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lumora.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Rational
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
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
 * Video surface with our own controls. Used inline at the top of the detail page (16:9) and
 * full screen (landscape / fullscreen button). One instance is kept while switching between the two.
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
    val ctx = LocalContext.current
    val act = ctx as Activity
    val handler = remember { Handler(Looper.getMainLooper()) }
    val live = req.kind == "live"
    val resumeAt = remember(req.url) { if (live || !vm.resumeOn) 0L else (req.item?.let { vm.progressOf(it) } ?: 0L) }
    var resumed by remember(req.url) { mutableStateOf(false) }
    var buffering by remember(req.url) { mutableStateOf(true) }
    var errorMsg by remember(req.url) { mutableStateOf("") }
    var show by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    var resize by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var speed by remember { mutableFloatStateOf(1f) }
    var pos by remember(req.url) { mutableLongStateOf(0L) }
    var dur by remember(req.url) { mutableLongStateOf(0L) }
    var isPlaying by remember(req.url) { mutableStateOf(true) }
    var drag by remember { mutableStateOf<Float?>(null) }
    var hint by remember { mutableStateOf("") }
    var attempt by remember(req.url) { mutableIntStateOf(0) }

    val exo = remember(req.url, attempt) {
        val ds = androidx.media3.datasource.DefaultDataSource.Factory(ctx, OkHttpDataSource.Factory(Net.client).setUserAgent(Net.UA))
        ExoPlayer.Builder(ctx).setMediaSourceFactory(DefaultMediaSourceFactory(ds)).build().apply {
            setMediaItem(MediaItem.fromUri(req.url))
            playWhenReady = true
            prepare()
        }
    }

    fun save() {
        req.item?.let { if (!live) vm.saveProgress(it, exo.currentPosition, exo.duration.coerceAtLeast(0L)) }
    }

    DisposableEffect(exo) {
        var retries = 0
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    if (!resumed) {
                        resumed = true
                        if (resumeAt > 0L) exo.seekTo(resumeAt)
                    }
                    errorMsg = ""
                    retries = 0
                }
            }

            override fun onIsPlayingChanged(p: Boolean) {
                isPlaying = p
            }

            override fun onPlayerError(error: PlaybackException) {
                if (retries < 6) {
                    retries++
                    handler.postDelayed({
                        try {
                            exo.prepare()
                            exo.play()
                        } catch (_: Exception) {
                        }
                    }, (700L * retries).coerceAtMost(5000))
                } else {
                    errorMsg = "Could not load this content."
                }
            }
        }
        exo.addListener(l)
        onDispose {
            save()
            handler.removeCallbacksAndMessages(null)
            exo.removeListener(l)
            exo.release()
        }
    }

    // keep progress safe even if Android kills the app in the background
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, exo) {
        val ob = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP || e == Lifecycle.Event.ON_PAUSE) save() }
        owner.lifecycle.addObserver(ob)
        onDispose { owner.lifecycle.removeObserver(ob) }
    }

    LaunchedEffect(exo) {
        var n = 0
        while (true) {
            delay(500)
            pos = exo.currentPosition
            dur = exo.duration.coerceAtLeast(0L)
            n++
            if (n % 10 == 0 && exo.isPlaying) save()
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
                Button(onClick = { errorMsg = ""; attempt++ }) { Text("Retry") }
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
                CtrlBtn(Icons.Default.ArrowBack, "Back", 44) { onBack() }
                Text(
                    req.title, Modifier.weight(1f), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp, fontWeight = FontWeight.Medium
                )
                CtrlBtn(Icons.Default.Cast, "Cast", 40) {
                    vm.cast(req, if (live) 0L else exo.currentPosition / 1000, true)
                }
                CtrlBtn(Icons.Default.PictureInPictureAlt, "PiP", 40) {
                    if (Build.VERSION.SDK_INT >= 26) try {
                        act.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
                    } catch (_: Exception) {
                    }
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
                            if (exo.isPlaying) exo.pause() else exo.play()
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
