package com.nhom4.subtrack.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate

/** Lớp truy cập SQLite. Chỉ Repository gọi trực tiếp lớp này. */
class SubTrackDatabase(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_NAME = "subtrack.db"
        const val DB_VERSION = 4
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE subscriptions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                amount REAL NOT NULL,
                currency TEXT NOT NULL,
                billing_cycle TEXT NOT NULL,
                next_payment_date INTEGER NOT NULL,
                category TEXT NOT NULL,
                status TEXT NOT NULL,
                remind_days_before INTEGER,
                management_url TEXT,
                notes TEXT,
                platforms TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                snoozed_until INTEGER,
                last_notified_on INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE TABLE tags (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, color INTEGER NOT NULL)")
        db.execSQL(
            """
            CREATE TABLE subscription_tags (
                subscription_id INTEGER NOT NULL REFERENCES subscriptions(id) ON DELETE CASCADE,
                tag_id INTEGER NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
                PRIMARY KEY (subscription_id, tag_id)
            )
            """.trimIndent()
        )
        createPayments(db)
    }

    /** v1 và v3 có cùng cột ở bảng subscriptions nên dùng chung một nhánh nâng cấp. */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE subscriptions ADD COLUMN snoozed_until INTEGER")
            db.execSQL("ALTER TABLE subscriptions ADD COLUMN last_notified_on INTEGER")
            createPayments(db)
        }
    }

    private fun createPayments(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE payments (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                subscription_id INTEGER REFERENCES subscriptions(id) ON DELETE SET NULL,
                subscription_name TEXT NOT NULL,
                category TEXT NOT NULL,
                period_date INTEGER NOT NULL,
                kind TEXT NOT NULL CHECK (kind IN ('PAID','SKIPPED')),
                paid_date INTEGER,
                amount REAL CHECK (amount IS NULL OR amount >= 0),
                currency TEXT,
                created_at INTEGER NOT NULL,
                UNIQUE (subscription_id, period_date)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_payments_paid_date ON payments(paid_date)")
    }

    /** Chạy [block] trong một giao dịch; ném lỗi thì hoàn tác. */
    fun <T> inTransaction(block: () -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try {
            return block().also { db.setTransactionSuccessful() }
        } finally {
            db.endTransaction()
        }
    }

    // ---------- Subscriptions ----------

    fun getSubscriptions(): List<Subscription> {
        val tagMap = HashMap<Long, MutableList<Long>>()
        readableDatabase.rawQuery("SELECT subscription_id, tag_id FROM subscription_tags", null).use { c ->
            while (c.moveToNext()) tagMap.getOrPut(c.getLong(0)) { mutableListOf() }.add(c.getLong(1))
        }
        return readableDatabase.rawQuery("SELECT * FROM subscriptions", null).use { c ->
            buildList { while (c.moveToNext()) add(c.toSubscription(tagMap)) }
        }
    }

    fun upsertSubscription(sub: Subscription): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            val values = ContentValues().apply {
                put("name", sub.name)
                put("amount", sub.amount)
                put("currency", sub.currency.name)
                put("billing_cycle", sub.cycle.name)
                put("next_payment_date", sub.nextPaymentDate.toEpochDay())
                put("category", sub.category.key)
                put("status", sub.status.name)
                if (sub.remindDaysBefore == null) putNull("remind_days_before") else put("remind_days_before", sub.remindDaysBefore)
                put("management_url", sub.managementUrl)
                put("notes", sub.notes)
                put("platforms", sub.platforms.joinToString(",") { it.key })
                put("updated_at", now)
            }
            val id = if (sub.id == 0L) {
                values.put("created_at", now)
                db.insertOrThrow("subscriptions", null, values)
            } else {
                // Đổi hạn hoặc trạng thái thì kỳ nhắc cũ không còn đúng: xóa trạng thái nhắc
                val changed = db.rawQuery(
                    "SELECT 1 FROM subscriptions WHERE id = ? AND (next_payment_date <> ? OR status <> ?)",
                    arrayOf(sub.id.toString(), sub.nextPaymentDate.toEpochDay().toString(), sub.status.name),
                ).use { it.moveToFirst() }
                if (changed) {
                    values.putNull("snoozed_until")
                    values.putNull("last_notified_on")
                }
                db.update("subscriptions", values, "id = ?", arrayOf(sub.id.toString()))
                sub.id
            }
            db.delete("subscription_tags", "subscription_id = ?", arrayOf(id.toString()))
            sub.tagIds.distinct().forEach { tagId ->
                db.insert("subscription_tags", null, ContentValues().apply {
                    put("subscription_id", id)
                    put("tag_id", tagId)
                })
            }
            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
        }
    }

    fun updateNextPaymentDate(id: Long, date: LocalDate) {
        writableDatabase.update(
            "subscriptions",
            ContentValues().apply { put("next_payment_date", date.toEpochDay()) },
            "id = ?", arrayOf(id.toString()),
        )
    }

    fun setStatusAndDate(id: Long, status: SubscriptionStatus, next: LocalDate) {
        writableDatabase.update(
            "subscriptions",
            ContentValues().apply {
                put("status", status.name)
                put("next_payment_date", next.toEpochDay())
            },
            "id = ?", arrayOf(id.toString()),
        )
    }

    fun setReminderState(id: Long, snoozedUntil: LocalDate?, lastNotifiedOn: LocalDate?) {
        writableDatabase.update(
            "subscriptions",
            ContentValues().apply {
                if (snoozedUntil == null) putNull("snoozed_until") else put("snoozed_until", snoozedUntil.toEpochDay())
                if (lastNotifiedOn == null) putNull("last_notified_on") else put("last_notified_on", lastNotifiedOn.toEpochDay())
            },
            "id = ?", arrayOf(id.toString()),
        )
    }

    fun deleteSubscription(id: Long) {
        writableDatabase.delete("subscriptions", "id = ?", arrayOf(id.toString()))
    }

    // ---------- Payments ----------

    /** Mới nhất trước. */
    fun getPayments(): List<Payment> =
        readableDatabase.rawQuery("SELECT * FROM payments ORDER BY period_date DESC, id DESC", null).use { c ->
            buildList { while (c.moveToNext()) add(c.toPayment()) }
        }

    /** Ném SQLiteConstraintException nếu kỳ này của gói đã có dòng. */
    fun insertPayment(p: Payment): Long =
        writableDatabase.insertOrThrow("payments", null, ContentValues().apply {
            if (p.subscriptionId == null) putNull("subscription_id") else put("subscription_id", p.subscriptionId)
            put("subscription_name", p.subscriptionName)
            put("category", p.category.key)
            put("period_date", p.periodDate.toEpochDay())
            put("kind", p.kind.name)
            if (p.paidDate == null) putNull("paid_date") else put("paid_date", p.paidDate.toEpochDay())
            if (p.amount == null) putNull("amount") else put("amount", p.amount)
            if (p.currency == null) putNull("currency") else put("currency", p.currency.name)
            put("created_at", p.createdAt.takeIf { it != 0L } ?: System.currentTimeMillis())
        })

    fun deletePayment(id: Long) {
        writableDatabase.delete("payments", "id = ?", arrayOf(id.toString()))
    }

    // ---------- Tags ----------

    fun getTags(): List<Tag> =
        readableDatabase.rawQuery("SELECT id, name, color FROM tags ORDER BY name COLLATE NOCASE", null).use { c ->
            buildList { while (c.moveToNext()) add(Tag(c.getLong(0), c.getString(1), c.getLong(2))) }
        }

    /** Trả về false nếu trùng tên. */
    fun upsertTag(tag: Tag): Boolean {
        val values = ContentValues().apply {
            put("name", tag.name)
            put("color", tag.color)
        }
        return if (tag.id == 0L) {
            writableDatabase.insert("tags", null, values) != -1L
        } else {
            runCatching { writableDatabase.update("tags", values, "id = ?", arrayOf(tag.id.toString())) }.isSuccess
        }
    }

    fun deleteTag(id: Long) {
        writableDatabase.delete("tags", "id = ?", arrayOf(id.toString()))
    }

    fun clearAll() {
        writableDatabase.apply {
            delete("subscription_tags", null, null)
            delete("subscriptions", null, null)
            delete("tags", null, null)
            delete("payments", null, null)
        }
    }

    private fun Cursor.toPayment(): Payment {
        fun longOrNull(col: String) = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getLong(it) }
        return Payment(
            id = getLong(getColumnIndexOrThrow("id")),
            subscriptionId = longOrNull("subscription_id"),
            subscriptionName = getString(getColumnIndexOrThrow("subscription_name")),
            category = Category.fromKey(getString(getColumnIndexOrThrow("category"))),
            periodDate = LocalDate.ofEpochDay(getLong(getColumnIndexOrThrow("period_date"))),
            kind = PaymentKind.valueOf(getString(getColumnIndexOrThrow("kind"))),
            paidDate = longOrNull("paid_date")?.let(LocalDate::ofEpochDay),
            amount = getColumnIndexOrThrow("amount").let { if (isNull(it)) null else getDouble(it) },
            currency = getColumnIndexOrThrow("currency").let { if (isNull(it)) null else Currency.valueOf(getString(it)) },
            createdAt = getLong(getColumnIndexOrThrow("created_at")),
        )
    }

    private fun Cursor.toSubscription(tagMap: Map<Long, List<Long>>): Subscription {
        val id = getLong(getColumnIndexOrThrow("id"))
        val remindIdx = getColumnIndexOrThrow("remind_days_before")
        return Subscription(
            id = id,
            name = getString(getColumnIndexOrThrow("name")),
            amount = getDouble(getColumnIndexOrThrow("amount")),
            currency = Currency.valueOf(getString(getColumnIndexOrThrow("currency"))),
            cycle = BillingCycle.valueOf(getString(getColumnIndexOrThrow("billing_cycle"))),
            nextPaymentDate = LocalDate.ofEpochDay(getLong(getColumnIndexOrThrow("next_payment_date"))),
            category = Category.fromKey(getString(getColumnIndexOrThrow("category"))),
            status = SubscriptionStatus.valueOf(getString(getColumnIndexOrThrow("status"))),
            remindDaysBefore = if (isNull(remindIdx)) null else getInt(remindIdx),
            managementUrl = getString(getColumnIndexOrThrow("management_url")),
            notes = getString(getColumnIndexOrThrow("notes")),
            platforms = getString(getColumnIndexOrThrow("platforms"))
                .split(",").filter { it.isNotBlank() }.mapNotNull { Platform.fromKey(it) },
            tagIds = tagMap[id].orEmpty(),
            createdAt = getLong(getColumnIndexOrThrow("created_at")),
            updatedAt = getLong(getColumnIndexOrThrow("updated_at")),
            snoozedUntil = getColumnIndexOrThrow("snoozed_until").let { if (isNull(it)) null else LocalDate.ofEpochDay(getLong(it)) },
            lastNotifiedOn = getColumnIndexOrThrow("last_notified_on").let { if (isNull(it)) null else LocalDate.ofEpochDay(getLong(it)) },
        )
    }
}
