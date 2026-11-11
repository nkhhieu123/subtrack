package com.nhom4.subtrack.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nhom4.subtrack.SubTrackApp
import com.nhom4.subtrack.ui.edit.EditViewModel
import com.nhom4.subtrack.ui.list.ListViewModel
import com.nhom4.subtrack.ui.tags.TagsViewModel

private fun CreationExtras.app() = this[APPLICATION_KEY] as SubTrackApp

/**
 * Cách tạo từng ViewModel: lấy SubTrackApp (chứa Repository dùng chung) rồi truyền vào
 * hàm khởi tạo. Đây là tiêm phụ thuộc thủ công, không cần thư viện Hilt.
 */
object AppViewModels {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer { ListViewModel(app()) }
        initializer { TagsViewModel(app()) }
    }

    /** Form thêm (id = 0) hoặc sửa (id > 0) một gói. */
    fun edit(id: Long): ViewModelProvider.Factory = viewModelFactory {
        initializer { EditViewModel(app(), id) }
    }
}
