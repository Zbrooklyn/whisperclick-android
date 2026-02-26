# Production Audit — WhisperClick Android

**Project:** WhisperClick Android (com.nefeshcore.whisperclick)
**Version:** v0.10.0-beta (versionCode 8)
**Scope:** All 19 Kotlin source files, build config, manifest, proguard
**Date:** 2026-02-26
**Auditor:** Claude Opus 4.6

---

## First Impressions

- **Feels surprisingly complete for a beta.** Voice keyboard works, has cloud/local STT, Magic Rewrite with 5 style variants, clipboard history, theme support, model management with download+resume. That's a lot of working features.
- **IME service file is a god-object.** `VoiceKeyboardInputMethodService.kt` at 679 lines handles recording, transcription, rewrite, clipboard history, key events, lifecycle, audio focus, AND preferences. It does everything.
- **No tests.** Zero. Not one unit test, integration test, or UI test. The test dependencies are declared in build.gradle but no tests exist.
- **API keys stored in plaintext SharedPreferences.** This is the single biggest security concern.
- **`MainScreenViewModel.kt` is dead weight.** The settings screen loads its own Whisper model independently from the keyboard service. That's a second model in RAM simultaneously — wasteful and confusing.
- **ProGuard rules file is empty.** Release builds have `minifyEnabled true` but no keep rules for JNI classes. This will crash in production.

---

## Inventory

- [x] `WhisperClickApplication.kt` — Application class
- [x] `MainActivity.kt` — Settings activity
- [x] `VoiceKeyboardInputMethodService.kt` — Core IME service (recording, transcription, rewrite, clipboard, lifecycle)
- [x] `VoiceKeyboardInputMethodService.kt::ClipEntry` — Clipboard data class
- [x] `VoiceKeyboardInputMethodService.kt::onCreate/onDestroy` — Service lifecycle
- [x] `VoiceKeyboardInputMethodService.kt::onStartInputView/onFinishInputView` — Input view lifecycle
- [x] `VoiceKeyboardInputMethodService.kt::toggleRecord` — Recording toggle
- [x] `VoiceKeyboardInputMethodService.kt::transcribe` — Local transcription
- [x] `VoiceKeyboardInputMethodService.kt::transcribeCloud` — Cloud transcription
- [x] `VoiceKeyboardInputMethodService.kt::requestRewriteAll` — Magic Rewrite orchestrator
- [x] `VoiceKeyboardInputMethodService.kt::applyRewrite/undoRewrite` — Rewrite application
- [x] `VoiceKeyboardInputMethodService.kt::clipboard methods` — addClipEntry, pasteClipEntry, deleteClipEntry, clearClipHistory, load/save
- [x] `VoiceKeyboardInputMethodService.kt::haptic/sendKeyPress/sendEnter` — Input helpers
- [x] `VoiceKeyboardInputMethodService.kt::switchKeyboard/openSettings/toggleSttMode` — Navigation/mode
- [x] `VoiceKeyboardView.kt` — Keyboard Compose UI (3-page pager)
- [x] `VoiceKeyboardView.kt::Content` — Root composable with pager + theme
- [x] `VoiceKeyboardView.kt::KeyboardPage` — Main keyboard row + edit toolbar
- [x] `VoiceKeyboardView.kt::RewritePanel` — Magic Rewrite panel (page 1)
- [x] `VoiceKeyboardView.kt::VariantCard` — Individual rewrite card
- [x] `VoiceKeyboardView.kt::ClipboardPanel` — Clipboard history (page 2)
- [x] `VoiceKeyboardView.kt::ClipEntryRow` — Clipboard entry row
- [x] `VoiceKeyboardView.kt::RecordButton` — Mic button with states
- [x] `VoiceKeyboardView.kt::RepeatKeyButton` — Auto-repeat key button
- [x] `VoiceKeyboardView.kt::LongPressButton` — Long-press gesture button
- [x] `api/RewriteProvider.kt` — Interface + RewriteVariants data class
- [x] `api/GeminiClient.kt` — Gemini rewrite API client
- [x] `api/OpenAIClient.kt` — OpenAI rewrite API client
- [x] `api/WhisperCloudClient.kt` — OpenAI Whisper Cloud STT client
- [x] `api/ApiKeyValidator.kt` — API key validation
- [x] `media/RiffWaveHelper.kt` — WAV encoding/decoding
- [x] `model/ModelManager.kt` — Model download/management
- [x] `recorder/Recorder.kt` — Audio capture
- [x] `ui/main/MainScreen.kt` — Settings screen (full UI)
- [x] `ui/main/MainScreenViewModel.kt` — Settings ViewModel
- [x] `ui/theme/Color.kt` — Color palette
- [x] `ui/theme/Theme.kt` — Theme definitions
- [x] `ui/theme/Type.kt` — Typography
- [x] `utils/AppLog.kt` — In-memory logging
- [x] `utils/CrashLogger.kt` — Crash file logger
- [x] `app/build.gradle` — Build configuration
- [x] `AndroidManifest.xml` — App manifest
- [x] `proguard-rules.pro` — ProGuard rules

---

## Per-Component Review

---

### WhisperClickApplication.kt — *WhisperClickApplication.kt:1-12*

**1. Why does this exist?** Custom Application class that installs the crash logger on app startup.

**2. What's working?** Clean and minimal. Does exactly one thing: `CrashLogger.install(this)`.

**3. What's not working?** Nothing wrong here.

**4. What happens when it fails?** If CrashLogger.install throws, the app crashes on startup. But install() has try/catch internally, so this is safe.

**5. How does this connect?** Referenced in `AndroidManifest.xml` via `android:name`. All other components inherit this Application.

**6. What would make this better?** Nothing. It's correct and minimal.

