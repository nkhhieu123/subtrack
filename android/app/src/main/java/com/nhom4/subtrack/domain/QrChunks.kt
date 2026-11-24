package com.nhom4.subtrack.domain

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Chuyển CSV thành các khung QR và ghép lại. Dữ liệu nén gzip + Base64 rồi chia khung
 * `ST1|<bộ>|<i>/<n>|<crc>|<phần>`; 1 khung = QR tĩnh, nhiều khung = QR động chiếu lần lượt.
 */
object QrChunks {
    const val PREFIX = "ST1"
    /** Ký tự dữ liệu mỗi khung: đủ nhỏ để camera điện thoại đọc nhanh từ màn hình. */
    const val CHUNK_SIZE = 700

    fun encode(text: String, setId: String = randomSetId(), chunkSize: Int = CHUNK_SIZE): List<String> {
        val payload = Base64.getEncoder().encodeToString(gzip(text.toByteArray(Charsets.UTF_8)))
        val crc = crc(payload)
        val parts = payload.chunked(chunkSize).ifEmpty { listOf("") }
        return parts.mapIndexed { i, part -> "$PREFIX|$setId|${i + 1}/${parts.size}|$crc|$part" }
    }

    fun randomSetId(): String = (1..6).map { "abcdefghijkmnpqrstuvwxyz23456789".random() }.joinToString("")

    internal fun crc(s: String): String = CRC32().apply { update(s.toByteArray(Charsets.US_ASCII)) }.value.toString(16)

    private fun gzip(bytes: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()

    internal fun gunzip(bytes: ByteArray): ByteArray = GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
}

/** Ghép khung QR theo bất kỳ thứ tự nào; khung trùng bỏ qua, khung của bộ khác thì bắt đầu lại. */
class QrAssembler {
    sealed interface Result {
        /** Mã không phải của SubTrack. */
        data object Ignored : Result
        data class Progress(val received: Int, val total: Int) : Result
        data class Done(val text: String) : Result
        /** Đủ khung nhưng hỏng (CRC sai / giải nén lỗi): đã xoá, quét lại từ đầu. */
        data object Corrupt : Result
    }

    private var setId: String? = null
    private var crc = ""
    private var parts = arrayOfNulls<String>(0)

    val received get() = parts.count { it != null }
    val total get() = parts.size

    fun add(frame: String): Result {
        val f = frame.split('|', limit = 5)
        if (f.size != 5 || f[0] != QrChunks.PREFIX) return Result.Ignored
        val (i, n) = f[2].split('/').mapNotNull { it.toIntOrNull() }.takeIf { it.size == 2 } ?: return Result.Ignored
        if (n < 1 || i !in 1..n) return Result.Ignored
        if (f[1] != setId || f[3] != crc || n != parts.size) {
            setId = f[1]; crc = f[3]; parts = arrayOfNulls(n)
        }
        parts[i - 1] = f[4]
        if (received < total) return Result.Progress(received, total)
        val payload = parts.joinToString("")
        val text = runCatching {
            check(QrChunks.crc(payload) == crc)
            String(QrChunks.gunzip(java.util.Base64.getDecoder().decode(payload)), Charsets.UTF_8)
        }.getOrNull()
        reset()
        return if (text != null) Result.Done(text) else Result.Corrupt
    }

    fun reset() {
        setId = null; crc = ""; parts = arrayOfNulls(0)
    }
}
