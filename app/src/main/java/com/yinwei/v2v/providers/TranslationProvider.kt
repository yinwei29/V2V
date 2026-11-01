package com.yinwei.v2v.providers

import com.yinwei.v2v.diagnostics.DiagnosticJournal
import com.yinwei.v2v.transcript.AudioChunk

enum class CloudProvider(val label: String) {
    OPENAI("OpenAI"), GEMINI("Google Gemini");
    companion object { fun parse(value: String?): CloudProvider? = entries.firstOrNull { it.name == value } }
}
interface TranslationProvider {
    val description: String
    val partialSupported: Boolean
    suspend fun healthCheck()
    suspend fun transcribe(chunk: AudioChunk, language: String): String
    suspend fun translateInContext(text: String, language: String, targetLanguage: String, context: TranslationContext): TranslatedText =
        translate(text, language, targetLanguage)
    suspend fun translate(text: String, language: String, targetLanguage: String = "zh-TW"): TranslatedText
}
fun CloudProvider.create(key: String, journal: DiagnosticJournal? = null, geminiModel: GeminiModel = GeminiModel.DEFAULT): TranslationProvider = when (this) {
    CloudProvider.OPENAI -> OpenAiProvider(key, diagnostics = journal)
    CloudProvider.GEMINI -> GeminiProvider(key, diagnostics = journal, model = geminiModel)
}

/** Providers that can return verbatim source and translation from one audio request. */
data class AudioTranslation(val original: String, val translation: TranslatedText)
interface AudioTranslationProvider {
    suspend fun translateAudioInContext(chunk: AudioChunk, language: String, targetLanguage: String, context: TranslationContext): AudioTranslation =
        translateAudio(chunk, language, targetLanguage)
    suspend fun translateAudio(chunk: AudioChunk, language: String, targetLanguage: String): AudioTranslation
}

/** Only preceding source text, bounded at the provider boundary; never contains instructions or keys. */
class TranslationContext private constructor(val precedingOriginals: List<String>) {
    companion object {
        val EMPTY = TranslationContext(emptyList())
        fun from(originals: List<String>) = TranslationContext(originals.takeLast(2)
            .filter { it.isNotBlank() && it.length <= 1200 }.toList())
    }
}
