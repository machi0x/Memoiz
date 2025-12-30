package com.machi.memoiz.domain.model

import java.util.Locale

/**
 * Central place for category labels that have special meaning in the app.
 */
object CategoryConstants {
    private const val FAILURE_CATEGORY_EN = "FAILURE"
    private const val FAILURE_CATEGORY_JA = "分類失敗"

    fun getFailureLabel(locale: Locale = Locale.getDefault()): String {
        return if (locale.language.equals("ja", ignoreCase = true)) {
            FAILURE_CATEGORY_JA
        } else {
            FAILURE_CATEGORY_EN
        }
    }

    fun matchesFailure(value: String): Boolean {
        return value.equals(FAILURE_CATEGORY_EN, ignoreCase = true) || value == FAILURE_CATEGORY_JA
    }
}
