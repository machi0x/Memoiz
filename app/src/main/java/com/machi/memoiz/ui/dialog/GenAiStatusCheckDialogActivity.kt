package com.machi.memoiz.ui.dialog

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import com.google.mlkit.genai.common.FeatureStatus
import kotlinx.coroutines.launch

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
                GenAiStatusDialog(manager = manager, forceOff = forceOff, onFinish = { finish() })
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

    AlertDialog(
        onDismissRequest = onFinish,
        confirmButton = {
            if (needsDownload) {
                TextButton(
                    onClick = {
                        // Prevent double clicks and set downloading state inside coroutine so analyzer sees read/write.
                        scope.launch {
                            if (!isDownloading) {
                                isDownloading = true
                                manager.downloadMissing(status!!)
                                onFinish()
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.genai_status_download_start))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onFinish) {
                Text(stringResource(R.string.dialog_close))
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
                    CircularProgressIndicator()
                }
            }
        }
    )
}
