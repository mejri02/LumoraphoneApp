package com.lumora.player

import org.junit.Assert.*
import org.junit.Test

class MediaUrlValidatorTest {
    @Test fun acceptsValidHttpAndHttpsUrls() {
        assertTrue(MediaUrlValidator.isHttpUrl("https://example.com/list.m3u"))
        assertTrue(MediaUrlValidator.isHttpUrl("http://127.0.0.1:8080/movie.ts"))
    }

    @Test fun rejectsMalformedAndUnsupportedUrls() {
        assertFalse(MediaUrlValidator.isHttpUrl(""))
        assertFalse(MediaUrlValidator.isHttpUrl("not a url"))
        assertFalse(MediaUrlValidator.isHttpUrl("ftp://example.com/list.m3u"))
        assertFalse(MediaUrlValidator.isHttpUrl("https:///missing-host"))
        assertFalse(MediaUrlValidator.isHttpUrl("https://example.com/a b"))
    }
}
