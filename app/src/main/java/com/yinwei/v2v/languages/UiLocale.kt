package com.yinwei.v2v.languages

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList

/** UI locale is independent of the audio source/target and survives process restarts. */
object UiLocale {
    val choices = linkedMapOf("system" to "System", "zh-TW" to "繁體中文", "en" to "English", "id" to "Bahasa Indonesia", "ja" to "日本語")
    fun selected(context: Context): String = if (Build.VERSION.SDK_INT >= 33) {
        context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags().ifBlank { "system" }
    } else context.getSharedPreferences("ui-locale", 0).getString("language", "system") ?: "system"
    fun set(context: Context, code: String) {
        require(code in choices)
        if (Build.VERSION.SDK_INT >= 33) context.getSystemService(LocaleManager::class.java).applicationLocales =
            if (code == "system") LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(code)
        else context.getSharedPreferences("ui-locale", 0).edit().putString("language", code).apply()
    }
    fun wrap(context: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return context
        val code = selected(context)
        if (code == "system") return context
        return context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(code)) })
    }
}
/** Stable resource keys let shared Compose, activity and overlay labels use the same catalog. */
fun Context.uiText(source: String): String {
    val key = "ui_" + Integer.toUnsignedString(source.hashCode(), 16)
    val id = resources.getIdentifier(key, "string", packageName)
    return if (id == 0) source else getString(id)
}

fun Context.languageLabel(code: String): String = if (code == "auto") uiText("自動偵測") else AppLanguage.fromCode(code)?.label?.substringBefore(" · ") ?: code
