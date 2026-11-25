package com.nhom4.subtrack

import com.nhom4.subtrack.data.BillingCycle
import com.nhom4.subtrack.data.Category
import com.nhom4.subtrack.data.CategoryKey
import com.nhom4.subtrack.data.Currency
import com.nhom4.subtrack.data.Subscription
import com.nhom4.subtrack.data.SubscriptionStatus
import com.nhom4.subtrack.domain.BillingMath
import com.nhom4.subtrack.domain.CurrencyConverter
import com.nhom4.subtrack.domain.CsvWriter
import com.nhom4.subtrack.domain.RenewalLevel
import com.nhom4.subtrack.domain.SpendMath
import java.time.YearMonth
import com.nhom4.subtrack.reminder.ReminderWorker
import com.nhom4.subtrack.reminder.Reminders
import com.nhom4.subtrack.data.Payment
import com.nhom4.subtrack.data.PaymentKind
import com.nhom4.subtrack.ui.detail.DetailViewModel
import com.nhom4.subtrack.ui.formatAmountInput
import com.nhom4.subtrack.ui.formatMoney
import com.nhom4.subtrack.ui.reports.ReportsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BillingMathTest {
    private val today = LocalDate.of(2026, 10, 27)

    private fun sub(
        amount: Double, cycle: BillingCycle, date: LocalDate = today.plusDays(10),
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE, currency: Currency = Currency.VND,
        remind: Int? = 3, name: String = "Gói",
        snoozedUntil: LocalDate? = null, lastNotifiedOn: LocalDate? = null,
    ) = Subscription(
        name = name, amount = amount, currency = currency, cycle = cycle, nextPaymentDate = date,
        category = Category.OTHER, status = status, remindDaysBefore = remind,
        snoozedUntil = snoozedUntil, lastNotifiedOn = lastNotifiedOn,
    )

    @Test
    fun monthlyCost_convertsEachCycle() {
        assertEquals(52_000.0, BillingMath.monthlyCost(12_000.0, BillingCycle.WEEKLY), 0.001)
        assertEquals(100_000.0, BillingMath.monthlyCost(100_000.0, BillingCycle.MONTHLY), 0.001)
        assertEquals(100_000.0, BillingMath.monthlyCost(300_000.0, BillingCycle.QUARTERLY), 0.001)
        assertEquals(100_000.0, BillingMath.monthlyCost(1_200_000.0, BillingCycle.YEARLY), 0.001)
    }

    @Test
    fun totalMonthly_onlyCountsActiveInCurrency() {
        val subs = listOf(
            sub(100_000.0, BillingCycle.MONTHLY),
            sub(1_200_000.0, BillingCycle.YEARLY),
            sub(50_000.0, BillingCycle.MONTHLY, status = SubscriptionStatus.PAUSED),
            sub(9.99, BillingCycle.MONTHLY, currency = Currency.USD),
        )
        assertEquals(200_000.0, BillingMath.totalMonthly(subs, Currency.VND), 0.001)
        assertEquals(9.99, BillingMath.totalMonthly(subs, Currency.USD), 0.001)
    }

    @Test
    fun nextOnOrAfter_rollsPastDatesForward() {
        assertEquals(LocalDate.of(2026, 11, 15), BillingMath.nextOnOrAfter(LocalDate.of(2026, 9, 15), BillingCycle.MONTHLY, today))
        assertEquals(LocalDate.of(2026, 10, 27), BillingMath.nextOnOrAfter(LocalDate.of(2026, 10, 20), BillingCycle.WEEKLY, today))
        assertEquals(LocalDate.of(2027, 1, 1), BillingMath.nextOnOrAfter(LocalDate.of(2025, 1, 1), BillingCycle.YEARLY, today))
        // Ngày tương lai giữ nguyên
        assertEquals(today.plusDays(5), BillingMath.nextOnOrAfter(today.plusDays(5), BillingCycle.MONTHLY, today))
    }

    @Test
    fun monthEnd_isHandledByJavaTime() {
        assertEquals(LocalDate.of(2026, 2, 28), BillingMath.advance(LocalDate.of(2026, 1, 31), BillingCycle.MONTHLY))
    }

    /** BUG-03: gói ngày 31 không được trôi vĩnh viễn về 28 sau tháng 2. */
    @Test
    fun advance_keepsBillingDay() {
        val feb = BillingMath.advance(LocalDate.of(2027, 1, 31), BillingCycle.MONTHLY, 31)
        assertEquals(LocalDate.of(2027, 2, 28), feb)
        assertEquals(LocalDate.of(2027, 3, 31), BillingMath.advance(feb, BillingCycle.MONTHLY, 31))
        assertEquals(LocalDate.of(2027, 5, 31), BillingMath.advance(feb, BillingCycle.QUARTERLY, 31))
        assertEquals(LocalDate.of(2028, 2, 29), BillingMath.advance(LocalDate.of(2027, 2, 28), BillingCycle.YEARLY, 29))
        assertEquals(LocalDate.of(2027, 3, 7), BillingMath.advance(feb, BillingCycle.WEEKLY, 31))
    }

    /** BUG-01: sắp theo chi phí tháng phải quy đổi tiền tệ trước khi so. */
    @Test
    fun priceSort_convertsCurrency() {
        fun s(name: String, amount: Double, c: Currency) = Subscription(name = name, amount = amount, currency = c,
            cycle = BillingCycle.MONTHLY, nextPaymentDate = today, category = Category.OTHER)
        val rates = mapOf(Currency.USD to 1.0, Currency.VND to 25_000.0)
        val subs = listOf(s("VND", 59_000.0, Currency.VND), s("USD", 10.0, Currency.USD), s("Không tỷ giá", 5.0, Currency.EUR))
        val sorted = subs.sortedWith(com.nhom4.subtrack.ui.list.ListViewModel.comparator(
            com.nhom4.subtrack.ui.list.SortOrder.PRICE, Currency.VND, rates))
        assertEquals(listOf("USD", "VND", "Không tỷ giá"), sorted.map { it.name })
        // Chưa có tỷ giá: gói khác tiền tệ xếp cuối, không làm hỏng thứ tự
        val noRates = subs.sortedWith(com.nhom4.subtrack.ui.list.ListViewModel.comparator(
            com.nhom4.subtrack.ui.list.SortOrder.PRICE, Currency.VND, null))
        assertEquals("VND", noRates.first().name)
    }

    @Test
    fun renewalLevel_matchesIndicatorRules() {
        assertEquals(RenewalLevel.SOON, BillingMath.renewalLevel(today.plusDays(7), today))
        assertEquals(RenewalLevel.THIS_MONTH, BillingMath.renewalLevel(today.plusDays(8), today))
        assertEquals(RenewalLevel.THIS_MONTH, BillingMath.renewalLevel(today.plusDays(30), today))
        assertEquals(RenewalLevel.LATER, BillingMath.renewalLevel(today.plusDays(31), today))
    }

    @Test
    fun parseAmount_vnd() {
        assertEquals(260_000.0, BillingMath.parseAmount("260000", Currency.VND)!!, 0.0)
        assertEquals(260_000.0, BillingMath.parseAmount("260.000", Currency.VND)!!, 0.0)
        assertEquals(1_190_000.0, BillingMath.parseAmount("1.190.000", Currency.VND)!!, 0.0)
        assertNull(BillingMath.parseAmount("1,190,000", Currency.VND)) // dấu phẩy là phần lẻ, VND không có
        assertNull(BillingMath.parseAmount("260000,5", Currency.VND))
        assertEquals(0.0, BillingMath.parseAmount("0", Currency.VND)!!, 0.0)
        assertNull(BillingMath.parseAmount("-5000", Currency.VND))
        assertNull(BillingMath.parseAmount("abc", Currency.VND))
        assertNull(BillingMath.parseAmount("  ", Currency.VND))
    }

    @Test
    fun parseAmount_usd() {
        assertEquals(9.99, BillingMath.parseAmount("9,99", Currency.USD)!!, 0.0)
        assertEquals(1_000_000.5, BillingMath.parseAmount("1.000.000,50", Currency.USD)!!, 0.0)
        assertEquals(1234.5, BillingMath.parseAmount("1234,5", Currency.EUR)!!, 0.0)
        assertEquals(1234.0, BillingMath.parseAmount("1.234", Currency.USD)!!, 0.0)
        assertEquals(12.0, BillingMath.parseAmount("12", Currency.USD)!!, 0.0)
        // Kiểu Mỹ bị từ chối, không đoán
        assertNull(BillingMath.parseAmount("9.99", Currency.USD))
        assertNull(BillingMath.parseAmount("1,234.5", Currency.USD))
        assertNull(BillingMath.parseAmount("1.2.3.4", Currency.USD))
        assertNull(BillingMath.parseAmount("1234.567", Currency.USD))
        assertNull(BillingMath.parseAmount("9,999", Currency.USD))
    }

    @Test
    fun urlValidation() {
        assertTrue(BillingMath.isValidUrl("https://www.netflix.com/account"))
        assertTrue(BillingMath.isValidUrl("http://example.com"))
        assertFalse(BillingMath.isValidUrl("netflix.com"))
        assertFalse(BillingMath.isValidUrl("https://"))
    }

    private fun remind(next: Int, notified: Int? = null, snoozed: Int? = null, remind: Int? = 3, status: SubscriptionStatus = SubscriptionStatus.ACTIVE) =
        ReminderWorker.shouldRemind(
            sub(1.0, BillingCycle.MONTHLY, today.plusDays(next.toLong()), status = status, remind = remind,
                snoozedUntil = snoozed?.let { today.plusDays(it.toLong()) },
                lastNotifiedOn = notified?.let { today.plusDays(it.toLong()) }),
            today,
        )

    @Test
    fun remind_firstTimeInWindow() {
        assertTrue(remind(3))
        assertFalse(remind(4))
    }

    @Test
    fun remind_onceThenQuietUntilDueDay() {
        assertFalse(remind(2, notified = -1))
        assertTrue(remind(0, notified = -3))
    }

    @Test
    fun remind_notTwiceSameDay() = assertFalse(remind(0, notified = 0))

    @Test
    fun remind_overdueEveryThreeDays() {
        assertTrue(remind(-4, notified = -3))
        assertFalse(remind(-4, notified = -2))
    }

    @Test
    fun remind_snoozedIsQuiet() = assertFalse(remind(-1, snoozed = 1))

    @Test
    fun snoozeEnds_remindsOnSnoozeDay() = assertTrue(remind(-1, snoozed = 0, notified = -1))

    @Test
    fun remind_offOrInactiveIsQuiet() {
        assertFalse(remind(1, remind = null))
        assertFalse(remind(1, status = SubscriptionStatus.CANCELLED))
        assertFalse(remind(1, status = SubscriptionStatus.PAUSED))
    }

    @Test
    fun reports_totalsSharesAndOthersSlice() {
        val subs = (1..8).map { sub(it * 10_000.0, BillingCycle.MONTHLY, name = "G$it") } +
            sub(5.0, BillingCycle.MONTHLY, currency = Currency.USD)
        val r = ReportsViewModel.build(subs, emptyList(), Currency.VND, today)
        assertEquals(360_000.0, r.monthly, 0.001)
        assertEquals(4_320_000.0, r.yearly, 0.001)
        assertEquals(8, r.activeCount)
        assertEquals(1, r.excludedCount)
        assertEquals(6, r.slices.size)
        assertNull(r.slices.last().label) // "Các gói khác"
        assertEquals(100.0, r.slices.sumOf { it.share }, 0.001)
        assertEquals("G8", r.top.first().name)
    }

    private val now = LocalDate.of(2026, 11, 18)

    private fun paid(date: LocalDate?, amount: Double?, cur: Currency? = Currency.VND, cat: Category = Category.ENTERTAINMENT,
                     kind: PaymentKind = PaymentKind.PAID, subId: Long? = 1) =
        Payment(subscriptionId = subId, subscriptionName = "G", category = cat, periodDate = date ?: now,
            kind = kind, paidDate = date, amount = amount, currency = cur)

    private val sample = listOf(
        paid(LocalDate.of(2026, 11, 2), 100.0),
        paid(LocalDate.of(2026, 10, 5), 200.0),
        paid(LocalDate.of(2025, 12, 30), 300.0),
    )

    @Test
    fun actual_monthAndYear() {
        val r = ReportsViewModel.build(emptyList(), sample, Currency.VND, now)
        assertEquals(100.0, r.actualThisMonth, 0.001)
        assertEquals(300.0, r.actualThisYear, 0.001)
    }

    @Test
    fun actual_lastSixMonthsIncludesZeros() {
        val m = SpendMath.lastMonths(sample, Currency.VND, YearMonth.of(2026, 11), 6)
        assertEquals(6, m.size)
        assertEquals(YearMonth.of(2026, 6), m.first().first)
        assertEquals(200.0, m.first { it.first == YearMonth.of(2026, 10) }.second, 0.001)
        assertEquals(0.0, m.first { it.first == YearMonth.of(2026, 9) }.second, 0.001)
    }

    @Test
    fun actual_ignoresSkippedAndOtherCurrency_keepsDeleted() {
        val p = listOf(
            paid(null, null, null, kind = PaymentKind.SKIPPED),
            paid(LocalDate.of(2026, 11, 3), 5.0, Currency.USD),
            paid(LocalDate.of(2026, 11, 4), 50.0, subId = null),
        )
        assertEquals(50.0, ReportsViewModel.build(emptyList(), p, Currency.VND, now).actualThisMonth, 0.001)
    }

    @Test
    fun actual_byCategoryUsesSnapshot() {
        val p = listOf(
            paid(LocalDate.of(2026, 11, 3), 30.0, cat = Category.MUSIC),
            paid(LocalDate.of(2026, 11, 4), 100.0),
        )
        assertEquals(
            listOf(CategoryKey(Category.ENTERTAINMENT) to 100.0, CategoryKey(Category.MUSIC) to 30.0),
            SpendMath.byCategory(p, Currency.VND, LocalDate.of(2025, 12, 1), LocalDate.of(2026, 12, 1)),
        )
    }

    @Test
    fun csv_escapesSpecialCharacters() {
        assertEquals("abc", CsvWriter.escape("abc"))
        assertEquals("\"a,b\"", CsvWriter.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvWriter.escape("say \"hi\""))
        val csv = CsvWriter.build("Tên,Số tiền", listOf(listOf("Gói, gia đình", "1000")))
        assertEquals("Tên,Số tiền\r\n\"Gói, gia đình\",1000\r\n", csv)
    }

    @Test
    fun money_isFormattedVietnamese() {
        val vnd = formatMoney(260_000.0, Currency.VND)
        assertTrue(vnd, vnd.contains("260.000") && vnd.contains("₫"))
        val usd = formatMoney(1_000_000.5, Currency.USD)
        assertTrue(usd, usd.contains("1.000.000,50"))
        val eur = formatMoney(1234.5, Currency.EUR)
        assertTrue(eur, eur.contains("1.234,50"))
        // Ô nhập dùng cùng quy ước và đọc lại được
        assertEquals("9,99", formatAmountInput(9.99, Currency.USD))
        assertEquals("260000", formatAmountInput(260_000.0, Currency.VND))
        assertEquals(1234.5, BillingMath.parseAmount(formatAmountInput(1234.5, Currency.EUR), Currency.EUR)!!, 0.0)
    }

    private fun pay(kind: PaymentKind, amount: Double?, cur: Currency?) = Payment(
        subscriptionId = 1, subscriptionName = "Gói", category = Category.OTHER, periodDate = today,
        kind = kind, paidDate = if (kind == PaymentKind.PAID) today else null, amount = amount, currency = cur,
    )

    @Test
    fun detail_paidTotalsPerCurrencyIgnoresSkipped() {
        val totals = DetailViewModel.paidTotals(
            listOf(
                pay(PaymentKind.PAID, 260000.0, Currency.VND),
                pay(PaymentKind.PAID, 5.0, Currency.USD),
                pay(PaymentKind.SKIPPED, null, null),
            )
        )
        assertEquals(mapOf(Currency.VND to 260000.0, Currency.USD to 5.0), totals)
    }

    @Test
    fun notif_kindByDays() {
        assertEquals(Reminders.NotifText.TODAY, Reminders.textKind(0))
        assertEquals(Reminders.NotifText.UPCOMING, Reminders.textKind(3))
        assertEquals(Reminders.NotifText.OVERDUE, Reminders.textKind(-3))
    }

    @Test
    fun detail_snoozeOnlyInWindowOrOverdue() {
        fun s(days: Long, status: SubscriptionStatus = SubscriptionStatus.ACTIVE) =
            sub(1.0, BillingCycle.MONTHLY, today.plusDays(days), status, remind = 3)
        assertFalse(DetailViewModel.inReminderWindow(s(5), today))
        assertTrue(DetailViewModel.inReminderWindow(s(3), today))
        assertTrue(DetailViewModel.inReminderWindow(s(-2), today))
        assertFalse(DetailViewModel.inReminderWindow(s(3, SubscriptionStatus.PAUSED), today))
    }

    private val rates = mapOf(Currency.USD to 1.0, Currency.VND to 25_000.0, Currency.EUR to 0.8)

    @Test
    fun convert_viaUsd() {
        assertEquals(250_000.0, CurrencyConverter.convert(10.0, Currency.USD, Currency.VND, rates)!!, 0.001)
        assertEquals(10.0, CurrencyConverter.convert(8.0, Currency.EUR, Currency.USD, rates)!!, 0.001)
        assertEquals(312_500.0, CurrencyConverter.convert(10.0, Currency.EUR, Currency.VND, rates)!!, 0.001)
        assertEquals(5.0, CurrencyConverter.convert(5.0, Currency.EUR, Currency.EUR, null)!!, 0.0)
        assertNull(CurrencyConverter.convert(5.0, Currency.USD, Currency.VND, null))
    }

    @Test
    fun totals_convertOtherCurrencies() {
        val subs = listOf(sub(100_000.0, BillingCycle.MONTHLY), sub(4.0, BillingCycle.MONTHLY, currency = Currency.USD))
        assertEquals(100_000.0, BillingMath.totalMonthly(subs, Currency.VND), 0.001)
        assertEquals(200_000.0, BillingMath.totalMonthly(subs, Currency.VND, rates), 0.001)
        val r = ReportsViewModel.build(subs, listOf(paid(now, 2.0, Currency.USD), paid(now, 50_000.0)), Currency.VND, now, rates)
        assertEquals(200_000.0, r.monthly, 0.001)
        assertEquals(2, r.activeCount)
        assertEquals(0, r.excludedCount)
        assertTrue(r.converted)
        assertEquals(100_000.0, r.actualThisMonth, 0.001)
        // Chưa có tỷ giá: giữ cách cũ, chỉ cộng cùng tiền tệ
        val old = ReportsViewModel.build(subs, emptyList(), Currency.VND, now)
        assertEquals(1, old.excludedCount)
        assertFalse(old.converted)
    }
}
