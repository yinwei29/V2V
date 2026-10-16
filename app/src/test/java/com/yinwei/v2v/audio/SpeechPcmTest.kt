package com.yinwei.v2v.audio

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SpeechPcmTest {
    @org.junit.Test fun onlyBoundarySilenceIsShortenedAndInternalPauseRemains() {
        val voiced = ShortArray(4800) { 2000 }
        val pcm = ShortArray(12000) + voiced + ShortArray(9600) + voiced + ShortArray(12000)
        val trimmed = SpeechPcm.trimBoundarySilence(pcm)
        org.junit.Assert.assertArrayEquals(ShortArray(1920) + voiced + ShortArray(9600) + voiced + ShortArray(3840), trimmed)
        org.junit.Assert.assertArrayEquals(trimmed, SpeechPcm.trimBoundarySilence(trimmed))
    }
    @org.junit.Test fun quietOrAlreadyTightAudioIsRetained() {
        for (pcm in listOf(shortArrayOf(), ShortArray(4800) { 20 }, shortArrayOf(500, 1000, -1000, 500))) {
            org.junit.Assert.assertArrayEquals(pcm, SpeechPcm.trimBoundarySilence(pcm))
        }
    }

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
