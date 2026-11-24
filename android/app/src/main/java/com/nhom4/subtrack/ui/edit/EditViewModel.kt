package com.nhom4.subtrack.ui.edit

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nhom4.subtrack.R
import com.nhom4.subtrack.SubTrackApp
import com.nhom4.subtrack.data.BillingCycle
import com.nhom4.subtrack.data.Category
import com.nhom4.subtrack.data.Currency
import com.nhom4.subtrack.data.Platform
import com.nhom4.subtrack.data.Subscription
import com.nhom4.subtrack.data.SubscriptionStatus
import com.nhom4.subtrack.data.Tag
import com.nhom4.subtrack.domain.BillingMath
import com.nhom4.subtrack.reminder.Reminders
import com.nhom4.subtrack.ui.formatAmountInput
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class EditForm(
    val name: String = "",
    val amount: String = "",
    val currency: Currency = Currency.VND,
    val cycle: BillingCycle = BillingCycle.MONTHLY,
    val nextPaymentDate: LocalDate = LocalDate.now().plusMonths(1),
    val category: Category = Category.ENTERTAINMENT,
    /** Tên danh mục tự nhập, chỉ dùng khi [category] = OTHER. */
    val customCategory: String = "",
    val status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
    val remindDaysBefore: Int? = 3,
    val tagIds: Set<Long> = emptySet(),
    val platforms: Set<Platform> = emptySet(),
    val url: String = "",
    val notes: String = "",
)

data class FormErrors(
    @StringRes val name: Int? = null,
    @StringRes val amount: Int? = null,
    @StringRes val url: Int? = null,
    @StringRes val customCategory: Int? = null,
) {
    val any get() = name != null || amount != null || url != null || customCategory != null
}

sealed interface EditEvent {
    data class Message(@StringRes val text: Int) : EditEvent
    data object Done : EditEvent
    data object Deleted : EditEvent
}

const val MAX_CUSTOM_CATEGORY = 30

class EditViewModel(private val app: SubTrackApp, private val id: Long) : ViewModel() {
    private val repo = app.repository
    val isNew = id == 0L

    var loading by mutableStateOf(!isNew)
        private set
    var notFound by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var form by mutableStateOf(EditForm(currency = app.settings.settings.value.defaultCurrency))
        private set
    var errors by mutableStateOf(FormErrors())
        private set
    /** Đã bấm Lưu ít nhất một lần → hiện lỗi ngay khi gõ. */
    private var attempted = false
    /** Gói đang sửa lúc mở form: giữ ngày gốc khi người dùng không đổi ngày thanh toán. */
    private var loaded: Subscription? = null

    val tags: StateFlow<List<Tag>> = repo.tags
    val remindersEnabled get() = app.settings.settings.value.remindersEnabled

    /** Các tên danh mục tự nhập đã dùng ở gói khác, để chọn nhanh và gõ thống nhất. */
    val customCategorySuggestions: List<String>
        get() = repo.subscriptions.value.orEmpty()
            .mapNotNull { it.categoryKey.customName }
            .distinctBy { it.lowercase() }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

