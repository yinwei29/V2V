# 測試報告 · 0.4.0-studio

2026-10-15。OpenJDK17 / Gradle8.13 / SDK35。本機assembleDebug、testDebugUnitTest及lintDebug已通過；53項測試，0 failure/error/skipped。最後完整build/test/lint為2m15s；lint 0 errors, 10 warnings。最後輸出與JUnit、lint見 evidence/studio/。完整CI以本版main commit的CircleCI狀態為準，不以舊版#4冒充本版結果。

## 新增回歸

- HistoryStore6項：final資料持久化與重新載入、revision去重／拒絕回退、關閉保存／partial／空譯文不落盤、清除watermark拒絕晚到舊完成段而接受新段、容量上限移除最舊、損壞檔不覆寫、UTF-8與時區日期／原文譯文匯出（多個斷言合併6方法）。
- SpeechLoudness4項：偏小PCM最多4×補強、近靜音不增強、關閉或已足夠響亮時原樣保留、含大峰值語句與400%混音不溢位／不反轉正負號。
- 其餘43項涵蓋DSP、語音格式、來源能力、分段、有限queue、provider取消／401／429、日誌隱私。新測試使用合成語音／虛構文字，沒有真實會議內容、API Key或使用者log。
- 修正舊provider overload測試時序：舊測試收到首個丟frame後立即要求整秒，可能在剩餘處理完成前誤判。新fixture以NO_RESPONSE保持供應商忙碌，等Flow超載事件，驗證丟棄計數上限及沒有第二請求，去掉任意startup sleep；專用ChunkMailbox測試另驗證精確容量。

## 介面與裝置驗收

四頁籤、固定紀錄工具列、搜尋／原文切換、清除確認、四組動作圖示、兩組浮窗stepper、48dp觸控目標與窄螢幕捲動皆已實作；Compose／View能編譯，lint無阻擋錯誤。沒有可操作Android裝置／emulator，不宣稱已做實際視覺、TalkBack、SAF、摺疊／放大字體／旋轉或OEM浮窗驗證。

錄音來源／YouTube／Zoom／Meet、防回灌、實際2×語速、實際音壓、耳機路由、鎖屏與30分鐘運作仍NOT_TESTED_ON_DEVICE。響度數值測量為合成PCM，不是耳機音壓；最高400%與響度增強不能繞過系統音量／安全限制，外部來源原聲仍不可單獨控制。

測試者：更新同CircleCI簽章APK → 開新翻譯 → 紀錄頁確認保存 → 重開App確認仍在 → 匯出TXT核對繁中與原文 → 清除後確認舊片段不回來 → 背景浮窗測播報／字幕stepper與捲動不重置 → 低音量起逐步測響度。細節見 STUDIO_UI_AND_HISTORY.md。

APK debug v2簽章已通過（1 signer）。本機SHA-256：`4a5817bd347504964af7329ae32ba35ec1ca0bc812b2b343e9abd8c68aa28c2f  V2V-0.4.0-studio-debug.apk`。
