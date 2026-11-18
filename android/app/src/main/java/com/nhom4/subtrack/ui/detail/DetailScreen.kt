@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.nhom4.subtrack.ui.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nhom4.subtrack.R
import com.nhom4.subtrack.data.BillingCycle
import com.nhom4.subtrack.data.Payment
import com.nhom4.subtrack.data.PaymentKind
import com.nhom4.subtrack.data.Subscription
import com.nhom4.subtrack.data.SubscriptionStatus
import com.nhom4.subtrack.domain.BillingMath
import com.nhom4.subtrack.domain.RenewalLevel
import com.nhom4.subtrack.ui.AppViewModels
import com.nhom4.subtrack.ui.ColorChip
import com.nhom4.subtrack.ui.EmptyState
import com.nhom4.subtrack.ui.LoadingState
import com.nhom4.subtrack.ui.SectionHeader
import com.nhom4.subtrack.ui.formatDate
import com.nhom4.subtrack.ui.formatMoney
import com.nhom4.subtrack.ui.label
import com.nhom4.subtrack.ui.theme.MonthOrange
import com.nhom4.subtrack.ui.theme.SoonRed
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@Composable
fun DetailScreen(
    id: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    vm: DetailViewModel = viewModel(key = "detail-$id", factory = AppViewModels.detail(id)),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is DetailEvent.Message -> scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    val result = snackbar.showSnackbar(
                        resources.getString(event.text),
                        actionLabel = event.undo?.let { resources.getString(R.string.action_undo) },
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) event.undo?.let(vm::undo)
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(state.sub?.name.orEmpty(), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
                actions = {
                    if (state.sub != null) {
                        IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, stringResource(R.string.action_edit)) }
                    }
                },
                windowInsets = WindowInsets(0),
            )
        },
    ) { padding ->
        val sub = state.sub
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.notFound || sub == null ->
                EmptyState(Icons.Outlined.EventBusy, stringResource(R.string.not_found), null, Modifier.padding(padding))
            else -> Column(
                Modifier
                    .padding(padding)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OverviewCard(sub, state, vm)
                if (sub.status == SubscriptionStatus.ACTIVE) PeriodCard(sub, state, vm)
                InfoSection(sub, state)
                HistorySection(state, vm)
            }
        }
    }
}

@Composable
private fun CardColumn(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

@Composable
private fun OverviewCard(sub: Subscription, state: DetailUiState, vm: DetailViewModel) {
    var reactivate by remember { mutableStateOf(false) }
    val days = BillingMath.daysUntil(sub.nextPaymentDate)
    val active = sub.status == SubscriptionStatus.ACTIVE
    CardColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatMoney(sub.amount, sub.currency) + " / " + stringResource(sub.cycle.label).lowercase(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            ColorChip(stringResource(sub.status.label), MaterialTheme.colorScheme.primary)
        }
        if (active) {
            val color = when {
                days < 0 -> MaterialTheme.colorScheme.error
                else -> when (BillingMath.renewalLevel(sub.nextPaymentDate)) {
                    RenewalLevel.SOON -> SoonRed
                    RenewalLevel.THIS_MONTH -> MonthOrange
                    RenewalLevel.LATER -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            }
            val dueText = when {
                days < 0 -> stringResource(R.string.due_pending)
                days == 0L -> stringResource(R.string.due_today)
                days == 1L -> stringResource(R.string.due_tomorrow)
                else -> pluralStringResource(R.plurals.due_in_days, days.toInt(), days.toInt())
            }
            Text(
                stringResource(R.string.next_payment, formatDate(sub.nextPaymentDate)) + " · " + dueText,
                color = color,
                fontWeight = FontWeight.Medium,
            )
        }
        Text(
            stringResource(R.string.monthly_equivalent, formatMoney(BillingMath.monthlyCost(sub.amount, sub.cycle), sub.currency)) +
                " · " + stringResource(sub.category.label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.paidTotals.isNotEmpty()) {
            val count = state.history.count { it.kind == PaymentKind.PAID }
            Text(
                stringResource(R.string.paid_total, state.paidTotals.entries.joinToString(" + ") { (c, v) -> formatMoney(v, c) }, count),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (!active) {
            Button(onClick = { reactivate = true }, enabled = !state.busy) { Text(stringResource(R.string.action_reactivate)) }
        }
    }
    if (reactivate) {
        DatePickDialog(
            title = stringResource(R.string.reactivate_dialog_title),
            initial = BillingMath.nextOnOrAfter(sub.nextPaymentDate, sub.cycle, LocalDate.now()),
            onDismiss = { reactivate = false },
            onPick = { vm.reactivate(it); reactivate = false },
        )
    }
}

@Composable
private fun PeriodCard(sub: Subscription, state: DetailUiState, vm: DetailViewModel) {
    var paid by remember { mutableStateOf(false) }
    var snoozeMenu by remember { mutableStateOf(false) }
    CardColumn {
        Text(stringResource(R.string.section_this_period), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { paid = true }, enabled = !state.busy) { Text(stringResource(R.string.action_paid)) }
            OutlinedButton(onClick = vm::skip, enabled = !state.busy) { Text(stringResource(R.string.action_skip)) }
            if (state.showSnooze) {
                Box {
                    OutlinedButton(onClick = { snoozeMenu = true }, enabled = !state.busy) { Text(stringResource(R.string.action_snooze) + " ▾") }
                    DropdownMenu(expanded = snoozeMenu, onDismissRequest = { snoozeMenu = false }) {
                        listOf(1 to R.string.snooze_1d, 3 to R.string.snooze_3d).forEach { (days, label) ->
                            DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { snoozeMenu = false; vm.snooze(days) })
                        }
                    }
                }
            }
            OutlinedButton(onClick = vm::cancel, enabled = !state.busy) { Text(stringResource(R.string.action_cancel_sub)) }
        }
    }
    if (paid) {
        PaidDialog(sub, onDismiss = { paid = false }, onConfirm = { date, amount -> vm.markPaid(date, amount); paid = false })
    }
}

@Composable
private fun PaidDialog(sub: Subscription, onDismiss: () -> Unit, onConfirm: (LocalDate, Double) -> Unit) {
    val today = LocalDate.now()
    var date by remember { mutableStateOf(today) }
    var amountText by remember { mutableStateOf(com.nhom4.subtrack.ui.formatAmountInput(sub.amount, sub.currency)) }
    var pickDate by remember { mutableStateOf(false) }
    val amount = BillingMath.parseAmount(amountText, sub.currency)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.paid_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.CalendarMonth, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.paid_dialog_date, formatDate(date)))
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(stringResource(R.string.field_amount) + " (" + sub.currency.code + ")") },
                    isError = amount == null,
                    supportingText = if (amount == null) ({ Text(stringResource(R.string.err_amount_invalid)) }) else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { amount?.let { onConfirm(date, it) } }, enabled = amount != null) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (pickDate) {
        DatePickDialog(
            title = stringResource(R.string.pick_date),
            initial = date,
            maxDate = today,
            onDismiss = { pickDate = false },
            onPick = { date = it; pickDate = false },
        )
    }
}

