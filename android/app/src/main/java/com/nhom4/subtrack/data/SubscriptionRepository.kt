package com.nhom4.subtrack.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Nguồn dữ liệu duy nhất cho các ViewModel.
 * - Mọi thao tác đọc/ghi chạy trên Dispatchers.IO để không làm đứng giao diện.
 * - Sau mỗi lần ghi (thêm/sửa/xóa) thì đọc lại và phát dữ liệu mới qua [subscriptions], [tags];
 *   màn hình nào đang theo dõi StateFlow sẽ tự vẽ lại.
 */
class SubscriptionRepository(private val db: SubTrackDatabase) {

    private val _subscriptions = MutableStateFlow<List<Subscription>?>(null)
    /** null = đang tải lần đầu. */
    val subscriptions: StateFlow<List<Subscription>?> = _subscriptions.asStateFlow()

    private val _tags = MutableStateFlow<List<Tag>>(emptyList())
    val tags: StateFlow<List<Tag>> = _tags.asStateFlow()

    /** READ: đọc lại toàn bộ gói và tag từ SQLite. */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        _subscriptions.value = db.getSubscriptions()
        _tags.value = db.getTags()
    }

    /** READ một gói theo id (null nếu không còn). */
    suspend fun getSubscription(id: Long): Subscription? = withContext(Dispatchers.IO) {
        db.getSubscriptions().firstOrNull { it.id == id }
    }

    /** CREATE (id = 0) hoặc UPDATE (id > 0) gói đăng ký. Trả về id. */
    suspend fun save(sub: Subscription): Long = withContext(Dispatchers.IO) {
        db.upsertSubscription(sub).also { refresh() }
    }

    /** DELETE gói; liên kết tag, nền tảng tự xóa theo khóa ngoại. */
    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        db.deleteSubscription(id)
        refresh()
    }

    /** CREATE / UPDATE tag. Trả về false nếu trùng tên. */
    suspend fun saveTag(tag: Tag): Boolean = withContext(Dispatchers.IO) {
        db.upsertTag(tag).also { refresh() }
    }

    /** DELETE tag; chỉ gỡ khỏi các gói, không xóa gói. */
    suspend fun deleteTag(id: Long) = withContext(Dispatchers.IO) {
        db.deleteTag(id)
        refresh()
    }
}
