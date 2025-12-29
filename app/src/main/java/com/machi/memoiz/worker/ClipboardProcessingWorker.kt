package com.machi.memoiz.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.machi.memoiz.data.MemoizDatabase
import com.machi.memoiz.data.repository.MemoRepository
import com.machi.memoiz.service.AiCategorizationService
import com.machi.memoiz.domain.model.Memo
import com.machi.memoiz.util.UsageStatsHelper

/**
 * WorkManager worker for processing clipboard content in background.
 * Handles heavy AI categorization without blocking the UI.
 */
class ClipboardProcessingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_CLIPBOARD_CONTENT = "clipboard_content"
        const val KEY_IMAGE_URI = "image_uri"
        const val KEY_SOURCE_APP = "source_app"
    }

    override suspend fun doWork(): Result {
        val aiService = AiCategorizationService(applicationContext)
        try {
            val content = inputData.getString(KEY_CLIPBOARD_CONTENT)
            val imageUri = inputData.getString(KEY_IMAGE_URI)
            val providedSourceApp = inputData.getString(KEY_SOURCE_APP)

            if (content.isNullOrBlank() && imageUri.isNullOrBlank()) {
                return Result.failure()
            }

            // Initialize database and repositories
            val database = MemoizDatabase.getDatabase(applicationContext)
            val memoRepository = MemoRepository(database.memoDao())

            // Try to get source app (UsageStats permission is checked inside UsageStatsHelper)
            val sourceApp = providedSourceApp ?: try {
                UsageStatsHelper(applicationContext).getLastForegroundApp()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }

            val memoEntity = if (imageUri != null) {
                aiService.processImageUri(imageUri, sourceApp)
            } else {
                aiService.processText(content.orEmpty(), sourceApp)
            } ?: return Result.failure()

            memoRepository.insertMemo(
                Memo(
                    content = memoEntity.content,
                    imageUri = memoEntity.imageUri,
                    category = memoEntity.category,
                    subCategory = memoEntity.subCategory,
                    summary = memoEntity.summary,
                    sourceApp = memoEntity.sourceApp,
                    createdAt = memoEntity.createdAt
                )
            )

            return Result.success()

        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        } finally {
            aiService.close()
        }
    }
}
