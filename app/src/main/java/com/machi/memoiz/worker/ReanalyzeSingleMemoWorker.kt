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
import kotlinx.coroutines.flow.first

/**
 * Worker that re-analyzes a single memo specified by memo_id input data.
 * Expects inputData.putLong("memo_id", memoId).
 */
class ReanalyzeSingleMemoWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_MEMO_ID = "memo_id"
    }

    override suspend fun doWork(): Result {
        try {
            val memoId = inputData.getLong(KEY_MEMO_ID, -1L)
            if (memoId <= 0L) return Result.failure()

            val database = MemoizDatabase.getDatabase(applicationContext)
            val categoryRepository = CategoryRepository(database.categoryDao())
            val memoRepository = MemoRepository(database.memoDao())
            val aiService = AiCategorizationService(applicationContext)
            val mlKit = MlKitCategorizer(applicationContext)

            val memo = memoRepository.getMemoById(memoId) ?: return Result.success()

            // Prepare content for categorization
            val contentForCategorization = AiWorkerHelper.prepareContentForCategorization(memo.content, memo.imageUri, mlKit)

            val result = AiWorkerHelper.categorizeWithUserCategories(
                contentForCategorization,
                categoryRepository,
                aiService,
                memo.sourceApp
            )

            if (result?.finalCategoryName != null && result.finalCategoryName != "FAILURE") {
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

            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }
    }
}
