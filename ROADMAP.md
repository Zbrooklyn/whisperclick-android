# WhisperClick Roadmap

> Voice-first Android keyboard powered by on-device Whisper with cloud fallback.
> Last updated: 2026-02-25

---

## Current State: v0.9.1-beta

### What Works
- On-device transcription via whisper.cpp v1.8.3 (Q5_1 quantized models)
- Cloud transcription via OpenAI Whisper API
- Magic Rewrite (Gemini 2.0 Flash / GPT-4o-mini) with 5 styles
- Full IME keyboard: mic, arrows, backspace, enter, edit toolbar
- Model download with resume support (7 models from Hugging Face)
- API key validation for OpenAI and Gemini
- Theme support (Light / Dark / OLED)
- Settings UI with logical grouping, dynamic sections, collapsible debug
- CPU variant detection (ARM64 FP16, ARMv7 VFPv4)
- Haptic feedback, audio focus management
- On-device crash logger with in-app viewer
- Versioned APK filenames + GitHub Releases (direct APK download)
- Version displayed from BuildConfig (auto-updates with builds)
- Log deduplication (suppresses repeated messages)

### What's Missing
No text keyboard, no punctuation/numbers/emoji, no VAD, no streaming,
no tests, plaintext API key storage, empty ProGuard rules, no accessibility labels,
no clipboard history, no auto-switch-back keyboard.

---

## Milestones

### v0.9.1-beta — Critical Fixes *(IN PROGRESS)*
> Goal: Eliminate crashes, security holes, and data loss risks.

| # | Issue | Status | File(s) |
|---|-------|--------|---------|
| 1 | **Encrypt API keys** — Move from plaintext SharedPreferences to EncryptedSharedPreferences | TODO | MainScreen.kt, VoiceKeyboardInputMethodService.kt |
| 2 | **Gemini key in URL** — Pass via header (`x-goog-api-key`) instead of query parameter (leaks in logs/analytics) | TODO | GeminiClient.kt |
| 3 | **ProGuard rules** — Add keep rules for JNI classes (`WhisperLib`, `WhisperCpuConfig`), Compose, and API model classes | TODO | proguard-rules.pro |
| 4 | ~~Replace `runBlocking` in `onDestroy`~~ — Non-blocking destroy with fire-and-forget release | DONE | VoiceKeyboardInputMethodService.kt |
| 5 | ~~Cancel coroutine scope on destroy~~ — `job.cancel()` in `onDestroy` | DONE | VoiceKeyboardInputMethodService.kt |
| 6 | **Runtime permission check before recording** — Verify `RECORD_AUDIO` is still granted before `AudioRecord.startRecording()` | TODO | VoiceKeyboardInputMethodService.kt |
| 7 | **Unbounded recording** — Add max duration timeout (e.g. 5 minutes) to prevent battery drain | TODO | Recorder.kt |
| 8 | **Network connectivity check** — Verify network before cloud STT/rewrite calls | TODO | WhisperCloudClient.kt, GeminiClient.kt, OpenAIClient.kt |
| 9 | ~~Compose state from IO thread~~ — Moved scope to `Dispatchers.Main` | DONE | VoiceKeyboardInputMethodService.kt |
| 10 | **Fix `Recorder.kt` finalizer** — `WhisperContext.release()` called in finalizer can deadlock | TODO | Recorder.kt, LibWhisper.kt |
| 11 | ~~VIBRATE permission~~ — Added to manifest + crash-safe `haptic()` | DONE | AndroidManifest.xml, VoiceKeyboardInputMethodService.kt |
| 12 | ~~ViewModelStoreOwner for Compose IME~~ — Added missing interface + complete view tree setup | DONE | VoiceKeyboardInputMethodService.kt |
| 13 | ~~Enter key logic~~ — Newline in multi-line fields, IME action in single-line | DONE | VoiceKeyboardInputMethodService.kt |
| 14 | ~~Settings redesign~~ — Dynamic sections, collapsible debug, logical grouping | DONE | MainScreen.kt |
| 15 | ~~Versioned APK + GitHub Releases~~ — Auto-named APKs, direct download | DONE | build.gradle, android_build.yml |
| 16 | ~~Log deduplication~~ — Suppress repeated consecutive messages | DONE | AppLog.kt |

---

### v0.10.0-beta — Core Features
> Goal: Make WhisperClick a complete daily-driver keyboard.

