package com.machi.memoiz.data.datastore

/**
 * Data class representing user preferences stored in DataStore
 */
data class UserPreferences(
    val customCategories: Set<String> = emptySet(),
    val categoryOrder: List<String> = emptyList(),
    val hasSeenTutorial: Boolean = false,
    val showTutorialOnNextLaunch: Boolean = false,
    val forceOffImageDescription: Boolean = false,
    val forceOffTextGeneration: Boolean = false,
    val forceOffSummarization: Boolean = false
)
