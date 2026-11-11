package com.nhom4.subtrack

import com.nhom4.subtrack.data.Currency
import com.nhom4.subtrack.domain.BillingMath
import com.nhom4.subtrack.ui.formatMoney
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kiểm tra các quy tắc dữ liệu nhập dùng trước khi thêm/sửa gói. */
class BillingMathTest {

    @Test
    fun parseAmount_vnd() {
        assertEquals(260_000.0, BillingMath.parseAmount("260000", Currency.VND)!!, 0.0)
        assertEquals(260_000.0, BillingMath.parseAmount("260.000", Currency.VND)!!, 0.0)
        assertEquals(1_190_000.0, BillingMath.parseAmount("1,190,000", Currency.VND)!!, 0.0)
        assertEquals(0.0, BillingMath.parseAmount("0", Currency.VND)!!, 0.0)
        assertNull(BillingMath.parseAmount("-5000", Currency.VND))
        assertNull(BillingMath.parseAmount("abc", Currency.VND))
        assertNull(BillingMath.parseAmount("  ", Currency.VND))
    }

    @Test
    fun parseAmount_usd() {
        assertEquals(9.99, BillingMath.parseAmount("9.99", Currency.USD)!!, 0.0)
        assertEquals(9.99, BillingMath.parseAmount("9,99", Currency.USD)!!, 0.0)
        assertEquals(1234.5, BillingMath.parseAmount("1,234.5", Currency.USD)!!, 0.0)
        assertEquals(12.0, BillingMath.parseAmount("12", Currency.USD)!!, 0.0)
        assertNull(BillingMath.parseAmount("1.2.3.4", Currency.USD))
    }

    @Test
    fun urlValidation() {
        assertTrue(BillingMath.isValidUrl("https://www.netflix.com/account"))
        assertTrue(BillingMath.isValidUrl("http://example.com"))
        assertFalse(BillingMath.isValidUrl("netflix.com"))
        assertFalse(BillingMath.isValidUrl("https://"))
    }

    @Test
    fun money_isFormattedVietnamese() {
        val vnd = formatMoney(260_000.0, Currency.VND)
        assertTrue(vnd, vnd.contains("260.000") && vnd.contains("₫"))
        val usd = formatMoney(9.99, Currency.USD)
        assertTrue(usd, usd.contains("9,99"))
    }
}
