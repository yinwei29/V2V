package com.yinwei.v2v.audio

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class StereoMixerTest {
    private val size = 4800
    private fun tone(f: Double, amplitude: Double = 0.2) = ShortArray(size) { (32767 * amplitude * sin(2 * PI * f * it / 48000)).roundToInt().toShort() }
    private fun energy(pcm: ShortArray, channel: Int): Double = (size / 2 until size).sumOf { val s = pcm[it * 2 + channel].toDouble(); s * s }
    private fun spectral(pcm: ShortArray, channel: Int, f: Double): Double {
        val re = (size / 2 until size).sumOf { pcm[it * 2 + channel] * cos(2 * PI * f * it / 48000) }
        val im = (size / 2 until size).sumOf { pcm[it * 2 + channel] * sin(2 * PI * f * it / 48000) }
        return re * re + im * im
    }
    @Test fun splitIsolatesAndSwapsDistinctFrequencies() {
        val o = tone(440.0); val t = tone(660.0)
        for (swap in listOf(false, true)) {
            val pcm = StereoMixer().render(o, t, MixSettings(MixMode.SPLIT, 1f, 1f, swap))
            val translationChannel = if (swap) 1 else 0
            assertTrue(spectral(pcm, translationChannel, 660.0) > spectral(pcm, translationChannel, 440.0) * 10000)
            assertTrue(spectral(pcm, 1 - translationChannel, 440.0) > spectral(pcm, 1 - translationChannel, 660.0) * 10000)
        }
    }
    @Test fun translationOnlySuppressesOriginalInBothEars() {
        val pcm = StereoMixer().render(tone(440.0), tone(660.0), MixSettings(MixMode.TRANSLATION_ONLY, 1f, 1f))
        for (i in pcm.indices step 2) assertEquals(pcm[i], pcm[i + 1])
        assertTrue(spectral(pcm, 0, 660.0) > spectral(pcm, 0, 440.0) * 10000)
    }
    @Test fun silentTranslationDoesNotChangeAsrInput() {
        val frame = AudioFrame("session", Source.TEST_FILE, 0, 1, 48000, tone(440.0))
        val probe = AsrProbe(); probe.consume(frame)
        val pcm = StereoMixer().render(frame.pcm, ShortArray(size), MixSettings(MixMode.TRANSLATION_ONLY, 0f, 0f))
        assertEquals(size.toLong(), probe.samples)
        assertTrue(probe.lastRms > 0.1)
        assertEquals(0.0, energy(pcm, 0), 0.0)
    }
    @Test fun limiterIsBoundedContinuousAndDoesNotHardClip() {
        for (i in -2000..2000) assertTrue(abs(StereoMixer.limit(i / 1000f)) <= 1f)
        assertEquals(StereoMixer.limit(0.8f), StereoMixer.limit(0.80001f), 0.00002f)
        assertTrue(StereoMixer.limit(1.0f) < StereoMixer.limit(1.2f))
        val pcm = StereoMixer().render(ShortArray(size) { 32767 }, ShortArray(size) { 32767 }, MixSettings(MixMode.MIX, 1f, 1f))
        assertTrue(pcm.all { it >= 0 })
        assertTrue(pcm.maxOrNull()!! <= 32767)
    }
    @Test fun rapidModeChangesRampWithoutDiscontinuityOrReplaying() {
        val mixer = StereoMixer()
        val input = ShortArray(960) { 8000 }
        val previous = IntArray(2)
        repeat(30) { round ->
            val pcm = mixer.render(input, ShortArray(input.size) { -4000 }, MixSettings(MixMode.entries[round % 3], 1f, 1f, round % 2 == 0))
            for (i in pcm.indices step 2) for (channel in 0..1) {
                val sample = pcm[i + channel].toInt()
                assertTrue(abs(sample - previous[channel]) <= 8)
                previous[channel] = sample
            }
            assertEquals(input.size * 2, pcm.size) // Output bounded by new input; no retained audio queue.
        }
    }
    @Test fun downmixRetainsHeadroomAndDocumentsPhaseCancellation() {
        assertTrue(StereoMixer.downmix(32767, 32767) < 24000)
        assertEquals(0.toShort(), StereoMixer.downmix(10000, -10000))
    }
    @Test fun originalGainIsRestoredByChangingMode() {
        val mixer = StereoMixer()
        val settings = MixSettings(MixMode.MIX, 0.4f, 0f)
        val input = ShortArray(size) { 10000 }
        mixer.render(input, ShortArray(size), settings)
        val mute = mixer.render(input, ShortArray(size), settings.copy(mode = MixMode.TRANSLATION_ONLY))
        assertEquals(0.toShort(), mute.last())
        val restored = mixer.render(input, ShortArray(size), settings)
        assertEquals(4000.0, restored.last().toDouble(), 1.0)
    }
    @Test fun externalCapabilityNeverGrantsOriginalControl() {
        assertEquals(UiCapability.CAPTURE_ONLY, SourceCapabilities(inputVerified = true).ui)
        assertEquals(OriginalGainControlCapability.UNAVAILABLE, SourceCapabilities().originalControl)
    }
}
