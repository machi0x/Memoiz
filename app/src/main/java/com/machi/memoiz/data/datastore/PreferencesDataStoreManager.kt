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
        private val UI_DISPLAY_MODE_KEY = stringPreferencesKey("ui_display_mode")
        // New: analytics collection preference stored in DataStore
        private val ANALYTICS_COLLECTION_KEY = stringPreferencesKey("analytics_collection_enabled")
        // GenAI last-check status keys
        private val GENAI_IMAGE_LAST_KEY = stringPreferencesKey("genai_last_image_status")
        private val GENAI_TEXT_LAST_KEY = stringPreferencesKey("genai_last_text_status")
        private val GENAI_SUMMARY_LAST_KEY = stringPreferencesKey("genai_last_summary_status")
        // NOTE: tutorial flags are now stored in SharedPreferences for immediate consistency
        private const val SP_FILE_NAME = "memoiz_shared_prefs"
        private const val SP_KEY_HAS_SEEN_TUTORIAL = "has_seen_tutorial"
        private const val SP_KEY_SHOW_TUTORIAL_ON_NEXT_LAUNCH = "show_tutorial_on_next_launch"
        // New: immediate-sync key for analytics collection (mirror of DataStore for sync reads)
        private const val SP_KEY_ANALYTICS_COLLECTION = "analytics_collection_sp"
        private const val SP_KEY_CONSENT_DIALOG_SHOWN = "consent_dialog_shown"
    }

    // SharedPreferences + DataStore combined flow: keep DataStore for heavier prefs and
    // SharedPreferences for immediate tutorial flags.
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(SP_FILE_NAME, Context.MODE_PRIVATE)
    // Use a Triple to include the new analyticsCollectionEnabled immediate value
    private val _sharedPrefFlow = MutableStateFlow(
        Triple(
            sharedPrefs.getBoolean(SP_KEY_HAS_SEEN_TUTORIAL, false),
            sharedPrefs.getBoolean(SP_KEY_SHOW_TUTORIAL_ON_NEXT_LAUNCH, false),
            sharedPrefs.getBoolean(SP_KEY_ANALYTICS_COLLECTION, false)
        )
    )

    private val spListener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
        if (key == SP_KEY_HAS_SEEN_TUTORIAL || key == SP_KEY_SHOW_TUTORIAL_ON_NEXT_LAUNCH || key == SP_KEY_ANALYTICS_COLLECTION) {
            _sharedPrefFlow.value = Triple(
                sp.getBoolean(SP_KEY_HAS_SEEN_TUTORIAL, false),
                sp.getBoolean(SP_KEY_SHOW_TUTORIAL_ON_NEXT_LAUNCH, false),
                sp.getBoolean(SP_KEY_ANALYTICS_COLLECTION, false)
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
            showTutorialOnNextLaunch = spPair.second,
            analyticsCollectionEnabled = preferences[ANALYTICS_COLLECTION_KEY]?.toBoolean() ?: spPair.third,
            uiDisplayMode = UiDisplayMode.fromString(preferences[UI_DISPLAY_MODE_KEY])
        )
    }.distinctUntilChanged()

    // Convenience flows for GenAI last-check status (nullable string: "unknown" or one of available/downloadable/unavailable)
    @Suppress("unused")
    val genAiImageLastCheckFlow: Flow<String?> = context.dataStore.data.map { it[GENAI_IMAGE_LAST_KEY] }
    @Suppress("unused")
    val genAiTextLastCheckFlow: Flow<String?> = context.dataStore.data.map { it[GENAI_TEXT_LAST_KEY] }
    @Suppress("unused")
    val genAiSummaryLastCheckFlow: Flow<String?> = context.dataStore.data.map { it[GENAI_SUMMARY_LAST_KEY] }

    @Suppress("unused")
    suspend fun setGenAiImageLastCheck(status: String) {
        context.dataStore.edit { preferences ->
            preferences[GENAI_IMAGE_LAST_KEY] = status
        }
    }

    @Suppress("unused")
    suspend fun setGenAiTextLastCheck(status: String) {
        context.dataStore.edit { preferences ->
            preferences[GENAI_TEXT_LAST_KEY] = status
        }
    }

    @Suppress("unused")
    suspend fun setGenAiSummaryLastCheck(status: String) {
        context.dataStore.edit { preferences ->
            preferences[GENAI_SUMMARY_LAST_KEY] = status
        }
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

    @Suppress("unused")
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

    fun markTutorialSeen() {
        android.util.Log.d("PreferencesDataStore", "markTutorialSeen() called — writing has_seen_tutorial = true to SharedPreferences")
        sharedPrefs.edit().putBoolean(SP_KEY_HAS_SEEN_TUTORIAL, true).putBoolean(SP_KEY_SHOW_TUTORIAL_ON_NEXT_LAUNCH, false).apply()
        // update the flow immediately (SharedPreferences listener will also update it)
        _sharedPrefFlow.value = Triple(true, false, _sharedPrefFlow.value.third)
        android.util.Log.d("PreferencesDataStore", "markTutorialSeen() completed — write finished")
    }

    fun requestTutorial() {
        android.util.Log.d("PreferencesDataStore", "requestTutorial() called — writing show_tutorial_on_next_launch = true to SharedPreferences")
        sharedPrefs.edit().putBoolean(SP_KEY_SHOW_TUTORIAL_ON_NEXT_LAUNCH, true).apply()
        _sharedPrefFlow.value = Triple(_sharedPrefFlow.value.first, true, _sharedPrefFlow.value.third)
        android.util.Log.d("PreferencesDataStore", "requestTutorial() completed — write finished")
    }

    suspend fun setUiDisplayMode(mode: UiDisplayMode) {
        context.dataStore.edit { preferences ->
            preferences[UI_DISPLAY_MODE_KEY] = mode.name
        }
    }

    /** Returns the current immediate analyticsCollectionEnabled value from SharedPreferences (sync read) */
    fun isAnalyticsCollectionEnabledSync(): Boolean {
        return sharedPrefs.getBoolean(SP_KEY_ANALYTICS_COLLECTION, false)
    }

    /** Synchronous getter for whether the pre-tutorial consent dialog has been shown/answered. */
    fun isConsentDialogShownSync(): Boolean {
        return sharedPrefs.getBoolean(SP_KEY_CONSENT_DIALOG_SHOWN, false)
    }

    /** Synchronous setter to mark the consent dialog as shown (mirror to SharedPreferences). */
    fun setConsentDialogShownSync(shown: Boolean) {
        sharedPrefs.edit().putBoolean(SP_KEY_CONSENT_DIALOG_SHOWN, shown).apply()
        // No flow update needed; this is only used as a guard to avoid re-showing the dialog.
    }

    /** Synchronous getter for whether tutorial was requested from settings (show_on_next_launch). */
    fun isShowTutorialOnNextLaunchSync(): Boolean {
        return sharedPrefs.getBoolean(SP_KEY_SHOW_TUTORIAL_ON_NEXT_LAUNCH, false)
    }

    /** Persist the analyticsCollectionEnabled boolean into DataStore and mirror to SharedPreferences */
    suspend fun setAnalyticsCollectionEnabled(enabled: Boolean) {
        // Write to DataStore
        context.dataStore.edit { preferences ->
            preferences[ANALYTICS_COLLECTION_KEY] = enabled.toString()
        }
        // Mirror to SharedPreferences for immediate sync reads
        sharedPrefs.edit().putBoolean(SP_KEY_ANALYTICS_COLLECTION, enabled).apply()
        // Update flow immediately
        _sharedPrefFlow.value = Triple(_sharedPrefFlow.value.first, _sharedPrefFlow.value.second, enabled)
    }

    /** Synchronous convenience method to set SharedPreferences immediately (no DataStore write)
     *  Use this for instant UI reaction; callers should also call suspend setAnalyticsCollectionEnabled to persist.
     */
    fun setAnalyticsCollectionEnabledSync(enabled: Boolean) {
        sharedPrefs.edit().putBoolean(SP_KEY_ANALYTICS_COLLECTION, enabled).apply()
        _sharedPrefFlow.value = Triple(_sharedPrefFlow.value.first, _sharedPrefFlow.value.second, enabled)
    }
}
