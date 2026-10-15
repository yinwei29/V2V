# 架構與 M1 邊界

單一 app module，套件按 ui / session / audio / settings 分層，避免形式拆分。Application 用 Koin 注入 SessionRepository、SettingsStore；Activity 訂閱 StateFlow 並發 Intent，前景服務持有獨立注入的 SessionController，controller 持有音訊生命週期。

自有來源：private WAV asset → 20ms mono PCM frame → AsrProbe（RMS / samples，非辨識）→ StereoMixer ← 譯音 fixture → 單一 AudioTrack。
外部來源：每次系統授權 → mediaProjection foreground service → AudioPlaybackCapture → PCM → AsrProbe / 診斷，不重播來源。

AudioFrame 帶 sessionId、sourceId、sequence、monotonic timestamp、48k PCM16 mono。每段新陣列，僅 synchronous consume，不保留 ASR backlog；fixture 2 秒且有固定長度驗證。沒有網路、TTS 任務或跨 session callback 結果。

服務不隨 Activity 旋轉或移除而重建。停止取消 coroutine、打斷阻塞 read、finally 關閉 AudioRecord / AudioTrack、projection callback、focus；onDestroy 額外清理。START_NOT_STICKY，不在 boot / process recreation 啟動。實際釋放時序與 20 次啟停須真機驗證。

M2 再加入真實 provider contracts、segmenter、bounded ASR/MT/TTS queues、segment revision 去重；M3 加入 PCM_TTS 與解碼／重採樣；M4 加 overlay。不能用現有 Probe 代稱完成 ASR。

## M2 preview 與 Log

新增 packages providers / security / transcript / diagnostics，仍單一 Gradle module。服務取得來源、同意、語言與 Key 建立 provider，SessionController 在增益前分流 frame 到 SubtitlePipeline；Activity 不持有錄音生命週期。

WavReader 使用串流讀 RIFF chunks，只接受48k mono PCM16；未實作格式解碼或重採樣，其他格式明確拒絕。AudioFrame 新增 sourcePositionSamples，給分段器保留丟片時的來源缺口。600ms energy endpoint +10sec ceiling；frame queue250，chunk rendezvous不排 backlog，忙碌丟片計數；正常EOF可等待最後尾段，停止則取消。原始 frame 由生產端建立後只讀，可由 bounded subtitle queue 持有到消費；mixer不改輸入。

ASR/MT worker 串行處理穩定 final，ledger按session/id/revision驗證，最多200段；MT先顯示原文再revision2譯文。沒有假partial、confidence、說話者或TTS。OkHttp Call 的 cancellable continuation涵蓋 body read，取消不是只關到headers。provider錯誤保留原文並顯示，不重新播放舊 session。

Diagnostics用型別化事件禁止自由文字，背景單writer queue128，檔案輪替3×512KiB；App監看StateFlow、服務錄路由/啟停/權限/錯誤class，provider只記status/latency。匯出barrier後取快照、FileProvider僅cache/diagnostics。純JVM測JSON、保存、輪替、刪除與隱私；Android整合待裝置。
