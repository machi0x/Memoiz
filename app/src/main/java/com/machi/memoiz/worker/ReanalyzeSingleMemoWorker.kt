package com.machi.memoiz.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.machi.memoiz.data.MemoizDatabase
import com.machi.memoiz.data.repository.MemoRepository
import com.machi.memoiz.service.AiCategorizationService
import com.machi.memoiz.service.CategoryMergeService
import com.machi.memoiz.data.datastore.PreferencesDataStoreManager
import com.machi.memoiz.util.FailureCategoryHelper
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
        val database = MemoizDatabase.getDatabase(applicationContext)
        val memoRepository = MemoRepository(database.memoDao())
        val existingCategories = memoRepository.getDistinctCategories()
        val preferences = PreferencesDataStoreManager(applicationContext).userPreferencesFlow.first()
        val aiService = AiCategorizationService(
            applicationContext,
            CategoryMergeService(applicationContext),
            existingCategories,
            preferences.customCategories
        )
        try {
            val memoId = inputData.getLong(KEY_MEMO_ID, -1L)
            if (memoId <= 0L) return Result.failure()

            val memo = memoRepository.getMemoById(memoId) ?: return Result.success()

            val updatedEntity = if (!memo.imageUri.isNullOrBlank()) {
                val bitmap = aiService.loadBitmapFromUri(memo.imageUri)
                bitmap?.let { aiService.processImage(it, memo.sourceApp, memo.imageUri) }
            } else {
                aiService.processText(memo.content, memo.sourceApp)
            }

            if (updatedEntity != null) {
                memoRepository.updateMemo(
                    memo.copy(
                        content = updatedEntity.content,
                        imageUri = updatedEntity.imageUri ?: memo.imageUri,
                        memoType = memo.memoType,
                        category = updatedEntity.category,
                        subCategory = updatedEntity.subCategory,
                        summary = updatedEntity.summary,
                        sourceApp = memo.sourceApp
                    )
                )
            }

            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        } finally {
            aiService.close()
        }
    }
}
