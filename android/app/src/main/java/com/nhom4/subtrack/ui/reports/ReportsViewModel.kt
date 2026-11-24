package com.nhom4.subtrack.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nhom4.subtrack.SubTrackApp
import com.nhom4.subtrack.data.CategoryKey
import com.nhom4.subtrack.data.Currency
import com.nhom4.subtrack.data.Payment
import com.nhom4.subtrack.data.PaymentKind
import com.nhom4.subtrack.data.Subscription
import com.nhom4.subtrack.data.SubscriptionStatus
import com.nhom4.subtrack.domain.BillingMath
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
data class CategoryTotal(val category: CategoryKey, val monthly: Double, val share: Double)

data class ReportsUiState(
    val loading: Boolean = true,
    val currency: Currency = Currency.VND,
    val monthly: Double = 0.0,
    val activeCount: Int = 0,
    val excludedCount: Int = 0,
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
    val actualCategories: List<Pair<CategoryKey, Double>> = emptyList(),
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
        combine(repo.subscriptions, repo.payments, app.settings.settings, chosenCurrency) { subs, payments, settings, chosen ->
            if (subs == null) return@combine ReportsUiState()
            build(subs, payments, chosen ?: settings.defaultCurrency, LocalDate.now())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState())

    init {
        viewModelScope.launch { if (repo.subscriptions.value == null) runCatching { repo.refresh() } }
    }

    fun setCurrency(c: Currency) {
        chosenCurrency.value = c
    }

    companion object {
        fun build(subs: List<Subscription>, payments: List<Payment>, currency: Currency, today: LocalDate): ReportsUiState {
            val active = subs.filter { it.status == SubscriptionStatus.ACTIVE }
            val inCurrency = active.filter { it.currency == currency }
            val costs = inCurrency.map { it to BillingMath.monthlyCost(it.amount, it.cycle) }.sortedByDescending { it.second }
            val total = costs.sumOf { it.second }
            fun share(v: Double) = if (total > 0) v / total * 100 else 0.0

            val slices = if (costs.size <= 6) {
                costs.map { (s, m) -> Slice(s.name, m, share(m)) }
            } else {
                costs.take(5).map { (s, m) -> Slice(s.name, m, share(m)) } +
                    costs.drop(5).sumOf { it.second }.let { Slice(null, it, share(it)) }
            }
            val categories = inCurrency.groupBy { it.categoryKey }
                .map { (c, list) -> list.sumOf { BillingMath.monthlyCost(it.amount, it.cycle) }.let { CategoryTotal(c, it, share(it)) } }
                .sortedByDescending { it.monthly }

            val month = YearMonth.from(today)
            val yearStart = LocalDate.of(today.year, 1, 1)
            val windowStart = month.minusMonths(11).atDay(1)
            val windowEnd = month.plusMonths(1).atDay(1)

            return ReportsUiState(
                loading = false,
                currency = currency,
                monthly = total,
                activeCount = inCurrency.size,
                excludedCount = active.size - inCurrency.size,
                slices = slices,
                categories = categories,
                top = costs.take(3).map { it.first },
                hasActual = payments.any { it.kind == PaymentKind.PAID && it.currency == currency },
                actualThisMonth = SpendMath.total(payments, currency, month.atDay(1), windowEnd),
                actualThisYear = SpendMath.total(payments, currency, yearStart, yearStart.plusYears(1)),
                actualMonths = SpendMath.lastMonths(payments, currency, month, 6),
                actualCategories = SpendMath.byCategory(payments, currency, windowStart, windowEnd),
            )
        }
    }
}
