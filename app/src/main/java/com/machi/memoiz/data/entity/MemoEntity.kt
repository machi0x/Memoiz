package com.machi.memoiz.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Memo entity for Room database.
 * Represents a memo with its content and associated category.
 *
 * @param id Unique identifier for the memo
 * @param content Text content of the memo
 * @param imageUri URI for image content if applicable
 * @param memoType Type of memo: TEXT, WEB_SITE, or IMAGE
 * @param category The AI-generated category name
 * @param subCategory AI-generated sub-category for additional context
 * @param summary AI-generated summary for long text or web pages
 * @param sourceApp Name of the app from which content was sourced
 * @param createdAt Timestamp when memo was created
 */
@Entity(tableName = "memos")
data class MemoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,
    val imageUri: String? = null,
    val memoType: String = MemoType.TEXT,
    val category: String,
    val subCategory: String? = null,
    val summary: String? = null,
    val sourceApp: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isCategoryLocked: Boolean = false,
    val usageCount: Int = 0
)

/**
 * Constants for memo types.
 */
object MemoType {
    const val TEXT = "TEXT"
    const val WEB_SITE = "WEB_SITE"
    const val IMAGE = "IMAGE"
}
