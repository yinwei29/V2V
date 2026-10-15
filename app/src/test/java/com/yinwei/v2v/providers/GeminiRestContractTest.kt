package com.yinwei.v2v.providers

import com.yinwei.v2v.diagnostics.*
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class GeminiRestContractTest {
    private val fixtureKey = "not-a-real-google-key"
    @Test fun outgoingWireValuesMatchIndependentGoogleDiscoveryEnums() = runBlocking {
        val contract = JSONObject(checkNotNull(javaClass.getResourceAsStream("/gemini-rest-enums.json")).bufferedReader().use { it.readText() })
        fun values(name: String) = contract.getJSONArray(name).let { a -> (0 until a.length()).map { a.getString(it) } }
        val server = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setBody("""{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"{\"text\":\"早安\"}"}]}}]}"""))
            GeminiProvider(fixtureKey, baseUrl = server.url("v1beta/").toString()).translate("good morning", "en")
            val config = JSONObject(server.takeRequest().body.readUtf8()).getJSONObject("generationConfig")
            assertTrue(config.getJSONObject("thinkingConfig").getString("thinkingLevel") in values("ThinkingConfig.thinkingLevel"))
            assertTrue(config.getJSONObject("responseFormat").getJSONObject("text").getString("mimeType") in values("TextResponseFormat.mimeType"))
            // The previously shipped strings are invalid for these REST enum fields.
            assertFalse("low" in values("ThinkingConfig.thinkingLevel"))
            assertFalse("application/json" in values("TextResponseFormat.mimeType"))
        } finally { server.shutdown() }
    }
    @Test fun invalidArgumentIsReportedAsRequestFormatWithoutLeakingErrorBody() = runBlocking {
        val server = MockWebServer().apply { start() }
        val directory = Files.createTempDirectory("gemini-safe-error").toFile()
        val journal = DiagnosticJournal(directory, DiagnosticEnvironment("test", 35, "test", "test"))
        try {
            server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"code":400,"status":"INVALID_ARGUMENT","message":"private transcript $fixtureKey","details":[{"@type":"type.googleapis.com/google.rpc.BadRequest","fieldViolations":[{"description":"private transcript"}]}]}}"""))
            try { GeminiProvider(fixtureKey, baseUrl = server.url("v1beta/").toString(), diagnostics = journal).translate("private input", "en"); fail("400 accepted") }
            catch (e: ProviderFailure) {
                assertEquals(400, e.status); assertEquals(ProviderIssue.INVALID_REQUEST, e.issue)
                assertTrue(e.message!!.contains("參數")); assertFalse(e.message!!.contains(fixtureKey)); assertFalse(e.message!!.contains("private"))
            }
            assertEquals(1, server.requestCount)
            val log = ByteArrayOutputStream().also(journal::export).toString("UTF-8")
            assertTrue(log.contains("INVALID_REQUEST")); assertFalse(log.contains(fixtureKey)); assertFalse(log.contains("private"))
        } finally { server.shutdown(); journal.close(); directory.deleteRecursively() }
    }
    @Test fun invalidKeyErrorIsDistinguishedFromBadRequestSyntax() = runBlocking {
        val server = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"status":"INVALID_ARGUMENT","message":"secret error $fixtureKey","details":[{"@type":"type.googleapis.com/google.rpc.ErrorInfo","reason":"API_KEY_INVALID","metadata":{"key":"$fixtureKey"}}]}}"""))
            try { GeminiProvider(fixtureKey, baseUrl = server.url("v1beta/").toString()).healthCheck(); fail("Key accepted") }
            catch (e: ProviderFailure) { assertEquals(ProviderIssue.INVALID_KEY, e.issue); assertTrue(e.message!!.contains("Key 無效")); assertFalse(e.message!!.contains(fixtureKey)) }
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }
    @Test fun oversizedErrorBodyRetainsHttpStatusWithoutExposingOrParsingPrefix() = runBlocking {
        val server = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setResponseCode(400).setBody("sensitive-".repeat(9000)))
            try { GeminiProvider(fixtureKey, baseUrl = server.url("v1beta/").toString()).healthCheck(); fail("400 accepted") }
            catch (e: ProviderFailure) { assertEquals(400, e.status); assertEquals(ProviderIssue.UNKNOWN, e.issue); assertFalse(e.message!!.contains("sensitive")) }
        } finally { server.shutdown() }
    }
    @Test fun cancellationStillOwnsCallWhileReadingAnErrorBody() = runBlocking {
        val server = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setResponseCode(400).setBody("{}").setBodyDelay(3, TimeUnit.SECONDS))
            val job = launch { GeminiProvider(fixtureKey, baseUrl = server.url("v1beta/").toString()).healthCheck() }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
            delay(100); withTimeout(1000) { job.cancelAndJoin() }; assertTrue(job.isCancelled)
        } finally { server.shutdown() }
    }
}
