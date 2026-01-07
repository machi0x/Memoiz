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

/**
 * Application class for Memoiz.
 * Used for app-wide initialization.
 */
class MemoizApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        // Future: Initialize WorkManager configurations here if needed
        // Future: Initialize AI service when Gemini Nano becomes available

        logInitialAnalytics()
    }

    private fun logInitialAnalytics() {
        GlobalScope.launch {
            val database = MemoizDatabase.getDatabase(applicationContext)
            val memoRepository = MemoRepository(database.memoDao())

            val memos = memoRepository.getAllMemos().first()
            val counts = memos.groupingBy { it.memoType }.eachCount()
            val textCount = counts[MemoType.TEXT] ?: 0
            val webCount = counts[MemoType.WEB_SITE] ?: 0
            val imageCount = counts[MemoType.IMAGE] ?: 0
            AnalyticsManager.logMemoStats(applicationContext, textCount, webCount, imageCount)

            val isPermissionGranted = UsageStatsHelper(applicationContext).hasUsageStatsPermission()
            AnalyticsManager.logUsageStatsPermission(applicationContext, isPermissionGranted)
        }
    }
}
