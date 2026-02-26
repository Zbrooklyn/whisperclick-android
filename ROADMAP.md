# WhisperClick Roadmap

> Voice-first Android keyboard powered by on-device Whisper with cloud fallback.
> Last updated: 2026-02-25

---

## Current State: v0.10.0-beta

### What Works
- On-device transcription via whisper.cpp v1.8.3 (Q5_1 quantized models)
- Cloud transcription via OpenAI Whisper API
- Magic Rewrite (swipe-right panel) — single API call returns Clean + 4 style variants as carousel
- Rewrite undo — one-tap revert after applying a rewrite
- Clipboard history — captures copies from any app, 3rd swipe page + edit toolbar button, tap to paste
- Full IME keyboard: mic, arrows, backspace, enter, edit toolbar with clipboard history button
- 3-page HorizontalPager: keyboard, Magic Rewrite, clipboard history
- Model download with resume support (7 models from Hugging Face)
- API key validation for OpenAI and Gemini
- Unified AI Provider settings — one provider picker, one API key (powers Cloud STT + Magic Rewrite)
- Theme support (Light / Dark / OLED) with live switching and system bar color sync
- Settings UI with logical grouping, dynamic sections, collapsible debug
- CPU variant detection (ARM64 FP16, ARMv7 VFPv4)
- Haptic feedback (crash-safe), audio focus management
- On-device crash logger with in-app viewer
- Versioned APK filenames + GitHub Releases (direct APK download)
- Version displayed from BuildConfig (auto-updates with builds)
- Log deduplication (suppresses repeated messages)
- Gemini API key passed via header (not URL query parameter)

### What's Missing
No text keyboard, no auto punctuation, no filler word removal, no VAD, no streaming,
no tests, plaintext API key storage, empty ProGuard rules, no accessibility labels,
no auto-switch-back keyboard, no Gemini Cloud STT, no offline rewrite,
no language selection, no privacy dashboard.

---

## Milestones

### v0.9.1-beta — Critical Fixes *(MOSTLY COMPLETE)*
> Goal: Eliminate crashes, security holes, and data loss risks.

| # | Issue | Status | File(s) |
|---|-------|--------|---------|
| 1 | **Encrypt API keys** — Move from plaintext SharedPreferences to EncryptedSharedPreferences | TODO | MainScreen.kt, VoiceKeyboardInputMethodService.kt |
| 2 | ~~Gemini key in URL~~ — Pass via header (`x-goog-api-key`) instead of query parameter | DONE | GeminiClient.kt |
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
| 14 | ~~Settings redesign~~ — Dynamic sections, collapsible debug, unified AI Provider, logical grouping | DONE | MainScreen.kt |
| 15 | ~~Versioned APK + GitHub Releases~~ — Auto-named APKs, direct download | DONE | build.gradle, android_build.yml |
| 16 | ~~Log deduplication~~ — Suppress repeated consecutive messages | DONE | AppLog.kt |
| 17 | ~~Theme reactivity~~ — Live theme switching via SharedPreferences listener + system bar color sync | DONE | MainActivity.kt, VoiceKeyboardView.kt |

---

### v0.10.0-beta — Core Features *(IN PROGRESS)*
> Goal: Make WhisperClick a complete daily-driver keyboard.

