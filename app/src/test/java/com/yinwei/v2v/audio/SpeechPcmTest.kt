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
}