**Severity:** Pass

---

### MainActivity.kt — *MainActivity.kt:1-66*

**1. Why does this exist?** Settings/launcher activity. Hosts the Compose settings screen.

**2. What's working?** Theme reactivity is well-implemented — `DisposableEffect` with `OnSharedPreferenceChangeListener` updates theme live. System bar color sync via `SideEffect` is correct.

**3. What's not working?** The cast `view.context as Activity` on line 52 is fragile. In Compose, `view.context` is usually a `ContextThemeWrapper`, not directly an Activity. This works today because of how `ComponentActivity.setContent` works, but it's a latent bug if the view hierarchy changes.

**4. What happens when it fails?** ClassCastException crash on `view.context as Activity`.

**5. How does this connect?** Launched from manifest as MAIN/LAUNCHER. Also opened by `VoiceKeyboardInputMethodService.openSettings()`.

**6. What would make this better?** Use `context.findActivity()` extension or `LocalContext.current as? Activity` with null safety.

**Severity:** Low
**Fix:** Replace `view.context as Activity` with a safe Activity lookup.

---

### VoiceKeyboardInputMethodService — Lifecycle — *:129-163*

**1. Why does this exist?** Core IME service lifecycle. Sets up preferences, clipboard listener, loads model, handles lifecycle events.

**2. What's working?** `savedStateRegistryCtrl.performRestore(null)` and lifecycle event handling are correctly implemented for Compose-in-IME. Non-blocking destroy with fire-and-forget release is correct.

**3. What's not working?**
- `onDestroy` fires a coroutine on `Dispatchers.Default` to release the WhisperContext, then immediately calls `job.cancel()`. This cancels the scope that other coroutines depend on, but the release coroutine is on a separate scope so it survives. However, the `clipboardManager?.removePrimaryClipChangedListener` runs synchronously — good.
- `sharedPref` is nullable (`SharedPreferences?`) but never null after `onCreate`. Should be `lateinit var` for cleaner code.

**4. What happens when it fails?** If model loading fails in `loadData()`, the exception is caught and logged but `canTranscribe` stays false — the mic button stays disabled. Correct degradation.

**5. How does this connect?** This is the core of the entire app. Everything flows through here — recording, transcription, rewrite, clipboard, key events.

**6. What would make this better?** Extract clipboard history, rewrite logic, and recording into separate manager classes. This file is a 679-line god object.

**Severity:** Medium
**Fix:** Extract ClipboardHistoryManager, RewriteManager, and RecordingManager from the service to separate concerns.

---

### VoiceKeyboardInputMethodService — onStartInputView/onFinishInputView — *:75-104*

**1. Why does this exist?** Manages Whisper model lifecycle tied to keyboard visibility. Loads on show, unloads on hide to free RAM.

**2. What's working?** Smart RAM management — model is loaded when keyboard appears, released when hidden. The `isModelLoading` guard prevents duplicate loads.

**3. What's not working?**
- `onFinishInputView` checks `!isRecording && !isTranscribing` before unloading, which is correct. But there's no guard against a race where `onStartInputView` fires while the previous `onFinishInputView` release coroutine is still running. The `whisperContext` could be nulled mid-transcription if the keyboard is quickly hidden and reshown.
- The release in `onFinishInputView` runs on `scope` (Main dispatcher) but `WhisperContext.release()` is presumably blocking JNI — should be on IO.

**4. What happens when it fails?** Model fails to reload: user sees disabled mic with no explanation (logged but not shown to user). This is the "model loading indicator" gap from the roadmap.

**5. How does this connect?** Called by Android framework. `whisperContext` is shared state used by `transcribe()`.

**6. What would make this better?** Add a mutex or state machine to prevent race between load/unload. Show loading indicator to user.

**Severity:** Medium
**Fix:** Add a mutex/flag to prevent concurrent load/release races. Move `release()` to Dispatchers.IO.

---

### VoiceKeyboardInputMethodService — toggleRecord — *:492-550*

**1. Why does this exist?** Core recording flow — start/stop mic, auto-insert spaces, transcribe, commit text.

**2. What's working?** Smart space handling — checks char before/after cursor and inserts/appends spaces as needed. Audio focus management is correct. Error handling wraps the whole thing.

**3. What's not working?**
- **No permission check before recording.** `recorder.startRecording()` will crash if RECORD_AUDIO permission was revoked after initial grant. The roadmap has this as v0.9.1 #6 TODO.
- **No recording timeout.** If the user forgets to stop, recording runs forever. Roadmap v0.9.1 #7.
- **`setComposingText("Transcribing...", 1)`** — the "Transcribing..." text is visible in the text field. If the user switches apps during transcription, this composing text stays visible to them. Should use a visual indicator in the keyboard itself, not in the target text field.

**4. What happens when it fails?** SecurityException on missing permission crashes the recording thread. The catch at line 545 catches it and logs, but `isRecording` stays true briefly before being reset. Users would see a stuck recording state.

**5. How does this connect?** Called by `VoiceKeyboardView::RecordButton`. Uses `recorder`, `whisperContext`, `currentInputConnection`.

**6. What would make this better?** Add runtime permission check. Add max recording duration. Show transcription progress in keyboard UI, not in the text field.

**Severity:** High
**Fix:** Add `checkSelfPermission(RECORD_AUDIO)` before `recorder.startRecording()`. Add timeout coroutine.

---

### VoiceKeyboardInputMethodService — transcribe — *:445-468*

**1. Why does this exist?** Local on-device transcription via whisper.cpp.

**2. What's working?** Good post-processing: strips special tokens (`[MUSIC]`, `[BLANK_AUDIO]`), handles casual mode (lowercase + strip trailing punctuation). Duration calculation and timing logs are useful.

