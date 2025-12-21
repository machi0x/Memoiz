package com.machika.memoiz.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.machika.memoiz.data.repository.CategoryRepository
import com.machika.memoiz.data.repository.MemoRepository
import com.machika.memoiz.ui.screens.MainViewModel
import com.machika.memoiz.ui.screens.SettingsViewModel

/**
 * ViewModelFactory for creating ViewModels with dependencies.
 * This ensures proper dependency injection and prevents recreation on recomposition.
 */
class ViewModelFactory(
    private val categoryRepository: CategoryRepository,
    private val memoRepository: MemoRepository
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(MainViewModel::class.java) -> {
                MainViewModel(memoRepository, categoryRepository) as T
            }
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(categoryRepository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
