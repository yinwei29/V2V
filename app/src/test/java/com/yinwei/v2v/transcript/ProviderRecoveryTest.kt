package com.yinwei.v2v.transcript

import com.yinwei.v2v.audio.*
import com.yinwei.v2v.providers.*
import com.yinwei.v2v.session.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ProviderRecoveryTest {
    private class Scripted(private val asr: suspend (Int) -> String,
        private val mt: suspend (Int) -> TranslatedText = { TranslatedText("譯文$it", null) }) : TranslationProvider {
        val asrCalls = AtomicInteger(); val mtCalls = AtomicInteger()
        var lastTarget: String? = null
        override val description = "test-provider"
        override val partialSupported = false
        override suspend fun healthCheck() = Unit
        override suspend fun transcribe(chunk: AudioChunk, language: String) = asr(asrCalls.incrementAndGet())
        override suspend fun translate(text: String, language: String, targetLanguage: String): TranslatedText { lastTarget = targetLanguage; return mt(mtCalls.incrementAndGet()) }
    }
    private suspend fun fixture(provider: Scripted, target: String = "zh-TW", block: suspend (SubtitlePipeline, SessionRepository) -> Unit) {
        val repository = SessionRepository().apply { set(SessionSnapshot(id = "recovery", cloudEnabled = true)) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val pipeline = SubtitlePipeline("recovery", "en", provider, repository, scope, targetLanguage = target)
        try { withTimeout(12000) { block(pipeline, repository) } }
        finally { pipeline.cancel(); scope.cancel() }
    }
    private fun send(pipeline: SubtitlePipeline, sentence: Int) {
        repeat(80) { index ->
            val frame = sentence * 80 + index
            pipeline.offer(AudioFrame("recovery", Source.OWNED_FILE, frame.toLong(), frame * 20000L,
                48000, ShortArray(960) { if (index < 50) 3000 else 0 }, frame * 960L))
        }
    }
    @Test fun chosenTargetFlowsThroughProviderAndPublishedSubtitle() = runBlocking {
        val provider = Scripted({ "原文" }, { TranslatedText("English translation", null) })
        fixture(provider, target = "en") { pipeline, repo ->
            send(pipeline, 0)
            val state = repo.state.first { it.segments.any { s -> s.revision == 2 } }
            assertEquals("en", provider.lastTarget)
            assertEquals("en", state.segments.single().targetLanguage)
            assertEquals("English translation", state.segments.single().translatedText)
        }
    }
    @Test fun temporaryAsr503DoesNotKillPipelineAndNextSentenceRecovers() = runBlocking {
        val provider = Scripted({ if (it == 1) throw ProviderFailure(503, "service unavailable") else "original$it" })
        fixture(provider) { pipeline, repo ->
            send(pipeline, 0); repo.state.first { it.providerRecovering }
            send(pipeline, 1)
            val recovered = repo.state.first { it.segments.any { segment -> segment.revision == 2 } && !it.providerRecovering }
            assertEquals(2, provider.asrCalls.get()); assertEquals("original2", recovered.segments.single().originalText)
            assertTrue(recovered.providerError.isBlank()); assertTrue(recovered.skippedSamples > 0)
        }
    }
    @Test fun timeoutAfterHttpRetryIsAlsoRecoverable() = runBlocking {
        val provider = Scripted({ if (it == 1) throw ProviderFailure(0, "timeout", ProviderIssue.NETWORK_TIMEOUT, retryable = true) else "next" })
        fixture(provider) { pipeline, repo ->
            send(pipeline, 0); repo.state.first { it.providerRecovering }; send(pipeline, 1)
            repo.state.first { it.segments.any { s -> s.translatedText.isNotBlank() } && !it.providerRecovering }
            assertEquals(2, provider.asrCalls.get())
        }
    }
    @Test fun temporaryTranslationFailureKeepsOriginalAndAllowsFollowingSentence() = runBlocking {
        val provider = Scripted({ "original$it" }, { if (it == 1) throw ProviderFailure(503, "busy") else TranslatedText("翻譯完成", null) })
        fixture(provider) { pipeline, repo ->
            send(pipeline, 0)
            val pending = repo.state.first { it.providerRecovering }
            assertEquals("original1", pending.segments.single().originalText)
            assertEquals(StageState.IDLE, pending.asrState); assertEquals(StageState.ERROR, pending.mtState)
            send(pipeline, 1)
            val recovered = repo.state.first { it.segments.any { s -> s.translatedText == "翻譯完成" } && !it.providerRecovering }
            assertEquals(2, recovered.segments.size); assertEquals("", recovered.segments.first().translatedText)
        }
    }
    @Test fun invalidKeyOrRequestIsTerminalAndNeverAutomaticallyRetried() = runBlocking {
        for (status in listOf(400, 401, 403, 429)) {
            val provider = Scripted({ throw ProviderFailure(status, "action required") })
            fixture(provider) { pipeline, repo ->
                send(pipeline, 0)
                repo.state.first { it.providerError.isNotBlank() }
                send(pipeline, 1); delay(50)
                assertEquals(1, provider.asrCalls.get()); assertFalse(repo.state.value.providerRecovering)
            }
        }
    }
    @Test fun repeatedOutageStopsAfterThreeFailedSentencesAndCancellationStopsRecovery() = runBlocking {
        val provider = Scripted({ throw ProviderFailure(503, "busy") })
        fixture(provider) { pipeline, repo ->
            send(pipeline, 0); repo.state.first { it.providerRecovering }
            send(pipeline, 1); send(pipeline, 2)
            val failed = repo.state.first { !it.providerRecovering && it.providerError.contains("3 段") }
            assertEquals(3, provider.asrCalls.get()); assertEquals(StageState.ERROR, failed.asrState)
            send(pipeline, 3); delay(50); assertEquals(3, provider.asrCalls.get())
        }
        val cancelled = Scripted({ throw ProviderFailure(503, "busy") })
        fixture(cancelled) { pipeline, repo ->
            send(pipeline, 0); repo.state.first { it.providerRecovering }; send(pipeline, 1)
            pipeline.cancel(); repo.set(SessionSnapshot(id = "new")); delay(50)
            assertEquals(1, cancelled.asrCalls.get()); assertEquals("new", repo.state.value.id)
            assertTrue(repo.state.value.providerError.isBlank())
        }
    }
}
