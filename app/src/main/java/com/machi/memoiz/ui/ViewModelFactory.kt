package com.machi.memoiz.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.work.WorkManager
import com.machi.memoiz.data.datastore.PreferencesDataStoreManager
import com.machi.memoiz.data.repository.MemoRepository
import com.machi.memoiz.service.GenAiStatusManager
import com.machi.memoiz.ui.screens.MainViewModel
import com.machi.memoiz.ui.screens.SettingsViewModel

/**
 * ViewModelFactory for creating ViewModels with dependencies.
 */
class ViewModelFactory(
    private val memoRepository: MemoRepository,
    private val preferencesManager: PreferencesDataStoreManager,
    private val workManager: WorkManager,
    private val context: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(MainViewModel::class.java) -> {
                MainViewModel(memoRepository, preferencesManager, workManager) as T
            }
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(preferencesManager, GenAiStatusManager(context)) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
