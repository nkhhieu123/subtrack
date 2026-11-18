package com.nhom4.subtrack.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

/**
 * Kịch bản DEMO thêm / sửa / xóa cho báo cáo tuần 3 (chưa cần giao diện, không cần máy ảo).
 *
 * Cách chạy: Android Studio → bấm ▶ cạnh tên lớp → Run 'CrudDemoTest'.
 * Sau mỗi bước, nội dung các bảng được in ra cửa sổ Run. Cuối cùng file CSDL được chép ra
 * android/app/build/demo/subtrack.db để mở bằng DB Browser for SQLite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CrudDemoTest {

    @Test
    fun demo_them_sua_xoa() {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(SubTrackDatabase.DB_NAME)
        val db = SubTrackDatabase(context)

        step("0. Tạo CSDL trống – các bảng có trong subtrack.db")
        db.readableDatabase.rawQuery(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata' ORDER BY name",
            null,
        ).use { c -> while (c.moveToNext()) println("   - " + c.getString(0)) }

        step("1. THÊM: 2 tag và 2 gói đăng ký")
        db.upsertTag(Tag(name = "Giải trí", color = 0xFFE5484D))
        db.upsertTag(Tag(name = "Gia đình", color = 0xFF30A46C))
        val giaiTri = db.getTags().first { it.name == "Giải trí" }.id
        val giaDinh = db.getTags().first { it.name == "Gia đình" }.id
        val netflixId = db.upsertSubscription(
            Subscription(
                name = "Netflix", amount = 260_000.0, currency = Currency.VND, cycle = BillingCycle.MONTHLY,
                nextPaymentDate = LocalDate.of(2026, 12, 5), category = Category.ENTERTAINMENT,
                tagIds = listOf(giaiTri, giaDinh), platforms = listOf(Platform.ANDROID, Platform.SMART_TV),
                managementUrl = "https://www.netflix.com/account",
            )
        )
        val spotifyId = db.upsertSubscription(
            Subscription(
                name = "Spotify", amount = 59_000.0, currency = Currency.VND, cycle = BillingCycle.MONTHLY,
                nextPaymentDate = LocalDate.of(2026, 12, 12), category = Category.MUSIC,
                tagIds = listOf(giaiTri), platforms = listOf(Platform.ANDROID, Platform.WINDOWS),
            )
        )
        dumpAll(db)

        step("2. KIỂM TRA RÀNG BUỘC: thêm tag \"gia đình\" (trùng, khác hoa/thường)")
        val ok = db.upsertTag(Tag(name = "gia đình", color = 0))
        println("   upsertTag trả về: $ok  → bị từ chối, bảng tags không đổi")
        assertFalse(ok)
        dump(db, "tags")

        step("3. SỬA: Netflix 260.000 → 220.000, tạm dừng, chỉ còn nền tảng Web, bỏ tag Gia đình")
        val netflix = db.getSubscriptions().first { it.id == netflixId }
        db.upsertSubscription(
            netflix.copy(
                amount = 220_000.0, status = SubscriptionStatus.PAUSED,
                tagIds = listOf(giaiTri), platforms = listOf(Platform.WEB),
            )
        )
        dumpAll(db)

        step("4. XÓA TAG \"Giải trí\": chỉ gỡ liên kết, các gói vẫn còn")
        db.deleteTag(giaiTri)
        dump(db, "tags")
        dump(db, "subscription_tags")
        dump(db, "subscriptions", "id, name, amount, status")

        step("5. XÓA GÓI Spotify: liên kết tag tự xóa theo (ON DELETE CASCADE)")
        db.deleteSubscription(spotifyId)
        dumpAll(db)

        assertEquals(listOf("Netflix"), db.getSubscriptions().map { it.name })

        // Xuất file CSDL để mở bằng DB Browser for SQLite
        val src = context.getDatabasePath(SubTrackDatabase.DB_NAME)
        db.close()
        val out = File("build/demo/subtrack.db").absoluteFile
        out.parentFile?.mkdirs()
        src.copyTo(out, overwrite = true)
        step("Đã xuất CSDL: ${out.path}")
    }

    private fun step(title: String) {
        println()
        println("=".repeat(78))
        println(title)
        println("=".repeat(78))
    }

    private fun dumpAll(db: SubTrackDatabase) {
        dump(db, "subscriptions", "id, name, amount, currency, status, date(next_payment_date * 86400, 'unixepoch') AS next_payment, platforms")
        dump(db, "tags")
        dump(db, "subscription_tags")
    }

    /** In một bảng dạng lưới. */
    private fun dump(db: SubTrackDatabase, table: String, columns: String = "*") {
        db.readableDatabase.rawQuery("SELECT $columns FROM $table", null).use { c ->
            val headers = c.columnNames.toList()
            val rows = buildList {
                while (c.moveToNext()) add(headers.indices.map { i -> if (c.isNull(i)) "NULL" else c.getString(i) })
            }
            val widths = headers.indices.map { i -> (rows.map { it[i].length } + headers[i].length).max() }
            fun line(cells: List<String>) = cells.mapIndexed { i, s -> s.padEnd(widths[i]) }.joinToString(" | ", "| ", " |")
            println("-- $table (${rows.size} dòng)")
            println(line(headers))
            println(widths.joinToString("-+-", "|-", "-|") { "-".repeat(it) })
            rows.forEach { println(line(it)) }
            if (rows.isEmpty()) println("   (trống)")
        }
    }
}
