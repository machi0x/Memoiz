package com.machi.memoiz.domain.model

/**
 * Domain model for Memo.
 * Represents a memo in the business logic layer.
 */
data class Memo(
    val id: Long = 0,
    val content: String,
    val imageUri: String? = null,
    val category: String,
    val subCategory: String? = null,
    val summary: String? = null,
    val sourceApp: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
