package com.machika.memoiz.service

import android.content.Context
import com.machika.memoiz.domain.model.CategorizationResult
import com.machika.memoiz.domain.model.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AI Categorization Service.
 * Implements 2-stage categorization:
 * 1. First stage: Free categorization by AI
 * 2. Second stage: Merge with custom/favorite categories if possible
 * 
 * Note: This is a placeholder implementation. In production, this would use
 * Gemini Nano / AICore for on-device AI processing.
 */
class AiCategorizationService(private val context: Context) {
    
    /**
     * Performs 2-stage categorization on the given content.
     * 
     * @param content The text content to categorize
     * @param customCategories User's custom categories (max 20)
     * @param favoriteCategories User's favorite categories
     * @return CategorizationResult with final category and original category
     */
    suspend fun categorizeContent(
        content: String,
        customCategories: List<Category>,
        favoriteCategories: List<Category>
    ): CategorizationResult = withContext(Dispatchers.Default) {
        
        // Stage 1: Free categorization
        val firstStageCategory = performFirstStageCategorization(content)
        
        // Stage 2: Merge with custom/favorite categories
        val finalCategory = performSecondStageMerge(
            firstStageCategory,
            customCategories,
            favoriteCategories
        )
        
        return@withContext CategorizationResult(
            finalCategoryName = finalCategory,
            originalCategory = firstStageCategory
        )
    }
    
    /**
     * First stage: AI performs free categorization based on content.
     * 
     * TODO: Integrate with Gemini Nano / AICore
     * Integration Plan:
     * 1. Add Google AI Core dependency when available in stable release
     * 2. Initialize AICore client in MemoizApplication.onCreate()
     * 3. Replace rule-based logic below with Gemini Nano API call:
     *    - Use generateText() with categorization prompt
     *    - Handle model availability checking
     *    - Implement fallback to rule-based for devices without AI support
     * 4. Expected Timeline: Q2 2024 when Gemini Nano APIs stabilize
     * 
     * For now, using rule-based categorization as placeholder.
     */
    private suspend fun performFirstStageCategorization(content: String): String {
        // Placeholder implementation using rule-based categorization
        // This will be replaced with Gemini Nano API
        
        val contentLower = content.lowercase()
        
        return when {
            contentLower.contains("http") || contentLower.contains("www") -> "Links"
            contentLower.contains("todo") || contentLower.contains("task") -> "Tasks"
            contentLower.contains("meeting") || contentLower.contains("schedule") -> "Meetings"
            contentLower.contains("code") || contentLower.contains("programming") -> "Code"
            contentLower.matches(Regex(".*\\d{4}-\\d{2}-\\d{2}.*")) -> "Dates"
            contentLower.contains("buy") || contentLower.contains("shop") -> "Shopping"
            contentLower.contains("recipe") || contentLower.contains("cooking") -> "Recipes"
            contentLower.contains("book") || contentLower.contains("read") -> "Reading"
            contentLower.length < 50 -> "Quick Notes"
            else -> "General"
        }
    }
    
    /**
     * Second stage: Try to merge first stage result with custom/favorite categories.
     * AI respects user preferences by checking if the first stage category 
     * can be merged with existing custom or favorite categories.
     */
    private suspend fun performSecondStageMerge(
        firstStageCategory: String,
        customCategories: List<Category>,
        favoriteCategories: List<Category>
    ): String {
        // TODO: Use AI (Gemini Nano) for semantic matching in production
        // For now, use simple string matching
        
        // Check if first stage category matches any custom category
        customCategories.forEach { customCat ->
            if (isSimilarCategory(firstStageCategory, customCat.name)) {
                return customCat.name
            }
        }
        
        // Check if first stage category matches any favorite category
        favoriteCategories.forEach { favCat ->
            if (isSimilarCategory(firstStageCategory, favCat.name)) {
                return favCat.name
            }
        }
        
        // No match found, use first stage category
        return firstStageCategory
    }
    
    /**
     * Checks if two category names are similar enough to merge.
     * In production, this would use AI semantic similarity.
     */
    private fun isSimilarCategory(category1: String, category2: String): Boolean {
        val c1 = category1.lowercase().trim()
        val c2 = category2.lowercase().trim()
        
        // Exact match
        if (c1 == c2) return true
        
        // One contains the other
        if (c1.contains(c2) || c2.contains(c1)) return true
        
        // Similar keywords (simple approach)
        val keywords1 = c1.split(" ", "-", "_")
        val keywords2 = c2.split(" ", "-", "_")
        val commonKeywords = keywords1.intersect(keywords2.toSet())
        
        return commonKeywords.isNotEmpty()
    }
}
