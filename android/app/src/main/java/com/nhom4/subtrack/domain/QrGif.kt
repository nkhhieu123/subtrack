package com.nhom4.subtrack.domain

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.ByteArrayOutputStream

/**
 * Mã QR động lưu thành một ảnh GIF lặp vô hạn: mỗi khung QR là một khung GIF.
 * Lưu một tệp, nạp lại một tệp; xem được trong thư viện ảnh và máy khác vẫn quét bằng camera được.
 * Android không có sẵn bộ mã hoá/giải mã GIF theo từng khung nên tự viết (LZW, theo giflib).
 */
object QrGif {
    /** Điểm ảnh mỗi ô QR trong GIF. */
    const val SCALE = 4
    /** Thời gian mỗi khung, đơn vị 1/100 giây (bằng tốc độ màn Xuất mã QR). */
    const val DELAY_CS = 35

    fun qrMatrix(text: String): BitMatrix = QRCodeWriter().encode(
        text, BarcodeFormat.QR_CODE, 0, 0,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2),
    )

    fun isGif(bytes: ByteArray) = bytes.size > 6 && String(bytes, 0, 4, Charsets.US_ASCII) == "GIF8"

    /** Các khung QR → GIF 2 màu (0 = trắng, 1 = đen). Khung nhỏ hơn được đặt giữa nền trắng. */
    fun encode(frames: List<String>): ByteArray {
        val ms = frames.map(::qrMatrix)
        val size = ms.maxOf { maxOf(it.width, it.height) } * SCALE
        val out = ByteArrayOutputStream()
        fun u16(v: Int) { out.write(v and 0xFF); out.write(v shr 8 and 0xFF) }
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        u16(size); u16(size)
        out.write(0x80); out.write(0); out.write(0) // bảng màu chung 2 màu
        out.write(byteArrayOf(-1, -1, -1, 0, 0, 0))
        // Lặp vô hạn (NETSCAPE2.0)
        out.write(byteArrayOf(0x21, -1, 0x0B)); out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(3, 1, 0, 0, 0))
        for (m in ms) {
            out.write(byteArrayOf(0x21, -7, 4, 4)); u16(DELAY_CS); out.write(0); out.write(0)
            out.write(0x2C); u16(0); u16(0); u16(size); u16(size); out.write(0)
            val ox = (size - m.width * SCALE) / 2
            val oy = (size - m.height * SCALE) / 2
            val px = ByteArray(size * size)
            for (y in 0 until m.height * SCALE) for (x in 0 until m.width * SCALE) {
                if (m[x / SCALE, y / SCALE]) px[(oy + y) * size + ox + x] = 1
            }
            out.write(2) // cỡ mã LZW tối thiểu
            val data = lzw(px, 2)
            for (i in data.indices step 255) {
                val n = minOf(255, data.size - i)
                out.write(n); out.write(data, i, n)
            }
            out.write(0)
        }
        out.write(0x3B)
        return out.toByteArray()
    }

    private fun lzw(px: ByteArray, minSize: Int): ByteArray {
        val clear = 1 shl minSize
        val eoi = clear + 1
        val out = ByteArrayOutputStream()
        var acc = 0
        var bits = 0
        var size = minSize + 1
        var next = eoi + 1
        val dict = HashMap<Int, Int>()
        fun emit(code: Int) {
            acc = acc or (code shl bits); bits += size
            while (bits >= 8) { out.write(acc and 0xFF); acc = acc ushr 8; bits -= 8 }
            if (next >= (1 shl size) && size < 12) size++
        }
        emit(clear)
        var cur = px[0].toInt()
        for (i in 1 until px.size) {
            val p = px[i].toInt()
            val key = (cur shl 8) or p
            val hit = dict[key]
            if (hit != null) { cur = hit; continue }
            emit(cur)
            if (next >= 4095) {
                emit(clear)
                next = eoi + 1; size = minSize + 1; dict.clear()
            } else dict[key] = next++
            cur = p
        }
        emit(cur)
        emit(eoi)
        if (bits > 0) out.write(acc and 0xFF)
        return out.toByteArray()
    }

    /** Khung GIF đã ghép lên nền, dạng ARGB. */
    class Frame(val width: Int, val height: Int, val argb: IntArray)

    /** Giải mã mọi khung của một GIF bất kỳ (bảng màu chung/riêng, xen dòng, khung một phần). */
    fun frames(bytes: ByteArray): List<Frame> {
        require(isGif(bytes)) { "Không phải ảnh GIF" }
        var pos = 6
        fun u8() = bytes[pos++].toInt() and 0xFF
        fun u16() = u8() or (u8() shl 8)
        fun table(n: Int) = IntArray(n) { 0xFF shl 24 or (u8() shl 16) or (u8() shl 8) or u8() }
        val w = u16(); val h = u16()
        val packed = u8(); pos += 2
        val global = if (packed and 0x80 != 0) table(2 shl (packed and 7)) else null
        val canvas = IntArray(w * h) { -1 }
        val result = ArrayList<Frame>()
        var transparent = -1
        while (pos < bytes.size) {
            when (u8()) {
                0x21 -> {
                    val label = u8()
                    if (label == 0xF9) { u8(); val p = u8(); pos += 2; val t = u8(); u8(); transparent = if (p and 1 != 0) t else -1 }
                    else while (true) { val n = u8(); if (n == 0) break; pos += n }
                }
                0x2C -> {
                    val fx = u16(); val fy = u16(); val fw = u16(); val fh = u16()
                    val fp = u8()
                    val colors = if (fp and 0x80 != 0) table(2 shl (fp and 7)) else global ?: IntArray(256) { -1 }
                    val minSize = u8()
                    val data = ByteArrayOutputStream()
                    while (true) { val n = u8(); if (n == 0) break; data.write(bytes, pos, n); pos += n }
                    val idx = unlzw(data.toByteArray(), minSize, fw * fh)
                    val rows = if (fp and 0x40 != 0) interlaced(fh) else IntArray(fh) { it }
                    for (r in 0 until fh) for (c in 0 until fw) {
                        val x = fx + c; val y = fy + rows[r]
                        val i = idx[r * fw + c].toInt() and 0xFF
                        if (x < w && y < h && i != transparent && i < colors.size) canvas[y * w + x] = colors[i]
                    }
                    result += Frame(w, h, canvas.copyOf())
                    transparent = -1
                }
                else -> break // 0x3B: hết tệp
            }
        }
        return result
    }

    private fun interlaced(h: Int): IntArray {
        val order = ArrayList<Int>(h)
        for ((start, step) in listOf(0 to 8, 4 to 8, 2 to 4, 1 to 2)) order += (start until h step step)
        return order.toIntArray()
    }

    private fun unlzw(data: ByteArray, minSize: Int, count: Int): ByteArray {
        val clear = 1 shl minSize
        val eoi = clear + 1
        val prefix = IntArray(4096)
        val suffix = ByteArray(4096)
        val first = ByteArray(4096)
        for (i in 0 until clear) { suffix[i] = i.toByte(); first[i] = i.toByte() }
        val out = ByteArray(count)
        val stack = ByteArray(4097)
        var n = 0
        var size = minSize + 1
        var next = eoi + 1
        var prev = -1
        var acc = 0
        var bits = 0
        var pos = 0
        while (n < count) {
            while (bits < size && pos < data.size) { acc = acc or ((data[pos++].toInt() and 0xFF) shl bits); bits += 8 }
            if (bits < size) break
            val code = acc and ((1 shl size) - 1)
            acc = acc ushr size; bits -= size
            if (code == clear) { size = minSize + 1; next = eoi + 1; prev = -1; continue }
            if (code == eoi) break
            var c = if (code < next || prev == -1) code else prev
            var sp = 0
            if (code >= next && prev != -1) stack[sp++] = first[prev]
            while (c >= clear) { stack[sp++] = suffix[c]; c = prefix[c] }
            stack[sp++] = c.toByte()
            if (prev != -1 && next < 4096) {
                prefix[next] = prev
                suffix[next] = stack[sp - 1]
                first[next] = first[prev]
                next++
                if (next == (1 shl size) && size < 12) size++
            }
            while (sp > 0 && n < count) out[n++] = stack[--sp]
            prev = code
        }
        return out
    }

    /** Đọc mã QR trong từng khung GIF. Khung không đọc được thì bỏ qua. */
    fun decodeTexts(bytes: ByteArray): List<String> = frames(bytes).mapNotNull { f ->
        val bmp = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(f.width, f.height, f.argb)))
        runCatching { QRCodeReader().decode(bmp, mapOf(DecodeHintType.PURE_BARCODE to true)).text }
            .recoverCatching { QRCodeReader().decode(bmp, mapOf(DecodeHintType.TRY_HARDER to true)).text }
            .getOrNull()
    }
}