/** Hộp chọn ngày; [maxDate] chặn chọn ngày sau mốc đó. */
@Composable
private fun DatePickDialog(title: String, initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit, maxDate: LocalDate? = null) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : androidx.compose.material3.SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) =
                maxDate == null || !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(maxDate)
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(state = state, title = { Text(title, Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}

@Composable
private fun InfoSection(sub: Subscription, state: DetailUiState) {
    val context = LocalContext.current
    val hasInfo = state.tags.isNotEmpty() || sub.platforms.isNotEmpty() || sub.managementUrl != null || sub.notes != null
    if (hasInfo) SectionHeader(stringResource(R.string.section_more))
    CardColumn {
        if (state.tags.isNotEmpty() || sub.platforms.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.tags.forEach { ColorChip(it.name, Color(it.color)) }
                sub.platforms.forEach { ColorChip(stringResource(it.label), MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Text(
            stringResource(R.string.field_remind) + ": " + when (val d = sub.remindDaysBefore) {
                null -> stringResource(R.string.remind_none)
                0 -> stringResource(R.string.remind_same_day)
                else -> pluralStringResource(R.plurals.remind_days_before, d, d)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        sub.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        sub.managementUrl?.let { url ->
            TextButton(onClick = { openUrl(context, url) }) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.open_url))
            }
        }
        OutlinedButton(onClick = { addToCalendar(context, sub) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.CalendarMonth, null, Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.add_to_calendar))
        }
    }
}

@Composable
private fun HistorySection(state: DetailUiState, vm: DetailViewModel) {
    SectionHeader(stringResource(R.string.history_title))
    if (state.history.isEmpty()) {
        Text(stringResource(R.string.history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        return
    }
    CardColumn {
        state.history.forEachIndexed { i, p ->
            if (i > 0) HorizontalDivider()
            HistoryRow(p, canUndo = i == 0 && !state.busy, onUndo = { vm.undoLatest(p.id) })
        }
    }
}

@Composable
private fun HistoryRow(p: Payment, canUndo: Boolean, onUndo: () -> Unit) {
    val paid = p.kind == PaymentKind.PAID
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(formatDate(if (paid) p.paidDate ?: p.periodDate else p.periodDate), Modifier.weight(1.2f))
        Text(
            if (paid && p.amount != null && p.currency != null) formatMoney(p.amount, p.currency) else "—",
            Modifier.weight(1.4f),
            fontWeight = FontWeight.Medium,
        )
        Text(
            stringResource(if (paid) R.string.action_paid else R.string.history_skipped),
            Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Chỉ dòng mới nhất mới hoàn tác được (repository cũng kiểm tra lại).
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (canUndo && p.subscriptionId != null) {
                IconButton(onClick = onUndo) { Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.action_undo)) }
            }
        }
    }
}

private fun addToCalendar(context: Context, sub: Subscription) {
    val start = sub.nextPaymentDate.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val rule = when (sub.cycle) {
        BillingCycle.WEEKLY -> "FREQ=WEEKLY"
        BillingCycle.MONTHLY -> "FREQ=MONTHLY"
        BillingCycle.QUARTERLY -> "FREQ=MONTHLY;INTERVAL=3"
        BillingCycle.YEARLY -> "FREQ=YEARLY"
    }
    val amountText = formatMoney(sub.amount, sub.currency)
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, context.getString(R.string.calendar_event_title, sub.name))
        .putExtra(CalendarContract.Events.DESCRIPTION, context.getString(R.string.calendar_event_desc, amountText, context.getString(sub.cycle.label)))
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
        .putExtra(CalendarContract.Events.RRULE, rule)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_calendar_app, Toast.LENGTH_SHORT).show()
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_browser, Toast.LENGTH_SHORT).show()
    }
}
