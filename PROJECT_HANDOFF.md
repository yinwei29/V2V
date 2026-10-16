# PROJECT_HANDOFF · 0.6.0-languages

使用者最新0.5.2Log為Gemini503後重試SocketTimeout，ASR錯誤後管線永久停止。新增暫時失敗恢復及模型選擇，同時按使用者要求完成來源／目的多語言（15種＋來源auto）、目的語言離線播報／紀錄匯出、手機播放音訊不限App名稱。既有勾一次記住及允許截圖保留。

詳見docs/LANGUAGES_AND_RECOVERY.md。Gemini新預設3.5 Flash-Lite／MINIMAL，另可選3.8 Flash／LOW；模型與語言保存DataStore並在服務開始時固定。服務503／5xx／408／網路故障可恢復，HTTP重試界限保留，管線對後續片段退避2／4秒，連續3段失敗明示暫停，不無限排隊、不切其他供應商。MT失敗保留原文；成功時清除故障。

AppLanguage固定15種語言，來源auto、目標不可auto。OpenAI中文hint映射zh，provider翻譯指令及TranscriptSegment傳目標；歷史JSON新增targetLanguage，舊資料缺欄預設繁中。TTS只用目的語言離線聲線，優先同地區、中文不跨繁簡；缺語音提示保留字幕。手機播放音訊沒有App UID白名單，仍受Android MEDIA/GAME/UNKNOWN及capture policy約束，不能保證通話／受保護內容，沒有麥克風fallback。

驗證：本機用快取Kotlin2.1.20／Compose編譯器直接編譯全主程式及全部測試程式已成功，執行57項不需網路的JUnit均通過。本機Gradle額外網路權限流程未成功啟動，不把舊build輸出當本版結果。完整Android assembleDebug、所有unit tests及lint由CircleCI完成，以本版commit狀態及Artifacts的verification.json／ci-build.log為準。沒有真實Gemini Key或Android裝置，語言品質、實際API、離線語音及OEM擷取待手機測試。沒有虛構本機新版APK或checksum。

交付：CircleCI產生V2V-0.6.0-languages-debug.apk／SHA256SUMS／verification.json。移除repo追蹤的舊checksum，避免與CI環境混用，歷史報告仍保留舊證據。Key不需重填或放CI，手機需選來源／目的及有離線聲音的目的語言；系統擷取同意照Android要求。

Git base本機98e0bba2d937b39bfe261dd82304ab614af50166，遠端68ffdcb109343aebdb6777314ff1adb186df05d0，tree同為063be9e80bc890aa68853d37fa9cf2cc3df0ddb9。用遠端parent加本機同tree建立commit、expected SHA推main，勿強推舊本機history。CI狀態另記/workspace/tooling/languages-github-upload.json；原始使用者Log不提交Git。
