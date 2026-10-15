package com.yinwei.v2v.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Engine PCM16 -> mono 48k. Only upsampling is supported, avoiding downsampling aliasing. */
object SpeechPcm {
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

/** Bounded playback storage; never truncates an utterance to make room. */
class SpeechBuffer {
    private val queue = ArrayDeque<ShortArray>()
    private var offset = 0
    private var samples = 0
    @Synchronized fun offer(pcm: ShortArray): Boolean {
        if (queue.size >= 3 || pcm.size > 480000 - samples) return false
        if (pcm.isNotEmpty()) { queue.addLast(pcm); samples += pcm.size }
        return true
    }
    @Synchronized fun take(size: Int): ShortArray = ShortArray(size).also { out ->
        var written = 0
        while (written < size && queue.isNotEmpty()) {
            val first = queue.first()
            val count = minOf(size - written, first.size - offset)
            first.copyInto(out, written, offset, offset + count)
            written += count; offset += count; samples -= count
            if (offset == first.size) { queue.removeFirst(); offset = 0 }
        }
    }
    @Synchronized fun clear() { queue.clear(); offset = 0; samples = 0 }
    @Synchronized fun utterances() = queue.size
    @Synchronized fun hasAudio() = samples > 0
}
