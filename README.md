# V2V · Live Audio Translator

Android 10+ 即時音訊翻譯工程。依 V2 規格第 16 節，首次交付 **M0 / M1 音訊驗證版**；CI 使用 **CircleCI**。

> 此版沒有 ASR、MT、真實 TTS、小圈圈或浮動字幕。440Hz／660Hz 為原創測試音檔；不代表翻譯成功。YouTube、Zoom、Meet 未經真機驗證，不宣稱支援。

## 已實作

- Kotlin / Compose / Material 3，Koin 注入、DataStore 模式與增益設定。
- 使用者由可見 Activity 啟動前景服務；通知停止；START_NOT_STICKY，不自動錄音。
- MediaProjection 每次新授權、AudioPlaybackCapture / AudioRecord PCM16 mono，排除本 APP UID。不使用麥克風 fallback。
- 20ms PCM frame、單調時間、RMS 電平、有效音訊驗證、3 秒 NO_SIGNAL（不假判 unsupported）。
- 自有 48kHz 測試 WAV，ASR 量測分支在增益之前；外部來源不重播副本。
- 單一 stereo AudioTrack：SPLIT、TRANSLATION_ONLY、MIX，左右交換、50ms ramp / crossfade、soft-knee limiter。
- 路由回報、underrun 計數、音訊焦點／耳機移除暫停本 APP 輸出與手動恢復。
- 能力限制：外部來源禁用原聲增益與完整混音模式；不改系統媒體音量。

## 建置

版本鎖定：AGP 8.9.2 / Kotlin 2.1.20 / Gradle 8.13 / Java 17+，compile / target SDK 35，min SDK 29。
Android Studio 或安裝 Android SDK 35 / Build Tools 35.0.0，`local.properties` 設 `sdk.dir`（不要提交）。

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
./scripts/package-apk.sh
```

APK：`app/build/outputs/apk/debug/app-debug.apk`；交付副本：`deliverables/V2V-0.1.0-m1-debug.apk`，SHA-256：`deliverables/SHA256SUMS`。Debug APK 需允許自行安裝；不適用 Play 發佈。不同 CI 環境的 debug 簽章可能不同；若無法覆蓋更新，移除舊版後重裝（設定會清除）。

## CircleCI

設定：`.circleci/config.yml`，執行 build / unit tests / lint，保存 APK、checksum、JUnit XML、HTML 報告。
CircleCI Projects → V2V → **Fastest: Use .circleci/config.yml in my repo** → 分支 `main` → Set Up Project。
本版不需雲端 API Key 或簽署金鑰。Artifact 保留期限由 CircleCI Plan Usage Controls 設定（建議 3 天），不能由此 YAML 保證。
CI 設定已提供；實際執行結果請以 CircleCI pipeline 為準，未連接專案不會自動有成功結果。

## 使用與驗證

1. 選自有測試音檔、開始。建議戴 stereo 耳機，從低音量開始；音調不是人聲。
2. 在混音控制切换三模式，譯音 660Hz、原聲 440Hz。原聲為 0 時診斷的 ASR 量測樣本應繼續增加。
3. 停止後選其他 APP 音訊，按系統提示授權，再在外部 APP 播放使用者有權存取的內容。
4. 診斷只報 PCM 電平，沒有字幕；安靜／來源拒絕都可能為 NO_SIGNAL。外部聲音不被本 APP 靜音。
5. 通知可停止；耳機移除停本 APP 音訊。重新接入後從主頁確認恢復。

無裝置的建置／DSP 測試不能證明第三方來源、藍牙隔離、鎖屏存活或 OEM 行為。

完整限制、測試證據及後續工作見 [PROJECT_HANDOFF](PROJECT_HANDOFF.md) 與 [docs](docs/)。