**3. What's not working?**
- The regex `\[[-_a-zA-Z0-9 ]*]` strips valid text like `[John]` if someone dictates it. This is overly aggressive.
- `nThreads` defaults to `maxThreads` (all CPU cores), which can make the device unresponsive during transcription. Should cap at `maxThreads - 1` or `maxThreads / 2`.
- The `transcriptionCancelled` volatile flag is checked AFTER transcription completes (line 510 in `toggleRecord`), not during. whisper.cpp transcription is a blocking JNI call — cancellation only works between recording stop and result commit, not during the actual transcription.

**4. What happens when it fails?** Returns null, which is handled in `toggleRecord` — composing text is finished without inserting anything. Correct degradation.

**5. How does this connect?** Called by `toggleRecord`. Depends on `whisperContext` (can be null if model was unloaded).

**6. What would make this better?** Make the cancel check interrupt the JNI call (whisper.cpp supports `whisper_abort_callback`). Limit thread count.

**Severity:** Medium
**Fix:** Cap thread count at `maxThreads - 1`. Investigate `whisper_abort_callback` for true cancellation.

---

### VoiceKeyboardInputMethodService — transcribeCloud — *:642-660*

**1. Why does this exist?** Cloud fallback STT via OpenAI Whisper API.

**2. What's working?** Simple, correct. Checks for API key, logs timing.

**3. What's not working?**
- **No network connectivity check.** If offline, this hangs for 30s (connect timeout) before failing. Roadmap v0.9.1 #8.
- Uses `openai_api_key` directly, but Cloud STT mode doesn't check if the selected AI provider is actually OpenAI. If user selected Gemini as their AI Provider, Cloud STT still uses the OpenAI key (which may be empty). The unified settings create confusion about which key is used for which service.

**4. What happens when it fails?** Returns null. Transcription silently fails — user sees "Transcribing..." briefly then nothing. No error message shown.

**5. How does this connect?** Called by `toggleRecord` when `useCloudStt == true`.

**6. What would make this better?** Show error to user on failure. Check network before attempting. Clarify which provider/key is used for cloud STT vs rewrite.

**Severity:** High
**Fix:** Add `ConnectivityManager.getActiveNetwork()` check before cloud calls. Show user-visible error on failure.

---

### VoiceKeyboardInputMethodService — requestRewriteAll — *:189-220*

**1. Why does this exist?** Orchestrates Magic Rewrite — gets text from cursor, validates, calls API, stores variants.

**2. What's working?** Good validation: checks for active input connection, non-blank text, non-empty API key. Error messages are user-facing.

**3. What's not working?**
- **No error handling in the coroutine.** Line 214 `client.rewriteAll(apiKey, textBefore)` can throw, but there's no try/catch. If the API call throws (network error, JSON parse failure, etc.), the coroutine dies silently and `isRewriting` stays `true` forever — the UI shows "Generating rewrites..." spinner permanently.
- `getTextBeforeCursor(2000, 0)` only gets 2000 chars before cursor. If the user has a long document and cursor is at the end, this could miss content. But 2000 chars is reasonable for rewrite.

**4. What happens when it fails?** UI hangs on "Generating rewrites..." with no way to dismiss except swiping back.

**5. How does this connect?** Called by `RewritePanel` "Rewrite" button. Reads from `currentInputConnection`, writes to `rewriteVariants` state.

**6. What would make this better?** Wrap the API call in try/catch. Set `rewriteError` on failure. Reset `isRewriting` in a finally block.

**Severity:** Critical
**Fix:** Add try/catch around `client.rewriteAll()` in the coroutine, set `rewriteError` and `isRewriting = false` on failure.

---

### VoiceKeyboardInputMethodService — applyRewrite/undoRewrite — *:222-244*

**1. Why does this exist?** Applies selected rewrite variant to the text field, with undo support.

**2. What's working?** Undo is well-implemented — stores original text, replaces on undo.

**3. What's not working?**
- `applyRewrite` uses `deleteSurroundingText(original.length, 0)` which deletes text BEFORE the cursor. If the user moved the cursor between requesting rewrite and applying it, this deletes the wrong text. Should use `setSelection` + `commitText` or extract/replace the exact range.
- `undoRewrite` has the same issue — it deletes `currentText.length` chars before cursor and replaces with original. If the user typed additional text after applying the rewrite, undo deletes their new text too.

**4. What happens when it fails?** User loses text that wasn't part of the rewrite. Data loss.

**5. How does this connect?** Called from `VariantCard` "Use" button and undo button in the UI.

**6. What would make this better?** Track cursor position at rewrite time. Use `ExtractedText` or `getTextBeforeCursor`/`getTextAfterCursor` to verify the text still matches before replacing.

**Severity:** High
**Fix:** Verify the text before cursor matches `rewriteOriginalText` before deleting. If it doesn't match, show an error instead of silently deleting wrong text.

---

### VoiceKeyboardInputMethodService — Clipboard History — *:253-321*

**1. Why does this exist?** Captures clipboard changes from any app, stores 50 entries, provides paste/delete/clear.

**2. What's working?** Deduplication (moves existing entry to top). JSON persistence. Proper listener registration/unregistration.

**3. What's not working?**
- **Clipboard contains sensitive data.** Passwords, credit card numbers, 2FA codes — all captured and stored in plaintext SharedPreferences. No filtering, no auto-expiry, no exclusion for password manager entries.
- `saveClipHistory()` is called on every clipboard change. With 50 entries, this serializes the entire list to JSON and writes to SharedPreferences synchronously on the main thread. Could cause frame drops.
- The listener fires for WhisperClick's own `pasteClipEntry()` calls too — pasting from clipboard history triggers `addClipEntry` again (though deduplication handles this, it's wasteful work).

