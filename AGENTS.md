# V2V repository instructions

- This repository is **yinwei29/V2V**, base/namespace `com.yinwei.v2v`, Kotlin/Compose. Debug APKs from0.8.1 use `com.yinwei.v2v.preview` / V2V Preview to coexist with legacy V2V; release identity stays unchanged. Confirm `git remote -v` before every push.
- **yinwei29/Ju (LinguaBridge) is a separate project**, with separate branches, PRs, architecture and test evidence. Do not copy its private source/configuration here or apply its test counts/CI results to V2V.
- Use V2V's existing `.circleci/config.yml` and the CircleCI project `https://app.circleci.com/projects/github/yinwei29/V2V`. Pushes run the existing verify pipeline. Do not assume a Ju CircleCI/GitHub workflow validates this repository.
- Preserve existing bounded audio/translation queues, cancellation, privacy/consent, key storage and history. Real provider keys, user audio/transcripts and raw private logs must not enter commits or CI artifacts.
- Use local synthetic/MockWebServer tests for automatic validation; never claim real provider, Bluetooth, capture policy or device listening acceptance from JVM tests.
- Derive version from app/build.gradle.kts and test/build outcome from the matching source commit's CircleCI reports. Do not label an old APK or another repository's artifact as the current build.

## Product scope clarification — 2026-11-01
- V2V owns phone playback capture, imported audio transcription/translation, overlays and controlled-source original/translated audio mixing. Current owned-file support is limited to documented WAV inputs; playback capture does not imply physical microphone recording or unrestricted call capture.
- Ju owns live microphone conversation, its own meeting transcripts/AI notes and Office/PDF document translation. Do not add these to V2V solely because a generic attachment mentions them.
- Read docs/PRODUCT_SCOPE.md alongside this repo's own docs/Android_Translator_AI_Spec_V2.md. Do not replace V2V milestones with Ju's phase gates or use either product's offline/CI results as the other's evidence.

- Never silently regenerate a missing CI signing key or alter scripts/debug-signer.sha256 to bypass a mismatch. Preview signer must match the pinned certificate; restore through protected CI configuration, not Git/artifacts/logs. See docs/INSTALLATION.md.
