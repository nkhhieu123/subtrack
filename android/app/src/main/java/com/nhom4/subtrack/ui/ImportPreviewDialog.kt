package com.nhom4.subtrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nhom4.subtrack.R
import com.nhom4.subtrack.SubTrackApp
import com.nhom4.subtrack.domain.ParsedDataset
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Dữ liệu vừa đọc được (từ tệp CSV hoặc mã QR), chờ người dùng chọn Lưu và dùng / Chỉ lưu / Hủy. */
data class ImportPreview(val parsed: ParsedDataset, val defaultName: String)

fun SubTrackApp.importPreview(parsed: ParsedDataset, now: LocalDateTime = LocalDateTime.now()) =
    ImportPreview(parsed, getString(R.string.import_default_name, now.format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))))

/** Luôn lưu thành bộ dữ liệu mới. Trả về thông báo để hiện snackbar. */
suspend fun SubTrackApp.saveImport(preview: ImportPreview, name: String, activate: Boolean): String {
    val info = datasets.create(name.ifBlank { preview.defaultName }, preview.parsed.data, activate = false)
    if (activate) switchDataset(info.id)
    return getString(if (activate) R.string.import_saved_used else R.string.import_saved, info.name)
}

@Composable
fun ImportPreviewDialog(
    preview: ImportPreview,
    onSave: (name: String, activate: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(preview.defaultName) }
    val d = preview.parsed.data
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.import_summary, d.subscriptions.size, d.tags.size, d.payments.size))
                if (preview.parsed.skipped > 0) {
                    Text(
                        stringResource(R.string.import_skipped, preview.parsed.skipped),
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedTextField(
                    name, { name = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.dataset_name_label)) }, singleLine = true,
                )
            }
        },
        confirmButton = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                TextButton(onClick = { onSave(name, true) }) { Text(stringResource(R.string.import_save_use)) }
                TextButton(onClick = { onSave(name, false) }) { Text(stringResource(R.string.import_save_only)) }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}
