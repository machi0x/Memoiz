@file:OptIn(ExperimentalMaterial3Api::class)

package com.machi.memoiz.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalInspectionMode
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

    // --- New state and launcher for Export/Import ---
    val scope = rememberCoroutineScope()
    var showImportProgress by remember { mutableStateOf(false) }
    var showExportProgress by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<ImportResult?>(null) }
    var exportFailureMessage by remember { mutableStateOf<String?>(null) }

    // Password dialogs and state
    var showExportPasswordDialog by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf("") }
    var exportPasswordError by remember { mutableStateOf<String?>(null) }

    var showImportPasswordDialog by remember { mutableStateOf(false) }
    var importPassword by remember { mutableStateOf("") }
    var importPasswordError by remember { mutableStateOf<String?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                showImportProgress = true
                try {
                    // Persist read permission if possible
                    try {
                        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (_: Exception) {
                        // ignore if not granted
                    }
                    val res = viewModel.importMemos(uri, context, null)
                    if (res.message == "PASSWORD_REQUIRED") {
                        // Prompt user for password
                        pendingImportUri = uri
                        importPassword = ""
                        importPasswordError = null
                        showImportPasswordDialog = true
                    } else {
                        // Map known messages to localized strings
                        val mapped = when (res.message) {
                            null -> null
                            "Invalid password or corrupted file" -> context.getString(R.string.settings_import_result_bad_password)
                            else -> {
                                val msg = res.message
                                when {
                                    msg.contains("Cannot open input stream", ignoreCase = true) -> context.getString(R.string.settings_import_result_open_failed)
                                    msg.contains("meta.json not found", ignoreCase = true) -> context.getString(R.string.settings_import_result_no_meta)
                                    else -> context.getString(R.string.settings_import_result_failure)
                                }
                            }
                        }
                        importResult = ImportResult(res.added, res.skipped, res.errors, mapped)
                    }
                } catch (_: Exception) {
                    importResult = ImportResult(0, 0, 1, context.getString(R.string.settings_import_result_failure))
                } finally {
                    showImportProgress = false
                }
            }
        }
    }

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

                // UI display mode item (unchanged)
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

                // Export PreferenceItem — open password dialog first
                item {
                    PreferenceItem(
                        title = stringResource(R.string.settings_export_title),
                        subtitle = stringResource(R.string.settings_export_description),
                        leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                        onClick = {
                            // Show optional password input dialog
                            exportPassword = ""
                            exportPasswordError = null
                            showExportPasswordDialog = true
                        }
                    )
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                }

                // Import PreferenceItem — launch SAF picker for ZIP
                item {
                    PreferenceItem(
                        title = stringResource(R.string.settings_import_title),
                        subtitle = stringResource(R.string.settings_import_description),
                        leadingIcon = { Icon(Icons.Filled.FileOpen, contentDescription = null) },
                        onClick = {
                            // Launch SAF file picker for ZIP
                            openDocumentLauncher.launch(arrayOf("application/zip", "application/*", "*/*"))
                        }
                    )
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
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

    // Export password dialog
    if (showExportPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showExportPasswordDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    // Validate password if not empty: alphanumeric 4-32
                    if (exportPassword.isNotEmpty()) {
                        val ok = Regex("^[A-Za-z0-9]{4,32}$").matches(exportPassword)
                        if (!ok) {
                            exportPasswordError = context.getString(R.string.password_validation_error)
                            return@TextButton
                        }
                    }
                    showExportPasswordDialog = false
                    // Start export
                    scope.launch {
                        showExportProgress = true
                        try {
                            val uri = viewModel.exportMemos(context, exportPassword.takeIf { it.isNotBlank() })
                            if (uri == null) {
                                exportFailureMessage = context.getString(R.string.settings_export_result_failure)
                            } else {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    type = "application/zip"
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                val chooser = Intent.createChooser(sendIntent, null).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try {
                                    context.startActivity(chooser)
                                } catch (_: Exception) {
                                    exportFailureMessage = context.getString(R.string.settings_export_result_failure)
                                }
                            }
                        } catch (e: Exception) {
                            exportFailureMessage = context.getString(R.string.settings_export_result_failure)
                        } finally {
                            showExportProgress = false
                        }
                    }
                }) { Text(stringResource(R.string.dialog_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showExportPasswordDialog = false }) { Text(stringResource(R.string.dialog_cancel)) }
            },
            title = { Text(text = stringResource(R.string.settings_export_title)) },
            text = {
                Column {
                    // Show only the password explanation in the export confirmation dialog
                    Text(text = stringResource(R.string.export_password_explain))
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = exportPassword,
                        onValueChange = { new ->
                            // Allow only ASCII alphanumeric
                            val filtered = new.filter { it.isLetterOrDigit() }
                            exportPassword = if (filtered.length > 32) filtered.take(32) else filtered
                            exportPasswordError = null
                        },
                        placeholder = { Text(text = stringResource(R.string.export_password_placeholder)) },
                        singleLine = true
                    )
                    exportPasswordError?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
                }
            }
        )
    }

    // Import password dialog: shown when import indicates password required
    if (showImportPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showImportPasswordDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    // Validate input
                    val ok = Regex("^[A-Za-z0-9]{4,32}$").matches(importPassword)
                    if (!ok) {
                        importPasswordError = context.getString(R.string.password_validation_error)
                        return@TextButton
                    }
                    // Try import with password
                    showImportPasswordDialog = false
                    pendingImportUri?.let { uri ->
                        scope.launch {
                            showImportProgress = true
                            try {
                                val res = viewModel.importMemos(uri, context, importPassword)
                                // Map known messages to localized strings like above
                                val mapped = when (res.message) {
                                    null -> null
                                    "Invalid password or corrupted file" -> context.getString(R.string.settings_import_result_bad_password)
                                    else -> {
                                        val msg = res.message
                                        when {
                                            msg.contains("Cannot open input stream", ignoreCase = true) -> context.getString(R.string.settings_import_result_open_failed)
                                            msg.contains("meta.json not found", ignoreCase = true) -> context.getString(R.string.settings_import_result_no_meta)
                                            else -> context.getString(R.string.settings_import_result_failure)
                                        }
                                    }
                                }
                                importResult = ImportResult(res.added, res.skipped, res.errors, mapped)
                            } catch (_: Exception) {
                                importResult = ImportResult(0, 0, 1, context.getString(R.string.settings_import_result_failure))
                            } finally {
                                showImportProgress = false
                                pendingImportUri = null
                            }
                        }
                    }
                }) { Text(stringResource(R.string.dialog_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showImportPasswordDialog = false }) { Text(stringResource(R.string.dialog_cancel)) }
            },
            title = { Text(text = stringResource(R.string.settings_import_title)) },
            text = {
                Column {
                    Text(text = stringResource(R.string.import_password_prompt))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importPassword,
                        onValueChange = { new ->
                            val filtered = new.filter { it.isLetterOrDigit() }
                            importPassword = if (filtered.length > 32) filtered.take(32) else filtered
                            importPasswordError = null
                        },
                        placeholder = { Text(text = stringResource(R.string.export_password_placeholder)) },
                        singleLine = true
                    )
                    importPasswordError?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
                }
            }
        )
    }

    // Import progress dialog
    if (showImportProgress) {
        AlertDialog(
            onDismissRequest = { /* non-cancelable while processing */ },
            confirmButton = {},
            title = { Text(text = stringResource(R.string.dialog_progress_processing)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = stringResource(R.string.dialog_progress_processing))
                }
            }
        )
    }

    // Import result dialog
    importResult?.let { res ->
        AlertDialog(
            onDismissRequest = { importResult = null },
            confirmButton = {
                TextButton(onClick = { importResult = null }) { Text(stringResource(R.string.dialog_close)) }
            },
            title = { Text(text = stringResource(R.string.settings_import_title)) },
            text = {
                if (res.errors > 0) {
                    Text(text = res.message ?: stringResource(R.string.settings_import_result_failure))
                } else {
                    // Show a concise success message to avoid confusion: "Imported X memos."
                    Text(text = stringResource(R.string.settings_import_result_simple, res.added))
                }
            }
        )
    }

    // Export failure dialog
    exportFailureMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { exportFailureMessage = null },
            confirmButton = {
                TextButton(onClick = { exportFailureMessage = null }) { Text(stringResource(R.string.dialog_close)) }
            },
            title = { Text(text = stringResource(R.string.settings_export_title)) },
            text = { Text(text = msg) }
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

                val isInPreview = LocalInspectionMode.current
                val aboutDialogContext = LocalContext.current

                // Log analytics event once per AboutDialog composition (Preview-safe)
                LaunchedEffect(Unit) {
                    if (!isInPreview) {
                        com.machi.memoiz.analytics.AnalyticsManager.logAboutThanksView(aboutDialogContext)
                    }
                }

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
    // Use an inline fake view model so the preview runs the real SettingsScreen composable code path.
    val fakeVm = remember {
        object : SettingsScreenViewModel {
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

            override suspend fun exportMemos(context: Context, password: String?): Uri? {
                return null
            }

            override suspend fun importMemos(uri: Uri, context: Context, password: String?): ImportResult {
                return ImportResult(0, 0, 0, null)
            }
        }
    }
    MemoizTheme {
        Surface {
            SettingsScreen(viewModel = fakeVm, onNavigateBack = {})
        }
    }
}
