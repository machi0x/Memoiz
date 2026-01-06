package com.machi.memoiz.data.repository

import com.machi.memoiz.data.dao.MemoDao
import com.machi.memoiz.data.entity.MemoEntity
import com.machi.memoiz.domain.model.Memo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

class MemoRepository(private val memoDao: MemoDao) {

    fun getAllMemos(): Flow<List<Memo>> {
        return memoDao.getAllMemos().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getMemoById(memoId: Long): Memo? {
        return memoDao.getMemoById(memoId)?.toDomain()
    }

    suspend fun insertMemo(memo: Memo): Long {
        return memoDao.insert(memo.toEntity())
    }

    suspend fun updateMemo(memo: Memo) {
        memoDao.update(memo.toEntity())
    }

    suspend fun deleteMemo(memo: Memo) {
        memoDao.delete(memo.toEntity())
    }

    suspend fun deleteMemosByCategoryName(categoryName: String) {
        memoDao.deleteMemosByCategoryName(categoryName)
    }

    fun getMemosByCategoryName(categoryName: String): Flow<List<Memo>> {
        return memoDao.getMemosByCategoryName(categoryName).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getMemosByCategories(categories: List<String>): List<Memo> {
        return memoDao.getMemosByCategories(categories).map { it.toDomain() }
    }

    suspend fun getDistinctCategories(): List<String> = memoDao.getDistinctCategories()

    suspend fun getAllMemosImmediate(): List<Memo> = memoDao.getAllMemosImmediate().map { it.toDomain() }

    suspend fun getMemosByCategoryImmediate(categoryName: String): List<Memo> =
        memoDao.getMemosByCategoryImmediate(categoryName).map { it.toDomain() }

    private fun MemoEntity.toDomain(): Memo {
        return Memo(
            id = id,
            content = content,
            imageUri = imageUri,
            memoType = memoType,
            category = category,
            subCategory = subCategory,
            summary = summary,
            sourceApp = sourceApp,
            createdAt = createdAt,
            isCategoryLocked = isCategoryLocked
        )
    }

    private fun Memo.toEntity(): MemoEntity {
        return MemoEntity(
            id = id,
            content = content,
            imageUri = imageUri,
            memoType = memoType,
            category = category,
            subCategory = subCategory,
            summary = summary,
            sourceApp = sourceApp,
            createdAt = createdAt,
            isCategoryLocked = isCategoryLocked
        )
    }

    // New: Immediate (suspending) helper to find memo by content
    suspend fun getMemoByContentImmediate(content: String): Memo? {
        return memoDao.findByContent(content)?.toDomain()
    }

    // New: Immediate (suspending) helper to find memo by imageUri
    suspend fun getMemoByImageUriImmediate(imageUri: String): Memo? {
        return memoDao.findByImageUri(imageUri)?.toDomain()
    }
}