| # | Feature | Priority | Status | Notes |
|---|---------|----------|--------|-------|
| 1 | ~~Magic Rewrite panel (swipe-right)~~ — HorizontalPager page 1. Single API call returns structured JSON with Clean + 4 style variants as carousel. Tap [Use] to apply and auto-swipe back | HIGH | DONE | Replaced dead-code MagicMenu.kt. JSON: `{"clean":"...","professional":"...","casual":"...","concise":"...","emojify":"..."}` |
| 2 | **Text keyboard fallback** — Full QWERTY layout users can switch to for manual typing | HIGH | TODO | Required for Play Store viability. Users need a way to type when they can't speak. Doesn't need to compete with Gboard — just functional |
| 3 | **Punctuation & numbers row** — Quick access to . , ! ? 0-9 without switching keyboards | HIGH | TODO | Can be a top row or swipe-up overlay |
| 4 | **Voice Activity Detection (VAD)** — Auto-start/stop recording when user speaks/pauses | HIGH | TODO | Silero VAD is lightweight (~2MB). Eliminates manual start/stop |
| 5 | **Last-used keyboard auto-switch** — Track which keyboard user switched FROM and auto-switch back to it. Uses ContentObserver on `Settings.Secure.DEFAULT_INPUT_METHOD` to record previous IME | HIGH | TODO | Better UX than cycling through all keyboards |
| 6 | ~~Clipboard history~~ — `ClipboardManager.addPrimaryClipChangedListener()` captures copies from any app. 3rd HorizontalPager page + edit toolbar button for direct access. LazyColumn of entries with tap-to-paste, delete, clear all. JSON in SharedPreferences, 50 entry cap, deduplication | HIGH | DONE | System clipboard listener is app-agnostic. Captures same content as Samsung/Gboard independently |
| 7 | **Cloud STT: Gemini support** — Add Gemini as a cloud STT provider alongside OpenAI. Unified provider selection means one API key per provider handles everything | HIGH | TODO | Currently cloud STT is OpenAI-only |
| 8 | **Auto punctuation** — Un-suppress Whisper's punctuation tokens in jni.c, or apply rule-based punctuation from pauses. Every competitor has this. Without it, dictated text is a run-on blob | HIGH | TODO | FUTO proves on-device punctuation works well. Check whisper.cpp token suppression settings first |
| 9 | **Filler word removal** — Strip "um", "uh", "like", "you know" post-transcription with regex filter. ~20 lines of code for massive polish | HIGH | TODO | FUTO and Wispr Flow both do this. Applied after transcribe() returns, before committing text |
| 10 | **Streaming transcription** — Show partial results as user speaks instead of waiting until stop | MEDIUM | TODO | whisper.cpp supports chunked processing. Improves perceived speed |
| 11 | **Emoji picker** — Basic emoji grid or search | MEDIUM | TODO | Android IME standard expectation |
| 12 | **Language selection** — Let user pick transcription language (currently hardcoded to "en" in jni.c) | MEDIUM | TODO | Multilingual models already available; just need UI + JNI param |
| 13 | **Custom rewrite prompts** — User-defined 6th card in the rewrite carousel. TextField where user types their own instruction. Saved styles persist in SharedPreferences | LOW | TODO | Builds on top of the swipe-right rewrite panel |
| 14 | ~~Rewrite undo~~ — One-tap revert after applying a rewrite. Stores original text so user can undo | LOW | DONE | `undoRewrite()` + `preRewriteText` in service |
| 15 | **No-timeout recording** — Allow generous pauses during recording without auto-stopping. Users need time to think mid-sentence | MEDIUM | TODO | Kaiboard is praised specifically for this. Gboard and Samsung cut users off on pause |
| 16 | **Privacy statement in settings** — One screen: "Audio never leaves device (local). API calls go to provider. No analytics." | LOW | TODO | Every user looking at voice keyboards worries about privacy. Marketing differentiator |
| 17 | **Model loading indicator** — Show "Warming up..." or progress bar when Whisper model loads into memory (2-5s on cold start) | MEDIUM | TODO | Users see dead mic button with no explanation. Quick win, moved from v0.11 |

---

### v0.11.0-beta — Polish & Performance
> Goal: Fast, reliable, accessible.

| # | Item | Category | Status | Notes |
|---|------|----------|--------|-------|
| 1 | ~~Version from BuildConfig~~ — Use `BuildConfig.VERSION_NAME` everywhere | UX | DONE | Completed in v0.9.1-beta |
| 2 | **Accessibility labels** — Add `contentDescription` to all icons and interactive elements | Accessibility | TODO | Currently most icons have hardcoded English strings, some are missing entirely |
| 3 | **TalkBack support** — Verify full keyboard navigation works with screen reader | Accessibility | TODO | Required for Play Store accessibility guidelines |
| 4 | **Connection timeout tuning** — Reduce cloud API timeouts when on slow networks. Add user-visible retry | Performance | TODO | Current: 15s connect, 30s read |
| 5 | **Keyboard height preference** — Let users adjust keyboard height (compact/normal/tall) | UX | TODO | Important for tablets and small phones |
| 6 | **Sound feedback option** — Optional click/beep sounds in addition to haptics | UX | TODO | Some users prefer audible feedback |
| 7 | **Landscape layout** — Optimize keyboard layout for horizontal orientation | UX | TODO | Current layout stretches awkwardly in landscape |
| 8 | ~~Remove dead code~~ — Deleted: MagicMenu.kt, ModelSelector.kt, BatteryMonitor.kt, MemoryMonitor.kt | Cleanup | DONE | Reduces APK size and confusion |
| 9 | **Benchmark improvements** — Show model inference time in settings for users to compare models | Performance | TODO | Data already available from whisper; just needs UI |

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

---

## Future Ideas (Post v1.0)

All evaluated features — recommended and deferred. Documented here so nothing is lost.

### RECOMMENDED — High value, reasonable effort

