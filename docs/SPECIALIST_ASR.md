# V2V 0.9.0-asr · Specialist recognition / 專用辨識

V2V only: playback capture/authorized WAV, no Ju source or modules. Maintains Preview package`com.yinwei.v2v.preview` and pinned signer; Code16 permits updating0.8.1 Preview. Legacy V2V remains independent.

## Problem and design / 問題與設計

User reports poor results with general Gemini3.5 Flash-Lite/3.8 Flash. A model name/prompt change alone does not establish better recognition. Existing combined route asks a general model for both original and translation in one audio request; neither text appears until both complete. New **opt-in** paths separate dedicated ASR from text MT, so live source is visible before translation and preserved in the session ledger if MT fails. This does not repair an incorrect transcript automatically or guarantee translation accuracy.

| Selection | Recognition | Translation | Requests per spoken chunk |
|---|---|---|---|
| Flash-Lite (existing/default unchanged) |3.5 Flash-Lite combined audio |Same combined response |1 |
| Flash (existing) |3.8 Flash combined audio |Same combined response |1 |
| Transcribe → Flash |gemini-3.5-transcribe |gemini-3.8-flash text only |2 |
| Transcribe → Flash-Lite |gemini-3.5-transcribe |gemini-3.5-flash-lite text only |2 |

Same opted-in Google key, no OpenAI fallback or second account. New model/account/API access must be available; 400/401/403/404 remain explicit errors rather than silently switching. Existing saved enum values/default remain valid. Prefer comparing Transcribe→Flash first for quality analysis, not labeling it objectively best without evaluation. Flash-Lite offers a cost-oriented comparison; current prices not fetched or invented by this feature.

## Wire contract and privacy / API契約

- `GeminiTranscribeProvider.kt` calls`POST /v1beta/interactions` with model`gemini-3.5-transcribe`, inline exact WAV in`input:[{type:audio,mime_type:audio/wav,data:base64}]`, header authentication, `store:false`, `stream:false`, `background:false`. No Files API upload, system prompt, prior interaction or request to summarize/translate in ASR. Provider processing/retention terms still apply; disabling Interaction storage is not a zero-retention claim.
- `generation_config.transcription_config.mode={type:verbatim}`. Omit language_codes forauto. Fixed BCP47 catalog: en-US,id-ID,etc; Spanish es-419,Arabic ar-EG,Mandarin cmn-Hans-CN for both Chinese selections. Dedicated model's documented Mandarin output may be Simplified even forzh-TW source; MT still explicitly targets Traditional Chinese. No claim of a distinct Traditional Mandarin ASR model.
- Only completed Interaction`steps[].type=model_output/content[].type=text` accepted. Reject incomplete/failed/tool/invalid/oversized output without including provider body in errors. No tools executed. Blank ASR skips MT/subtitles/TTS rather than inventing words.
- `GeminiProvider.translateInContext` sends only the current source text plus bounded prior-source reference, not audio. Current text/negation/numbers must be preserved by prompt; context is not translated again. No previous translations reinforce mistakes. Existing source-window reset/queue limits/cancellation apply.
- Sequential session-owned ASR usage is consumed once by MT. Total tokens are reported only if both service responses provide known nonnegative counts and addition does not overflow; missing counts remain unknown, not zero/billing estimates. No raw audio/text/context/key enters diagnostics; existing separate ASR_MS/MT_MS timing becomes useful. Those timings exclude collection/queue/TTS delay.
- Connection test reads metadata for both models, no test audio or fabricated recognition result. Metadata visibility does not prove Interaction-call eligibility or quality.

## Use / 使用

更新Preview後：設定→引擎與金鑰→Google Gemini→選 **Transcribe → Flash**，保留既有Google Key→測試連線→停止舊工作階段後重新開始。需要便宜的比較路徑可選 **Transcribe → Flash-Lite**；既有單請求模式仍可選。沒有自動提高既有選擇的花費。舊的非Preview V2V仍不被覆蓋；不要為了更新刪除舊資料。

Compare a fixed authorized recording and language pair across modes: known source script, numbers/negation/names, source errors versus translation errors, missing/duplicate segments, source capture gaps and playback latency. Export technical log and note selected model; do not share keys. Two sequential requests and audio/context billing may increase cost/latency. Collection still600ms pause/4s ceiling; hard cuts can divide words. Dedicated ASR can still be wrong. No confirmed real provider/device quality evidence in this task.

## Validation / 驗證

11 new synthetic/MockWebServer cases plus original123:134/134 passed on [CircleCI#23](https://circleci.com/gh/yinwei29/V2V/23), source2c9f4d0d33eb45037e2dd87102eb83675b4e437b. Build/install guards passed; lint0 Error/Fatal and221 warnings (two added dynamic-label UnusedResources versus#21; locale check confirms references). [Verification metadata](validation/circleci-23-specialist-asr.json) records actual APK digest and pinned Preview signer. Cover exact inline audio/auth/storage/verbatim/language hints, health metadata, independent ASR→MT request/current-source context, known/unknown usage, malformed/incomplete/tool rejection, cancellation, explicit factory selection, original before translation, retained original/no speech on MT failure, silence skipping MT, safe numeric diagnostics and identical subtitle/spoken text. Mocks establish contracts/plumbing only. Run locale206 labels/four locales and existing CircleCI build/unit/lint + actual APK signing/install-metadata checks. Physical installation/listening and real provider accuracy remain unverified.

## Sources / 官方來源（2026-11-01檢閱）

- https://ai.google.dev/gemini-api/docs/transcribe — dedicated ASR request/mode/language hints.
- https://ai.google.dev/gemini-api/docs/models/gemini-3.5-transcribe — supported model/locales, no thinking; Mandarin catalog limit.
- https://ai.google.dev/api/interactions — inline AudioContent, store flag, completed status and steps/model_output/text response shape (SDK output_text is a convenience, not the raw REST field).
- https://ai.google.dev/gemini-api/docs/live-api/live-transcribe — distinct streaming endpoint; NOT implemented or claimed by this unary path.

## Scope and rollback / 範圍與回退

No new offline ASR/MT, live WebSocket, source integration, diarization, automatic transcript revision or cross-language voice model. Existing captured-source restrictions, TTS engine, bounded queues, stored history and signing guards stay intact. Revert feature commits to remove specialist selections; no database migration/deletion. Unknown future saved selection safely falls back per existing settings rules. Build/release uses matching CircleCI artifact and manual verified GitHub prerelease upload; no Play publication.
