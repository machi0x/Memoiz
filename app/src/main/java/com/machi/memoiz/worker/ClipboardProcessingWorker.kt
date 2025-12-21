package com.machi.memoiz.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.machi.memoiz.data.MemoizDatabase
import com.machi.memoiz.data.repository.CategoryRepository
import com.machi.memoiz.data.repository.MemoRepository
import com.machi.memoiz.service.AiCategorizationService
import com.machi.memoiz.service.AiWorkerHelper
import com.machi.memoiz.service.MlKitCategorizer
import com.machi.memoiz.domain.model.Memo
import com.machi.memoiz.util.UsageStatsHelper
import kotlinx.coroutines.flow.first

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
    }

    override suspend fun doWork(): Result {
        try {
            val content = inputData.getString(KEY_CLIPBOARD_CONTENT)
            val imageUri = inputData.getString(KEY_IMAGE_URI)

            if (content.isNullOrBlank() && imageUri.isNullOrBlank()) {
                return Result.failure()
            }

            // Initialize database and repositories
            val database = MemoizDatabase.getDatabase(applicationContext)
            val categoryRepository = CategoryRepository(database.categoryDao())
            val memoRepository = MemoRepository(database.memoDao())
            val aiService = AiCategorizationService(applicationContext)
            val mlKit = MlKitCategorizer(applicationContext)

            // Try to get source app (UsageStats permission is checked inside UsageStatsHelper)
            val sourceApp = try {
                UsageStatsHelper(applicationContext).getLastForegroundApp()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }

            // Prepare content for categorization (uses image description when needed)
            val contentForCategorization = AiWorkerHelper.prepareContentForCategorization(content, imageUri, mlKit)

            // Perform AI categorization with user's categories
            val categorizationResult = AiWorkerHelper.categorizeWithUserCategories(
                contentForCategorization,
                categoryRepository,
                aiService,
                sourceApp
            )

            // If categorization failed (null or finalCategory == FAILURE), mark as Failure
            val finalCategoryName = categorizationResult?.finalCategoryName ?: "FAILURE"

            // Find or create the category, passing localized labels when available
            val categoryId = if (finalCategoryName == "FAILURE") {
                categoryRepository.getOrCreateFailureCategory(applicationContext)
            } else {
                categoryRepository.findOrCreateCategory(
                    finalCategoryName,
                    isCustom = false,
                    nameEn = categorizationResult?.finalCategoryNameEn,
                    nameJa = categorizationResult?.finalCategoryNameJa
                )
            }

            // Create and save the memo with sub-category and source app
            val memo = Memo(
                content = content ?: "",
                imageUri = imageUri,
                categoryId = categoryId,
                originalCategory = categorizationResult?.originalCategory,
                subCategory = categorizationResult?.subCategory,
                sourceApp = sourceApp
            )

            memoRepository.insertMemo(memo)

            return Result.success()

        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }
    }
}
