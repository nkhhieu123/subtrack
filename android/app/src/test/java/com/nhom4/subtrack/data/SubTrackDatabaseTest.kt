package com.nhom4.subtrack.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
// Application mặc định để không chạy SubTrackApp (WorkManager) trong test DB
@Config(sdk = [34], application = android.app.Application::class)
class SubTrackDatabaseTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(SubTrackDatabase.DB_NAME)
    }

    private val d1 = LocalDate.of(2026, 3, 1)
    private val d2 = LocalDate.of(2026, 3, 2)

    private fun sub(notes: String? = null, next: LocalDate = d1) = Subscription(
        name = "Netflix", amount = 260000.0, currency = Currency.VND, cycle = BillingCycle.MONTHLY,
        nextPaymentDate = next, category = Category.ENTERTAINMENT, notes = notes,
    )

    private fun payment(subId: Long?) = Payment(
        subscriptionId = subId, subscriptionName = "Netflix", category = Category.ENTERTAINMENT,
        periodDate = d1, kind = PaymentKind.PAID, paidDate = d1, amount = 260000.0, currency = Currency.VND,
    )

    private fun userVersion(db: SQLiteDatabase) =
        db.rawQuery("PRAGMA user_version", null).use { it.moveToFirst(); it.getInt(0) }

    private fun columns(db: SQLiteDatabase, table: String) =
        db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
        }

    @Test
    fun freshInstall_hasV5Schema() {
        val helper = SubTrackDatabase(context)
        val db = helper.writableDatabase
        assertEquals(5, userVersion(db))
        assertTrue(columns(db, "payments").isNotEmpty())
        val cols = columns(db, "subscriptions")
        assertTrue("snoozed_until" in cols && "last_notified_on" in cols && "billing_day" in cols)
        helper.close()
    }

    /** v4 → v5 chỉ thêm cột billing_day; gói cũ lấy ngày gốc theo ngày thanh toán tiếp theo. */
    @Test
    fun upgradeFromV4_addsBillingDay() {
        val old = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(SubTrackDatabase.DB_NAME), null)
        old.execSQL(subsV1.replace("updated_at INTEGER NOT NULL)", "updated_at INTEGER NOT NULL, snoozed_until INTEGER, last_notified_on INTEGER)"))
        old.execSQL("CREATE TABLE tags (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL COLLATE NOCASE UNIQUE, color INTEGER NOT NULL)")
        old.execSQL("CREATE TABLE subscription_tags (subscription_id INTEGER NOT NULL, tag_id INTEGER NOT NULL, PRIMARY KEY (subscription_id, tag_id))")
        old.execSQL(
            "CREATE TABLE payments (id INTEGER PRIMARY KEY AUTOINCREMENT, subscription_id INTEGER, subscription_name TEXT NOT NULL, " +
                "category TEXT NOT NULL, period_date INTEGER NOT NULL, kind TEXT NOT NULL, paid_date INTEGER, amount REAL, currency TEXT, " +
                "created_at INTEGER NOT NULL, UNIQUE (subscription_id, period_date))"
        )
        old.execSQL(
            "INSERT INTO subscriptions (id,name,amount,currency,billing_cycle,next_payment_date,category,status,created_at,updated_at) " +
                "VALUES (7,'Netflix',260000,'VND','MONTHLY',${d1.toEpochDay()},'entertainment','ACTIVE',1,1)"
        )
        old.execSQL("PRAGMA user_version = 4")
        old.close()

        val helper = SubTrackDatabase(context)
        val s = helper.getSubscriptions().single()
        assertEquals("Netflix", s.name)
        assertEquals(null, s.billingDay)
        assertEquals(5, userVersion(helper.readableDatabase))
        helper.close()
    }

    private val subsV1 = """CREATE TABLE subscriptions (
        id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, amount REAL NOT NULL, currency TEXT NOT NULL,
        billing_cycle TEXT NOT NULL, next_payment_date INTEGER NOT NULL, category TEXT NOT NULL, status TEXT NOT NULL,
        remind_days_before INTEGER, management_url TEXT, notes TEXT, platforms TEXT NOT NULL DEFAULT '',
        created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)"""

    /** Dựng file DB cũ với lược đồ cho trước, rồi mở bằng SubTrackDatabase và kiểm tra dữ liệu còn nguyên. */
    private fun upgradeFromAndAssert(version: Int, subscriptionsSql: String, tagsSql: String) {
        val old = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(SubTrackDatabase.DB_NAME), null)
        old.execSQL(subscriptionsSql)
        old.execSQL(tagsSql)
        old.execSQL(
            """CREATE TABLE subscription_tags (
                subscription_id INTEGER NOT NULL REFERENCES subscriptions(id) ON DELETE CASCADE,
                tag_id INTEGER NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
                PRIMARY KEY (subscription_id, tag_id))"""
        )
        old.execSQL(
            "INSERT INTO subscriptions (id,name,amount,currency,billing_cycle,next_payment_date,category,status,created_at,updated_at) " +
                "VALUES (7,'Netflix',260000,'VND','MONTHLY',${d1.toEpochDay()},'entertainment','ACTIVE',1,1)"
        )
        old.execSQL("INSERT INTO tags (id,name,color) VALUES (3,'Phim',255)")
        old.execSQL("INSERT INTO subscription_tags VALUES (7,3)")
        old.execSQL("PRAGMA user_version = $version")
        old.close()

        val helper = SubTrackDatabase(context)
        val s = helper.getSubscriptions().single()
        assertEquals("Netflix", s.name)
        assertEquals(listOf(3L), s.tagIds)
        assertNull(s.snoozedUntil)
        assertTrue(helper.getPayments().isEmpty())
        helper.close()
    }

    @Test
    fun upgradeFromV1_keepsData() = upgradeFromAndAssert(
        1, subsV1,
        "CREATE TABLE tags (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, color INTEGER NOT NULL)",
    )

    @Test
    fun upgradeFromV3_keepsData() = upgradeFromAndAssert(
        3, subsV1.replace("amount REAL NOT NULL,", "amount REAL NOT NULL CHECK (amount >= 0),"),
        "CREATE TABLE tags (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL COLLATE NOCASE UNIQUE, color INTEGER NOT NULL)",
    )

    @Test
    fun deleteSubscription_keepsPayments() {
        val helper = SubTrackDatabase(context)
        val id = helper.upsertSubscription(sub())
        helper.insertPayment(payment(id))
        helper.deleteSubscription(id)
        val p = helper.getPayments().single()
        assertNull(p.subscriptionId)
        assertEquals("Netflix", p.subscriptionName)
        helper.close()
    }

    @Test
    fun upsert_changedDate_resetsReminderState() {
        val helper = SubTrackDatabase(context)
        val id = helper.upsertSubscription(sub())
        helper.setReminderState(id, d1, d2)
        helper.upsertSubscription(sub(notes = "ghi chú").copy(id = id))
        var s = helper.getSubscriptions().single()
        assertEquals(d1, s.snoozedUntil)
        assertEquals(d2, s.lastNotifiedOn)
        helper.upsertSubscription(sub(notes = "ghi chú", next = d2).copy(id = id))
        s = helper.getSubscriptions().single()
        assertNull(s.snoozedUntil)
        assertNull(s.lastNotifiedOn)
        helper.close()
    }

    @Test
    fun upsert_changedStatus_resetsReminderState() {
        val helper = SubTrackDatabase(context)
        val id = helper.upsertSubscription(sub())
        helper.setReminderState(id, d1, d2)
        helper.upsertSubscription(sub().copy(id = id, status = SubscriptionStatus.PAUSED))
        val s = helper.getSubscriptions().single()
        assertNull(s.snoozedUntil)
        assertNull(s.lastNotifiedOn)
        helper.close()
    }

    @Test
    fun payments_uniquePerPeriod() {
        val helper = SubTrackDatabase(context)
        val id = helper.upsertSubscription(sub())
        helper.insertPayment(payment(id))
        assertThrows(SQLiteConstraintException::class.java) { helper.insertPayment(payment(id)) }
        helper.close()
    }
}
