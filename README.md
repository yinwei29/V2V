# V2V · Live Audio Translator

Android 10+。目前 **0.3.0-speech-overlay-preview**：音訊擷取／混音、BYOK 雲端字幕、Android PCM 語音、浮動字幕與技術 Log。GitHub: https://github.com/yinwei29/V2V ，CI 使用 CircleCI。

> 使用者已確認 M2 主畫面可見原文與翻譯；M2 CircleCI #2 success。此版加入 M3/M4 工程預覽，語音引擎、浮窗與 Bluetooth 仍需裝置驗收，不能宣稱完整產品完成。離線辨識／翻譯模型尚未交付；YouTube／Zoom／Meet 仍按版本與裝置驗證。

## 測試者操作

1. 開啟「引擎與診斷」確認 **技術事件記錄中**。
2. 選「自有測試音檔」驗證 stereo mixer；440Hz / 660Hz 是原創測試音，不是真實 TTS。
3. 選授權 WAV（**48kHz / mono / PCM16**）或其他 APP 擷取。外部擷取每次都需系統授權，不偷開麥克風。
4. 雲端字幕需先在 App 儲存自己的 OpenAI Key、測試連線，並在主頁勾選音訊／文字傳送同意。選 en / id / auto → 台灣繁體中文。Key 不傳聊天、不放 Git 或 CircleCI。
5. 雲端翻譯後自動嘗試離線台灣中文播報；若出現語音錯誤，按「Android 文字轉語音設定」安裝離線台灣中文語音、選擇引擎，再重新開始。語音引擎不可用時保留字幕。
6. 按「設定浮動字幕權限」開啟上層顯示 → 返回 → 開始 →「顯示浮動字幕／小圈圈」。小圈圈可拖曳吸附，點一下開控制；隱藏字幕不停止翻譯；通知可恢復。混音頁可改語速、字級、行數、背景透明度與關閉播報。
7. 不勾雲端同意時不送 ASR／MT。外部來源不重播原聲副本；只能調整本 APP 譯音，不能靜音來源 APP。
8. 重現問題後停止 → 診斷 → **匯出 Log／分享 Log**，把 JSONL 附回聊天，補上來源版本、耳機和步驟。Log 不含錄音／字幕／Key，不自動上傳；詳見 [DIAGNOSTIC_LOGS](docs/DIAGNOSTIC_LOGS.md)。

字幕是分段 final，最長 10秒／600ms 安靜斷句，不宣稱串流 partial。provider 逾時或過載會顯示錯誤／跳過，不無限排隊。外部來源原聲不可控，相關滑桿與純翻譯原聲控制禁用，不改系統媒體音量。

## 建置

AGP 8.9.2 / Kotlin 2.1.20 / Gradle 8.13 / Java 17+ / compile & target SDK35 / min29。Gradle wrapper 附官方 SHA-256；相依版本有 lockfile。SDK 35 / Build Tools 35.0.0；local.properties 設 sdk.dir（勿提交）。

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
./scripts/package-apk.sh
```

APK：app/build/outputs/apk/debug/app-debug.apk；交付副本 deliverables/V2V-0.3.0-speech-overlay-preview-debug.apk，SHA-256 見 deliverables/SHA256SUMS。Debug 簽章在不同 CI 環境可能不同；無法更新時需移除舊版再裝（設定／Log 會清除，先匯出）。

## CircleCI

已啟用，推送 main 會 build / unit test / lint。進 Pipeline → verify → android-verify → Artifacts 下載 APK、checksum、報告。CircleCI #1 對應 M1 d3fef3a 已 success；新版本請以該 commit 的 pipeline 為準。本版 CI 不放雲端 Key，網路測試只連 local MockWebServer。

cimg/android:2025.04 / medium，cache 使用 dependency lockfile 與 wrapper checksum。CI 另外保存預設 debug.keystore 快取，供後續同來源測試 APK 穩定更新；這不是正式 release key，也不是雲端 API Key。快取清除或本機/CI 簽章不同時仍可能需要重裝。Artifact 保留期限由 CircleCI 帳號 Usage Controls 設定，建議 3 天；不在 YAML 假定 retention 已生效。

## 限制與文件

Key 是 AES-GCM ciphertext，AES 金鑰在 Android Keystore；排除 backup / device transfer。Key 表單不保存到 saved state，Activity 禁止 screenshot / app preview。逐字稿只在本次記憶體內留最近 200 段，沒有 Room 歷史或匯出。Log 是獨立的技術資訊，最多 3×512KiB，開關可持久化、可刪除。

無真機不能證明第三方擷取、Bluetooth 實體左右隔離、鎖屏、30分鐘與20次啟停。詳見 [PROJECT_HANDOFF](PROJECT_HANDOFF.md)、[TEST_REPORT](docs/TEST_REPORT.md)、[PROVIDER_MATRIX](docs/PROVIDER_MATRIX.md) 與來源矩陣。
