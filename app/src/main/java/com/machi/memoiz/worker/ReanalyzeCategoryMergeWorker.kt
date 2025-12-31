package com.machi.memoiz.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.machi.memoiz.data.MemoizDatabase
import com.machi.memoiz.data.datastore.PreferencesDataStoreManager
import com.machi.memoiz.data.repository.MemoRepository
import com.machi.memoiz.domain.model.Memo
import com.machi.memoiz.service.CategoryMergeService
import kotlinx.coroutines.flow.first

class ReanalyzeCategoryMergeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_TARGET_CATEGORY = "target_category"
        const val KEY_MODE = "mode"
        const val MODE_FULL = "full"
        const val MODE_MERGE_ONLY = "merge_only"
    }

    override suspend fun doWork(): Result {
        val database = MemoizDatabase.getDatabase(applicationContext)
        val memoRepository = MemoRepository(database.memoDao())
        val targetCategory = inputData.getString(KEY_TARGET_CATEGORY)
        val mode = inputData.getString(KEY_MODE) ?: MODE_FULL

        val preferences = PreferencesDataStoreManager(applicationContext).userPreferencesFlow.first()
        val existingCategories = memoRepository.getDistinctCategories()
        val mergeService = CategoryMergeService(applicationContext)

        val memosToProcess: List<Memo> = when {
            targetCategory.isNullOrBlank() -> memoRepository.getAllMemosImmediate()
            else -> memoRepository.getMemosByCategoryImmediate(targetCategory)
        }

        return try {
            memosToProcess.forEach { memo ->
                val input = CategoryMergeService.MergeInput(
                    aiCategory = memo.category,
                    aiSubCategory = memo.subCategory,
                    existingCategories = existingCategories,
                    customCategories = preferences.customCategories
                )
                val mergeResult = mergeService.merge(input)
                if (mergeResult.finalCategory != memo.category) {
                    memoRepository.updateMemo(memo.copy(category = mergeResult.finalCategory))
                }
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        } finally {
            mergeService.close()
        }
    }
}

