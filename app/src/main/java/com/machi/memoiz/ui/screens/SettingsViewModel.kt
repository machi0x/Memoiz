package com.machi.memoiz.ui.screens

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.delay

/**
 * Contract (interface) used by SettingsScreen so Preview can inject a Fake VM.
 */
interface SettingsScreenViewModel {
    val genAiPreferences: Flow<com.machi.memoiz.data.datastore.UserPreferences>
    val baseModelNames: StateFlow<Triple<String?, String?, String?>>
    val featureStates: StateFlow<GenAiFeatureStates?>

    // Allow SettingsScreen to request an explicit refresh of GenAI status
    fun refreshFeatureStates()

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
    // Helper to pretty-print FeatureStatus int constants for logs
    private fun featureStatusName(@com.google.mlkit.genai.common.FeatureStatus status: Int): String {
        return when (status) {
            com.google.mlkit.genai.common.FeatureStatus.AVAILABLE -> "AVAILABLE"
            com.google.mlkit.genai.common.FeatureStatus.DOWNLOADABLE -> "DOWNLOADABLE"
            com.google.mlkit.genai.common.FeatureStatus.UNAVAILABLE -> "UNAVAILABLE"
            else -> "UNKNOWN($status)"
        }
    }

    private fun prettyStates(s: GenAiFeatureStates?): String {
        if (s == null) return "null"
        return "image=${featureStatusName(s.imageDescription)} text=${featureStatusName(s.textGeneration)} sum=${featureStatusName(s.summarization)}"
    }

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

    override fun refreshFeatureStates() {
        viewModelScope.launch {
            _loadingFeatureStates.value = true
            try {
                // Try to refresh base model names first; preserve parts that failed to load.
                val prevNames = _baseModelNames.value
                val newNames = runCatching { genAiStatusManager.getBaseModelNames() }.getOrNull()
                if (newNames != null) {
                    _baseModelNames.value = Triple(
                        newNames.first ?: prevNames.first,
                        newNames.second ?: prevNames.second,
                        newNames.third ?: prevNames.third
                    )
                }

                // Then check feature states; on failure keep previous value to avoid flipping to UNAVAILABLE.
                val prevStates = _featureStates.value
                var checked = runCatching { genAiStatusManager.checkAll() }.getOrNull()
                Log.d("SettingsViewModel", "initial checkAll returned: ${prettyStates(checked)}; prevStates=${prettyStates(prevStates)}")
                // If first check failed or returned all UNAVAILABLE and we had no previous value,
                // retry once after a short delay to avoid transient GMS/GenAI hiccups causing false UNAVAILABLE.
                val prev = prevStates
                val firstAllUnavailable = checked != null && (
                    checked.imageDescription == com.google.mlkit.genai.common.FeatureStatus.UNAVAILABLE &&
                    checked.textGeneration == com.google.mlkit.genai.common.FeatureStatus.UNAVAILABLE &&
                    checked.summarization == com.google.mlkit.genai.common.FeatureStatus.UNAVAILABLE
                )
                if ((checked == null || (firstAllUnavailable && prev == null))) {
                    try {
                        delay(500)
                        checked = runCatching { genAiStatusManager.checkAll() }.getOrNull()
                        Log.d("SettingsViewModel", "retry checkAll returned: ${prettyStates(checked)}")
                    } catch (_: Exception) {
                        // ignore retry failures
                    }
                }

                // If still null after retry, keep prevStates (avoid overwriting with null).
                if (checked == null) {
                    Log.w("SettingsViewModel", "checkAll returned null even after retry; will keep previous state if any")
                    // leave checked null so subsequent logic preserves prevStates
                }

                if (checked != null) {
                    // Use the freshly checked state directly. Inference based on
                    // model name hints can create a UI mismatch where the status
                    // appears AVAILABLE even though checkAll reports UNAVAILABLE.
                    // To ensure Settings reflects the real checked status, accept
                    // the checked result as authoritative.
                    _featureStates.value = checked
                } else {
                    // keep prevStates
                }
            } catch (ignored: Exception) {
                // keep previous values o chn unexpected errors
            } finally {
                _loadingFeatureStates.value = false
            }
        }
    }
}
