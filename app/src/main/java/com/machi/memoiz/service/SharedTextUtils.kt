package com.machi.memoiz.service

/**
 * Utilities for detecting "shared URL" style text blobs (e.g. "Title... <newline?> https://..."),
 * and extracting the URL when the blob should be treated as a URL-type memo.
 *
 * Criteria implemented:
 *  - Input must be 1..3 non-blank lines
 *  - Exactly one http(s)://\S+ URL must appear
 *  - After the URL there must be no additional non-punctuation text (URL must be at the end)
 *  - Trailing punctuation frequently attached by sharing UIs (e.g. ".", ")", full-width punctuation) is trimmed
 */
object SharedTextUtils {
    private val urlRegex = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

    // Characters commonly attached to URLs by surrounding text; we trim these when checking
    private val trailingPunctChars = setOf(
        '.', ',', ';', ':', '、', '。', ')', '）', ']', '］', '}', '｝', '>', '〉', '》',
        '\'', '"', '”', '’', '」', '』', '»', '。'
    )

    /**
     * If the given text matches the "shared title + url" pattern, extract and return the URL (trimmed).
     * Otherwise return null.
     */
    fun extractSharedUrlIfEligible(text: String?): String? {
        if (text.isNullOrBlank()) return null

        val lines = text.trim().lines().filter { it.isNotBlank() }
        if (lines.isEmpty() || lines.size > 3) return null

        val matches = urlRegex.findAll(text).toList()
        if (matches.size != 1) return null
        val match = matches.first()
        var url = match.value
        url = trimTrailingPunctuation(url)
        if (url.length <= "http://".length) return null

        // Ensure there's no substantive text after the URL in the original string.
        val after = text.substring(match.range.last + 1)
        // If there's any non-whitespace characters after the match, they must be only punctuation we recognize
        val afterTrim = after.trim()
        if (afterTrim.isNotEmpty()) {
            val ok = afterTrim.all { ch -> ch.isWhitespace() || trailingPunctChars.contains(ch) }
            if (!ok) return null
        }

        // Finally, ensure the (trimmed) URL is at the logical end of the whole text (ignoring trailing punctuation/whitespace)
        var end = text.trimEnd()
        while (end.isNotEmpty() && (end.last().isWhitespace() || trailingPunctChars.contains(end.last()))) {
            end = end.dropLast(1)
        }
        if (!end.endsWith(url)) return null

        return url
    }

    private fun trimTrailingPunctuation(s: String): String {
        var res = s
        while (res.isNotEmpty() && (res.last().isWhitespace() || trailingPunctChars.contains(res.last()))) {
            res = res.dropLast(1)
        }
        return res
    }

    fun isLikelySharedUrlText(text: String?): Boolean = extractSharedUrlIfEligible(text) != null
}

