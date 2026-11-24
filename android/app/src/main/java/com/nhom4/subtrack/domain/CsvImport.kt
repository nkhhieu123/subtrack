package com.nhom4.subtrack.domain

import com.nhom4.subtrack.data.BillingCycle
import com.nhom4.subtrack.data.Category
import com.nhom4.subtrack.data.Currency
import com.nhom4.subtrack.data.Platform
import com.nhom4.subtrack.data.Subscription
import com.nhom4.subtrack.data.SubscriptionStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/** Đọc CSV (RFC 4180). Không phụ thuộc Android để kiểm thử bằng JUnit. */
object CsvReader {
    /** Dấu BOM (U+FEFF) Excel thêm vào đầu file UTF-8. */
    private val BOM = 0xFEFF.toChar().toString()

    /**
     * Tách [text] thành các dòng ô: hỗ trợ ô trong ngoặc kép, "" là một dấu ngoặc kép, xuống dòng
     * trong ô. Bỏ BOM đầu file và các dòng trống.
     */
    fun parse(text: String, delimiter: Char = detectDelimiter(text)): List<List<String>> {
        val s = text.removePrefix(BOM)
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < s.length && s[i + 1] == '"') {
                        cell.append('"')
                        i++
                    } else quoted = false
                } else cell.append(c)
            } else when (c) {
                '"' -> quoted = true
                delimiter -> {
                    row.add(cell.toString())
                    cell.clear()
                }
                '\r' -> Unit
                '\n' -> {
                    row.add(cell.toString())
                    cell.clear()
                    rows.add(row)
                    row = mutableListOf()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row.add(cell.toString())
            rows.add(row)
        }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }

    /** Excel bản tiếng Việt hay lưu CSV bằng dấu chấm phẩy: chọn dấu xuất hiện nhiều hơn ở dòng đầu. */
    fun detectDelimiter(text: String): Char {
        val first = text.removePrefix(BOM).lineSequence().firstOrNull().orEmpty()
        return if (first.count { it == ';' } > first.count { it == ',' }) ';' else ','
    }
}

/** Các cột của file CSV, cùng thứ tự với file xuất (chuỗi csv_header). */
enum class CsvColumn(vararg val aliases: String) {
    NAME("name", "tên"),
    AMOUNT("amount", "price", "giá", "số tiền"),
    CURRENCY("currency", "tiền tệ"),
    CYCLE("cycle", "billing_cycle", "chu kỳ"),
    NEXT_DATE("next_payment_date", "next_date", "date", "ngày thanh toán"),
    CATEGORY("category", "danh mục"),
    STATUS("status", "trạng thái"),
    REMIND("remind_days_before", "remind", "nhắc trước"),
    TAGS("tags", "tag"),
    PLATFORMS("platforms", "platform", "nền tảng"),
    URL("url", "management_url", "liên kết"),
    NOTES("notes", "note", "ghi chú"),
}

/**
 * Từ vựng để hiểu nội dung ô: nhãn tiếng Việt (lấy từ strings.xml) và mã tiếng Anh của từng giá trị.
 * Khóa của các map đã chuẩn hóa bằng [norm].
 */
class CsvVocabulary(
    /** Tiêu đề cột của file xuất, đúng thứ tự [CsvColumn]. */
    headers: List<String>,
    val cycles: Map<String, BillingCycle>,
    val categories: Map<String, Category>,
    val statuses: Map<String, SubscriptionStatus>,
    val platforms: Map<String, Platform>,
    /** Mẫu ghi chú cho nền tảng ngoài danh sách cố định, ví dụ "Nền tảng khác: %s". */
    val otherPlatformsNote: String,
) {
    val columnByHeader: Map<String, CsvColumn> = buildMap {
        CsvColumn.entries.forEach { col ->
            col.aliases.forEach { put(norm(it), col) }
            put(norm(col.name), col)
            headers.getOrNull(col.ordinal)?.let { put(norm(it), col) }
        }
    }

    companion object {
        fun norm(s: String): String = s.trim().lowercase(Locale.ROOT).replace(Regex("""\s+"""), " ")

        /** Tạo map nhãn → giá trị gồm nhãn hiển thị, tên enum và [extraKey] (nếu có). */
        fun <E : Enum<E>> labels(values: List<E>, label: (E) -> String, extraKey: (E) -> String? = { null }): Map<String, E> =
            buildMap {
                values.forEach { v ->
                    put(norm(v.name), v)
                    extraKey(v)?.let { put(norm(it), v) }
                    put(norm(label(v)), v)
                }
            }
    }
}

