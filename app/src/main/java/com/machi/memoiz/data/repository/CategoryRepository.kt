package com.machi.memoiz.data.repository

import android.content.Context
import com.machi.memoiz.data.dao.CategoryDao
import com.machi.memoiz.data.entity.CategoryEntity
import com.machi.memoiz.domain.model.Category
import com.machi.memoiz.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for Category operations.
 * Handles data operations and mapping between entity and domain models.
 */
class CategoryRepository(private val categoryDao: CategoryDao) {
    
    fun getAllCategories(): Flow<List<Category>> {
        return categoryDao.getAllCategories().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    suspend fun getCategoryById(categoryId: Long): Category? {
        return categoryDao.getCategoryById(categoryId)?.toDomain()
    }
    
    suspend fun getCategoryByName(name: String): Category? {
        return categoryDao.getCategoryByName(name)?.toDomain()
    }
    
    fun getCustomCategories(): Flow<List<Category>> {
        return categoryDao.getCustomCategories().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    fun getFavoriteCategories(): Flow<List<Category>> {
        return categoryDao.getFavoriteCategories().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    suspend fun getCustomCategoryCount(): Int {
        return categoryDao.getCustomCategoryCount()
    }
    
    suspend fun insertCategory(category: Category): Long {
        return categoryDao.insertCategory(category.toEntity())
    }
    
    suspend fun updateCategory(category: Category) {
        categoryDao.updateCategory(category.toEntity())
    }
    
    suspend fun deleteCategory(category: Category) {
        categoryDao.deleteCategory(category.toEntity())
    }
    
    suspend fun toggleFavorite(category: Category) {
        val updated = category.copy(isFavorite = !category.isFavorite)
        categoryDao.updateCategory(updated.toEntity())
    }
    
    /**
     * Finds or creates a category by name.
     * Overloaded: if nameEn/nameJa provided, use them when creating.
     */
    suspend fun findOrCreateCategory(name: String, isCustom: Boolean = false, nameEn: String? = null, nameJa: String? = null): Long {
        val existing = categoryDao.getCategoryByName(name)
        return if (existing != null) {
            existing.id
        } else {
            categoryDao.insertCategory(
                CategoryEntity(
                    name = name,
                    nameEn = nameEn,
                    nameJa = nameJa,
                    isCustom = isCustom
                )
            )
        }
    }

    /**
     * Ensure the special Failure category exists; return its categoryId.
     */
    suspend fun getOrCreateFailureCategory(context: Context): Long {
        val localizedLabel = context.getString(R.string.failure_category)
        // Try multiple name forms including canonical key
        val candidates = listOf("FAILURE", localizedLabel)
        for (name in candidates) {
            val existing = categoryDao.getCategoryByName(name)
            if (existing != null) return existing.id
        }
        // Not found => create with canonical key "FAILURE" and localized label
        return categoryDao.insertCategory(
            CategoryEntity(
                name = "FAILURE",
                nameEn = localizedLabel,
                nameJa = localizedLabel,
                isCustom = false
            )
        )
    }

    private fun CategoryEntity.toDomain(): Category {
        return Category(
            id = id,
            name = name,
            nameEn = nameEn,
            nameJa = nameJa,
            isFavorite = isFavorite,
            isCustom = isCustom,
            createdAt = createdAt
        )
    }
    
    private fun Category.toEntity(): CategoryEntity {
        return CategoryEntity(
            id = id,
            name = name,
            nameEn = nameEn,
            nameJa = nameJa,
            isFavorite = isFavorite,
            isCustom = isCustom,
            createdAt = createdAt
        )
    }
}
