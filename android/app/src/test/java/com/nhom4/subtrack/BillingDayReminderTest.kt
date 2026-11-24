package com.nhom4.subtrack

import com.nhom4.subtrack.data.BillingCycle
import com.nhom4.subtrack.domain.BillingMath
import com.nhom4.subtrack.reminder.ReminderTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class BillingDayReminderTest {

    // ---------- Ngày gốc ----------

    private fun chain(start: LocalDate, cycle: BillingCycle, n: Int): List<LocalDate> =
        generateSequence(start) { BillingMath.advance(it, cycle, start.dayOfMonth) }.take(n).toList()

    @Test
    fun billingDay31_doesNotDrift() {
        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 4, 30), LocalDate.of(2026, 5, 31),
            ),
            chain(LocalDate.of(2026, 1, 31), BillingCycle.MONTHLY, 5),
        )
    }

    @Test
    fun billingDay_quarterlyAndLeapYear() {
        assertEquals(
            listOf(LocalDate.of(2025, 11, 30), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 5, 30)),
            chain(LocalDate.of(2025, 11, 30), BillingCycle.QUARTERLY, 3),
        )
        assertEquals(
            listOf(LocalDate.of(2028, 2, 29), LocalDate.of(2029, 2, 28), LocalDate.of(2030, 2, 28), LocalDate.of(2031, 2, 28), LocalDate.of(2032, 2, 29)),
            chain(LocalDate.of(2028, 2, 29), BillingCycle.YEARLY, 5),
        )
        // Tuần không dùng ngày gốc
        assertEquals(LocalDate.of(2026, 2, 7), BillingMath.advance(LocalDate.of(2026, 1, 31), BillingCycle.WEEKLY, 31))
    }

    @Test
    fun nextOnOrAfter_usesBillingDay() {
        val today = LocalDate.of(2026, 3, 10)
        // Kỳ đang lưu đã lùi về 28/02 nhưng ngày gốc là 31 → kỳ tới 31/03, không phải 28/03
        assertEquals(LocalDate.of(2026, 3, 31), BillingMath.nextOnOrAfter(LocalDate.of(2026, 2, 28), BillingCycle.MONTHLY, today, 31))
        assertEquals(LocalDate.of(2026, 3, 28), BillingMath.nextOnOrAfter(LocalDate.of(2026, 2, 28), BillingCycle.MONTHLY, today))
    }

    // ---------- Giờ nhắc ----------

    @Test
    fun reminderDelay_targetsNext8am() {
        val day = LocalDate.of(2026, 11, 18)
        assertEquals(Duration.ofHours(2), ReminderTime.delayUntilNext(day.atTime(6, 0)))
        assertEquals(Duration.ofHours(23), ReminderTime.delayUntilNext(day.atTime(9, 0)))
        // Đúng 8:00 thì hẹn sáng mai, không chạy lại ngay
        assertEquals(Duration.ofDays(1), ReminderTime.delayUntilNext(day.atTime(8, 0)))
        assertEquals(Duration.ofMinutes(30), ReminderTime.delayUntilNext(LocalDateTime.of(2026, 12, 31, 7, 30)))
    }

    @Test
    fun immediateCheck_isQuietAtNight() {
        assertFalse(ReminderTime.canNotifyNow(LocalTime.of(7, 59)))
        assertTrue(ReminderTime.canNotifyNow(LocalTime.of(8, 0)))
        assertTrue(ReminderTime.canNotifyNow(LocalTime.of(21, 59)))
        assertFalse(ReminderTime.canNotifyNow(LocalTime.of(23, 30)))
    }
}
