package com.yinwei.v2v.transcript

import com.yinwei.v2v.audio.*
import com.yinwei.v2v.providers.*
import com.yinwei.v2v.session.*
import com.yinwei.v2v.diagnostics.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Synthetic PCM and local protocol fixtures verify plumbing, not actual recognition accuracy. */
class SpecialistPipelineTest {
    private fun asr(text: String) = """{"status":"completed","steps":[{"type":"model_output","content":[{"type":"text","text":"$text"}]}]}"""
    private val mt = """{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"{\"text\":\"不要關閉12號閥門。\"}"}]}}]}"""
    private suspend fun fixture(block: suspend (MockWebServer, SessionRepository, SubtitlePipeline, List<TranscriptSegment>, DiagnosticJournal) -> Unit) {
        val server = MockWebServer().apply { start() }
        val repo = SessionRepository().apply { set(SessionSnapshot(id = "abc123", cloudEnabled = true)) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dir = Files.createTempDirectory("specialist-fixture").toFile()
        val journal = DiagnosticJournal(dir, DiagnosticEnvironment("test", 35, "test", "test"))
        val spoken = java.util.concurrent.CopyOnWriteArrayList<TranscriptSegment>()
        val provider = GeminiTranscribeProvider("not-a-real-key", baseUrl = server.url("v1beta/").toString(), diagnostics = journal)
        val pipeline = SubtitlePipeline("abc123", "en", provider, repo, scope, journal, translated = { spoken.add(it) })
        try { withTimeout(6000) { block(server, repo, pipeline, spoken, journal) } }
        finally { pipeline.cancel(); scope.cancel(); journal.close(); dir.deleteRecursively(); server.shutdown() }
    }
    private fun send(pipeline: SubtitlePipeline) {
        repeat(80) { i -> pipeline.offer(AudioFrame("abc123", Source.OWNED_FILE, i.toLong(), i * 20000L, 48000,
            ShortArray(960) { if (i < 50) 3000 else 0 }, i * 960L)) }
    }
    @Test fun sourceAppearsBeforeTranslationAndSpeechUsesTheExactPublishedTextWithSafeTimings() = runBlocking {
        fixture { server, repo, pipeline, spoken, journal ->
            server.enqueue(MockResponse().setBody(asr("Do not close valve 12.")))
            server.enqueue(MockResponse().setBody(mt).setBodyDelay(400, TimeUnit.MILLISECONDS))
            send(pipeline)
            val source = repo.state.first { it.segments.any { s -> s.revision == 1 } }
            assertEquals("Do not close valve 12.", source.segments.single().originalText)
            assertTrue(source.segments.single().translatedText.isEmpty())
            pipeline.finish()
            val final = repo.state.value.segments.single()
            assertEquals("不要關閉12號閥門。", final.translatedText); assertEquals(listOf(final), spoken)
            assertEquals("/v1beta/interactions", server.takeRequest().path)
            assertEquals("/v1beta/models/gemini-3.8-flash:generateContent", server.takeRequest().path)
            val logs = ByteArrayOutputStream().also(journal::export).toString("UTF-8")
            assertTrue(logs.contains("PIPELINE_TIMING")); assertTrue(logs.contains("\"COMBINED_REQUEST\":0"))
            assertFalse(logs.contains("Do not close")); assertFalse(logs.contains("不要關閉")); assertFalse(logs.contains("not-a-real-key"))
        }
    }
    @Test fun translationFailureRetainsVerifiedSourceAndNeverQueuesInventedSpeech() = runBlocking {
        fixture { server, repo, pipeline, spoken, _ ->
            server.enqueue(MockResponse().setBody(asr("Do not close valve 12.")))
            server.enqueue(MockResponse().setResponseCode(403).setBody("private provider text"))
            send(pipeline)
            val failed = repo.state.first { it.providerError.isNotBlank() }
            assertEquals("Do not close valve 12.", failed.segments.single().originalText)
            assertEquals("", failed.segments.single().translatedText); assertTrue(spoken.isEmpty())
            assertEquals(StageState.ERROR, failed.mtState); assertFalse(failed.providerError.contains("private"))
            assertEquals(2, server.requestCount)
        }
    }
    @Test fun emptySpeechResultDoesNotCallTranslationPublishCaptionsOrQueueSpeech() = runBlocking {
        fixture { server, repo, pipeline, spoken, _ ->
            server.enqueue(MockResponse().setBody(asr("")))
            send(pipeline); pipeline.finish()
            assertEquals(1, server.requestCount); assertTrue(repo.state.value.segments.isEmpty()); assertTrue(spoken.isEmpty())
            assertEquals(StageState.IDLE, repo.state.value.asrState)
        }
    }
}
