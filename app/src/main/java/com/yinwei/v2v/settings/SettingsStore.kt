package com.yinwei.v2v.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.yinwei.v2v.audio.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
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
            (p[original] ?: 0.2f).coerceIn(0f, 1f), (p[translation] ?: 1f).coerceIn(0f, 1f), p[swapped] ?: false,
        )
    }
    suspend fun save(s: MixSettings) { store.edit {
        it[intPreferencesKey("schema_version")] = 1
        it[mode] = s.mode.name; it[original] = s.originalGain
        it[translation] = s.translationGain; it[swapped] = s.swap
    } }
}
