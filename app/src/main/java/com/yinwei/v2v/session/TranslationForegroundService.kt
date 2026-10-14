package com.yinwei.v2v.session

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import com.yinwei.v2v.R
import com.yinwei.v2v.audio.*
import com.yinwei.v2v.settings.SettingsStore
import com.yinwei.v2v.ui.MainActivity
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import java.util.UUID

class TranslationForegroundService : Service() {
    private val repository: SessionRepository by inject()
    private val settings: SettingsStore by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var projection: MediaProjection? = null
    private val controller: SessionController by inject()
    @Volatile private var mix = MixSettings()
    @Volatile private var outputPaused = false
    @Volatile private var ending = false
    private var focus: AudioFocusRequest? = null
    private lateinit var audio: AudioManager
    private var terminalMessage = "使用者已停止"
    private var terminalState = SessionState.STOPPED
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() { end("系統已撤銷或停止擷取；重新開始需再次授權", SessionState.ERROR) }
    }
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { pauseOutput("輸出裝置變更：已停止本 APP 音訊；請確認後恢復") }
    }
    private val devices = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
            if (removed.any { it.isSink }) pauseOutput("音訊裝置移除：已停止本 APP 音訊；來源字幕輸入持續")
        }
    }
    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(AudioManager::class.java)
        audio.registerAudioDeviceCallback(devices, Handler(mainLooper))
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        scope.launch { settings.settings.collect { mix = it } }
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "音訊驗證工作階段", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            STOP -> end("使用者已停止")
            RESUME -> { outputPaused = false; repository.update { it.copy(outputPaused = false, message = "已恢復測試音訊；非真實翻譯") } }
            START -> if (job == null) begin(intent)
            else -> if (job == null) stopSelf()
        }
        return START_NOT_STICKY
    }
    private fun begin(intent: Intent) {
        val external = intent.getBooleanExtra("external", false)
        val source = if (external) Source.SYSTEM_PLAYBACK else Source.TEST_FILE
        val id = UUID.randomUUID().toString()
        terminalState = SessionState.STOPPED
        terminalMessage = "來源結束"
        ending = false
        outputPaused = false
        repository.set(SessionSnapshot(id = id, state = SessionState.STARTING, source = source,
            capabilities = if (external) SourceCapabilities() else SourceCapabilities(
                originalControl = OriginalGainControlCapability.APP_OWNED, externalDirectOutputPresent = false,
                reason = "本 APP 自有測試音檔；440Hz 原聲／660Hz 模擬譯音，不是 TTS")))
        try {
            startForeground(1, notification(), if (external) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                else ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
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
            end("啟動失敗：${e.javaClass.simpleName}", SessionState.ERROR)
            return
        }
        job = scope.launch {
            try {
                withContext(Dispatchers.IO) { controller.run(id, source, projection, { mix }, { outputPaused }) }
            } catch (_: CancellationException) {
                // Explicit end owns terminal state.
            } catch (e: Exception) {
                if (!ending) {
                    terminalMessage = "音訊失敗：${e.javaClass.simpleName}；請重新授權或檢查來源"
                    terminalState = SessionState.ERROR
                }
            } finally {
                releaseProjection()
                focus?.let { audio.abandonAudioFocusRequest(it) }; focus = null
                repository.update { it.copy(state = terminalState, level = 0f, message = terminalMessage,
                    sourceStatus = if (terminalState == SessionState.ERROR) SourceStatus.ERROR else it.sourceStatus) }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }
    private fun pauseOutput(message: String) {
        outputPaused = true
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
        if (job?.isActive == true && !ending) {
            terminalState = SessionState.STOPPED
            terminalMessage = "系統已停止服務；請由主畫面重新開始"
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
        val stop = PendingIntent.getService(this, 1, Intent(this, javaClass).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_wave)
            .setContentTitle("V2V · 音訊驗證中").setContentText("M1 測試／擷取模式 · 尚無真實翻譯")
            .setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null, "停止", stop).build()).build()
    }
    companion object {
        const val START = "com.yinwei.v2v.START"
        const val STOP = "com.yinwei.v2v.STOP"
        const val RESUME = "com.yinwei.v2v.RESUME"
        private const val CHANNEL = "session"
    }
}
