package com.machi.memoiz.util

import com.machi.memoiz.BuildConfig
import com.machi.memoiz.data.datastore.MemoizStatus

/**
 * Helper functions for deriving Memoiz status labels used in UI and analytics.
 */
object MemoizStatusHelper {
    /**
     * Compute the memoiz status label from stored parameters.
     * Returns one of: "neutral", "kindness", "coolness", "smartness", "curiosity",
     * or their "_last" variants (e.g. "coolness_last", "neutral_last").
     *
     * Rules implemented (matches dialog logic):
     *  - Let paramThreshold = 1 for debug builds, otherwise 15.
     *  - If all parameters < paramThreshold AND exp >= 50 -> "neutral_last".
     *  - If any parameter > 30 -> choose the largest parameter and return "<param>_last".
     *  - If any parameter >= paramThreshold AND exp >= 50 -> choose the largest parameter and return "<param>_last".
     *  - Otherwise, if all parameters < paramThreshold -> "neutral".
     *  - Otherwise choose the largest parameter name (priority: kindness > coolness > smartness > curiosity).
     */
    fun computeStatusLabel(status: MemoizStatus): String {
        val paramThreshold = if (BuildConfig.DEBUG) 1 else 15
        // Debug overrides: use smaller thresholds when debugging to allow easier testing
        val highLastThreshold = if (BuildConfig.DEBUG) 2 else 30
        val expLastThreshold = if (BuildConfig.DEBUG) 5 else 50

        val vals = listOf(status.kindness, status.coolness, status.smartness, status.curiosity)
        val map = listOf("kindness" to status.kindness, "coolness" to status.coolness, "smartness" to status.smartness, "curiosity" to status.curiosity)
        val maxVal = map.maxByOrNull { it.second }?.second ?: 0
        val maxKey = map.first { it.second == maxVal }.first

        // Rule 3: none of parameter >= paramThreshold but EXP >= expLastThreshold -> neutral_last
        if (vals.all { it < paramThreshold } && status.exp >= expLastThreshold) {
            return "neutral_last"
        }

        // Determine whether we should use _last for the max param
        val useLastForMax = when {
            // Rule 1: any parameter > highLastThreshold -> _last for the largest param
            vals.any { it > highLastThreshold } -> true
            // Rule 2: any parameter >= paramThreshold AND EXP >= expLastThreshold -> _last for the largest param
            vals.any { it >= paramThreshold } && status.exp >= expLastThreshold -> true
            else -> false
        }

        return if (useLastForMax) {
            "${maxKey}_last"
        } else {
            if (vals.all { it < paramThreshold }) {
                "neutral"
            } else {
                maxKey
            }
        }
    }
}
