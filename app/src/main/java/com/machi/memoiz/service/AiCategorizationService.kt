package com.machi.memoiz.service

import android.content.Context
import com.machi.memoiz.data.entity.MemoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AI Categorization Service.
 * A wrapper around MlKitCategorizer to process content and return a MemoEntity.
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
     * Processes text content, gets a category from the AI, and builds a MemoEntity.
     */
    suspend fun processText(content: String, sourceApp: String?): MemoEntity? = withContext(Dispatchers.Default) {
        try {
            val (category, subCategory, summary) = mlKitCategorizer.categorize(content, sourceApp) ?: return@withContext null
            MemoEntity(
                content = content,
                category = category ?: "Uncategorized",
                subCategory = subCategory,
                summary = summary,
                sourceApp = sourceApp
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Processes an image, gets a description from the AI, and builds a MemoEntity.
     */
    suspend fun processImage(bitmap: android.graphics.Bitmap, sourceApp: String?): MemoEntity? = withContext(Dispatchers.Default) {
        try {
            val (category, subCategory, summary) = mlKitCategorizer.categorizeImage(bitmap, sourceApp) ?: return@withContext null
            MemoEntity(
                content = "", // No text content for images
                imageUri = "", // URI will be set in ProcessTextActivity
                category = category ?: "Image",
                subCategory = subCategory,
                summary = summary,
                sourceApp = sourceApp
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
