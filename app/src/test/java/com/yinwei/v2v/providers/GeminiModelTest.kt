package com.yinwei.v2v.providers

import com.yinwei.v2v.transcript.AudioChunk
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class GeminiModelTest {
    @Test fun selectedModelControlsEndpointThinkingAndHealthCheck() = runBlocking {
        val server = MockWebServer().apply { start() }
        try {
            for (model in GeminiModel.entries) {
                val provider = GeminiProvider("not-real", baseUrl = server.url("v1beta/").toString(), model = model)
                server.enqueue(MockResponse().setBody("""{"name":"models/${model.apiId}","supportedGenerationMethods":["generateContent"]}"""))
                provider.healthCheck(); assertEquals("/v1beta/models/${model.apiId}", server.takeRequest().path)
                server.enqueue(MockResponse().setBody("""{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"{\"text\":\"早安\"}"}]}}]}"""))
                provider.translate("hello", "en")
                val request = server.takeRequest()
                assertEquals("/v1beta/models/${model.apiId}:generateContent", request.path)
                assertEquals(model.thinkingLevel, JSONObject(request.body.readUtf8()).getJSONObject("generationConfig").getJSONObject("thinkingConfig").getString("thinkingLevel"))
                assertTrue(provider.description.contains(model.apiId))
            }
            assertEquals(GeminiModel.FLASH_LITE, GeminiModel.DEFAULT)
            assertNull(GeminiModel.parse("https://untrusted/"))
        } finally { server.shutdown() }
    }
    @Test fun actual503ThenReadTimeoutRemainsTransientRatherThanBecomingKeyFailure() = runBlocking {
        val server = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":{"status":"UNAVAILABLE"}}"""))
            server.enqueue(MockResponse().setBody("{}").setBodyDelay(1, TimeUnit.SECONDS))
            val client = OkHttpClient.Builder().readTimeout(150, TimeUnit.MILLISECONDS).callTimeout(2, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false).build()
            try {
                GeminiProvider("not-real", client = client, baseUrl = server.url("v1beta/").toString()).transcribe(AudioChunk(0,0,1000,ShortArray(48000)), "auto")
                fail("Timeout accepted")
            } catch (e: ProviderFailure) {
                assertTrue(e.retryable); assertEquals(ProviderIssue.NETWORK_TIMEOUT, e.issue)
                assertEquals(2, server.requestCount)
            }
        } finally { server.shutdown() }
    }
}
