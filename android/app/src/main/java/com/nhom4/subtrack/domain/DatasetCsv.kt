package com.nhom4.subtrack.domain

import com.nhom4.subtrack.R
import com.nhom4.subtrack.data.BillingCycle
import com.nhom4.subtrack.data.Category
import com.nhom4.subtrack.data.Currency
import com.nhom4.subtrack.data.Payment
import com.nhom4.subtrack.data.PaymentKind
import com.nhom4.subtrack.data.Platform
import com.nhom4.subtrack.data.Subscription
import com.nhom4.subtrack.data.SubscriptionStatus
import com.nhom4.subtrack.data.Tag
import com.nhom4.subtrack.ui.formatAmountInput
import com.nhom4.subtrack.ui.formatDate
import com.nhom4.subtrack.ui.label
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Dữ liệu gốc của một bộ: không gồm số liệu báo cáo (app tự tính lại). */
data class Dataset(val subscriptions: List<Subscription>, val tags: List<Tag>, val payments: List<Payment>)

/** [skipped] = số dòng hỏng bị bỏ qua khi đọc. */
data class ParsedDataset(val data: Dataset, val skipped: Int)

/**
 * Định dạng CSV chung cho xuất file, mã QR và nhập lại. Dễ đọc bằng Excel (nhãn tiếng Việt,
 * ngày dd/MM/yyyy, tiền kiểu vi-VN) và đọc lại được: cột tìm theo tên, nhãn ánh xạ ngược về enum.
 * Một file gồm các phần [Gói đăng ký], [Tag], [Lịch sử thanh toán]; file cũ chỉ có bảng gói vẫn đọc được.
 *
 * [text] lấy chuỗi từ strings.xml (Context::getString).
 */
class DatasetCsv(private val text: (Int) -> String) {

    fun write(d: Dataset): String = buildString {
        val tagName = d.tags.associate { it.id to it.name }
        appendSection(SEC_SUBS, SUB_COLS, d.subscriptions.sortedBy { it.name.lowercase() }.map { s ->
            listOf(
                s.id.toString(), s.name, formatAmountInput(s.amount, s.currency), s.currency.code,
                text(s.cycle.label), formatDate(s.nextPaymentDate), text(s.category.label), text(s.status.label),
                s.remindDaysBefore?.toString().orEmpty(),
                s.tagIds.mapNotNull { tagName[it] }.joinToString(LIST_SEP),
                s.platforms.joinToString(LIST_SEP) { text(it.label) },
                s.managementUrl.orEmpty(), s.notes.orEmpty(),
            )
        })
        append("\r\n")
        appendSection(SEC_TAGS, TAG_COLS, d.tags.map { listOf(it.name, "#%06X".format(it.color and 0xFFFFFF)) })
        append("\r\n")
        appendSection(SEC_PAYMENTS, PAY_COLS, d.payments.map { p ->
            listOf(
                p.subscriptionId?.toString().orEmpty(), p.subscriptionName, text(p.category.label),
                formatDate(p.periodDate), text(p.kind.label), p.paidDate?.let(::formatDate).orEmpty(),
                if (p.amount != null && p.currency != null) formatAmountInput(p.amount, p.currency) else "",
                p.currency?.code.orEmpty(),
            )
        })
    }

    private fun StringBuilder.appendSection(title: String, cols: List<String>, rows: List<List<String>>) {
        append(CsvWriter.build("[$title]\r\n" + cols.joinToString(","), rows))
    }

