package com.machi.memoiz.service

import android.app.Application
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
import com.machi.memoiz.data.datastore.PreferencesDataStoreManager
import com.machi.memoiz.notification.GenAiStatusNotification
import com.machi.memoiz.ui.ProcessingDialogActivity
import com.machi.memoiz.ui.dialog.GenAiStatusCheckDialogActivity
import com.machi.memoiz.worker.ClipboardProcessingWorker
import com.machi.memoiz.worker.ReanalyzeCategoryMergeWorker
import com.machi.memoiz.worker.ReanalyzeFailedMemosWorker
import com.machi.memoiz.worker.ReanalyzeSingleMemoWorker
import com.machi.memoiz.worker.WORK_TAG_MEMO_PROCESSING
import com.google.mlkit.genai.common.FeatureStatus
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import com.machi.memoiz.data.MemoizDatabase
import com.machi.memoiz.data.repository.MemoRepository

/**
 * Helper functions to enqueue clipboard categorization work from various entry points.
 */
object ContentProcessingLauncher {

    sealed class EnqueueResult {
        object Enqueued : EnqueueResult()
        object NothingToCategorize : EnqueueResult()
        object DuplicateIgnored : EnqueueResult()
    }

    private suspend fun checkGenAiAndMaybeNotify(context: Context, showDialogOnUnavailable: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            // Force-off flags removed — always perform an unforced real check
            val manager = GenAiStatusManager(context)
            val status = manager.checkAll()
            val unavailable = status.anyUnavailable()
            if (unavailable) {
                if (showDialogOnUnavailable) {
                    GenAiStatusCheckDialogActivity.start(context)
                } else if (context is Application) {
                    GenAiStatusNotification.showUnavailable(context)
                }
            }
            unavailable
        }
    }

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

    // New: WithResult version for UI callers to inspect duplicate vs nothing-to-categorize
    fun enqueueFromClipboardWithResult(context: Context, showDialog: Boolean = true): EnqueueResult {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboard.primaryClip ?: return EnqueueResult.NothingToCategorize
        if (clipData.itemCount == 0) return EnqueueResult.NothingToCategorize

        val item = clipData.getItemAt(0)
        val text = item.coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }
        val uri = item.uri
        return enqueueWorkWithResult(context, text, uri, showDialog = showDialog)
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
        forceCopyImage: Boolean = false,
        showStatusDialogOnUnavailable: Boolean = false
    ): Boolean {
        return enqueueWorkWithResult(context, text, imageUri, sourceApp, showDialog, forceCopyImage, showStatusDialogOnUnavailable) == EnqueueResult.Enqueued
    }

    // New: WithResult API
    fun enqueueWorkWithResult(
        context: Context,
        text: String?,
        imageUri: Uri?,
        sourceApp: String? = null,
        showDialog: Boolean = true,
        forceCopyImage: Boolean = false,
        showStatusDialogOnUnavailable: Boolean = false
    ): EnqueueResult {
        // Quick validation
        if (text.isNullOrBlank() && imageUri == null) {
            return EnqueueResult.NothingToCategorize
        }

        // Prepare persisted image uri if provided — this is needed both for Work input and for duplicate check
        var persistedUriString: String? = null
        if (imageUri != null) {
            val persistedUri = ImageUriManager.prepareUriForWork(context, imageUri, forceCopyImage)
            persistedUriString = persistedUri?.toString()
        }

        // If nothing after prepare
        if (text.isNullOrBlank() && persistedUriString.isNullOrBlank()) {
            return EnqueueResult.NothingToCategorize
        }

        // Duplicate check: consult DB synchronously to allow immediate UI feedback
        try {
            val db = MemoizDatabase.getDatabase(context.applicationContext)
            val repo = MemoRepository(db.memoDao())
            val duplicateFound = runBlocking {
                if (!persistedUriString.isNullOrBlank()) {
                    repo.getMemoByImageUriImmediate(persistedUriString) != null
                } else if (!text.isNullOrBlank()) {
                    repo.getMemoByContentImmediate(text) != null
                } else {
                    false
                }
            }
            if (duplicateFound) {
                return EnqueueResult.DuplicateIgnored
            }
        } catch (e: Exception) {
            // On DB error, continue with enqueue to avoid blocking user flow
            e.printStackTrace()
        }

        val workData = Data.Builder().apply {
            text?.let { putString(ClipboardProcessingWorker.KEY_CLIPBOARD_CONTENT, it) }
            persistedUriString?.let { putString(ClipboardProcessingWorker.KEY_IMAGE_URI, it) }
            sourceApp?.let { putString(ClipboardProcessingWorker.KEY_SOURCE_APP, it) }
        }.build()
        if (!workData.keyValueMap.containsKey(ClipboardProcessingWorker.KEY_CLIPBOARD_CONTENT)
            && !workData.keyValueMap.containsKey(ClipboardProcessingWorker.KEY_IMAGE_URI)
        ) {
            return EnqueueResult.NothingToCategorize
        }

        val workRequest = OneTimeWorkRequestBuilder<ClipboardProcessingWorker>()
            .setInputData(workData)
            .addTag(WORK_TAG_MEMO_PROCESSING)
            .build()

        // Fire-and-forget GenAI status check to surface notification if unavailable.
        CoroutineScope(Dispatchers.IO).launch {
            checkGenAiAndMaybeNotify(context.applicationContext, showStatusDialogOnUnavailable)
        }

        if (showDialog) {
            ProcessingDialogActivity.start(context.applicationContext)
        }

        WorkManager.getInstance(context.applicationContext).enqueue(workRequest)
        return EnqueueResult.Enqueued
    }

    fun enqueueManualMemo(context: Context, text: String): Boolean {
        return enqueueWork(context, text, null, showDialog = true, showStatusDialogOnUnavailable = true)
    }

    // New: WithResult for manual memo
    fun enqueueManualMemoWithResult(context: Context, text: String): EnqueueResult {
        return enqueueWorkWithResult(context, text, null, showDialog = true, showStatusDialogOnUnavailable = true)
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
}
