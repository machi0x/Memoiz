package com.machi.memoiz.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.machi.memoiz.data.Memo
import com.machi.memoiz.data.repository.CategoryRepository
import com.machi.memoiz.data.repository.MemoRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MemoGroup(val category: String, val memos: List<Memo>)

class MainViewModel(
    private val memoRepository: MemoRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    val memoGroups: StateFlow<List<MemoGroup>> = memoRepository.getAllMemos()
        .map {
            it.groupBy { memo -> memo.category }.map { (category, memos) ->
                MemoGroup(category, memos)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteMemo(memo: Memo) {
        viewModelScope.launch {
            memoRepository.deleteMemo(memo)
        }
    }

    fun deleteCategory(categoryName: String) {
        viewModelScope.launch {
            memoRepository.deleteMemosByCategoryName(categoryName)
        }
    }
}
