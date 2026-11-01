# V2V · Live Audio Translator

**專案範圍**：手機播放音訊及音訊檔轉錄／即時翻譯；Ju／LinguaBridge的對話與文件翻譯是另一個專案。功能與已支援限制見 [範圍核對](docs/PRODUCT_SCOPE.md)。 / V2V translates playback and imported audio; Ju is a separate conversation/document app.

Android 10+。目前 **0.9.0-asr**：音訊擷取／混音、BYOK 雲端字幕、Android PCM 語音、浮動字幕與技術 Log。GitHub: https://github.com/yinwei29/V2V ，CI 使用 CircleCI。

> 使用者已確認 M2 主畫面可見原文與翻譯；M2 CircleCI #2 success。此版加入 M3/M4 工程預覽，語音引擎、浮窗與 Bluetooth 仍需裝置驗收，不能宣稱完整產品完成。離線辨識／翻譯模型尚未交付；YouTube／Zoom／Meet 仍按版本與裝置驗證。

## 0.9 · 專用語音辨識 / Specialist speech recognition

設定→引擎與金鑰→Google Gemini，新增 **Transcribe → Flash** 及 **Transcribe → Flash-Lite**。以同一Google Key使用`gemini-3.5-transcribe`先辨識，再用所選Flash模型翻譯文字；原文會先顯示，翻譯失敗保留本次原文。原有Flash／Flash-Lite單次音訊請求模式仍保留，不自動變更已選模型／帳號／費用選擇。詳見 [核心設計、官方契約及限制](docs/SPECIALIST_ASR.md)。

New explicit specialist options use dedicated Gemini Transcribe ASR followed by text MT. This isolates recognition from translation and permits inspecting the source first; it is not a proven accuracy improvement or streaming implementation. Two requests may add cost/latency. Same Preview package/signing pin as0.8.1; no uninstall/data migration required to update Preview.

## 0.8.1 · 安裝修復 / Installation recovery

測試APK現在顯示為 **V2V Preview**（`com.yinwei.v2v.preview`），可與舊V2V並存，避免舊簽章衝突，不需要移除舊版。兩個安裝的設定、Key、歷史各自獨立；Preview首次需重新設定Key／權限，舊資料仍保留在舊V2V。翻譯核心維持0.8.0，不修改Ju。下載Releases中的`-preview.apk`；開啟APK並依Android要求允許安裝來源。詳見 [安裝與簽章](docs/INSTALLATION.md)。

Preview installs alongside legacy V2V; no automatic data migration or old-app removal. CI now pins the public signer fingerprint and verifies the actual APK signature/package/version. Missing/changed signing keys stop the build instead of creating another incompatible update. Protected signing-key backup still requires CircleCI project administration; cache alone is not durable key storage.

## 0.8 · 翻譯核心 / Translation core

移除Gemini固定至少4秒的收集等待；所有實際雲端管線在600ms安靜時斷句，連續語音最長4秒便送出。OpenAI文字翻譯及Gemini音訊翻譯可參考最近兩段原文（每段最多1,200字元），只翻譯目前片段、不重播前文。失敗／漏段／長停頓／新工作階段清除上下文。字幕與語音沿用同一個成功譯文，不引入另一份語音翻譯。詳見 [核心調整及測試](docs/TRANSLATION_CORE.md)。

Cloud chunks now endpoint on600ms silence with a4s ceiling; Gemini no longer waits for a4s minimum. Bounded preceding source context helps terminology across chunks. This is segmented final output, not streaming or a guaranteed4s end-to-end latency. Shorter chunks and context can increase request/token costs. Real accuracy, throughput and listening still need device/API evaluation.

## 0.7.1 · SRT 字幕匯出

紀錄頁匯出可選TXT或SRT；SRT逐工作階段選擇，支援譯文或雙語，保留來源時間軸、最新修訂與UTF-8。詳見 [SRT操作與限制](docs/SRT_EXPORT.md)。Android實機與字幕播放器相容性仍待驗收。

## 0.6 · 多語言與服務恢復

來源及目的語言均可選：繁中、簡中、英文、印尼文、日文、韓文、越南文、泰文、馬來文、西班牙文、法文、德文、葡萄牙文、印地文、阿拉伯文；來源另有自動偵測。設定會記住，工作中固定語言。字幕、紀錄與TXT匯出標示實際目的語言，舊紀錄預設繁中。離線播報選擇目的語言聲線；缺少相應離線聲音時提示安裝，保留字幕，不改用雲端TTS。

