package com.machi.memoiz.service

import com.machi.memoiz.R
import com.machi.memoiz.data.MemoizDatabase
import com.machi.memoiz.data.repository.MemoRepository
import com.machi.memoiz.data.datastore.PreferencesDataStoreManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.machi.memoiz.data.entity.MemoEntity
import com.machi.memoiz.util.FailureCategoryHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

/**
 * AI Categorization Service.
 * A wrapper around MlKitCategorizer to process content and return a MemoEntity.
 */
class AiCategorizationService(
    private val context: Context,
    private val mergeService: CategoryMergeService = CategoryMergeService(context),
    private val existingCategories: List<String> = emptyList(),
    private val customCategories: Set<String> = emptySet(),
    private val isCategoryLocked: Boolean = false,
    private val summarizationOnlyMode: Boolean = false
) {
    private val uncategorizableLabel by lazy { context.getString(R.string.category_uncategorizable) }

    companion object {
        suspend fun createWithRepository(context: Context, isCategoryLocked: Boolean = false): AiCategorizationService {
            val database = MemoizDatabase.getDatabase(context)
            val memoRepository = MemoRepository(database.memoDao())
            val preferences = PreferencesDataStoreManager(context).userPreferencesFlow.first()
            val existing = memoRepository.getDistinctCategories()
            return AiCategorizationService(
                context,
                CategoryMergeService(context),
                existing,
                preferences.customCategories,
                isCategoryLocked,
                summarizationOnlyMode = false // force-off flags removed
            )
        }
    }

    private val mlKitCategorizer = MlKitCategorizer(context, summarizationOnlyMode)

    private suspend fun mergeCategory(category: String, subCategory: String?, summary: String?): String {
        if (isCategoryLocked || category.isBlank() || shouldSkipMerge(category) || customCategories.contains(category)) {
            return category
        }
        if (existingCategories.isEmpty() && customCategories.isEmpty()) return category
        val result = mergeService.merge(
            CategoryMergeService.MergeInput(
                aiCategory = category,
                aiSubCategory = subCategory,
                existingCategories = existingCategories,
                customCategories = customCategories,
                memoSummary = summary
            )
        )
        return if (shouldSkipMerge(result.finalCategory)) category else result.finalCategory
    }

    private fun shouldSkipMerge(category: String): Boolean {
        if (FailureCategoryHelper.isFailureLabel(context, category)) return true
        return category.equals(uncategorizableLabel, ignoreCase = true)
    }

    /**
     * Processes text content, gets a category from the AI, and builds a MemoEntity.
     */
    suspend fun processText(content: String, sourceApp: String?): MemoEntity? = withContext(Dispatchers.Default) {
        try {
            // If the incoming text is a shared "title + URL" style blob, extract the URL and use it
            val extractedUrl = SharedTextUtils.extractSharedUrlIfEligible(content)
            val contentForCategorize = extractedUrl ?: content

            val (category, subCategory, summary) = mlKitCategorizer.categorize(contentForCategorize, sourceApp) ?: return@withContext null
            val finalCategory = mergeCategory(category ?: "Uncategorized", subCategory, summary)

            // Determine memo type based on whether we extracted a shared URL; otherwise fallback to previous heuristic
            val memoType = when {
                extractedUrl != null -> com.machi.memoiz.data.entity.MemoType.WEB_SITE
                content.startsWith("http://", ignoreCase = true) ||
                content.startsWith("https://", ignoreCase = true) -> com.machi.memoiz.data.entity.MemoType.WEB_SITE
                else -> com.machi.memoiz.data.entity.MemoType.TEXT
            }

            MemoEntity(
                // Store canonicalized content when we extracted a shared URL (ignore title part)
                content = extractedUrl ?: content,
                memoType = memoType,
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
            val (category, subCategory, summary) = mlKitCategorizer.categorizeImage(bitmap, sourceApp, originalImageUri) ?: return@withContext null
            val finalCategory = mergeCategory(category ?: "Image", subCategory, summary)
            MemoEntity(
                content = summary ?: "", // Store image description in content field
                imageUri = originalImageUri,
                memoType = com.machi.memoiz.data.entity.MemoType.IMAGE,
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
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.isMutableRequired = false
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val maxDimension = maxOf(info.size.width, info.size.height)
                val maxAllowed = 2048
                if (maxDimension > maxAllowed) {
                    val sampleSize = (maxDimension + maxAllowed - 1) / maxAllowed
                    decoder.setTargetSampleSize(sampleSize)
                }
            }
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