    private val _events = Channel<EditEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            if (repo.subscriptions.value == null) runCatching { repo.refresh() }
            if (!isNew) {
                val sub = runCatching { repo.getSubscription(id) }.getOrNull()
                if (sub == null) notFound = true else { loaded = sub; form = sub.toForm() }
                loading = false
            }
        }
    }

    fun update(transform: (EditForm) -> EditForm) {
        form = transform(form)
        if (attempted) errors = validate(form).first
    }

    fun toggleTag(tagId: Long) = update { f -> f.copy(tagIds = if (tagId in f.tagIds) f.tagIds - tagId else f.tagIds + tagId) }
    fun togglePlatform(p: Platform) = update { f -> f.copy(platforms = if (p in f.platforms) f.platforms - p else f.platforms + p) }

    /** Tạo tag mới ngay trong form và chọn luôn tag đó. Trả về false nếu trùng tên. */
    fun createTag(name: String, color: Long, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repo.saveTag(Tag(name = name.trim(), color = color))
            if (ok) repo.tags.value.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }?.let { toggleTag(it.id) }
            onResult(ok)
        }
    }

    /** Chi phí tương đương mỗi tháng của số tiền đang nhập (null nếu chưa hợp lệ). */
    fun monthlyPreview(): Double? =
        BillingMath.parseAmount(form.amount, form.currency)?.let { BillingMath.monthlyCost(it, form.cycle) }

    fun save() {
        attempted = true
        val (errs, amount) = validate(form)
        errors = errs
        if (errs.any || amount == null) {
            _events.trySend(EditEvent.Message(R.string.err_fix_fields))
            return
        }
        saving = true
        viewModelScope.launch {
            val f = form
            val result = runCatching {
                repo.save(
                    Subscription(
                        id = id,
                        name = f.name.trim(),
                        amount = amount,
                        currency = f.currency,
                        cycle = f.cycle,
                        nextPaymentDate = f.nextPaymentDate,
                        // Đổi ngày thì ngày gốc theo ngày mới (null = lấy ngày của nextPaymentDate)
                        billingDay = loaded?.takeIf { it.nextPaymentDate == f.nextPaymentDate }?.billingDay,
                        category = f.category,
                        customCategory = normalizedCustomCategory(f),
                        status = f.status,
                        remindDaysBefore = f.remindDaysBefore,
                        managementUrl = f.url.trim().ifEmpty { null },
                        notes = f.notes.trim().ifEmpty { null },
                        tagIds = f.tagIds.toList(),
                        platforms = Platform.entries.filter { it in f.platforms },
                    )
                )
            }
            saving = false
            if (result.isSuccess) {
                if (remindersEnabled) Reminders.checkNow(app)
                _events.send(EditEvent.Message(R.string.saved))
                _events.send(EditEvent.Done)
            } else {
                _events.send(EditEvent.Message(R.string.save_failed))
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            runCatching { repo.delete(id) }
                .onSuccess {
                    _events.send(EditEvent.Message(R.string.deleted))
                    _events.send(EditEvent.Deleted)
                }
                .onFailure { _events.send(EditEvent.Message(R.string.save_failed)) }
        }
    }

    private fun validate(f: EditForm): Pair<FormErrors, Double?> {
        val name = f.name.trim()
        val nameErr = when {
            name.isEmpty() -> R.string.err_name_required
            name.length > BillingMath.MAX_NAME_LENGTH -> R.string.err_name_too_long
            else -> null
        }
        val amount = BillingMath.parseAmount(f.amount, f.currency)
        val amountErr = when {
            f.amount.isBlank() -> R.string.err_amount_required
            amount == null -> R.string.err_amount_invalid
            amount <= 0 -> R.string.err_amount_zero
            else -> null
        }
        val urlErr = if (f.url.isNotBlank() && !BillingMath.isValidUrl(f.url)) R.string.err_url_invalid else null
        val customErr = when {
            f.category != Category.OTHER -> null
            f.customCategory.isBlank() -> R.string.err_custom_category_required
            f.customCategory.trim().length > MAX_CUSTOM_CATEGORY -> R.string.err_custom_category_too_long
            else -> null
        }
        return FormErrors(nameErr, amountErr, urlErr, customErr) to amount
    }

    /** Chỉ lưu khi chọn "Khác"; trùng tên đã có (khác hoa/thường) thì dùng lại cách viết cũ để gom nhóm đúng. */
    private fun normalizedCustomCategory(f: EditForm): String? {
        if (f.category != Category.OTHER) return null
        val name = f.customCategory.trim().replace(Regex("""\s+"""), " ").ifEmpty { return null }
        return customCategorySuggestions.firstOrNull { it.equals(name, ignoreCase = true) } ?: name
    }

    private fun Subscription.toForm() = EditForm(
        name = name,
        amount = formatAmountInput(amount, currency),
        currency = currency,
        cycle = cycle,
        nextPaymentDate = nextPaymentDate,
        category = category,
        customCategory = categoryKey.customName.orEmpty(),
        status = status,
        remindDaysBefore = remindDaysBefore,
        tagIds = tagIds.toSet(),
        platforms = platforms.toSet(),
        url = managementUrl.orEmpty(),
        notes = notes.orEmpty(),
    )
}
