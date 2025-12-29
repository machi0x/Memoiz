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
    val sorted = stats.sortedByDescending { it.lastTimeUsed }
    val memoizPackage = context.packageName
    val excludedPackages = setOf(memoizPackage, INTENT_RESOLVER_PACKAGE)
    val candidate = sorted.firstOrNull { it.packageName !in excludedPackages } ?: sorted.first()
    return resolveAppLabel(context, candidate.packageName)
}

private fun resolveAppLabel(context: Context, packageName: String?): String? {
    packageName ?: return null
    return try {
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(appInfo).toString()
    } catch (_: Exception) {
        null
    }
}

private const val INTENT_RESOLVER_PACKAGE = "com.android.intentresolver"