| Idea | Effort | Impact | Notes |
|------|--------|--------|-------|
| **Offline Magic Rewrite (Gemma/Phi via llama.cpp)** | High | High | Eliminate cloud dependency for rewrites entirely. Use llama.cpp JNI (same pattern as whisper.cpp). Gemma 2B Q4_K_M (~1.5GB, ~2GB RAM) handles grammar fix and concise well. Phi-3 Mini 3.8B (~2.3GB) for near-cloud quality. Add as 3rd provider option: "On-Device (Gemma 2B)" alongside Gemini/OpenAI. Trade-off: 10-30s vs 1-2s inference, creative rewrites weaker than cloud |
| **Moonshine models** | Medium | High | MIT-licensed, 5x faster than Whisper on mobile. Evaluate as alternative STT backend |
| **Whispered input mode** | Medium | Medium | Adjust audio preprocessing for lower input volumes. Lets users dictate quietly in public (offices, transit, libraries). Typeless proved this is practical and valued |
| **Context-aware formatting** | Medium | Medium | Format output differently based on target app (email vs chat vs notes). Wispr Flow's signature feature. Could integrate with Magic Rewrite — detect app context and auto-suggest appropriate style |
| **Dictation mode** | Low | Medium | Continuous recording with auto-punctuation. Natural extension of VAD + auto-punctuation |
| **Widget for quick transcription** | Low | Low | Home screen widget that records and copies to clipboard. Simple, useful for quick notes |

### EVALUATE — Could be valuable but needs research

| Idea | Effort | Impact | Notes |
|------|--------|--------|-------|
| **Tone-based punctuation** | Medium | High | FUTO's standout feature — detect ? and ! from voice inflection, not spoken words. Far more natural than saying "question mark". Requires audio analysis beyond Whisper's text output. Research whether whisper.cpp exposes confidence/timing data that could enable this |
| **Vulkan GPU acceleration** | High | Medium | whisper.cpp has Vulkan backend but unreliable on mobile GPUs. Monitor upstream progress. Could dramatically speed up on-device inference when stable |
| **Adaptive accuracy (per-user learning)** | High | Medium | CleverType learns user's accent and vocabulary over time. Requires local fine-tuning or vocabulary boosting. Complex but impactful for non-standard accents. Research whether whisper.cpp supports prompt conditioning or vocabulary biasing |
| **Privacy dashboard** | Low | Medium | Dedicated settings screen showing exactly what data goes where: audio (local/cloud), text for rewrite (API), API keys (device only), analytics (none). CleverType has this. Both a feature and a marketing differentiator |

### SKIP — Not worth the complexity for WhisperClick's focus

| Idea | Effort | Impact | Why Skip |
|------|--------|--------|----------|
| **Swipe/glide typing** | Very High | Medium | Multi-year effort to do well. FUTO and Gboard own this space. WhisperClick's QWERTY fallback should be functional, not competitive on typing. If users want swipe, they switch to Gboard |
| **Floating bubble overlay** | High | Medium | Wispr Flow's approach — works alongside any keyboard without switching IME. Cool but requires completely different architecture (accessibility service instead of IME). Fundamentally different product |
| **AI chatbot in keyboard** | Medium | Low | CleverType has custom AI assistants in the keyboard. Feature creep — WhisperClick should transcribe and rewrite, not be ChatGPT. Users have dedicated AI apps for chat |
| **GIF search / stickers** | Medium | Low | Standard in Gboard/SwiftKey but bloat for a voice-first keyboard. Not what WhisperClick users are looking for |
| **Multi-device sync** | High | Low | Sync settings/models across devices via cloud. Adds cloud infrastructure complexity for minimal gain. Users rarely switch phones mid-session |
| **Wear OS companion** | High | Low | Voice input from smartwatch. Tiny audience, massive development effort. Smartwatches already have basic voice input |
| **Image generation from keyboard** | Medium | Low | Some AI keyboards offer this. Pure bloat for a voice-first tool. Not aligned with WhisperClick's purpose |
| **Voice commands ("delete last word")** | Medium | Medium | Gboard has this on Pixel 6+ only. Powerful but complex to implement reliably — requires real-time intent parsing during transcription. Conflicts with "just transcribe what I said" expectations. Consider post-v2.0 if demand exists |

---

## Competitive Landscape

Summary of key competitors and WhisperClick's positioning.

| Competitor | Approach | Strengths | Weaknesses |
|-----------|----------|-----------|------------|
| **Wispr Flow** | Cloud overlay (not a keyboard) | Best AI cleanup, context-aware formatting, 100+ languages | Cloud-only, $12/month subscription, no offline |
| **FUTO Keyboard** | Full offline keyboard + voice | Complete daily driver, tone-based punctuation, $5 one-time | No AI rewrite, limited language support |
| **Kaiboard** | Offline voice keyboard | No timeout on pauses, open source | No text keyboard, tiny model only, basic UI |
| **WhisperInput** | Offline voice (3 modes) | Triple-mode (IME/panel/assistant), true privacy | No text keyboard, no rewrite, basic UI |
| **Dictate** | Cloud Whisper + GPT | Excellent accuracy, prompt-based rewording | Cloud-only, no offline, no typing keyboard |
| **Whisper Droid** | Cloud voice + QWERTY | Sub-second transcription, tiny app, has typing keyboard | Cloud-only, privacy depends on their servers |
| **Typeless** | Cloud AI companion | Whispered input, auto-formatting (lists), cleans speech | Cloud-only, no typing keyboard, companion approach |
| **CleverType** | AI keyboard | 98%+ accuracy in noisy environments, per-user learning | Subscription model, heavy cloud dependency |
| **Gboard** | Full keyboard + voice | Best ecosystem, offline voice, Gemini AI | Voice commands Pixel-only, times out on pauses, data collection |

