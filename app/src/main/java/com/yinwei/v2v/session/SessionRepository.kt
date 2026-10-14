package com.yinwei.v2v.session

import com.yinwei.v2v.audio.*
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
    val outputPaused: Boolean = false,
    val message: String = "M0 / M1 音訊驗證版 · 尚未接入 ASR、翻譯及真實 TTS",
)
class SessionRepository {
    private val mutable = MutableStateFlow(SessionSnapshot())
    val state = mutable.asStateFlow()
    fun set(snapshot: SessionSnapshot) { mutable.value = snapshot }
    fun update(transform: (SessionSnapshot) -> SessionSnapshot) { mutable.update(transform) }
}
