package com.machi.memoiz.service

import com.machi.memoiz.data.repository.CategoryRepository
import kotlinx.coroutines.flow.first

/**
 * Helper utilities used by WorkManager workers to prepare content and perform AI categorization.
 */
object AiWorkerHelper {

    /**
     * Prepares content to be sent to AI: prefer text content; if missing and imageUri present,
     * call ML Kit Image Description to get a textual description; fallback to "Image" or empty string.
     */
    suspend fun prepareContentForCategorization(
        content: String?,
        imageUri: String?,
        mlKit: MlKitCategorizer
    ): String {
        return if (!content.isNullOrBlank()) {
            content
        } else if (!imageUri.isNullOrBlank()) {
            try {
                mlKit.describeImageUri(imageUri)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            } ?: "Image"
        } else {
            ""
        }
    }

    /**
     * Performs AI categorization using the provided Ai service and the user's custom/favorite categories.
     * The helper fetches custom/favorite categories from the repository and calls AiCategorizationService.
     */
    suspend fun categorizeWithUserCategories(
        contentForCategorization: String,
        categoryRepository: CategoryRepository,
        aiService: AiCategorizationService,
        sourceApp: String? = null
    ) = runCatching {
        val customCategories = categoryRepository.getCustomCategories().first()
        val favoriteCategories = categoryRepository.getFavoriteCategories().first()
        aiService.categorizeContent(
            content = contentForCategorization,
            customCategories = customCategories,
            favoriteCategories = favoriteCategories,
            sourceApp = sourceApp
        )
    }.getOrElse {
        it.printStackTrace()
        null
    }
}
