# PROJECT_HANDOFF · 0.4.0-studio

本次依使用者要求新增翻譯紀錄頁、TXT匯出、清除、整體介面與圖示重整、緊湊浮窗與更強譯音響度。使用者主流程仍為其他App／背景翻譯。原先0.3.1遠端ba14a8a已通過CircleCI #4；本次main推送後需看本版CI。

## 交付

- 四頁：翻譯、紀錄、聲音、設定。字幕深色高對比卡、動作區、來源／語言卡、折疊診斷，原文／譯文歷史從主畫面移到紀錄頁。共用向量圖示、launcher圖示與透明通知圖示。
- 紀錄存noBackupFilesDir/history/translations.json，最多1,000段／4MB；預設保存為使用者明確要求，頁面可關閉自動保存。每個final回呼保存一次，revision去重；fsync與atomic rename後發布StateFlow。清除含確認與目前session完成段watermark，避免晚到callback復活舊紀錄；未來新片段仍保存。損壞檔不覆寫並提示。
- 固定紀錄頁工具列匯出全部UTF-8 TXT／清除；搜尋、原文顯示切換、文字選取。SAF由使用者選位置，不自動分享，上版未保存的字幕無法從技術Log還原。
- 浮窗播報／字幕分類，語速、增益、字級、行數、背景、寬度每項只有單列減／值／加；更新值不重建整個面板，保留捲動。底部主畫面／隱藏／暫停／結束圖示；讀取／拖曳與權限降級保留。
- 響度增強可關閉：偏小PCM補至0.18 RMS，最多4×，近靜音不放大，soft-knee處理峰值；之後手動增益上限400%及單一mixer。數位增益不是實際聲壓倍數，不改系統媒體音量或其他App原聲。Log新增RMS前後值與增強倍率，不含字幕文字。

## 驗證

本機53項單元測試及build/lint通過。證據與原始輸出見 docs/evidence/studio/、docs/TEST_REPORT.md。最後APK為deliverables/V2V-0.4.0-studio-debug.apk，SHA256SUMS依打包產生；CI與本機簽章／環境不同，各自比對自己的checksum。

## 使用者設定／待測

不需重設CircleCI、Key或已授權浮窗。新版本起開始保存完成翻譯，可在紀錄頁關閉；匯出TXT含原文與譯文，僅使用者選定的外部檔案。清除App紀錄不刪除已匯出TXT。記錄檔不進備份／Log／預設FileProvider分享目錄；Key表單仍FLAG_SECURE。

沒有Android真機／emulator，實際外觀、SAF、浮窗stepper、摺疊與字體放大、聲音響度／路由、YouTube／Zoom／Meet／藍牙／鎖屏／30分鐘均需裝置複測。外部原聲控制、離線ASR/MT、模型管理、SRT、來源授權整合及全產品穩定性仍未完成，不能標全功能PASS。

本機與遠端先前commit不同但tree相同；本次以遠端ba14a8a為parent，以7f97df7ef5401931b27700932a444561b5570480為base tree做fast-forward，禁止用舊本機history強推。

最後本機驗證2m15s，lint 0 errors, 10 warnings；本機APK SHA256：`4a5817bd347504964af7329ae32ba35ec1ca0bc812b2b343e9abd8c68aa28c2f  V2V-0.4.0-studio-debug.apk`。