enum class ImportProblem { NAME, AMOUNT, CURRENCY, CYCLE, DATE, STATUS }

/** [line] là số thứ tự dòng trong file (dòng tiêu đề là dòng 1). */
data class ImportedRow(val line: Int, val sub: Subscription, val tagNames: List<String>)
data class RowError(val line: Int, val problem: ImportProblem)

sealed interface CsvImportResult {
    /** Không tìm thấy cột Tên dịch vụ hoặc Số tiền ở dòng đầu. */
    data object BadFormat : CsvImportResult
    data class Parsed(val rows: List<ImportedRow>, val errors: List<RowError>) : CsvImportResult
}

/**
 * Chuyển nội dung CSV thành danh sách gói. Bắt buộc có cột Tên dịch vụ, Số tiền và ngày thanh toán
 * hợp lệ ở từng dòng; các cột khác thiếu thì lấy mặc định như khi thêm gói bằng tay.
 */
class CsvImporter(
    private val vocab: CsvVocabulary,
    private val defaultCurrency: Currency,
    private val today: LocalDate = LocalDate.now(),
) {

    fun parse(text: String): CsvImportResult {
        val rows = CsvReader.parse(text)
        val header = rows.firstOrNull() ?: return CsvImportResult.BadFormat
        val index = HashMap<CsvColumn, Int>()
        header.forEachIndexed { i, h -> vocab.columnByHeader[CsvVocabulary.norm(h)]?.let { index.putIfAbsent(it, i) } }
        if (CsvColumn.NAME !in index || CsvColumn.AMOUNT !in index) return CsvImportResult.BadFormat

        val ok = mutableListOf<ImportedRow>()
        val errors = mutableListOf<RowError>()
        rows.drop(1).forEachIndexed { i, cells ->
            val line = i + 2
            when (val r = parseRow { c -> index[c]?.let { cells.getOrNull(it) }?.trim() }) {
                is RowOutcome.Ok -> ok += ImportedRow(line, r.sub, r.tags)
                is RowOutcome.Bad -> errors += RowError(line, r.problem)
            }
        }
        return CsvImportResult.Parsed(ok, errors)
    }

    private sealed interface RowOutcome {
        data class Ok(val sub: Subscription, val tags: List<String>) : RowOutcome
        data class Bad(val problem: ImportProblem) : RowOutcome
    }

    /** [cell] trả về null khi file không có cột đó, "" khi có cột mà ô trống. */
    private fun parseRow(cell: (CsvColumn) -> String?): RowOutcome {
        fun bad(p: ImportProblem) = RowOutcome.Bad(p)

        val name = cell(CsvColumn.NAME).orEmpty()
        if (name.isEmpty() || name.length > 60) return bad(ImportProblem.NAME)

        val amountText = cell(CsvColumn.AMOUNT).orEmpty()
        val currency = parseCurrency(cell(CsvColumn.CURRENCY).orEmpty().ifEmpty { amountText }) ?: return bad(ImportProblem.CURRENCY)
        val amount = parseAmount(amountText, currency) ?: return bad(ImportProblem.AMOUNT)

        val cycleText = cell(CsvColumn.CYCLE).orEmpty()
        val cycle = if (cycleText.isEmpty()) BillingCycle.MONTHLY
        else vocab.cycles[CsvVocabulary.norm(cycleText)] ?: return bad(ImportProblem.CYCLE)

        // Không có cột ngày → một tháng sau hôm nay như form thêm gói; có cột mà sai/trống → lỗi dòng
        val dateText = cell(CsvColumn.NEXT_DATE)
        val date = if (dateText == null) today.plusMonths(1) else parseDate(dateText) ?: return bad(ImportProblem.DATE)

        val statusText = cell(CsvColumn.STATUS).orEmpty()
        val status = if (statusText.isEmpty()) SubscriptionStatus.ACTIVE
        else vocab.statuses[CsvVocabulary.norm(statusText)] ?: return bad(ImportProblem.STATUS)

        // Danh mục lạ → "Khác" kèm tên tự nhập
        val categoryText = cell(CsvColumn.CATEGORY).orEmpty()
        val category = vocab.categories[CsvVocabulary.norm(categoryText)]
        val custom = if (category == null && categoryText.isNotEmpty()) categoryText.take(30).trim() else null

        // Không có cột → nhắc trước 3 ngày như form; có cột mà ô trống → không nhắc (giống file xuất)
        val remindText = cell(CsvColumn.REMIND)
        val remind = if (remindText == null) 3
        else remindText.filter { it.isDigit() }.take(3).toIntOrNull()?.coerceAtMost(365)

        // Nền tảng ngoài 12 nền tảng cố định → chọn "Khác" và ghi tên gốc vào ghi chú
        val platformNames = split(cell(CsvColumn.PLATFORMS))
        val platforms = platformNames.map { vocab.platforms[CsvVocabulary.norm(it)] ?: Platform.OTHER }.toSet()
        val unknownPlatforms = platformNames.filter { vocab.platforms[CsvVocabulary.norm(it)] == null }

        val notes = listOfNotNull(
            cell(CsvColumn.NOTES)?.takeIf { it.isNotEmpty() },
            unknownPlatforms.takeIf { it.isNotEmpty() }?.let { vocab.otherPlatformsNote.format(it.joinToString(", ")) },
        ).joinToString("\n").ifEmpty { null }

        return RowOutcome.Ok(
            Subscription(
                name = name,
                amount = amount,
                currency = currency,
                cycle = cycle,
                nextPaymentDate = date,
                billingAnchorDay = date.dayOfMonth,
                category = category ?: Category.OTHER,
                customCategory = custom,
                status = status,
                remindDaysBefore = remind,
                managementUrl = parseUrl(cell(CsvColumn.URL).orEmpty()),
                notes = notes,
                platforms = Platform.entries.filter { it in platforms },
            ),
            split(cell(CsvColumn.TAGS)).map { it.take(40).trim() }.distinctBy { it.lowercase() },
        )
    }

    /** "VND", "₫", "đ", "USD", "$", "EUR", "€"; không có ký hiệu → tiền tệ mặc định; mã lạ → null. */
    private fun parseCurrency(text: String): Currency? {
        val t = text.trim().uppercase(Locale.ROOT)
        return when {
            t.none { it.isLetter() || it in "₫$€" } -> defaultCurrency
            "USD" in t || "$" in t -> Currency.USD
            "EUR" in t || "€" in t -> Currency.EUR
            "VND" in t || "₫" in t || t.endsWith("Đ") -> Currency.VND
            else -> null
        }
    }

    /** Bỏ ký hiệu tiền tệ và khoảng trắng ("260.000 ₫", "9,99 US$") rồi đọc như ô Số tiền trong form. */
    private fun parseAmount(text: String, currency: Currency): Double? {
        if ('-' in text) return null
        return BillingMath.parseAmount(text.filter { it.isDigit() || it == '.' || it == ',' }, currency)
    }

    private val dateFormats = listOf("d/M/uuuu", "d-M-uuuu", "d.M.uuuu", "uuuu-M-d", "uuuu/M/d")
        .map { DateTimeFormatter.ofPattern(it).withResolverStyle(ResolverStyle.STRICT) }

    private fun parseDate(text: String): LocalDate? {
        val t = text.trim().substringBefore(' ')
        if (t.isEmpty()) return null
        return dateFormats.firstNotNullOfOrNull { f -> runCatching { LocalDate.parse(t, f) }.getOrNull() }
    }

    /** Thiếu "https://" thì tự thêm; liên kết sai thì bỏ qua (không làm hỏng cả dòng). */
    private fun parseUrl(text: String): String? {
        if (text.isEmpty()) return null
        val url = if (text.contains("://")) text else "https://$text"
        return url.takeIf { BillingMath.isValidUrl(it) }
    }

    /** Tag và nền tảng cách nhau bằng dấu chấm phẩy (như file xuất). */
    private fun split(text: String?): List<String> =
        text.orEmpty().split(';').map { it.trim() }.filter { it.isNotEmpty() }
}
