package com.nhom4.subtrack.data

import android.content.Context
import com.nhom4.subtrack.domain.Dataset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Một bộ dữ liệu = một file SQLite riêng. [id] = [MAIN_ID] là dữ liệu gốc của người dùng. */
data class DatasetInfo(val id: String, val name: String, val file: String, val createdAt: Long) {
    val isMain get() = id == DatasetRegistry.MAIN_ID
}

data class DatasetsState(val list: List<DatasetInfo>, val activeId: String) {
    val active get() = list.first { it.id == activeId }
}

/**
 * Danh sách bộ dữ liệu (SharedPreferences "datasets") và bộ đang dùng. Nhập dữ liệu luôn tạo bộ
 * mới nên dữ liệu gốc không bao giờ bị ghi đè; chuyển bộ chỉ đổi CSDL bên trong [repository].
 */
class DatasetRegistry(private val context: Context) {
    private val prefs = context.getSharedPreferences("datasets", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<DatasetsState> = _state.asStateFlow()

    val repository = SubscriptionRepository(SubTrackDatabase(context, _state.value.active.file))

    private fun load(): DatasetsState {
        val main = DatasetInfo(MAIN_ID, context.getString(com.nhom4.subtrack.R.string.dataset_main), SubTrackDatabase.DB_NAME, 0)
        val extra = runCatching {
            val arr = JSONArray(prefs.getString(KEY_LIST, "[]"))
            (0 until arr.length()).map { i ->
                arr.getJSONObject(i).let { DatasetInfo(it.getString("id"), it.getString("name"), it.getString("file"), it.getLong("createdAt")) }
            }
        }.getOrDefault(emptyList())
        val mainName = prefs.getString(KEY_MAIN_NAME, null)
        val list = listOf(if (mainName != null) main.copy(name = mainName) else main) + extra
        val active = prefs.getString(KEY_ACTIVE, MAIN_ID)?.takeIf { id -> list.any { it.id == id } } ?: MAIN_ID
        return DatasetsState(list, active)
    }

    private fun save(s: DatasetsState) {
        val arr = JSONArray()
        s.list.filter { !it.isMain }.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("file", it.file).put("createdAt", it.createdAt))
        }
        // commit: danh sách phải khớp với file CSDL vừa tạo/xoá, kể cả khi app bị tắt ngay sau đó
        prefs.edit().putString(KEY_LIST, arr.toString()).putString(KEY_ACTIVE, s.activeId)
            .putString(KEY_MAIN_NAME, s.list.first { it.isMain }.name).commit()
        _state.value = s
    }

    /** Tạo bộ mới từ [data]; lỗi thì không để lại file dở. [activate] = "Lưu và dùng". */
    suspend fun create(name: String, data: Dataset, activate: Boolean, now: Long = System.currentTimeMillis()): DatasetInfo =
        withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val info = DatasetInfo(id, name.trim().ifEmpty { id.take(8) }, "ds_$id.db", now)
            try {
                SubTrackDatabase(context, info.file).use { it.importAll(data) }
            } catch (e: Exception) {
                context.deleteDatabase(info.file)
                throw e
            }
            save(_state.value.let { it.copy(list = it.list + info) })
            if (activate) switchTo(id)
            info
        }

    suspend fun switchTo(id: String) = withContext(Dispatchers.IO) {
        val s = _state.value
        val info = s.list.firstOrNull { it.id == id } ?: return@withContext
        if (id == s.activeId) return@withContext
        save(s.copy(activeId = id))
        repository.switchTo(SubTrackDatabase(context, info.file))
        // Thông báo cũ trỏ tới gói của bộ trước: bấm vào sẽ thao tác nhầm bộ, nên gỡ hết
        androidx.core.app.NotificationManagerCompat.from(context).cancelAll()
    }

    fun rename(id: String, name: String) {
        val n = name.trim().ifEmpty { return }
        val s = _state.value
        save(s.copy(list = s.list.map { if (it.id == id) it.copy(name = n) else it }))
    }

    /** Không xoá được dữ liệu gốc và bộ đang dùng. */
    fun delete(id: String): Boolean {
        val s = _state.value
        val info = s.list.firstOrNull { it.id == id } ?: return false
        if (info.isMain || id == s.activeId) return false
        save(s.copy(list = s.list - info))
        context.deleteDatabase(info.file)
        return true
    }

    /** Số gói của một bộ (bộ đang dùng lấy từ repository, bộ khác mở file để đếm). */
    suspend fun count(info: DatasetInfo): Int = withContext(Dispatchers.IO) {
        if (info.id == _state.value.activeId) repository.subscriptions.value?.size ?: 0
        else runCatching {
            SubTrackDatabase(context, info.file).use { db ->
                db.readableDatabase.rawQuery("SELECT COUNT(*) FROM subscriptions", null).use { c -> c.moveToFirst(); c.getInt(0) }
            }
        }.getOrDefault(0)
    }

    companion object {
        const val MAIN_ID = "main"
        private const val KEY_LIST = "list"
        private const val KEY_ACTIVE = "active"
        private const val KEY_MAIN_NAME = "main_name"
    }
}
