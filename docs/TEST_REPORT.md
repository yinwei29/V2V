# 測試報告 · 0.5.0-gemini

2026-10-15。本機 assembleDebug / testDebugUnitTest / lintDebug 通過，69 項測試，0 failure/error/skipped；最後完整驗證 2m 57s。lint 0 errors/fatal，10 warnings。原始輸出、JUnit及lint見 evidence/gemini/；前版報告與證據保留於下方與 evidence/studio/。本版 CircleCI 請以 main 的本次提交狀態為準。

新增16項Gemini測試：WAV解碼與原始PCM一致、固定模型路徑與header驗證、台灣繁中與結構化JSON、用量缺失未知、無語音空字串、空譯文錯誤、截斷／安全阻擋／錯誤格式／長度限制、thought不當字幕、拒絕工具輸出、HTTP 400/401/403/404不重試且隱藏body、429僅再試一次、healthcheck不傳音訊或生成、退避與body讀取可取消、完整pipeline回寫revision2及呼叫播報callback、Log不含Key／字幕。原有53項完整回歸，包括OpenAI原路徑與取消、DSP、紀錄、語音等。

沒有真實Gemini Key、Google生成呼叫或Android真機／emulator。模擬成功只證明本機請求／解析與管線邏輯，不證明帳號可用、模型品質、延遲、實際播報、Keystore或UI操作。手機測試按 GEMINI_SETUP.md 操作，既有裝置限制仍適用。

本機交付 checksum：`16bedb4654f25a6d59392835a8b25bd3208fb4649f894c174a05949d1e42fbf1  V2V-0.5.0-gemini-debug.apk`。CircleCI產物各用自己的checksum，避免混淆不同建置／簽章。

---

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
