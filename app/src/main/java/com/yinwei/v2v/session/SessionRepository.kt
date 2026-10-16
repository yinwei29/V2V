package com.yinwei.v2v.session

import com.yinwei.v2v.providers.CloudProvider
import com.yinwei.v2v.providers.GeminiModel

import com.yinwei.v2v.audio.*
import com.yinwei.v2v.transcript.TranscriptSegment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class StageState { NOT_CONFIGURED, IDLE, RUNNING, ERROR }

data class SessionSnapshot(
    val id: String = "", val state: SessionState = SessionState.IDLE,
    val source: Source = Source.TEST_FILE, val sourceStatus: SourceStatus = SourceStatus.UNKNOWN,
    val capabilities: SourceCapabilities = SourceCapabilities(), val level: Float = 0f,
    val frames: Long = 0, val asrSamples: Long = 0, val elapsedMs: Long = 0,
    val route: String = "尚未輸出", val underruns: Int = 0,
    val asrState: StageState = StageState.NOT_CONFIGURED,
    val mtState: StageState = StageState.NOT_CONFIGURED,
    val ttsState: StageState = StageState.NOT_CONFIGURED,
    val ttsError: String = "", val ttsDropped: Int = 0, val ttsFailures: Int = 0,
    val ttsRate: Float = 1f, val ttsBufferedMs: Long = 0, val ttsPending: Int = 0,
    val overlayError: String = "",
    val outputPaused: Boolean = false, val cloudEnabled: Boolean = false, val sourceLanguage: String = "en",
    val targetLanguage: String = "zh-TW",
    val cloudProvider: CloudProvider = CloudProvider.OPENAI,
    val segments: List<TranscriptSegment> = emptyList(),
    val skippedSamples: Long = 0, val providerTokens: Long? = null,
    val providerError: String = "", val providerRecovering: Boolean = false,
    val geminiModel: GeminiModel = GeminiModel.DEFAULT,
    val message: String = "選擇手機播放音訊及翻譯語言、同意雲端翻譯後開始；需本次系統授權",
)
class SessionRepository {
    private val mutable = MutableStateFlow(SessionSnapshot())
    val state = mutable.asStateFlow()
    fun set(snapshot: SessionSnapshot) { mutable.value = snapshot }
    fun update(transform: (SessionSnapshot) -> SessionSnapshot) { mutable.update(transform) }
}
