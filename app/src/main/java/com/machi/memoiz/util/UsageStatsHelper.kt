package com.machi.memoiz.util

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process

/**
 * Utility class for detecting the source app from which content was copied.
 * Requires PACKAGE_USAGE_STATS permission (enabled via Settings).
 */
class UsageStatsHelper(private val context: Context) {

    /**
     * Check if Usage Stats permission is granted.
     */
    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_DEFAULT -> context.checkCallingOrSelfPermission(
                Manifest.permission.PACKAGE_USAGE_STATS
            ) == PackageManager.PERMISSION_GRANTED
            else -> false
        }
    }

    /**
     * Get the name of the app that was in foreground most recently.
     * Returns null if permission is not granted or app cannot be determined.
     */
    fun getLastForegroundApp(): String? {
        if (!hasUsageStatsPermission()) {
            return null
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null

        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000 * 10 // Last 10 seconds

        try {
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
