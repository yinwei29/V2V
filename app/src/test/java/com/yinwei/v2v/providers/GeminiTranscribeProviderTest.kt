package com.yinwei.v2v.providers

import com.yinwei.v2v.transcript.*
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
import java.util.concurrent.TimeUnit

class GeminiTranscribeProviderTest {
    private val chunk = AudioChunk(0, 0, 1000, ShortArray(48000) { 3000 })
    private suspend fun fixture(block: suspend (MockWebServer, GeminiTranscribeProvider) -> Unit) {
        val server = MockWebServer().apply { start() }
        try { block(server, GeminiTranscribeProvider("not-a-real-key", baseUrl = server.url("v1beta/").toString())) }
        finally { server.shutdown() }
    }
    private fun transcript(text: String, tokens: Long? = null) = JSONObject()
        .put("status", "completed").put("steps", JSONArray().put(JSONObject().put("type", "model_output")
            .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", text)))))
        .apply { tokens?.let { put("usage", JSONObject().put("total_tokens", it)) } }.toString()
    private fun translation(tokens: Long? = null): String = JSONObject().put("candidates", JSONArray().put(JSONObject()
        .put("finishReason", "STOP").put("content", JSONObject().put("parts", JSONArray().put(JSONObject()
            .put("text", JSONObject().put("text", "不要關閉12號閥門。").toString()))))))
        .apply { tokens?.let { put("usageMetadata", JSONObject().put("totalTokenCount", it)) } }.toString()
    @Test fun dedicatedRequestUsesExactAudioVerbatimConfigHeaderAuthAndNoStoredInteraction() = runBlocking {
        fixture { server, provider ->
            server.enqueue(MockResponse().setBody(transcript("Jangan tutup katup 12.")))
            assertEquals("Jangan tutup katup 12.", provider.transcribe(chunk, "id"))
            val request = server.takeRequest()
            assertEquals("/v1beta/interactions", request.path); assertEquals("not-a-real-key", request.getHeader("x-goog-api-key"))
            val body = JSONObject(request.body.readUtf8())
            assertEquals("gemini-3.5-transcribe", body.getString("model"))
            assertFalse(body.getBoolean("store")); assertFalse(body.getBoolean("stream")); assertFalse(body.getBoolean("background"))
            val audio = body.getJSONArray("input").getJSONObject(0)
            assertEquals("audio", audio.getString("type")); assertEquals("audio/wav", audio.getString("mime_type"))
            assertArrayEquals(chunk.wavBytes(), Base64.getDecoder().decode(audio.getString("data")))
            val config = body.getJSONObject("generation_config").getJSONObject("transcription_config")
            assertEquals("verbatim", config.getJSONObject("mode").getString("type"))
            assertEquals("id-ID", config.getJSONArray("language_codes").getString(0))
            assertFalse(body.has("systemInstruction")); assertFalse(body.has("previous_interaction_id"))
        }
    }
    @Test fun autoLanguageOmitsHintsAndCatalogUsesDocumentedSpeechLocales() = runBlocking {
        fixture { server, provider ->
            server.enqueue(MockResponse().setBody(transcript("")))
            assertEquals("", provider.transcribe(chunk, "auto"))
            val body = JSONObject(server.takeRequest().body.readUtf8())
            assertFalse(body.getJSONObject("generation_config").getJSONObject("transcription_config").has("language_codes"))
            assertEquals("cmn-Hans-CN", GeminiTranscribeProvider.languageHint("zh-TW"))
            assertEquals("cmn-Hans-CN", GeminiTranscribeProvider.languageHint("zh-CN"))
            assertEquals("es-419", GeminiTranscribeProvider.languageHint("es"))
            assertEquals("ar-EG", GeminiTranscribeProvider.languageHint("ar"))
            assertEquals("en-US", GeminiTranscribeProvider.languageHint("en"))
        }
    }
    @Test fun healthChecksBothModelsWithoutSendingAudio() = runBlocking {
        fixture { server, provider ->
            server.enqueue(MockResponse().setBody("""{"name":"models/gemini-3.5-transcribe"}"""))
            server.enqueue(MockResponse().setBody("""{"name":"models/gemini-3.8-flash","supportedGenerationMethods":["generateContent"]}"""))
            provider.healthCheck()
            assertEquals("/v1beta/models/gemini-3.5-transcribe", server.takeRequest().path)
            assertEquals("/v1beta/models/gemini-3.8-flash", server.takeRequest().path)
            assertFalse((provider as TranslationProvider) is AudioTranslationProvider); assertFalse(provider.partialSupported)
        }
    }
    @Test fun sourceAndTextTranslationAreSeparateRequestsWithBoundedContextAndSummedKnownUsage() = runBlocking {
        fixture { server, provider ->
            server.enqueue(MockResponse().setBody(transcript("Don't close valve 12.", 40)))
            server.enqueue(MockResponse().setBody(translation(25)))
            val original = provider.transcribe(chunk, "en")
            val result = provider.translateInContext(original, "en", "zh-TW", TranslationContext.from(listOf("The valve is open.")))
            assertEquals("不要關閉12號閥門。", result.text); assertEquals(65L, result.tokens)
            assertEquals("/v1beta/interactions", server.takeRequest().path)
            val mt = server.takeRequest(); assertEquals("/v1beta/models/gemini-3.8-flash:generateContent", mt.path)
            val body = JSONObject(mt.body.readUtf8()); val content = body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
            assertFalse(content.getJSONObject(0).has("inlineData"))
            val data = JSONObject(content.getJSONObject(0).getString("text"))
            assertEquals(original, data.getString("current_transcript"))
            assertEquals("The valve is open.", data.getJSONArray("preceding_source_context").getString(0))
            assertTrue(body.getJSONObject("systemInstruction").toString().contains("Preserve numbers and negation"))
        }
    }
    @Test fun missingUsageIsUnknownAndNoOldAsrTokensLeakIntoAnotherTranslation() = runBlocking {
        fixture { server, provider ->
            server.enqueue(MockResponse().setBody(transcript("Don't close valve 12.", 40)))
            server.enqueue(MockResponse().setBody(translation()))
            provider.transcribe(chunk, "en"); assertNull(provider.translate("Don't close valve 12.", "en").tokens)
            server.enqueue(MockResponse().setBody(translation(25)))
            assertNull(provider.translate("Don't close valve 12.", "en").tokens)
        }
    }
    @Test fun incompleteToolAndMalformedResponsesNeverBecomeTranscriptsOrExposeBodies() = runBlocking {
        fixture { server, provider ->
            val bad = listOf("private-response-not-a-real-key", "{}", transcript("private").replace("completed", "incomplete"),
                transcript("private").replace("model_output", "tool_call"), transcript("private").replace("\"type\":\"text\"", "\"type\":\"thought\""),
                transcript("private").replace("\"private\"", "12"), transcript("a".repeat(12001)))
            for (body in bad) {
                server.enqueue(MockResponse().setBody(body))
                try { provider.transcribe(chunk, "en"); fail("Bad response accepted") }
                catch (e: ProviderFailure) { assertFalse(e.message!!.contains("private")); assertFalse(e.message!!.contains("not-a-real-key")) }
            }
        }
    }
    @Test fun cancellationWhileReadingTranscriptionClosesRequest() = runBlocking {
        fixture { server, provider ->
            server.enqueue(MockResponse().setBody(transcript("late")).setBodyDelay(3, TimeUnit.SECONDS))
            val job = launch { provider.transcribe(chunk, "en") }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
            delay(100); withTimeout(1000) { job.cancelAndJoin() }; assertTrue(job.isCancelled)
        }
    }
    @Test fun factoryKeepsExistingSelectionsAndMakesSpecialistModeAnExplicitChoice() {
        assertTrue(CloudProvider.GEMINI.create("not-real", geminiModel = GeminiModel.FLASH) is GeminiProvider)
        assertTrue(CloudProvider.GEMINI.create("not-real", geminiModel = GeminiModel.FLASH_LITE) is GeminiProvider)
        assertTrue(CloudProvider.GEMINI.create("not-real", geminiModel = GeminiModel.TRANSCRIBE_FLASH) is GeminiTranscribeProvider)
        assertTrue(CloudProvider.GEMINI.create("not-real", geminiModel = GeminiModel.TRANSCRIBE_FLASH_LITE) is GeminiTranscribeProvider)
        assertEquals(GeminiModel.FLASH_LITE, GeminiModel.DEFAULT)
    }
}
