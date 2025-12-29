package com.machi.memoiz.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.machi.memoiz.data.repository.MemoRepository
import com.machi.memoiz.ui.screens.MainViewModel
import com.machi.memoiz.ui.screens.SettingsViewModel

/**
 * ViewModelFactory for creating ViewModels with dependencies.
 */
class ViewModelFactory(
    private val memoRepository: MemoRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(MainViewModel::class.java) -> {
                MainViewModel(memoRepository) as T
            }
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel() as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
