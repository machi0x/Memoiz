package com.machi.memoiz.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.machi.memoiz.data.datastore.PreferencesDataStoreManager
import com.machi.memoiz.service.ContentProcessingLauncher
import com.machi.memoiz.service.GenAiStatusManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Settings screen.
 */
class SettingsViewModel(
    private val preferencesManager: PreferencesDataStoreManager,
    private val genAiStatusManager: GenAiStatusManager
) : ViewModel() {
    val genAiPreferences = preferencesManager.userPreferencesFlow

    private val _baseModelNames = MutableStateFlow<Triple<String?, String?, String?>>(Triple(null, null, null))
    val baseModelNames = _baseModelNames.asStateFlow()

    init {
        viewModelScope.launch {
            _baseModelNames.value = genAiStatusManager.getBaseModelNames()
        }
    }

    fun requestTutorial() {
        viewModelScope.launch { preferencesManager.requestTutorial() }
    }

    fun remergeAllMemos(context: Context) {
        ContentProcessingLauncher.enqueueMergeWork(context, null)
    }

    fun setForceOffImageDescription(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setForceOffImageDescription(enabled) }
    }

    fun setForceOffTextGeneration(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setForceOffTextGeneration(enabled) }
    }

    fun setForceOffSummarization(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setForceOffSummarization(enabled) }
    }
}
