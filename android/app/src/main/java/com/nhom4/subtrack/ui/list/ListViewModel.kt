package com.nhom4.subtrack.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nhom4.subtrack.SubTrackApp
import com.nhom4.subtrack.data.Subscription
import com.nhom4.subtrack.data.Tag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Trạng thái màn hình danh sách: đang tải / lỗi / danh sách gói. */
data class ListUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val items: List<Subscription> = emptyList(),
    val tags: Map<Long, Tag> = emptyMap(),
)

/** READ: lấy danh sách gói từ Repository, sắp theo ngày thanh toán gần nhất. */
class ListViewModel(app: SubTrackApp) : ViewModel() {
    private val repo = app.repository
    private val error = MutableStateFlow(false)

    val state: StateFlow<ListUiState> = combine(repo.subscriptions, repo.tags, error) { subs, tags, hasError ->
        if (subs == null) return@combine ListUiState(loading = !hasError, error = hasError)
        ListUiState(
            loading = false,
            items = subs.sortedBy { it.nextPaymentDate },
            tags = tags.associateBy { it.id },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListUiState())

    init {
        load()
    }

    fun load() {
        error.value = false
        viewModelScope.launch {
            runCatching { repo.refresh() }.onFailure { error.value = true }
        }
    }
}
