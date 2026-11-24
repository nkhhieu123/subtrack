package com.nhom4.subtrack.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate

/**
 * Lớp truy cập SQLite. Chỉ Repository gọi trực tiếp lớp này.
 *
 * Lược đồ (version 4):
 * - subscriptions: một dòng cho mỗi gói đăng ký; nền tảng lưu chung một cột chuỗi,
 *   các khóa cách nhau bằng dấu phẩy, ví dụ "android,smart_tv"; có thêm trạng thái nhắc hạn
 *   (snoozed_until, last_notified_on).
 * - tags: tag do người dùng tạo, tên không trùng (không phân biệt hoa/thường).
 * - subscription_tags: bảng nối N–N giữa gói và tag.
 * - payments: lịch sử chi trả (đã trả / bỏ qua) theo từng kỳ của gói.
 */
class SubTrackDatabase(context: Context, name: String = DB_NAME) :
    SQLiteOpenHelper(context, name, null, DB_VERSION) {

    /**
     * Bật khóa ngoại sau khi tạo/nâng cấp xong. Không bật trong onConfigure vì khi nâng cấp
     * phải dựng lại bảng cha (DROP + RENAME); nếu khóa ngoại đang bật, ON DELETE CASCADE
     * sẽ xóa mất dữ liệu ở bảng nối subscription_tags.
     */
    override fun onOpen(db: SQLiteDatabase) {
        if (!db.isReadOnly) db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        createSubscriptions(db, "subscriptions")
        createTags(db, "tags")
        createSubscriptionTags(db)
        createPayments(db)
    }

    /**
     * - Version 1: nền tảng dạng chuỗi, tên tag phân biệt hoa/thường, chưa có CHECK số tiền.
     * - Version 2: nền tảng tách ra bảng subscription_platforms.
     * - Version 3: nền tảng quay lại dạng chuỗi; giữ tag không phân biệt hoa/thường và CHECK.
     * - Version 4: thêm trạng thái nhắc hạn và bảng payments.
     * - Version 5 (hiện tại): thêm billing_day (ngày gốc của gói) để kỳ sau không trôi về 28.
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            if (oldVersion < 2) mergeDuplicateTagsAndRebuild(db)
            // Bảng dựng lại đã có sẵn cột nhắc hạn của version 4
            rebuildSubscriptions(db, platformsFromTable = oldVersion == 2)
        } else if (oldVersion < 4) {
            db.execSQL("ALTER TABLE subscriptions ADD COLUMN snoozed_until INTEGER")
            db.execSQL("ALTER TABLE subscriptions ADD COLUMN last_notified_on INTEGER")
        }
        // v5: ngày gốc của gói; bảng dựng lại ở nhánh < 3 đã có cột này
        if (oldVersion in 3..4) db.execSQL("ALTER TABLE subscriptions ADD COLUMN billing_day INTEGER")
        if (oldVersion < 4) createPayments(db)
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
        val db = readableDatabase
        val tagMap = HashMap<Long, MutableList<Long>>()
        db.rawQuery("SELECT subscription_id, tag_id FROM subscription_tags", null).use { c ->
            while (c.moveToNext()) tagMap.getOrPut(c.getLong(0)) { mutableListOf() }.add(c.getLong(1))
        }
        return db.rawQuery("SELECT * FROM subscriptions", null).use { c ->
            buildList { while (c.moveToNext()) add(c.toSubscription(tagMap)) }
        }
    }

    /** id = 0 thì thêm mới, ngược lại cập nhật. Gói và tag được ghi trong cùng một giao dịch. */
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
                put("billing_day", sub.billingDay ?: sub.nextPaymentDate.dayOfMonth)
                put("category", sub.category.key)
                put("status", sub.status.name)
                if (sub.remindDaysBefore == null) putNull("remind_days_before") else put("remind_days_before", sub.remindDaysBefore)
                put("management_url", sub.managementUrl)
                put("notes", sub.notes)
                put("platforms", platformsToString(sub.platforms))
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
                db.insertOrThrow("subscription_tags", null, ContentValues().apply {
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

    /** [billingDay] khác null khi người dùng chọn ngày mới (kích hoạt lại): đổi luôn ngày gốc. */
    fun setStatusAndDate(id: Long, status: SubscriptionStatus, next: LocalDate, billingDay: Int? = null) {
        writableDatabase.update(
            "subscriptions",
            ContentValues().apply {
                put("status", status.name)
                put("next_payment_date", next.toEpochDay())
                if (billingDay != null) put("billing_day", billingDay)
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

    /**
     * Liên kết tag của gói tự xóa theo ON DELETE CASCADE; nền tảng nằm trong cùng dòng nên mất theo.
     * Lịch sử chi trả vẫn giữ (subscription_id thành NULL, còn tên gói).
     */
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

    /**
     * Trả về false nếu trùng tên (không phân biệt hoa/thường). COLLATE NOCASE của SQLite chỉ
     * so được chữ ASCII nên kiểm tra thêm bằng lowercase() để bắt cả "Đọc sách" / "đọc sách".
     */
    fun upsertTag(tag: Tag): Boolean {
        val key = tag.name.lowercase()
        if (getTags().any { it.id != tag.id && it.name.lowercase() == key }) return false
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

    /** Chỉ xóa tag và liên kết của nó (CASCADE), không xóa gói đăng ký. */
    fun deleteTag(id: Long) {
        writableDatabase.delete("tags", "id = ?", arrayOf(id.toString()))
    }

    fun clearAll() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("subscription_tags", null, null)
            db.delete("subscriptions", null, null)
            db.delete("tags", null, null)
            db.delete("payments", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Ghi cả bộ dữ liệu trong một giao dịch (lỗi thì không ghi gì). Id trong [d] chỉ dùng để nối
     * gói ↔ tag ↔ lịch sử; dòng mới nhận id mới.
     */
    fun importAll(d: com.nhom4.subtrack.domain.Dataset) = inTransaction {
        val db = writableDatabase
        val tagIds = d.tags.associate { t ->
            t.id to db.insertOrThrow("tags", null, ContentValues().apply {
                put("name", t.name)
                put("color", t.color)
            })
        }
        val subIds = d.subscriptions.associate { s ->
            s.id to upsertSubscription(s.copy(id = 0, tagIds = s.tagIds.mapNotNull { tagIds[it] }))
        }
        d.payments.forEach { p -> insertPayment(p.copy(id = 0, subscriptionId = p.subscriptionId?.let { subIds[it] })) }
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
            platforms = platformsFromString(getString(getColumnIndexOrThrow("platforms"))),
            tagIds = tagMap[id].orEmpty(),
            createdAt = getLong(getColumnIndexOrThrow("created_at")),
            updatedAt = getLong(getColumnIndexOrThrow("updated_at")),
            snoozedUntil = getColumnIndexOrThrow("snoozed_until").let { if (isNull(it)) null else LocalDate.ofEpochDay(getLong(it)) },
            lastNotifiedOn = getColumnIndexOrThrow("last_notified_on").let { if (isNull(it)) null else LocalDate.ofEpochDay(getLong(it)) },
            billingDay = getColumnIndexOrThrow("billing_day").let { if (isNull(it)) null else getInt(it) },
        )
    }

    // ---------- Nâng cấp lược đồ ----------

    /**
     * Dựng lại bảng subscriptions theo lược đồ hiện tại (có CHECK amount ≥ 0, có cột platforms).
     * [platformsFromTable] = true khi nâng từ version 2: gộp các dòng của subscription_platforms
     * thành chuỗi rồi xóa bảng đó.
     */
    private fun rebuildSubscriptions(db: SQLiteDatabase, platformsFromTable: Boolean) {
        createSubscriptions(db, "subscriptions_new")
        if (platformsFromTable) {
            db.execSQL("INSERT INTO subscriptions_new ($BASE_COLUMNS) SELECT $BASE_COLUMNS FROM subscriptions")
            val byId = HashMap<Long, MutableList<Platform>>()
            db.rawQuery("SELECT subscription_id, platform FROM subscription_platforms", null).use { c ->
                while (c.moveToNext()) {
                    Platform.fromKey(c.getString(1))?.let { byId.getOrPut(c.getLong(0)) { mutableListOf() }.add(it) }
                }
            }
            byId.forEach { (id, list) ->
                db.update("subscriptions_new", ContentValues().apply { put("platforms", platformsToString(list)) },
                    "id = ?", arrayOf(id.toString()))
            }
            db.execSQL("DROP TABLE subscription_platforms")
        } else {
            db.execSQL("INSERT INTO subscriptions_new ($BASE_COLUMNS, platforms) SELECT $BASE_COLUMNS, platforms FROM subscriptions")
        }
        db.execSQL("DROP TABLE subscriptions")
        db.execSQL("ALTER TABLE subscriptions_new RENAME TO subscriptions")
    }

    /** Version 1: gộp tag trùng tên khác hoa/thường vào tag có id nhỏ nhất, dựng lại bảng tags với COLLATE NOCASE. */
    private fun mergeDuplicateTagsAndRebuild(db: SQLiteDatabase) {
        val keepByName = HashMap<String, Long>()
        val duplicates = mutableListOf<Pair<Long, Long>>() // (tag thừa, tag giữ lại)
        db.rawQuery("SELECT id, name FROM tags ORDER BY id", null).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val keep = keepByName.getOrPut(c.getString(1).lowercase()) { id }
                if (keep != id) duplicates += id to keep
            }
        }
        duplicates.forEach { (dup, keep) ->
            db.execSQL(
                "INSERT OR IGNORE INTO subscription_tags (subscription_id, tag_id) SELECT subscription_id, ? FROM subscription_tags WHERE tag_id = ?",
                arrayOf<Any>(keep, dup),
            )
            db.execSQL("DELETE FROM subscription_tags WHERE tag_id = ?", arrayOf<Any>(dup))
            db.execSQL("DELETE FROM tags WHERE id = ?", arrayOf<Any>(dup))
        }
        createTags(db, "tags_new")
        db.execSQL("INSERT INTO tags_new (id, name, color) SELECT id, name, color FROM tags")
        db.execSQL("DROP TABLE tags")
        db.execSQL("ALTER TABLE tags_new RENAME TO tags")
    }

    companion object {
        const val DB_NAME = "subtrack.db"
        const val DB_VERSION = 5

        /** Các cột chung của mọi version (trừ platforms). */
        private const val BASE_COLUMNS =
            "id, name, amount, currency, billing_cycle, next_payment_date, category, status, " +
                "remind_days_before, management_url, notes, created_at, updated_at"

        /** [ANDROID, SMART_TV] → "android,smart_tv" (thứ tự cố định theo danh sách nền tảng). */
        fun platformsToString(platforms: List<Platform>): String =
            platforms.distinct().sortedBy { it.ordinal }.joinToString(",") { it.key }

        /** "android,smart_tv" → [ANDROID, SMART_TV]; bỏ qua khóa không hợp lệ. */
        fun platformsFromString(value: String?): List<Platform> =
            value.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }
                .mapNotNull { Platform.fromKey(it) }.distinct().sortedBy { it.ordinal }

        private fun createSubscriptions(db: SQLiteDatabase, table: String) {
            db.execSQL(
                """
                CREATE TABLE $table (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    amount REAL NOT NULL CHECK (amount >= 0),
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
                    last_notified_on INTEGER,
                    billing_day INTEGER
                )
                """.trimIndent()
            )
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

        private fun createTags(db: SQLiteDatabase, table: String) {
            db.execSQL(
                "CREATE TABLE $table (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL COLLATE NOCASE UNIQUE, color INTEGER NOT NULL)"
            )
        }

        private fun createSubscriptionTags(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE subscription_tags (
                    subscription_id INTEGER NOT NULL REFERENCES subscriptions(id) ON DELETE CASCADE,
                    tag_id INTEGER NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
                    PRIMARY KEY (subscription_id, tag_id)
                )
                """.trimIndent()
            )
        }
    }
}
