package com.machi.memoiz.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.machi.memoiz.data.datastore.PreferencesDataStoreManager
import com.machi.memoiz.data.repository.MemoRepository
import com.machi.memoiz.domain.model.Memo
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MemoGroup(val category: String, val memos: List<Memo>)

enum class SortMode {
    CREATED_DESC,  // Category groups ordered by latest memo timestamp
    CATEGORY_NAME  // Category groups ordered alphabetically
}

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

    // Expand/collapse state for accordion
    private val _expandedCategories = MutableStateFlow<Set<String>>(emptySet())
    val expandedCategories: StateFlow<Set<String>> = _expandedCategories.asStateFlow()

    // Custom categories from DataStore
    val customCategories: StateFlow<Set<String>> = preferencesManager.userPreferencesFlow
        .map { it.customCategories }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // All available categories (from memos + custom)
    val availableCategories: StateFlow<List<String>> = combine(
        memoRepository.getAllMemos(),
        customCategories
    ) { memos, custom ->
        (memos.map { it.category }.toSet() + custom).sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered and sorted memo groups
    val memoGroups: StateFlow<List<MemoGroup>> = combine(
        memoRepository.getAllMemos(),
        _searchQuery,
        _sortMode,
        _categoryFilter
    ) { memos, query, mode, filter ->
        // Filter by category
        var filtered = if (filter != null) {
            memos.filter { it.category == filter }
        } else {
            memos
        }

        // Filter by search query
        if (query.isNotBlank()) {
            val lowerQuery = query.lowercase()
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
                // Sort memos within category by created date (newest first)
                MemoGroup(category, memos.sortedByDescending { it.createdAt })
            }

        // Sort groups
        when (mode) {
            SortMode.CREATED_DESC -> {
                // Order by latest memo in each group (desc)
                grouped.sortedByDescending { group ->
                    group.memos.maxOfOrNull { it.createdAt } ?: 0L
                }
            }
            SortMode.CATEGORY_NAME -> {
                // Order alphabetically by category name
                grouped.sortedBy { it.category }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
    }

    fun setCategoryFilter(category: String?) {
        _categoryFilter.value = category
    }

    fun toggleCategoryExpanded(category: String) {
        _expandedCategories.update { current ->
            if (category in current) {
                current - category
            } else {
                current + category
            }
        }
    }

    fun isCategoryExpanded(category: String): Boolean {
        return category in _expandedCategories.value
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
        viewModelScope.launch {
            memoRepository.deleteMemo(memo)
        }
    }

    fun deleteCategory(categoryName: String) {
        viewModelScope.launch {
            memoRepository.deleteMemosByCategoryName(categoryName)
        }
    }
}
