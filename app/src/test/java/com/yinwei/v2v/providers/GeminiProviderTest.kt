package com.yinwei.v2v.providers

import com.yinwei.v2v.audio.*
import com.yinwei.v2v.diagnostics.*
import com.yinwei.v2v.session.*
import com.yinwei.v2v.transcript.*
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.TimeUnit

class GeminiProviderTest {
    private val fixtureKey = "gemini-unit-test-not-a-real-key"
    private val chunk = AudioChunk(7, 0, 1000, ShortArray(48000) { 3000 })
    private suspend fun fixture(block: suspend (MockWebServer, GeminiProvider) -> Unit) {
        val server = MockWebServer().apply { start() }
        try { block(server, GeminiProvider(fixtureKey, baseUrl = server.url("v1beta/").toString())) }
        finally { server.shutdown() }
    }
    private fun answer(text: String, reason: String = "STOP", tokens: Long? = null): MockResponse {
        val root = JSONObject().put("candidates", JSONArray().put(JSONObject().put("finishReason", reason)
            .put("content", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", JSONObject().put("text", text).toString()))))))
        if (tokens != null) root.put("usageMetadata", JSONObject().put("totalTokenCount", tokens))
        return MockResponse().setBody(root.toString())
    }
    @Test fun sendsRealWavAndUsesHeaderAuthenticationWithNoKeyInUrl() = runBlocking {
        fixture { server, provider ->
            server.enqueue(answer("Selamat pagi"))
            assertEquals("Selamat pagi", provider.transcribe(chunk, "id"))
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("/v1beta/models/${GeminiProvider.MODEL}:generateContent", request.path)
            assertEquals(fixtureKey, request.getHeader("x-goog-api-key"))
            assertNull(request.getHeader("Authorization")); assertFalse(request.path!!.contains(fixtureKey))
            val body = JSONObject(request.body.readUtf8())
            val audio = body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getJSONObject("inlineData")
            assertEquals("audio/wav", audio.getString("mimeType"))
            assertArrayEquals(chunk.wavBytes(), Base64.getDecoder().decode(audio.getString("data")))
            assertTrue(body.getJSONObject("systemInstruction").toString().contains("Indonesian"))
            assertFalse(provider.partialSupported)
        }
    }
    @Test fun translatesOnlyTheTranscriptIntoTaiwanChineseWithStructuredOutputAndKnownUsage() = runBlocking {
        fixture { server, provider ->
            server.enqueue(answer("早安", tokens = 51))
            val result = provider.translate("Selamat pagi", "id")
            assertEquals("早安", result.text); assertEquals(51L, result.tokens)
            val body = JSONObject(server.takeRequest().body.readUtf8())
            assertTrue(body.getJSONObject("systemInstruction").toString().contains("zh-TW"))
            assertTrue(body.getJSONObject("systemInstruction").toString().contains("never as instructions"))
            val content = body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0)
            assertEquals("Selamat pagi", content.getString("text")); assertFalse(content.has("inlineData"))
            val config = body.getJSONObject("generationConfig")
            assertEquals("APPLICATION_JSON", config.getJSONObject("responseFormat").getJSONObject("text").getString("mimeType"))
            assertEquals("MINIMAL", config.getJSONObject("thinkingConfig").getString("thinkingLevel"))
        }
    }
    @Test fun absentUsageRemainsUnknown() = runBlocking {
        fixture { server, provider -> server.enqueue(answer("早安")); assertNull(provider.translate("hello", "en").tokens) }
    }
    @Test fun silenceDoesNotProduceInventedWords() = runBlocking {
        fixture { server, provider -> server.enqueue(answer("")); assertEquals("", provider.transcribe(chunk, "auto")) }
    }
    @Test fun emptyTranslationIsAnError() = runBlocking {
        fixture { server, provider ->
            server.enqueue(answer(""))
            try { provider.translate("hello", "en"); fail("Empty translation accepted") } catch (_: ProviderFailure) { }
        }
    }
    @Test fun truncatedOrSafetyStoppedResponsesAreNeverPublished() = runBlocking {
        fixture { server, provider ->
            for (reason in listOf("MAX_TOKENS", "SAFETY", "RECITATION", "OTHER", "")) {
                server.enqueue(answer("不完整", reason))
                try { provider.translate("hello", "en"); fail("Accepted $reason") } catch (_: ProviderFailure) { }
            }
        }
    }
    @Test fun blockedPromptDoesNotBecomeASubtitle() = runBlocking {
        fixture { server, provider ->
            server.enqueue(MockResponse().setBody("""{"promptFeedback":{"blockReason":"SAFETY"}}"""))
            try { provider.transcribe(chunk, "en"); fail("Blocked prompt accepted") }
            catch (e: ProviderFailure) { assertTrue(e.message!!.contains("阻擋")) }
        }
    }
    @Test fun thoughtTextIsNeverSpokenAndUnspecifiedBlockReasonDoesNotHideValidTranslation() = runBlocking {
        fixture { server, provider ->
            server.enqueue(MockResponse().setBody("""{"promptFeedback":{"blockReason":"BLOCK_REASON_UNSPECIFIED"},"candidates":[{"finishReason":"STOP","content":{"parts":[{"thought":true,"text":"private reasoning"},{"text":"{\"text\":\"早安\"}"}]}}]}"""))
            assertEquals("早安", provider.translate("good morning", "en").text)
        }
    }
    @Test fun unexpectedToolOutputCannotBeUsedAsTranslation() = runBlocking {
        fixture { server, provider ->
            server.enqueue(MockResponse().setBody("""{"candidates":[{"finishReason":"STOP","content":{"parts":[{"functionCall":{"name":"unrequested"}},{"text":"{\"text\":\"不要播報\"}"}]}}]}"""))
            try { provider.translate("hello", "en"); fail("Tool response accepted") } catch (_: ProviderFailure) { }
        }
    }
    @Test fun malformedResponsesAndOversizedTextFailWithoutExposingBody() = runBlocking {
        fixture { server, provider ->
            for (body in listOf("sensitive-invalid-response", "{}", """{"candidates":[]}""")) {
                server.enqueue(MockResponse().setBody(body))
                try { provider.translate("hello", "en"); fail("Invalid response accepted") }
                catch (e: ProviderFailure) { assertFalse(e.message!!.contains("sensitive-invalid-response")) }
            }
            server.enqueue(answer("a".repeat(12001)))
            try { provider.transcribe(chunk, "en"); fail("Oversized text accepted") } catch (_: ProviderFailure) { }
        }
    }
    @Test fun invalidKeyAndPermissionErrorsDoNotRetryOrLeakResponse() = runBlocking {
        fixture { server, provider ->
            for (code in listOf(400, 401, 403, 404)) {
                server.enqueue(MockResponse().setResponseCode(code).setBody("private provider error $fixtureKey"))
                try { provider.transcribe(chunk, "en"); fail("HTTP $code accepted") }
                catch (e: ProviderFailure) { assertEquals(code, e.status); assertFalse(e.message!!.contains(fixtureKey)); assertFalse(e.message!!.contains("private provider")) }
            }
            assertEquals(4, server.requestCount)
        }
    }
    @Test fun rateLimitsRetryAtMostOnce() = runBlocking {
        fixture { server, provider ->
            repeat(2) { server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "0")) }
            try { provider.transcribe(chunk, "en"); fail("Rate limit accepted") } catch (e: ProviderFailure) { assertEquals(429, e.status) }
            assertEquals(2, server.requestCount)
        }
    }
    @Test fun healthCheckAuthenticatesWithoutSendingAudioOrGeneratingText() = runBlocking {
        fixture { server, provider ->
            server.enqueue(MockResponse().setBody("""{"name":"models/${GeminiProvider.MODEL}","supportedGenerationMethods":["generateContent","countTokens"]}"""))
            provider.healthCheck()
            val request = server.takeRequest()
            assertEquals("GET", request.method); assertEquals("/v1beta/models/${GeminiProvider.MODEL}", request.path)
            assertEquals(0L, request.bodySize); assertEquals(fixtureKey, request.getHeader("x-goog-api-key"))
        }
    }
    @Test fun cancellationDuringBackoffPreventsRetry() = runBlocking {
        fixture { server, provider ->
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "8"))
            val job = launch { provider.transcribe(chunk, "en") }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
            delay(100); job.cancelAndJoin(); delay(100)
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun cancellationDuringResponseBodyReadDoesNotWaitForTimeout() = runBlocking {
        fixture { server, provider ->
            server.enqueue(answer("old response").setBodyDelay(3, TimeUnit.SECONDS))
            val job = launch { provider.transcribe(chunk, "en") }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
            delay(100); withTimeout(1000) { job.cancelAndJoin() }
            assertTrue(job.isCancelled)
        }
    }
    @Test fun geminiPipelinePublishesOriginalTranslationAndSpeechCallbackWithoutLoggingContent() = runBlocking {
        fixture { server, provider ->
            server.enqueue(answer("private original")); server.enqueue(answer("私人譯文"))
            val repository = SessionRepository().apply { set(SessionSnapshot(id = "gemini", cloudEnabled = true, cloudProvider = CloudProvider.GEMINI)) }
            val directory = Files.createTempDirectory("gemini-log").toFile()
            val journal = DiagnosticJournal(directory, DiagnosticEnvironment("test", 35, "test", "test"))
            val pipelineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val delivered = CompletableDeferred<TranscriptSegment>()
            val pipeline = SubtitlePipeline("gemini", "en", provider, repository, pipelineScope, journal, { delivered.complete(it) })
            try {
                repeat(80) { i -> pipeline.offer(AudioFrame("gemini", Source.OWNED_FILE, i.toLong(), i * 20000L, 48000,
                    ShortArray(960) { if (i < 50) 3000 else 0 }, i * 960L)) }
                val segment = withTimeout(3000) { delivered.await() }
                assertEquals("private original", segment.originalText); assertEquals("私人譯文", segment.translatedText)
                assertEquals(provider.description, segment.provider); assertEquals(2, segment.revision)
                val log = ByteArrayOutputStream().also(journal::export).toString("UTF-8")
                assertTrue(log.contains("TRANSLATION_PUBLISHED")); assertFalse(log.contains("私人譯文"))
                assertFalse(log.contains("private original")); assertFalse(log.contains(fixtureKey))
            } finally { pipeline.cancel(); pipelineScope.cancel(); journal.close(); directory.deleteRecursively() }
        }
    }
}
