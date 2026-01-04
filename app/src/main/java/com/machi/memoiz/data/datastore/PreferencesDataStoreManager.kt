package com.machi.memoiz.data.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*

/**
 * Manager for persisting user preferences using DataStore
 */
class PreferencesDataStoreManager(private val context: Context) {

    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")
        private val CUSTOM_CATEGORIES_KEY = stringSetPreferencesKey("custom_categories")
        private val CATEGORY_ORDER_KEY = stringPreferencesKey("category_order")
        // NOTE: tutorial flags are now stored in SharedPreferences for immediate consistency
        private const val SP_FILE_NAME = "memoiz_shared_prefs"
        private const val SP_KEY_HAS_SEEN_TUTORIAL = "has_seen_tutorial"
        private const val SP_KEY_SHOW_TUTORIAL_ON_NEXT_LAUNCH = "show_tutorial_on_next_launch"
    }

    // SharedPreferences + DataStore combined flow: keep DataStore for heavier prefs and
    // SharedPreferences for immediate tutorial flags.
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(SP_FILE_NAME, Context.MODE_PRIVATE)
    private val _sharedPrefFlow = MutableStateFlow(
        Pair(
            sharedPrefs.getBoolean(SP_KEY_HAS_SEEN_TUTORIAL, false),
            sharedPrefs.getBoolean(SP_KEY_SHOW_TUTORIAL_ON_NEXT_LAUNCH, false)
        )
    )

    private val spListener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
        if (key == SP_KEY_HAS_SEEN_TUTORIAL || key == SP_KEY_SHOW_TUTORIAL_ON_NEXT_LAUNCH) {
            _sharedPrefFlow.value = Pair(
                sp.getBoolean(SP_KEY_HAS_SEEN_TUTORIAL, false),
                sp.getBoolean(SP_KEY_SHOW_TUTORIAL_ON_NEXT_LAUNCH, false)
            )
        }
    }

    init {
        sharedPrefs.registerOnSharedPreferenceChangeListener(spListener)
    }

    val userPreferencesFlow: Flow<UserPreferences> = combine(
        context.dataStore.data,
        _sharedPrefFlow
    ) { preferences, spPair ->
        UserPreferences(
            customCategories = preferences[CUSTOM_CATEGORIES_KEY] ?: emptySet(),
            categoryOrder = preferences[CATEGORY_ORDER_KEY]?.split(',')?.filter { it.isNotBlank() } ?: emptyList(),
            hasSeenTutorial = spPair.first,
            showTutorialOnNextLaunch = spPair.second
        )
    }.distinctUntilChanged()

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
        android.util.Log.d("PreferencesDataStore", "markTutorialSeen() called — writing has_seen_tutorial = true to SharedPreferences")
        sharedPrefs.edit().putBoolean(SP_KEY_HAS_SEEN_TUTORIAL, true).putBoolean(SP_KEY_SHOW_TUTORIAL_ON_NEXT_LAUNCH, false).apply()
        // update the flow immediately (SharedPreferences listener will also update it)
        _sharedPrefFlow.value = Pair(true, false)
        android.util.Log.d("PreferencesDataStore", "markTutorialSeen() completed — write finished")
    }

    suspend fun requestTutorial() {
        android.util.Log.d("PreferencesDataStore", "requestTutorial() called — writing show_tutorial_on_next_launch = true to SharedPreferences")
        sharedPrefs.edit().putBoolean(SP_KEY_SHOW_TUTORIAL_ON_NEXT_LAUNCH, true).apply()
        _sharedPrefFlow.value = Pair(_sharedPrefFlow.value.first, true)
        android.util.Log.d("PreferencesDataStore", "requestTutorial() completed — write finished")
    }
}
