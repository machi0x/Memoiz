package com.machi.memoiz.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.mlkit.genai.common.FeatureStatus

/**
 * Lightweight Analytics wrapper for logging simple events.
 * Currently logs two image-view events used by the app UI.
 */
object AnalyticsManager {
    private const val EVENT_TUTORIAL_MAIN_UI = "tutorial_main_ui_view"
    private const val EVENT_ABOUT_THANKS = "about_thanks_view"
    private const val EVENT_MEMO_STATS = "memo_stats"
    private const val EVENT_USAGE_STATS_PERMISSION = "usage_stats_permission_status"

    // New startup-specific events
    private const val EVENT_STARTUP_SORT_KEY = "startup_sort_key"
    private const val EVENT_STARTUP_MY_CATEGORY_COUNT = "startup_my_category_count"
    private const val EVENT_STARTUP_MEMO_STATS_RANGES = "startup_memo_stats_ranges"
    private const val EVENT_STARTUP_GENAI_IMAGE = "startup_genai_image_status"
    private const val EVENT_STARTUP_GENAI_TEXT = "startup_genai_text_status"
    private const val EVENT_STARTUP_GENAI_SUMMARY = "startup_genai_summarization_status"


    fun logEvent(context: Context, name: String, params: Bundle? = null) {
        try {
            val analytics = FirebaseAnalytics.getInstance(context)
            analytics.logEvent(name, params)
        } catch (e: Exception) {
            // Swallow any analytics errors; analytics must never crash the app.
            android.util.Log.w("AnalyticsManager", "logEvent failed: $name", e)
        }
    }

    fun logTutorialMainUiView(context: Context) {
        logEvent(context, EVENT_TUTORIAL_MAIN_UI)
    }

    fun logAboutThanksView(context: Context) {
        logEvent(context, EVENT_ABOUT_THANKS)
    }

    fun logMemoStats(context: Context, textCount: Int, webCount: Int, imageCount: Int) {
        val params = Bundle().apply {
            putInt("text_memo_count", textCount)
            putInt("web_memo_count", webCount)
            putInt("image_memo_count", imageCount)
        }
        logEvent(context, EVENT_MEMO_STATS, params)
    }

    fun logUsageStatsPermission(context: Context, isGranted: Boolean) {
        val params = Bundle().apply {
            putString("is_granted", isGranted.toString())
        }
        logEvent(context, EVENT_USAGE_STATS_PERMISSION, params)
    }

    // --- New helpers & startup logging ---

    /**
     * Convert an integer count into the project's predefined range labels.
     * Ranges: 0, 1 - 10, 10 - 50, 50 - 100, 100 - 200, 200 - 500, 1000+
     */
    fun bucketCountToLabel(count: Int): String {
        return when {
            count <= 0 -> "0"
            count in 1..10 -> "1 - 10"
            count in 11..50 -> "10 - 50"
            count in 51..100 -> "50 - 100"
            count in 101..200 -> "100 - 200"
            count in 201..500 -> "200 - 500"
            else -> "1000+"
        }
    }

    /**
     * Map ML Kit FeatureStatus int to the telemetry string values.
     * UNKNOWN-like values should be mapped to "unknown" by the caller if needed.
     */
    fun featureStatusToString(@FeatureStatus status: Int): String = when (status) {
        FeatureStatus.AVAILABLE -> "available"
        FeatureStatus.DOWNLOADABLE -> "downloadable"
        FeatureStatus.UNAVAILABLE -> "unavailable"
        else -> "unknown"
    }

    fun logStartupSortKey(context: Context, sortKeyLabel: String) {
        val params = Bundle().apply {
            putString("sort_key", sortKeyLabel)
        }
        logEvent(context, EVENT_STARTUP_SORT_KEY, params)
    }

    fun logStartupMyCategoryCount(context: Context, myCategoryCountLabel: String) {
        val params = Bundle().apply {
            putString("my_category_count_range", myCategoryCountLabel)
        }
        logEvent(context, EVENT_STARTUP_MY_CATEGORY_COUNT, params)
    }

    fun logStartupMemoStatsRanges(context: Context, textRange: String, webRange: String, imageRange: String) {
        val params = Bundle().apply {
            putString("text_memo_count_range", textRange)
            putString("web_memo_count_range", webRange)
            putString("image_memo_count_range", imageRange)
        }
        logEvent(context, EVENT_STARTUP_MEMO_STATS_RANGES, params)
    }

    // Generic method (keeps flexibility)
    fun logStartupGenAiStatus(context: Context, featureEventName: String, status: String) {
        val params = Bundle().apply {
            putString("status", status)
        }
        logEvent(context, featureEventName, params)
    }

    // Convenience wrappers for the three GenAI features so callers don't need to know event names
    fun logStartupGenAiImageStatus(context: Context, status: String) {
        val params = Bundle().apply { putString("status", status) }
        logEvent(context, EVENT_STARTUP_GENAI_IMAGE, params)
    }

    fun logStartupGenAiTextStatus(context: Context, status: String) {
        val params = Bundle().apply { putString("status", status) }
        logEvent(context, EVENT_STARTUP_GENAI_TEXT, params)
    }

    fun logStartupGenAiSummaryStatus(context: Context, status: String) {
        val params = Bundle().apply { putString("status", status) }
        logEvent(context, EVENT_STARTUP_GENAI_SUMMARY, params)
    }
}
