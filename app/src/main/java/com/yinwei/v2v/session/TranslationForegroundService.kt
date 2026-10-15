package com.yinwei.v2v.session

import com.yinwei.v2v.overlay.CaptionOverlay
import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import com.yinwei.v2v.R
import com.yinwei.v2v.security.ApiKeyStore
import com.yinwei.v2v.providers.OpenAiProvider
import com.yinwei.v2v.diagnostics.*
import com.yinwei.v2v.audio.*
import com.yinwei.v2v.settings.SettingsStore
import com.yinwei.v2v.settings.PresentationSettings
import com.yinwei.v2v.ui.MainActivity
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import java.util.UUID

class TranslationForegroundService : Service() {
    private val repository: SessionRepository by inject()
    private val settings: SettingsStore by inject()
    private val keys: ApiKeyStore by inject()
    private val diagnostics: Diagnostics by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var overlay: CaptionOverlay? = null
    private var projection: MediaProjection? = null
    private val controller: SessionController by inject()
    @Volatile private var presentation = PresentationSettings()
    @Volatile private var mix = MixSettings()
    @Volatile private var outputPaused = false
    @Volatile private var ending = false
    private var focus: AudioFocusRequest? = null
    private lateinit var audio: AudioManager
    private var terminalMessage = "使用者已停止"
    private var terminalState = SessionState.STOPPED
    private var stopReason = StopReason.SOURCE_FINISHED
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() { stopReason = StopReason.SYSTEM_REVOKED; end("系統已撤銷或停止擷取；重新開始需再次授權", SessionState.ERROR) }
    }
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { pauseOutput("輸出裝置變更：已停止本 APP 音訊；請確認後恢復") }
    }
    private val devices = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
            removed.filter { it.isSink }.forEach { diagnostics.journal.record(DiagnosticEvent.ROUTE_CHANGE, repository.state.value.id, mapOf(Metric.ROUTE_TYPE to it.type)) }
            if (removed.any { it.isSink }) pauseOutput("音訊裝置移除：已停止本 APP 音訊；來源字幕輸入持續")
        }
    }
    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(AudioManager::class.java)
        overlay = CaptionOverlay(this, {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }, { stopReason = StopReason.USER; end("使用者已停止") }, {
            if (outputPaused) resumeOutput() else pauseOutput("播報已暫停；字幕持續")
        }, { message -> repository.update { it.copy(overlayError = message) } })
        scope.launch { repository.state.collect { overlay?.update(it) } }
        audio.registerAudioDeviceCallback(devices, Handler(mainLooper))
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        scope.launch { settings.presentation.collect {
            if (presentation.speechEnabled && !it.speechEnabled) controller.clearSpeech()
            presentation = it; overlay?.configure(it)
        } }
        scope.launch { settings.settings.collect { mix = it; diagnostics.mix(it) } }
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "音訊驗證工作階段", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            STOP -> { stopReason = StopReason.USER; end("使用者已停止") }
            RESUME -> resumeOutput()
            SHOW_OVERLAY -> if (job != null) overlay?.show() else stopSelf()
            PAUSE -> pauseOutput("播報已暫停；字幕持續")
            START -> if (job == null) begin(intent)
            else -> if (job == null) stopSelf()
        }
        return START_NOT_STICKY
    }
    private fun begin(intent: Intent) {
        val external = intent.getBooleanExtra("external", false)
        val fileUri = intent.getStringExtra("fileUri")?.let(android.net.Uri::parse)
        val source = if (external) Source.SYSTEM_PLAYBACK else if (fileUri != null) Source.OWNED_FILE else Source.TEST_FILE
        val cloud = intent.getBooleanExtra("cloudConsent", false) && source != Source.TEST_FILE
        val language = intent.getStringExtra("language") ?: "en"
        if (language !in setOf("en", "id", "auto")) { stopSelf(); return }
        val provider = if (cloud) keys.read()?.let { OpenAiProvider(it, diagnostics = diagnostics.journal) } else null
        if (cloud && provider == null) {
            repository.update { it.copy(state = SessionState.ERROR, message = "尚未設定有效的 API Key；沒有開始擷取或傳送") }
            stopSelf(); return
        }
        val id = UUID.randomUUID().toString()
        diagnostics.journal.record(DiagnosticEvent.SESSION_START, id, mapOf(Metric.CLOUD_ENABLED to if (cloud) 1 else 0), mapOf(Tag.SOURCE to source, Tag.LANGUAGE to DiagnosticLanguage.valueOf(language.uppercase(java.util.Locale.ROOT))))
        terminalState = SessionState.STOPPED
        terminalMessage = "來源結束"
        stopReason = StopReason.SOURCE_FINISHED
        ending = false
        outputPaused = false
        repository.set(SessionSnapshot(id = id, state = SessionState.STARTING, source = source, cloudEnabled = cloud, sourceLanguage = language,
            capabilities = if (external) SourceCapabilities() else SourceCapabilities(
                originalControl = OriginalGainControlCapability.APP_OWNED, externalDirectOutputPresent = false,
                reason = if (source == Source.TEST_FILE) "本 APP 自有測試音檔；440Hz 原聲／660Hz 模擬譯音，不是 TTS"
                    else "使用者授權 WAV，由本 APP 播放；尚未接入真實 TTS"),
            asrState = if (cloud) StageState.IDLE else StageState.NOT_CONFIGURED,
            mtState = if (cloud) StageState.IDLE else StageState.NOT_CONFIGURED))
        try {
            val serviceType = if (external) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                    (if (provider != null) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            }
            startForeground(1, notification(), serviceType)
            if (external) {
                val consent = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra("consent", Intent::class.java)
                    else @Suppress("DEPRECATION") (intent.getParcelableExtra("consent") as? Intent)
                requireNotNull(consent) { "缺少本次螢幕擷取授權" }
                projection = getSystemService(MediaProjectionManager::class.java)
                    .getMediaProjection(intent.getIntExtra("resultCode", Activity.RESULT_CANCELED), consent)
                checkNotNull(projection).registerCallback(projectionCallback, Handler(mainLooper))
            } else {
                focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                    .setOnAudioFocusChangeListener { change -> if (change < 0) pauseOutput("音訊焦點失去；請確認後恢復") }.build()
                check(audio.requestAudioFocus(focus!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "無法取得音訊焦點" }
            }
        } catch (e: Exception) {
            diagnostics.journal.record(DiagnosticEvent.SESSION_STOP, id, tags = mapOf(Tag.STOP_REASON to StopReason.AUDIO_ERROR), error = e.javaClass)
            end("啟動失敗：${e.javaClass.simpleName}", SessionState.ERROR)
            return
        }
        if (android.provider.Settings.canDrawOverlays(this)) overlay?.show()
        job = scope.launch {
            try {
                withContext(Dispatchers.IO) { controller.run(id, source, projection, { mix }, { outputPaused }, fileUri, provider, language, { presentation }) }
            } catch (_: CancellationException) {
                // Explicit end owns terminal state.
            } catch (e: Exception) {
                if (!ending) {
                    diagnostics.journal.record(DiagnosticEvent.SESSION_STOP, id, tags = mapOf(Tag.STOP_REASON to StopReason.AUDIO_ERROR), error = e.javaClass)
                    terminalMessage = if (e is IllegalArgumentException && e.message == WavReader.FORMAT) WavReader.FORMAT
                        else "音訊失敗：${e.javaClass.simpleName}；請檢查 WAV 格式、權限或來源"
                    terminalState = SessionState.ERROR
                    stopReason = StopReason.AUDIO_ERROR
                }
            } finally {
                val last = repository.state.value
                diagnostics.journal.record(DiagnosticEvent.SESSION_STOP, id,
                    metrics = mapOf(Metric.FRAMES to last.frames, Metric.SAMPLES to last.asrSamples, Metric.ELAPSED_MS to last.elapsedMs,
                        Metric.UNDERRUNS to last.underruns, Metric.DROPPED_SAMPLES to last.skippedSamples),
                    tags = mapOf(Tag.STATE to terminalState, Tag.STOP_REASON to stopReason))
                releaseProjection()
                focus?.let { audio.abandonAudioFocusRequest(it) }; focus = null
                repository.update { it.copy(state = terminalState, level = 0f, message = terminalMessage,
                    sourceStatus = if (terminalState == SessionState.ERROR) SourceStatus.ERROR else it.sourceStatus) }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }
    private fun resumeOutput() {
        outputPaused = false
        controller.resumeOutput()
        repository.update { it.copy(outputPaused = false, message = "已恢復本 APP 音訊；舊播報不會重播") }
    }
    private fun pauseOutput(message: String) {
        diagnostics.journal.record(DiagnosticEvent.OUTPUT_PAUSE, repository.state.value.id)
        outputPaused = true
        controller.pauseOutput()
        repository.update { it.copy(outputPaused = true, message = message) }
    }
    private fun end(message: String, state: SessionState = SessionState.STOPPED) {
        ending = true
        terminalMessage = message; terminalState = state
        repository.update { it.copy(state = SessionState.STOPPING, message = message) }
        controller.interrupt()
        job?.cancel()
        if (job == null) {
            releaseProjection()
            focus?.let { audio.abandonAudioFocusRequest(it) }; focus = null
            repository.update { it.copy(state = state, message = message,
                sourceStatus = if (state == SessionState.ERROR) SourceStatus.ERROR else it.sourceStatus) }
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
        }
    }
    private fun releaseProjection() {
        val previous = projection
        projection = null
        runCatching { previous?.unregisterCallback(projectionCallback) }
        runCatching { previous?.stop() }
    }
    override fun onDestroy() {
        overlay?.close(); overlay = null
        if (job?.isActive == true && !ending) {
            terminalState = SessionState.STOPPED
            terminalMessage = "系統已停止服務；請由主畫面重新開始"
            stopReason = StopReason.SERVICE_DESTROYED
            repository.update { it.copy(state = SessionState.STOPPED, level = 0f, message = terminalMessage) }
        }
        controller.interrupt()
        scope.cancel()
        releaseProjection()
        audio.unregisterAudioDeviceCallback(devices)
        unregisterReceiver(noisy)
        focus?.let { audio.abandonAudioFocusRequest(it) }
        super.onDestroy()
    }
    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val restore = PendingIntent.getService(this, 2, Intent(this, javaClass).setAction(SHOW_OVERLAY), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, javaClass).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_wave)
            .setContentTitle("V2V · 音訊工作階段").setContentText("擷取／字幕工作中 · 按停止結束；無自動重新錄音")
            .setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null, "顯示字幕", restore).build()).addAction(Notification.Action.Builder(null, "停止", stop).build()).build()
    }
    companion object {
        const val START = "com.yinwei.v2v.START"
        const val STOP = "com.yinwei.v2v.STOP"
        const val SHOW_OVERLAY = "com.yinwei.v2v.SHOW_OVERLAY"
        const val PAUSE = "com.yinwei.v2v.PAUSE"
        const val RESUME = "com.yinwei.v2v.RESUME"
        private const val CHANNEL = "session"
    }
}
