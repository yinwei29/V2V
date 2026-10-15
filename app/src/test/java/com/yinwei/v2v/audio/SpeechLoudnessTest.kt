package com.yinwei.v2v.audio

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin

class SpeechLoudnessTest {
    private fun tone(amplitude: Int) = ShortArray(48000) { (sin(it * 2 * Math.PI * 500 / 48000) * amplitude).toInt().toShort() }
    @Test fun quietSpeechBecomesAudiblyStrongerWithinGainCap() {
        val level = SpeechLoudness.process(tone(1500), true)
        assertEquals(4f, level.appliedGain, 0f)
        assertTrue(level.outputRms / level.inputRms > 3.9f)
    }
    @Test fun silenceAndNearSilenceAreNeverBoosted() {
        val silence = SpeechLoudness.process(ShortArray(960), true)
        val noise = SpeechLoudness.process(tone(10), true)
        assertEquals(0f, silence.outputRms, 0f); assertEquals(1f, noise.appliedGain, 0f)
    }
    @Test fun disabledEnhancementAndAlreadyLoudVoicePreserveSamples() {
        assertArrayEquals(tone(5000), SpeechLoudness.process(tone(5000), false).pcm)
        assertArrayEquals(tone(18000), SpeechLoudness.process(tone(18000), true).pcm)
    }
    @Test fun quietPhraseWithLoudPeakDoesNotWrapOrInvertSign() {
        val pcm = tone(1000).apply { this[300] = 32767; this[301] = -32768 }
        val output = SpeechLoudness.process(pcm, true)
        assertTrue(output.pcm[300] > 30000); assertTrue(output.pcm[301] < -30000)
        assertTrue(output.pcm.indices.all { i -> pcm[i].toInt() * output.pcm[i].toInt() >= 0 })
        val mixed = StereoMixer(rampMs = 0).render(ShortArray(output.pcm.size), output.pcm, MixSettings(translationGain = 4f))
        assertTrue(mixed.indices.all { i -> output.pcm[i / 2].toInt() * mixed[i].toInt() >= 0 })
    }
}
