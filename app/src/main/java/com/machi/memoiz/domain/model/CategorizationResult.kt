package com.machi.memoiz.domain.model

/**
 * Result of AI categorization process.
 * Contains the final category and intermediate results.
 */
data class CategorizationResult(
    val finalCategoryName: String,
    val originalCategory: String,
    val confidence: Float = 1.0f
)
