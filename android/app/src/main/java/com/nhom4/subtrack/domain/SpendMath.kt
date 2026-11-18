package com.nhom4.subtrack.domain

import com.nhom4.subtrack.data.Category
import com.nhom4.subtrack.data.Currency
import com.nhom4.subtrack.data.Payment
import com.nhom4.subtrack.data.PaymentKind
import java.time.LocalDate
import java.time.YearMonth

/** Chi thực tế: chỉ các dòng PAID đúng tiền tệ, tính theo ngày trả (paidDate). */
object SpendMath {
    private fun paid(p: List<Payment>, currency: Currency, from: LocalDate, toExclusive: LocalDate) =
        p.filter {
            val d = it.paidDate
            it.kind == PaymentKind.PAID && it.currency == currency && it.amount != null &&
                d != null && !d.isBefore(from) && d.isBefore(toExclusive)
        }

    fun total(p: List<Payment>, currency: Currency, from: LocalDate, toExclusive: LocalDate): Double =
        paid(p, currency, from, toExclusive).sumOf { it.amount ?: 0.0 }

    /** Cũ → mới, gồm cả tháng bằng 0. */
    fun lastMonths(p: List<Payment>, currency: Currency, endMonth: YearMonth, count: Int): List<Pair<YearMonth, Double>> =
        (count - 1 downTo 0).map { back ->
            val m = endMonth.minusMonths(back.toLong())
            m to total(p, currency, m.atDay(1), m.plusMonths(1).atDay(1))
        }

    /** Giảm dần, bỏ danh mục bằng 0. */
    fun byCategory(p: List<Payment>, currency: Currency, from: LocalDate, toExclusive: LocalDate): List<Pair<Category, Double>> =
        paid(p, currency, from, toExclusive).groupBy { it.category }
            .map { (c, l) -> c to l.sumOf { it.amount ?: 0.0 } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
}