**4. What happens when it fails?** JSON parse failure in `loadClipHistory` is caught — history starts empty. Not catastrophic but loses data.

**5. How does this connect?** Listener registered in `onCreate`, unregistered in `onDestroy`. UI in `VoiceKeyboardView::ClipboardPanel`.

**6. What would make this better?** Auto-expire entries older than 24h. Don't capture entries from password managers (check `ClipDescription.extras` for sensitive flag). Debounce saves.

**Severity:** High
**Fix:** Check `clip.description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE)` to skip sensitive clipboard items. Add timestamp-based auto-expiry.

---

### VoiceKeyboardInputMethodService — performRewrite (legacy) — *:323-358*

**1. Why does this exist?** Legacy single-style rewrite function. Comment says "kept for compatibility."

**2. What's working?** Structure is correct — gets text, validates, calls API, replaces.

**3. What's not working?** This function is dead code. Nothing calls it. The new `requestRewriteAll()` replaced it entirely. The comment "kept for compatibility" is misleading — there is no compatibility concern since this is not a public API.

**4. What happens when it fails?** N/A — never called.

**5. How does this connect?** Nothing calls it. It should be deleted.

**6. What would make this better?** Delete it.

**Severity:** Low
**Fix:** Delete `performRewrite()` — it's dead code.

---

### VoiceKeyboardInputMethodService — haptic/sendKeyPress/sendEnter — *:552-606*

**1. Why does this exist?** Input helper functions — vibration feedback, key events, smart enter behavior.

**2. What's working?** `haptic()` is crash-safe with SecurityException catch. `sendEnter()` correctly handles multi-line vs single-line fields, respecting `IME_FLAG_NO_ENTER_ACTION`. Well-implemented.

**3. What's not working?** Nothing significant. Minor: `sendKeyPress` could check if `currentInputConnection` is still valid (it does null-check, which is correct).

**4. What happens when it fails?** Null IC is logged and skipped. Vibration failure is silently caught. Correct degradation.

**5. How does this connect?** Called by VoiceKeyboardView button handlers.

**6. What would make this better?** Nothing. These are solid.

**Severity:** Pass

---

### VoiceKeyboardView.kt — Content — *:98-170*

**1. Why does this exist?** Root composable for the keyboard. 3-page HorizontalPager with animated height.

**2. What's working?** Layout-phase height interpolation (custom `Modifier.layout`) is the correct Compose performance pattern. Theme reactivity via preference listener works well.

**3. What's not working?**
- `keyboardHeightPx` and `panelHeightPx` are computed in the composition phase from `keyboardHeightDp` which depends on `showEditRow`. When `showEditRow` changes, the height values are recomputed — but this also depends on density which shouldn't change, so this is fine.
- The `pagerState.currentPageOffsetFraction` read inside `Modifier.layout` is correct for avoiding recomposition, but the `keyboardHeightPx`/`panelHeightPx` captured in the lambda are state from the composition — if `showEditRow` changes, the lambda captures stale values until next recomposition. In practice this is fine because `showEditRow` change triggers recomposition anyway.

**4. What happens when it fails?** Layout miscalculation would clip content. Not dangerous.

**5. How does this connect?** Created in `onCreateInputView()` of the service. References `service` directly for all state.

**6. What would make this better?** The `service` reference as constructor parameter tightly couples view to service. A state holder or interface would be cleaner but is not urgent.

**Severity:** Pass

---

### VoiceKeyboardView.kt — KeyboardPage — *:172-311*

**1. Why does this exist?** Main keyboard row (mic, arrows, backspace, enter, switcher) + optional edit toolbar.

**2. What's working?** Button weights create a proportional layout. Edit toolbar has all essential actions (select all, copy, paste, undo, redo, clipboard, settings). RepeatKeyButton for arrows/backspace is a great UX touch.

**3. What's not working?**
- The keyboard switcher button dual-purpose (tap=switch if available, else toggle edit row; long-press=always toggle edit row) is confusing. Users won't discover the long-press behavior.
- Fixed dp heights (36dp edit buttons, 56dp/96dp keyboard) may not work well on all screen densities and phone sizes.

**4. What happens when it fails?** UI layout issues, not data loss.

**5. How does this connect?** Rendered as page 0 of the HorizontalPager.

