package com.nhom4.subtrack.ui.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nhom4.subtrack.SubTrackApp
import com.nhom4.subtrack.data.Tag
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Một dòng trên màn hình Tag: tag và số gói đang gắn tag đó. */
data class TagRow(val tag: Tag, val count: Int)

data class TagsUiState(
    val loading: Boolean = true,
    val tags: List<TagRow> = emptyList(),
)

/** Thêm / sửa / xóa tag. */
class TagsViewModel(app: SubTrackApp) : ViewModel() {
    private val repo = app.repository

    val state: StateFlow<TagsUiState> = combine(repo.subscriptions, repo.tags) { subs, tags ->
        if (subs == null) return@combine TagsUiState()
        TagsUiState(
            loading = false,
            tags = tags.map { t -> TagRow(t, subs.count { t.id in it.tagIds }) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TagsUiState())

    init {
        viewModelScope.launch { if (repo.subscriptions.value == null) runCatching { repo.refresh() } }
    }

    /** CREATE (id = 0) hoặc UPDATE tag; [onResult] nhận false nếu trùng tên. */
    fun saveTag(tag: Tag, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(runCatching { repo.saveTag(tag) }.getOrDefault(false)) }
    }

    /** DELETE tag. */
    fun deleteTag(id: Long) {
        viewModelScope.launch { runCatching { repo.deleteTag(id) } }
    }
}
