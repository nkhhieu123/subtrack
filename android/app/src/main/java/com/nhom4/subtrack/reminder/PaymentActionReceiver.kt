package com.nhom4.subtrack.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.nhom4.subtrack.SubTrackApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Xử lý nút "Đã trả" / "Nhắc sau 1 ngày" ngay trong thông báo. */
class PaymentActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_SUB_ID, -1L)
        if (id <= 0) return
        val period = LocalDate.ofEpochDay(intent.getLongExtra(EXTRA_PERIOD, 0L))
        val repo = (context.applicationContext as SubTrackApp).repository
        val action = intent.action
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Lỗi CSDL không được làm sập tiến trình; giữ thông báo để người dùng thử lại
                val done = when (action) {
                    // null ở đây là kỳ đã cũ: không còn gì để làm
                    ACTION_PAID -> runCatching { repo.markPaid(id, period) }.isSuccess
                    ACTION_SNOOZE -> runCatching { repo.snooze(id, 1) }.getOrNull() != null
                    else -> false
                }
                if (done) NotificationManagerCompat.from(context).cancel(id.toInt())
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PAID = "com.nhom4.subtrack.action.PAID"
        const val ACTION_SNOOZE = "com.nhom4.subtrack.action.SNOOZE"
        const val EXTRA_SUB_ID = "sub_id"
        const val EXTRA_PERIOD = "period"
    }
}
