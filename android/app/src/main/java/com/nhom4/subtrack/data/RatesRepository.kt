package com.nhom4.subtrack.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

data class RatesState(
    /** Số đơn vị mỗi tiền tệ cho 1 USD; null = chưa từng lấy được. */
    val perUsd: Map<Currency, Double>? = null,
    /** Ngày lấy tỷ giá về máy. */
    val fetchedOn: LocalDate? = null,
    val syncing: Boolean = false,
    /** Lần thử gần nhất bị lỗi (mất mạng, máy chủ lỗi…). */
    val failed: Boolean = false,
)

/**
 * Tỷ giá từ ExchangeRate-API (bản open access, không cần key, cập nhật mỗi ngày).
 * Lưu bản gần nhất trong SharedPreferences để dùng khi không có mạng.
 */
class RatesRepository(
    context: Context,
    private val fetch: suspend () -> String = { httpGet(URL_LATEST_USD) },
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    private val prefs = context.getSharedPreferences("rates", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    private val _state = MutableStateFlow(read())
    val state: StateFlow<RatesState> = _state.asStateFlow()

    /** Gọi khi mở app: chỉ lấy nếu hôm nay chưa lấy được. */
    fun syncIfStale() {
        if (_state.value.fetchedOn != today()) scope.launch { sync() }
    }

    /** Nút "Cập nhật": luôn lấy lại (bỏ qua nếu đang lấy). */
    fun syncNow() {
        scope.launch { sync() }
    }

    /** Trả về true nếu lấy và lưu thành công; false nếu lỗi hoặc đang có lần lấy khác. */
    suspend fun sync(): Boolean {
        if (!lock.tryLock()) return false
        try {
            _state.update { it.copy(syncing = true) }
            val rates = runCatching { parse(fetch()) }.getOrNull()
            if (rates == null) {
                _state.update { it.copy(syncing = false, failed = true) }
                return false
            }
            val day = today()
            prefs.edit().apply {
                rates.forEach { (c, r) -> putString(KEY_RATE + c.name, r.toString()) }
                putLong(KEY_FETCHED_ON, day.toEpochDay())
            }.apply()
            _state.value = RatesState(rates, day, syncing = false, failed = false)
            return true
        } finally {
            lock.unlock()
        }
    }

    private fun read(): RatesState {
        val rates = Currency.entries.associateWith { prefs.getString(KEY_RATE + it.name, null)?.toDoubleOrNull() }
        if (rates.values.any { it == null || it <= 0 } || !prefs.contains(KEY_FETCHED_ON)) return RatesState()
        return RatesState(rates.mapValues { it.value!! }, LocalDate.ofEpochDay(prefs.getLong(KEY_FETCHED_ON, 0)))
    }

    companion object {
        const val URL_LATEST_USD = "https://open.er-api.com/v6/latest/USD"
        const val ATTRIBUTION_URL = "https://www.exchangerate-api.com"
        private const val KEY_RATE = "per_usd_"
        private const val KEY_FETCHED_ON = "fetched_on"

        /** Đọc phản hồi JSON; null nếu lỗi hoặc thiếu / sai tỷ giá của tiền tệ nào trong app. */
        fun parse(json: String): Map<Currency, Double>? = runCatching {
            val o = JSONObject(json)
            if (o.optString("result") != "success" || o.optString("base_code") != "USD") return null
            val rates = o.getJSONObject("rates")
            Currency.entries.associateWith { c ->
                rates.optDouble(c.code, Double.NaN).takeIf { it.isFinite() && it > 0 } ?: return null
            }
        }.getOrNull()

        private suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                check(conn.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${conn.responseCode}" }
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
    }
}
