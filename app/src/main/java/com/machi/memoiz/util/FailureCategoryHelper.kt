package com.machi.memoiz.util

import android.content.Context
import com.machi.memoiz.R

/**
 * Centralizes lookups for the special "failure" category so we can
 * localize the display label while still recognizing legacy values.
 */
object FailureCategoryHelper {
    private const val LEGACY_EN = "FAILURE"
    private const val LEGACY_JA = "分類失敗"

    fun currentLabel(context: Context): String = context.getString(R.string.failure_category)

    fun aliases(context: Context): List<String> {
        val canonical = currentLabel(context)
        return listOf(canonical, LEGACY_EN, LEGACY_JA).distinct()
    }

    fun isFailureLabel(context: Context, value: String?): Boolean {
        value ?: return false
        return aliases(context).any { it.equals(value, ignoreCase = true) }
    }
}
