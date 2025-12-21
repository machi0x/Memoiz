package com.machika.memoiz.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.machika.memoiz.data.repository.CategoryRepository
import com.machika.memoiz.domain.model.Category
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for settings/category management screen.
 * Manages custom categories (up to 20) and favorite categories.
 */
class SettingsViewModel(
    private val categoryRepository: CategoryRepository
) : ViewModel() {
    
    companion object {
        const val MAX_CUSTOM_CATEGORIES = 20
    }
    
    val allCategories: StateFlow<List<Category>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val customCategories: StateFlow<List<Category>> = categoryRepository.getCustomCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val favoriteCategories: StateFlow<List<Category>> = categoryRepository.getFavoriteCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val customCategoryCount: StateFlow<Int> = customCategories
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    
    val canAddCustomCategory: StateFlow<Boolean> = customCategoryCount
        .map { it < MAX_CUSTOM_CATEGORIES }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    fun addCustomCategory(name: String) {
        viewModelScope.launch {
            if (customCategoryCount.value < MAX_CUSTOM_CATEGORIES && name.isNotBlank()) {
                val category = Category(
                    name = name.trim(),
                    isCustom = true
                )
                categoryRepository.insertCategory(category)
            }
        }
    }
    
    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            categoryRepository.deleteCategory(category)
        }
    }
    
    fun toggleFavorite(category: Category) {
        viewModelScope.launch {
            categoryRepository.toggleFavorite(category)
        }
    }
}
