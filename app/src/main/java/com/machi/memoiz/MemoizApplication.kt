package com.machi.memoiz

import android.app.Application
import com.machi.memoiz.analytics.AnalyticsManager
import com.machi.memoiz.data.MemoizDatabase
import com.machi.memoiz.data.entity.MemoType
import com.machi.memoiz.data.repository.MemoRepository
import com.machi.memoiz.util.UsageStatsHelper
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.machi.memoiz.data.datastore.PreferencesDataStoreManager
import com.machi.memoiz.service.GenAiStatusManager

/**
 * Application class for Memoiz.
 * Used for app-wide initialization.
 */
class MemoizApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        // Initialize analytics/crashlytics collection according to stored user preference (default: false)
        try {
            val prefsManager = PreferencesDataStoreManager(applicationContext)
            val analyticsEnabled = prefsManager.isAnalyticsCollectionEnabledSync()
            AnalyticsManager.setCollectionEnabled(applicationContext, analyticsEnabled)
        } catch (e: Exception) {
            android.util.Log.w("MemoizApplication", "Failed to initialize analytics collection from prefs", e)
        }
        // Future: Initialize WorkManager configurations here if needed
        // Future: Initialize AI service when Gemini Nano becomes available

        logInitialAnalytics()
    }

    private fun logInitialAnalytics() {
        GlobalScope.launch {
            // Only send startup analytics if the user has consented.
            val preferencesManager = PreferencesDataStoreManager(applicationContext)
            val userPrefs = try {
                preferencesManager.userPreferencesFlow.first()
            } catch (e: Exception) {
                android.util.Log.w("MemoizApplication", "Failed to read user prefs for analytics; skipping startup analytics", e)
                null
            }

            if (userPrefs == null || !userPrefs.analyticsCollectionEnabled) {
                android.util.Log.d("MemoizApplication", "Analytics disabled by user preference; skipping startup analytics events")
                return@launch
            }

            try {
                val database = MemoizDatabase.getDatabase(applicationContext)
                val memoRepository = MemoRepository(database.memoDao())

                val memos = memoRepository.getAllMemos().first()
                val counts = memos.groupingBy { it.memoType }.eachCount()
                val textCount = counts[MemoType.TEXT] ?: 0
                val webCount = counts[MemoType.WEB_SITE] ?: 0
                val imageCount = counts[MemoType.IMAGE] ?: 0

                // Send memo counts as ranges
                val textRange = AnalyticsManager.bucketCountToLabel(textCount)
                val webRange = AnalyticsManager.bucketCountToLabel(webCount)
                val imageRange = AnalyticsManager.bucketCountToLabel(imageCount)
                AnalyticsManager.logStartupMemoStatsRanges(applicationContext, textRange, webRange, imageRange)

                // Send my (custom) category count as a range
                val myCategoryCount = userPrefs.customCategories.size
                val myCategoryRange = AnalyticsManager.bucketCountToLabel(myCategoryCount)
                AnalyticsManager.logStartupMyCategoryCount(applicationContext, myCategoryRange)

                // Determine sort key label
                val sortKeyLabel = if (userPrefs.categoryOrder.isNotEmpty()) {
                    "manual_order"
                } else {
                    "created_desc"
                }
                AnalyticsManager.logStartupSortKey(applicationContext, sortKeyLabel)

                // Send GenAI last-check statuses from DataStore (if absent -> "unknown") as individual events
                val imgLast = preferencesManager.genAiImageLastCheckFlow.first()
                val textLast = preferencesManager.genAiTextLastCheckFlow.first()
                val sumLast = preferencesManager.genAiSummaryLastCheckFlow.first()

                AnalyticsManager.logStartupGenAiStatus(applicationContext, "startup_genai_image_status", imgLast ?: "unknown")
                AnalyticsManager.logStartupGenAiStatus(applicationContext, "startup_genai_text_status", textLast ?: "unknown")
                AnalyticsManager.logStartupGenAiStatus(applicationContext, "startup_genai_summarization_status", sumLast ?: "unknown")

                // Existing telemetry: usage stats permission (keep as before)
                val isPermissionGranted = UsageStatsHelper(applicationContext).hasUsageStatsPermission()
                AnalyticsManager.logUsageStatsPermission(applicationContext, isPermissionGranted)

                // Kick off a background GenAI check to refresh saved statuses (do not block startup)
                try {
                    val manager = GenAiStatusManager(applicationContext)
                    val fresh = manager.checkAll() // suspending
                    // Map int statuses to strings using Analytics helper
                    val imgStatus = AnalyticsManager.featureStatusToString(fresh.imageDescription)
                    val textStatus = AnalyticsManager.featureStatusToString(fresh.textGeneration)
                    val sumStatus = AnalyticsManager.featureStatusToString(fresh.summarization)

                    // Persist results to DataStore for next launch
                    try {
                        preferencesManager.setGenAiImageLastCheck(imgStatus)
                        preferencesManager.setGenAiTextLastCheck(textStatus)
                        preferencesManager.setGenAiSummaryLastCheck(sumStatus)
                    } catch (e: Exception) {
                        android.util.Log.w("MemoizApplication", "Failed to persist GenAI last-check statuses", e)
                    }

                    manager.close()
                } catch (e: Exception) {
                    android.util.Log.w("MemoizApplication", "Background GenAI status check failed", e)
                }
            } catch (e: Exception) {
                android.util.Log.w("MemoizApplication", "Startup analytics collection failed", e)
            }
        }
    }
}
