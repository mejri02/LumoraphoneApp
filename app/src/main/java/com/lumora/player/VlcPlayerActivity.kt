package com.lumora.player

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.Locale

/** Dedicated LibVLC surface for RTMP, UDP/RTP and legacy MMS streams. */
class VlcPlayerActivity : Activity() {
    companion object {
        const val EXTRA_URL = "lumora.vlc.URL"
        const val EXTRA_TITLE = "lumora.vlc.TITLE"
        const val EXTRA_LIVE = "lumora.vlc.LIVE"
    }

    private lateinit var libVlc: LibVLC
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var videoLayout: VLCVideoLayout
    private lateinit var status: TextView
    private lateinit var playButton: Button
    private lateinit var seek: SeekBar
    private lateinit var time: TextView
    private var live = true
    private var seeking = false
    private var multicastLock: WifiManager.MulticastLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            if (::mediaPlayer.isInitialized) {
                val length = mediaPlayer.length
                val current = mediaPlayer.time
                if (!live && length > 0 && !seeking) {
                    seek.max = 1000
                    seek.progress = ((current * 1000L) / length).toInt().coerceIn(0, 1000)
                }
                time.text = if (live) "LIVE" else "${clock(current)} / ${clock(length)}"
                playButton.text = if (mediaPlayer.isPlaying) "Pause" else "Play"
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        live = intent.getBooleanExtra(EXTRA_LIVE, true)
        if (url.isBlank()) { finish(); return }

        val root = FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        videoLayout = VLCVideoLayout(this)
        root.addView(videoLayout, FrameLayout.LayoutParams(-1, -1))
        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(12))
            setBackgroundColor(0x99000000.toInt())
        }
        val heading = TextView(this).apply {
            text = title.ifBlank { url }
            setTextColor(-1); textSize = 15f; maxLines = 1
        }
        status = TextView(this).apply {
            text = "Connecting…"; setTextColor(0xFFFFD166.toInt()); textSize = 12f
        }
        seek = SeekBar(this).apply {
            max = 1000
            visibility = if (live) View.GONE else View.VISIBLE
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) { seeking = fromUser }
                override fun onStartTrackingTouch(bar: SeekBar?) { seeking = true }
                override fun onStopTrackingTouch(bar: SeekBar?) {
                    val length = mediaPlayer.length
                    if (length > 0) mediaPlayer.time = length * (bar?.progress ?: 0) / 1000L
                    seeking = false
                }
            })
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        playButton = Button(this).apply {
            text = "Pause"
            setOnClickListener { if (mediaPlayer.isPlaying) mediaPlayer.pause() else mediaPlayer.play() }
        }
        val retry = Button(this).apply {
            text = "Retry"
            setOnClickListener { openMedia(url) }
        }
        val close = Button(this).apply {
            text = "Close"
            setOnClickListener { finish() }
        }
        time = TextView(this).apply { setTextColor(-1); textSize = 12f }
        controls.addView(playButton)
        controls.addView(retry)
        controls.addView(time, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })
        controls.addView(close)
        overlay.addView(heading)
        overlay.addView(status)
        overlay.addView(seek)
        overlay.addView(controls)
        val lp = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
        root.addView(overlay, lp)
        setContentView(root)

        val scheme = Uri.parse(url).scheme?.lowercase(Locale.ROOT)
        if (scheme == "udp" || scheme == "rtp") {
            try {
                val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                multicastLock = wifi.createMulticastLock("Lumora:VLC multicast").apply {
                    setReferenceCounted(false)
                    acquire()
                }
            } catch (_: Exception) { }
        }

        libVlc = LibVLC(this, arrayListOf(
            "--network-caching=2000",
            "--live-caching=2000",
            "--file-caching=1500",
            "--clock-jitter=0",
            "--clock-synchro=0"
        ))
        mediaPlayer = MediaPlayer(libVlc)
        mediaPlayer.attachViews(videoLayout, null, false, false)
        mediaPlayer.setEventListener { event ->
            runOnUiThread {
                status.text = when (event.type) {
                    MediaPlayer.Event.Opening -> "Opening stream…"
                    MediaPlayer.Event.Buffering -> "Buffering…"
                    MediaPlayer.Event.Playing -> "Playing"
                    MediaPlayer.Event.Paused -> "Paused"
                    MediaPlayer.Event.Stopped -> "Stopped"
                    MediaPlayer.Event.EndReached -> "Stream ended"
                    MediaPlayer.Event.EncounteredError -> "VLC could not decode or reach this stream"
                    else -> status.text
                }
            }
        }
        openMedia(url)
        handler.post(ticker)
    }

    private fun openMedia(url: String) {
        try {
            status.text = "Connecting…"
            val media = Media(libVlc, Uri.parse(url)).apply {
                setHWDecoderEnabled(true, false)
                addOption(":network-caching=2000")
                addOption(":live-caching=2000")
                addOption(":http-reconnect")
            }
            mediaPlayer.stop()
            mediaPlayer.media = media
            media.release()
            mediaPlayer.play()
        } catch (e: Exception) {
            status.text = "Playback error: ${e.message ?: "unknown"}"
        }
    }

    private fun clock(ms: Long): String {
        if (ms < 0) return "0:00"
        val sec = ms / 1000
        return if (sec >= 3600) "%d:%02d:%02d".format(Locale.US, sec / 3600, (sec % 3600) / 60, sec % 60)
        else "%d:%02d".format(Locale.US, sec / 60, sec % 60)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onStop() {
        super.onStop()
        if (::mediaPlayer.isInitialized) mediaPlayer.pause()
    }

    override fun onStart() {
        super.onStart()
        if (::mediaPlayer.isInitialized && !isFinishing) mediaPlayer.play()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        if (::mediaPlayer.isInitialized) {
            mediaPlayer.stop()
            mediaPlayer.detachViews()
            mediaPlayer.release()
        }
        if (::libVlc.isInitialized) libVlc.release()
        multicastLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
