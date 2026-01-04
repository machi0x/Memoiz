package com.machi.memoiz.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.machi.memoiz.data.datastore.PreferencesDataStoreManager
import com.machi.memoiz.service.ContentProcessingLauncher
import com.machi.memoiz.service.GenAiStatusManager
import com.machi.memoiz.service.GenAiFeatureStates
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

    // Expose current feature status so the UI can show available/downloadable/unavailable
    private val _featureStates = MutableStateFlow<GenAiFeatureStates?>(null)
    val featureStates = _featureStates.asStateFlow()

    private val _loadingFeatureStates = MutableStateFlow(false)
    val loadingFeatureStates = _loadingFeatureStates.asStateFlow()

    init {
        viewModelScope.launch {
            _baseModelNames.value = genAiStatusManager.getBaseModelNames()
            // initial load
            refreshFeatureStates()
        }
    }

    fun requestTutorial() {
        viewModelScope.launch { preferencesManager.requestTutorial() }
    }

    fun remergeAllMemos(context: Context) {
        ContentProcessingLauncher.enqueueMergeWork(context, null)
    }

    // Existing force-off setters (lower-level) kept for backward compatibility
    fun setForceOffImageDescription(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setForceOffImageDescription(enabled) }
    }

    fun setForceOffTextGeneration(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setForceOffTextGeneration(enabled) }
    }

    fun setForceOffSummarization(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setForceOffSummarization(enabled) }
    }

    // Helper 'use' setters (UI shows switches as "Use this AI model"), which invert the stored force-off setting
    fun setUseImageDescription(use: Boolean) {
        viewModelScope.launch { preferencesManager.setForceOffImageDescription(!use) }
    }

    fun setUseTextGeneration(use: Boolean) {
        viewModelScope.launch { preferencesManager.setForceOffTextGeneration(!use) }
    }

    fun setUseSummarization(use: Boolean) {
        viewModelScope.launch { preferencesManager.setForceOffSummarization(!use) }
    }

    fun refreshFeatureStates() {
        viewModelScope.launch {
            _loadingFeatureStates.value = true
            try {
                _featureStates.value = genAiStatusManager.checkAll()
            } catch (ignored: Exception) {
                // keep previous value or null on error
            } finally {
                _loadingFeatureStates.value = false
            }
        }
    }
}
