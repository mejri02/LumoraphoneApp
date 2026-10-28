package com.lumora.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchNormalizationTest {
    @Test fun foldsArabicAlefAndYaVariants() {
        assertEquals(searchKey("أخبار"), searchKey("اخبار"))
        assertEquals(searchKey("على"), searchKey("علي"))
        assertEquals(searchKey("مدرسة"), searchKey("مدرسه"))
    }

    @Test fun foldsLatinDiacriticsAndWhitespace() {
        assertEquals(searchKey("cafe sport"), searchKey("  café   sport  "))
        assertTrue(searchKey("Movie title").contains(searchKey("movie")))
    }
}
