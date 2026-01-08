package com.machi.memoiz.processtext

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.machi.memoiz.R
import com.machi.memoiz.service.ContentProcessingLauncher
import com.machi.memoiz.service.determineSourceApp

class ProcessTextActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val handled = when (intent?.action) {
            Intent.ACTION_PROCESS_TEXT -> handleProcessText()
            Intent.ACTION_SEND -> handleSend(intent)
            else -> false
        }

        if (!handled) {
            Toast.makeText(this, R.string.nothing_to_categorize, Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_CANCELED)
        } else {
            setResult(Activity.RESULT_OK)
        }

        finish()
    }

    private fun handleProcessText(): Boolean {
        val text = intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        if (text.isNullOrBlank()) return false

        val sourceApp = determineSourceApp(this)
        // Use WithResult API so we can distinguish DuplicateIgnored and show proper toast
        return when (ContentProcessingLauncher.enqueueWorkWithResult(this, text, null, sourceApp, creationSource = "external_process_text")) {
            ContentProcessingLauncher.EnqueueResult.Enqueued -> true
            ContentProcessingLauncher.EnqueueResult.NothingToCategorize -> false
            ContentProcessingLauncher.EnqueueResult.DuplicateIgnored -> {
                Toast.makeText(this, R.string.toast_already_exists, Toast.LENGTH_SHORT).show()
                true
            }
        }
    }

    private fun handleSend(sendIntent: Intent): Boolean {
        val text = sendIntent.getStringExtra(Intent.EXTRA_TEXT)
        val streamUri = sendIntent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        val sourceApp = determineSourceApp(this)

        return if (streamUri != null && sendIntent.type?.startsWith("image/") == true) {
            when (ContentProcessingLauncher.enqueueWorkWithResult(this, text, streamUri, sourceApp, forceCopyImage = true, creationSource = "external_send")) {
                ContentProcessingLauncher.EnqueueResult.Enqueued -> true
                ContentProcessingLauncher.EnqueueResult.NothingToCategorize -> false
                ContentProcessingLauncher.EnqueueResult.DuplicateIgnored -> {
                    Toast.makeText(this, R.string.toast_already_exists, Toast.LENGTH_SHORT).show()
                    true
                }
            }
        } else if (!text.isNullOrBlank()) {
            when (ContentProcessingLauncher.enqueueWorkWithResult(this, text, null, sourceApp, creationSource = "external_send")) {
                ContentProcessingLauncher.EnqueueResult.Enqueued -> true
                ContentProcessingLauncher.EnqueueResult.NothingToCategorize -> false
                ContentProcessingLauncher.EnqueueResult.DuplicateIgnored -> {
                    Toast.makeText(this, R.string.toast_already_exists, Toast.LENGTH_SHORT).show()
                    true
                }
            }
        } else {
            false
        }
    }
}