Gemini可選3.5 Flash-Lite（預設，MINIMAL）或3.8 Flash（LOW）。503／5xx／408／網路逾時在有限HTTP重試後，略過未完成段、退避2秒及4秒處理後續音訊，最多連續3段失敗後明示暫停；400／Key錯誤／權限或429額度錯誤不在管線內持續重試。UI及Log區分恢復中與真正暫停，不自動切換供應商。

來源統一為「手機播放音訊」，不依App名稱或UID白名單限制，只排除V2V自己的輸出防止回灌。Android仍只允許擷取符合usage及capture policy的音訊，通話或受保護內容不保證可取；不偷偷改用麥克風。詳細規格與手機測試見 [LANGUAGES_AND_RECOVERY](docs/LANGUAGES_AND_RECOVERY.md)。

## Gemini REST 格式修正

修正generateContent的REST列舉值：responseFormat.text.mimeType使用APPLICATION_JSON，thinkingConfig.thinkingLevel使用LOW。前版送出的MIME字串與小寫thinking不符Google discovery列舉定義，可能造成400。增加獨立官方enum契約測試，以及安全的參數／Key／專案錯誤分類；實際Google帳號仍需手機驗證。

## 記住授權與允許截圖

音訊授權只需勾一次，會保存到裝置設定；重開App、重新翻譯及切換引擎都會保留，可手動取消。未勾選時明示提示並停用開始翻譯，不再誤啟動為只擷取音訊的模式；未設定Key時可直接前往設定。測試音訊仍可單獨使用。依使用者要求，App內各頁允許截圖，Key欄位仍遮蔽。

Android本身的音訊／螢幕擷取授權仍依系統每次要求，這與App內保存的雲端同意不同。

## Gemini API

在「設定 → 引擎與金鑰」選擇 OpenAI 或 Google Gemini，各自加密保存 Key。Gemini 使用所選的 Flash-Lite 或 Flash，接收分段 WAV 辨識後翻譯成台灣繁中；字幕、紀錄、浮窗與離線播報共用既有流程。切換引擎需結束本次翻譯；音訊授權依已儲存的勾選套用。不自動切換供應商。設定與限制見 [GEMINI_SETUP](docs/GEMINI_SETUP.md)。

## 翻譯工作台

- 底部四個頁籤：翻譯、紀錄、聲音、設定。使用一致向量圖示、圓角卡片與高對比字幕；紀錄頁頂部提供文字匯出與清除。
- 完成的譯文及原文只保存在手機私有noBackup目錄，最多1,000段／4MB，可關閉自動保存、搜尋、切換原文顯示，並匯出UTF-8 TXT。清除有確認，進行中已完成的舊片段不會重新灌回。
- 浮窗分播報／字幕兩組；語速、增益、字級、行數、背景與寬度均為「一列數值＋減／加」，不用捲過重複大按鈕。設定值更新不重建面板，保留捲動位置。
- 新增可關閉的語音響度增強：偏小的合成語音朝0.18 RMS補強，最多4×（約12dB），近靜音不放大；手動譯音增益上限400%，仍經limiter，不改手機媒體音量或其他App原聲。這不是保證實際聲壓四倍，設備／系統安全上限仍有效。
- 字幕與音量實際效果、摺疊／放大字體／浮窗觸控仍需裝置複測；建置與單元測試不能代替Android真機驗收。

## 外部 App 主要流程

