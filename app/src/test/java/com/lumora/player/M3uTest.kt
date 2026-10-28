package com.lumora.player

import org.junit.Assert.*
import org.junit.Test

class M3uTest {
    @Test fun parsesValidEntriesAndIgnoresMalformedLinks() {
        val input = """
            #EXTM3U
            #EXTINF:-1 tvg-name="News" group-title="News",News Channel
            https://example.com/live.m3u8
            #EXTINF:-1,Invalid entry
            javascript:alert(1)
            #EXTINF:-1,Movie
            https://example.com/movie.mp4
        """.trimIndent()

        val (live, vod, series) = M3u.parse(input, "test-source")
        assertEquals(1, live.size)
        assertEquals("News Channel", live[0].name)
        assertEquals(1, vod.size)
        assertEquals("Movie", vod[0].name)
        assertTrue(series.isEmpty())
    }

    @Test fun toleratesEmptyOrCorruptedPlaylist() {
        val (live, vod, series) = M3u.parse("not a playlist\nhttps://example.com/good.m3u8\nhttp://", "test")
        assertEquals(1, live.size)
        assertTrue(vod.isEmpty())
        assertTrue(series.isEmpty())
    }
}
