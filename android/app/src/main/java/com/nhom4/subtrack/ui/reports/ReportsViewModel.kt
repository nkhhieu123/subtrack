package com.nhom4.subtrack.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nhom4.subtrack.SubTrackApp
import com.nhom4.subtrack.data.Category
import com.nhom4.subtrack.data.Currency
import com.nhom4.subtrack.data.Payment
import com.nhom4.subtrack.data.PaymentKind
import com.nhom4.subtrack.data.Subscription
import com.nhom4.subtrack.data.SubscriptionStatus
import com.nhom4.subtrack.data.RatesState
import com.nhom4.subtrack.domain.BillingMath
import com.nhom4.subtrack.domain.CurrencyConverter
import com.nhom4.subtrack.domain.SpendMath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class Slice(val label: String?, val monthly: Double, val share: Double)
data class CategoryTotal(val category: Category, val monthly: Double, val share: Double)

data class ReportsUiState(
    val loading: Boolean = true,
    val currency: Currency = Currency.VND,
    val monthly: Double = 0.0,
    val activeCount: Int = 0,
    /** Gói đang hoạt động khác tiền tệ, chưa quy đổi được (chưa có tỷ giá). */
    val excludedCount: Int = 0,
    /** Có khoản khác tiền tệ đã được quy đổi (gói hoặc lịch sử chi trả). */
    val converted: Boolean = false,
    /** Tối đa 6 lát; lát cuối có label null = "Các gói khác". */
    val slices: List<Slice> = emptyList(),
    val categories: List<CategoryTotal> = emptyList(),
    val top: List<Subscription> = emptyList(),
    val hasActual: Boolean = false,
    val actualThisMonth: Double = 0.0,
    val actualThisYear: Double = 0.0,
    /** 6 tháng gần nhất, cũ → mới. */
    val actualMonths: List<Pair<YearMonth, Double>> = emptyList(),
    /** 12 tháng gần nhất, giảm dần. */
    val actualCategories: List<Pair<Category, Double>> = emptyList(),
) {
    val weekly get() = BillingMath.weeklyFromMonthly(monthly)
    val quarterly get() = BillingMath.quarterlyFromMonthly(monthly)
    val yearly get() = BillingMath.yearlyFromMonthly(monthly)
}

class ReportsViewModel(app: SubTrackApp) : ViewModel() {
    private val repo = app.repository
    /** null = dùng tiền tệ mặc định trong Cài đặt. */
    private val chosenCurrency = MutableStateFlow<Currency?>(null)

    val state: StateFlow<ReportsUiState> =
        combine(repo.subscriptions, repo.payments, app.settings.settings, chosenCurrency, app.rates.state) { subs, payments, settings, chosen, rates ->
            if (subs == null) return@combine ReportsUiState()
            build(subs, payments, chosen ?: settings.defaultCurrency, LocalDate.now(), rates.perUsd)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState())

    init {
        viewModelScope.launch { if (repo.subscriptions.value == null) runCatching { repo.refresh() } }
    }

    val rates: StateFlow<RatesState> = app.rates.state
    private val ratesRepo = app.rates

    fun setCurrency(c: Currency) {
        chosenCurrency.value = c
    }

    fun syncRates() = ratesRepo.syncNow()

    companion object {
        fun build(
            subs: List<Subscription>,
            payments: List<Payment>,
            currency: Currency,
            today: LocalDate,
            perUsd: Map<Currency, Double>? = null,
        ): ReportsUiState {
            val active = subs.filter { it.status == SubscriptionStatus.ACTIVE }
            val costs = active.mapNotNull { s -> BillingMath.monthlyIn(s, currency, perUsd)?.let { s to it } }
                .sortedByDescending { it.second }
            val total = costs.sumOf { it.second }
            fun share(v: Double) = if (total > 0) v / total * 100 else 0.0

            val slices = if (costs.size <= 6) {
                costs.map { (s, m) -> Slice(s.name, m, share(m)) }
            } else {
                costs.take(5).map { (s, m) -> Slice(s.name, m, share(m)) } +
                    costs.drop(5).sumOf { it.second }.let { Slice(null, it, share(it)) }
            }
            val categories = costs.groupBy { it.first.category }
                .map { (c, list) -> list.sumOf { it.second }.let { CategoryTotal(c, it, share(it)) } }
                .sortedByDescending { it.monthly }

            val month = YearMonth.from(today)
            val yearStart = LocalDate.of(today.year, 1, 1)
            val windowStart = month.minusMonths(11).atDay(1)
            val windowEnd = month.plusMonths(1).atDay(1)
            val paidActual = payments.filter {
                val cur = it.currency
                it.kind == PaymentKind.PAID && it.amount != null && cur != null &&
                    CurrencyConverter.convert(1.0, cur, currency, perUsd) != null
            }

            return ReportsUiState(
                loading = false,
                currency = currency,
                monthly = total,
                activeCount = costs.size,
                excludedCount = active.size - costs.size,
                converted = costs.any { it.first.currency != currency } || paidActual.any { it.currency != currency },
                slices = slices,
                categories = categories,
                top = costs.take(3).map { it.first },
                hasActual = paidActual.isNotEmpty(),
                actualThisMonth = SpendMath.total(payments, currency, month.atDay(1), windowEnd, perUsd),
                actualThisYear = SpendMath.total(payments, currency, yearStart, yearStart.plusYears(1), perUsd),
                actualMonths = SpendMath.lastMonths(payments, currency, month, 6, perUsd),
                actualCategories = SpendMath.byCategory(payments, currency, windowStart, windowEnd, perUsd),
            )
        }
    }
}
