# 測試報告 · M0/M1

測試日期：2026-10-14。環境：Linux、OpenJDK 17.0.20.1、Android SDK 35、Gradle 8.13。無 emulator 或 Android 真機；不提供裝置耗時、RAM、耗電或穩定性數據。

## 自動檢查

最終執行 `./gradlew --no-daemon assembleDebug testDebugUnitTest lintDebug`：**BUILD SUCCESSFUL**（2m46s，53 tasks，26 executed / 27 up-to-date）。

| 檢查 | 結果 | 證據 |
|---|---|---|
| assembleDebug | PASS | app/build/outputs/apk/debug/app-debug.apk |
| testDebugUnitTest | PASS | 8 tests / 0 failures / 0 errors / 0 skipped，JUnit XML 附於 docs/evidence |
| lintDebug | PASS | 0 errors / 4 warnings，均為 pinned dependencies 有較新版本提示 |
| APK 簽章 | PASS | apksigner verify，v2 scheme，1 signer（debug） |
| Gradle wrapper | PASS | 官方產生 wrapper，8.13 distribution SHA-256 與官方 checksum 一致 |
| XML / WAV / CircleCI YAML | PASS（靜態） | XML 解析、48k mono PCM16 2秒 WAV、YAML 解析；不是 CircleCI 實際執行 |

本環境未配置 CircleCI 帳號連線，也沒有實際 pipeline 執行結果，設定檔已交付；使用者需在帳號連接 V2V / main。沒有 emulator / device，不執行 connectedDebugAndroidTest。

DSP 測試涵蓋不同頻率波形能量的 SPLIT 隔離與左右交換、雙耳純譯音、gO/gT=0 時辨識量測分支仍前進、limiter 連續性與單調性、30 次切換的相鄰樣本差、downmix headroom / phase cancellation、模式切回增益恢復、外部來源不能升級原聲控制。

## 規格驗收範圍

| 項目 | 結果 | 說明 / 證據需求 |
|---|---|---|
| T01 權限 / 旋轉 / 重啟 | NOT_TESTED_ON_DEVICE | 程式有拒絕與 callback 分支；需真機反覆撤銷 |
| T02 PCM / ASR 分流 | PASS（軟體） / NOT_TESTED_ON_DEVICE | 軟體零增益分支測試；實際服務待測 |
| T03 外部 PCM / NO_SIGNAL | NOT_TESTED_ON_DEVICE | RMS / 3秒無訊號邏輯已實作，不推斷 unsupported |
| T04 第三方來源 | NOT_TESTED_ON_DEVICE | YouTube / Zoom / Meet 版本未知 |
| T05 不可控原聲 UI | NOT_TESTED_ON_DEVICE | 能力單元測試及 UI 條件；需裝置操作驗證 |
| T06 mixer PCM | PASS（軟體） | 實體左右隔離仍 NOT_TESTED_ON_DEVICE |
| T07 gain / 30 次模式切換 | PASS（軟體） | PCM 自動測試；click/pop 主觀試聽未測 |
| T08 真實 TTS / 防回灌 | BLOCKED | 真實 PCM_TTS 尚未接入 |
| T09 路由 / 耳機斷線 | NOT_TESTED_ON_DEVICE | callback 已實作；延遲與外洩風險未量測 |
| T10 停止 / 20 次啟停 | NOT_TESTED_ON_DEVICE | 本版沒有網路結果；音訊資源實測待驗證 |
| T11–T14 浮窗 / 字幕 | BLOCKED | M4 尚未實作 |
| T15 provider / 模型錯誤 | BLOCKED | M2/M5 尚未實作 |
| T16 30分鐘 / 鎖屏 | NOT_TESTED_ON_DEVICE | 無裝置，禁止填 PASS |
| T17 Key / cache / 歷史 | BLOCKED（後續功能） | M1 不保存此資料，無 Key、network 或 TTS cache |
| T18 匯出 / 費用 | BLOCKED | 尚未實作 |
| T19 完整外部來源控制 | BLOCKED | 沒有外部直出關閉且仍輸入的接入證據 |
| T20 真實會議 | BLOCKED | 未指定會議 SDK、授權及版本 |

第三方來源與裝置欄位詳見 CAPTURE_COMPATIBILITY。CI 沒有 emulator job；不能把 unit test 當 instrumentation。

已驗證程式碼 commit：`0a34c0a5b61adda15dbeb9a11857485a44f1fb61`。
