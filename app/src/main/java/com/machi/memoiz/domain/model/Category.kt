package com.machi.memoiz.domain.model

/**
 * Domain model for Category.
 * Represents a category in the business logic layer.
 */
data class Category(
    val id: Long = 0,
    val name: String,
    val nameEn: String? = null,
    val nameJa: String? = null,
    val isFavorite: Boolean = false,
    val isCustom: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
