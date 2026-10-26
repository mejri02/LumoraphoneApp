@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.lumora.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@Composable
fun PlayerScreen(vm: AppVM, req: PlayReq) {
    val ctx = LocalContext.current
    val act = ctx as Activity
    val land = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var overlay by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf("") }
    var resize by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val live = req.kind == "live"

    val exo = remember(req.url) {
        val ds = OkHttpDataSource.Factory(Net.client).setUserAgent(Net.UA)
        ExoPlayer.Builder(ctx).setMediaSourceFactory(DefaultMediaSourceFactory(ds)).build().apply {
            setMediaItem(MediaItem.fromUri(req.url))
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(exo) {
        var retries = 0
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) { errorMsg = ""; retries = 0 }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (retries < 6) {
                    retries++
                    handler.postDelayed({ try { exo.prepare(); exo.play() } catch (_: Exception) { } }, (700L * retries).coerceAtMost(5000))
                } else errorMsg = "Could not load this content."
            }
        }
        exo.addListener(l)
        onDispose {
            handler.removeCallbacksAndMessages(null)
            exo.removeListener(l)
            exo.release()
        }
    }

    DisposableEffect(land) {
        val w = act.window
        val c = WindowCompat.getInsetsController(w, w.decorView)
        WindowCompat.setDecorFitsSystemWindows(w, !land)
        if (land) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else c.show(WindowInsetsCompat.Type.systemBars())
        onDispose { }
    }

    DisposableEffect(Unit) {
        onDispose {
            act.requestedOrientation = ActivityInfo.REQUESTED_ORIENTATION_UNSPECIFIED
            val w = act.window
            WindowCompat.setDecorFitsSystemWindows(w, true)
            WindowCompat.getInsetsController(w, w.decorView).show(WindowInsetsCompat.Type.systemBars())
        }
    }

    BackHandler {
        vm.playing = null
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                PlayerView(c).apply {
                    this.player = exo
                    useController = true
                    keepScreenOn = true
                    resizeMode = resize
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v -> overlay = v == View.VISIBLE })
                }
            },
            update = { it.resizeMode = resize }
        )
        if (buffering && errorMsg.isEmpty()) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        if (errorMsg.isNotEmpty()) Text(errorMsg, Modifier.align(Alignment.Center).background(Color(0xCC000000)).padding(16.dp), color = Color.White)
        if (overlay) Row(
            Modifier.fillMaxWidth().align(Alignment.TopCenter).background(Color(0x66000000)).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { vm.playing = null }) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
            Text(req.title, Modifier.weight(1f), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            req.item?.let { fav ->
                IconButton(onClick = { vm.toggleFav(fav) }) {
                    Icon(if (vm.isFav(fav)) Icons.Default.Star else Icons.Default.StarBorder, "Favorite", tint = if (vm.isFav(fav)) Color(0xFFFBBF24) else Color.White)
                }
            }
            IconButton(onClick = {
                resize = when (resize) {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            }) { Icon(Icons.Default.AspectRatio, "Aspect", tint = Color.White) }
            IconButton(onClick = {
                act.requestedOrientation = if (land) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }) { Icon(Icons.Default.ScreenRotation, "Rotate", tint = Color.White) }
            IconButton(onClick = {
                if (Build.VERSION.SDK_INT >= 26) try {
                    act.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
                } catch (_: Exception) { }
            }) { Icon(Icons.Default.PictureInPictureAlt, "PiP", tint = Color.White) }
            IconButton(onClick = {
                val pos = if (live) 0L else exo.currentPosition / 1000
                vm.cast(req, pos, true)
            }) { Icon(Icons.Default.Cast, "Cast", tint = Color.White) }
        }
    }
}
