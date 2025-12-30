package com.machi.memoiz.data.dao

import androidx.room.*
import com.machi.memoiz.data.entity.MemoEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Memo operations.
 * Provides methods to interact with the memos table.
 */
@Dao
interface MemoDao {
    
    @Query("SELECT * FROM memos ORDER BY createdAt DESC")
    fun getAllMemos(): Flow<List<MemoEntity>>
    
    @Query("SELECT * FROM memos WHERE id = :memoId")
    suspend fun getMemoById(memoId: Long): MemoEntity?
    
    @Query("SELECT * FROM memos WHERE category = :categoryName")
    fun getMemosByCategoryName(categoryName: String): Flow<List<MemoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memo: MemoEntity): Long
    
    @Update
    suspend fun update(memo: MemoEntity)
    
    @Delete
    suspend fun delete(memo: MemoEntity)
    
    @Query("DELETE FROM memos WHERE id = :memoId")
    suspend fun deleteMemoById(memoId: Long)
    
    @Query("DELETE FROM memos WHERE category = :categoryName")
    suspend fun deleteMemosByCategoryName(categoryName: String)

    @Query("SELECT DISTINCT category FROM memos")
    suspend fun getDistinctCategories(): List<String>

    @Query("SELECT * FROM memos WHERE category IN (:categoryNames)")
    suspend fun getMemosByCategories(categoryNames: List<String>): List<MemoEntity>
}
