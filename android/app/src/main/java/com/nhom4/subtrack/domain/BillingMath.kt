package com.nhom4.subtrack.domain

import com.nhom4.subtrack.data.Currency

/** Quy tắc kiểm tra dữ liệu nhập, tách khỏi Android để kiểm thử bằng JUnit. */
object BillingMath {

    /**
     * Đọc số tiền người dùng nhập. VND không có phần thập phân nên bỏ mọi dấu
     * phân cách; USD/EUR chấp nhận cả "4.99" và "4,99". Trả về null nếu sai.
     */
    fun parseAmount(input: String, currency: Currency): Double? {
        val raw = input.trim().replace(" ", "")
        if (raw.isEmpty() || !raw.matches(Regex("""[\d.,]+"""))) return null
        // Dấu cuối cùng theo sau bởi 1–2 chữ số là dấu thập phân (không áp dụng cho VND)
        val lastSep = maxOf(raw.lastIndexOf('.'), raw.lastIndexOf(','))
        val hasDecimal = currency != Currency.VND && lastSep >= 0 && raw.length - lastSep - 1 in 1..2
        val intPart = if (hasDecimal) raw.substring(0, lastSep) else raw
        val fraction = if (hasDecimal) raw.substring(lastSep + 1) else ""
        if (!isGroupedInteger(intPart)) return null
        val digits = intPart.replace(".", "").replace(",", "")
        return (if (fraction.isEmpty()) digits else "$digits.$fraction").toDoubleOrNull()
    }

    /** "1234", "1.234", "1,234,567" hợp lệ; "1.2.3" thì không. */
    private fun isGroupedInteger(s: String): Boolean {
        if (s.isEmpty()) return false
        val groups = s.split('.', ',')
        if (groups.size == 1) return groups[0].all { it.isDigit() }
        return groups[0].length in 1..3 && groups.drop(1).all { it.length == 3 } && groups.all { g -> g.all { it.isDigit() } }
    }

    fun isValidUrl(url: String): Boolean =
        Regex("""^https?://[^\s/$.?#].[^\s]*$""", RegexOption.IGNORE_CASE).matches(url.trim())
}
