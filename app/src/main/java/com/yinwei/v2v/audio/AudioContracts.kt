package com.yinwei.v2v.audio

import kotlin.math.sqrt

enum class Source { TEST_FILE, SYSTEM_PLAYBACK }
enum class SessionState { IDLE, REQUESTING_PERMISSION, STARTING, RUNNING, NO_SIGNAL, PAUSED, STOPPING, ERROR, STOPPED }
enum class SourceStatus { UNKNOWN, AVAILABLE, NO_SIGNAL, UNSUPPORTED, BLOCKED_SOURCE_INTEGRATION, ERROR }
enum class MixMode { SPLIT, TRANSLATION_ONLY, MIX }
enum class UiCapability { FULL_MIXER_CONTROL, TRANSLATION_OUTPUT_ONLY, CAPTURE_ONLY, UNSUPPORTED }
enum class OriginalGainControlCapability { APP_OWNED, PROVIDER_CONTROLLED, PLATFORM_VERIFIED, UNAVAILABLE }

data class SourceCapabilities(
    val captureAvailable: Boolean = false,
    val stereoOutputSupported: Boolean = false,
    val verificationEvidence: String = "NOT_TESTED_ON_DEVICE",
    val inputVerified: Boolean = false,
    val originalControl: OriginalGainControlCapability = OriginalGainControlCapability.UNAVAILABLE,
    val externalDirectOutputPresent: Boolean = true,
    val reason: String = "尚未驗證來源；啟動擷取不代表取得有效音訊",
) {
    val originalOutputControlled: Boolean get() = originalControl != OriginalGainControlCapability.UNAVAILABLE
    val ui: UiCapability get() = if (originalControl == OriginalGainControlCapability.APP_OWNED && !externalDirectOutputPresent)
        UiCapability.FULL_MIXER_CONTROL else UiCapability.CAPTURE_ONLY
}

/** PCM16 mono, immutable by convention. Producer allocates; consumers must not retain beyond consume(). */
data class AudioFrame(
    val sessionId: String, val sourceId: Source, val sequence: Long,
    val timestampUs: Long, val sampleRate: Int, val pcm: ShortArray,
) {
    val channelCount = 1
    val encoding = "PCM16"
    fun rms(): Float = if (pcm.isEmpty()) 0f else
        sqrt(pcm.sumOf { val f = it.toDouble() / 32768; f * f } / pcm.size).toFloat()
}

data class MixSettings(
    val mode: MixMode = MixMode.TRANSLATION_ONLY,
    val originalGain: Float = 0.2f,
    val translationGain: Float = 1f,
    val swap: Boolean = false,
)
