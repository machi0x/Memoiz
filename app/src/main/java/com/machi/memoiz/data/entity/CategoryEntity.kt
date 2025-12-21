package com.machi.memoiz.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Category entity for Room database.
 * Represents a category that can be used to organize memos.
 * 
 * @param id Unique identifier for the category
 * @param name Name of the category
 * @param isFavorite Whether the category is marked as favorite by user
 * @param isCustom Whether this is a user-defined "My Category" (max 20)
 * @param createdAt Timestamp when category was created
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "name_en")
    val nameEn: String? = null,
    @ColumnInfo(name = "name_ja")
    val nameJa: String? = null,
    val isFavorite: Boolean = false,
    val isCustom: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
