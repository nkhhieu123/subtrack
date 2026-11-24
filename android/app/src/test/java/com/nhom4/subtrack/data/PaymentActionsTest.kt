package com.nhom4.subtrack.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
// Application mặc định để không chạy SubTrackApp (WorkManager)
@Config(sdk = [34], application = android.app.Application::class)
class PaymentActionsTest {
    private lateinit var db: SubTrackDatabase
    private lateinit var repo: SubscriptionRepository

    private val nov20 = LocalDate.of(2026, 11, 20)
    private val dec20 = LocalDate.of(2026, 12, 20)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(SubTrackDatabase.DB_NAME)
        db = SubTrackDatabase(context)
        repo = SubscriptionRepository(db)
    }

    private fun sub(next: LocalDate = nov20, status: SubscriptionStatus = SubscriptionStatus.ACTIVE) = Subscription(
        name = "Netflix", amount = 260000.0, currency = Currency.VND, cycle = BillingCycle.MONTHLY,
        nextPaymentDate = next, category = Category.ENTERTAINMENT, status = status,
    )

    private fun add(s: Subscription = sub()): Long = runBlocking { repo.save(s) }
    private fun get(id: Long): Subscription = runBlocking { repo.getSubscription(id)!! }

    @Test
    fun markPaid_recordsAndAdvances() = runBlocking {
        val id = add()
        repo.setReminderForTest(id)
        assertNotNull(repo.markPaid(id, nov20, paidDate = LocalDate.of(2026, 11, 19)))
        val p = db.getPayments().single()
        assertEquals(PaymentKind.PAID, p.kind)
        assertEquals(260000.0, p.amount!!, 0.0)
        assertEquals(nov20, p.periodDate)
        assertEquals(LocalDate.of(2026, 11, 19), p.paidDate)
        val s = get(id)
        assertEquals(dec20, s.nextPaymentDate)
        assertNull(s.snoozedUntil)
        assertNull(s.lastNotifiedOn)
    }

    @Test
    fun markPaid_staleperiod_isNoOp() = runBlocking {
        val id = add()
        assertNotNull(repo.markPaid(id, nov20))
        assertNull(repo.markPaid(id, nov20))
        assertEquals(1, db.getPayments().size)
        assertEquals(dec20, get(id).nextPaymentDate)
    }

    @Test
    fun markPaid_concurrentCalls_oneRowNoThrow() = runBlocking {
        val id = add()
        val results = (1..2).map { async(Dispatchers.IO) { repo.markPaid(id, nov20) } }.awaitAll()
        assertEquals(1, results.count { it != null })
        assertEquals(1, db.getPayments().size)
        assertEquals(dec20, get(id).nextPaymentDate)
    }

    @Test
    fun skip_recordsSkippedWithoutAmount() = runBlocking {
        val id = add()
        assertNotNull(repo.skip(id, nov20))
        val p = db.getPayments().single()
        assertEquals(PaymentKind.SKIPPED, p.kind)
        assertNull(p.amount)
        assertNull(p.paidDate)
        assertEquals(dec20, get(id).nextPaymentDate)
    }

    @Test
    fun snooze_setsSnoozedUntil() = runBlocking {
        val id = add()
        assertNotNull(repo.snooze(id, 3, today = LocalDate.of(2026, 11, 18)))
        assertEquals(LocalDate.of(2026, 11, 21), get(id).snoozedUntil)
        assertTrue(db.getPayments().isEmpty())
    }

    @Test
    fun undo_afterCancel_restoresStatusAndKeepsOldPayments() = runBlocking {
        val id = add()
        repo.markPaid(id, nov20)
        val before = get(id)
        val token = repo.cancel(id)!!
        assertEquals(SubscriptionStatus.CANCELLED, get(id).status)
        repo.undo(token)
        val s = get(id)
        assertEquals(SubscriptionStatus.ACTIVE, s.status)
        assertEquals(before.nextPaymentDate, s.nextPaymentDate)
        assertEquals(1, db.getPayments().size)
    }

    @Test
    fun reactivate_thenUndo_restoresPaused() = runBlocking {
        val may14 = LocalDate.of(2026, 5, 14)
        val id = add(sub(next = may14, status = SubscriptionStatus.PAUSED))
        val token = repo.reactivate(id, LocalDate.of(2026, 12, 14))!!
        var s = get(id)
        assertEquals(SubscriptionStatus.ACTIVE, s.status)
        assertEquals(LocalDate.of(2026, 12, 14), s.nextPaymentDate)
        assertTrue(db.getPayments().isEmpty())
        repo.undo(token)
        s = get(id)
        assertEquals(SubscriptionStatus.PAUSED, s.status)
        assertEquals(may14, s.nextPaymentDate)
    }

    @Test
    fun undo_restoresExactMonthEndDate() = runBlocking {
        val jan31 = LocalDate.of(2026, 1, 31)
        val id = add(sub(next = jan31))
        val token = repo.markPaid(id, jan31)!!
        assertEquals(LocalDate.of(2026, 2, 28), get(id).nextPaymentDate)
        repo.undo(token)
        assertEquals(jan31, get(id).nextPaymentDate)
        assertTrue(db.getPayments().isEmpty())
    }

    @Test
    fun undoLatestPayment_onlyNewest() = runBlocking {
        val id = add()
        repo.markPaid(id, nov20)
        repo.markPaid(id, dec20)
        val (newest, oldest) = db.getPayments()
        assertFalse(repo.undoLatestPayment(oldest.id))
        assertTrue(repo.undoLatestPayment(newest.id))
        assertEquals(dec20, get(id).nextPaymentDate)
        assertEquals(1, db.getPayments().size)
    }

    @Test
    fun editPrice_keepsHistoryAmounts() = runBlocking {
        val id = add()
        repo.markPaid(id, nov20)
        repo.save(get(id).copy(amount = 300000.0))
        assertEquals(260000.0, db.getPayments().single().amount!!, 0.0)
    }

    @Test
    fun refresh_doesNotRollOverPastDates() = runBlocking {
        val past = LocalDate.now().minusDays(10)
        val id = add(sub(next = past))
        repo.refresh()
        assertEquals(past, get(id).nextPaymentDate)
    }

    /** BUG-03: trả hai kỳ liên tiếp của gói ngày 31 phải về lại ngày 31. */
    @Test
    fun markPaid_monthEnd_keepsBillingDay() = runBlocking {
        val jan31 = LocalDate.of(2027, 1, 31)
        val feb28 = LocalDate.of(2027, 2, 28)
        val id = add(sub(next = jan31))
        repo.markPaid(id, jan31)
        assertEquals(feb28, get(id).nextPaymentDate)
        repo.markPaid(id, feb28)
        assertEquals(LocalDate.of(2027, 3, 31), get(id).nextPaymentDate)
    }

    private fun SubscriptionRepository.setReminderForTest(id: Long) = db.setReminderState(id, nov20, nov20)
}
