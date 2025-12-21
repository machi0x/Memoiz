package com.machika.memoiz.data.repository

import com.machika.memoiz.data.dao.CategoryDao
import com.machika.memoiz.data.entity.CategoryEntity
import com.machika.memoiz.domain.model.Category
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
     * Used during AI categorization.
     */
    suspend fun findOrCreateCategory(name: String, isCustom: Boolean = false): Long {
        val existing = categoryDao.getCategoryByName(name)
        return if (existing != null) {
            existing.id
        } else {
            categoryDao.insertCategory(
                CategoryEntity(
                    name = name,
                    isCustom = isCustom
                )
            )
        }
    }
    
    private fun CategoryEntity.toDomain(): Category {
        return Category(
            id = id,
            name = name,
            isFavorite = isFavorite,
            isCustom = isCustom,
            createdAt = createdAt
        )
    }
    
    private fun Category.toEntity(): CategoryEntity {
        return CategoryEntity(
            id = id,
            name = name,
            isFavorite = isFavorite,
            isCustom = isCustom,
            createdAt = createdAt
        )
    }
}
