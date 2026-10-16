package com.yinwei.v2v.transcript

import com.yinwei.v2v.audio.*
import com.yinwei.v2v.providers.*
import com.yinwei.v2v.session.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class CombinedPipelineTest {
    @Test fun combinedProviderPublishesBothTextsAndSpeechWithoutSeparateCalls() = runBlocking {
        var calls = 0
        val provider = object : TranslationProvider, AudioTranslationProvider {
            override val description = "combined fixture"
            override val partialSupported = false
            override suspend fun healthCheck() = Unit
            override suspend fun transcribe(chunk: AudioChunk, language: String): String = error("Unexpected ASR call")
            override suspend fun translate(text: String, language: String, targetLanguage: String): TranslatedText = error("Unexpected MT call")
            override suspend fun translateAudio(chunk: AudioChunk, language: String, targetLanguage: String): AudioTranslation {
                calls++
                assertEquals("en", language); assertEquals("id", targetLanguage)
                return AudioTranslation("A complete sentence.", TranslatedText("Sebuah kalimat lengkap.", 50))
            }
        }
        val repository = SessionRepository().apply { set(SessionSnapshot(id = "test", cloudEnabled = true)) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val spoken = mutableListOf<TranscriptSegment>()
        val pipeline = SubtitlePipeline("test", "en", provider, repository, scope, translated = { spoken += it }, targetLanguage = "id")
        try {
            repeat(200) { i -> pipeline.offer(AudioFrame("test", Source.OWNED_FILE, i.toLong(), i * 20000L, 48000,
                ShortArray(960) { if (i < 170) 3000 else 0 }, i * 960L)) }
            withTimeout(3000) { pipeline.finish() }
            assertEquals(1, calls)
            val final = repository.state.value.segments.single()
            assertEquals("A complete sentence.", final.originalText)
            assertEquals("Sebuah kalimat lengkap.", final.translatedText)
            assertEquals(2, final.revision); assertEquals("id", final.targetLanguage)
            assertEquals(listOf(final), spoken)
            assertEquals(0L, repository.state.value.skippedSamples)
            assertEquals(50L, repository.state.value.providerTokens)
        } finally { pipeline.cancel(); scope.cancel() }
    }
}
