@file:OptIn(ExperimentalMaterial3Api::class)

package com.nhom4.subtrack.ui.dataset

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nhom4.subtrack.R
import com.nhom4.subtrack.data.DatasetInfo
import com.nhom4.subtrack.ui.AppViewModels
import com.nhom4.subtrack.ui.LoadingState
import com.nhom4.subtrack.ui.formatDate
import java.time.Instant
import java.time.ZoneId

@Composable
fun DatasetsScreen(onBack: () -> Unit, vm: DatasetsViewModel = viewModel(factory = AppViewModels.Factory)) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var renaming by remember { mutableStateOf<DatasetInfo?>(null) }
    var deleting by remember { mutableStateOf<DatasetInfo?>(null) }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.datasets_title), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                windowInsets = WindowInsets(0),
            )
        },
    ) { padding ->
        val list = rows
        if (list == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(stringResource(R.string.datasets_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(list, key = { it.info.id }) { row ->
                DatasetCard(row, onUse = { vm.use(row.info) }, onRename = { renaming = row.info }, onDelete = { deleting = row.info })
            }
        }
    }

    renaming?.let { info ->
        var name by rememberSaveable(info.id) { mutableStateOf(info.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.dataset_rename)) },
            text = {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.dataset_name_label)) })
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { vm.rename(info, name); renaming = null }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    deleting?.let { info ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.dataset_delete_title, info.name)) },
            text = { Text(stringResource(R.string.dataset_delete_body)) },
            confirmButton = {
                TextButton(onClick = { vm.delete(info); deleting = null }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun DatasetCard(row: DatasetRow, onUse: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (row.active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(row.info.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (row.active) AssistChip(onClick = {}, label = { Text(stringResource(R.string.dataset_active)) })
            }
            val created = formatDate(Instant.ofEpochMilli(row.info.createdAt).atZone(ZoneId.systemDefault()).toLocalDate())
            Text(
                if (row.info.isMain) stringResource(R.string.dataset_meta_main, row.count)
                else stringResource(R.string.dataset_meta, row.count, created),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!row.active) TextButton(onClick = onUse) { Text(stringResource(R.string.dataset_use)) }
                TextButton(onClick = onRename) { Text(stringResource(R.string.dataset_rename)) }
                if (!row.active && !row.info.isMain) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}
