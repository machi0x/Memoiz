package com.machi.memoiz.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.machi.memoiz.data.repository.CategoryRepository
import com.machi.memoiz.data.repository.MemoRepository
import com.machi.memoiz.domain.model.Category
import com.machi.memoiz.domain.model.Memo
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the main screen.
 * Manages memos and categories.
 */
class MainViewModel(
    private val memoRepository: MemoRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {
    
    val categories: StateFlow<List<Category>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val memos: StateFlow<List<Memo>> = memoRepository.getAllMemos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    val selectedCategoryId: StateFlow<Long?> = _selectedCategoryId.asStateFlow()
    
    val filteredMemos: StateFlow<List<Memo>> = combine(
        memos,
        _selectedCategoryId
    ) { allMemos, categoryId ->
        if (categoryId == null) {
            allMemos
        } else {
            allMemos.filter { it.categoryId == categoryId }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    fun selectCategory(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
    }
    
    fun toggleFavorite(category: Category) {
        viewModelScope.launch {
            categoryRepository.toggleFavorite(category)
        }
    }
    
    fun deleteMemo(memo: Memo) {
        viewModelScope.launch {
            memoRepository.deleteMemo(memo)
        }
    }
    
    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            // First delete all memos in this category
            memoRepository.deleteMemosByCategory(category.id)
            // Then delete the category
            categoryRepository.deleteCategory(category)
        }
    }
}
