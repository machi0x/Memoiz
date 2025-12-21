package com.machika.memoiz.data.dao

import androidx.room.*
import com.machika.memoiz.data.entity.MemoEntity
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
    
    @Query("SELECT * FROM memos WHERE categoryId = :categoryId ORDER BY createdAt DESC")
    fun getMemosByCategory(categoryId: Long): Flow<List<MemoEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemo(memo: MemoEntity): Long
    
    @Update
    suspend fun updateMemo(memo: MemoEntity)
    
    @Delete
    suspend fun deleteMemo(memo: MemoEntity)
    
    @Query("DELETE FROM memos WHERE id = :memoId")
    suspend fun deleteMemoById(memoId: Long)
    
    @Query("DELETE FROM memos WHERE categoryId = :categoryId")
    suspend fun deleteMemosByCategory(categoryId: Long)
}
