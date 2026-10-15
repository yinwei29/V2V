package com.yinwei.v2v.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.yinwei.v2v.audio.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "settings")
class SettingsStore(context: Context) {
    private val store = context.dataStore
    private val mode = stringPreferencesKey("mode")
    private val original = floatPreferencesKey("original_gain")
    private val translation = floatPreferencesKey("translation_gain")
    private val swapped = booleanPreferencesKey("swap")
    val settings = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { p ->
        MixSettings(
            runCatching { MixMode.valueOf(p[mode].orEmpty()) }.getOrDefault(MixMode.TRANSLATION_ONLY),
            (p[original] ?: 0.2f).coerceIn(0f, 1f), (p[translation] ?: 1f).coerceIn(0f, 2f), p[swapped] ?: false,
            runCatching { TranslationChannel.valueOf(p[stringPreferencesKey("translation_channel")].orEmpty()) }.getOrDefault(TranslationChannel.BOTH),
        )
    }.distinctUntilChanged()
    val presentation = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { p ->
        PresentationSettings(p[booleanPreferencesKey("speech_enabled")] ?: true,
            (p[floatPreferencesKey("speech_rate")] ?: 1f).coerceIn(0.75f, 2f),
            (p[floatPreferencesKey("caption_size")] ?: 18f).coerceIn(14f, 30f),
            (p[floatPreferencesKey("caption_opacity")] ?: 0.55f).coerceIn(0f, 1f),
            (p[intPreferencesKey("caption_lines")] ?: 4).coerceIn(2, 6),
            p[booleanPreferencesKey("speech_auto_catch_up")] ?: true)
    }.distinctUntilChanged()
    suspend fun savePresentation(s: PresentationSettings) { store.edit {
        it[booleanPreferencesKey("speech_enabled")] = s.speechEnabled
        it[booleanPreferencesKey("speech_auto_catch_up")] = s.autoCatchUp
        it[floatPreferencesKey("speech_rate")] = s.speechRate.coerceIn(0.75f, 2f)
        it[floatPreferencesKey("caption_size")] = s.captionSize.coerceIn(14f, 30f)
        it[floatPreferencesKey("caption_opacity")] = s.backgroundOpacity.coerceIn(0f, 1f)
        it[intPreferencesKey("caption_lines")] = s.captionLines.coerceIn(2, 6)
    } }
    val diagnosticsEnabled = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[booleanPreferencesKey("diagnostics_enabled")] ?: true }.distinctUntilChanged()
    suspend fun setDiagnosticsEnabled(enabled: Boolean) { store.edit { it[booleanPreferencesKey("diagnostics_enabled")] = enabled } }
    suspend fun save(s: MixSettings) { store.edit {
        it[intPreferencesKey("schema_version")] = 1
        it[mode] = s.mode.name; it[original] = s.originalGain
        it[translation] = s.translationGain; it[stringPreferencesKey("translation_channel")] = s.translationChannel.name; it[swapped] = s.swap
    } }
}
