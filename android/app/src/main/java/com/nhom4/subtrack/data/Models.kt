package com.nhom4.subtrack.data

import java.time.LocalDate

enum class BillingCycle { WEEKLY, MONTHLY, QUARTERLY, YEARLY }

enum class SubscriptionStatus { ACTIVE, PAUSED, CANCELLED }

/** Danh mục cố định, nhãn hiển thị lấy từ strings.xml theo [key]. */
enum class Category(val key: String, val color: Long) {
    ENTERTAINMENT("entertainment", 0xFFE5484D),
    MUSIC("music", 0xFFF76B15),
    EDUCATION("education", 0xFF30A46C),
    STORAGE("storage", 0xFF0090FF),
    WORK("work", 0xFF6E56CF),
    UTILITIES("utilities", 0xFF12A594),
    OTHER("other", 0xFF8B8D98);

    companion object {
        fun fromKey(key: String?): Category = entries.firstOrNull { it.key == key } ?: OTHER
    }
}

/**
 * Danh mục dùng để hiển thị và gom nhóm: danh mục cố định, hoặc tên người dùng tự nhập
 * khi chọn "Khác" ([customName] chỉ có giá trị khi [category] = OTHER).
 */
data class CategoryKey(val category: Category, val customName: String? = null) {
    companion object {
        fun of(category: Category, custom: String?): CategoryKey =
            CategoryKey(category, custom?.trim()?.takeIf { category == Category.OTHER && it.isNotEmpty() })
    }
}

/** Nền tảng có sẵn; lưu theo [key]. */
enum class Platform(val key: String) {
    ANDROID("android"), IPHONE("iphone"), IPAD("ipad"), TABLET("tablet"),
    WEB("web"), WINDOWS("windows"), MAC("mac"), SMART_TV("smart_tv"),
    PLAYSTATION("playstation"), XBOX("xbox"), NINTENDO("nintendo"), OTHER("other");

    companion object {
        fun fromKey(key: String): Platform? = entries.firstOrNull { it.key == key }
    }
}

enum class Currency(val code: String) { VND("VND"), USD("USD"), EUR("EUR") }

data class Tag(
    val id: Long = 0,
    val name: String,
    val color: Long,
)

data class Subscription(
    val id: Long = 0,
    val name: String,
    val amount: Double,
    val currency: Currency,
    val cycle: BillingCycle,
    val nextPaymentDate: LocalDate,
    val category: Category,
    /** Tên danh mục tự nhập khi [category] = OTHER; null = chỉ hiện "Khác". */
    val customCategory: String? = null,
    val status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
    /** Số ngày nhắc trước hạn; null = không nhắc. */
    val remindDaysBefore: Int? = 3,
    val managementUrl: String? = null,
    val notes: String? = null,
    val tagIds: List<Long> = emptyList(),
    val platforms: List<Platform> = emptyList(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    /** Không nhắc trước ngày này (hoãn nhắc); null = không hoãn. */
    val snoozedUntil: LocalDate? = null,
    /** Ngày gửi thông báo gần nhất cho kỳ hiện tại; null = chưa nhắc. */
    val lastNotifiedOn: LocalDate? = null,
    /** Ngày gốc trong tháng để tính kỳ sau (vd 31); null = lấy ngày của [nextPaymentDate]. */
    val billingDay: Int? = null,
) {
    val categoryKey: CategoryKey get() = CategoryKey.of(category, customCategory)
}

enum class PaymentKind { PAID, SKIPPED }

/**
 * Kết quả xử lý một kỳ của một gói. Tên, danh mục, số tiền, tiền tệ được chụp lại lúc ghi
 * nên vẫn đúng khi gói bị sửa/xóa ([subscriptionId] = null khi gói đã bị xóa).
 */
data class Payment(
    val id: Long = 0,
    val subscriptionId: Long?,
    val subscriptionName: String,
    val category: Category,
    val periodDate: LocalDate,
    val kind: PaymentKind,
    val paidDate: LocalDate?,
    val amount: Double?,
    val currency: Currency?,
    val createdAt: Long = 0,
    val customCategory: String? = null,
) {
    val categoryKey: CategoryKey get() = CategoryKey.of(category, customCategory)
}
