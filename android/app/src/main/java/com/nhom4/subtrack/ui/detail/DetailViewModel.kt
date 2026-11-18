package com.nhom4.subtrack.ui.detail

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nhom4.subtrack.R
import com.nhom4.subtrack.SubTrackApp
import com.nhom4.subtrack.data.Currency
import com.nhom4.subtrack.data.Payment
import com.nhom4.subtrack.data.PaymentKind
import com.nhom4.subtrack.data.Subscription
import com.nhom4.subtrack.data.SubscriptionStatus
import com.nhom4.subtrack.data.Tag
import com.nhom4.subtrack.data.UndoToken
import com.nhom4.subtrack.domain.BillingMath
import com.nhom4.subtrack.reminder.Reminders
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DetailUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val sub: Subscription? = null,
    val tags: List<Tag> = emptyList(),
    val history: List<Payment> = emptyList(),
    val paidTotals: Map<Currency, Double> = emptyMap(),
    val showSnooze: Boolean = false,
    val busy: Boolean = false,
)

sealed interface DetailEvent {
    data class Message(@StringRes val text: Int, val undo: UndoToken?) : DetailEvent
}

class DetailViewModel(private val app: SubTrackApp, private val id: Long) : ViewModel() {
    private val repo = app.repository
    private val busy = MutableStateFlow(false)

    private val _events = Channel<DetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state: StateFlow<DetailUiState> = combine(repo.subscriptions, repo.tags, repo.payments, busy) { subs, tags, payments, busy ->
        if (subs == null) return@combine DetailUiState(busy = busy)
        val sub = subs.firstOrNull { it.id == id }
            ?: return@combine DetailUiState(loading = false, notFound = true, busy = busy)
        val history = payments.filter { it.subscriptionId == id }
        DetailUiState(
            loading = false,
            sub = sub,
            tags = sub.tagIds.mapNotNull { tid -> tags.firstOrNull { it.id == tid } },
            history = history,
            paidTotals = paidTotals(history),
            showSnooze = inReminderWindow(sub, LocalDate.now()),
            busy = busy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    init {
        viewModelScope.launch { if (repo.subscriptions.value == null) runCatching { repo.refresh() } }
    }

    fun markPaid(paidDate: LocalDate, amount: Double?) =
        act(R.string.msg_paid) { sub -> repo.markPaid(id, sub.nextPaymentDate, paidDate, amount) }

    fun skip() = act(R.string.msg_skipped) { sub -> repo.skip(id, sub.nextPaymentDate) }
    fun snooze(days: Int) = act(R.string.msg_snoozed) { repo.snooze(id, days) }
    fun cancel() = act(R.string.msg_cancelled) { repo.cancel(id) }
    fun reactivate(next: LocalDate) = act(R.string.msg_reactivated) { repo.reactivate(id, next) }

    fun undo(token: UndoToken) = launchBusy { repo.undo(token) }
    fun undoLatest(paymentId: Long) = launchBusy { repo.undoLatestPayment(paymentId) }

    /** Chạy thao tác trên gói hiện tại, rồi phát snackbar kèm nút Hoàn tác nếu thao tác có hiệu lực. */
    private fun act(@StringRes message: Int, op: suspend (Subscription) -> UndoToken?) {
        val sub = state.value.sub ?: return
        launchBusy {
            op(sub)?.let { _events.send(DetailEvent.Message(message, it)) }
        }
    }

    private fun launchBusy(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                block()
                if (app.settings.settings.value.remindersEnabled) Reminders.checkNow(app)
            } finally {
                busy.value = false
            }
        }
    }

    companion object {
        /** Tổng đã chi theo từng tiền tệ, chỉ tính bản ghi PAID. */
        fun paidTotals(history: List<Payment>): Map<Currency, Double> =
            history.filter { it.kind == PaymentKind.PAID && it.amount != null && it.currency != null }
                .groupBy { it.currency!! }
                .mapValues { (_, list) -> list.sumOf { it.amount!! } }

        /** Cho phép "Nhắc lại sau" khi gói đang hoạt động và đã quá hạn hoặc nằm trong khoảng nhắc. */
        fun inReminderWindow(sub: Subscription, today: LocalDate): Boolean {
            if (sub.status != SubscriptionStatus.ACTIVE) return false
            val days = BillingMath.daysUntil(sub.nextPaymentDate, today)
            return days < 0 || (sub.remindDaysBefore != null && days <= sub.remindDaysBefore)
        }
    }
}
