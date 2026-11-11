@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.nhom4.subtrack.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nhom4.subtrack.R
import com.nhom4.subtrack.data.Subscription
import com.nhom4.subtrack.data.SubscriptionStatus
import com.nhom4.subtrack.data.Tag
import com.nhom4.subtrack.ui.AppViewModels
import com.nhom4.subtrack.ui.ColorChip
import com.nhom4.subtrack.ui.EmptyState
import com.nhom4.subtrack.ui.ErrorState
import com.nhom4.subtrack.ui.LoadingState
import com.nhom4.subtrack.ui.formatDate
import com.nhom4.subtrack.ui.formatMoney
import com.nhom4.subtrack.ui.label

/**
 * Màn hình danh sách gói (READ). Nút "Thêm gói đăng ký" mở form trống (CREATE);
 * chạm vào một gói mở form của gói đó để sửa hoặc xóa (UPDATE / DELETE).
 */
@Composable
fun ListScreen(
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    vm: ListViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.list_title), fontWeight = FontWeight.Bold) },
                windowInsets = WindowInsets(0),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.add_subscription)) },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            )
        },
    ) { padding ->
        when {
            state.error -> ErrorState(vm::load, Modifier.padding(padding))
            state.loading -> LoadingState(Modifier.padding(padding))
            state.items.isEmpty() -> EmptyState(
                Icons.Outlined.Inbox,
                stringResource(R.string.empty_title),
                stringResource(R.string.empty_body),
                Modifier.padding(padding),
            )
            else -> LazyColumn(
                Modifier.padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.items, key = { it.id }) { sub ->
                    SubscriptionRow(sub, state.tags, onClick = { onOpen(sub.id) })
                }
            }
        }
    }
}

@Composable
private fun SubscriptionRow(sub: Subscription, tags: Map<Long, Tag>, onClick: () -> Unit) {
    val active = sub.status == SubscriptionStatus.ACTIVE
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    sub.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(sub.cycle.label) + " · " + stringResource(sub.category.label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.next_payment, formatDate(sub.nextPaymentDate)) + " · " + stringResource(sub.status.label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val chipTags = sub.tagIds.mapNotNull { tags[it] }
                if (chipTags.isNotEmpty() || sub.platforms.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        chipTags.forEach { ColorChip(it.name, Color(it.color)) }
                        sub.platforms.forEach { ColorChip(stringResource(it.label), MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(formatMoney(sub.amount, sub.currency), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
