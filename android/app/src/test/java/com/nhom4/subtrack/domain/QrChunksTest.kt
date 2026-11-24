package com.nhom4.subtrack.domain

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QrChunksTest {
    /** Văn bản khó nén để chắc chắn ra nhiều khung. */
    private val big = buildString { val r = Random(1); repeat(3000) { append("Gói ${r.nextLong()},${r.nextInt()};") } }

    @Test
    fun smallData_singleFrame() {
        val text = "Tên,Số tiền\r\nNetflix,260000\r\n"
        val frames = QrChunks.encode(text, setId = "abc234")
        assertEquals(1, frames.size)
        assertEquals(QrAssembler.Result.Done(text), QrAssembler().add(frames[0]))
    }

    @Test
    fun bigData_outOfOrderWithDuplicates() {
        val frames = QrChunks.encode(big)
        assertTrue(frames.size > 3)
        val a = QrAssembler()
        var last: QrAssembler.Result? = null
        frames.indices.reversed().forEachIndexed { k, i ->
            last = a.add(frames[i])
            if (k == 0) assertEquals(QrAssembler.Result.Progress(1, frames.size), a.add(frames[i])) // trùng: không tăng
        }
        assertEquals(QrAssembler.Result.Done(big), last)
    }

    @Test
    fun otherSetResets_foreignAndCorruptHandled() {
        val a = QrAssembler()
        val one = QrChunks.encode(big, setId = "aaaaaa")
        val two = QrChunks.encode(big + "x", setId = "bbbbbb")
        a.add(one[0]); a.add(one[1])
        assertEquals(QrAssembler.Result.Progress(1, two.size), a.add(two[0]))
        assertEquals(QrAssembler.Result.Ignored, a.add("https://example.com"))
        assertEquals(QrAssembler.Result.Ignored, a.add("ST1|x|0/2|0|abc"))
        // Sửa dữ liệu một khung: CRC sai → Corrupt, quét lại thì được
        val single = QrChunks.encode("dữ liệu", setId = "cccccc")[0]
        val broken = single.dropLast(4) + (if (single.endsWith("AAAA")) "BBBB" else "AAAA")
        assertEquals(QrAssembler.Result.Corrupt, a.add(broken))
        assertEquals(QrAssembler.Result.Done("dữ liệu"), a.add(single))
    }

    @Test
    fun realQrImage_roundTrip() {
        val frames = QrChunks.encode(big)
        val a = QrAssembler()
        var result: QrAssembler.Result? = null
        frames.forEach { frame ->
            val m = QRCodeWriter().encode(
                frame, BarcodeFormat.QR_CODE, 0, 0, // 1 điểm ảnh/ô, phóng to ×4 bên dưới như ảnh chụp màn hình
                mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2),
            )
            val w = m.width * 4
            val px = IntArray(w * w) { if (m[it % w / 4, it / w / 4]) 0xFF000000.toInt() else -1 }
            val bmp = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(w, w, px)))
            result = a.add(QRCodeReader().decode(bmp, mapOf(DecodeHintType.PURE_BARCODE to true)).text)
        }
        assertEquals(QrAssembler.Result.Done(big), result)
    }
}
