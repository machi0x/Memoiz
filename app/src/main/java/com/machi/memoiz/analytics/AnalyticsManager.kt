package com.machi.memoiz.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Lightweight Analytics wrapper for logging simple events.
 * Currently logs two image-view events used by the app UI.
 */
object AnalyticsManager {
    private const val EVENT_TUTORIAL_MAIN_UI = "tutorial_main_ui_view"
    private const val EVENT_ABOUT_THANKS = "about_thanks_view"
    private const val EVENT_MEMO_STATS = "memo_stats"
    private const val EVENT_USAGE_STATS_PERMISSION = "usage_stats_permission_status"


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
}
