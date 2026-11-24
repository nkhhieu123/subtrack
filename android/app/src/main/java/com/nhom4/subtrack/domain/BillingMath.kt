package com.nhom4.subtrack.domain

import com.nhom4.subtrack.data.BillingCycle
import com.nhom4.subtrack.data.Currency
import com.nhom4.subtrack.data.Subscription
import com.nhom4.subtrack.data.SubscriptionStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class RenewalLevel { SOON, THIS_MONTH, LATER }

/** Quy tắc tính tiền và ngày, tách khỏi Android để kiểm thử bằng JUnit. */
object BillingMath {

    /**
     * Quy đổi về chi phí một tháng:
     * tuần × 52 / 12, tháng × 1, quý / 3, năm / 12.
     */
    fun monthlyCost(amount: Double, cycle: BillingCycle): Double = when (cycle) {
        BillingCycle.WEEKLY -> amount * 52.0 / 12.0
        BillingCycle.MONTHLY -> amount
        BillingCycle.QUARTERLY -> amount / 3.0
        BillingCycle.YEARLY -> amount / 12.0
    }

    fun weeklyFromMonthly(monthly: Double) = monthly * 12.0 / 52.0
    fun quarterlyFromMonthly(monthly: Double) = monthly * 3.0
    fun yearlyFromMonthly(monthly: Double) = monthly * 12.0

    /** Chi phí tháng của gói quy về [to]; null nếu khác tiền tệ mà chưa có tỷ giá. */
    fun monthlyIn(sub: Subscription, to: Currency, perUsd: Map<Currency, Double>?): Double? =
        CurrencyConverter.convert(monthlyCost(sub.amount, sub.cycle), sub.currency, to, perUsd)

    /** Tổng chi phí tháng của các gói đang hoạt động quy về [currency]; bỏ qua gói chưa quy đổi được. */
    fun totalMonthly(subs: List<Subscription>, currency: Currency, perUsd: Map<Currency, Double>? = null): Double =
        subs.filter { it.status == SubscriptionStatus.ACTIVE }
            .sumOf { monthlyIn(it, currency, perUsd) ?: 0.0 }

    fun advance(date: LocalDate, cycle: BillingCycle): LocalDate = when (cycle) {
        BillingCycle.WEEKLY -> date.plusWeeks(1)
        BillingCycle.MONTHLY -> date.plusMonths(1)
        BillingCycle.QUARTERLY -> date.plusMonths(3)
        BillingCycle.YEARLY -> date.plusYears(1)
    }

    /** Ngày thanh toán đầu tiên không trước [today], tính từ [date] theo chu kỳ. */
    fun nextOnOrAfter(date: LocalDate, cycle: BillingCycle, today: LocalDate): LocalDate {
        var d = date
        var guard = 0
        while (d.isBefore(today) && guard < 10_000) {
            d = advance(d, cycle)
            guard++
        }
        return d
    }

    fun daysUntil(date: LocalDate, today: LocalDate = LocalDate.now()): Long =
        ChronoUnit.DAYS.between(today, date)

    /** Đỏ: trong 7 ngày; cam: trong 30 ngày; còn lại: xa. */
    fun renewalLevel(date: LocalDate, today: LocalDate = LocalDate.now()): RenewalLevel {
        val days = daysUntil(date, today)
        return when {
            days <= 7 -> RenewalLevel.SOON
            days <= 30 -> RenewalLevel.THIS_MONTH
            else -> RenewalLevel.LATER
        }
    }

    /**
     * Đọc số tiền theo quy ước vi-VN dùng chung cho mọi tiền tệ: "." tách nghìn,
     * "," trước phần lẻ (1–2 chữ số, VND không có phần lẻ). "9.99" bị từ chối
     * thay vì đoán. Trả về null nếu sai.
     */
    fun parseAmount(input: String, currency: Currency): Double? {
        val raw = input.trim().replace(" ", "")
        val m = Regex("""(\d+(?:\.\d{3})*)(?:,(\d{1,2}))?""").matchEntire(raw) ?: return null
        val intPart = m.groupValues[1]
        val fraction = m.groupValues[2]
        if (intPart.contains('.') && intPart.substringBefore('.').length > 3) return null
        if (fraction.isNotEmpty() && currency == Currency.VND) return null
        val digits = intPart.replace(".", "")
        return (if (fraction.isEmpty()) digits else "$digits.$fraction").toDoubleOrNull()
    }

    fun isValidUrl(url: String): Boolean =
        Regex("""^https?://[^\s/$.?#].[^\s]*$""", RegexOption.IGNORE_CASE).matches(url.trim())
}
