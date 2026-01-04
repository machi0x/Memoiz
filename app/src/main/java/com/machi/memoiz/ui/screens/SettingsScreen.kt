@file:OptIn(ExperimentalMaterial3Api::class)

package com.machi.memoiz.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import com.machi.memoiz.BuildConfig
import com.machi.memoiz.R
import com.machi.memoiz.data.datastore.UserPreferences
import com.machi.memoiz.ui.theme.MemoizTheme
import com.machi.memoiz.util.UsageStatsHelper
import kotlinx.coroutines.flow.MutableStateFlow
import com.machi.memoiz.service.GenAiFeatureStates
import com.google.mlkit.genai.common.FeatureStatus

/**
 * Settings screen for app configuration.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsScreenViewModel,
    onNavigateBack: () -> Unit
) {
    var showAboutDialog by remember { mutableStateOf(false) }
    var showReMergeConfirm by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasUsageStatsPermission by remember {
        mutableStateOf(UsageStatsHelper(context).hasUsageStatsPermission())
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsageStatsPermission = UsageStatsHelper(context).hasUsageStatsPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val appVersion = remember { BuildConfig.VERSION_NAME }
    val genAiPrefs by viewModel.genAiPreferences.collectAsState(initial = UserPreferences())
    val baseModelNames by viewModel.baseModelNames.collectAsStateWithLifecycle()
    // Collect feature states for GenAI (nullable until loaded)
    val featureStates by viewModel.featureStates.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                item {
                    PreferenceItem(
                        title = stringResource(R.string.settings_tutorial_title),
                        subtitle = stringResource(R.string.settings_tutorial_description),
                        leadingIcon = {
                            Icon(Icons.Default.School, contentDescription = null)
                        },
                        onClick = {
                            viewModel.requestTutorial()
                            onNavigateBack()
                        }
                    )
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                }

                item {
                    PreferenceItem(
                        title = stringResource(R.string.settings_usage_title),
                        subtitle = stringResource(R.string.settings_usage_description),
                        leadingIcon = {
                            Icon(
                                imageVector = if (hasUsageStatsPermission) Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (hasUsageStatsPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        extraContent = {
                            val statusText = if (hasUsageStatsPermission) {
                                stringResource(R.string.settings_usage_status_enabled)
                            } else {
                                stringResource(R.string.settings_usage_status_disabled)
                            }
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (hasUsageStatsPermission) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        },
                        onClick = { openUsageAccessSettings(context) }
                    )
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                }

                item {
                    PreferenceItem(
                        title = stringResource(R.string.settings_remerge_all_title),
                        subtitle = stringResource(R.string.settings_remerge_all_description),
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                        onClick = { showReMergeConfirm = true }
                    )
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                }

                item {
                    PreferenceItem(
                        title = stringResource(R.string.settings_oss_title),
                        subtitle = stringResource(R.string.settings_oss_description),
                        leadingIcon = {
                            Icon(Icons.Default.Description, contentDescription = null)
                        },
                        onClick = { openOssLicenses(context) }
                    )
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                }

                item {
                    PreferenceItem(
                        title = stringResource(R.string.settings_about_button),
                        leadingIcon = {
                            Icon(Icons.Default.Info, contentDescription = null)
                        },
                        onClick = { showAboutDialog = true }
                    )
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                }

                // Consolidated AI features header: use PreferenceItem so it aligns with other sections. Adjust subtitle padding if needed.
                item {
                    PreferenceItem(
                        title = stringResource(R.string.genai_models_title),
                        subtitle = stringResource(R.string.genai_models_switch_warning),
                        leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    )
                }

                val imageState = featureStates?.imageDescription ?: FeatureStatus.UNAVAILABLE
                val textState = featureStates?.textGeneration ?: FeatureStatus.UNAVAILABLE
                val sumState = featureStates?.summarization ?: FeatureStatus.UNAVAILABLE

                // Image description row (use smaller headline style and disable until feature state loaded)
                item {
                    val loaded = featureStates != null
                    val available = loaded && imageState == FeatureStatus.AVAILABLE
                    // If featureStates not yet loaded, default to showing ON when user hasn't forced OFF.
                    val checked = (!genAiPrefs.forceOffImageDescription) && (featureStates == null || available)
                    PreferenceItem(
                        title = stringResource(R.string.genai_model_label_image),
                        headlineStyle = MaterialTheme.typography.titleSmall,
                        subtitle = baseModelNames.first ?: stringResource(R.string.genai_models_loading),
                        leadingIcon = { Spacer(modifier = Modifier.width(48.dp)) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Switch(
                                    modifier = Modifier.size(36.dp),
                                    checked = checked,
                                    enabled = loaded,
                                    onCheckedChange = { newValue ->
                                        if (!loaded) return@Switch
                                        if (newValue) {
                                            if (!available) {
                                                // Navigate back to main UI so the usual check/download dialog runs
                                                onNavigateBack()
                                            } else {
                                                viewModel.setUseImageDescription(true)
                                            }
                                        } else {
                                            viewModel.setUseImageDescription(false)
                                        }
                                    }
                                )
                            }
                        },
                        compact = true // Use compact mode for denser appearance
                    )
                }

                // Text generation row (smaller headline)
                item {
                    val loaded = featureStates != null
                    val available = loaded && textState == FeatureStatus.AVAILABLE
                    val checked = (!genAiPrefs.forceOffTextGeneration) && (featureStates == null || available)
                    PreferenceItem(
                        title = stringResource(R.string.genai_model_label_text),
                        headlineStyle = MaterialTheme.typography.titleSmall,
                        subtitle = baseModelNames.second ?: stringResource(R.string.genai_models_loading),
                        leadingIcon = { Spacer(modifier = Modifier.width(48.dp)) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Switch(
                                    modifier = Modifier.size(36.dp),
                                    checked = checked,
                                    enabled = loaded,
                                    onCheckedChange = { newValue ->
                                        if (!loaded) return@Switch
                                        if (newValue) {
                                            if (!available) {
                                                onNavigateBack()
                                            } else {
                                                viewModel.setUseTextGeneration(true)
                                            }
                                        } else {
                                            viewModel.setUseTextGeneration(false)
                                        }
                                    }
                                )
                            }
                        },
                        compact = true // Use compact mode for denser appearance
                    )
                }

                // Summarization row (smaller headline)
                item {
                    val loaded = featureStates != null
                    val available = loaded && sumState == FeatureStatus.AVAILABLE
                    val checked = (!genAiPrefs.forceOffSummarization) && (featureStates == null || available)
                    PreferenceItem(
                        title = stringResource(R.string.genai_model_label_summarization),
                        headlineStyle = MaterialTheme.typography.titleSmall,
                        subtitle = baseModelNames.third ?: stringResource(R.string.genai_models_loading),
                        leadingIcon = { Spacer(modifier = Modifier.width(48.dp)) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Switch(
                                    modifier = Modifier.size(36.dp),
                                    checked = checked,
                                    enabled = loaded,
                                    onCheckedChange = { newValue ->
                                        if (!loaded) return@Switch
                                        if (newValue) {
                                            if (!available) {
                                                onNavigateBack()
                                            } else {
                                                viewModel.setUseSummarization(true)
                                            }
                                        } else {
                                            viewModel.setUseSummarization(false)
                                        }
                                    }
                                )
                            }
                        },
                        compact = true // Use compact mode for denser appearance
                    )
                }

                // DEBUG: show current feature states and prefs (remove in production)
                item {
                    val dbgImage = when (featureStates?.imageDescription) {
                        FeatureStatus.AVAILABLE -> "IMAGE:AV"
                        FeatureStatus.DOWNLOADABLE -> "IMAGE:DL"
                        FeatureStatus.UNAVAILABLE -> "IMAGE:NA"
                        else -> "IMAGE:??"
                    }
                    val dbgText = when (featureStates?.textGeneration) {
                        FeatureStatus.AVAILABLE -> "TEXT:AV"
                        FeatureStatus.DOWNLOADABLE -> "TEXT:DL"
                        FeatureStatus.UNAVAILABLE -> "TEXT:NA"
                        else -> "TEXT:??"
                    }
                    val dbgSum = when (featureStates?.summarization) {
                        FeatureStatus.AVAILABLE -> "SUM:AV"
                        FeatureStatus.DOWNLOADABLE -> "SUM:DL"
                        FeatureStatus.UNAVAILABLE -> "SUM:NA"
                        else -> "SUM:??"
                    }
                    Text(
                        text = "$dbgImage  $dbgText  $dbgSum\nforceOff: img=${genAiPrefs.forceOffImageDescription} text=${genAiPrefs.forceOffTextGeneration} sum=${genAiPrefs.forceOffSummarization}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }

    if (showAboutDialog) {
        AboutDialog(
            appVersion = appVersion,
            onDismiss = { showAboutDialog = false }
        )
    }

    if (showReMergeConfirm) {
        AlertDialog(
            onDismissRequest = { showReMergeConfirm = false },
            title = { Text(stringResource(R.string.settings_remerge_all_title)) },
            text = { Text(stringResource(R.string.settings_remerge_all_description)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remergeAllMemos(context)
                    showReMergeConfirm = false
                }) { Text(stringResource(R.string.dialog_reanalyze_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showReMergeConfirm = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}

@Composable
private fun PreferenceItem(
    title: String,
    subtitle: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    extraContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    headlineStyle: androidx.compose.ui.text.TextStyle? = null,
    compact: Boolean = false,
) {
    val verticalPad = if (compact) 0.dp else 8.dp
    val supportingSpacing = if (compact) 0.dp else 4.dp

    val supporting: (@Composable () -> Unit)? = if (subtitle != null || extraContent != null) {
        {
            Column(verticalArrangement = Arrangement.spacedBy(supportingSpacing)) {
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                extraContent?.invoke()
            }
        }
    } else {
        null
    }

    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = verticalPad)
            .then(
                if (onClick != null) {
                    // Use indication = null to avoid PlatformRipple/Indication mismatch issues.
                    Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            ),
        headlineContent = { Text(title, style = headlineStyle ?: MaterialTheme.typography.titleMedium) },
        supportingContent = supporting,
        leadingContent = leadingIcon?.let { { it() } },
        trailingContent = trailingContent?.let { { it() } } ?: onClick?.let {
            {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    )
}

private fun openUsageAccessSettings(context: Context) {
    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun openOssLicenses(context: Context) {
    val intent = Intent(context, OssLicensesMenuActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

@Composable
private fun AboutDialog(
    appVersion: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_close))
            }
        },
        icon = {
            Icon(Icons.Default.Info, contentDescription = null)
        },
        title = { Text(text = stringResource(R.string.settings_about_title)) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_about_description),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.settings_about_version, appVersion),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Image(
                    painter = painterResource(id = R.drawable.thanks),
                    contentDescription = null,
                    modifier = Modifier
                        .size(200.dp)
                        .padding(top = 8.dp)
                )
            }
        }
    )
}

// Preview for Settings screen
@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    // Use the fake view model so the preview runs the real SettingsScreen composable code path.
    val fakeVm = remember { FakeSettingsViewModel() }
    MemoizTheme {
        Surface {
            SettingsScreen(viewModel = fakeVm, onNavigateBack = {})
        }
    }
}

// Preview-only fake ViewModel that matches the small API surface used by SettingsScreen.
private class FakeSettingsViewModel : SettingsScreenViewModel {
    override val genAiPreferences: kotlinx.coroutines.flow.Flow<UserPreferences> = MutableStateFlow(UserPreferences())
    override val baseModelNames: kotlinx.coroutines.flow.StateFlow<Triple<String?, String?, String?>> = MutableStateFlow(Triple("nano-v2", "nano-text", "nano-v2"))
    override val featureStates: kotlinx.coroutines.flow.StateFlow<GenAiFeatureStates?> = MutableStateFlow(
        GenAiFeatureStates(
            imageDescription = FeatureStatus.AVAILABLE,
            textGeneration = FeatureStatus.DOWNLOADABLE,
            summarization = FeatureStatus.AVAILABLE
        )
    )

    override fun requestTutorial() {}
    override fun remergeAllMemos(context: Context) {}
    override fun setUseImageDescription(use: Boolean) { /* preview-only: no-op */ }
    override fun setUseTextGeneration(use: Boolean) { /* preview-only: no-op */ }
    override fun setUseSummarization(use: Boolean) { /* preview-only: no-op */ }
}
