package com.machi.memoiz.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Memo entity for Room database.
 * Represents a memo with its content and associated category.
 * 
 * @param id Unique identifier for the memo
 * @param content Text content of the memo (from clipboard)
 * @param imageUri URI for image content if applicable
 * @param categoryId Foreign key to associated category
 * @param originalCategory AI's initial free categorization
 * @param subCategory AI-generated sub-category for additional information
 * @param sourceApp Name of the app from which content was copied (if Usage Stats enabled)
 * @param createdAt Timestamp when memo was created
 */
@Entity(
    tableName = "memos",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["categoryId"])]
)
data class MemoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,
    val imageUri: String? = null,
    val categoryId: Long,
    val originalCategory: String? = null, // AI's first-stage categorization
    val subCategory: String? = null, // AI-generated sub-category
    val sourceApp: String? = null, // App from which content was copied
    val createdAt: Long = System.currentTimeMillis()
)
