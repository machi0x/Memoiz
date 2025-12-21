package com.machi.memoiz.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import com.machi.memoiz.data.MemoizDatabase
import com.machi.memoiz.data.repository.CategoryRepository
import com.machi.memoiz.data.repository.MemoRepository
import com.machi.memoiz.service.AiCategorizationService
import com.machi.memoiz.domain.model.Memo
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
            
            // Get custom and favorite categories
            val customCategories = categoryRepository.getCustomCategories().first()
            val favoriteCategories = categoryRepository.getFavoriteCategories().first()
            
            // Perform 2-stage AI categorization
            val categorizationResult = aiService.categorizeContent(
                content = content ?: "Image",
                customCategories = customCategories,
                favoriteCategories = favoriteCategories
            )
            
            // Find or create the category
            val categoryId = categoryRepository.findOrCreateCategory(
                name = categorizationResult.finalCategoryName,
                isCustom = false
            )
            
            // Create and save the memo
            val memo = Memo(
                content = content ?: "",
                imageUri = imageUri,
                categoryId = categoryId,
                originalCategory = categorizationResult.originalCategory
            )
            
            memoRepository.insertMemo(memo)
            
            return Result.success()
            
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }
    }
}
