# V2V 功能範圍核對 / V2V product scope

2026-11-01，以使用者最新澄清與本repo自己的 [Android音訊翻譯V2規格](Android_Translator_AI_Spec_V2.md) 為準。

**V2V是手機播放音訊、使用者授權音訊檔的轉錄與即時翻譯。Ju／LinguaBridge是另一個對話／文件翻譯APP。**

| 功能 / Feature | V2V範圍與實際狀態 / Scope and status |
|---|---|
| 手機播放／喇叭外放的來源音訊 | PlaybackCapture透過Android允許的播放擷取取得音訊，不是默默開麥克風重錄喇叭；受來源usage/capture policy限制。 |
| 音訊檔轉錄／翻譯 | OWNED_FILE，目前是48kHz mono PCM16 WAV；其他編碼／容器未因此當作已支援。原創測試音也不能替代真實來源驗收。 |
| YouTube／Zoom／Meet | 屬V2V外部來源整合範圍；各服務來源有效性與控制權需真機證據，不保證通話／受保護音訊可取。 |
| 背景／浮窗／小圈圈 | 屬V2V自己的前景服務與overlay，與Ju雙語面對面字幕不同。 |
| 原聲／譯音混音及聲道 | 可控自有來源用StereoMixer；外部APP仍直出原聲時不能宣稱可獨立靜音／改原聲聲道，不重播擷取副本。 |
| 多語字幕、模型與TTS | V2V自己的provider／目標語言／浮窗與設定。離線TTS重用系統聲音，不等於離線ASR/MT。 |
| 離線ASR/MT | 原V2V規格有待辦與驗收要求，目前未交付；不能引用Ju模型或原生測試宣稱完成。 |
| 麥克風雙人即時對話／共享雙耳語言 | Ju主線；不自動移植進V2V。V2V未實作麥克風fallback，不能將權限RECORD_AUDIO誤當正在錄麥克風。 |
| PDF／Word／Excel／PPTX及OCR | Ju文件模組。V2V的音訊檔不是Office/PDF文件，沒有因此新增此模組。 |
| 會議紀要／錄音管理／同步 | Ju既有功能或規劃；V2V自己的轉錄歷史/TXT匯出保留，不等於已做Ju的AI紀要。新增此類功能需V2V單獨需求。 |

## 已檢查改動 / Change audit

V2V PR#1僅更正ProviderTest的production backlog超載輸入，以及版本／識別／CI證據文件。沒有引入Ju程式碼、文件翻譯、雙人對話或AI會議模組。CircleCI#13的106 JVM測試、build/lint與APK只屬V2V，不是Ju的152 instrumentation驗收。

The October31 generic staged specification discussed elsewhere combines both product domains. It is not a replacement for this repository's own specification and approved follow-ups. Generic PCM/TTS/latency concepts require separate implementation and acceptance per product. No private Ju source or full private specifications are copied here.

## 文件更正與優先序 / Documentation precedence

1. 使用者最新明確的V2V需求優先。
2. 本repo的Android_Translator_AI_Spec_V2.md及其後續V2V功能紀錄維持獨立，不改套Ju Phase0–6。
3. 本scope說明只釐清歸屬，不刪除原V2V需求或未完成項，也不新增或宣告完成功能。
4. 公開README／AGENTS／handoff指向此表；任何新需求先確認repo、功能範圍與該repo的測試。

This is documentation-only. No production source, keys, dependencies, user records, model settings or CI configuration changed; no new build/device/API acceptance is claimed.
