# 來源整合決策 · 2026-10-14

| 服務 | 接入 | 原始 PCM | 外部畫面 | 額外參與者 | 外部直出 | 靜音後輸入 | 授權 / 成本 | 結果 |
|---|---|---|---|---|---|---|---|---|
| 原創 test WAV | APP asset 解碼 | 已知格式；DSP 可測 | 不適用 | 無 | 無 | ASR 量測分支持續 | 本專案原創測試信號，無服務費 | 軟體測試；真機待測 |
| YouTube | Android AudioPlaybackCapture 候選 | 未真機驗證 | 可切換外部畫面，持續運作待測 | 無 | UNKNOWN | UNKNOWN | 使用者內容權限及平台政策；未做串流提取 | NOT_TESTED_ON_DEVICE；完整控制 BLOCKED_SOURCE_INTEGRATION |
| Zoom | 一般播放擷取不當作會議 PCM API | UNKNOWN | UNKNOWN | SDK 方案 UNKNOWN | UNKNOWN | UNKNOWN | 未指定 SDK、會議授權、帳號或費率 | BLOCKED_SOURCE_INTEGRATION |
| Google Meet | 一般播放擷取不當作通話 API | UNKNOWN | UNKNOWN | 授權方案 UNKNOWN | UNKNOWN | UNKNOWN | 未取得正式連續音訊通道與條款證據 | BLOCKED_SOURCE_INTEGRATION |

不提取受保護 URL、不以 WebView 注入、Accessibility、root、媒體音量或搶焦點偽造原聲控制。YouTube 嵌入播放器不等於 PCM API。
會議 SDK 若要求本 APP 內呈現或額外 participant，必須再明示與實測；尚未決定採用。
替代可交付：原創測試音檔；M2 擴充使用者授權檔案。麥克風／桌面 Companion 尚未實作。
