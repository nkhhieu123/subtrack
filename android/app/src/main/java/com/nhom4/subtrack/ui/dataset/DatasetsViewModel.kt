package com.nhom4.subtrack.ui.dataset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nhom4.subtrack.R
import com.nhom4.subtrack.SubTrackApp
import com.nhom4.subtrack.data.DatasetInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DatasetRow(val info: DatasetInfo, val count: Int, val active: Boolean)

@OptIn(ExperimentalCoroutinesApi::class)
class DatasetsViewModel(private val app: SubTrackApp) : ViewModel() {
    private val registry = app.datasets

    /** Đếm lại số gói mỗi khi danh sách bộ hoặc dữ liệu bộ đang dùng đổi. */
    val rows: StateFlow<List<DatasetRow>?> = combine(registry.state, app.repository.subscriptions) { s, _ -> s }
        .mapLatest { s -> s.list.map { DatasetRow(it, registry.count(it), it.id == s.activeId) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun use(info: DatasetInfo) {
        viewModelScope.launch {
            runCatching { app.switchDataset(info.id) }
                .onSuccess { _messages.send(app.getString(R.string.dataset_switched, info.name)) }
                .onFailure { _messages.send(app.getString(R.string.error_generic)) }
        }
    }

    fun rename(info: DatasetInfo, name: String) = registry.rename(info.id, name)

    fun delete(info: DatasetInfo) {
        if (!registry.delete(info.id)) _messages.trySend(app.getString(R.string.error_generic))
    }
}
