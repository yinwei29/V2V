package com.yinwei.v2v.providers

enum class GeminiModel(val apiId: String, val label: String, val thinkingLevel: String) {
    FLASH_LITE("gemini-3.5-flash-lite", "Flash-Lite · 快速", "MINIMAL"),
    FLASH("gemini-3.8-flash", "Flash · 一般", "LOW");
    companion object {
        val DEFAULT = FLASH_LITE
        fun parse(value: String?): GeminiModel? = entries.firstOrNull { it.name == value }
    }
}
