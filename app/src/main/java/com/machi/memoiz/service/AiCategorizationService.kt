package com.machi.memoiz.service

import android.content.Context
import com.machi.memoiz.domain.model.CategorizationResult
import com.machi.memoiz.domain.model.Category
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
     * @param sourceApp Optional app name from which content was copied
     * @return CategorizationResult with final category, original category, and sub-category
     */
    suspend fun categorizeContent(
        content: String,
        customCategories: List<Category>,
        favoriteCategories: List<Category>,
        sourceApp: String? = null
    ): CategorizationResult = withContext(Dispatchers.Default) {
        
        // Stage 1: Free categorization
        val firstStageCategory = performFirstStageCategorization(content, sourceApp)
        
        // Generate sub-category for additional information
        val subCategory = generateSubCategory(content, firstStageCategory, sourceApp)
        
        // Stage 2: Merge with custom/favorite categories
        val finalCategory = performSecondStageMerge(
            firstStageCategory,
            customCategories,
            favoriteCategories
        )
        
        return@withContext CategorizationResult(
            finalCategoryName = finalCategory,
            originalCategory = firstStageCategory,
            subCategory = subCategory
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
    private suspend fun performFirstStageCategorization(content: String, sourceApp: String? = null): String {
        // Placeholder implementation using rule-based categorization
        // This will be replaced with Gemini Nano API
        
        val contentLower = content.lowercase()
        
        // Consider source app in categorization if available
        val appBasedCategory = sourceApp?.let { getAppBasedCategory(it) }
        if (appBasedCategory != null) {
            return appBasedCategory
        }
        
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
     * Generate sub-category for additional context.
     * AI freely adds this to express additional information.
     */
    private suspend fun generateSubCategory(
        content: String,
        mainCategory: String,
        sourceApp: String? = null
    ): String? {
        // TODO: Use Gemini Nano for intelligent sub-category generation
        // For now, use rule-based approach
        
        val contentLower = content.lowercase()
        
        // If source app is available, use it as sub-category context
        if (sourceApp != null) {
            return sourceApp
        }
        
        // Generate contextual sub-categories based on content
        return when (mainCategory) {
            "Links" -> when {
                contentLower.contains("youtube") || contentLower.contains("video") -> "Video"
                contentLower.contains("github") || contentLower.contains("git") -> "Code Repository"
                contentLower.contains("twitter") || contentLower.contains("social") -> "Social Media"
                contentLower.contains("news") || contentLower.contains("article") -> "Article"
                else -> "Web Link"
            }
            "Tasks" -> when {
                contentLower.contains("urgent") || contentLower.contains("asap") -> "Urgent"
                contentLower.contains("today") -> "Today"
                contentLower.contains("tomorrow") -> "Tomorrow"
                contentLower.contains("week") -> "This Week"
                else -> "To Do"
            }
            "Code" -> when {
                contentLower.contains("function") || contentLower.contains("def ") -> "Function"
                contentLower.contains("class") -> "Class"
                contentLower.contains("import") -> "Import/Dependency"
                contentLower.contains("error") || contentLower.contains("exception") -> "Error/Bug"
                else -> "Snippet"
            }
            "Shopping" -> when {
                contentLower.contains("grocery") || contentLower.contains("food") -> "Groceries"
                contentLower.contains("electronics") -> "Electronics"
                contentLower.contains("clothes") || contentLower.contains("fashion") -> "Clothing"
                else -> "Shopping List"
            }
            else -> null
        }
    }
    
    /**
     * Get category based on source app if Usage Stats is available.
     */
    private fun getAppBasedCategory(sourceApp: String): String? {
        val appLower = sourceApp.lowercase()
        return when {
            appLower.contains("chrome") || appLower.contains("browser") || appLower.contains("firefox") -> "Links"
            appLower.contains("slack") || appLower.contains("teams") || appLower.contains("discord") -> "Work Chat"
            appLower.contains("whatsapp") || appLower.contains("telegram") || appLower.contains("messenger") -> "Messages"
            appLower.contains("gmail") || appLower.contains("mail") || appLower.contains("outlook") -> "Email"
            appLower.contains("notes") || appLower.contains("keep") || appLower.contains("notion") -> "Notes"
            appLower.contains("twitter") || appLower.contains("instagram") || appLower.contains("facebook") -> "Social Media"
            appLower.contains("youtube") -> "Videos"
            appLower.contains("github") || appLower.contains("gitlab") -> "Code"
            else -> null
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
