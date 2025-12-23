package com.machi.memoiz.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.machi.memoiz.data.MemoizDatabase
import com.machi.memoiz.data.repository.CategoryRepository
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
    }

    override suspend fun doWork(): Result {
        val aiService = AiCategorizationService(applicationContext)
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

            // Try to get source app (UsageStats permission is checked inside UsageStatsHelper)
            val sourceApp = try {
                UsageStatsHelper(applicationContext).getLastForegroundApp()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }

            // Prepare content for categorization (uses image description when needed)
            val contentForCategorization = aiService.prepareContentForCategorization(content, imageUri)

            // Perform AI categorization with user's categories
            val categorizationResult = aiService.categorizeWithUserCategories(
                contentForCategorization,
                categoryRepository,
                sourceApp
            )

            // If categorization failed (finalCategory == FAILURE), mark as Failure
            val finalCategoryName = categorizationResult.finalCategoryName

            // Find or create the category, passing localized labels when available
            val categoryId = if (finalCategoryName == "FAILURE") {
                categoryRepository.getOrCreateFailureCategory(applicationContext)
            } else {
                categoryRepository.findOrCreateCategory(
                    finalCategoryName,
                    isCustom = false,
                    nameEn = categorizationResult.finalCategoryNameEn,
                    nameJa = categorizationResult.finalCategoryNameJa
                )
            }

            // Create and save the memo with sub-category and source app
            val memo = Memo(
                content = content ?: "",
                imageUri = imageUri,
                categoryId = categoryId,
                originalCategory = categorizationResult.originalCategory,
                subCategory = categorizationResult.subCategory,
                sourceApp = sourceApp
            )

            memoRepository.insertMemo(memo)

            return Result.success()

        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        } finally {
            aiService.close()
        }
    }
}