    /** Ném [IllegalArgumentException] nếu không đọc được gói nào. */
    fun read(csv: String): ParsedDataset {
        var section = SEC_SUBS
        var header: Map<String, Int>? = null
        val subs = LinkedHashMap<Long, Subscription>()
        val tags = LinkedHashMap<String, Tag>() // khóa: tên viết thường
        val payments = mutableListOf<Payment>()
        val seenPeriods = HashSet<Pair<Long, LocalDate>>()
        var skipped = 0
        var nextId = 1L

        fun tagId(name: String, color: Long? = null): Long {
            val key = name.lowercase()
            val old = tags[key]
            if (old != null) {
                if (color != null) tags[key] = old.copy(color = color)
                return old.id
            }
            val id = tags.size + 1L
            tags[key] = Tag(id, name, color ?: DEFAULT_TAG_COLORS[(id - 1).toInt() % DEFAULT_TAG_COLORS.size])
            return id
        }

        for (row in parseRows(csv)) {
            val cells = row.map { it.trim() }
            if (cells.all { it.isEmpty() }) continue
            val first = cells.first()
            if (cells.count { it.isNotEmpty() } == 1 && first.startsWith("[") && first.endsWith("]")) {
                section = first.removeSurrounding("[", "]").trim()
                header = null
                continue
            }
            if (header == null) {
                header = cells.withIndex().associate { (i, h) -> h.lowercase() to i }
                continue
            }
            val h = header
            fun cell(col: String) = h[col.lowercase()]?.let { cells.getOrNull(it) }.orEmpty()
            val ok = runCatching {
                when (section) {
                    SEC_SUBS -> {
                        val name = cell(COL_NAME).ifEmpty { error("thiếu tên") }
                        val currency = Currency.entries.first { it.code.equals(cell(COL_CURRENCY), true) }
                        val code = cell(COL_ID).toLongOrNull()?.takeIf { it > 0 && it !in subs } ?: (-nextId++)
                        subs[code] = Subscription(
                            id = code, name = name,
                            amount = BillingMath.parseAmount(cell(COL_AMOUNT), currency) ?: error("số tiền"),
                            currency = currency,
                            cycle = match(cell(COL_CYCLE), BillingCycle.entries) { it.label }!!,
                            nextPaymentDate = date(cell(COL_NEXT))!!,
                            category = match(cell(COL_CATEGORY), Category.entries, { it.key }) { it.label } ?: Category.OTHER,
                            status = match(cell(COL_STATUS), SubscriptionStatus.entries) { it.label } ?: SubscriptionStatus.ACTIVE,
                            remindDaysBefore = cell(COL_REMIND).toIntOrNull()?.takeIf { it >= 0 },
                            tagIds = split(cell(COL_TAGS)).map { tagId(it) }.distinct(),
                            platforms = split(cell(COL_PLATFORMS))
                                .mapNotNull { v -> match(v, Platform.entries, { it.key }) { it.label } }.distinct(),
                            managementUrl = cell(COL_URL).ifEmpty { null },
                            notes = cell(COL_NOTES).ifEmpty { null },
                        )
                    }
                    SEC_TAGS -> tagId(
                        cell(COL_TAG_NAME).ifEmpty { error("thiếu tên tag") },
                        cell(COL_TAG_COLOR).removePrefix("#").toLongOrNull(16)?.let { 0xFF000000 or it },
                    )
                    SEC_PAYMENTS -> {
                        val subId = cell(COL_PAY_SUB).toLongOrNull()?.takeIf { it in subs }
                        val period = date(cell(COL_PERIOD))!!
                        if (subId != null && !seenPeriods.add(subId to period)) error("trùng kỳ")
                        val currency = Currency.entries.firstOrNull { it.code.equals(cell(COL_PAY_CURRENCY), true) }
                        payments += Payment(
                            subscriptionId = subId,
                            subscriptionName = cell(COL_PAY_NAME).ifEmpty { error("thiếu tên gói") },
                            category = match(cell(COL_PAY_CATEGORY), Category.entries, { it.key }) { it.label } ?: Category.OTHER,
                            periodDate = period,
                            kind = match(cell(COL_KIND), PaymentKind.entries) { it.label }!!,
                            paidDate = date(cell(COL_PAID_DATE)),
                            amount = currency?.let { BillingMath.parseAmount(cell(COL_PAY_AMOUNT), it) },
                            currency = currency,
                        )
                    }
                    else -> Unit // phần lạ: bỏ qua
                }
            }.isSuccess
            if (!ok) skipped++
        }
        require(subs.isNotEmpty()) { "Không có gói hợp lệ" }
        return ParsedDataset(Dataset(subs.values.toList(), tags.values.toList(), payments), skipped)
    }

    /** Khớp theo nhãn hiển thị, tên enum hoặc khóa; rỗng/không khớp → null. */
    private fun <E : Enum<E>> match(value: String, entries: List<E>, key: (E) -> String = { it.name }, label: (E) -> Int): E? =
        if (value.isEmpty()) null
        else entries.firstOrNull { e ->
            text(label(e)).equals(value, true) || e.name.equals(value, true) || key(e).equals(value, true)
        }

