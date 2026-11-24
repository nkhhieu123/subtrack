package com.nhom4.subtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.random.Random

class QrGifTest {
    private val big = buildString { val r = Random(7); repeat(1500) { append("Gói ${r.nextLong()},${r.nextInt()};") } }

    @Test
    fun animatedGif_roundTrip_assemblesOriginal() {
        val frames = QrChunks.encode(big)
        assertTrue(frames.size > 3)
        val gif = QrGif.encode(frames)
        assertTrue(QrGif.isGif(gif))
        assertEquals(frames, QrGif.decodeTexts(gif))
        val a = QrAssembler()
        val results = QrGif.decodeTexts(gif).map(a::add)
        assertEquals(QrAssembler.Result.Done(big), results.last())
    }

    /** Bộ giải mã GIF chuẩn của JDK đọc được tệp: đủ khung và mã QR trong từng khung còn nguyên. */
    @Test
    fun encoder_readableByStandardDecoder() {
        val frames = QrChunks.encode(big)
        val gif = QrGif.encode(frames)
        val reader = ImageIO.getImageReadersByFormatName("gif").next()
        reader.input = ImageIO.createImageInputStream(ByteArrayInputStream(gif))
        val n = reader.getNumImages(true)
        assertEquals(frames.size, n)
        val img = reader.read(n - 1)
        val mine = QrGif.frames(gif).last()
        for (y in 0 until img.height step 3) for (x in 0 until img.width step 3) {
            assertEquals("($x,$y)", img.getRGB(x, y) and 0xFFFFFF, mine.argb[y * mine.width + x] and 0xFFFFFF)
        }
    }

    /** Bộ giải mã đọc được GIF do công cụ khác tạo (ở đây là JDK: bảng màu và cách nén khác). */
    @Test
    fun decoder_readsForeignGif() {
        val text = QrChunks.encode("Tên,Số tiền\r\nNetflix,260000\r\n").single()
        val m = QrGif.qrMatrix(text)
        val s = 3
        val img = BufferedImage(m.width * s, m.height * s, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until img.height) for (x in 0 until img.width) img.setRGB(x, y, if (m[x / s, y / s]) 0 else 0xFFFFFF)
        val bytes = ByteArrayOutputStream().also { ImageIO.write(img, "gif", it) }.toByteArray()
        assertEquals(listOf(text), QrGif.decodeTexts(bytes))
    }
}
