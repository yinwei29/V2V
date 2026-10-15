# PROJECT_HANDOFF · 2026-10-15

版本：0.2.0-m2-preview。GitHub main 已可寫入；使用者已完成 CircleCI 設定。M1 d3fef3a 的 [CircleCI #1](https://circleci.com/gh/yinwei29/V2V/1) 已 success，有APK artifacts。M1原報告保留 docs/TEST_REPORT_M1.md。

## 本次變更

- 使用者實測回報後新增技術 Log：開關保存、每秒電平／計數、權限／模式／路由／啟停／錯誤class，JSONL輪替、SAF匯出、系統分享、清除、Java uncaught error類別；無錄音／字幕／Key、不自動上傳。
- M2工程管線：授權48k mono PCM16 WAV / 外部MediaProjection，en/id/auto ASR whisper-1，gpt-4o-mini指定zh-TW字幕，分段final／有界queue／過載提示／取消／session revision防舊結果。
- Key以Keystore AES-GCM存noBackupFilesDir，表單不進saved state，健康檢查、更新、移除、雲端同意。API Key需使用者在App自行輸入，不在聊天或CI。
- 目前沒有真實Key與我們可直接操作的Android裝置；M2真實provider端到端與語言品質驗收 **BLOCKED_NO_KEY / NOT_TESTED_ON_DEVICE**，不能用local fixture tests當作通過。

## 測試與交付

最終本機 build/test/lint 已通過，28項測試全過，lint 0 errors / 4版本更新提示。證據見 docs/TEST_REPORT.md 與 docs/evidence/m2/。標準 ./gradlew assembleDebug testDebugUnitTest lintDebug。

APK：app/build/outputs/apk/debug/app-debug.apk；scripts/package-apk.sh 依 output-metadata version 產生 deliverables/V2V-0.2.0-m2-preview-debug.apk 和 SHA256SUMS。原始碼 ZIP 不是build輸入，不提交Git。新版本推送後CircleCI自動跑，不需使用者再設定。

APK SHA-256：`7c343cae2022fdc3fb3859d513b1384bd2dc1ec62930744c09efc5a4cbef641f`。

已驗證M2程式碼 commit：`d03a3dba8a6ab122983759b83faf4ea242793dce`。本文件後續更新不改程式碼，最新文件 commit 可用 git log -1 核對。GitHub 推送及 CircleCI 最終狀態仍待查核。

## 手動設定／真機需求（已提早告知使用者）

1. CircleCI啟用已完成，無需Key。M2推送後看對應commit結果，不能沿用M1 success稱M2通過。
2. 安裝新版後，先用Log測試既有擷取；開啟Log→重現→停止→匯出JSONL，附來源版本與耳機步驟。不同debug簽章可能需先匯出log、卸載舊版再裝。
3. 真實字幕：使用者在App輸入自有OpenAI API Key、測連線、選授權48k mono PCM16 WAV、選en/id、勾傳送同意、開始。可能產生費用，不提供尚未查核的金額估算。
4. 收集實際en/id→zh-TW準確度與latency；provider body無敏感log。只有圖片的34.8秒測試視為局部證據，詳見來源矩陣。

## 未完成

真實PCM TTS / M3、M4小圈圈與浮動字幕、離線與模型管理、術語／上下文／速度品質選擇、歷史/SRT匯出、整合來源授權、30分鐘穩定性和20次啟停。外部原聲控制與YouTube/會議版本仍blocked／待測，沒有額外直出關閉的證據。
