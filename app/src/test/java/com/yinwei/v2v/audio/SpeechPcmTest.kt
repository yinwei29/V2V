package com.yinwei.v2v.audio

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SpeechPcmTest {
    private fun bytes(vararg pcm: Short) = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply { pcm.forEach { putShort(it) } }.array()
    @Test fun stereoDownmixDoesNotOverflowAndPreservesLength() {
        val result = SpeechPcm.decode(bytes(32767, 32767, -32768, -32768), 48000, 2)
        assertArrayEquals(shortArrayOf(32767, -32768), result)
    }
    @Test fun resamplesEngine24kMonoTo48k() {
        assertArrayEquals(shortArrayOf(0, 500, 1000, 1000), SpeechPcm.decode(bytes(0, 1000), 24000, 1))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnsupportedDownsampling() {
        SpeechPcm.decode(bytes(0, 0), 96000, 1)
    }
    @Test fun bufferBoundsAndClearPreventOldPlayback() {
        val buffer = SpeechBuffer()
        assertTrue(buffer.offer(ShortArray(480000) { 123 }))
        assertFalse(buffer.offer(shortArrayOf(8)))
        assertArrayEquals(shortArrayOf(123, 123), buffer.take(2))
        buffer.clear()
        assertFalse(buffer.hasAudio())
        assertArrayEquals(shortArrayOf(0, 0), buffer.take(2))
    }
    @Test fun bufferMaintainsFifoAndThreeUtteranceLimit() {
        val buffer = SpeechBuffer()
        assertTrue(buffer.offer(shortArrayOf(1, 2)))
        assertTrue(buffer.offer(shortArrayOf(3)))
        assertTrue(buffer.offer(shortArrayOf(4)))
        assertFalse(buffer.offer(shortArrayOf(5)))
        assertArrayEquals(shortArrayOf(1, 2, 3, 4, 0), buffer.take(5))
    }
}