| # | Feature | Priority | Notes |
|---|---------|----------|-------|
| 1 | **Magic Rewrite panel (swipe-right)** — Swipe right on keyboard to reveal rewrite panel. Uses `HorizontalPager` (page 0 = keyboard, page 1 = rewrite). Single API call returns structured JSON with clean + all style variants. Two-step flow: (1) Clean baseline fixes spelling, grammar, punctuation; (2) style variants (Professional, Casual, Concise, Emojify) are derived from the clean text. Results shown as horizontal carousel with dot indicators. "Clean" is always the first card (most common use case for voice transcription). Tap [Use] to apply and auto-swipe back to keyboard | HIGH | Replaces current dead-code MagicMenu.kt. One API call instead of 5. JSON response: `{"clean":"...","professional":"...","casual":"...","concise":"...","emojify":"..."}` |
| 2 | **Text keyboard fallback** — Full QWERTY layout users can switch to for manual typing | HIGH | Required for Play Store viability. Users need a way to type when they can't speak |
| 3 | **Punctuation & numbers row** — Quick access to . , ! ? 0-9 without switching keyboards | HIGH | Can be a top row or swipe-up overlay |
| 4 | **Voice Activity Detection (VAD)** — Auto-start/stop recording when user speaks/pauses | HIGH | Silero VAD is lightweight (~2MB). Eliminates manual start/stop |
| 5 | **Last-used keyboard auto-switch** — Track which keyboard user switched FROM and auto-switch back to it. Uses ContentObserver on `Settings.Secure.DEFAULT_INPUT_METHOD` to record previous IME. Falls back to preferred keyboard setting if no history | HIGH | Better UX than cycling through all keyboards |
| 6 | **Clipboard history** — Build our own clipboard history using `ClipboardManager.addPrimaryClipChangedListener()`. Captures copies from any app. Button on keyboard opens recent clips for quick paste. Stored locally, auto-prune old entries | HIGH | System clipboard listener is app-agnostic — works regardless of which keyboard copied the text. Cannot sync with Samsung/Gboard proprietary history but captures the same content independently |
| 7 | **Streaming transcription** — Show partial results as user speaks instead of waiting until stop | MEDIUM | whisper.cpp supports chunked processing. Improves perceived speed |
| 8 | **Emoji picker** — Basic emoji grid or search | MEDIUM | Android IME standard expectation |
| 9 | **Language selection** — Let user pick transcription language (currently hardcoded to "en" in jni.c) | MEDIUM | Multilingual models already available; just need UI + JNI param |
| 10 | **Custom rewrite prompts** — User-defined 6th card in the rewrite carousel. TextField where user types their own instruction (e.g. "Rewrite as if explaining to a 5-year-old"). Separate API call since it's user-defined. Saved styles persist in SharedPreferences | LOW | Builds on top of the swipe-right rewrite panel |
| 11 | **Rewrite undo** — One-tap revert after applying a rewrite. Stores original text so user can undo if the result isn't right | LOW | Simple: save original in a variable before replacing |

---

### v0.11.0-beta — Polish & Performance
> Goal: Fast, reliable, accessible.

| # | Item | Category | Notes |
|---|------|----------|-------|
| 1 | ~~Version from BuildConfig~~ — Use `BuildConfig.VERSION_NAME` everywhere | DONE | Completed in v0.9.1-beta |
| 2 | **Accessibility labels** — Add `contentDescription` to all icons and interactive elements | Accessibility | Currently most icons have hardcoded English strings, some are missing entirely |
| 3 | **TalkBack support** — Verify full keyboard navigation works with screen reader | Accessibility | Required for Play Store accessibility guidelines |
| 4 | **Connection timeout tuning** — Reduce cloud API timeouts when on slow networks. Add user-visible retry | Performance | Current: 15s connect, 30s read |
| 5 | **Model loading indicator** — Show progress when whisper model is being loaded into memory (can take 2-5s on cold start) | UX | Users currently see a dead mic button with no explanation |
| 6 | **Keyboard height preference** — Let users adjust keyboard height (compact/normal/tall) | UX | Important for tablets and small phones |
| 7 | **Sound feedback option** — Optional click/beep sounds in addition to haptics | UX | Some users prefer audible feedback |
| 8 | **Landscape layout** — Optimize keyboard layout for horizontal orientation | UX | Current layout stretches awkwardly in landscape |
| 9 | **Remove dead code** — Delete unused files: MagicMenu.kt (no UI trigger), ModelSelector.kt (unused), BatteryMonitor.kt, MemoryMonitor.kt | Cleanup | Reduces APK size and confusion |
| 10 | **Benchmark improvements** — Show model inference time in settings for users to compare models | Performance | Data already available from whisper; just needs UI |

