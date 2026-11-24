package com.nhom4.subtrack.domain

import com.nhom4.subtrack.data.Category
import com.nhom4.subtrack.data.Currency
import com.nhom4.subtrack.data.Payment
import com.nhom4.subtrack.data.PaymentKind
import java.time.LocalDate
import java.time.YearMonth

/**
 * Chi thực tế: các dòng PAID tính theo ngày trả (paidDate), quy về tiền tệ báo cáo bằng
 * tỷ giá hiện có; dòng khác tiền tệ mà chưa có tỷ giá thì bỏ qua.
 */
object SpendMath {
    // shortcut: dùng tỷ giá hiện tại cho mọi kỳ cũ, vì API miễn phí không có tỷ giá lịch sử.
    private fun paid(p: List<Payment>, currency: Currency, from: LocalDate, toExclusive: LocalDate, perUsd: Map<Currency, Double>?) =
        p.mapNotNull {
            val d = it.paidDate
            val amount = it.amount
            val cur = it.currency
            if (it.kind != PaymentKind.PAID || amount == null || cur == null || d == null || d.isBefore(from) || !d.isBefore(toExclusive)) null
            else CurrencyConverter.convert(amount, cur, currency, perUsd)?.let { v -> it to v }
        }

    fun total(p: List<Payment>, currency: Currency, from: LocalDate, toExclusive: LocalDate, perUsd: Map<Currency, Double>? = null): Double =
        paid(p, currency, from, toExclusive, perUsd).sumOf { it.second }

    /** Cũ → mới, gồm cả tháng bằng 0. */
    fun lastMonths(p: List<Payment>, currency: Currency, endMonth: YearMonth, count: Int, perUsd: Map<Currency, Double>? = null): List<Pair<YearMonth, Double>> =
        (count - 1 downTo 0).map { back ->
            val m = endMonth.minusMonths(back.toLong())
            m to total(p, currency, m.atDay(1), m.plusMonths(1).atDay(1), perUsd)
        }

    /** Giảm dần, bỏ danh mục bằng 0. */
    fun byCategory(p: List<Payment>, currency: Currency, from: LocalDate, toExclusive: LocalDate, perUsd: Map<Currency, Double>? = null): List<Pair<Category, Double>> =
        paid(p, currency, from, toExclusive, perUsd).groupBy { it.first.category }
            .map { (c, l) -> c to l.sumOf { it.second } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
}
