package com.nhom4.subtrack.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
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

/**
 * Kiểm thử thêm / sửa / xóa trên SQLite thật (Robolectric chạy trên JVM, không cần máy ảo).
 * Mỗi test có một file subtrack.db mới.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubTrackDatabaseTest {
    private lateinit var context: Context
    private lateinit var db: SubTrackDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(SubTrackDatabase.DB_NAME)
        db = SubTrackDatabase(context)
    }

    @After
    fun tearDown() = db.close()

    private fun netflix(tagIds: List<Long> = emptyList()) = Subscription(
        name = "Netflix",
        amount = 260_000.0,
        currency = Currency.VND,
        cycle = BillingCycle.MONTHLY,
        nextPaymentDate = LocalDate.of(2026, 12, 5),
        category = Category.ENTERTAINMENT,
        remindDaysBefore = 3,
        managementUrl = "https://www.netflix.com/account",
        notes = "Gói Premium",
        tagIds = tagIds,
        platforms = listOf(Platform.ANDROID, Platform.SMART_TV),
    )

    private fun count(table: String): Int =
        db.readableDatabase.rawQuery("SELECT COUNT(*) FROM $table", null).use { it.moveToFirst(); it.getInt(0) }

    /** Giá trị thô của cột platforms trong SQLite. */
    private fun rawPlatforms(id: Long): String =
        db.readableDatabase.rawQuery("SELECT platforms FROM subscriptions WHERE id = ?", arrayOf(id.toString()))
            .use { it.moveToFirst(); it.getString(0) }

    private fun tagId(name: String) = db.getTags().first { it.name == name }.id

    @Test
    fun them_goi_luu_du_cac_truong_tag_va_nen_tang() {
        db.upsertTag(Tag(name = "Giải trí", color = 0xFFE5484D))
        val id = db.upsertSubscription(netflix(tagIds = listOf(tagId("Giải trí"))))

        val saved = db.getSubscriptions().single()
        assertTrue(id > 0)
        assertEquals(id, saved.id)
        assertEquals("Netflix", saved.name)
        assertEquals(260_000.0, saved.amount, 0.0)
        assertEquals(LocalDate.of(2026, 12, 5), saved.nextPaymentDate)
        assertEquals(3, saved.remindDaysBefore)
        assertEquals(listOf(Platform.ANDROID, Platform.SMART_TV), saved.platforms)
        assertEquals(listOf(tagId("Giải trí")), saved.tagIds)
        assertTrue(saved.createdAt > 0)
        assertEquals(1, count("subscription_tags"))
        assertEquals("android,smart_tv", rawPlatforms(id))
    }

    @Test
    fun sua_goi_cap_nhat_truong_va_thay_tag_nen_tang() {
        db.upsertTag(Tag(name = "Giải trí", color = 1))
        db.upsertTag(Tag(name = "Gia đình", color = 2))
        val id = db.upsertSubscription(netflix(tagIds = listOf(tagId("Giải trí"))))
        val before = db.getSubscriptions().single()
        Thread.sleep(5)

        db.upsertSubscription(
            before.copy(
                amount = 220_000.0,
                status = SubscriptionStatus.PAUSED,
                remindDaysBefore = null,
                notes = null,
                tagIds = listOf(tagId("Gia đình")),
                platforms = listOf(Platform.WEB),
            )
        )

        val after = db.getSubscriptions().single()
        assertEquals(id, after.id)
        assertEquals(220_000.0, after.amount, 0.0)
        assertEquals(SubscriptionStatus.PAUSED, after.status)
        assertNull(after.remindDaysBefore)
        assertNull(after.notes)
        assertEquals(listOf(tagId("Gia đình")), after.tagIds)
        assertEquals(listOf(Platform.WEB), after.platforms)
        assertEquals("web", rawPlatforms(id))
        assertEquals(before.createdAt, after.createdAt)
        assertTrue(after.updatedAt > before.updatedAt)
    }

    @Test
    fun xoa_goi_xoa_luon_lien_ket_nhung_giu_tag() {
        db.upsertTag(Tag(name = "Giải trí", color = 1))
        val id = db.upsertSubscription(netflix(tagIds = listOf(tagId("Giải trí"))))

        db.deleteSubscription(id)

        assertTrue(db.getSubscriptions().isEmpty())
        assertEquals(0, count("subscription_tags"))
        assertEquals(1, db.getTags().size)
    }

    @Test
    fun xoa_tag_chi_go_lien_ket_khong_xoa_goi() {
        db.upsertTag(Tag(name = "Giải trí", color = 1))
        db.upsertSubscription(netflix(tagIds = listOf(tagId("Giải trí"))))

        db.deleteTag(tagId("Giải trí"))

        val sub = db.getSubscriptions().single()
        assertTrue(sub.tagIds.isEmpty())
        assertTrue(db.getTags().isEmpty())
    }

    @Test
    fun them_sua_tag_chan_trung_ten_khong_phan_biet_hoa_thuong() {
        assertTrue(db.upsertTag(Tag(name = "Netflix", color = 1)))
        assertFalse(db.upsertTag(Tag(name = "netflix", color = 2)))
        assertTrue(db.upsertTag(Tag(name = "Học tập", color = 3)))

        val hocTap = db.getTags().first { it.name == "Học tập" }
        assertFalse(db.upsertTag(hocTap.copy(name = "NETFLIX")))
        assertTrue(db.upsertTag(hocTap.copy(name = "Học tiếng Anh", color = 4)))
        // Chữ có dấu tiếng Việt cũng không phân biệt hoa/thường
        assertTrue(db.upsertTag(Tag(name = "Đọc sách", color = 5)))
        assertFalse(db.upsertTag(Tag(name = "đọc sách", color = 6)))

        assertEquals(listOf("Học tiếng Anh", "Netflix", "Đọc sách"), db.getTags().map { it.name })
    }

    @Test
    fun du_lieu_con_sau_khi_mo_lai_database() {
        db.upsertSubscription(netflix())
        db.close()

        db = SubTrackDatabase(context)
        assertEquals("Netflix", db.getSubscriptions().single().name)
    }

    @Test
    fun xoa_toan_bo_du_lieu() {
        db.upsertTag(Tag(name = "Giải trí", color = 1))
        db.upsertSubscription(netflix(tagIds = listOf(tagId("Giải trí"))))
        db.upsertSubscription(netflix().copy(name = "Spotify"))

        db.clearAll()

        assertTrue(db.getSubscriptions().isEmpty())
        assertTrue(db.getTags().isEmpty())
        assertEquals(0, count("subscription_tags"))
    }

    @Test
    fun nang_cap_tu_version_1_giu_du_lieu() {
        db.close()
        context.deleteDatabase(SubTrackDatabase.DB_NAME)
        // Tạo file database theo lược đồ version 1 (nền tảng lưu chuỗi, tag phân biệt hoa/thường)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(SubTrackDatabase.DB_NAME), null).use { v1 ->
            v1.execSQL(
                """
                CREATE TABLE subscriptions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, amount REAL NOT NULL,
                    currency TEXT NOT NULL, billing_cycle TEXT NOT NULL, next_payment_date INTEGER NOT NULL,
                    category TEXT NOT NULL, status TEXT NOT NULL, remind_days_before INTEGER,
                    management_url TEXT, notes TEXT, platforms TEXT NOT NULL DEFAULT '',
                    created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)
                """.trimIndent()
            )
            v1.execSQL("CREATE TABLE tags (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, color INTEGER NOT NULL)")
            v1.execSQL(
                """
                CREATE TABLE subscription_tags (
                    subscription_id INTEGER NOT NULL REFERENCES subscriptions(id) ON DELETE CASCADE,
                    tag_id INTEGER NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
                    PRIMARY KEY (subscription_id, tag_id))
                """.trimIndent()
            )
            val date = LocalDate.of(2026, 12, 5).toEpochDay()
            v1.execSQL(
                "INSERT INTO subscriptions VALUES (7, 'Netflix', 260000, 'VND', 'MONTHLY', $date, 'entertainment', 'ACTIVE', 3, NULL, NULL, 'android,smart_tv,khong_ro', 1, 1)"
            )
            v1.execSQL("INSERT INTO tags VALUES (1, 'Giải trí', 1), (2, 'giải trí', 2)")
            v1.execSQL("INSERT INTO subscription_tags VALUES (7, 1), (7, 2)")
            v1.version = 1
        }

        db = SubTrackDatabase(context)
        val sub = db.getSubscriptions().single()

        assertEquals(7L, sub.id)
        assertEquals("Netflix", sub.name)
        assertEquals(listOf(Platform.ANDROID, Platform.SMART_TV), sub.platforms)
        assertEquals(listOf(Tag(1, "Giải trí", 1)), db.getTags())
        assertEquals(listOf(1L), sub.tagIds)
        assertEquals(SubTrackDatabase.DB_VERSION, db.readableDatabase.version)
        // Khóa ngoại vẫn hoạt động sau khi dựng lại bảng
        db.deleteSubscription(7)
        assertEquals(0, count("subscription_tags"))
        // Id mới không dùng lại id cũ (AUTOINCREMENT vẫn giữ bộ đếm)
        assertTrue(db.upsertSubscription(netflix()) > 7)
    }

    @Test
    fun nang_cap_tu_version_2_gop_nen_tang_ve_chuoi() {
        db.close()
        context.deleteDatabase(SubTrackDatabase.DB_NAME)
        // Lược đồ version 2: nền tảng nằm ở bảng subscription_platforms
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(SubTrackDatabase.DB_NAME), null).use { v2 ->
            v2.execSQL(
                """
                CREATE TABLE subscriptions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, amount REAL NOT NULL CHECK (amount >= 0),
                    currency TEXT NOT NULL, billing_cycle TEXT NOT NULL, next_payment_date INTEGER NOT NULL,
                    category TEXT NOT NULL, status TEXT NOT NULL, remind_days_before INTEGER,
                    management_url TEXT, notes TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)
                """.trimIndent()
            )
            v2.execSQL("CREATE TABLE tags (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL COLLATE NOCASE UNIQUE, color INTEGER NOT NULL)")
            v2.execSQL(
                """
                CREATE TABLE subscription_tags (
                    subscription_id INTEGER NOT NULL REFERENCES subscriptions(id) ON DELETE CASCADE,
                    tag_id INTEGER NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
                    PRIMARY KEY (subscription_id, tag_id))
                """.trimIndent()
            )
            v2.execSQL(
                """
                CREATE TABLE subscription_platforms (
                    subscription_id INTEGER NOT NULL REFERENCES subscriptions(id) ON DELETE CASCADE,
                    platform TEXT NOT NULL, PRIMARY KEY (subscription_id, platform))
                """.trimIndent()
            )
            val date = LocalDate.of(2026, 12, 5).toEpochDay()
            v2.execSQL("INSERT INTO subscriptions VALUES (3, 'Netflix', 260000, 'VND', 'MONTHLY', $date, 'entertainment', 'ACTIVE', 3, NULL, NULL, 1, 1)")
            v2.execSQL("INSERT INTO subscriptions VALUES (4, 'Spotify', 59000, 'VND', 'MONTHLY', $date, 'music', 'ACTIVE', 3, NULL, NULL, 1, 1)")
            v2.execSQL("INSERT INTO tags VALUES (1, 'Giải trí', 1)")
            v2.execSQL("INSERT INTO subscription_tags VALUES (3, 1)")
            v2.execSQL("INSERT INTO subscription_platforms VALUES (3, 'smart_tv'), (3, 'android')")
            v2.version = 2
        }

        db = SubTrackDatabase(context)
        val subs = db.getSubscriptions().associateBy { it.id }

        assertEquals(listOf(Platform.ANDROID, Platform.SMART_TV), subs.getValue(3).platforms)
        assertEquals("android,smart_tv", rawPlatforms(3))
        assertEquals("", rawPlatforms(4))
        assertEquals(listOf(1L), subs.getValue(3).tagIds)
        // Bảng nền tảng cũ đã bị xóa
        val tables = db.readableDatabase.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null)
            .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        assertFalse("subscription_platforms" in tables)
        // Khóa ngoại vẫn hoạt động
        db.deleteSubscription(3)
        assertEquals(0, count("subscription_tags"))
    }
}
