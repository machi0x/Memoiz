package com.machi.memoiz.ui.dialog

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.machi.memoiz.R
import com.machi.memoiz.service.GenAiFeatureStates
import com.machi.memoiz.service.GenAiStatusManager
import com.machi.memoiz.ui.theme.MemoizTheme
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import kotlinx.coroutines.launch
import java.util.Locale

/** Dialog-like Activity that checks GenAI feature status and optionally downloads models, using Jetpack Compose. */
class GenAiStatusCheckDialogActivity : ComponentActivity() {

    private lateinit var manager: GenAiStatusManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = GenAiStatusManager(applicationContext)

        val forceOff = GenAiFeatureStates(
            imageDescription = if (intent.getBooleanExtra(GenAiStatusManager.EXTRA_FORCE_OFF_IMAGE, false)) FeatureStatus.UNAVAILABLE else FeatureStatus.AVAILABLE,
            textGeneration = if (intent.getBooleanExtra(GenAiStatusManager.EXTRA_FORCE_OFF_TEXT, false)) FeatureStatus.UNAVAILABLE else FeatureStatus.AVAILABLE,
            summarization = if (intent.getBooleanExtra(GenAiStatusManager.EXTRA_FORCE_OFF_SUM, false)) FeatureStatus.UNAVAILABLE else FeatureStatus.AVAILABLE
        )

        setContent {
            MemoizTheme {
                // Ensure that when the dialog finishes (user taps Close/back/outside),
                // any in-progress downloads are cancelled before the Activity exits.
                GenAiStatusDialog(manager = manager, forceOff = forceOff, onFinish = {
                    // Cancel any ongoing model downloads to avoid background work
                    // continuing after the UI is dismissed.
                    try { manager.cancelDownloads() } catch (_: Exception) { }
                    finish()
                })
            }
        }
    }

    override fun onDestroy() {
        manager.close()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context, forceOff: Triple<Boolean, Boolean, Boolean>) {
            context.startActivity(Intent(context, GenAiStatusCheckDialogActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(GenAiStatusManager.EXTRA_FORCE_OFF_IMAGE, forceOff.first)
                putExtra(GenAiStatusManager.EXTRA_FORCE_OFF_TEXT, forceOff.second)
                putExtra(GenAiStatusManager.EXTRA_FORCE_OFF_SUM, forceOff.third)
            })
        }
    }
}

