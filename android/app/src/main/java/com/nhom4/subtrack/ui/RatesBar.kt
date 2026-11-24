package com.nhom4.subtrack.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.nhom4.subtrack.R
import com.nhom4.subtrack.data.RatesRepository
import com.nhom4.subtrack.data.RatesState
import java.time.LocalDate

/** Trạng thái tỷ giá + nút cập nhật. Lỗi hiện ngay tại đây, không dùng thông báo hệ thống. */
@Composable
fun RatesBar(rates: RatesState, onSync: () -> Unit, modifier: Modifier = Modifier) {
    val today = LocalDate.now()
    val status = when {
        rates.syncing -> stringResource(R.string.rates_syncing)
        rates.fetchedOn == today -> stringResource(R.string.rates_today)
        rates.fetchedOn != null -> stringResource(R.string.rates_not_today, formatDate(rates.fetchedOn))
        rates.failed -> stringResource(R.string.rates_none_failed)
        else -> stringResource(R.string.rates_none)
    }
    val warn = !rates.syncing && rates.fetchedOn != today && (rates.failed || rates.fetchedOn != null)
    val uri = LocalUriHandler.current
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                status,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onSync, enabled = !rates.syncing) {
                if (rates.syncing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Outlined.Sync, null, Modifier.size(18.dp))
                Text(stringResource(R.string.rates_sync), Modifier.padding(start = 6.dp))
            }
        }
        // Điều kiện dùng bản miễn phí của ExchangeRate-API: ghi nguồn ở nơi hiện tỷ giá.
        Text(
            stringResource(R.string.rates_attribution),
            Modifier.clickable { runCatching { uri.openUri(RatesRepository.ATTRIBUTION_URL) } },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textDecoration = TextDecoration.Underline,
        )
    }
}
