package com.machi.memoiz.data.datastore

/**
 * Represents the persisted status of Memoiz (EXP and four parameters).
 */
data class MemoizStatus(
    val exp: Int = 0,
    val kindness: Int = 0,
    val coolness: Int = 0,
    val smartness: Int = 0,
    val curiosity: Int = 0,
    val usedMemoIds: Set<Long> = emptySet()
)
