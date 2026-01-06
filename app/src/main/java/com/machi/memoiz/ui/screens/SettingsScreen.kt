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
import com.machi.memoiz.data.datastore.UiDisplayMode
import com.machi.memoiz.ui.theme.MemoizTheme
import com.machi.memoiz.util.UsageStatsHelper
import kotlinx.coroutines.flow.MutableStateFlow
import com.machi.memoiz.service.GenAiFeatureStates
import com.google.mlkit.genai.common.FeatureStatus
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * Settings screen for app configuration.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsScreenViewModel,
    onNavigateBack: () -> Unit
) {
    var showAboutDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasUsageStatsPermission by remember {
        mutableStateOf(UsageStatsHelper(context).hasUsageStatsPermission())
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsageStatsPermission = UsageStatsHelper(context).hasUsageStatsPermission()
                // Re-check GenAI feature states when Settings becomes visible to avoid stale/unknown UI state.
                try {
                    viewModel.refreshFeatureStates()
                } catch (_: Exception) {
                    // ignore - refreshFeatureStates is already safe and preserves previous values on failure
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val appVersion = remember { BuildConfig.VERSION_NAME }
    // genAiPreferences is available in the viewModel but not needed for the read-only display here.
    // val genAiPrefs by viewModel.genAiPreferences.collectAsState(initial = UserPreferences())
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
                            Icon(Icons.Filled.School, contentDescription = null)
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
                                imageVector = if (hasUsageStatsPermission) Icons.Filled.CheckCircle else Icons.Filled.Info,
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

                // <-- Insert UI display mode here (between usage and OSS) -->
                item {
                    // collect latest user preferences to get current ui display mode
                    val userPrefs by viewModel.genAiPreferences.collectAsStateWithLifecycle(initialValue = UserPreferences())
                    val currentMode = userPrefs.uiDisplayMode
                    val ctx = LocalContext.current
                    val scope = rememberCoroutineScope()

                    PreferenceItem(
                        title = stringResource(R.string.settings_ui_display_mode),
                        leadingIcon = { Icon(Icons.Filled.Brightness6, contentDescription = null) },
                        extraContent = {
                            Column(modifier = Modifier.padding(start = 4.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            viewModel.setUiDisplayMode(UiDisplayMode.LIGHT)
                                            // Delay recreation slightly so DataStore write can complete
                                            scope.launch {
                                                kotlinx.coroutines.delay(300)
                                                (ctx as? android.app.Activity)?.recreate()
                                            }
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = currentMode == UiDisplayMode.LIGHT, onClick = {
                                        viewModel.setUiDisplayMode(UiDisplayMode.LIGHT)
                                        scope.launch {
                                            kotlinx.coroutines.delay(300)
                                            (ctx as? android.app.Activity)?.recreate()
                                        }
                                    })
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = stringResource(R.string.settings_ui_display_mode_light), style = MaterialTheme.typography.bodySmall)
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            viewModel.setUiDisplayMode(UiDisplayMode.DARK)
                                            scope.launch {
                                                kotlinx.coroutines.delay(300)
                                                (ctx as? android.app.Activity)?.recreate()
                                            }
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = currentMode == UiDisplayMode.DARK, onClick = {
                                        viewModel.setUiDisplayMode(UiDisplayMode.DARK)
                                        scope.launch {
                                            kotlinx.coroutines.delay(300)
                                            (ctx as? android.app.Activity)?.recreate()
                                        }
                                    })
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = stringResource(R.string.settings_ui_display_mode_dark), style = MaterialTheme.typography.bodySmall)
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            viewModel.setUiDisplayMode(UiDisplayMode.SYSTEM)
                                            // For system mode, let system handle theme; no recreate necessary
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = currentMode == UiDisplayMode.SYSTEM, onClick = {
                                        viewModel.setUiDisplayMode(UiDisplayMode.SYSTEM)
                                    })
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = stringResource(R.string.settings_ui_display_mode_system), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
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
                            Icon(Icons.Filled.Description, contentDescription = null)
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
                            Icon(Icons.Filled.Info, contentDescription = null)
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

                // Show each AI feature as a two-column, no-border row: left = feature name, right = two stacked rows (model name / status)
                item {
                    // Image description
                    val imageModelName = baseModelNames.first
                    val imageModelDisplay = imageModelName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.genai_models_unknown)
                    AiFeatureRow(
                        featureTitle = stringResource(R.string.genai_model_label_image),
                        modelDisplay = imageModelDisplay,
                        state = featureStates?.imageDescription ?: FeatureStatus.UNAVAILABLE
                    )

                    // Text generation
                    val textModelName = baseModelNames.second
                    val textModelDisplay = textModelName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.genai_models_unknown)
                    AiFeatureRow(
                        featureTitle = stringResource(R.string.genai_model_label_text),
                        modelDisplay = textModelDisplay,
                        state = featureStates?.textGeneration ?: FeatureStatus.UNAVAILABLE
                    )

                    // Summarization
                    val sumModelName = baseModelNames.third
                    val sumModelDisplay = sumModelName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.genai_models_unknown)
                    AiFeatureRow(
                        featureTitle = stringResource(R.string.genai_model_label_summarization),
                        modelDisplay = sumModelDisplay,
                        state = featureStates?.summarization ?: FeatureStatus.UNAVAILABLE
                    )
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                }

                // UI Display Mode preference: no description, three radio rows
                // (Removed duplicate - already present earlier between usage and OSS)

            }
        }
    }

    if (showAboutDialog) {
        AboutDialog(
            appVersion = appVersion,
            onDismiss = { showAboutDialog = false }
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
            Icon(Icons.Filled.Info, contentDescription = null)
        },
        title = { Text(text = stringResource(R.string.settings_about_title)) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Use the localized long description so Japanese/English copy remains consistent
                Text(
                    text = stringResource(R.string.settings_about_description),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Image(
                    painter = painterResource(id = R.drawable.thanks),
                    contentDescription = null,
                    modifier = Modifier
                        .size(200.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.settings_about_version, appVersion),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun AiFeatureRow(featureTitle: String, modelDisplay: String, state: @FeatureStatus Int) {
    // Render a no-border two-column row: left = feature title, right = column(model, status)
    val statusTextRes = if (state == FeatureStatus.AVAILABLE) {
        R.string.genai_status_paren_available
    } else if (state == FeatureStatus.DOWNLOADABLE) {
        R.string.genai_status_paren_downloadable
    } else {
        R.string.genai_status_paren_unavailable
    }
    val statusColor = if (state == FeatureStatus.AVAILABLE) {
        Color(0xFF2E7D32)
    } else if (state == FeatureStatus.DOWNLOADABLE) {
        Color(0xFFFBC02D)
    } else {
        MaterialTheme.colorScheme.error
    }

    // Use a Card with a subtle background to make this section look like an indented table row
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 72.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left column: feature title (vertically centered)
            Box(modifier = Modifier.width(160.dp).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                Text(text = featureTitle, style = MaterialTheme.typography.titleSmall)
            }

            // Right column: model name (upper) and status (lower)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.genai_label_model_name, modelDisplay), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = stringResource(R.string.genai_label_status), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(imageVector = Icons.Filled.FiberManualRecord, contentDescription = null, tint = statusColor, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(statusTextRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
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
    override fun refreshFeatureStates() { /* preview-only: no-op */ }
    override fun setUseImageDescription(use: Boolean) { /* preview-only: no-op */ }
    override fun setUseTextGeneration(use: Boolean) { /* preview-only: no-op */ }
    override fun setUseSummarization(use: Boolean) { /* preview-only: no-op */ }
    override fun setUiDisplayMode(mode: UiDisplayMode) { /* preview-only: no-op */ }
}
