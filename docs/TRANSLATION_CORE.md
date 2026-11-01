# V2V 0.8.0-core · Translation core / 翻譯核心

V2V only: playback capture and authorized WAV transcription/translation. No Ju changes, microphone fallback, conversation/document module or source integration claim.

## Changes and files / 修改

- `transcript/SubtitlePipeline.kt`: production endpoint600ms silence; max4,000ms (previously10,000ms); remove Gemini4,000ms minimum. Serial ordered worker, bounded250-frame ingress,30s/12-chunk mailbox, cancellation/backoff/error rules remain. Exact PCM/source timeline preserved; no audio overlap/repetition or artificial subtitle completion. Hard boundaries can still divide words; this is a latency/quality tradeoff needing listening evaluation.
- `providers/TranslationProvider.kt`, `transcript/SourceContextWindow.kt`: worker-owned memory context, latest two successfully processed source strings, each at most1,200 UTF-16 units. Oversized originals are omitted whole, never truncated through Unicode. Clear on missing chunk IDs, reversed time, gap over3s, empty ASR or provider failure. New pipeline starts empty. No prior translation is used, to avoid reinforcing its errors. Context is not added to history/diagnostic logs; existing opted-in cloud requests can contain preceding source text.
- `providers/OpenAiProvider.kt`, `GeminiProvider.kt`: OpenAI MT includes current text and separate JSON reference context; Gemini combined request retains exact current WAV and adds context as separate text. System instructions limit output to current input, preserve numbers/negation and forbid inventing continuations. Existing model IDs, authentication, response validation and TTS path remain. No guarantee that instructions eliminate all model mistakes or prompt injection.
- `diagnostics/DiagnosticJournal.kt`: successful segments add typed `PIPELINE_TIMING`, `CHUNK_MS`, `ASR_MS`, `MT_MS`, `COMBINED_REQUEST`, `CONTEXT_SEGMENTS`. Gemini combined ASR+MT request time is reported inASR_MS, MT_MS=0 and COMBINED_REQUEST=1, not a claim of zero translation time. OpenAI reports independent ASR/MT service durations. Neither field includes audio accumulation, queue wait or playback delay; these are not end-to-end latency. Logs contain no transcript/audio/key/context contents.
- Added core pipeline/context/provider contract regressions; unchanged SRT/history, keys/settings, source/mixer, speech queue and offline TTS.

## Use and tradeoffs / 使用與取捨

不需重新輸入Key或切換新模式。維持原有來源與目的語言設定，在已有雲端同意下重新啟動工作階段即可使用。停頓後較早送出短句；連續說話最多收集4秒一段，再加排隊、網路、供應商與TTS時間。不是保證4秒內聽到譯音，也不是串流partial。用相同來源、語言、模型、網路與語速比較前後版本，勿把TTS播放時間當收音或翻譯耗時。

Shorter chunks can increase API request frequency, audio-container overhead and billable input tokens; up to2,400 characters of reference can add text input cost per request. Processing slower than source still causes bounded backlog/drop counters rather than unlimited latency. Existing quotas and HTTP retry limits remain; no automatic paid model switch. Uncertain partial speech is not later rewritten or re-spoken. Model accuracy remains unverified without authorized live evaluation.

## Validation / 驗證

Run `python3 scripts/verify-locales.py` and `./gradlew --no-daemon assembleDebug testDebugUnitTest lintDebug` via V2V CircleCI. New tests use synthetic PCM and MockWebServer only; no live keys/private transcripts. Tests cover sending short Gemini speech before4s/finish, continuous4s boundary and exact tail/source timeline, session isolation, context caps/reset and current-audio wire contract, recovery clearing context, numeric timing diagnostics with no private content. Existing112 tests retained;11 added (expected123). Matching-source CircleCI result required before claiming pass.

Device checklist: authorized WAV with natural pauses and continuous speech; compare recorded original against known script, confirm no missing/duplicate source ranges, negation/numbers/technical terms, subtitles and spoken content, stop/start isolation; external allowed playback capture separately. Verify no-signal/overload/quota/cancel paths and export technical log. Physical source/TTS/Bluetooth and real model throughput/quality are pending, not inferred from mocks.

## Rollback / 回退

Revert the core feature commits to restore0.7.1 behavior. No storage schema/data migration or deletion. VersionCode14 /0.8.0-core APK must come from matching CircleCI artifacts. No store publication. Offline ASR/MT and actual streaming provider work remain unfinished.
