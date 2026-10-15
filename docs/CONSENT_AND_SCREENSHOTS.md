# 音訊授權記憶與截圖 · 0.5.1-start-fix

使用者回報Gemini無法翻譯後，檢查提供的Log。檔案包含前版舊工作階段，故以最新APP_START所屬runId分析，沒有把較早64段成功翻譯當成Gemini成功。最新版本0.5.0，所選GEMINI，SESSION_START.CLOUD_ENABLED=0；該次共約81秒、4,043 frames及3,881,280 samples，末次RMS約0.208，證明曾收到非靜音PCM。該次沒有HTTP事件、辨識原文或譯文發布，因此不是已證實的Gemini API拒絕／Key錯誤，而是沒有啟用雲端管線。原始Log僅在私有附件目錄分析，不提交Git。

原因：原介面把「開始翻譯」接到可選cloudConsent，未勾選仍啟動擷取；同意只存Compose畫面狀態，重開程序或切換引擎就不保留。使用者明確要求僅勾一次、以後記住，並要求App內可以截圖。

修正：
- cloud_consent持久化到既有DataStore，首次預設false。勾選一次後，重開App、停止／重啟翻譯與切換雲端引擎均保留，手動取消保存false；文案明示同意所選雲端引擎。沒有每次開啟或開始的確認框。
- 等儲存設定及所選引擎Key狀態載入後才開放啟動；未勾選顯示只需一次的提示，停用實際翻譯啟動。Key缺失時按鈕直接前往設定。
- 服務另檢查非測試音訊必須有cloudConsent，否則顯示錯誤並停止，不會默默啟動只擷取的模式。測試tone不需Key／雲端授權，按鈕明示音訊測試。
- 移除Activity的FLAG_SECURE，允許各頁截圖。已儲存Key不回顯，輸入仍以PasswordVisualTransformation遮蔽，不改Key加密及Log隱私。

持久化回歸測試使用真實PreferenceDataStore檔案，結束原scope後重開檔案，驗證首次未同意、勾選後重啟及provider切換保留、手動取消後重啟保留。不是只測記憶體boolean。沒有新增Android截圖自動化測試；目前無真機／emulator，截圖、系統擷取授權及完整Gemini雲端翻譯仍需手機驗證。

測試者：安裝新版→設定Gemini Key→主頁音訊授權勾一次→開始翻譯→確認Log有CLOUD_ENABLED=1及HTTP事件→結束並重開App→確認仍勾選→再次開始→必要時匯出Log；最後測試取消勾選後重開仍未勾。各頁均可手動截圖。Android系統每次音訊／螢幕擷取授權仍照系統要求，不由本項設定略過。