@Composable
private fun GenAiStatusDialog(manager: GenAiStatusManager, forceOff: GenAiFeatureStates, onFinish: () -> Unit) {
    var status by remember { mutableStateOf<GenAiFeatureStates?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var totalBytesToDownload by remember { mutableStateOf(0L) }
    var totalBytesDownloaded by remember { mutableStateOf(0L) }
    // We intentionally do not expose raw exception messages to users.
    // For failures we show a friendly localized message (strings) only.
    // Removed unused showError and errorMessage variables to avoid showing raw errors.

    // New state: null = no final result yet, true = success, false = failed/cancelled
    var downloadResult by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val checkedStatus = manager.checkAll(forceOff)
        // If all features are available, just close the dialog.
        if (!checkedStatus.anyDownloadable() && !checkedStatus.anyUnavailable()) {
            onFinish()
        } else {
            status = checkedStatus
        }
    }

    if (status == null) {
        // You can show a loading indicator here if the check is slow,
        // but it's usually fast enough not to need one.
        return
    }

    val needsDownload = status!!.anyDownloadable()
    val anyUnavailable = status!!.anyUnavailable()

    val title: String
    val message: String
    val iconRes: Int

    // If download result exists, show final result screen overriding other states
    if (downloadResult != null) {
        if (downloadResult == true) {
            title = stringResource(R.string.genai_status_title_download)
            message = stringResource(R.string.genai_status_message_download_completed)
            iconRes = R.drawable.model_download
        } else {
            title = stringResource(R.string.genai_status_title_unavailable)
            message = stringResource(R.string.genai_status_message_download_failed_dialog)
            iconRes = R.drawable.model_unavailable
        }
    } else {
        when {
            needsDownload -> {
                title = stringResource(R.string.genai_status_title_download)
                message = stringResource(R.string.genai_status_message_download)
                iconRes = R.drawable.model_download
            }
            anyUnavailable -> {
                title = stringResource(R.string.genai_status_title_unavailable)
                message = stringResource(R.string.genai_status_message_unavailable)
                iconRes = R.drawable.model_unavailable
            }
            else -> {
                // This case should be handled by the LaunchedEffect, but as a fallback:
                onFinish()
                return
            }
        }
    }

    AlertDialog(
        onDismissRequest = onFinish,
        confirmButton = {
            // If we already have a final result, show a single OK button that closes the dialog.
            if (downloadResult != null) {
                TextButton(onClick = onFinish) {
                    Text(stringResource(R.string.dialog_close))
                }
                return@AlertDialog
            }

            // Show the start-download / cancel buttons depending on downloading state.
            val showStartButton = needsDownload && !isDownloading
            val showCancelButton = needsDownload && isDownloading
            if (showStartButton) {
                TextButton(
                    onClick = {
                        // Prevent double clicks and set downloading state inside coroutine so analyzer sees read/write.
                        scope.launch {
                            if (!isDownloading) {
                                isDownloading = true
                                // Provide a DownloadCallback to receive progress updates
                                val callback = object : DownloadCallback {
                                    override fun onDownloadStarted(bytesToDownload: Long) {
                                        scope.launch { totalBytesToDownload = bytesToDownload }
                                    }

                                    override fun onDownloadFailed(e: GenAiException) {
                                        // Show final failed state in the dialog but do not show raw exception text
                                        scope.launch {
                                            isDownloading = false
                                            downloadResult = false
                                            // Clear progress values
                                            totalBytesDownloaded = 0L
                                            totalBytesToDownload = 0L
                                        }
                                    }

                                    override fun onDownloadProgress(totalBytesDownloadedArg: Long) {
                                        // Update state via Compose scope (main thread)
                                        scope.launch { totalBytesDownloaded = totalBytesDownloadedArg }
                                    }

                                    override fun onDownloadCompleted() {
                                        scope.launch {
                                            isDownloading = false
                                            // Ensure progress shows complete
                                            totalBytesDownloaded = totalBytesToDownload
                                            // Show success result in dialog instead of closing immediately
                                            downloadResult = true
                                        }
                                    }
                                }
                                // The manager may also report an overall failure via the throwable callback.
                                // We intentionally do not expose throwable.message to users; show localized message instead.
                                manager.downloadMissing(status!!, callback) { _ ->
                                    scope.launch {
                                        isDownloading = false
                                        downloadResult = false
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.genai_status_download_start))
                }
            } else if (showCancelButton) {
                TextButton(onClick = {
                    // Cancel downloads and update UI to final failed state
                    scope.launch {
                        manager.cancelDownloads()
                        isDownloading = false
                        totalBytesDownloaded = 0L
                        totalBytesToDownload = 0L
                        downloadResult = false
                    }
                }) {
                    Text(stringResource(R.string.genai_status_download_cancel))
                }
             }
         },
        dismissButton = {
            // If we're in final result state, hide the cancel/dismiss; otherwise keep Close button
            if (downloadResult == null) {
                TextButton(onClick = onFinish) {
                    Text(stringResource(R.string.dialog_close))
                }
            }
        },
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(240.dp)
                )
                Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                if (isDownloading) {
                     val progressFraction = if (totalBytesToDownload > 0) totalBytesDownloaded.toDouble() / totalBytesToDownload.toDouble() else 0.0
                     // Determinate bar with an indeterminate overlay to show motion
                     Box(modifier = Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
                         // Determinate background fill
                         LinearProgressIndicator(
                             progress = { progressFraction.toFloat() },
                             modifier = Modifier
                                 .fillMaxWidth()
                                 .height(8.dp)
                         )
                         // Semi-transparent indeterminate overlay to show movement
                         LinearProgressIndicator(
                             modifier = Modifier
                                 .fillMaxWidth()
                                 .height(8.dp),
                             // no progress -> indeterminate animation
                         )
                     }
                     // Show numeric bytes below the bar
                     val downloadedText = if (totalBytesToDownload > 0) {
                         val downloadedMb = totalBytesDownloaded.toDouble() / (1024.0 * 1024.0)
                         val totalMb = totalBytesToDownload.toDouble() / (1024.0 * 1024.0)
                         String.format(Locale.getDefault(), "%.2f MB / %.2f MB (%.1f%%)", downloadedMb, totalMb, 100.0 * downloadedTextSafe(totalBytesDownloaded, totalBytesToDownload))
                     } else {
                         String.format(Locale.getDefault(), "%.2f MB", totalBytesDownloaded.toDouble() / (1024.0 * 1024.0))
                     }
                     Text(downloadedText, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                 }
             }
         }
     )
}

private fun downloadedTextSafe(downloaded: Long, total: Long): Double {
    return if (total > 0) downloaded.toDouble() / total.toDouble() else 0.0
}
