package com.yinwei.v2v.speech

import com.yinwei.v2v.languages.AppLanguage
import java.util.Locale

object OfflineVoicePolicy {
    /** Exact region first; allow same-language regional voices except crossing Chinese scripts. */
    fun score(targetCode: String, voiceTag: String, networkRequired: Boolean): Int? {
        if (networkRequired) return null
        val target = AppLanguage.fromCode(targetCode) ?: return null
        val wanted = Locale.forLanguageTag(target.voiceTag)
        val voice = Locale.forLanguageTag(voiceTag)
        val language = if (voice.language == "in") "id" else voice.language
        if (language != wanted.language) return null
        if (target == AppLanguage.ZH_TW && voice.country != "TW" && voice.script != "Hant") return null
        if (target == AppLanguage.ZH_CN && voice.country != "CN" && voice.script != "Hans") return null
        return if (voice.country == wanted.country) 0 else 1
    }
}
