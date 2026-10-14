# PROJECT_HANDOFF

日期：2026-10-14。依規格第16節，首次只推進 M0、M1；使用者指定 CircleCI 取代規格預設 GitHub Actions。

## 目前交付

- M0：Compose、Koin、DataStore、服務與狀態骨架、CircleCI、README；建置、8 項 DSP 測試與 lint 已通過。
- M1：自有 WAV、MediaProjection/AudioRecord、PCM RMS、SourceCapabilities、StereoMixer、AsrProbe、路由診斷、8 個有波形驗證的單元測試。
- M1 來源 Gate：軟體混音測試 PASS；外部來源與完整目標 blocked。無真機不可標完成。
- M2–M6 未完成：真實 ASR/MT/TTS、en/id→zh-TW、浮窗、字幕外觀、Key、離線模型、歷史匯出、來源整合與持續運作測試。

## 驗證與檔案

建置與測試結果見 docs/TEST_REPORT.md。APK 已產生，路徑 app/build/outputs/apk/debug/app-debug.apk；scripts/package-apk.sh 已複製 deliverables 並附 SHA-256。

APK SHA-256：`52c1f053ab3265548cd87ebde51d9206995e5ed0d1b7e4adb5934a3756ef7598`。Debug v2 簽章已驗證。

Git：由空倉庫建立工程；首次實際 commit 建立後補記程式碼 SHA。
CircleCI：設定檔已提供，但專案仍需帳號內 Set Up Project；沒有假造 pipeline PASS。

## 阻擋與下一步

1. build/test/lint 與打包已完成，推送 main 後，CircleCI 使用 main 的 .circleci/config.yml 啟用。
2. 真機（主力 Samsung + 一台低階機）驗證 T01–T10、來源矩陣、20 次啟停與 30 次切換；記錄真實 OS / 來源版本。
3. M2 選定 provider、確認條款與費率、Key 安全儲存與雲端同意，再用授權音檔驗證 en/id→zh-TW。
4. M3 Android synthesizeToFile / 解碼 / 抗混疊重採樣 / 單一 mixer，不以測試 fixture 代替真實 TTS。
5. M4 overlay、M5 合法來源與離線可用鏈路、M6 30 分鐘穩定性，按原規格逐步推進。

費用／授權：M1 無外部付費服務，CircleCI 建置可能耗用帳號 credits。後續 provider / meeting SDK / model 授權未選定，沒有代購或 Play 發佈。
