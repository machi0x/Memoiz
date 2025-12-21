package com.machi.memoiz.data.repository

import com.machi.memoiz.data.dao.MemoDao
import com.machi.memoiz.data.entity.MemoEntity
import com.machi.memoiz.domain.model.Memo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for Memo operations.
 * Handles data operations and mapping between entity and domain models.
 */
class MemoRepository(private val memoDao: MemoDao) {
    
    fun getAllMemos(): Flow<List<Memo>> {
        return memoDao.getAllMemos().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    suspend fun getMemoById(memoId: Long): Memo? {
        return memoDao.getMemoById(memoId)?.toDomain()
    }
    
    fun getMemosByCategory(categoryId: Long): Flow<List<Memo>> {
        return memoDao.getMemosByCategory(categoryId).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    suspend fun insertMemo(memo: Memo): Long {
        return memoDao.insertMemo(memo.toEntity())
    }
    
    suspend fun updateMemo(memo: Memo) {
        memoDao.updateMemo(memo.toEntity())
    }
    
    suspend fun deleteMemo(memo: Memo) {
        memoDao.deleteMemo(memo.toEntity())
    }
    
    suspend fun deleteMemosByCategory(categoryId: Long) {
        memoDao.deleteMemosByCategory(categoryId)
    }
    
    private fun MemoEntity.toDomain(): Memo {
        return Memo(
            id = id,
            content = content,
            imageUri = imageUri,
            categoryId = categoryId,
            originalCategory = originalCategory,
            subCategory = subCategory,
            sourceApp = sourceApp,
            createdAt = createdAt
        )
    }
    
    private fun Memo.toEntity(): MemoEntity {
        return MemoEntity(
            id = id,
            content = content,
            imageUri = imageUri,
            categoryId = categoryId,
            originalCategory = originalCategory,
            subCategory = subCategory,
            sourceApp = sourceApp,
            createdAt = createdAt
        )
    }
}
