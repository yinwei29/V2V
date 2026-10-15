# PROJECT_HANDOFF · 0.5.2-gemini-fix

使用者先回報Gemini無法翻譯，第一份Log最新session CLOUD_ENABLED=0；要求勾一次記住、App允許截圖，已完成0.5.1（遠端9052312，CircleCI #7 success）。接著新Log顯示CLOUD_ENABLED=1、第一個ASR HTTP400，使用者明確回報400。本版修正Gemini REST請求格式並改善安全錯誤分類，保留全部前項要求。

## 修正與依據

直接GET Google官方v1beta discovery定義，確認TextResponseFormat.mimeType為enum APPLICATION_JSON而非MIME字串application/json，ThinkingConfig.thinkingLevel為LOW而非low。前版送出錯誤的REST欄位字串，MockWebServer總回200未檢查官方列舉；現修正兩欄並加入獨立機器定義快照契約測試。模型／端點、分段、辨識→翻譯→離線播報流程不變。官方來源 https://generativelanguage.googleapis.com/$discovery/rest?version=v1beta ，快照app/src/test/resources/gemini-rest-enums.json。

Gemini錯誤body僅在記憶體讀最多64KiB，解析Google status及ErrorInfo.reason已知值為ProviderIssue；UI及Log只用App內固定說明／enum，未知message、metadata、Key、字幕、路徑皆不顯示／不記錄。可區分INVALID_REQUEST、INVALID_KEY、KEY_RESTRICTED、PROJECT_SETUP_REQUIRED、SERVICE_DISABLED、BILLING_DISABLED、MODEL_UNAVAILABLE、ACCESS_DENIED、QUOTA_EXCEEDED、UNKNOWN。OpenAI仍不讀失敗body；數字HTTP碼及既有429／5xx最多一次重試不變，400不重試。取消包含失敗body讀取。

## 前項需求

cloud_consent存DataStore，首次false，使用者勾一次後重開／切換provider／重開翻譯保留，手動取消亦保存。未勾選不誤入只擷取模式，無Key可跳設定；測試tone仍可單獨使用。Activity移除FLAG_SECURE允許截圖，Key輸入仍密碼遮蔽、不回顯儲存Key。Android系統音訊／螢幕擷取同意仍依系統每次要求。

## 驗證與交付

本機79項測試及build/lint通過，3m 23s；lint0 errors/fatal、10 warnings。新增8項契約／錯誤分類／隱私／上限／取消測試。docs/evidence/gemini-400/及docs/TEST_REPORT.md有原始結果。

APK deliverables/V2V-0.5.2-gemini-fix-debug.apk，checksum `a7d6b98c25f67647ac6a1c69b63447e2b0033dfd59699b4b04f700af23418f99  V2V-0.5.2-gemini-fix-debug.apk`。CircleCI以本次main提交結果為準，不以舊#7冒充本版。提交與CI狀態另記/workspace/tooling/gemini-400-github-upload.json。

## 待裝置驗證

沒有真實Gemini Key／Google生成呼叫／Android真機，已修正會造成400的格式，不保證帳號資格或實際翻譯已成功。安裝本版後首次勾音訊授權一次、開始，觀察原文／譯文／播報；重開維持勾選、各頁可截圖。若仍失敗請匯出新Log，看安全PROVIDER_REASON，不能把原400直接認定Key失效。Key保留，不要求貼聊天或加入CI。其他App原聲與會議擷取限制仍適用。

## Git

本機base6200987967a11990e2ebe3f0801f35feadda8959，遠端base9052312878a3ca814562d4fb410abda2f9b13364，tree同為c3f4a828d0b4eea133133e6757d22a024b5598dc。本次以遠端parent建立與本機相同tree，expected SHA推main，禁止由舊本機history強推。原使用者Log不提交Git。