**WhisperClick's unique position:** The only open-source keyboard combining on-device Whisper + cloud fallback + AI rewrite + clipboard history. No subscription. Privacy by default.

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
| Unified AI Provider settings | One provider picker + one API key instead of separate fields in STT and Rewrite sections. Eliminates "which key goes where" confusion | 2026-02 |
| HorizontalPager with animated height | 3-page pager (keyboard, rewrite, clipboard) with lerp-based height transition. Keyboard pins to bottom, panels use full height | 2026-02 |
| llama.cpp for offline rewrite (planned) | Same JNI pattern as whisper.cpp. Gemma 2B Q4_K_M (~1.5GB) for basic rewrite, Phi-3 Mini for quality. Third provider option alongside cloud | 2026-02 |

---

## Model Evaluation Plan — Offline Rewrite

Before committing to a specific model for offline Magic Rewrite, run hands-on benchmarks on the target device.

### Models to Test

| Model | Params | Quant Size | RAM Est. | License | Why Test |
|-------|--------|-----------|---------|---------|----------|
| **SmolLM2 1.7B** | 1.7B | ~1.0GB (Q4_K_M) | ~1.5GB | Apache 2.0 | Smallest viable model. If this works, it's the most phone-friendly option |
| **Gemma 2 2B** | 2.6B | ~1.5GB (Q4_K_M) | ~2.0GB | Apache 2.0 | Google's small model. Good instruction following. Primary candidate |
| **Phi-3 Mini 3.8B** | 3.8B | ~2.3GB (Q4_K_M) | ~3.0GB | MIT | Microsoft's strong small model. Near-cloud quality but heavier |
| **Qwen2.5 3B** | 3B | ~1.8GB (Q4_K_M) | ~2.5GB | Apache 2.0 | Alibaba's multilingual model. Good if we want multi-language rewrite |
| **TinyLlama 1.1B** | 1.1B | ~0.6GB (Q4_K_M) | ~1.0GB | Apache 2.0 | Ultra-small baseline. Test to see if 1B is even usable for rewrite |

### Test Protocol

For each model, test on the actual target device (Samsung Galaxy, ARM64):

1. **Download model** — GGUF format from Hugging Face
2. **Load time** — Cold start: how long to load model into memory?
3. **Inference speed** — Time to generate a rewrite response for:
   - Short text (~20 words): "hey so i was thinking we should probly meet up tmrw if your free"
   - Medium text (~50 words): A rambling voice-transcribed paragraph with filler words
   - Long text (~100 words): A full email-length dictation
4. **Quality assessment** — For each test text, evaluate all 5 rewrite styles:
   - Clean (grammar/spelling fix): Does it fix errors without changing meaning?
   - Professional: Does it sound professional without being robotic?
   - Casual: Does it sound natural?
   - Concise: Does it actually shorten the text meaningfully?
   - Emojify: Does it add relevant emojis?
5. **RAM usage** — Monitor actual RAM consumption during inference
6. **Battery impact** — Note battery drain during a 10-minute rewrite session
7. **Stability** — Does it crash, OOM, or produce garbage output?

### Evaluation Criteria

| Criteria | Weight | Threshold |
|----------|--------|-----------|
| Clean rewrite quality | 30% | Must fix grammar without hallucinating new content |
| Inference speed | 25% | Under 15 seconds for short text, under 30 for medium |
| RAM usage | 20% | Must not OOM on 6GB RAM device with Whisper also loaded |
| Model download size | 15% | Under 2GB preferred, under 3GB acceptable |
| Style variant quality | 10% | Professional and Concise must be noticeably different from Clean |

### Decision Gate

After testing, pick ONE model to ship as the default offline rewrite engine. Document:
- Which model was selected and why
- Benchmark numbers on target device
- Any quality gaps vs cloud rewrite (Gemini/OpenAI)
- Whether to offer multiple model sizes (like we do for Whisper) or just ship one

---

## How to Contribute

1. Pick an item from the current milestone
2. Create a feature branch: `feature/short-description`
3. Implement with tests
4. PR against `main` — CI must pass
5. Tag releases: `vX.Y.Z-beta` during beta, `vX.Y.Z` for stable
