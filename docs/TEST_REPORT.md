# 測試報告 · 0.3.0-speech-overlay-preview

2026-10-15，OpenJDK17 / Gradle8.13 / Android SDK35。最終 assembleDebug testDebugUnitTest lintDebug **BUILD SUCCESSFUL，2m23s**。原始輸出見 evidence/m3/。

| 檢查 | 結果 | 範圍 |
|---|---|---|
| Debug APK | PASS | versionCode4 / 0.3.0-speech-overlay-preview |
| JVM 單元測試 | PASS | 37 tests，0 failure / error / skipped |
| lint | PASS | 0 errors，7 warnings：4既有套件更新與3 KTX／本地化建議 |
| Debug 簽章 | PASS | apksigner verify，v2 scheme，1 signer |
| M2 CircleCI #2 | PASS | bdfdcc1，已查核 success |
| 本版 CircleCI | 推送後查核 | 以本版 commit 的 ci/circleci 狀態為準 |

37項為：Mixer8、Segmentation7、Provider10、Journal4、ChunkMailbox3、SpeechPcm5。新增測試驗證 PCM16 stereo downmix 不溢位、24k→48k長度與插值、拒絕未支援降採樣、FIFO／三段及10秒上限、清除後無舊音訊、字幕revision2與log僅metadata、短chunk backlog與長chunk交付。Provider只用local MockWebServer，不能代表真實語音品質。

## 使用者既有 M2 回報

使用者提供技術JSONL，該次約47.5秒，2364個輸入frame，4次ASR HTTP200（其中一次空結果）、3次MT HTTP200，跳過約1.12秒輸入。使用者隨後確認主畫面看得到原文與翻譯，缺少的是播報與外部浮動字幕。因此保留字幕可見性與backlog改善，並新增M3/M4預覽。這是部分使用者證據，不是我們執行的受控語言或來源驗收；原始log與字幕不提交Git。

## 裝置驗收

| 項目 | 狀態 |
|---|---|
| Android語音引擎、台灣中文聲線、數字／英文詞／印尼姓名 | NOT_TESTED_ON_DEVICE |
| 真實譯音混音、左右隔離、防回灌 | NOT_TESTED_ON_DEVICE |
| 浮窗權限、觸控、閱讀／調整、縮放、旋轉、重啟位置 | NOT_TESTED_ON_DEVICE |
| 耳機／Bluetooth斷線、立即暫停／flush、恢復無舊句 | NOT_TESTED_ON_DEVICE |
| 20次啟停、30分鐘、鎖屏、OEM背景限制 | NOT_TESTED_ON_DEVICE |
| YouTube／Zoom／Meet版本與完整原聲控制 | 未驗證／完整整合仍BLOCKED |
| 離線ASR／MT模型、歷史／SRT、術語／上下文 | 後續功能未交付 |

Android12+閱讀模式為通過系統浮窗觸控安全限制，降低整窗alpha到系統上限，文字會略透明；普通模式背景alpha與文字描邊獨立。完整外觀色票／行距／描邊調整尚未完成。M3/M4是可測工程預覽，沒有將缺少裝置證據的里程碑標為完整PASS。操作見 SPEECH_AND_OVERLAY.md。
