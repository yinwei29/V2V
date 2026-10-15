package com.yinwei.v2v.audio

import kotlin.math.sqrt
import kotlin.math.roundToInt

data class SpeechLevel(val pcm: ShortArray, val inputRms: Float, val outputRms: Float, val appliedGain: Float)

/** Raises quiet synthesized speech toward -15dBFS RMS; never amplifies near-silence. */
object SpeechLoudness {
    fun process(pcm: ShortArray, enabled: Boolean): SpeechLevel {
        fun rms(samples: ShortArray): Float = if (samples.isEmpty()) 0f else
            sqrt(samples.sumOf { val x = it.toDouble() / 32768; x * x } / samples.size).toFloat()
        val input = rms(pcm)
        val gain = if (!enabled || input < 0.0015f) 1f else (0.18f / input).coerceIn(1f, 4f)
        val output = if (gain == 1f) pcm else ShortArray(pcm.size) { i ->
            (StereoMixer.limit(pcm[i] / 32768f * gain) * 32767).roundToInt().coerceIn(-32767, 32767).toShort()
        }
        return SpeechLevel(output, input, rms(output), gain)
    }
}
