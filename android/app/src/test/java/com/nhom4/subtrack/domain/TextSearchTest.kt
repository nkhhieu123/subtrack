package com.nhom4.subtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSearchTest {
    /** BUG-02: gõ không dấu vẫn tìm được tên có dấu. */
    @Test
    fun matches_ignoresCaseAndVietnameseMarks() {
        assertEquals("goi gia dinh", TextSearch.fold("Gói Gia Đình"))
        assertTrue(TextSearch.matches("Gói gia đình", "goi gia dinh"))
        assertTrue(TextSearch.matches("Gói gia đình", "GÓI"))
        assertTrue(TextSearch.matches("Netflix", "net"))
        assertEquals(false, TextSearch.matches("Netflix", "xyz"))
    }
}
