package com.machi.memoiz.ui.screens

import android.content.Context
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.machi.memoiz.data.datastore.PreferencesDataStoreManager
import com.machi.memoiz.data.repository.MemoRepository
import com.machi.memoiz.domain.model.Memo
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.work.WorkManager
import androidx.work.WorkInfo
import com.machi.memoiz.R
import com.machi.memoiz.worker.WORK_TAG_MEMO_PROCESSING
import com.machi.memoiz.data.datastore.UserPreferences
import com.machi.memoiz.data.datastore.UiDisplayMode
import com.machi.memoiz.service.ContentProcessingLauncher

data class MemoGroup(val category: String, val memos: List<Memo>)

enum class SortMode {
    CREATED_DESC,  // Category groups ordered by latest memo timestamp
    CATEGORY_NAME, // Category groups ordered alphabetically
    MOST_USED      // Category groups ordered by highest usageCount in the group
}

private data class MemoFilterState(
    val memos: List<Memo>,
    val query: String,
    val sortMode: SortMode,
    val categoryFilter: String?,
    val typeFilter: String?
)

class MainViewModel(
    private val memoRepository: MemoRepository,
    private val preferencesManager: PreferencesDataStoreManager,
    private val workManager: WorkManager
) : ViewModel() {
    private val processingLiveData = workManager.getWorkInfosByTagLiveData(WORK_TAG_MEMO_PROCESSING)
    private val processingObserver = Observer<List<WorkInfo>> { infos ->
        val hasRunning = infos?.any { it.state == WorkInfo.State.RUNNING } == true
        _isProcessing.value = hasRunning
    }

    // Search and filter state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.CREATED_DESC)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    private val _categoryFilter = MutableStateFlow<String?>(null)
    val categoryFilter: StateFlow<String?> = _categoryFilter.asStateFlow()

    private val _memoTypeFilter = MutableStateFlow<String?>(null)
    val memoTypeFilter: StateFlow<String?> = _memoTypeFilter.asStateFlow()

    // Expand/collapse state for accordion
    private val _expandedCategories = MutableStateFlow<Set<String>>(emptySet())
    val expandedCategories: StateFlow<Set<String>> = _expandedCategories.asStateFlow()

    private val userPreferencesFlow = preferencesManager.userPreferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    // Expose the UiDisplayMode so composables can adapt resource resolution to the
    // app's user-selected display mode (LIGHT/DARK/SYSTEM).
    val uiDisplayMode = userPreferencesFlow
        .map { it.uiDisplayMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiDisplayMode.SYSTEM)

    // Expose whether the user has consented to sending usage stats
    val sendUsageStats: StateFlow<Boolean> = userPreferencesFlow
        .map { it.sendUsageStats }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Indicates whether we've observed the first real preferences emission from DataStore.
    private val _preferencesLoaded = MutableStateFlow(false)
    val preferencesLoaded: StateFlow<Boolean> = _preferencesLoaded.asStateFlow()

    // Local in-memory immediate flag to reflect tutorial seen state without waiting for
    // async persistence. This prevents a brief window where UI still thinks the user
    // hasn't seen the tutorial while the SharedPreferences write completes.
    private val _localHasSeenTutorial = MutableStateFlow(false)
    val localHasSeenTutorial: StateFlow<Boolean> = _localHasSeenTutorial.asStateFlow()

    private val _categoryOrder = MutableStateFlow<List<String>>(emptyList())
    val categoryOrder: StateFlow<List<String>> = _categoryOrder.asStateFlow()

    // Combine persisted prefs with local immediate flag. Only show tutorial when
    // the persisted prefs indicate it should be shown AND the local flag hasn't
    // already marked it as seen.
    val shouldShowTutorial: StateFlow<Boolean> = combine(
        userPreferencesFlow,
        _localHasSeenTutorial
    ) { prefs, localSeen ->
        // Show if user hasn't seen it (persisted) AND we haven't locally marked it seen,
        // or when explicit show-on-next-launch is set.
        (!prefs.hasSeenTutorial && !localSeen) || prefs.showTutorialOnNextLaunch
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        viewModelScope.launch {
            userPreferencesFlow.collect { prefs ->
                _categoryOrder.value = prefs.categoryOrder
            }
        }

        // Kick off a background collection to detect when DataStore has emitted at least once.
        viewModelScope.launch {
            try {
                // This will suspend until the first real emission from preferencesManager.userPreferencesFlow
                preferencesManager.userPreferencesFlow.first()
                _preferencesLoaded.value = true
            } catch (_: Exception) {
                // ignore - keep false if we couldn't load
            }
        }

        processingLiveData.observeForever(processingObserver)
    }

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    override fun onCleared() {
        processingLiveData.removeObserver(processingObserver)
        super.onCleared()
    }

    // Custom categories from DataStore
    val customCategories: StateFlow<Set<String>> = userPreferencesFlow
        .map { it.customCategories }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // All available categories (from memos + custom)
    val availableCategories: StateFlow<List<String>> = combine(
        memoRepository.getAllMemos(),
        customCategories
    ) { memos, custom ->
        (memos.map { it.category }.toSet() + custom).sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val memoFilterFlow = combine(
        memoRepository.getAllMemos(),
        _searchQuery,
        _sortMode,
        _categoryFilter,
        _memoTypeFilter
    ) { memos, query, mode, filter, typeFilter ->
        MemoFilterState(memos, query, mode, filter, typeFilter)
    }

    // Filtered and sorted memo groups
    val memoGroups: StateFlow<List<MemoGroup>> = combine(
        memoFilterFlow,
        categoryOrder
    ) { state, order ->
        var filtered = state.memos

        // Filter by memo type
        filtered = if (state.typeFilter != null) {
            filtered.filter { it.memoType == state.typeFilter }
        } else {
            filtered
        }

        // Filter by category
        filtered = if (state.categoryFilter != null) {
            filtered.filter { it.category == state.categoryFilter }
        } else {
            filtered
        }

        // Filter by search query
        if (state.query.isNotBlank()) {
            val lowerQuery = state.query.lowercase()
            filtered = filtered.filter { memo ->
                memo.content.lowercase().contains(lowerQuery) ||
                        memo.summary?.lowercase()?.contains(lowerQuery) == true ||
                        memo.subCategory?.lowercase()?.contains(lowerQuery) == true ||
                        memo.category.lowercase().contains(lowerQuery)
            }
        }

        // Group by category
        val grouped = filtered.groupBy { it.category }
            .map { (category, memos) ->
                // Default inner order by createdAt; final ordering may change based on SortMode
                MemoGroup(category, memos.sortedByDescending { it.createdAt })
            }

        val manualOrderActive = order.isNotEmpty()
        val orderedGroups = if (manualOrderActive) {
            val orderMap = order.mapIndexed { index, value -> value to index }.toMap()
            grouped.sortedWith(compareBy({ orderMap[it.category] ?: Int.MAX_VALUE }, { it.category }))
        } else {
            grouped
        }

        if (manualOrderActive) {
            orderedGroups
        } else {
            when (state.sortMode) {
                SortMode.CREATED_DESC -> orderedGroups
                    .map { g -> g.copy(memos = g.memos.sortedByDescending { it.createdAt }) }
                    .sortedByDescending { group -> group.memos.maxOfOrNull { it.createdAt } ?: 0L }

                SortMode.CATEGORY_NAME -> orderedGroups
                    .map { g -> g.copy(memos = g.memos.sortedByDescending { it.createdAt }) }
                    .sortedBy { it.category }

                SortMode.MOST_USED -> orderedGroups
                    .map { g ->
                        // sort memos within group by usageCount desc, fallback to createdAt
                        g.copy(memos = g.memos.sortedWith(compareByDescending<Memo> { it.usageCount }.thenByDescending { it.createdAt }))
                    }
                    .sortedByDescending { group ->
                        group.memos.maxOfOrNull { it.usageCount } ?: 0
                    }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    fun clearToast() {
        _toastMessage.value = null
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
        if (_categoryOrder.value.isNotEmpty()) {
            clearCategoryOrder()
        }
    }

    fun setCategoryFilter(category: String?) {
        _categoryFilter.value = category
    }

    fun setMemoTypeFilter(memoType: String?) {
        _memoTypeFilter.value = memoType
    }

    fun toggleCategoryExpanded(category: String) {
        _expandedCategories.update { current ->
            if (category in current) current - category else current + category
        }
    }

    fun addCustomCategory(categoryName: String) {
        viewModelScope.launch {
            preferencesManager.addCustomCategory(categoryName.trim())
        }
    }

    fun removeCustomCategory(categoryName: String) {
        viewModelScope.launch {
            preferencesManager.removeCustomCategory(categoryName)
        }
    }

    fun deleteMemo(memo: Memo) {
        viewModelScope.launch { memoRepository.deleteMemo(memo) }
    }

    fun deleteCategory(categoryName: String) {
        viewModelScope.launch { memoRepository.deleteMemosByCategoryName(categoryName) }
    }

    fun reanalyzeMemo(context: Context, memoId: Long) {
        ContentProcessingLauncher.enqueueSingleMemoReanalyze(context, memoId)
    }

    fun reanalyzeFailureBatch(context: Context) {
        ContentProcessingLauncher.enqueueFailureBatchReanalyze(context)
    }

    fun addCustomCategoryWithMerge(context: Context, categoryName: String) {
        viewModelScope.launch {
            val trimmed = categoryName.trim()
            preferencesManager.addCustomCategory(trimmed)
            ContentProcessingLauncher.enqueueMergeWork(context, null)
            _toastMessage.value = context.getString(R.string.toast_merge_enqueued)
        }
    }

    fun removeCustomCategoryAndReanalyze(context: Context, categoryName: String) {
        viewModelScope.launch {
            preferencesManager.removeCustomCategory(categoryName)
            ContentProcessingLauncher.enqueueMergeWork(context, categoryName)
            _toastMessage.value = context.getString(R.string.toast_merge_enqueued)
        }
    }

    fun onCategoryMoved(fromIndex: Int, toIndex: Int, displayedCategories: List<String>) {
        if (displayedCategories.isEmpty()) return
        val baseOrder = if (_categoryOrder.value.isEmpty()) {
            displayedCategories
        } else {
            _categoryOrder.value
        }
        val current = baseOrder.toMutableList()
        if (current.size != displayedCategories.size) {
            current.clear()
            current.addAll(displayedCategories)
        }
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        _categoryOrder.value = current
        viewModelScope.launch { preferencesManager.updateCategoryOrder(current) }
    }

    fun ensureCategoryOrder(categories: List<String>) {
        val current = _categoryOrder.value
        if (current.isEmpty()) return
        val merged = current.filter { it in categories }
        val missing = categories.filter { it !in current }
        val newOrder = merged + missing
        if (newOrder != current) {
            _categoryOrder.value = newOrder
            viewModelScope.launch { preferencesManager.updateCategoryOrder(newOrder) }
        }
    }

    fun removeCategoryFromOrder(category: String) {
        viewModelScope.launch { preferencesManager.removeCategoryFromOrder(category) }
    }

    private fun clearCategoryOrder() {
        _categoryOrder.value = emptyList()
        viewModelScope.launch { preferencesManager.updateCategoryOrder(emptyList()) }
    }

    fun markTutorialSeen() {
        // Set local indicator first to prevent UI race, then persist.
        _localHasSeenTutorial.value = true
        viewModelScope.launch { preferencesManager.markTutorialSeen() }
    }

    fun updateMemoCategory(memo: Memo, newCategory: String, newSubCategory: String?) {
        viewModelScope.launch {
            memoRepository.updateMemo(
                memo.copy(
                    category = newCategory,
                    subCategory = newSubCategory,
                    isCategoryLocked = true
                )
            )
        }
    }

    fun addCustomCategoryIfMissing(categoryName: String) {
        val trimmed = categoryName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            preferencesManager.addCustomCategory(trimmed)
        }
    }

    // Record that a memo was used (copy/share/open). This increments usageCount in DB.
    fun recordMemoUsed(memoId: Long) {
        viewModelScope.launch {
            memoRepository.incrementUsage(memoId)
        }
    }

    /** Set user consent for sending usage stats. Updates SharedPreferences immediately and persists to DataStore asynchronously. */
    fun setSendUsageStats(enabled: Boolean) {
        // immediate UI reaction via SharedPreferences mirror
        try {
            preferencesManager.setSendUsageStatsSync(enabled)
        } catch (e: Exception) {
            android.util.Log.w("MainViewModel", "Failed to set sendUsageStats sync", e)
        }
        // persist to DataStore in background
        viewModelScope.launch {
            try {
                preferencesManager.setSendUsageStats(enabled)
            } catch (e: Exception) {
                android.util.Log.w("MainViewModel", "Failed to persist sendUsageStats to DataStore", e)
            }
        }
    }

    /** Synchronous check whether the pre-tutorial consent dialog was already shown. */
    fun isConsentDialogShownSync(): Boolean {
        return try {
            preferencesManager.isConsentDialogShownSync()
        } catch (e: Exception) {
            android.util.Log.w("MainViewModel", "Failed to read consent dialog shown flag sync", e)
            false
        }
    }

    /** Mark the consent dialog as shown synchronously (so it won't be shown again). */
    fun setConsentDialogShownSync(shown: Boolean) {
        try {
            preferencesManager.setConsentDialogShownSync(shown)
        } catch (e: Exception) {
            android.util.Log.w("MainViewModel", "Failed to set consent dialog shown flag sync", e)
        }
    }

    /** Synchronous check whether tutorial was requested via settings (show_on_next_launch). */
    fun isShowTutorialOnNextLaunchSync(): Boolean {
        return try {
            preferencesManager.isShowTutorialOnNextLaunchSync()
        } catch (e: Exception) {
            android.util.Log.w("MainViewModel", "Failed to read showTutorialOnNextLaunchSync", e)
            false
        }
    }
}
