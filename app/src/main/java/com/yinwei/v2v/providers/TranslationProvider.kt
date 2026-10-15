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
    suspend fun translate(text: String, language: String): TranslatedText
}
fun CloudProvider.create(key: String, journal: DiagnosticJournal? = null): TranslationProvider = when (this) {
    CloudProvider.OPENAI -> OpenAiProvider(key, diagnostics = journal)
    CloudProvider.GEMINI -> GeminiProvider(key, diagnostics = journal)
}
