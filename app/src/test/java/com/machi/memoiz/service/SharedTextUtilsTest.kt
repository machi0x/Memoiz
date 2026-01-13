package com.machi.memoiz.service

import org.junit.Assert.*
import org.junit.Test

class SharedTextUtilsTest {

    @Test
    fun `extract URL from title newline url`() {
        val text = "速報 総理　衆議院の解散\nhttps://example.com/article"
        val url = SharedTextUtils.extractSharedUrlIfEligible(text)
        assertEquals("https://example.com/article", url)
    }

    @Test
    fun `extract URL from one line with title and url`() {
        val text = "最高のAndroidアプリ！ https://share.machi/1blddv9LJa5eswcDKv"
        val url = SharedTextUtils.extractSharedUrlIfEligible(text)
        // This one is a single line with title and url; allowed by rule (1..3 lines) and url at end
        assertEquals("https://share.machi/1blddv9LJa5eswcDKv", url)
    }

    @Test
    fun `do not extract when url in middle of long text`() {
        val text = "This is a long text that mentions https://example.com in the middle and continues after the url with more content."
        val url = SharedTextUtils.extractSharedUrlIfEligible(text)
        assertNull(url)
    }

    @Test
    fun `do not extract when multiple urls present`() {
        val text = "Check this https://a.com and also https://b.com"
        val url = SharedTextUtils.extractSharedUrlIfEligible(text)
        assertNull(url)
    }

    @Test
    fun `trim trailing punctuation`() {
        val text = "タイトル\nhttps://example.com/article."
        val url = SharedTextUtils.extractSharedUrlIfEligible(text)
        assertEquals("https://example.com/article", url)
    }
}

