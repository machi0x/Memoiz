package com.machi.memoiz.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Utility class for detecting the source app from which content was copied.
 * Requires PACKAGE_USAGE_STATS permission (enabled via Settings).
 */
class UsageStatsHelper(private val context: Context) {
    
    /**
     * Check if Usage Stats permission is granted.
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun hasUsageStatsPermission(): Boolean {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return false
        
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000 * 10 // Last 10 seconds
        
        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )
        
        return usageStatsList != null && usageStatsList.isNotEmpty()
    }
    
    /**
     * Get the name of the app that was in foreground most recently.
     * Returns null if permission is not granted or app cannot be determined.
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun getLastForegroundApp(): String? {
        if (!hasUsageStatsPermission()) {
            return null
        }
        
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000 * 10 // Last 10 seconds
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
                var lastApp: String? = null
                var lastTime = 0L
                
                while (usageEvents.hasNextEvent()) {
                    val event = UsageEvents.Event()
                    usageEvents.getNextEvent(event)
                    
                    if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                        event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                        if (event.timeStamp > lastTime) {
                            lastTime = event.timeStamp
                            lastApp = event.packageName
                        }
                    }
                }
                
                return lastApp?.let { getAppName(it) }
            } else {
                // Fallback for LOLLIPOP
                val usageStatsList = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    startTime,
                    endTime
                )
                
                val lastUsed = usageStatsList?.maxByOrNull { it.lastTimeUsed }
                return lastUsed?.packageName?.let { getAppName(it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * Get human-readable app name from package name.
     */
    private fun getAppName(packageName: String): String {
        return try {
            val packageManager = context.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }
}
