package com.machi.memoiz.data.dao

import androidx.room.*
import com.machi.memoiz.data.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Category operations.
 * Provides methods to interact with the categories table.
 */
@Dao
interface CategoryDao {
    
    @Query("SELECT * FROM categories ORDER BY isFavorite DESC, name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>
    
    @Query("SELECT * FROM categories WHERE id = :categoryId")
    suspend fun getCategoryById(categoryId: Long): CategoryEntity?
    
    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun getCategoryByName(name: String): CategoryEntity?
    
    @Query("SELECT * FROM categories WHERE isCustom = 1")
    fun getCustomCategories(): Flow<List<CategoryEntity>>
    
    @Query("SELECT * FROM categories WHERE isFavorite = 1")
    fun getFavoriteCategories(): Flow<List<CategoryEntity>>
    
    @Query("SELECT COUNT(*) FROM categories WHERE isCustom = 1")
    suspend fun getCustomCategoryCount(): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long
    
    @Update
    suspend fun updateCategory(category: CategoryEntity)
    
    @Delete
    suspend fun deleteCategory(category: CategoryEntity)
    
    @Query("DELETE FROM categories WHERE id = :categoryId")
    suspend fun deleteCategoryById(categoryId: Long)
}
