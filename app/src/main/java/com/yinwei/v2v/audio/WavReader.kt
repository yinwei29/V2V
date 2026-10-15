package com.yinwei.v2v.audio

import java.io.EOFException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Streaming RIFF reader. M2 deliberately accepts only 48kHz mono PCM16 WAV, never arbitrary PCM guesses. */
class WavReader(private val input: InputStream) : AutoCloseable {
    private var remaining = 0L
    init {
        val header = exact(12)
        require(String(header, 0, 4, Charsets.US_ASCII) == "RIFF" && String(header, 8, 4, Charsets.US_ASCII) == "WAVE") { FORMAT }
        var formatSeen = false
        var scanned = 12L
        while (true) {
            val chunk = exact(8)
            val name = String(chunk, 0, 4, Charsets.US_ASCII)
            val length = ByteBuffer.wrap(chunk, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
            if (name == "data") {
                require(formatSeen && length % 2 == 0L) { FORMAT }
                remaining = length
                break
            }
            require(length <= 1024 * 1024 && scanned + length <= 2 * 1024 * 1024) { "WAV metadata 過大" }
            if (name == "fmt ") {
                require(length in 16..1024) { FORMAT }
                val fmt = ByteBuffer.wrap(exact(length.toInt())).order(ByteOrder.LITTLE_ENDIAN)
                val codec = fmt.short.toInt(); val channels = fmt.short.toInt(); val rate = fmt.int
                val byteRate = fmt.int; val align = fmt.short.toInt(); val bits = fmt.short.toInt()
                require(codec == 1 && channels == 1 && rate == 48000 && bits == 16 && align == 2 && byteRate == 96000) { FORMAT }
                formatSeen = true
            } else discard(length)
            if (length % 2 == 1L) exact(1)
            scanned += length + 8
        }
    }
    fun readFrame(): ShortArray? {
        if (remaining == 0L) return null
        val size = minOf(1920L, remaining).toInt()
        val bytes = exact(size); remaining -= size
        return ShortArray(size / 2).also { ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(it) }
    }
    private fun exact(size: Int): ByteArray {
        val buffer = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val count = input.read(buffer, offset, size - offset)
            if (count < 0) throw EOFException("WAV 檔案不完整")
            if (count == 0) throw EOFException("無法讀取 WAV")
            offset += count
        }
        return buffer
    }
    private fun discard(size: Long) {
        var left = size
        while (left > 0) { val count = minOf(left, 8192).toInt(); exact(count); left -= count }
    }
    override fun close() = input.close()
    companion object { const val FORMAT = "請選擇 48kHz / mono / PCM16 WAV；本版不支援 MP3、AAC、立體聲或其他取樣率" }
}
