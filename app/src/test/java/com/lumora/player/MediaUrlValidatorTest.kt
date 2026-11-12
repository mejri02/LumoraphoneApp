package com.lumora.player

import org.junit.Assert.*
import org.junit.Test

class MediaUrlValidatorTest {
    @Test fun acceptsValidHttpAndHttpsUrls() {
        assertTrue(MediaUrlValidator.isHttpUrl("https://example.com/list.m3u"))
        assertTrue(MediaUrlValidator.isHttpUrl("http://127.0.0.1:8080/movie.ts"))
        assertTrue(MediaUrlValidator.isHttpUrl("http://[::1]:8080/live.ts"))
    }

    @Test fun acceptsUrlsWithNonAsciiAndSpecialCharacters() {
        assertTrue(MediaUrlValidator.isHttpUrl("http://example.com/أفلام/فيلم.mp4"))
        assertTrue(MediaUrlValidator.isHttpUrl("http://example.com/live.ts|User-Agent=VLC"))
    }

    @Test fun rejectsMalformedAndUnsupportedUrls() {
        assertFalse(MediaUrlValidator.isHttpUrl(""))
        assertFalse(MediaUrlValidator.isHttpUrl("not a url"))
        assertFalse(MediaUrlValidator.isHttpUrl("ftp://example.com/list.m3u"))
        assertFalse(MediaUrlValidator.isHttpUrl("https:///missing-host"))
        assertFalse(MediaUrlValidator.isHttpUrl("https://example.com/a b"))
        assertFalse(MediaUrlValidator.isHttpUrl("javascript:alert(1)"))
        assertFalse(MediaUrlValidator.isHttpUrl("http://user:pass@example.com/a"))
        assertFalse(MediaUrlValidator.isHttpUrl("http://example.com:99999/a"))
    }

    @Test fun streamUrlsIncludeVlcTransports() {
        assertTrue(MediaUrlValidator.isStreamUrl("rtmp://example.com/live/stream"))
        assertTrue(MediaUrlValidator.isStreamUrl("udp://239.0.0.1:1234"))
        assertFalse(MediaUrlValidator.isStreamUrl("file:///sdcard/a.mp4"))
        assertFalse(MediaUrlValidator.isHttpUrl("rtmp://example.com/live/stream"))
    }
}
