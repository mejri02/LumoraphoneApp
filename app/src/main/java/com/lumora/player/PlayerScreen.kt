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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import java.io.File
import java.util.Locale
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
import kotlinx.coroutines.launch

fun fmtTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val t = ms / 1000
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(Locale.US, h, m, s) else "%d:%02d".format(Locale.US, m, s)
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
    var pos by remember(req.url) { mutableLongStateOf(0L) }
    var dur by remember(req.url) { mutableLongStateOf(0L) }
    var drag by remember { mutableStateOf<Float?>(null) }
    var hint by remember { mutableStateOf("") }
    var showSubtitles by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var subtitleFile by remember(req.url) { mutableStateOf<File?>(null) }
    var subtitleOffset by remember(req.url) { mutableIntStateOf(0) }
    var subtitleStatus by remember { mutableStateOf("") }
    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                val name = uri.lastPathSegment.orEmpty()
                val ext = if (name.substringAfterLast('.', "").equals("vtt", true)) "vtt" else "srt"
                val file = File(ctx.cacheDir, "subtitle_${System.currentTimeMillis()}.$ext")
                val input = ctx.contentResolver.openInputStream(uri) ?: throw java.io.IOException("cannot open the file")
                input.use { inp -> file.outputStream().use { inp.copyTo(it) } }
                subtitleFile = file
                subtitleOffset = 0
                subtitleStatus = "Subtitle loaded: ${file.name}"
                applySubtitleFile(exo, req.url, file, 0)
            } catch (e: Exception) { subtitleStatus = "Could not read subtitle file: ${e.message ?: "unknown error"}" }
        }
    }

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
                    keepScreenOn = vm.keepOn
                    resizeMode = resize
                }
            },
            update = {
                it.player = exo
                it.resizeMode = resize
                it.keepScreenOn = vm.keepOn
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
                            vm.cycleSpeed()
                            tick++
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) { Text(vm.speedLabel(), color = Color.White, fontSize = 13.sp) }
                    if (!live) CtrlBtn(Icons.Default.Subtitles, "Subtitles", 40) { showSubtitles = true; show = true }
                    IconButton(onClick = { showSleep = true; show = true }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Default.Timer, "Sleep timer",
                            tint = if (vm.sleepAt > 0L) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.size(25.dp)
                        )
                    }
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

        if (showSleep) AlertDialog(
            onDismissRequest = { showSleep = false },
            title = { Text("Sleep timer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (vm.sleepAt > 0L) {
                        val left = ((vm.sleepAt - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0L) + 1L
                        Text("Pauses in about $left min", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("Pause playback automatically after:", style = MaterialTheme.typography.bodySmall)
                    }
                    listOf(0 to "Off", 15 to "15 minutes", 30 to "30 minutes", 60 to "1 hour", 90 to "1.5 hours", 120 to "2 hours").forEach { (m, label) ->
                        TextButton(onClick = { vm.setSleepTimer(m); showSleep = false }, modifier = Modifier.fillMaxWidth()) {
                            Text(label, Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSleep = false }) { Text("Close") } }
        )

        if (showSubtitles) AlertDialog(
            onDismissRequest = { showSubtitles = false },
            title = { Text("Subtitles") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Load a subtitle file from your phone (.srt or .vtt).", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { subtitlePicker.launch(arrayOf("text/*", "application/x-subrip", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Choose subtitle file (.srt / .vtt)")
                    }
                    if (subtitleFile != null) {
                        Divider()
                        Text("Sync: ${if (subtitleOffset > 0) "+" else ""}%.1f s".format(Locale.US, subtitleOffset / 1000.0))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { subtitleOffset -= 500; applySubtitleFile(exo, req.url, subtitleFile!!, subtitleOffset) }, modifier = Modifier.weight(1f)) { Text("−0.5 s") }
                            OutlinedButton(onClick = { subtitleOffset += 500; applySubtitleFile(exo, req.url, subtitleFile!!, subtitleOffset) }, modifier = Modifier.weight(1f)) { Text("+0.5 s") }
                            TextButton(onClick = { subtitleOffset = 0; applySubtitleFile(exo, req.url, subtitleFile!!, 0) }) { Text("Reset") }
                        }
                    }
                    if (subtitleStatus.isNotBlank()) Text(subtitleStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            },
            confirmButton = { TextButton(onClick = { showSubtitles = false }) { Text("Done") } }
        )
    }
}

private fun applySubtitleFile(player: androidx.media3.exoplayer.ExoPlayer, videoUrl: String, original: File, offsetMs: Int) {
    try {
        val raw = decodeSubtitle(original.readBytes())
        val shifted = shiftSubtitleTimestamps(raw, offsetMs)
        // Trust the file content as well as the extension: some pickers report a generic name.
        val isVtt = original.extension.equals("vtt", true) || raw.trimStart().startsWith("WEBVTT", ignoreCase = true)
        val out = File(original.parentFile, "${original.nameWithoutExtension}_offset_${offsetMs}.${if (isVtt) "vtt" else "srt"}")
        out.writeText(shifted, Charsets.UTF_8)
        val mime = if (isVtt) "text/vtt" else "application/x-subrip"
        val config = androidx.media3.common.MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(out))
            .setMimeType(mime).setLanguage("und").setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT).build()
        val item = androidx.media3.common.MediaItem.Builder().setUri(videoUrl).setSubtitleConfigurations(listOf(config)).build()
        val position = player.currentPosition
        val wasPlaying = player.isPlaying
        player.replaceMediaItem(player.currentMediaItemIndex.coerceAtLeast(0), item)
        player.seekTo(position)
        player.playWhenReady = wasPlaying
    } catch (_: Exception) { }
}

/**
 * Subtitle files are often not UTF-8 (Arabic files are commonly windows-1256), and reading them as
 * UTF-8 turns every letter into "?". Detect the encoding instead of assuming one.
 */
internal fun decodeSubtitle(b: ByteArray): String {
    if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()) return String(b, 3, b.size - 3, Charsets.UTF_8)
    if (b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16LE)
    if (b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16BE)
    try {
        return Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(b)).toString()
    } catch (_: java.nio.charset.CharacterCodingException) {
    }
    val high = b.count { it.toInt() and 0x80 != 0 }
    val arabic = try { String(b, java.nio.charset.Charset.forName("windows-1256")) } catch (_: Exception) { null }
    if (arabic != null && high > 0 && arabic.count { it in '؀'..'ۿ' } * 2 >= high) return arabic
    return try { String(b, java.nio.charset.Charset.forName("windows-1252")) } catch (_: Exception) { String(b, Charsets.ISO_8859_1) }
}

private fun shiftSubtitleTimestamps(text: String, offsetMs: Int): String {
    // Standard SRT/VTT timestamps are HH:MM:SS,mmm, but some files use MM:SS,mmm.
    val regex = Regex("(?m)(?<!\\d)(?:(\\d{1,2}):)?(\\d{1,2}):(\\d{2})[,.](\\d{3})")
    return regex.replace(text) { match ->
        val hadHours = match.groupValues[1].isNotEmpty()
        val h0 = match.groupValues[1].toLongOrNull() ?: 0L
        val m0 = match.groupValues[2].toLong()
        val s0 = match.groupValues[3].toLong()
        val ms0 = match.groupValues[4].toLong()
        val millis = ((h0 * 3600 + m0 * 60 + s0) * 1000 + ms0 + offsetMs).coerceAtLeast(0L)
        val h = millis / 3600000
        val m = (millis % 3600000) / 60000
        val sec = (millis % 60000) / 1000
        val ms = millis % 1000
        val separator = if (match.value.contains('.')) '.' else ','
        if (hadHours || h > 0) "%02d:%02d:%02d%s%03d".format(Locale.US, h, m, sec, separator, ms)
        else "%02d:%02d%s%03d".format(Locale.US, m, sec, separator, ms)
    }
}
