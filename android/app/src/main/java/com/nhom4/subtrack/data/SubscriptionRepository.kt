package com.nhom4.subtrack.data

import com.nhom4.subtrack.domain.BillingMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Nguồn dữ liệu duy nhất cho UI. Mọi thao tác đọc/ghi chạy trên Dispatchers.IO,
 * sau mỗi lần ghi thì làm mới [subscriptions] và [tags].
 */
class SubscriptionRepository(private val db: SubTrackDatabase) {

    private val _subscriptions = MutableStateFlow<List<Subscription>?>(null)
    /** null = đang tải lần đầu. */
    val subscriptions: StateFlow<List<Subscription>?> = _subscriptions.asStateFlow()

    private val _tags = MutableStateFlow<List<Tag>>(emptyList())
    val tags: StateFlow<List<Tag>> = _tags.asStateFlow()

    private val _payments = MutableStateFlow<List<Payment>>(emptyList())
    val payments: StateFlow<List<Payment>> = _payments.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _subscriptions.value = db.getSubscriptions()
        _tags.value = db.getTags()
        _payments.value = db.getPayments()
    }

    suspend fun getSubscription(id: Long): Subscription? = withContext(Dispatchers.IO) {
        db.getSubscriptions().firstOrNull { it.id == id }
    }

    suspend fun save(sub: Subscription): Long = withContext(Dispatchers.IO) {
        db.upsertSubscription(sub).also { refresh() }
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        db.deleteSubscription(id)
        refresh()
    }

    suspend fun setStatus(sub: Subscription, status: SubscriptionStatus) = save(sub.copy(status = status))

    suspend fun saveTag(tag: Tag): Boolean = withContext(Dispatchers.IO) {
        db.upsertTag(tag).also { refresh() }
    }

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
    private suspend fun record(id: Long, period: LocalDate, kind: PaymentKind, paidDate: LocalDate?, amount: Double?): UndoToken? =
        withContext(Dispatchers.IO) {
            val sub = db.getSubscriptions().firstOrNull { it.id == id }
            if (sub == null || period != sub.nextPaymentDate) return@withContext null
            db.inTransaction {
                val pid = db.insertPayment(
                    Payment(
                        subscriptionId = id, subscriptionName = sub.name, category = sub.category, periodDate = period,
                        kind = kind, paidDate = paidDate, amount = amount, currency = if (amount != null) sub.currency else null,
                    )
                )
                db.updateNextPaymentDate(id, BillingMath.advance(period, sub.cycle))
                db.setReminderState(id, null, null)
                token(sub, pid)
            }.also { refresh() }
        }

    suspend fun markPaid(id: Long, period: LocalDate, paidDate: LocalDate = LocalDate.now(), amount: Double? = null): UndoToken? {
        val price = amount ?: withContext(Dispatchers.IO) { db.getSubscriptions().firstOrNull { it.id == id }?.amount } ?: return null
        return record(id, period, PaymentKind.PAID, paidDate, price)
    }

    suspend fun skip(id: Long, period: LocalDate): UndoToken? = record(id, period, PaymentKind.SKIPPED, null, null)

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
