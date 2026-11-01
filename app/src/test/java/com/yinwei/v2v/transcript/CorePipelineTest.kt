package com.yinwei.v2v.transcript

import com.yinwei.v2v.audio.*
import com.yinwei.v2v.providers.*
import com.yinwei.v2v.session.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.*
import org.junit.Test

class CorePipelineTest {
    private class Combined : TranslationProvider, AudioTranslationProvider {
        val received = Channel<Pair<AudioChunk, TranslationContext>>(Channel.UNLIMITED)
        override val description = "synthetic combined"
        override val partialSupported = false
        override suspend fun healthCheck() = Unit
        override suspend fun transcribe(chunk: AudioChunk, language: String): String = error("Separate ASR called")
        override suspend fun translate(text: String, language: String, targetLanguage: String): TranslatedText = error("Separate MT called")
        override suspend fun translateAudio(chunk: AudioChunk, language: String, targetLanguage: String): AudioTranslation = error("Context path bypassed")
        override suspend fun translateAudioInContext(chunk: AudioChunk, language: String, targetLanguage: String, context: TranslationContext): AudioTranslation {
            received.send(chunk to context)
            return AudioTranslation("source-${chunk.id}", TranslatedText("translation-${chunk.id}", null))
        }
    }
    private suspend fun fixture(block: suspend (SubtitlePipeline, Combined, SessionRepository) -> Unit) {
        val provider = Combined()
        val repo = SessionRepository().apply { set(SessionSnapshot(id = "core")) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val pipeline = SubtitlePipeline("core", "en", provider, repo, scope)
        try { withTimeout(5000) { block(pipeline, provider, repo) } }
        finally { pipeline.cancel(); scope.cancel() }
    }
    private fun feed(pipeline: SubtitlePipeline, from: Int, count: Int, voiced: (Int) -> Boolean) {
        repeat(count) { offset ->
            val i = from + offset
            pipeline.offer(AudioFrame("core", Source.OWNED_FILE, i.toLong(), i * 20000L, 48000,
                ShortArray(960) { if (voiced(offset)) 3000 else 0 }, i * 960L))
        }
    }
    @Test fun shortGeminiSentenceIsSubmittedAfterSilenceWithoutWaitingForFourSecondsOrFinish() = runBlocking {
        fixture { pipeline, provider, _ ->
            feed(pipeline, 0, 80) { it < 50 }
            val (chunk, context) = provider.received.receive()
            assertEquals(1600L, chunk.endMs); assertEquals(0L, chunk.startMs)
            assertTrue(context.precedingOriginals.isEmpty())
            pipeline.finish()
        }
    }
    @Test fun sustainedSpeechIsSubmittedAtFourSecondsAndTailRemainsExact() = runBlocking {
        fixture { pipeline, provider, repo ->
            feed(pipeline, 0, 200) { true }
            val first = provider.received.receive().first
            assertEquals(4000L, first.endMs); assertEquals(192000, first.pcm.size)
            feed(pipeline, 200, 50) { true }
            pipeline.finish()
            val (tail, context) = provider.received.receive()
            assertEquals(4000L, tail.startMs); assertEquals(5000L, tail.endMs)
            assertEquals(listOf("source-0"), context.precedingOriginals)
            assertEquals(listOf("translation-0", "translation-1"), repo.state.value.segments.map { it.translatedText })
            assertEquals(0L, repo.state.value.skippedSamples)
        }
    }
    @Test fun onlySourceContextFlowsToFollowingAudioAndNewSessionStartsEmpty() = runBlocking {
        repeat(2) {
            fixture { pipeline, provider, _ ->
                feed(pipeline, 0, 80) { it < 50 }
                assertTrue(provider.received.receive().second.precedingOriginals.isEmpty())
                feed(pipeline, 80, 80) { it < 50 }
                assertEquals(listOf("source-0"), provider.received.receive().second.precedingOriginals)
                pipeline.finish()
            }
        }
    }
}
