package com.yinwei.v2v.languages

/** Fixed catalog: language names/codes used in prompts and API hints cannot contain user instructions. */
enum class AppLanguage(val code: String, val label: String, val englishName: String, val asrCode: String, val voiceTag: String) {
    ZH_TW("zh-TW", "繁體中文", "Traditional Chinese used in Taiwan", "zh", "zh-TW"),
    ZH_CN("zh-CN", "简体中文", "Simplified Chinese", "zh", "zh-CN"),
    EN("en", "English · 英文", "English", "en", "en-US"),
    ID("id", "Bahasa Indonesia · 印尼文", "Indonesian", "id", "id-ID"),
    JA("ja", "日本語 · 日文", "Japanese", "ja", "ja-JP"),
    KO("ko", "한국어 · 韓文", "Korean", "ko", "ko-KR"),
    VI("vi", "Tiếng Việt · 越南文", "Vietnamese", "vi", "vi-VN"),
    TH("th", "ไทย · 泰文", "Thai", "th", "th-TH"),
    MS("ms", "Bahasa Melayu · 馬來文", "Malay", "ms", "ms-MY"),
    ES("es", "Español · 西班牙文", "Spanish", "es", "es-ES"),
    FR("fr", "Français · 法文", "French", "fr", "fr-FR"),
    DE("de", "Deutsch · 德文", "German", "de", "de-DE"),
    PT("pt", "Português · 葡萄牙文", "Portuguese", "pt", "pt-BR"),
    HI("hi", "हिन्दी · 印地文", "Hindi", "hi", "hi-IN"),
    AR("ar", "العربية · 阿拉伯文", "Arabic", "ar", "ar-SA");
    companion object {
        fun fromCode(code: String): AppLanguage? = entries.firstOrNull { it.code == code }
        fun validSource(code: String) = code == "auto" || fromCode(code) != null
        fun sourceName(code: String): String { require(validSource(code)); return fromCode(code)?.englishName ?: "automatically detected original language" }
        fun label(code: String) = if (code == "auto") "自動偵測" else fromCode(code)?.label ?: code
    }
}
data class LanguagePair(val source: String = "en", val target: String = "zh-TW") {
    init { require(AppLanguage.validSource(source)); require(AppLanguage.fromCode(target) != null) }
}
