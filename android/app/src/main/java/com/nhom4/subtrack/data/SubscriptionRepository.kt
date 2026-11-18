package com.nhom4.subtrack.data

import android.database.sqlite.SQLiteConstraintException
import com.nhom4.subtrack.domain.BillingMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.LocalDate

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

    private val _payments = MutableStateFlow<List<Payment>>(emptyList())
    val payments: StateFlow<List<Payment>> = _payments.asStateFlow()

    /** READ: đọc lại toàn bộ gói và tag từ SQLite. */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        _subscriptions.value = db.getSubscriptions()
        _tags.value = db.getTags()
        _payments.value = db.getPayments()
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

    suspend fun setStatus(sub: Subscription, status: SubscriptionStatus) = save(sub.copy(status = status))

    /** CREATE / UPDATE tag. Trả về false nếu trùng tên. */
    suspend fun saveTag(tag: Tag): Boolean = withContext(Dispatchers.IO) {
        db.upsertTag(tag).also { refresh() }
    }

    /** DELETE tag; chỉ gỡ khỏi các gói, không xóa gói. */
    suspend fun deleteTag(id: Long) = withContext(Dispatchers.IO) {
        db.deleteTag(id)
        refresh()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        db.clearAll()
        refresh()
    }

    /** Giá trị cần để [undo] khôi phục gói về đúng trạng thái trước một thao tác. */
    private fun token(sub: Subscription, insertedPaymentId: Long? = null) =
        UndoToken(sub.id, sub.status, sub.nextPaymentDate, sub.snoozedUntil, sub.lastNotifiedOn, insertedPaymentId)

    /** Ghi một kỳ (PAID/SKIPPED), tiến ngày một chu kỳ, xóa trạng thái nhắc. null nếu [period] không phải kỳ hiện tại. */
    private suspend fun record(
        id: Long, period: LocalDate, kind: PaymentKind, paidDate: LocalDate?, amountOf: (Subscription) -> Double?,
    ): UndoToken? = withContext(Dispatchers.IO) {
        try {
            // Đọc, kiểm tra kỳ và ghi trong cùng một giao dịch để hai lần bấm đồng thời không cùng vượt qua kiểm tra.
            db.inTransaction {
                val sub = db.getSubscriptions().firstOrNull { it.id == id }
                if (sub == null || period != sub.nextPaymentDate) return@inTransaction null
                val amount = amountOf(sub)
                val pid = db.insertPayment(
                    Payment(
                        subscriptionId = id, subscriptionName = sub.name, category = sub.category, periodDate = period,
                        kind = kind, paidDate = paidDate, amount = amount, currency = if (amount != null) sub.currency else null,
                    )
                )
                db.updateNextPaymentDate(id, BillingMath.advance(period, sub.cycle))
                db.setReminderState(id, null, null)
                token(sub, pid)
            }
        } catch (e: SQLiteConstraintException) {
            null // kỳ này đã có dòng: coi như bấm trùng
        }.also { refresh() }
    }

    suspend fun markPaid(id: Long, period: LocalDate, paidDate: LocalDate = LocalDate.now(), amount: Double? = null): UndoToken? =
        record(id, period, PaymentKind.PAID, paidDate) { amount ?: it.amount }

    suspend fun skip(id: Long, period: LocalDate): UndoToken? = record(id, period, PaymentKind.SKIPPED, null) { null }

    suspend fun snooze(id: Long, days: Int, today: LocalDate = LocalDate.now()): UndoToken? = withContext(Dispatchers.IO) {
        val sub = db.getSubscriptions().firstOrNull { it.id == id } ?: return@withContext null
        db.setReminderState(id, today.plusDays(days.toLong()), sub.lastNotifiedOn)
        token(sub).also { refresh() }
    }

    suspend fun cancel(id: Long): UndoToken? = withContext(Dispatchers.IO) {
        val sub = db.getSubscriptions().firstOrNull { it.id == id } ?: return@withContext null
        db.inTransaction {
            db.setStatusAndDate(id, SubscriptionStatus.CANCELLED, sub.nextPaymentDate)
            db.setReminderState(id, null, null)
        }
        token(sub).also { refresh() }
    }

    suspend fun reactivate(id: Long, next: LocalDate): UndoToken? = withContext(Dispatchers.IO) {
        val sub = db.getSubscriptions().firstOrNull { it.id == id } ?: return@withContext null
        db.inTransaction {
            db.setStatusAndDate(id, SubscriptionStatus.ACTIVE, next)
            db.setReminderState(id, null, null)
        }
        token(sub).also { refresh() }
    }

    suspend fun undo(token: UndoToken) = withContext(Dispatchers.IO) {
        db.inTransaction {
            token.insertedPaymentId?.let(db::deletePayment)
            db.setStatusAndDate(token.subscriptionId, token.status, token.nextPaymentDate)
            db.setReminderState(token.subscriptionId, token.snoozedUntil, token.lastNotifiedOn)
        }
        refresh()
    }

    /** Hoàn tác dòng lịch sử; chỉ được với dòng mới nhất của gói. */
    suspend fun undoLatestPayment(paymentId: Long): Boolean = withContext(Dispatchers.IO) {
        val all = db.getPayments()
        val p = all.firstOrNull { it.id == paymentId }
        val subId = p?.subscriptionId
        if (p == null || subId == null || all.first { it.subscriptionId == subId }.id != paymentId) return@withContext false
        db.inTransaction {
            db.deletePayment(paymentId)
            db.updateNextPaymentDate(subId, p.periodDate)
            db.setReminderState(subId, null, null)
        }
        refresh()
        true
    }

    /** Chỉ gọi sau khi gửi thông báo thành công. */
    suspend fun markNotified(id: Long, day: LocalDate) = withContext(Dispatchers.IO) {
        val sub = db.getSubscriptions().firstOrNull { it.id == id } ?: return@withContext
        db.setReminderState(id, sub.snoozedUntil, day)
        refresh()
    }
}

data class UndoToken(
    val subscriptionId: Long, val status: SubscriptionStatus, val nextPaymentDate: LocalDate,
    val snoozedUntil: LocalDate?, val lastNotifiedOn: LocalDate?, val insertedPaymentId: Long?,
)