---

### v1.0.0 — Production Release
> Goal: Play Store ready. Stable, secure, tested.

#### Security
- [ ] Certificate pinning for OpenAI and Gemini API endpoints
- [ ] Input sanitization on all text fields (API keys, settings)
- [ ] Prevent API key leakage in logs (audit all `AppLog.log` calls)
- [ ] Security policy document (SECURITY.md)

#### Testing
- [ ] Unit tests for: ModelManager, ApiKeyValidator, RiffWaveHelper, WhisperCpuConfig
- [ ] Integration tests for: recording pipeline, transcription flow, cloud fallback
- [ ] UI tests for: settings screen, keyboard view, model download
- [ ] Crash-free rate target: 99.5%+

#### Play Store Requirements
- [ ] Privacy policy URL
- [ ] Feature graphic (1024x500)
- [ ] Screenshots (phone + tablet)
- [ ] Short description (80 chars) and full description
- [ ] Content rating questionnaire
- [ ] Data safety form (microphone data, API keys, network calls)
- [ ] Target API level compliance (currently 34, check latest requirement)
- [ ] 64-bit native library compliance (already arm64-v8a)

#### Localization
- [ ] Extract all hardcoded strings to strings.xml
- [ ] RTL layout support
- [ ] Translate UI to top 5 languages (Spanish, French, German, Portuguese, Japanese)

#### Offline Resilience
- [ ] Graceful degradation when no network (cloud features disabled with clear messaging)
- [ ] Bundle tiny-en model in APK for zero-setup experience
- [ ] Model integrity verification (checksum after download)

#### Battery & Memory
- [ ] Profile memory usage under sustained recording
- [ ] Ensure whisper context is fully freed when switching apps
- [ ] Battery usage report in settings (leverage existing BatteryMonitor.kt or remove it)

---

## Future Ideas (Post v1.0)

These are not committed — just possibilities to evaluate.

| Idea | Effort | Impact | Notes |
|------|--------|--------|-------|
| Moonshine models | Medium | High | MIT-licensed, 5x faster than Whisper on mobile. Evaluate as alternative backend |
| Vulkan GPU acceleration | High | Medium | whisper.cpp has Vulkan backend but unreliable on mobile GPUs. Monitor upstream |
| On-device rewrite (Gemma/Phi) | High | Medium | Eliminate cloud dependency for rewrites. Needs 2-4GB RAM |
| Voice commands | Medium | Medium | "Delete last word", "new line", "select all" via voice |
| Dictation mode | Low | Medium | Continuous recording with auto-punctuation |
| Multi-device sync | High | Low | Sync settings/models across devices via cloud |
| Wear OS companion | High | Low | Voice input from smartwatch |
| Widget for quick transcription | Low | Low | Home screen widget that records and copies to clipboard |

---

## Architecture Decisions

Decisions made during development that should be preserved.

| Decision | Rationale | Date |
|----------|-----------|------|
| whisper.cpp over faster-whisper | faster-whisper requires Python + CTranslate2 (server-only). whisper.cpp is the only viable C++ engine for Android JNI | 2026-02 |
| Q5_1 quantization over FP16 | 60% smaller models, faster ARM inference, near-identical accuracy (< 0.5% WER difference) | 2026-02 |
| whisper.cpp v1.8.3 | Flash attention (default since v1.8.0), improved ARM NEON/dotprod/i8mm kernels, restructured ggml via FetchContent | 2026-02 |
| Compose for keyboard UI | Matches app settings UI. Simpler state management. Trade-off: slightly higher memory than XML views | 2026-01 |
| Single coroutine scope for WhisperContext | Whisper is not thread-safe. SingleThreadDispatcher ensures sequential access | 2026-01 |
| Cloud STT as fallback, not primary | On-device privacy is the product differentiator. Cloud is opt-in for users who want speed/accuracy | 2026-01 |
| Own clipboard history over Samsung sync | Samsung/Gboard clipboard APIs are proprietary. System ClipboardManager listener captures same content app-agnostically | 2026-02 |
| ContentObserver for last-used keyboard | No Android API for "previous IME". Track changes to DEFAULT_INPUT_METHOD setting and save last non-WhisperClick value | 2026-02 |

---

## How to Contribute

1. Pick an item from the current milestone
2. Create a feature branch: `feature/short-description`
3. Implement with tests
4. PR against `main` — CI must pass
5. Tag releases: `vX.Y.Z-beta` during beta, `vX.Y.Z` for stable
