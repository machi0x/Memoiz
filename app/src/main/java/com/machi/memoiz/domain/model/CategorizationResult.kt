package com.machi.memoiz.domain.model

/**
 * Result of AI categorization process.
 * Contains the final category and intermediate results, including localized labels.
 */
data class CategorizationResult(
    val finalCategoryName: String,
    val originalCategory: String,
    val subCategory: String? = null,
    val confidence: Float = 1.0f,
    val finalCategoryNameEn: String? = null,
    val finalCategoryNameJa: String? = null
)
