# PROJECT_HANDOFF · 0.5.0-gemini

使用者本次要求增加Gemini API。已新增OpenAI／Google Gemini切換、獨立Key、雲端同意與設定保存，延續0.4工作台／翻譯紀錄／響度增強及現有其他App音訊流程。CircleCI仍使用既有android-verify，不需雲端Key或帳號新設定。

## 交付

- TranslationProvider介面與工廠；Gemini gemini-3.8-flash，固定GoogleHTTPS endpoint，x-goog-api-key header，先辨識後翻譯兩個獨立請求，結構化text回應；來源語言en/id/auto，zh-TW譯文。OpenAI API／模型保留。
- 共用ProviderHttp：40秒call、取消涵蓋body讀取、成功body上限1MiB、429／5xx最多再試一次，1–8秒退避；不跟redirect、不記錄Key、字幕或HTTP正文。Gemini阻擋／非STOP／格式錯誤不產生成功字幕或播報。
- ApiKeyStore保留既有OpenAI檔名provider-key.bin與alias v2v.provider.aes.v1；Gemini另有gemini-key.bin及v2v.gemini.aes.v1。各自儲存／移除，不互相覆蓋，均noBackup與AES-GCM。
- DataStore保存所選引擎，Activity等載入後才顯示操作；開始請求固定provider至session，工作中不能切換；切換provider重設雲端同意與Key輸入欄位。服務檢查所選引擎有效與相應Key存在，錯誤不啟動擷取。SessionSnapshot與技術SESSION_START均記provider，TranscriptSegment使用實際模型description。
- 主頁顯示目前引擎並可跳至設定切換；Key面板提供測試連線（僅查模型）。Gemini與OpenAI共用字幕、浮窗、紀錄及Android離線TTS，未新增雲端TTS或麥克風回退。

## 驗證／交付狀態

本機69項單元測試及build/lint成功，最後2m 57s；lint0 errors/fatal、10 warnings。docs/evidence/gemini/、docs/TEST_REPORT.md記實際結果。APK deliverables/V2V-0.5.0-gemini-debug.apk，checksum `16bedb4654f25a6d59392835a8b25bd3208fb4649f894c174a05949d1e42fbf1  V2V-0.5.0-gemini-debug.apk`。本版CI以對應main提交查詢，不沿用舊版#5結果。

## 需要使用者設定與待測

在Google AI Studio建立Gemini API Key，手機設定→Google Gemini→儲存→測試連線，返回主頁重新同意雲端傳送。Key不傳聊天、不放Git／CI。原OpenAI Key不需重填。詳見docs/GEMINI_SETUP.md。

沒有真實Gemini生成測試與Android裝置；模型權限／額度、語言品質／延遲、Key保存／切換／移除、浮窗／離線播報需手機驗證。加入Gemini無法解除YouTube／Zoom／Meet音訊擷取政策或單獨控制其他App原聲。既有藍牙路由、鎖屏、30分鐘、離線ASR／MT等仍待驗收或未實作。

## Git注意

本機baseline e44a767，遠端baseline7f841f315610979ccd81e4a0c02c2738a978552d，tree同為366fe9c594236c2f47e64225089a761104d2a63a。本次用遠端parent建立等同本機tree的新commit並以expected SHA推main，禁止從舊本機history強推。遠端實際commit與CI結果另存 /workspace/tooling/gemini-github-upload.json。
