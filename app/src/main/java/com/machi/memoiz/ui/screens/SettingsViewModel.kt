package com.machi.memoiz.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.machi.memoiz.data.datastore.PreferencesDataStoreManager
import com.machi.memoiz.service.ContentProcessingLauncher
import com.machi.memoiz.service.GenAiStatusManager
import com.machi.memoiz.service.GenAiFeatureStates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Contract (interface) used by SettingsScreen so Preview can inject a Fake VM.
 */
interface SettingsScreenViewModel {
    val genAiPreferences: Flow<com.machi.memoiz.data.datastore.UserPreferences>
    val baseModelNames: StateFlow<Triple<String?, String?, String?>>
    val featureStates: StateFlow<GenAiFeatureStates?>

    fun requestTutorial()
    fun remergeAllMemos(context: Context)
    fun setUseImageDescription(use: Boolean)
    fun setUseTextGeneration(use: Boolean)
    fun setUseSummarization(use: Boolean)
}

/**
 * ViewModel for Settings screen.
 */
class SettingsViewModel(
    private val preferencesManager: PreferencesDataStoreManager,
    private val genAiStatusManager: GenAiStatusManager
) : ViewModel(), SettingsScreenViewModel {
    override val genAiPreferences = preferencesManager.userPreferencesFlow

    private val _baseModelNames = MutableStateFlow<Triple<String?, String?, String?>>(Triple(null, null, null))
    override val baseModelNames = _baseModelNames.asStateFlow()

    // Expose current feature status so the UI can show available/downloadable/unavailable
    private val _featureStates = MutableStateFlow<GenAiFeatureStates?>(null)
    override val featureStates = _featureStates.asStateFlow()

    private val _loadingFeatureStates = MutableStateFlow(false)
    val loadingFeatureStates = _loadingFeatureStates.asStateFlow()

    init {
        viewModelScope.launch {
            _baseModelNames.value = genAiStatusManager.getBaseModelNames()
            // initial load
            refreshFeatureStates()
        }
    }

    override fun requestTutorial() {
        viewModelScope.launch { preferencesManager.requestTutorial() }
    }

    override fun remergeAllMemos(context: Context) {
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
    override fun setUseImageDescription(use: Boolean) {
        viewModelScope.launch { preferencesManager.setForceOffImageDescription(!use) }
    }

    override fun setUseTextGeneration(use: Boolean) {
        viewModelScope.launch { preferencesManager.setForceOffTextGeneration(!use) }
    }

    override fun setUseSummarization(use: Boolean) {
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