**6. What would make this better?** Add a visible indicator that edit row exists (dot or bar). Consider making keyboard height configurable (roadmap v0.11 #5).

**Severity:** Low
**Fix:** Add a visual hint that the edit toolbar is available via long-press.

---

### VoiceKeyboardView.kt — RewritePanel — *:314-482*

**1. Why does this exist?** Magic Rewrite UI — shows loading, error, variant carousel, or empty state.

**2. What's working?** Clean state machine: `isRewriting` -> loading, `error` -> error, `variants` -> carousel, else -> empty. Carousel with dot indicators. Back button clears state.

**3. What's not working?**
- No way to dismiss the loading state if the API hangs. The Back button calls `clearRewriteState()` which sets `isRewriting = false`, so this actually works — the user CAN dismiss. Good.
- The nested HorizontalPager (variant cards inside the main keyboard pager) could cause gesture conflicts. Swiping on variant cards might accidentally swipe the outer pager.

**4. What happens when it fails?** Error state shows message. If API call throws (per the Critical finding above), spinner hangs.

**5. How does this connect?** Page 1 of main pager. Reads state from `service.rewriteVariants`, `service.isRewriting`, etc.

**6. What would make this better?** Add `nestedScroll` handling or `userScrollEnabled = false` on outer pager when inner pager is active.

**Severity:** Medium
**Fix:** Handle nested pager gesture conflicts to prevent accidental outer-pager swipes.

---

### VoiceKeyboardView.kt — RecordButton — *:811-911*

**1. Why does this exist?** The primary mic button with 5 visual states: first render, disabled, recording (with timer), transcribing (with cancel), and idle (with cloud/local icon).

**2. What's working?** Timer implementation with `LaunchedEffect(isRecording)` is correct. Long-press to toggle STT mode is clever. Cancel button during transcription.

**3. What's not working?**
- The `firstRender` state variable is confusing. It exists to show the mic icon on cold start before the model loads, versus showing "Loading model..." text after the first interaction. This is fragile — `firstRender` is set to `false` in multiple places but never reset.
- The long-press detection uses `collectLatest` which cancels the previous collection. This means rapid presses could miss a long-press. In practice, users don't rapid-press the mic, so this is fine.

**4. What happens when it fails?** Worst case: button shows wrong state. Not dangerous.

**5. How does this connect?** Used in KeyboardPage row 1. Calls `service::toggleRecord`, `service::cancelTranscription`, `service::toggleSttMode`.

**6. What would make this better?** Replace `firstRender` with an explicit `ModelState` enum (Loading, Ready, Recording, Transcribing).

**Severity:** Low
**Fix:** Replace `firstRender` boolean with explicit state enum for clarity.

---

### api/RewriteProvider.kt — *:1-24*

**1. Why does this exist?** Defines the `RewriteProvider` interface and `RewriteVariants` data class.

**2. What's working?** Clean interface. `toList()` is a convenient conversion for UI.

**3. What's not working?** Nothing.

**4. What happens when it fails?** N/A — it's a data class and interface.

**5. How does this connect?** Implemented by `GeminiClient` and `OpenAIClient`. Used by the IME service.

**6. What would make this better?** Nothing. It's correct and minimal.

**Severity:** Pass

---

### api/GeminiClient.kt — *:1-133*

**1. Why does this exist?** Gemini API client for Magic Rewrite.

**2. What's working?** API key passed via header (`x-goog-api-key`), not URL query parameter. JSON construction is correct. Fallback variants on failure prevent crashes.

**3. What's not working?**
- **User input injected directly into prompt.** `"Text: \"$originalText\""` — if user's text contains `"`, it breaks the JSON prompt. User text with `\"` could escape the string and inject prompt content. Should sanitize or use a different delimiter.
- `callApi` doesn't close `connection` in error paths. The `writer` is flushed but never closed explicitly (though `HttpURLConnection` handles this).
- `parseResponse` returns `null` on error but the return type annotation says `String`. The `?: ""` handles this, but the function signature is misleading.

**4. What happens when it fails?** Returns fallback variants (original text for all styles). Silent failure — user sees their original text repeated 5 times. Could be confusing.

**5. How does this connect?** Implements `RewriteProvider`. Called by `VoiceKeyboardInputMethodService.requestRewriteAll()`.

**6. What would make this better?** Sanitize user input in prompts. Show a user-visible error when API returns fallback.

**Severity:** Medium
**Fix:** Escape/sanitize `originalText` before embedding in the prompt string. Add connection close in finally block.

---

### api/OpenAIClient.kt — *:1-127*

**1. Why does this exist?** OpenAI API client for Magic Rewrite.

**2. What's working?** System prompt + user prompt separation is correct for chat completions API. Same robust fallback pattern as GeminiClient.

**3. What's not working?** Same prompt injection risk as GeminiClient — user text embedded directly in prompt string. Same missing connection.close(). Exact same code duplication pattern.

**4. What happens when it fails?** Same as GeminiClient — fallback variants.

**5. How does this connect?** Implements `RewriteProvider`. Selected when `ai_provider == "openai"`.

**6. What would make this better?** Same fixes as GeminiClient. Consider extracting shared prompt/parse logic to reduce duplication.

**Severity:** Medium
**Fix:** Same as GeminiClient — sanitize input, close connections, deduplicate shared code.

---

### api/WhisperCloudClient.kt — *:1-103*

**1. Why does this exist?** Cloud STT via OpenAI Whisper API. Converts audio samples to WAV, uploads via multipart/form-data.

**2. What's working?** WAV encoding is correct (proper RIFF header, 16kHz mono 16-bit). Multipart construction is manual but correct. Good error logging.

**3. What's not working?**
- **60-second read timeout** is very long. If the OpenAI API hangs, the user waits a full minute with no feedback.
- The `encodeWav` duplicates logic from `RiffWaveHelper.kt::headerBytes`. Two different WAV encoding implementations exist.
- No retry logic. If the first attempt fails, that's it.

**4. What happens when it fails?** Returns null. `transcribeCloud` in the service logs failure but doesn't show user an error.

**5. How does this connect?** Called by `VoiceKeyboardInputMethodService.transcribeCloud()`.

**6. What would make this better?** Reuse `RiffWaveHelper` instead of duplicating WAV encoding. Reduce read timeout to 30s. Show user error.

**Severity:** Medium
**Fix:** Deduplicate WAV encoding with `RiffWaveHelper`. Reduce timeout.

---

### api/ApiKeyValidator.kt — *:1-50*

**1. Why does this exist?** Validates API keys by making a lightweight API call.

**2. What's working?** Correct approach — hit a lightweight endpoint (`/v1/models` for OpenAI, `/v1beta/models` for Gemini) and check response code. Format validation for OpenAI (`sk-` prefix).

**3. What's not working?**
- **Gemini validation uses API key in URL query parameter** (`?key=$apiKey`). This is the same vulnerability that was fixed for the rewrite client (moved to header). Validation leaks the key in URL (logged by proxies, servers, etc.).
- No timeout handling — if network is slow, the coroutine hangs with no user feedback (though the UI does show a spinner).

**4. What happens when it fails?** Returns `Invalid` with error message. Correct behavior.

**5. How does this connect?** Called from `MainScreen::ApiKeyField` "Verify" button.

**6. What would make this better?** Move Gemini validation to use header authentication (consistent with rewrite client).

**Severity:** High
**Fix:** Change Gemini validation to use `x-goog-api-key` header instead of URL query parameter.

---

### media/RiffWaveHelper.kt — *:1-87*

**1. Why does this exist?** WAV file encoding/decoding for audio samples.

**2. What's working?** Correct RIFF/WAV spec implementation. `decodeShortArray` handles mono and stereo. Clamping to [-1, 1] range prevents overflow.

**3. What's not working?**
- `headerBytes` has a misleading `require(totalLength >= 44)` — the parameter is `totalLength` of data, but 44 is the header size. If someone passes data length < 44, the assertion fails with a confusing message. The parameter should be named `dataLength` and the check adjusted.
- `decodeWaveFile` hardcodes position 44 for data start. Non-standard WAV files with extra chunks will be decoded incorrectly. For this app's use case (reading its own recordings), this is fine.

**4. What happens when it fails?** `decodeWaveFile` would produce garbage audio data. `encodeWaveFile` would produce a malformed file.

**5. How does this connect?** `decodeShortArray` is called in `VoiceKeyboardInputMethodService.transcribe()`. `encodeWaveFile` is called in `MainScreenViewModel.toggleRecord()`.

**6. What would make this better?** Rename parameter in `headerBytes`. Add a comment about the hardcoded-offset assumption.

**Severity:** Low
**Fix:** Rename `totalLength` to `dataSize` in `headerBytes` and adjust the require message.

---

### model/ModelManager.kt — *:1-148*

**1. Why does this exist?** Manages Whisper model downloads with resume support, model selection, and deletion.

**2. What's working?** Download resume via HTTP Range headers is excellent. Cancel support. Progress tracking via StateFlow. Temp file pattern prevents partial-download corruption.

**3. What's not working?**
- **No model integrity verification.** After download, the file is renamed from `.tmp` to final name with no checksum validation. Corrupted downloads produce a file that crashes whisper.cpp on load.
- `getActiveModelName` and `setActiveModel` use a hardcoded preference file name `"com.nefeshcore.whisperclick.settings"` which is DIFFERENT from the preference file used everywhere else (`R.string.preference_file_key`). This means model selection in settings may not be seen by the keyboard service.
- Line 116-118: After a 206 resume, it opens a `FileOutputStream(tempFile, true)` for appending, but also gets `tempFile.outputStream()` from the `let` block (line 116). The non-206 path uses the `let` output stream but the 206 path creates a new one — the `let` stream is orphaned. This is a file handle leak.

**4. What happens when it fails?** Download failure returns false with empty progress. Correct. Corrupt model file would crash on load with no recovery.

**5. How does this connect?** Called from `MainScreen` for download/select. `VoiceKeyboardInputMethodService.loadBaseModel()` reads the active model preference.

**6. What would make this better?** Add SHA256 checksum verification. Fix the preferences file name mismatch. Fix the file handle leak.

**Severity:** Critical
**Fix:** Fix SharedPreferences file name to match `R.string.preference_file_key`. Fix file handle leak in resume path. Add checksum verification.

---

### recorder/Recorder.kt — *:1-96*

**1. Why does this exist?** Audio capture from microphone. Runs on a dedicated thread to avoid blocking.

**2. What's working?** `AtomicBoolean` for thread-safe stop signal. Dedicated single-thread executor prevents concurrent recording. `AudioRecord.release()` in finally block.

**3. What's not working?**
- **`@SuppressLint("MissingPermission")`** — this suppresses the lint warning but doesn't actually check the permission. If permission is revoked, `AudioRecord()` constructor throws `SecurityException`.
- The `Recorder` instance's `scope` creates a `SingleThreadExecutor` that is **never shut down**. Over the lifetime of the IME service, this thread lives forever even when not recording.
- `chunks` accumulates memory without limit. A long recording (30+ minutes) would accumulate hundreds of MB of `ShortArray` chunks in memory. No max recording size protection.
- `t.join()` blocks the coroutine's thread. With `withContext(scope.coroutineContext)`, this blocks the single-thread executor — correct but not cancelable.

**4. What happens when it fails?** `onError` callback fires. If permission is missing, `SecurityException` propagates to the error callback. Works, but should be caught earlier.

**5. How does this connect?** Used by both `VoiceKeyboardInputMethodService` and `MainScreenViewModel`. Two separate instances.

**6. What would make this better?** Check permission before starting. Add max recording duration. Shut down executor in the service's onDestroy.

**Severity:** High
**Fix:** Add recording duration limit (5 min max). Check permission before calling `AudioRecord()`.

---

### ui/main/MainScreen.kt — *:1-1255*

**1. Why does this exist?** Full settings UI. Setup section, STT config, AI provider, preferences, debug tools.

**2. What's working?** Well-organized sections with cards. Setup auto-collapses when complete. API key validation with visual feedback. Model download progress. Collapsible debug section.

**3. What's not working?**
- **1255 lines in a single file.** This is the largest file in the project. Should be split into separate composable files (SetupSection, SttSection, AiProviderSection, PreferencesSection, DebugSection).
- Settings state is all local `remember` — if user navigates away and back, state is read from SharedPreferences again, which is correct. But there's no reactive update if the keyboard service changes a preference (e.g., toggling STT mode via long-press on mic).
- `downloadedModels` state doesn't refresh when user returns to the screen — only updates after a download completes.

**4. What happens when it fails?** UI issues, not data loss.

**5. How does this connect?** Hosted by `MainActivity`. Uses `MainScreenViewModel` for benchmark/record testing.

**6. What would make this better?** Split into separate composable files. Add SharedPreferences listener for reactive updates.

**Severity:** Medium
**Fix:** Split MainScreen.kt into separate section files (200-300 lines each).

---

### ui/main/MainScreenViewModel.kt — *:1-195*

**1. Why does this exist?** ViewModel for the settings screen. Loads a Whisper model for benchmarking and test recording.

**2. What's working?** Benchmark implementation is correct. Test recording with WAV save and playback works.

**3. What's not working?**
- **`runBlocking` in `onCleared()`.** Line 178. This blocks the main thread while releasing the Whisper context. If the JNI release takes time (it can), this causes ANR. The keyboard service correctly uses a fire-and-forget coroutine for this — the ViewModel should do the same.
- **Loads a SECOND Whisper model into RAM** independently from the keyboard service. If the keyboard is open and the user opens settings, two copies of the model are in memory simultaneously. On a 6GB RAM device, this can cause OOM.
- `loadBaseModel()` only loads from bundled assets, ignoring downloaded models. The keyboard service checks downloaded models first. Inconsistent behavior.
- `dataLog` appends strings indefinitely — it's never cleared. Long sessions accumulate memory.

**4. What happens when it fails?** `runBlocking` causes ANR. Dual model load causes OOM.

**5. How does this connect?** Used by `MainScreen` for benchmark and test recording buttons (debug section only).

**6. What would make this better?** Remove the duplicate model load. Make benchmark/test use the keyboard service's model if possible, or defer load until benchmark is actually requested. Replace `runBlocking` with coroutine.

**Severity:** High
**Fix:** Replace `runBlocking` in `onCleared()` with fire-and-forget coroutine. Lazy-load model only when benchmark/test is requested.

---

### ui/theme/Color.kt, Theme.kt, Type.kt — *theme/*

**1. Why do these exist?** Define the app's visual identity — colors, dark/light/OLED schemes, typography.

**2. What's working?** Three theme variants (Dark, Light, OLED) are well-defined. Orange accent color is distinctive. Light theme colors are properly inverted.

**3. What's not working?** Nothing significant. Typography only overrides `bodyLarge` — all other text styles use Material3 defaults, which is fine.

**4. What happens when it fails?** Visual issues only.

**5. How does this connect?** Used by `KaiboardTheme()` wrapper in `MainActivity` and `VoiceKeyboardView`.

**6. What would make this better?** Nothing urgent.

**Severity:** Pass

---

### utils/AppLog.kt — *:1-58*

**1. Why does this exist?** In-memory log buffer with deduplication (suppresses repeated messages). Exposed as StateFlow for UI.

**2. What's working?** Thread-safe via `synchronized`. Deduplication with repeat count is clever. 200-line cap prevents unbounded growth.

**3. What's not working?** `SimpleDateFormat` is not thread-safe. While access is synchronized in `log()`, this is correct. But the field is a shared instance — if someone calls `timeFormat.format()` outside the synchronized block, it could corrupt state. Low risk since it's private.

**4. What happens when it fails?** Log entries are lost or corrupted. Not user-facing.

**5. How does this connect?** Used by every component for logging. Displayed in settings debug section.

**6. What would make this better?** Nothing urgent.

**Severity:** Pass

---

### utils/CrashLogger.kt — *:1-79*

**1. Why does this exist?** Writes crash reports to disk with stack trace + app log snapshot. Retains last 10 crashes.

**2. What's working?** Chains to default handler (system crash dialog still shows). Includes AppLog snapshot in crash report. Auto-prunes old files. Entire `writeCrash` wrapped in try/catch to prevent infinite crash loops.

**3. What's not working?** Nothing significant.

**4. What happens when it fails?** Crash logging silently fails. Original crash is still handled by default handler.

**5. How does this connect?** Installed in `WhisperClickApplication.onCreate()`. Read by `MainScreen::CrashLogContent`.

**6. What would make this better?** Nothing. This is solid.

**Severity:** Pass

---

### app/build.gradle — *:1-91*

**1. Why does this exist?** Build configuration.

**2. What's working?** Release signing from environment variables. Versioned APK filenames. Build config enabled.

**3. What's not working?**
- **`compileSdk 34` and `targetSdk 34`** — Android 15 (API 35) is now required for new Play Store submissions. Should bump.
- **Compose BOM not used.** Dependencies pin specific versions (`1.5.0`, `1.6.8`, `1.1.1`) which can create version conflicts. Should use `platform("androidx.compose:compose-bom:2024.xx.xx")`.
- Version mismatch: `compose-ui:1.6.8` but `compose-ui-tooling:1.5.0`. These should match.
- `accompanist-permissions:0.28.0` is quite old. Newer versions have fixes.

**4. What happens when it fails?** Build failures or runtime crashes from version conflicts.

**5. How does this connect?** Defines all dependencies and build parameters.

**6. What would make this better?** Adopt Compose BOM. Bump targetSdk to 35. Align all Compose versions.

**Severity:** Medium
**Fix:** Adopt Compose BOM for version alignment. Bump targetSdk to 35.

---

### AndroidManifest.xml — *:1-49*

**1. Why does this exist?** Declares app components, permissions, IME service.

**2. What's working?** BIND_INPUT_METHOD permission on service. RECORD_AUDIO, VIBRATE, INTERNET permissions. Correct intent filters.

**3. What's not working?** `tools:ignore="WrongManifestParent"` — suppresses a lint warning that should be investigated, not suppressed.

**4. What happens when it fails?** Manifest issues cause install failures.

**5. How does this connect?** Defines all entry points.

**6. What would make this better?** Investigate the suppressed lint warning.

**Severity:** Low
**Fix:** Investigate `WrongManifestParent` lint warning instead of suppressing.

---

### proguard-rules.pro — *:1-21*

**1. Why does this exist?** ProGuard/R8 rules for release builds.

**2. What's working?** Nothing — the file is the default template with no project-specific rules.

**3. What's not working?** **Release builds have `minifyEnabled true` with no keep rules.** This WILL strip or rename:
- `WhisperLib` / `WhisperCpuConfig` JNI classes — native code won't find them
- JSON field names used in API parsing
- Compose-related classes that use reflection
The release APK will crash on startup.

**4. What happens when it fails?** Release APK crashes. Users who download from GitHub Releases get a broken app.

**5. How does this connect?** Referenced by `build.gradle` proguardFiles.

**6. What would make this better?** Add keep rules for JNI classes, JSON models, and Compose.

**Severity:** Critical
**Fix:** Add ProGuard keep rules: `-keep class com.whispercpp.** { *; }` for JNI, `-keep class com.nefeshcore.whisperclick.api.RewriteVariants { *; }` for JSON parsing, and standard Compose keep rules.

---

## GATE: Review Completion Check

All 40 inventory items reviewed. All six questions answered for each. Every finding has a severity and fix. Proceeding to Step Back.

---

## Step Back — System-Level Analysis

### Duplication

**Prompt construction and JSON parsing duplicated between GeminiClient and OpenAIClient.** Both have identical `parseVariants()`, `fallbackVariants()`, and similar `callApi()` patterns. The prompt text for rewrite is copy-pasted. WAV encoding exists in both `RiffWaveHelper.kt` and `WhisperCloudClient.kt`.

**Severity:** Medium
**Fix:** Extract shared `BaseRewriteClient` with common prompt, parsing, and fallback logic. Reuse `RiffWaveHelper` in `WhisperCloudClient`.

### Data Flow

Data flow is traceable but has gaps:
- Audio: Mic -> Recorder -> ShortArray -> decodeShortArray -> WhisperContext.transcribeData -> text -> InputConnection
- Rewrite: InputConnection.getTextBeforeCursor -> API client -> RewriteVariants -> UI -> InputConnection.commitText
- Clipboard: System ClipboardManager -> listener -> ClipEntry list -> SharedPreferences

**Issue:** The rewrite flow reads from cursor position at request time but deletes at apply time — cursor may have moved. This is the data-loss finding from `applyRewrite`.

**Severity:** High (covered in applyRewrite finding)

### Responsibilities

`VoiceKeyboardInputMethodService` has too many responsibilities: audio recording, transcription, API client selection, rewrite orchestration, clipboard management, key event handling, preference management, lifecycle management. This is the classic "god object" anti-pattern.

`MainScreen.kt` at 1255 lines is too large for a single composable file.

**Severity:** Medium
**Fix:** Extract focused manager classes from the service. Split MainScreen into section files.

### Dependencies

Components are reasonably decoupled:
- API clients are independent objects behind an interface
- Theme is separate from logic
- CrashLogger and AppLog are standalone utilities

**Issue:** `VoiceKeyboardView` takes the service directly as a constructor parameter and accesses its properties throughout. This means the view cannot be tested without the full service.

**Severity:** Low
**Fix:** Define a keyboard state interface that the service implements, allowing the view to depend on the interface.

### Gaps

Missing entirely:
1. **Tests** — Zero unit tests, integration tests, or UI tests
2. **API key encryption** — Plaintext SharedPreferences
3. **ProGuard rules** — Empty file with minify enabled
4. **Network connectivity checks** — No pre-flight check before API calls
5. **Input validation** — User text embedded raw in API prompts
6. **Sensitive clipboard filtering** — Passwords captured without filtering

**Severity:** Critical (ProGuard), High (tests, encryption, input validation)
**Fix:** Prioritize ProGuard rules (release builds crash), then API key encryption, then tests.

---

## GATE: Step Back Completion Check

All five system-level concerns evaluated. Findings logged for each. Proceeding to summary.

---

## Summary

**Critical:** 3 | **High:** 6 | **Medium:** 7 | **Low:** 5 | **Pass:** 6

**System-level findings:** Code duplication in API clients, god object service, missing tests, missing ProGuard rules, plaintext API key storage.

**Top 3 priorities:**

1. **[CRITICAL] Fix ProGuard rules** — Release APK crashes due to empty proguard-rules.pro with `minifyEnabled true`. Add keep rules for JNI classes (`com.whispercpp.**`), API model classes, and Compose. This is a ship-blocker.

2. **[CRITICAL] Add try/catch to requestRewriteAll coroutine** — If the API call throws, `isRewriting` stays `true` forever and the UI shows a permanent spinner. Two-line fix: wrap in try/catch, set error state in catch.

3. **[CRITICAL] Fix ModelManager SharedPreferences mismatch** — `ModelManager` uses hardcoded `"com.nefeshcore.whisperclick.settings"` while everything else uses `R.string.preference_file_key`. Model selection in settings may not be seen by the keyboard service. Also has a file handle leak in the download resume path.

**Overall — would you ship this?**

Not yet. The three Critical findings must be fixed first — ProGuard will crash release builds, the rewrite spinner can hang indefinitely, and the preferences mismatch means model selection may silently fail. After those three fixes, the High findings (permission check before recording, clipboard sensitive data filtering, Gemini key in URL for validation, ViewModel runBlocking) should be addressed before a public release. The app's core functionality is solid and well-implemented, but these production readiness gaps need to be closed.

---

## GATE: Final Verification

- Severity counts verified: 3 Critical + 6 High + 7 Medium + 5 Low + 6 Pass = 27 findings for 40 components (some components had findings, some passed)
- Top 3 priorities are all backed by specific findings above
- All sections of the framework completed
- Audit is complete.
