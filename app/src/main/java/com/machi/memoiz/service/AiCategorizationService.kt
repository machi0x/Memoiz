package com.machi.memoiz.service

import android.content.Context
import com.machi.memoiz.domain.model.CategorizationResult
import com.machi.memoiz.domain.model.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AI-only Categorization Service.
 * Uses MlKitCategorizer for all categorization steps. If AI fails at any point,
 * the item is assigned to the special "Failure" category to be reprocessed later.
 */
class AiCategorizationService(private val context: Context) {

    private val mlKitCategorizer = MlKitCategorizer(context)

    /**
     * Call this when the service is no longer needed to release ML Kit resources.
     */
    fun close() {
        mlKitCategorizer.close()
    }

    /**
     * Performs AI-first categorization.
     * Stage 1: free-form category generation by AI.
     * Stage 2: if possible, merge into an existing user category; otherwise use the new category.
     * On AI total failure, returns finalCategoryName = "Failure".
     */
    suspend fun categorizeContent(
        content: String,
        customCategories: List<Category>,
        favoriteCategories: List<Category>,
        sourceApp: String? = null
    ): CategorizationResult = withContext(Dispatchers.Default) {

        try {
            // 1) First-stage: ask ML Kit for localized labels (en/ja)
            val localized = try {
                mlKitCategorizer.categorizeLocalized(content, sourceApp)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }

            val enLabel = localized?.first
            val jaLabel = localized?.second

            if (enLabel.isNullOrBlank() && jaLabel.isNullOrBlank()) {
                return@withContext CategorizationResult(
                    finalCategoryName = "FAILURE",
                    originalCategory = "FAILURE",
                    subCategory = null,
                    finalCategoryNameEn = null,
                    finalCategoryNameJa = null
                )
            }

            // choose an originalCategory string for logging: prefer English then Japanese
            val originalCategory = enLabel ?: jaLabel ?: ""

            // 2) Sub-category (optional) - ask model using chosen originalCategory
            val subCategory = try {
                mlKitCategorizer.generateSubCategory(content, originalCategory, sourceApp)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }

            // 3) Stage-2 merge: prepare candidate list and ask conservative matcher
            val candidates = (customCategories.map { it.name } + favoriteCategories.map { it.name }).distinct()
            val mergeTarget: String? = if (candidates.isNotEmpty()) {
                try {
                    mlKitCategorizer.matchCategoryToListLocalized(content, enLabel, jaLabel, candidates)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            } else null

            // If merge target exists, finalCategoryName = mergeTarget, else use the new label (prefer en, then ja)
            val finalCategory = mergeTarget ?: (enLabel ?: jaLabel ?: "FAILURE")

            return@withContext CategorizationResult(
                finalCategoryName = finalCategory,
                originalCategory = originalCategory,
                subCategory = subCategory,
                finalCategoryNameEn = enLabel,
                finalCategoryNameJa = jaLabel
            )

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext CategorizationResult(
                finalCategoryName = "FAILURE",
                originalCategory = "FAILURE",
                subCategory = null,
                finalCategoryNameEn = null,
                finalCategoryNameJa = null
            )
        }
    }
}
