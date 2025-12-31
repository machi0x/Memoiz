package com.machi.memoiz.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.machi.memoiz.data.datastore.PreferencesDataStoreManager
import com.machi.memoiz.data.repository.MemoRepository
import com.machi.memoiz.domain.model.Memo
import com.machi.memoiz.worker.ReanalyzeFailedMemosWorker
import com.machi.memoiz.worker.ReanalyzeSingleMemoWorker
import com.machi.memoiz.worker.ReanalyzeCategoryMergeWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.machi.memoiz.R
import java.util.concurrent.TimeUnit
import com.machi.memoiz.data.datastore.UserPreferences

data class MemoGroup(val category: String, val memos: List<Memo>)

enum class SortMode {
    CREATED_DESC,  // Category groups ordered by latest memo timestamp
    CATEGORY_NAME  // Category groups ordered alphabetically
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
    private val preferencesManager: PreferencesDataStoreManager
) : ViewModel() {

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

    private val _categoryOrder = MutableStateFlow<List<String>>(emptyList())
    val categoryOrder: StateFlow<List<String>> = _categoryOrder.asStateFlow()

    init {
        viewModelScope.launch {
            userPreferencesFlow.collect { prefs ->
                _categoryOrder.value = prefs.categoryOrder
            }
        }
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
                SortMode.CREATED_DESC -> orderedGroups.sortedByDescending { group ->
                    group.memos.maxOfOrNull { it.createdAt } ?: 0L
                }
                SortMode.CATEGORY_NAME -> orderedGroups.sortedBy { it.category }
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
        val data = Data.Builder().putLong(ReanalyzeSingleMemoWorker.KEY_MEMO_ID, memoId).build()
        val request = OneTimeWorkRequestBuilder<ReanalyzeSingleMemoWorker>()
            .setInputData(data)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueue(request)
    }

    fun reanalyzeFailureBatch(context: Context) {
        val request = OneTimeWorkRequestBuilder<ReanalyzeFailedMemosWorker>().build()
        WorkManager.getInstance(context.applicationContext).enqueue(request)
    }

    fun scheduleDailyFailureReanalyze(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReanalyzeFailedMemosWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(
                "daily_failure_reanalyze",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
    }

    fun addCustomCategoryWithMerge(context: Context, categoryName: String) {
        viewModelScope.launch {
            val trimmed = categoryName.trim()
            preferencesManager.addCustomCategory(trimmed)
            enqueueMergeWork(context, null)
            _toastMessage.value = context.getString(R.string.toast_merge_enqueued)
        }
    }

    fun removeCustomCategoryAndReanalyze(context: Context, categoryName: String) {
        viewModelScope.launch {
            preferencesManager.removeCustomCategory(categoryName)
            enqueueMergeWork(context, categoryName)
            _toastMessage.value = context.getString(R.string.toast_merge_enqueued)
        }
    }

    private fun enqueueMergeWork(context: Context, targetCategory: String?) {
        val data = Data.Builder()
            .putString(ReanalyzeCategoryMergeWorker.KEY_TARGET_CATEGORY, targetCategory)
            .build()
        val request = OneTimeWorkRequestBuilder<ReanalyzeCategoryMergeWorker>()
            .setInputData(data)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueue(request)
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

    fun updateMemoCategory(memo: Memo, newCategory: String, newSubCategory: String?) {
        viewModelScope.launch {
            memoRepository.updateMemo(
                memo.copy(
                    category = newCategory,
                    subCategory = newSubCategory
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
}
