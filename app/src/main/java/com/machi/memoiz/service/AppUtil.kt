package com.machi.memoiz.service

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager

fun determineSourceApp(context: Context): String? {
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val time = System.currentTimeMillis()
    val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 10, time)
    if (stats.isNullOrEmpty()) {
        return null
    }
    var recentApp: String? = null
    var lastTime = 0L
    for (usageStats in stats) {
        if (usageStats.lastTimeUsed > lastTime) {
            recentApp = usageStats.packageName
            lastTime = usageStats.lastTimeUsed
        }
    }
    return try {
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(recentApp!!, 0)
        pm.getApplicationLabel(appInfo).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    } catch (e: Exception){
        null
    }
}
