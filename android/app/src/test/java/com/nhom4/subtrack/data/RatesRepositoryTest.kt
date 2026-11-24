package com.nhom4.subtrack.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RatesRepositoryTest {
    private lateinit var context: Context
    private val day = LocalDate.of(2026, 11, 19)

    private val ok = """{"result":"success","base_code":"USD","time_last_update_unix":1,
        |"rates":{"USD":1,"VND":25350.5,"EUR":0.92,"GBP":0.8}}""".trimMargin()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("rates", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun parse_successAndErrors() {
        val r = RatesRepository.parse(ok)!!
        assertEquals(25350.5, r[Currency.VND]!!, 0.0)
        assertEquals(0.92, r[Currency.EUR]!!, 0.0)
        assertNull(RatesRepository.parse("""{"result":"error","error-type":"unsupported-code"}"""))
        assertNull(RatesRepository.parse("""{"result":"success","base_code":"USD","rates":{"USD":1,"EUR":0.9}}""")) // thiếu VND
        assertNull(RatesRepository.parse("""{"result":"success","base_code":"USD","rates":{"USD":1,"VND":0,"EUR":0.9}}"""))
        assertNull(RatesRepository.parse("<html>429</html>"))
    }

    @Test
    fun sync_savesAndSurvivesRestart() = runBlocking {
        val repo = RatesRepository(context, fetch = { ok }, today = { day })
        assertTrue(repo.sync())
        assertEquals(day, repo.state.value.fetchedOn)
        assertFalse(repo.state.value.failed)
        // Mở lại app (không mạng): vẫn còn tỷ giá cũ
        val again = RatesRepository(context, fetch = { throw IOException("offline") }, today = { day.plusDays(1) })
        assertEquals(25350.5, again.state.value.perUsd!![Currency.VND]!!, 0.0)
        assertEquals(day, again.state.value.fetchedOn)
    }

    @Test
    fun sync_failureKeepsOldRatesAndFlagsError() = runBlocking {
        RatesRepository(context, fetch = { ok }, today = { day }).sync()
        val repo = RatesRepository(context, fetch = { throw IOException("offline") }, today = { day.plusDays(1) })
        assertFalse(repo.sync())
        val s = repo.state.value
        assertTrue(s.failed)
        assertFalse(s.syncing)
        assertEquals(day, s.fetchedOn) // vẫn dùng tỷ giá hôm trước
        assertEquals(0.92, s.perUsd!![Currency.EUR]!!, 0.0)
    }

    @Test
    fun sync_ignoresSecondCallWhileRunning() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val repo = RatesRepository(context, fetch = { calls++; gate.await(); ok }, today = { day })
        val first = async(Dispatchers.IO) { repo.sync() }
        while (!repo.state.value.syncing) Thread.sleep(5)
        assertFalse(repo.sync()) // đang lấy: bỏ qua, không gọi API lần nữa
        gate.complete(Unit)
        assertTrue(first.await())
        assertEquals(1, calls)
        assertFalse(repo.state.value.syncing)
    }
}
