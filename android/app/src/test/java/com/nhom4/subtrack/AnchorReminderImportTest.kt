package com.nhom4.subtrack

import com.nhom4.subtrack.data.BillingCycle
import com.nhom4.subtrack.data.Category
import com.nhom4.subtrack.data.Currency
import com.nhom4.subtrack.data.Platform
import com.nhom4.subtrack.data.SubscriptionStatus
import com.nhom4.subtrack.domain.BillingMath
import com.nhom4.subtrack.domain.CsvImportResult
import com.nhom4.subtrack.domain.CsvImporter
import com.nhom4.subtrack.domain.CsvReader
import com.nhom4.subtrack.domain.CsvVocabulary
import com.nhom4.subtrack.domain.CsvWriter
import com.nhom4.subtrack.domain.ImportProblem
import com.nhom4.subtrack.domain.RowError
import com.nhom4.subtrack.reminder.ReminderTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class AnchorReminderImportTest {

    // ---------- Ngày neo ----------

    private fun chain(start: LocalDate, cycle: BillingCycle, n: Int): List<LocalDate> =
        generateSequence(start) { BillingMath.advance(it, cycle, start.dayOfMonth) }.take(n).toList()

    @Test
    fun anchor31_doesNotDrift() {
        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 4, 30), LocalDate.of(2026, 5, 31),
            ),
            chain(LocalDate.of(2026, 1, 31), BillingCycle.MONTHLY, 5),
        )
    }

    @Test
    fun anchor_quarterlyAndLeapYear() {
        assertEquals(
            listOf(LocalDate.of(2025, 11, 30), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 5, 30)),
            chain(LocalDate.of(2025, 11, 30), BillingCycle.QUARTERLY, 3),
        )
        assertEquals(
            listOf(LocalDate.of(2028, 2, 29), LocalDate.of(2029, 2, 28), LocalDate.of(2030, 2, 28), LocalDate.of(2031, 2, 28), LocalDate.of(2032, 2, 29)),
            chain(LocalDate.of(2028, 2, 29), BillingCycle.YEARLY, 5),
        )
        // Tuần không dùng ngày neo
        assertEquals(LocalDate.of(2026, 2, 7), BillingMath.advance(LocalDate.of(2026, 1, 31), BillingCycle.WEEKLY, 31))
    }

    @Test
    fun anchorFor_keepsOldAnchorOnlyWhenDateIsItsMonthEnd() {
        assertEquals(31, BillingMath.anchorFor(LocalDate.of(2026, 2, 28), 31))
        assertEquals(31, BillingMath.anchorFor(LocalDate.of(2026, 3, 31), 31))
        assertEquals(15, BillingMath.anchorFor(LocalDate.of(2026, 3, 15), 31))
        assertEquals(28, BillingMath.anchorFor(LocalDate.of(2026, 2, 28), null))
    }

    @Test
    fun nextOnOrAfter_usesAnchor() {
        val today = LocalDate.of(2026, 3, 10)
        assertEquals(LocalDate.of(2026, 3, 31), BillingMath.nextOnOrAfter(LocalDate.of(2026, 1, 31), BillingCycle.MONTHLY, today, 31))
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

    // ---------- Nhập CSV ----------

    private val header = "Tên dịch vụ,Số tiền,Tiền tệ,Chu kỳ,Ngày thanh toán tiếp theo,Danh mục,Trạng thái,Nhắc trước (ngày),Tag,Nền tảng,Liên kết quản lý,Ghi chú"

    private val vocab = CsvVocabulary(
        headers = header.split(','),
        cycles = CsvVocabulary.labels(BillingCycle.entries, { mapOf(BillingCycle.MONTHLY to "Hằng tháng", BillingCycle.YEARLY to "Hằng năm")[it] ?: it.name }),
        categories = CsvVocabulary.labels(Category.entries, { mapOf(Category.ENTERTAINMENT to "Giải trí", Category.OTHER to "Khác")[it] ?: it.name }, { it.key }),
        statuses = CsvVocabulary.labels(SubscriptionStatus.entries, { if (it == SubscriptionStatus.PAUSED) "Tạm dừng" else it.name }),
        platforms = CsvVocabulary.labels(Platform.entries, { if (it == Platform.SMART_TV) "Smart TV" else it.name }, { it.key }),
        otherPlatformsNote = "Nền tảng khác: %s",
    )
    private val importer = CsvImporter(vocab, Currency.VND, LocalDate.of(2026, 11, 18))

    private fun parsed(text: String) = importer.parse(text) as CsvImportResult.Parsed

    @Test
    fun csvReader_handlesQuotesNewlinesAndBom() {
        val rows = CsvReader.parse("﻿a,b\r\n\"x, y\",\"say \"\"hi\"\"\nline2\"\r\n\r\n")
        assertEquals(listOf(listOf("a", "b"), listOf("x, y", "say \"hi\"\nline2")), rows)
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), CsvReader.parse("a;b\n1;2"))
    }

    @Test
    fun import_roundTripsExportFormat() {
        val csv = CsvWriter.build(header, listOf(
            listOf("Netflix", "260000", "VND", "Hằng tháng", "31/01/2027", "Giải trí", "Tạm dừng", "", "Phim; Gia đình", "Smart TV; Android", "https://netflix.com", "Gói, gia đình"),
            listOf("Gym", "20.5", "USD", "Hằng năm", "05/12/2026", "Thể thao", "", "7", "", "Garmin", "", ""),
        ))
        val r = parsed(csv)
        assertTrue(r.errors.isEmpty())
        val (netflix, gym) = r.rows.map { it.sub }
        assertEquals(260_000.0, netflix.amount, 0.0)
        assertEquals(LocalDate.of(2027, 1, 31), netflix.nextPaymentDate)
        assertEquals(31, netflix.billingAnchorDay)
        assertEquals(SubscriptionStatus.PAUSED, netflix.status)
        assertNull(netflix.remindDaysBefore)
        assertEquals(listOf(Platform.ANDROID, Platform.SMART_TV), netflix.platforms)
        assertEquals("Gói, gia đình", netflix.notes)
        assertEquals(listOf("Phim", "Gia đình"), r.rows[0].tagNames)

        assertEquals(Currency.USD, gym.currency)
        assertEquals(20.5, gym.amount, 0.0)
        assertEquals(BillingCycle.YEARLY, gym.cycle)
        assertEquals(Category.OTHER, gym.category)
        assertEquals("Thể thao", gym.customCategory)
        assertEquals(7, gym.remindDaysBefore)
        // Nền tảng ngoài danh sách → "Khác" và giữ tên trong ghi chú
        assertEquals(listOf(Platform.OTHER), gym.platforms)
        assertEquals("Nền tảng khác: Garmin", gym.notes)
    }

    @Test
    fun import_minimalColumnsUseDefaults() {
        val r = parsed("name;amount\nSpotify;59.000 ₫\n")
        val s = r.rows.single().sub
        assertEquals(59_000.0, s.amount, 0.0)
        assertEquals(Currency.VND, s.currency)
        assertEquals(BillingCycle.MONTHLY, s.cycle)
        assertEquals(LocalDate.of(2026, 12, 18), s.nextPaymentDate)
        assertEquals(3, s.remindDaysBefore)
    }

    @Test
    fun import_reportsBadRowsAndFormat() {
        val r = parsed("Tên dịch vụ,Số tiền,Tiền tệ,Ngày thanh toán tiếp theo\n,100,VND,01/01/2027\nA,abc,VND,01/01/2027\nB,5,JPY,01/01/2027\nC,5,VND,31/02/2027\nD,5,VND,01/01/2027\n")
        assertEquals(listOf("D"), r.rows.map { it.sub.name })
        assertEquals(
            listOf(RowError(2, ImportProblem.NAME), RowError(3, ImportProblem.AMOUNT), RowError(4, ImportProblem.CURRENCY), RowError(5, ImportProblem.DATE)),
            r.errors,
        )
        assertEquals(CsvImportResult.BadFormat, importer.parse("foo,bar\n1,2"))
        assertEquals(CsvImportResult.BadFormat, importer.parse(""))
    }
}