- 長譯文分成短句接續合成，避免舊版超過10秒時整段漏播。三段待播與10秒PCM上限保留，PCM滿先等播放釋出；真正過載與合成失敗分別計數。
- 語速0.75–2×、可關閉的自動追趕、譯音增益0–400%；引擎實際速度與音壓需裝置驗證。
- 浮窗可直接加減語速與譯音增益；初始來源改為其他App。外部來源可選V2V譯音雙耳／左／右，不能操控來源App的原聲、單獨靜音或隔離它的聲道。
- Samsung可手動嘗試 [Sound Assistant 個別應用程式音量](https://galaxystore.samsung.com/detail/com.samsung.android.soundassistant)，來源先調低、V2V保持較高。部分機型不支援；會議／擷取是否受連動影響仍需實測。App不宣稱已透過該設定取得原聲控制能力。

## 測試者操作

1. 開啟「設定 → 診斷與測試」確認 **技術事件記錄中**。
2. 選「測試音訊」驗證 stereo mixer；440Hz / 660Hz 是原創測試音，不是真實 TTS。
3. 選授權 WAV（**48kHz / mono / PCM16**）或其他 APP 擷取。外部擷取每次都需系統授權，不偷開麥克風。
4. 雲端字幕需先在 App 儲存所選引擎的 OpenAI 或 Gemini Key、測試連線，並在主頁勾選音訊／文字傳送同意。選來源與目的語言，來源可自動偵測。Key 不傳聊天、不放 Git 或 CircleCI。
5. 雲端翻譯後自動嘗試所選目的語言的離線播報；若出現語音錯誤，按「Android 文字轉語音設定」安裝目的語言的離線語音、選擇引擎，再重新開始。語音引擎不可用時保留字幕。
6. 按「設定浮動字幕權限」開啟上層顯示 → 返回 → 開始 →「顯示浮動字幕／小圈圈」。小圈圈可拖曳吸附，點一下開控制；隱藏字幕不停止翻譯；通知可恢復。混音頁可改語速、字級、行數、背景透明度與關閉播報。
7. 不勾雲端同意時不送 ASR／MT。外部來源不重播原聲副本；只能調整本 APP 譯音，不能靜音來源 APP。
8. 重現問題後停止 → 診斷 → **匯出 Log／分享 Log**，把 JSONL 附回聊天，補上來源版本、耳機和步驟。Log 不含錄音／字幕／Key，不自動上傳；詳見 [DIAGNOSTIC_LOGS](docs/DIAGNOSTIC_LOGS.md)。

字幕是分段 final，最長 4秒／600ms 安靜斷句，不宣稱串流 partial。provider 逾時或過載會顯示錯誤／跳過，不無限排隊。外部來源原聲不可控，相關滑桿與純翻譯原聲控制禁用，不改系統媒體音量。

## 建置

AGP 8.9.2 / Kotlin 2.1.20 / Gradle 8.13 / Java 17+ / compile & target SDK35 / min29。Gradle wrapper 附官方 SHA-256；相依版本有 lockfile。SDK 35 / Build Tools 35.0.0；local.properties 設 sdk.dir（勿提交）。

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
./scripts/package-apk.sh
```

APK：app/build/outputs/apk/debug/app-debug.apk；交付副本 deliverables/V2V-0.9.0-asr-preview.apk，SHA-256 及驗證摘要見同次CircleCI Artifacts的SHA256SUMS與verification.json。Preview簽章會比對固定指紋，簽章缺失或不符會停止交付；不要為了安裝而直接移除有資料的舊V2V。

## CircleCI

已啟用，推送 main 會 build / unit test / lint。進 Pipeline → verify → android-verify → Artifacts 下載 APK、checksum、報告。CircleCI #1 對應 M1 d3fef3a 已 success；新版本請以該 commit 的 pipeline 為準。本版 CI 不放雲端 Key，網路測試只連 local MockWebServer。

cimg/android:2025.04 / medium，cache 使用 dependency lockfile 與 wrapper checksum。CI 另外保存預設 debug.keystore 快取，供後續同來源測試 APK 穩定更新；這不是正式 release key，也不是雲端 API Key。快取清除或本機/CI 簽章不同時仍可能需要重裝。Artifact 保留期限由 CircleCI 帳號 Usage Controls 設定，建議 3 天；不在 YAML 假定 retention 已生效。

## 限制與文件

Key 是 AES-GCM ciphertext，AES 金鑰在 Android Keystore；排除 backup / device transfer。Key 表單不保存到 saved state，App 允許 screenshot / app preview，Key欄位仍以密碼遮蔽。即時字幕仍保留最近200段於記憶體；使用者要求新增的翻譯紀錄另存手機私有目錄，可停用／匯出／清除，新增按工作階段的SRT匯出。Log 是獨立的技術資訊，最多 3×512KiB，開關可持久化、可刪除。

無真機不能證明第三方擷取、Bluetooth 實體左右隔離、鎖屏、30分鐘與20次啟停。詳見 [PROJECT_HANDOFF](PROJECT_HANDOFF.md)、[TEST_REPORT](docs/TEST_REPORT.md)、[PROVIDER_MATRIX](docs/PROVIDER_MATRIX.md) 與來源矩陣。
