package com.machi.memoiz.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.machi.memoiz.data.MemoizDatabase
import com.machi.memoiz.data.repository.CategoryRepository
import com.machi.memoiz.data.repository.MemoRepository
import com.machi.memoiz.domain.model.Category
import com.machi.memoiz.domain.model.Memo
import com.machi.memoiz.service.AiCategorizationService
import com.machi.memoiz.service.AiWorkerHelper
import com.machi.memoiz.service.MlKitCategorizer
import kotlinx.coroutines.flow.first

/**
 * Worker that finds memos in the special "Failure" category and re-analyzes them using AI.
 * If AI produces a non-failure category, the memo is updated to the new category.
 */
class ReanalyzeFailedMemosWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            val database = MemoizDatabase.getDatabase(applicationContext)
            val categoryRepository = CategoryRepository(database.categoryDao())
            val memoRepository = MemoRepository(database.memoDao())
            val aiService = AiCategorizationService(applicationContext)
            val mlKit = MlKitCategorizer(applicationContext)

            // Ensure Failure category exists (localized)
            val failureCategoryId = categoryRepository.getOrCreateFailureCategory(applicationContext)

            // Get memos currently assigned to Failure category
            val failedMemos: List<Memo> = memoRepository.getMemosByCategory(failureCategoryId).first()

            if (failedMemos.isEmpty()) return Result.success()

            for (memo in failedMemos) {
                try {
                    val contentForCategorization = AiWorkerHelper.prepareContentForCategorization(memo.content, memo.imageUri, mlKit)

                    val result = AiWorkerHelper.categorizeWithUserCategories(
                        contentForCategorization,
                        categoryRepository,
                        aiService,
                        memo.sourceApp
                    )

                    if (result?.finalCategoryName != null && result.finalCategoryName != "FAILURE") {
                        // Find or create the category and update memo
                        val categoryId = categoryRepository.findOrCreateCategory(
                            result.finalCategoryName,
                            isCustom = false,
                            nameEn = result.finalCategoryNameEn,
                            nameJa = result.finalCategoryNameJa
                        )
                        val updated = memo.copy(
                            categoryId = categoryId,
                            originalCategory = result.originalCategory,
                            subCategory = result.subCategory
                        )
                        memoRepository.updateMemo(updated)
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                    // continue with next memo
                }
            }

            return Result.success()

        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }
    }
}
