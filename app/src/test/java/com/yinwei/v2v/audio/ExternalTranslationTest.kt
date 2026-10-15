package com.yinwei.v2v.audio

import org.junit.Assert.*
import org.junit.Test

class ExternalTranslationTest {
    @Test fun translationCanBeBoostedToTwoHundredPercentWithoutBoostingOriginal() {
        val mixer = StereoMixer(rampMs = 0)
        val original = shortArrayOf(1000, -1000)
        val voice = shortArrayOf(3000, -3000)
        val output = mixer.render(original, voice, MixSettings(MixMode.SPLIT, 0.2f, 2f))
        assertTrue(kotlin.math.abs(output[0] - 6000) <= 1)
        assertTrue(kotlin.math.abs(output[1] - 200) <= 1)
        val limited = mixer.render(shortArrayOf(32767), shortArrayOf(32767), MixSettings(MixMode.MIX, 1f, 2f))
        assertTrue(limited.all { it in 0..32767 })
    }
    @Test fun externalOutputRoutesOnlyTranslationWithoutGrantingOriginalControl() {
        for (channel in TranslationChannel.entries) {
            val config = MixSettings(MixMode.MIX, 1f, 0.5f, translationChannel = channel).forExternalOutput()
            val result = StereoMixer(rampMs = 0).render(ShortArray(10) { 20000 }, ShortArray(10) { 10000 }, config)
            val left = result[0].toInt(); val right = result[1].toInt()
            assertTrue(kotlin.math.abs((if (channel == TranslationChannel.RIGHT) 0 else 5000) - left) <= 1)
            assertTrue(kotlin.math.abs((if (channel == TranslationChannel.LEFT) 0 else 5000) - right) <= 1)
            assertEquals(0f, config.originalGain, 0f)
        }
        assertEquals(UiCapability.TRANSLATION_OUTPUT_ONLY, SourceCapabilities(translationOutputAvailable = true).ui)
        assertFalse(SourceCapabilities(translationOutputAvailable = true).originalOutputControlled)
    }
}
