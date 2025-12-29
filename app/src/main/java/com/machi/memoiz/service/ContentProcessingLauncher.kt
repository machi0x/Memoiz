package com.machi.memoiz.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.machi.memoiz.ui.ProcessingDialogActivity
import com.machi.memoiz.worker.ClipboardProcessingWorker

/**
 * Helper functions to enqueue clipboard categorization work from various entry points.
 */
object ContentProcessingLauncher {

    /**
     * Reads the current clipboard content and enqueues background categorization if possible.
     * @return true when work was enqueued, false if clipboard was empty.
     */
    fun enqueueFromClipboard(context: Context, showDialog: Boolean = true): Boolean {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboard.primaryClip ?: return false
        if (clipData.itemCount == 0) return false

        val item = clipData.getItemAt(0)
        val text = item.coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }
        val uri = item.uri
        return enqueueWork(context, text, uri, showDialog = showDialog)
    }

    /**
     * Enqueues a single categorization work request using provided text or image Uri.
     */
    fun enqueueWork(
        context: Context,
        text: String?,
        imageUri: Uri?,
        sourceApp: String? = null,
        showDialog: Boolean = true,
    ): Boolean {
        if (text.isNullOrBlank() && imageUri == null) {
            return false
        }

        val workData = Data.Builder().apply {
            text?.let { putString(ClipboardProcessingWorker.KEY_CLIPBOARD_CONTENT, it) }
            imageUri?.let { putString(ClipboardProcessingWorker.KEY_IMAGE_URI, it.toString()) }
            sourceApp?.let { putString(ClipboardProcessingWorker.KEY_SOURCE_APP, it) }
        }.build()

        val workRequest = OneTimeWorkRequestBuilder<ClipboardProcessingWorker>()
            .setInputData(workData)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueue(workRequest)
        if (showDialog) {
            ProcessingDialogActivity.start(context.applicationContext)
        }
        return true
    }
}
