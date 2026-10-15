package com.yinwei.v2v.diagnostics

import android.content.Context
import android.os.Build
import com.yinwei.v2v.BuildConfig
import com.yinwei.v2v.audio.MixSettings
import com.yinwei.v2v.session.SessionSnapshot
import java.io.File

class Diagnostics(context: Context) {
    val journal = DiagnosticJournal(File(context.noBackupFilesDir, "technical-logs"),
        DiagnosticEnvironment(BuildConfig.VERSION_NAME, Build.VERSION.SDK_INT, Build.MANUFACTURER, Build.MODEL))
    private var previousState = ""
    private var previousProvider = ""
    private var previousSamples = 0L
    private var sampledAt = 0L
    private var previousMix: MixSettings? = null
    @Synchronized fun session(snapshot: SessionSnapshot, force: Boolean = false) {
        if (force) { previousState = ""; previousProvider = ""; sampledAt = 0; previousSamples = -1 }
        val stateKey = "${snapshot.id}/${snapshot.state}/${snapshot.sourceStatus}"
        if (previousState != stateKey) {
            previousState = stateKey
            journal.record(DiagnosticEvent.SESSION_STATE, snapshot.id,
                tags = mapOf(Tag.SOURCE to snapshot.source, Tag.STATE to snapshot.state, Tag.SOURCE_STATUS to snapshot.sourceStatus))
        }
        val provider = "${snapshot.asrState}/${snapshot.mtState}/${snapshot.ttsState}"
        if (previousProvider != provider) {
            previousProvider = provider
            journal.record(DiagnosticEvent.PROVIDER_STATE, snapshot.id, tags = mapOf(Tag.ASR_STATE to snapshot.asrState, Tag.MT_STATE to snapshot.mtState, Tag.TTS_STATE to snapshot.ttsState))
        }
        val now = System.nanoTime() / 1_000_000
        if (snapshot.asrSamples != previousSamples && now - sampledAt >= 1000) {
            previousSamples = snapshot.asrSamples; sampledAt = now
            journal.record(DiagnosticEvent.PCM_SAMPLE, snapshot.id, metrics = mapOf(Metric.RMS to snapshot.level, Metric.FRAMES to snapshot.frames,
                Metric.SAMPLES to snapshot.asrSamples, Metric.ELAPSED_MS to snapshot.elapsedMs, Metric.UNDERRUNS to snapshot.underruns,
                Metric.DROPPED_SAMPLES to snapshot.skippedSamples, Metric.TTS_DROPPED to snapshot.ttsDropped, Metric.TTS_FAILURES to snapshot.ttsFailures,
                Metric.SPEECH_RATE to snapshot.ttsRate, Metric.BUFFERED_MS to snapshot.ttsBufferedMs, Metric.PENDING_SEGMENTS to snapshot.ttsPending))
        }
    }
    @Synchronized fun mix(settings: MixSettings) {
        if (previousMix == settings) return
        previousMix = settings
        journal.record(DiagnosticEvent.MIX_CHANGE, metrics = mapOf(Metric.ORIGINAL_GAIN to settings.originalGain,
            Metric.TRANSLATION_GAIN to settings.translationGain, Metric.SWAP to if (settings.swap) 1 else 0), tags = mapOf(Tag.MIX_MODE to settings.mode))
    }
}
