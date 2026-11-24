package com.nhom4.subtrack.domain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nhom4.subtrack.data.BillingCycle
import com.nhom4.subtrack.data.Category
import com.nhom4.subtrack.data.Currency
import com.nhom4.subtrack.data.Payment
import com.nhom4.subtrack.data.PaymentKind
import com.nhom4.subtrack.data.Platform
import com.nhom4.subtrack.data.Subscription
import com.nhom4.subtrack.data.SubscriptionStatus
import com.nhom4.subtrack.data.Tag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DatasetCsvTest {
    private lateinit var csv: DatasetCsv
    private val day = LocalDate.of(2026, 11, 24)

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        csv = DatasetCsv(ctx::getString)
    }

    private val sample = Dataset(
        subscriptions = listOf(
            Subscription(
                id = 7, name = "Netflix, \"4K\"", amount = 260_000.0, currency = Currency.VND, cycle = BillingCycle.MONTHLY,
                nextPaymentDate = day, category = Category.ENTERTAINMENT, status = SubscriptionStatus.PAUSED,
                remindDaysBefore = null, managementUrl = "https://netflix.com", notes = "dòng 1\ndòng 2, có phẩy",
                tagIds = listOf(1, 2), platforms = listOf(Platform.ANDROID, Platform.SMART_TV),
            ),
            Subscription(
                id = 9, name = "ChatGPT", amount = 1234.5, currency = Currency.USD, cycle = BillingCycle.YEARLY,
                nextPaymentDate = day.plusDays(30), category = Category.WORK,
            ),
        ),
        tags = listOf(Tag(1, "Gia đình", 0xFF30A46C), Tag(2, "Đọc sách", 0xFFD6409F)),
        payments = listOf(
            Payment(subscriptionId = 7, subscriptionName = "Netflix, \"4K\"", category = Category.ENTERTAINMENT,
                periodDate = day.minusMonths(1), kind = PaymentKind.PAID, paidDate = day.minusMonths(1).plusDays(1),
                amount = 260_000.0, currency = Currency.VND),
            Payment(subscriptionId = 9, subscriptionName = "ChatGPT", category = Category.WORK,
                periodDate = day.minusYears(1), kind = PaymentKind.SKIPPED, paidDate = null, amount = null, currency = null),
            Payment(subscriptionId = null, subscriptionName = "Gói đã xoá", category = Category.MUSIC,
                periodDate = day.minusDays(40), kind = PaymentKind.PAID, paidDate = day.minusDays(40), amount = 9.99, currency = Currency.EUR),
        ),
    )

    @Test
    fun roundTrip_keepsEverything() {
        val parsed = csv.read(csv.write(sample))
        assertEquals(0, parsed.skipped)
        val d = parsed.data
        val tagNames = d.tags.associate { it.id to it.name }
        val netflix = d.subscriptions.first { it.name == "Netflix, \"4K\"" }
        assertEquals(sample.subscriptions[0].copy(id = netflix.id, tagIds = netflix.tagIds), netflix)
        assertEquals(listOf("Gia đình", "Đọc sách"), netflix.tagIds.map { tagNames[it] })
        assertEquals(0xFFD6409F, d.tags.first { it.name == "Đọc sách" }.color)
        val gpt = d.subscriptions.first { it.name == "ChatGPT" }
        assertEquals(1234.5, gpt.amount, 0.0)
        assertEquals(SubscriptionStatus.ACTIVE, gpt.status)
        assertEquals(3, gpt.remindDaysBefore)

        assertEquals(3, d.payments.size)
        val paid = d.payments.first { it.kind == PaymentKind.PAID && it.subscriptionId != null }
        assertEquals(netflix.id, paid.subscriptionId) // nối theo cột Mã
        assertEquals(sample.payments[0].paidDate, paid.paidDate)
        val skipped = d.payments.first { it.kind == PaymentKind.SKIPPED }
        assertEquals(gpt.id, skipped.subscriptionId)
        assertNull(skipped.amount)
        val orphan = d.payments.first { it.subscriptionId == null }
        assertEquals(9.99, orphan.amount!!, 0.0)
        assertEquals(Currency.EUR, orphan.currency)
    }

    @Test
    fun readsOldExportFormat() {
        val old = Char(0xFEFF) + "Tên dịch vụ,Số tiền,Tiền tệ,Chu kỳ,Ngày thanh toán tiếp theo,Danh mục,Trạng thái,Nhắc trước (ngày),Tag,Nền tảng,Liên kết quản lý,Ghi chú\r\n" +
            "Spotify,59000,VND,MONTHLY,05/12/2026,music,ACTIVE,3,Nhạc; Gia đình,android; web,,\r\n" +
            "iCloud,\"0,99\",USD,MONTHLY,01/12/2026,storage,,,,,,\r\n"
        val parsed = csv.read(old)
        assertEquals(2, parsed.data.subscriptions.size)
        val spotify = parsed.data.subscriptions.first { it.name == "Spotify" }
        assertEquals(listOf(Platform.ANDROID, Platform.WEB), spotify.platforms)
        assertEquals(Category.MUSIC, spotify.category)
        assertEquals(2, spotify.tagIds.size)
        assertEquals(0.99, parsed.data.subscriptions.first { it.name == "iCloud" }.amount, 0.0)
        assertEquals(LocalDate.of(2026, 12, 5), spotify.nextPaymentDate)
    }

    @Test
    fun badRowsSkipped_emptyFileRejected() {
        val text = "Tên dịch vụ,Số tiền,Tiền tệ,Chu kỳ,Ngày thanh toán tiếp theo\n" +
            "Tốt,100000,VND,MONTHLY,2026-12-01\n" +
            "Sai tiền,9.99,USD,MONTHLY,2026-12-01\n" + // kiểu Mỹ bị từ chối
            "Sai ngày,1000,VND,MONTHLY,31/02/2026\n" +
            ",1000,VND,MONTHLY,2026-12-01\n"
        val parsed = csv.read(text)
        assertEquals(listOf("Tốt"), parsed.data.subscriptions.map { it.name })
        assertEquals(3, parsed.skipped)
        assertThrows(IllegalArgumentException::class.java) { csv.read("xin chào\n") }
    }

    @Test
    fun parseRows_quotesAndNewlines() {
        val rows = DatasetCsv.parseRows("a,\"b,\"\"c\"\"\",\"x\r\ny\"\r\n,\n")
        assertEquals(listOf(listOf("a", "b,\"c\"", "x\r\ny"), listOf("", "")), rows)
    }
}
