package com.yinwei.v2v.audio

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.tanh

/** Single-thread DSP. Inputs already share rate/format. 50ms ramp on the full routing matrix. */
class StereoMixer(private val sampleRate: Int = 48000, rampMs: Int = 50) {
    private val rampFrames = (sampleRate * rampMs / 1000).coerceAtLeast(1)
    private val current = FloatArray(4)
    private var target = FloatArray(4)
    private var step = FloatArray(4)
    private var remaining = 0
    private var settings: MixSettings? = null

    fun render(original: ShortArray, translation: ShortArray, config: MixSettings): ShortArray {
        require(original.size == translation.size)
        require(config.originalGain in 0f..1f && config.translationGain in 0f..1f)
        if (config != settings) {
            val o = config.originalGain
            val t = config.translationGain
            target = when (config.mode) {
                MixMode.TRANSLATION_ONLY -> floatArrayOf(0f, t, 0f, t)
                MixMode.MIX -> floatArrayOf(o, t, o, t)
                MixMode.SPLIT -> if (config.swap) floatArrayOf(o, 0f, 0f, t) else floatArrayOf(0f, t, o, 0f)
            }
            step = FloatArray(4) { (target[it] - current[it]) / rampFrames }
            remaining = rampFrames
            settings = config
        }
        return ShortArray(original.size * 2).also { out ->
            original.indices.forEach { i ->
                if (remaining > 0) {
                    for (c in 0..3) current[c] += step[c]
                    remaining--
                    if (remaining == 0) target.copyInto(current)
                }
                val o = original[i] / 32768f
                val t = translation[i] / 32768f
                out[i * 2] = pcm(limit(o * current[0] + t * current[1]))
                out[i * 2 + 1] = pcm(limit(o * current[2] + t * current[3]))
            }
        }
    }

    companion object {
        // Continuous soft knee: identity up to 0.8, asymptotic ceiling 1. No hard clipping.
        fun limit(x: Float): Float {
            val a = abs(x)
            return if (a <= 0.8f) x else {
                val limited = 0.8f + 0.2f * tanh((a - 0.8f) / 0.2f)
                if (x < 0) -limited else limited
            }
        }
        private fun pcm(x: Float) = (x * 32767).roundToInt().toShort()
        /** Equal-weight stereo downmix with 3dB headroom. Opposite-phase content can cancel. */
        fun downmix(left: Short, right: Short): Short = ((left.toInt() + right.toInt()) * 0.3535534f).roundToInt().toShort()
    }
}

/** Bounded metrics sink for the future ASR branch; does not claim recognition. */
class AsrProbe(private val maxFrames: Int = 240000) {
    var samples: Long = 0; private set
    var lastRms: Float = 0f; private set
    var bufferedFrames: Int = 0; private set
    var droppedFrames: Long = 0; private set
    fun consume(frame: AudioFrame) {
        samples += frame.pcm.size
        lastRms = frame.rms()
        // M1 consumes synchronously; no PCM retained, no unbounded backlog.
        if (frame.pcm.size > maxFrames) droppedFrames += frame.pcm.size - maxFrames
        bufferedFrames = 0
    }
}
