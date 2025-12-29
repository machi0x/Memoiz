package com.machi.memoiz.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.machi.memoiz.domain.model.Category
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {

    companion object {
        const val MAX_CUSTOM_CATEGORIES = 20

        private val DEFAULT_CATEGORIES = listOf(
            Category(id = 1, name = "Work"),
            Category(id = 2, name = "Personal"),
            Category(id = 3, name = "Ideas"),
            Category(id = 4, name = "Shopping"),
            Category(id = 5, name = "Learning"),
        )
    }

    private val _allCategories = MutableStateFlow(DEFAULT_CATEGORIES)
    val allCategories: StateFlow<List<Category>> = _allCategories.asStateFlow()

    private val _customCategories = MutableStateFlow<List<Category>>(emptyList())
    val customCategories: StateFlow<List<Category>> = _customCategories.asStateFlow()

    private val _customCategoryCount = MutableStateFlow(0)
    val customCategoryCount: StateFlow<Int> = _customCategoryCount.asStateFlow()

    private val _canAddCustomCategory = MutableStateFlow(true)
    val canAddCustomCategory: StateFlow<Boolean> = _canAddCustomCategory.asStateFlow()

    fun addCustomCategory(name: String) {
        viewModelScope.launch {
            _customCategories.update { current ->
                if (current.size >= MAX_CUSTOM_CATEGORIES) {
                    current
                } else {
                    current + Category(
                        id = System.currentTimeMillis(),
                        name = name.trim(),
                        isCustom = true
                    )
                }
            }
            syncCounters()
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            _customCategories.update { current -> current.filterNot { it.id == category.id } }
            syncCounters()
        }
    }

    private fun syncCounters() {
        val currentSize = _customCategories.value.size
        _customCategoryCount.value = currentSize
        _canAddCustomCategory.value = currentSize < MAX_CUSTOM_CATEGORIES
    }
}
