# 架構與 M1 邊界

單一 app module，套件按 ui / session / audio / settings 分層，避免形式拆分。Application 用 Koin 注入 SessionRepository、SettingsStore；Activity 訂閱 StateFlow 並發 Intent，前景服務持有獨立注入的 SessionController，controller 持有音訊生命週期。

自有來源：private WAV asset → 20ms mono PCM frame → AsrProbe（RMS / samples，非辨識）→ StereoMixer ← 譯音 fixture → 單一 AudioTrack。
外部來源：每次系統授權 → mediaProjection foreground service → AudioPlaybackCapture → PCM → AsrProbe / 診斷，不重播來源。

AudioFrame 帶 sessionId、sourceId、sequence、monotonic timestamp、48k PCM16 mono。每段新陣列，僅 synchronous consume，不保留 ASR backlog；fixture 2 秒且有固定長度驗證。沒有網路、TTS 任務或跨 session callback 結果。

服務不隨 Activity 旋轉或移除而重建。停止取消 coroutine、打斷阻塞 read、finally 關閉 AudioRecord / AudioTrack、projection callback、focus；onDestroy 額外清理。START_NOT_STICKY，不在 boot / process recreation 啟動。實際釋放時序與 20 次啟停須真機驗證。

M2 再加入真實 provider contracts、segmenter、bounded ASR/MT/TTS queues、segment revision 去重；M3 加入 PCM_TTS 與解碼／重採樣；M4 加 overlay。不能用現有 Probe 代稱完成 ASR。
