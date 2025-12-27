package com.machi.memoiz.processtext

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.machi.memoiz.R
import com.machi.memoiz.service.ContentProcessingLauncher
import kotlinx.coroutines.launch

/**
 * Handles ACTION_PROCESS_TEXT and ACTION_SEND intents to forward selected text or shared content
 * into Memoiz's background categorization pipeline.
 */
class ProcessTextActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val handled = when (intent?.action) {
                Intent.ACTION_PROCESS_TEXT -> handleProcessText()
                Intent.ACTION_SEND -> handleSend(intent)
                else -> false
            }

            if (!handled) {
                Toast.makeText(this@ProcessTextActivity, R.string.nothing_to_categorize, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@ProcessTextActivity, R.string.categorization_started, Toast.LENGTH_SHORT).show()
            }

            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun handleProcessText(): Boolean {
        val text = intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        return ContentProcessingLauncher.enqueueWork(this, text, null)
    }

    private fun handleSend(sendIntent: Intent): Boolean {
        val text = sendIntent.getStringExtra(Intent.EXTRA_TEXT)
        val streamUri = sendIntent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        return ContentProcessingLauncher.enqueueWork(this, text, streamUri)
    }
}