    companion object {
        const val SEC_SUBS = "Gói đăng ký"
        const val SEC_TAGS = "Tag"
        const val SEC_PAYMENTS = "Lịch sử thanh toán"
        private const val LIST_SEP = "; "

        private const val COL_ID = "Mã"
        private const val COL_NAME = "Tên dịch vụ"
        private const val COL_AMOUNT = "Số tiền"
        private const val COL_CURRENCY = "Tiền tệ"
        private const val COL_CYCLE = "Chu kỳ"
        private const val COL_NEXT = "Ngày thanh toán tiếp theo"
        private const val COL_CATEGORY = "Danh mục"
        private const val COL_STATUS = "Trạng thái"
        private const val COL_REMIND = "Nhắc trước (ngày)"
        private const val COL_TAGS = "Tag"
        private const val COL_PLATFORMS = "Nền tảng"
        private const val COL_URL = "Liên kết quản lý"
        private const val COL_NOTES = "Ghi chú"
        private val SUB_COLS = listOf(
            COL_ID, COL_NAME, COL_AMOUNT, COL_CURRENCY, COL_CYCLE, COL_NEXT, COL_CATEGORY, COL_STATUS,
            COL_REMIND, COL_TAGS, COL_PLATFORMS, COL_URL, COL_NOTES,
        )

        private const val COL_TAG_NAME = "Tên tag"
        private const val COL_TAG_COLOR = "Màu"
        private val TAG_COLS = listOf(COL_TAG_NAME, COL_TAG_COLOR)

        private const val COL_PAY_SUB = "Mã gói"
        private const val COL_PAY_NAME = "Tên gói"
        private const val COL_PAY_CATEGORY = "Danh mục"
        private const val COL_PERIOD = "Kỳ"
        private const val COL_KIND = "Loại"
        private const val COL_PAID_DATE = "Ngày trả"
        private const val COL_PAY_AMOUNT = "Số tiền"
        private const val COL_PAY_CURRENCY = "Tiền tệ"
        private val PAY_COLS = listOf(
            COL_PAY_SUB, COL_PAY_NAME, COL_PAY_CATEGORY, COL_PERIOD, COL_KIND, COL_PAID_DATE, COL_PAY_AMOUNT, COL_PAY_CURRENCY,
        )

        /** Màu cho tag không có trong phần [Tag] (cùng bảng màu với màn Tổ chức). */
        val DEFAULT_TAG_COLORS = listOf(
            0xFFE5484D, 0xFFF76B15, 0xFFF2A900, 0xFF30A46C, 0xFF12A594,
            0xFF0090FF, 0xFF6C4CF1, 0xFFD6409F, 0xFF8B8D98,
        )

        // STRICT: 31/02 là lỗi, không tự lùi về 28/02
        private val VN_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT)

        /** dd/MM/yyyy (như khi xuất) hoặc yyyy-MM-dd; rỗng → null; sai → ném lỗi. */
        private fun date(v: String): LocalDate? = when {
            v.isEmpty() -> null
            v.contains('/') -> LocalDate.parse(v, VN_DATE)
            else -> LocalDate.parse(v)
        }

        private fun split(v: String) = v.split(";").map { it.trim() }.filter { it.isNotEmpty() }

        /** Đọc CSV theo RFC 4180 (ngoặc kép, xuống dòng trong ô, CRLF hoặc LF), bỏ BOM. */
        fun parseRows(csv: String): List<List<String>> {
            val rows = mutableListOf<List<String>>()
            var row = mutableListOf<String>()
            val cell = StringBuilder()
            var quoted = false
            var i = if (csv.startsWith(Char(0xFEFF))) 1 else 0
            while (i < csv.length) {
                val c = csv[i]
                if (quoted) {
                    if (c == '"') {
                        if (csv.getOrNull(i + 1) == '"') { cell.append('"'); i++ } else quoted = false
                    } else cell.append(c)
                } else when (c) {
                    '"' -> quoted = true
                    ',' -> { row += cell.toString(); cell.clear() }
                    '\r' -> Unit
                    '\n' -> { row += cell.toString(); cell.clear(); rows += row; row = mutableListOf() }
                    else -> cell.append(c)
                }
                i++
            }
            if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
            return rows
        }
    }
}

@get:androidx.annotation.StringRes
val PaymentKind.label: Int
    get() = when (this) {
        PaymentKind.PAID -> R.string.action_paid
        PaymentKind.SKIPPED -> R.string.history_skipped
    }
