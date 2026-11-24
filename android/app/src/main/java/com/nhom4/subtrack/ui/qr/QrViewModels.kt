package com.nhom4.subtrack.ui.qr

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.nhom4.subtrack.R
import com.nhom4.subtrack.SubTrackApp
import com.nhom4.subtrack.domain.DatasetCsv
import com.nhom4.subtrack.domain.QrAssembler
import com.nhom4.subtrack.domain.QrChunks
import com.nhom4.subtrack.domain.QrGif
import com.nhom4.subtrack.ui.ImportPreview
import com.nhom4.subtrack.ui.importPreview
import com.nhom4.subtrack.ui.saveImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 1 điểm ảnh mỗi ô; màn hình phóng to bằng FilterQuality.None nên ảnh nhỏ mà vẫn sắc nét. */
fun qrImage(text: String): ImageBitmap {
    val m = QrGif.qrMatrix(text)
    val px = IntArray(m.width * m.height) { if (m[it % m.width, it / m.width]) 0xFF000000.toInt() else -1 }
    return Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888).asImageBitmap()
}

data class QrExportState(val datasetName: String, val subscriptions: Int, val payments: Int, val frames: List<ImageBitmap>)

class QrExportViewModel(private val app: SubTrackApp) : ViewModel() {
    private val _state = MutableStateFlow<QrExportState?>(null)
    /** null = đang tạo mã. */
    val state: StateFlow<QrExportState?> = _state.asStateFlow()
    private var texts: List<String> = emptyList()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            val data = app.repository.snapshot()
            val text = DatasetCsv(app::getString).write(data)
            texts = QrChunks.encode(text)
            val frames = withContext(Dispatchers.Default) { texts.map(::qrImage) }
            _state.value = QrExportState(app.datasets.state.value.active.name, data.subscriptions.size, data.payments.size, frames)
        }
    }

    /** Lưu toàn bộ các khung thành một ảnh GIF lặp vô hạn; nạp lại chỉ cần chọn đúng tệp này. */
    fun saveGif(uri: Uri) {
        val t = texts.ifEmpty { return }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val gif = QrGif.encode(t)
                    app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(gif) }
                }.isSuccess
            }
            _messages.send(app.getString(if (ok) R.string.qr_gif_saved else R.string.qr_gif_failed))
        }
    }
}

data class QrScanState(val received: Int = 0, val total: Int = 0, val corrupt: Boolean = false, val preview: ImportPreview? = null)

class QrScanViewModel(private val app: SubTrackApp) : ViewModel() {
    private val assembler = QrAssembler()
    private val csv = DatasetCsv(app::getString)
    private val _state = MutableStateFlow(QrScanState())
    val state: StateFlow<QrScanState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()
    private val _saved = Channel<String>(Channel.BUFFERED)
    /** Đã lưu xong: màn hình báo rồi đóng. */
    val saved = _saved.receiveAsFlow()

    /** Gọi trên luồng chính cho mỗi mã camera đọc được (một khung có thể tới nhiều lần). */
    fun onCode(raw: String) {
        if (_state.value.preview != null) return
        when (val r = assembler.add(raw)) {
            QrAssembler.Result.Ignored -> Unit
            is QrAssembler.Result.Progress -> _state.value = QrScanState(r.received, r.total)
            QrAssembler.Result.Corrupt -> _state.value = QrScanState(corrupt = true)
            is QrAssembler.Result.Done -> {
                val parsed = runCatching { csv.read(r.text) }.getOrNull()
                if (parsed == null) {
                    _state.value = QrScanState()
                    _messages.trySend(app.getString(R.string.import_failed))
                } else _state.value = QrScanState(preview = app.importPreview(parsed))
            }
        }
    }

    private val imageScanner by lazy {
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
    }

    /**
     * Đọc mã QR trong các ảnh đã chọn, ghép như khi quét camera: ảnh GIF mã động (đọc mọi khung),
     * ảnh chụp mã tĩnh, hoặc ảnh từng khung. Ảnh không có mã SubTrack thì bỏ qua.
     */
    fun scanImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val codes = withContext(Dispatchers.IO) {
                uris.flatMap { uri ->
                    runCatching {
                        val bytes = app.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                        // ML Kit chỉ đọc khung đầu của GIF nên GIF tự giải mã từng khung
                        if (QrGif.isGif(bytes)) QrGif.decodeTexts(bytes)
                        else Tasks.await(imageScanner.process(InputImage.fromFilePath(app, uri))).mapNotNull { it.rawValue }
                    }.getOrDefault(emptyList())
                }
            }
            val ours = codes.filter { it.startsWith(QrChunks.PREFIX + "|") }
            ours.forEach(::onCode)
            val s = _state.value
            when {
                ours.isEmpty() -> _messages.send(app.getString(R.string.qr_images_none))
                s.preview == null && s.total > 1 -> _messages.send(app.getString(R.string.qr_images_incomplete, s.received, s.total))
            }
        }
    }

    override fun onCleared() {
        runCatching { imageScanner.close() }
    }

    /** Hủy: bỏ dữ liệu vừa quét, quét lại từ đầu. */
    fun cancel() {
        assembler.reset()
        _state.value = QrScanState()
    }

    fun save(name: String, activate: Boolean) {
        val p = _state.value.preview ?: return
        viewModelScope.launch {
            runCatching { app.saveImport(p, name, activate) }
                .onSuccess { _saved.send(it) }
                .onFailure { _messages.send(app.getString(R.string.save_failed)); cancel() }
        }
    }
}
