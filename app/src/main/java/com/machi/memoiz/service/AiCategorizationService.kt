package com.machi.memoiz.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.machi.memoiz.data.entity.MemoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AI Categorization Service.
 * A wrapper around MlKitCategorizer to process content and return a MemoEntity.
 */
class AiCategorizationService(
    private val context: Context,
    private val mergeService: CategoryMergeService = CategoryMergeService(context),
    private val existingCategories: List<String> = emptyList(),
    private val customCategories: Set<String> = emptySet()
) {

    private val mlKitCategorizer = MlKitCategorizer(context)

    private suspend fun mergeCategory(category: String, subCategory: String?): String {
        if (existingCategories.isEmpty() && customCategories.isEmpty()) return category
        val result = mergeService.merge(
            CategoryMergeService.MergeInput(
                aiCategory = category,
                aiSubCategory = subCategory,
                existingCategories = existingCategories,
                customCategories = customCategories
            )
        )
        return result.finalCategory
    }

    /**
     * Processes text content, gets a category from the AI, and builds a MemoEntity.
     */
    suspend fun processText(content: String, sourceApp: String?): MemoEntity? = withContext(Dispatchers.Default) {
        try {
            val (category, subCategory, summary) = mlKitCategorizer.categorize(content, sourceApp) ?: return@withContext null
            val finalCategory = mergeCategory(category ?: "Uncategorized", subCategory)
            MemoEntity(
                content = content,
                category = finalCategory,
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
    suspend fun processImage(bitmap: Bitmap, sourceApp: String?, originalImageUri: String? = null): MemoEntity? = withContext(Dispatchers.Default) {
        try {
            val (category, subCategory, summary) = mlKitCategorizer.categorizeImage(bitmap, sourceApp) ?: return@withContext null
            val finalCategory = mergeCategory(category ?: "Image", subCategory)
            MemoEntity(
                content = "", // No text content for images
                imageUri = originalImageUri,
                category = finalCategory,
                subCategory = subCategory,
                summary = summary,
                sourceApp = sourceApp
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun processImageUri(imageUri: String, sourceApp: String?): MemoEntity? {
        val bitmap = loadBitmapFromUri(imageUri) ?: return null
        return processImage(bitmap, sourceApp, imageUri)
    }

    fun loadBitmapFromUri(imageUri: String?): Bitmap? {
        if (imageUri.isNullOrBlank()) return null
        return try {
            val uri = Uri.parse(imageUri)
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Call this when the service is no longer needed to release ML Kit resources.
     */
    fun close() {
        mlKitCategorizer.close()
        mergeService.close()
    }
}
