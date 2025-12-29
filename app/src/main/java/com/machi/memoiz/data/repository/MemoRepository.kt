package com.machi.memoiz.data.repository

import com.machi.memoiz.data.dao.MemoDao
import com.machi.memoiz.data.entity.MemoEntity
import com.machi.memoiz.domain.model.Memo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

    private fun MemoEntity.toDomain(): Memo {
        return Memo(
            id = id,
            content = content,
            imageUri = imageUri,
            category = category,
            subCategory = subCategory,
            summary = summary,
            sourceApp = sourceApp,
            createdAt = createdAt
        )
    }

    private fun Memo.toEntity(): MemoEntity {
        return MemoEntity(
            id = id,
            content = content,
            imageUri = imageUri,
            category = category,
            subCategory = subCategory,
            summary = summary,
            sourceApp = sourceApp,
            createdAt = createdAt
        )
    }
}
