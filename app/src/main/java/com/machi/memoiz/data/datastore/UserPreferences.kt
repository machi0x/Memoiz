package com.machi.memoiz.data.datastore

import com.machi.memoiz.data.datastore.UiDisplayMode

/**
 * Data class representing user preferences stored in DataStore
 */
data class UserPreferences(
    val customCategories: Set<String> = emptySet(),
    val categoryOrder: List<String> = emptyList(),
    val hasSeenTutorial: Boolean = false,
    val showTutorialOnNextLaunch: Boolean = false,
    val uiDisplayMode: UiDisplayMode = UiDisplayMode.SYSTEM,
    // New: whether the user consents to sending non-private usage statistics (Firebase)
    val sendUsageStats: Boolean = false
)
