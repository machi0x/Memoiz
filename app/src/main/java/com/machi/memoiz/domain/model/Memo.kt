package com.machi.memoiz.domain.model

/**
 * Domain model for Memo.
 * Represents a memo in the business logic layer.
 */
data class Memo(
    val id: Long = 0,
    val content: String,
    val imageUri: String? = null,
    val categoryId: Long,
    val categoryName: String? = null,
    val originalCategory: String? = null,
    val subCategory: String? = null,
    val sourceApp: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
