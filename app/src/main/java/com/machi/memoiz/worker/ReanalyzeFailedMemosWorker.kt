package com.machi.memoiz.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.machi.memoiz.data.MemoizDatabase
import com.machi.memoiz.data.datastore.PreferencesDataStoreManager
import com.machi.memoiz.data.repository.MemoRepository
import com.machi.memoiz.service.AiCategorizationService
import com.machi.memoiz.service.CategoryMergeService
import com.machi.memoiz.util.FailureCategoryHelper
import com.machi.memoiz.worker.WORK_TAG_MEMO_PROCESSING
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
        val failureAliases = FailureCategoryHelper.aliases(applicationContext)
        try {
            val failedMemos = memoRepository.getMemosByCategories(failureAliases)

            if (failedMemos.isEmpty()) return Result.success()

            for (memo in failedMemos) {
                try {
                    val updatedEntity = if (!memo.imageUri.isNullOrBlank()) {
                        val bitmap = aiService.loadBitmapFromUri(memo.imageUri)
                        bitmap?.let { aiService.processImage(it, memo.sourceApp, memo.imageUri) }
                    } else {
                        aiService.processText(memo.content, memo.sourceApp)
                    }

                    if (updatedEntity != null && !FailureCategoryHelper.isFailureLabel(applicationContext, updatedEntity.category)) {
                        memoRepository.updateMemo(memo.copy(
                            content = updatedEntity.content,
                            imageUri = updatedEntity.imageUri,
                            memoType = updatedEntity.memoType,
                            category = updatedEntity.category,
                            subCategory = updatedEntity.subCategory,
                            summary = updatedEntity.summary
                        ))
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
        } finally {
            aiService.close()
        }
    }
}
