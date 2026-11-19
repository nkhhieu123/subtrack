package com.nhom4.subtrack.domain

import com.nhom4.subtrack.data.Currency

/** Quy đổi qua tỷ giá theo USD (số đơn vị tiền tệ cho 1 USD). */
object CurrencyConverter {
    /** null nếu khác tiền tệ mà chưa có tỷ giá. */
    fun convert(amount: Double, from: Currency, to: Currency, perUsd: Map<Currency, Double>?): Double? {
        if (from == to) return amount
        val rFrom = perUsd?.get(from) ?: return null
        val rTo = perUsd[to] ?: return null
        return amount / rFrom * rTo
    }
}
