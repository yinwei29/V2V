# 0.4 工作台、紀錄與響度

## 操作

- 翻譯：上方字幕卡、主要開始／結束按鈕、音訊來源／語言與雲端同意；浮窗授權與顯示集中於下方卡片。
- 紀錄：最近的完成譯文優先顯示，含時間與語言。上方固定工具列的下載圖示匯出全部紀錄為UTF-8 TXT，垃圾桶清除前確認。可搜尋、顯示／隱藏原文、選取複製文字。關閉自動保存只影響新片段，既有紀錄仍可查看或清除。
- 聲音：播放開關、0.75–2×基準語速、自動追趕、0–400%手動增益、語音響度增強、譯音聲道及字幕外觀。另有系統音量設定入口。其他App原聲不受控制，不把譯音左／右聲道當作原聲隔離。
- 設定：Key／語音引擎；技術Log與工程數據收合在診斷卡片，減少主流程干擾。
- 浮窗：小圈圈點開，播報／字幕切換；同一數值只有一列減號、數值、加號。底部圖示列為主畫面、隱藏、暫停／恢復、結束。字幕閱讀模式穿透限制與權限降級沿用先前版本。

## 儲存與清除

明確使用者要求保存翻譯紀錄後，預設保存final原文及譯文在noBackupFilesDir/history/translations.json，不保存來源錄音。最多1,000段且4MB，超出移除最舊記錄。私有暫存寫入、fsync、atomic rename後才發布列表。以session/id/revision去重，不由UI重播ledger；清除記住目前session完成段的watermark，晚到的舊完成callback不會復活已清除紀錄。新完成段在保存開啟時仍會追加。檔案損壞不覆寫原檔，顯示錯誤，使用者可清除後恢復。現有技術Log與Key備份／分享限制保持，譯文不進Log。歷史TXT使用SAF使用者選定位置；清除App內紀錄不會刪除已匯出的外部TXT。

更新前未保存的舊版字幕無法從技術Log還原；新紀錄從本版完成的翻譯開始。沒有伺服器同步／Room／SRT／自動雲端備份。

## 響度

固定百分比放大無法判斷引擎輸出本身是否太小。新SpeechLoudness先量測每個短句的PCM RMS：低於0.0015不增強；其餘偏小語音朝0.18 RMS（約-15dBFS）補強，最多4×，已足夠響亮的片段不減弱。提高增益的峰值經soft-knee limiter，不用硬截斷；隨後仍由既有單一mixer處理手動增益與左右聲道。最高400%手動增益與響度增強是不同階段，不代表實際音壓倍數，也無法突破手機／耳機硬體或安全音量上限。TTS_PCM_READY新增INPUT_RMS／OUTPUT_RMS／NORMALIZATION_GAIN，方便區分合成偏小與實際輸出路由問題。

## 設計依據與待測

採Android Material 3的主要底部導覽、頂部工具列、卡片分組、圖示動作，非仿製任何第三方App。四頁保留文字標籤，工具圖示都有contentDescription，按鈕目標至少48dp；主要畫面寬度上限760dp並可捲動，紀錄以LazyColumn避免一次產生1,000個卡片。

官方參考（2026-10-15讀取）：
- https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-and-nav-patterns
- https://developer.android.com/design/ui/mobile/guides/components/material-overview

無可操作Android裝置／emulator，本次不宣稱實際視覺、TalkBack、手機／摺疊、SAF picker、OEM浮窗及音壓已通過。需要使用者在主力手機驗證；自動測試只涵蓋可驗證的保存／匯出／清除與PCM響度性質。
