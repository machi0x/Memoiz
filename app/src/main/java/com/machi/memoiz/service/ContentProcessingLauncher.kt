package com.machi.memoiz.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.machi.memoiz.ui.ProcessingDialogActivity
import com.machi.memoiz.worker.ClipboardProcessingWorker
import com.machi.memoiz.worker.ReanalyzeCategoryMergeWorker
import com.machi.memoiz.worker.ReanalyzeFailedMemosWorker
import com.machi.memoiz.worker.ReanalyzeSingleMemoWorker
import com.machi.memoiz.worker.WORK_TAG_MEMO_PROCESSING
import java.util.concurrent.TimeUnit

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
        forceCopyImage: Boolean = false
    ): Boolean {
        if (text.isNullOrBlank() && imageUri == null) {
            return false
        }

        val workData = Data.Builder().apply {
            text?.let { putString(ClipboardProcessingWorker.KEY_CLIPBOARD_CONTENT, it) }
            imageUri?.let { original ->
                val persistedUri = ImageUriManager.prepareUriForWork(context, original, forceCopyImage)
                persistedUri?.let { putString(ClipboardProcessingWorker.KEY_IMAGE_URI, it.toString()) }
            }
            sourceApp?.let { putString(ClipboardProcessingWorker.KEY_SOURCE_APP, it) }
        }.build()
        if (!workData.keyValueMap.containsKey(ClipboardProcessingWorker.KEY_CLIPBOARD_CONTENT)
            && !workData.keyValueMap.containsKey(ClipboardProcessingWorker.KEY_IMAGE_URI)
        ) {
            return false
        }

        val workRequest = OneTimeWorkRequestBuilder<ClipboardProcessingWorker>()
            .setInputData(workData)
            .addTag(WORK_TAG_MEMO_PROCESSING)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueue(workRequest)
        if (showDialog) {
            ProcessingDialogActivity.start(context.applicationContext)
        }
        return true
    }

    fun enqueueSingleMemoReanalyze(context: Context, memoId: Long) {
        val data = Data.Builder().putLong(ReanalyzeSingleMemoWorker.KEY_MEMO_ID, memoId).build()
        val request = OneTimeWorkRequestBuilder<ReanalyzeSingleMemoWorker>()
            .setInputData(data)
            .addTag(WORK_TAG_MEMO_PROCESSING)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueue(request)
    }

    private fun failureReanalyzeConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    fun enqueueFailureBatchReanalyze(context: Context) {
        val request = OneTimeWorkRequestBuilder<ReanalyzeFailedMemosWorker>()
            .setConstraints(failureReanalyzeConstraints())
            .addTag(WORK_TAG_MEMO_PROCESSING)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueue(request)
    }

    fun enqueueMergeWork(context: Context, targetCategory: String?) {
        val data = Data.Builder()
            .putString(ReanalyzeCategoryMergeWorker.KEY_TARGET_CATEGORY, targetCategory)
            .build()
        val request = OneTimeWorkRequestBuilder<ReanalyzeCategoryMergeWorker>()
            .setInputData(data)
            .addTag(WORK_TAG_MEMO_PROCESSING)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueue(request)
    }

    fun enqueueManualMemo(context: Context, text: String): Boolean {
        return enqueueWork(context, text, null, showDialog = true)
    }
}
