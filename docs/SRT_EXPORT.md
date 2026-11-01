# V2V 0.7.1-srt · Subtitle export

V2V only. Implements the existing V2V specification §9 / T18; no Ju code or meeting/document module is imported.

## Operation / 操作

紀錄頁右上匯出→文字紀錄（TXT）或字幕（SRT）。TXT保留全紀錄原方式；SRT先選工作階段（日期/時間＋段數），再選譯文或原文＋譯文，各工作階段以自己的來源相對時間軸輸出，不能把多個從0開始的session混在同一SRT。

History → Export → TXT or SRT. Choose a session for SRT, then translation-only or bilingual captions. Content is formatted off the UI thread, snapshotted before the OS save picker, and written as UTF-8 to the user-selected destination. The snapshot survives Activity recreation in a ViewModel, not Bundle/logs; process death can interrupt export and asks the user to retry. Cancelling the picker does not change history.

外部APP擷取的時間碼從本次擷取開始計算；V2V無法知道YouTube等APP的原片播放位置、暫停或跳轉，不保證匯出的SRT可直接對齊完整原片。音訊檔路徑則使用本次來源的取樣時間軸。

External-capture SRT is relative to this capture session, not the other app's original video playhead. No full-video synchronization or lip-sync guarantee is made.

## Contract / 格式

- Source start/end timestamps, not archive creation time, remain intact, including real skipped-source gaps. Cues sort by source start then segment ID.
- Deduplicate by session+segment ID, newest revision wins. HistoryStore already accepts only stable final translations; SRT does not invent partial captions.
- Reject negative/empty/reversed/out-of-range timestamps, empty translations or invalid revision metadata; the completion message reports the omitted count; unsupported records stay available through original history/TXT. No synthesized timing or silent cross-session concatenation.
- Hours are bounded at99:59:59,999 for portable SRT timecodes, with ASCII digits even on Arabic UI locales.
- UTF-8 Chinese/Indonesian/emoji retained. Normalize line endings, remove internal empty cue separators/control bytes, escape literal ampersands/angle brackets for subtitle renderers. This is formatting, not new translation or diarization.
- No automatic file upload, cloud calls, source reprocessing, new database schema, original recording, source deletion, model change or background-session restart.

## Checks / 驗證

Added6 JVM tests covering exact syntax/UTF-8, session isolation, revision/order/gaps, invalid records/time bounds, bilingual normalization/escaping and device-locale-independent timecodes. Existing106 cases retained (expected112). Locale check covers204 labels/four locales. Full Android assemble/unit/lint runs in this branch's CircleCI; only completed matching-source results can be called passed. No physical SAF picker/rotation/player compatibility or live API quality acceptance is claimed without device tests.

## Rollback / 回退

Revert this feature commit to restore the previous TXT-only control. History schema and stored content remain unchanged; exported files are user-owned and never automatically deleted. VersionCode13/version0.7.1-srt distinguishes the new artifact from0.7.0-global. No store publication.

## Remaining specification work

External-source capability/phone tests, complete offline ASR/MT, streaming provider paths, listening quality, background/route/soak acceptance remain independent unfinished work. This feature does not claim the whole V2V specification is complete.
