package com.yinwei.v2v.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Engine PCM16 -> mono 48k. Only upsampling is supported, avoiding downsampling aliasing. */
object SpeechPcm {
    /** Remove only near-silent engine padding at edges, preserving consonant margins and internal pauses. */
    fun trimBoundarySilence(pcm: ShortArray): ShortArray {
        val first = pcm.indexOfFirst { kotlin.math.abs(it.toInt()) > 32 }
        if (first < 0) return pcm
        val last = pcm.indexOfLast { kotlin.math.abs(it.toInt()) > 32 }
        val start = (first - 1920).coerceAtLeast(0) // 40ms onset margin at 48k
        val end = (last + 1 + 3840).coerceAtMost(pcm.size) // 80ms release margin
        return if (start == 0 && end == pcm.size) pcm else pcm.copyOfRange(start, end)
    }

    fun decode(bytes: ByteArray, sampleRate: Int, channels: Int): ShortArray {
        require(sampleRate in 8000..48000 && channels in 1..2)
        require(bytes.size % (channels * 2) == 0)
        val samples = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val mono = FloatArray(samples.remaining() / channels) {
            if (channels == 1) samples.get().toFloat() else (samples.get().toInt() + samples.get().toInt()) / 2f
        }
        if (mono.isEmpty()) return ShortArray(0)
        return ShortArray((mono.size.toLong() * 48000 / sampleRate).toInt()) { i ->
            val position = i.toDouble() * sampleRate / 48000
            val left = position.toInt().coerceAtMost(mono.lastIndex)
            val right = (left + 1).coerceAtMost(mono.lastIndex)
            (mono[left] + (mono[right] - mono[left]) * (position - left)).toInt().coerceIn(-32768, 32767).toShort()
        }
    }
}
