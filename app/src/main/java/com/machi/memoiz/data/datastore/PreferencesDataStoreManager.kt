package com.machi.memoiz.data.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*

/**
 * Manager for persisting user preferences using DataStore
 */
class PreferencesDataStoreManager(private val context: Context) {
    // Internal scope for creating hot StateFlows owned by this manager.
    // This manager is intended to live for the application lifetime, so we keep
    // a long-lived scope here. If you ever need to release resources, call close().
    private val internalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")
        private val CUSTOM_CATEGORIES_KEY = stringSetPreferencesKey("custom_categories")
        private val CATEGORY_ORDER_KEY = stringPreferencesKey("category_order")
        private val UI_DISPLAY_MODE_KEY = stringPreferencesKey("ui_display_mode")
        // New: analytics collection preference stored in DataStore
        private val ANALYTICS_COLLECTION_KEY = stringPreferencesKey("analytics_collection_enabled")
        // Timestamp when the MainScreen was last marked seen (epoch ms)
        private val LAST_MAIN_SCREEN_SEEN_AT_KEY = stringPreferencesKey("last_main_screen_seen_at")
        // GenAI last-check status keys
        private val GENAI_IMAGE_LAST_KEY = stringPreferencesKey("genai_last_image_status")
        private val GENAI_TEXT_LAST_KEY = stringPreferencesKey("genai_last_text_status")
        private val GENAI_SUMMARY_LAST_KEY = stringPreferencesKey("genai_last_summary_status")
        // Memoiz Status keys
        private val MEMOIZ_EXP_KEY = stringPreferencesKey("memoiz_exp")
        // DataStore keys (keep original key strings for compatibility)
        private val MEMOIZ_YASASHISA_KEY = stringPreferencesKey("memoiz_yasashisa")
        private val MEMOIZ_KAKKOYOSA_KEY = stringPreferencesKey("memoiz_kakkoyosa")
        private val MEMOIZ_KASHIKOSA_KEY = stringPreferencesKey("memoiz_kashikosa")
        private val MEMOIZ_KOUKISHIN_KEY = stringPreferencesKey("memoiz_koukishin")
        private val MEMOIZ_USED_MEMO_IDS_KEY = stringPreferencesKey("memoiz_used_memo_ids")
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
            uiDisplayMode = UiDisplayMode.fromString(preferences[UI_DISPLAY_MODE_KEY]),
            lastMainScreenSeenAt = preferences[LAST_MAIN_SCREEN_SEEN_AT_KEY]?.toLongOrNull() ?: 0L
        )
    }.distinctUntilChanged()

    // Simple hot StateFlow exposing only the lastMainScreenSeenAt timestamp.
    // Consumers (e.g. ViewModel) can directly subscribe to this instead of mapping userPreferencesFlow.
    val lastMainScreenSeenAtState: StateFlow<Long> = context.dataStore.data
        .map { prefs -> prefs[LAST_MAIN_SCREEN_SEEN_AT_KEY]?.toLongOrNull() ?: 0L }
        .stateIn(internalScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /**
     * Flow exposing the current Memoiz status (EXP and parameters).
     */
    fun memoizStatusFlow(): Flow<MemoizStatus> = context.dataStore.data.map { preferences ->
        val exp = preferences[MEMOIZ_EXP_KEY]?.toIntOrNull() ?: 0
        val kindness = preferences[MEMOIZ_YASASHISA_KEY]?.toIntOrNull() ?: 0
        val coolness = preferences[MEMOIZ_KAKKOYOSA_KEY]?.toIntOrNull() ?: 0
        val smartness = preferences[MEMOIZ_KASHIKOSA_KEY]?.toIntOrNull() ?: 0
        val curiosity = preferences[MEMOIZ_KOUKISHIN_KEY]?.toIntOrNull() ?: 0
        val ids = preferences[MEMOIZ_USED_MEMO_IDS_KEY]?.split(',')?.filter { it.isNotBlank() }?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
        MemoizStatus(exp = exp, kindness = kindness, coolness = coolness, smartness = smartness, curiosity = curiosity, usedMemoIds = ids)
    }

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

    /**
     * Record that the user executed "メモイズから一言" for the given memoId and apply parameter changes
     * according to the provided feeling label. If the memoId was not previously counted, increment EXP.
     */
    suspend fun recordCatCommentUsage(memoId: Long, feelingLabel: String?) {
        context.dataStore.edit { preferences ->
            // load existing
            val idsStr = preferences[MEMOIZ_USED_MEMO_IDS_KEY] ?: ""
            val ids = idsStr.split(',').filter { it.isNotBlank() }.mapNotNull { it.toLongOrNull() }.toMutableSet()
            var exp = preferences[MEMOIZ_EXP_KEY]?.toIntOrNull() ?: 0

            // Determine if this memoId is new (first execution)
            val isNew = !ids.contains(memoId)
            if (isNew) {
                ids.add(memoId)
                exp += 1
                preferences[MEMOIZ_EXP_KEY] = exp.toString()
                preferences[MEMOIZ_USED_MEMO_IDS_KEY] = ids.joinToString(",")

                // Apply feeling-based parameter change only on first-time usage
                feelingLabel?.let { label ->
                    val logic = mapOf(
                        "happy" to ("kindness" to "coolness"),
                        "cool" to ("coolness" to "kindness"),
                        "thoughtful" to ("smartness" to "curiosity"),
                        "confused" to ("curiosity" to "smartness"),
                        "difficult" to ("smartness" to "kindness"),
                        "curious" to ("curiosity" to "coolness"),
                        "scared" to ("kindness" to "curiosity"),
                        "neutral" to ("kindness" to "smartness")
                    )

                    val pair = logic[label]
                    if (pair != null) {
                        val plus = pair.first
                        val minus = pair.second

                        val plusVal = when (plus) {
                            "kindness" -> (preferences[MEMOIZ_YASASHISA_KEY]?.toIntOrNull() ?: 0) + 1
                            "coolness" -> (preferences[MEMOIZ_KAKKOYOSA_KEY]?.toIntOrNull() ?: 0) + 1
                            "smartness" -> (preferences[MEMOIZ_KASHIKOSA_KEY]?.toIntOrNull() ?: 0) + 1
                            "curiosity" -> (preferences[MEMOIZ_KOUKISHIN_KEY]?.toIntOrNull() ?: 0) + 1
                            else -> 0
                        }

                        val minusVal = when (minus) {
                            "kindness" -> ((preferences[MEMOIZ_YASASHISA_KEY]?.toIntOrNull() ?: 0) - 1).coerceAtLeast(0)
                            "coolness" -> ((preferences[MEMOIZ_KAKKOYOSA_KEY]?.toIntOrNull() ?: 0) - 1).coerceAtLeast(0)
                            "smartness" -> ((preferences[MEMOIZ_KASHIKOSA_KEY]?.toIntOrNull() ?: 0) - 1).coerceAtLeast(0)
                            "curiosity" -> ((preferences[MEMOIZ_KOUKISHIN_KEY]?.toIntOrNull() ?: 0) - 1).coerceAtLeast(0)
                            else -> 0
                        }

                        when (plus) {
                            "kindness" -> preferences[MEMOIZ_YASASHISA_KEY] = plusVal.toString()
                            "coolness" -> preferences[MEMOIZ_KAKKOYOSA_KEY] = plusVal.toString()
                            "smartness" -> preferences[MEMOIZ_KASHIKOSA_KEY] = plusVal.toString()
                            "curiosity" -> preferences[MEMOIZ_KOUKISHIN_KEY] = plusVal.toString()
                        }

                        when (minus) {
                            "kindness" -> preferences[MEMOIZ_YASASHISA_KEY] = minusVal.toString()
                            "coolness" -> preferences[MEMOIZ_KAKKOYOSA_KEY] = minusVal.toString()
                            "smartness" -> preferences[MEMOIZ_KASHIKOSA_KEY] = minusVal.toString()
                            "curiosity" -> preferences[MEMOIZ_KOUKISHIN_KEY] = minusVal.toString()
                        }
                     }
                 }
             }
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

    /** Persist the time (epoch ms) when the MainScreen was last seen/left. */
    suspend fun markMainScreenSeen(atMillis: Long = System.currentTimeMillis()) {
        context.dataStore.edit { preferences ->
            preferences[LAST_MAIN_SCREEN_SEEN_AT_KEY] = atMillis.toString()
        }
    }

    /** Optional: cancel internal resources when the manager is no longer needed. */
    fun close() {
        internalScope.cancel()
    }
}
