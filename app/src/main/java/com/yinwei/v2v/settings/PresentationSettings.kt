package com.yinwei.v2v.settings

data class PresentationSettings(
    val speechEnabled: Boolean = true,
    val speechRate: Float = 1f,
    val captionSize: Float = 18f,
    val backgroundOpacity: Float = 0.55f,
    val captionLines: Int = 4,
    val autoCatchUp: Boolean = true,
    val saveHistory: Boolean = true,
    val enhanceSpeech: Boolean = true,
)
