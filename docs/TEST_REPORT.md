# 測試報告 · 0.3.1-speech-fix

2026-10-15。本機 assembleDebug testDebugUnitTest lintDebug **BUILD SUCCESSFUL，2m1s**。JUnit與build/lint輸出見 evidence/speech-fix/。

- 43項測試，0 failure/error/skipped。Mixer8、PCM3、ExternalTranslation2、Segmentation7、Provider10、Journal4、ChunkMailbox3、SpeechPlan6。
- lint：0 errors，8 warnings（既有依賴版本與KTX／本地化建議）。
- APK：versionCode5，0.3.1-speech-fix；apksigner verify通過，debug v2，1 signer。
- CI：main推送後，以該commit的CircleCI狀態為準。M3 #3已success，不能代表本版CI。

## 本次回報與根因判讀

使用者本次0.3測試：16個final翻譯、3個TTS_PCM_READY，其餘13段後出現TTS_ERROR，未出現TTS_DROPPED，輸入丟棄0、underrun0。成功譯文較短，8字／1.67秒、43字／8.55秒、51字／9.73秒；較長段多數在合成後很快ERROR。舊程式將整個譯文一次合成，超過10秒時整段捨棄且只顯示一般錯誤，因此高度符合超長合成漏播；舊log不含詳細錯誤enum，不能單憑log排除每個引擎錯誤。原始JSONL與裝置識別資訊不提交Git。

另有大量同一runId的APP_START／狀態重設，符合DataStore每次設定變更重發相同診斷開關值；現加distinctUntilChanged，避免調語速／外觀造成事件風暴。

## 回歸證據

- 66字、13.75秒的合成假PCM，分片進入10秒上限的queue，容量滿先讀取釋放，再接續加入；660000個有聲sample完整且按序輸出，不整段捨棄。
- Unicode／emoji、數字、標點、空白與普通英文詞不遺失；每片<=18code points；語速手動可達2×，自動追趕上限2×、關閉時尊重手動值。
- 三個翻譯段在全部短句產生／播放完以前保留名額；清除後晚到PCM不接受，新段可處理。
- 外部譯音BOTH/LEFT/RIGHT軟體PCM驗證，來源分支始終0，不錯誤賦予原聲控制能力；200%譯音增益與原聲20%互不干擾，limiter不溢位。
- Android語音callback、超長再分短重試、Sound Assistant、浮窗按鈕及實際2×聲速仍NOT_TESTED_ON_DEVICE，不以純JVM測試冒充真機。

## 裝置待測

以其他App為主要流程：YouTube、指定版本Zoom／Meet分別測；外部原聲仍無獨立靜音／左右隔離權限。個別App音量須使用者在支援的Samsung Sound Assistant手動設定，可能受來源及通話路由限制。先測短影片、長段、不停說話、背景浮窗加減語速／音量，再測會議與耳機斷線；聲線、漏播改善與可懂度需使用者複測，無受控數據不宣稱完成。
