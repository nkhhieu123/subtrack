package com.nhom4.subtrack.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nhom4.subtrack.domain.Dataset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DatasetRegistryTest {
    private lateinit var context: Context
    private lateinit var registry: DatasetRegistry
    private val day = LocalDate.of(2026, 11, 24)

    private fun sub(name: String, id: Long = 0, tags: List<Long> = emptyList()) = Subscription(
        id = id, name = name, amount = 100_000.0, currency = Currency.VND, cycle = BillingCycle.MONTHLY,
        nextPaymentDate = day, category = Category.OTHER, tagIds = tags,
    )

    private val imported = Dataset(
        subscriptions = listOf(sub("Bạn A", id = 5, tags = listOf(1))),
        tags = listOf(Tag(1, "Chia sẻ", 0xFF0090FF)),
        payments = listOf(
            Payment(subscriptionId = 5, subscriptionName = "Bạn A", category = Category.OTHER, periodDate = day.minusMonths(1),
                kind = PaymentKind.PAID, paidDate = day.minusMonths(1), amount = 100_000.0, currency = Currency.VND),
        ),
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("datasets", Context.MODE_PRIVATE).edit().clear().commit()
        context.databaseList().forEach { context.deleteDatabase(it) }
        registry = DatasetRegistry(context)
        runBlocking { registry.repository.save(sub("Của tôi")) }
    }

    @After
    fun tearDown() = runBlocking { registry.switchTo(DatasetRegistry.MAIN_ID) }

    @Test
    fun createAndSwitch_keepsOriginalUntouched() = runBlocking {
        val info = registry.create("Từ máy bạn", imported, activate = true)
        assertEquals(info.id, registry.state.value.activeId)
        val repo = registry.repository
        assertEquals(listOf("Bạn A"), repo.subscriptions.value!!.map { it.name })
        val s = repo.subscriptions.value!!.single()
        assertEquals(listOf("Chia sẻ"), s.tagIds.map { id -> repo.tags.value.first { it.id == id }.name })
        assertEquals(s.id, repo.payments.value.single().subscriptionId)

        repo.save(s.copy(name = "Bạn A sửa")) // bộ phụ sửa được
        registry.switchTo(DatasetRegistry.MAIN_ID)
        assertEquals(listOf("Của tôi"), repo.subscriptions.value!!.map { it.name })
        assertEquals(1, registry.count(info))
    }

    @Test
    fun saveOnly_doesNotSwitch_andSurvivesRestart() = runBlocking {
        val info = registry.create("Chỉ lưu", imported, activate = false)
        assertEquals(DatasetRegistry.MAIN_ID, registry.state.value.activeId)
        registry.switchTo(info.id)
        registry.rename(info.id, "  Đổi tên  ")
        val again = DatasetRegistry(context)
        assertEquals(info.id, again.state.value.activeId)
        assertEquals("Đổi tên", again.state.value.active.name)
        again.repository.refresh()
        assertEquals(listOf("Bạn A"), again.repository.subscriptions.value!!.map { it.name })
        again.switchTo(DatasetRegistry.MAIN_ID)
        registry.switchTo(DatasetRegistry.MAIN_ID)
    }

    @Test
    fun delete_rules() = runBlocking {
        val info = registry.create("Tạm", imported, activate = true)
        assertFalse(registry.delete(DatasetRegistry.MAIN_ID))
        assertFalse(registry.delete(info.id)) // đang dùng
        registry.switchTo(DatasetRegistry.MAIN_ID)
        assertTrue(registry.delete(info.id))
        assertNull(registry.state.value.list.firstOrNull { it.id == info.id })
        assertFalse(context.databaseList().contains(info.file))
    }

    @Test
    fun failedImport_leavesNothing() = runBlocking {
        val bad = imported.copy(subscriptions = listOf(sub("Âm", id = 5).copy(amount = -1.0))) // CHECK amount >= 0
        val r = runCatching { registry.create("Hỏng", bad, activate = true) }
        assertTrue(r.isFailure)
        assertEquals(1, registry.state.value.list.size)
        assertEquals(DatasetRegistry.MAIN_ID, registry.state.value.activeId)
        assertTrue(context.databaseList().none { it.startsWith("ds_") })
    }
}
