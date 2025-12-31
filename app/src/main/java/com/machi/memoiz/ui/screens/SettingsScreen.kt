@file:OptIn(ExperimentalMaterial3Api::class)

package com.machi.memoiz.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Description
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import com.machi.memoiz.BuildConfig
import com.machi.memoiz.R
import com.machi.memoiz.ui.theme.MemoizTheme
import com.machi.memoiz.util.UsageStatsHelper

/**
 * Settings screen for app configuration.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    var showAboutDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasUsageStatsPermission by remember {
        mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) UsageStatsHelper(context).hasUsageStatsPermission() else false)
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                hasUsageStatsPermission = UsageStatsHelper(context).hasUsageStatsPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val appVersion = remember { BuildConfig.VERSION_NAME }

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
    onClick: (() -> Unit)? = null
) {
    val supporting: (@Composable () -> Unit)? = if (subtitle != null || extraContent != null) {
        {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
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
    MemoizTheme {
        Surface {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                item {
                    PreferenceItem(
                        title = "Source App Detection",
                        subtitle = "Enabled - AI can see which app you copied from",
                        leadingIcon = {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    )
                }
                item {
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                }
                item {
                    PreferenceItem(
                        title = "About this app",
                        leadingIcon = {
                            Icon(Icons.Default.Info, contentDescription = null)
                        }
                    )
                }
            }
        }
    }
}
