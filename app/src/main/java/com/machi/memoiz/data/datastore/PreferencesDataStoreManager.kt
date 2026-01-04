package com.machi.memoiz.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Manager for persisting user preferences using DataStore
 */
class PreferencesDataStoreManager(private val context: Context) {

    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")
        private val CUSTOM_CATEGORIES_KEY = stringSetPreferencesKey("custom_categories")
        private val CATEGORY_ORDER_KEY = stringPreferencesKey("category_order")
        private val HAS_SEEN_TUTORIAL_KEY = booleanPreferencesKey("has_seen_tutorial")
        private val SHOW_TUTORIAL_ON_NEXT_LAUNCH_KEY = booleanPreferencesKey("show_tutorial_on_next_launch")
        private val FORCE_OFF_IMAGE_DESCRIPTION_KEY = booleanPreferencesKey("force_off_image_description")
        private val FORCE_OFF_TEXT_GENERATION_KEY = booleanPreferencesKey("force_off_text_generation")
        private val FORCE_OFF_SUMMARIZATION_KEY = booleanPreferencesKey("force_off_summarization")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { preferences ->
        UserPreferences(
            customCategories = preferences[CUSTOM_CATEGORIES_KEY] ?: emptySet(),
            categoryOrder = preferences[CATEGORY_ORDER_KEY]?.split(',')?.filter { it.isNotBlank() } ?: emptyList(),
            hasSeenTutorial = preferences[HAS_SEEN_TUTORIAL_KEY] ?: false,
            showTutorialOnNextLaunch = preferences[SHOW_TUTORIAL_ON_NEXT_LAUNCH_KEY] ?: false,
            forceOffImageDescription = preferences[FORCE_OFF_IMAGE_DESCRIPTION_KEY] ?: false,
            forceOffTextGeneration = preferences[FORCE_OFF_TEXT_GENERATION_KEY] ?: false,
            forceOffSummarization = preferences[FORCE_OFF_SUMMARIZATION_KEY] ?: false
        )
    }

    suspend fun addCustomCategory(categoryName: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[CUSTOM_CATEGORIES_KEY] ?: emptySet()
            preferences[CUSTOM_CATEGORIES_KEY] = current + categoryName
        }
    }

    suspend fun removeCustomCategory(categoryName: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[CUSTOM_CATEGORIES_KEY] ?: emptySet()
            preferences[CUSTOM_CATEGORIES_KEY] = current - categoryName
        }
    }

    suspend fun clearCustomCategories() {
        context.dataStore.edit { preferences ->
            preferences.remove(CUSTOM_CATEGORIES_KEY)
        }
    }

    suspend fun updateCategoryOrder(newOrder: List<String>) {
        context.dataStore.edit { preferences ->
            preferences[CATEGORY_ORDER_KEY] = newOrder.joinToString(",")
        }
    }

    suspend fun removeCategoryFromOrder(categoryName: String) {
        context.dataStore.edit { preferences ->
            val currentOrder = preferences[CATEGORY_ORDER_KEY]?.split(',')?.toMutableList() ?: mutableListOf()
            if (currentOrder.remove(categoryName)) {
                preferences[CATEGORY_ORDER_KEY] = currentOrder.joinToString(",")
            }
        }
    }

    suspend fun markTutorialSeen() {
        context.dataStore.edit { preferences ->
            preferences[HAS_SEEN_TUTORIAL_KEY] = true
            preferences[SHOW_TUTORIAL_ON_NEXT_LAUNCH_KEY] = false
        }
    }

    suspend fun requestTutorial() {
        context.dataStore.edit { preferences ->
            preferences[SHOW_TUTORIAL_ON_NEXT_LAUNCH_KEY] = true
        }
    }

    suspend fun setForceOffImageDescription(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[FORCE_OFF_IMAGE_DESCRIPTION_KEY] = enabled
        }
    }

    suspend fun setForceOffTextGeneration(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[FORCE_OFF_TEXT_GENERATION_KEY] = enabled
        }
    }

    suspend fun setForceOffSummarization(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[FORCE_OFF_SUMMARIZATION_KEY] = enabled
        }
    }
}
