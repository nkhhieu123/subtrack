@file:OptIn(ExperimentalMaterial3Api::class)

package com.nhom4.subtrack.ui.qr

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nhom4.subtrack.R
import com.nhom4.subtrack.ui.AppViewModels
import com.nhom4.subtrack.ui.LoadingState
import kotlinx.coroutines.delay

/** Thời gian mỗi khung của QR động. */
private const val FRAME_MS = 350L

@Composable
fun QrExportScreen(onBack: () -> Unit, vm: QrExportViewModel = viewModel(factory = AppViewModels.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    val view = LocalView.current
    val context = LocalContext.current
    val saveGif = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/gif")) { uri -> uri?.let(vm::saveGif) }
    LaunchedEffect(vm) { vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }
    // Giữ màn hình sáng trong lúc máy kia quét
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.qr_export_title), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                windowInsets = WindowInsets(0),
            )
        },
    ) { padding ->
        val s = state
        if (s == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        var index by rememberSaveable { mutableIntStateOf(0) }
        var playing by rememberSaveable { mutableStateOf(true) }
        val n = s.frames.size
        LaunchedEffect(playing, n) {
            while (playing && n > 1) {
                delay(FRAME_MS)
                index = (index + 1) % n
            }
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.qr_export_summary, s.datasetName, s.subscriptions, s.payments), style = MaterialTheme.typography.titleSmall)
            // Nền trắng cố định: mã QR phải đen trên trắng kể cả ở giao diện tối
            Image(
                s.frames[index % n], contentDescription = stringResource(R.string.qr_export_title),
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(1f).background(Color.White),
                filterQuality = FilterQuality.None,
            )
            if (n > 1) {
                Text(stringResource(R.string.qr_frame, index % n + 1, n), style = MaterialTheme.typography.labelLarge)
                // Tạm dừng thì bước từng khung: đưa lại khung máy quét bị sót, hoặc chụp ảnh từng khung
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!playing) IconButton(onClick = { index = (index % n + n - 1) % n }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.qr_prev))
                    }
                    OutlinedButton(onClick = { playing = !playing }) {
                        Text(stringResource(if (playing) R.string.qr_pause else R.string.qr_play))
                    }
                    if (!playing) IconButton(onClick = { index = (index % n + 1) % n }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.qr_next))
                    }
                }
            }
            Button(onClick = { saveGif.launch("subtrack-qr.gif") }) { Text(stringResource(R.string.qr_save_gif)) }
            Text(stringResource(R.string.qr_export_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
