package com.nhom4.subtrack.ui.organize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nhom4.subtrack.SubTrackApp
import com.nhom4.subtrack.data.CategoryKey
import com.nhom4.subtrack.data.Currency
import com.nhom4.subtrack.data.Platform
import com.nhom4.subtrack.data.RatesState
import com.nhom4.subtrack.data.SubscriptionStatus
import com.nhom4.subtrack.data.Tag
import com.nhom4.subtrack.domain.BillingMath
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TagRow(val tag: Tag, val count: Int)
data class PlatformRow(val platform: Platform, val count: Int)
data class CategoryRow(val category: CategoryKey, val count: Int, val monthly: Double)

data class OrganizeUiState(
    val loading: Boolean = true,
    val tags: List<TagRow> = emptyList(),
    val platforms: List<PlatformRow> = emptyList(),
    val categories: List<CategoryRow> = emptyList(),
    val currency: Currency = Currency.VND,
    /** Gói đang hoạt động khác tiền tệ: đã quy đổi / chưa quy đổi được. */
    val convertedCount: Int = 0,
    val excludedCount: Int = 0,
)

class OrganizeViewModel(app: SubTrackApp) : ViewModel() {
    private val repo = app.repository

    val rates: StateFlow<RatesState> = app.rates.state
    private val ratesRepo = app.rates

    val state: StateFlow<OrganizeUiState> = combine(repo.subscriptions, repo.tags, app.settings.settings, app.rates.state) { subs, tags, settings, rates ->
        if (subs == null) return@combine OrganizeUiState()
        val cur = settings.defaultCurrency
        val costs = subs.filter { it.status == SubscriptionStatus.ACTIVE }
            .map { it to BillingMath.monthlyIn(it, cur, rates.perUsd) }
        val active = costs.mapNotNull { (s, m) -> m?.let { s to it } }
        OrganizeUiState(
            loading = false,
            tags = tags.map { t -> TagRow(t, subs.count { t.id in it.tagIds }) },
            platforms = Platform.entries.map { p -> PlatformRow(p, subs.count { p in it.platforms }) }
                .filter { it.count > 0 }.sortedByDescending { it.count },
            categories = active.groupBy { it.first.categoryKey }.map { (c, inCat) ->
                CategoryRow(c, inCat.size, inCat.sumOf { it.second })
            }.sortedByDescending { it.monthly },
            currency = cur,
            convertedCount = active.count { it.first.currency != cur },
            excludedCount = costs.count { it.second == null },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrganizeUiState())

    init {
        viewModelScope.launch { if (repo.subscriptions.value == null) runCatching { repo.refresh() } }
    }

    fun syncRates() = ratesRepo.syncNow()

    fun saveTag(tag: Tag, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(runCatching { repo.saveTag(tag) }.getOrDefault(false)) }
    }

    fun deleteTag(id: Long) {
        viewModelScope.launch { runCatching { repo.deleteTag(id) } }
    }
}
