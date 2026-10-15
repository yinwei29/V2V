package com.yinwei.v2v.transcript

import com.yinwei.v2v.providers.*
import com.yinwei.v2v.session.*
import com.yinwei.v2v.audio.*
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Only local HTTP fixtures are used. No account key or live API is contacted. */
class ProviderTest {
    private val fixtureKey = "unit-test-not-a-real-api-key"
    private val chunk = AudioChunk(0, 0, 1000, ShortArray(48000) { 3000 })
    private suspend fun fixture(block: suspend (MockWebServer, OpenAiProvider) -> Unit) {
        val server = MockWebServer().apply { start() }
        try { block(server, OpenAiProvider(fixtureKey, baseUrl = server.url("v1/").toString())) }
        finally { server.shutdown() }
    }
    @Test fun sendsActualWavWithIndonesianHintAndRequestsTaiwanTraditionalChinese() = runBlocking {
        fixture { server, provider ->
            server.enqueue(MockResponse().setBody("{\"text\":\"Selamat pagi\"}"))
            assertEquals("Selamat pagi", provider.transcribe(chunk, "id"))
            val asr = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("/v1/audio/transcriptions", asr.path)
            val body = asr.body.readUtf8()
            assertTrue(body.contains("RIFF")); assertTrue(body.contains("whisper-1")); assertTrue(body.contains("\r\nid\r\n"))
            server.enqueue(MockResponse().setBody("""{"choices":[{"finish_reason":"stop","message":{"content":"早安"}}],"usage":{"total_tokens":37}}"""))
            val result = provider.translate("Selamat pagi", "id")
            assertEquals("早安", result.text); assertEquals(37L, result.tokens)
            val mt = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("/v1/chat/completions", mt.path)
            val json = mt.body.readUtf8()
            assertTrue(json.contains("zh-TW")); assertTrue(json.contains("gpt-4o-mini")); assertTrue(json.contains("Selamat pagi"))
            assertFalse(provider.partialSupported)
        }
    }
    @Test fun absentUsageIsUnknownRatherThanPretendingZeroBilling() = runBlocking {
        fixture { server, provider ->
            server.enqueue(MockResponse().setBody("""{"choices":[{"finish_reason":"stop","message":{"content":"早安"}}]}"""))
            val result = provider.translate("Good morning", "en")
            assertEquals("早安", result.text); assertNull(result.tokens)
        }
    }
    @Test fun unauthorizedNeverRetriesOrExposesSensitiveErrorBody() = runBlocking {
        fixture { server, provider ->
            server.enqueue(MockResponse().setResponseCode(401).setBody("sensitive-provider-message"))
            try { provider.transcribe(chunk, "en"); fail("401 accepted") }
            catch (e: ProviderFailure) { assertEquals(401, e.status); assertFalse(e.message!!.contains("sensitive-provider-message")); assertFalse(e.message!!.contains(fixtureKey)) }
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun rateLimitRetriesOnlyOnceAndThenReportsQuota() = runBlocking {
        fixture { server, provider ->
            repeat(2) { server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "0")) }
            try { provider.transcribe(chunk, "en"); fail("429 accepted") }
            catch (e: ProviderFailure) { assertEquals(429, e.status) }
            assertEquals(2, server.requestCount)
        }
    }
    @Test fun cancellationDuringBackoffPreventsOldSessionRetry() = runBlocking {
        fixture { server, provider ->
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "8"))
            val job = launch { provider.transcribe(chunk, "en") }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
            delay(100); job.cancelAndJoin(); delay(100)
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun cancellationWhileReadingBodyClosesCallWithoutWaitingForReadTimeout() = runBlocking {
        fixture { server, provider ->
            server.enqueue(MockResponse().setBody("{\"text\":\"late\"}").setBodyDelay(3, TimeUnit.SECONDS))
            val job = launch { provider.transcribe(chunk, "en") }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
            delay(100)
            withTimeout(1000) { job.cancelAndJoin() }
            assertTrue(job.isCancelled)
        }
    }
    @Test fun busyProviderDropsAreCountedAndDoNotGrowAnUnboundedQueue() = runBlocking {
        fixture { server, provider ->
            server.enqueue(MockResponse().setBody("{\"text\":\"late\"}").setBodyDelay(3, TimeUnit.SECONDS))
            val repository = SessionRepository().apply { set(SessionSnapshot(id = "busy")) }
            val pipelineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val pipeline = SubtitlePipeline("busy", "en", provider, repository, pipelineScope)
            fun sendSentence(start: Int) {
                repeat(80) { i -> pipeline.offer(AudioFrame("busy", Source.OWNED_FILE, (start + i).toLong(), (start + i) * 20000L,
                    48000, ShortArray(960) { if (i < 50) 3000 else 0 }, (start + i) * 960L)) }
            }
            try {
                delay(100); sendSentence(0)
                withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
                sendSentence(80)
                withTimeout(2000) { while (repository.state.value.skippedSamples == 0L) delay(10) }
                assertTrue(repository.state.value.skippedSamples >= 48000)
            } finally { pipeline.cancel(); pipelineScope.cancel() }
        }
    }
    @Test fun truncatedTranslationNeverBecomesSuccessfulSubtitle() = runBlocking {
        fixture { server, provider ->
            server.enqueue(MockResponse().setBody("""{"choices":[{"finish_reason":"length","message":{"content":"incomplete"}}]}"""))
            try { provider.translate("original", "en"); fail("Truncated text accepted") }
            catch (e: ProviderFailure) { assertTrue(e.message!!.contains("未完整")) }
        }
    }
    @Test fun cancelledPipelineCannotPublishLateResponseToNewSession() = runBlocking {
        fixture { server, provider ->
            server.enqueue(MockResponse().setBody("{\"text\":\"old audio\"}").setBodyDelay(2, TimeUnit.SECONDS))
            val repository = SessionRepository().apply { set(SessionSnapshot(id = "old")) }
            val pipelineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val pipeline = SubtitlePipeline("old", "en", provider, repository, pipelineScope)
            try {
                delay(100)
                repeat(80) { i -> pipeline.offer(AudioFrame("old", Source.OWNED_FILE, i.toLong(), i * 20000L, 48000,
                    ShortArray(960) { if (i < 50) 3000 else 0 }, i * 960L)) }
                withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(3, TimeUnit.SECONDS)) }
                pipeline.cancel(); repository.set(SessionSnapshot(id = "new"))
                delay(200)
                assertEquals("new", repository.state.value.id)
                assertTrue(repository.state.value.segments.isEmpty())
            } finally { pipeline.cancel(); pipelineScope.cancel() }
        }
    }
}
