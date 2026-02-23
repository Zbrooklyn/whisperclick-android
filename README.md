# WhisperClick Android 🖱️✨

**WhisperClick** is an open-source Android keyboard that combines privacy-first voice typing (Whisper) with powerful AI text refinement (Gemini).

## Features

### 🎙️ Whisper Voice Typing
*   **Offline:** Runs entirely on-device using `whisper.cpp`.
*   **Private:** No audio ever leaves your phone.
*   **Accurate:** Uses OpenAI's Whisper model for superior transcription.

### ✨ Magic Rewrite
*   **AI-Powered:** Instantly rewrite your text using Google Gemini.
*   **Styles:**
    *   **Fix Grammar:** Correct mistakes without changing the tone.
    *   **Professional:** Make it sound like a CEO.
    *   **Casual:** Make it sound like a text to a friend.
    *   **Concise:** Shorten it.
    *   **Emojify:** Add relevant emojis. 🤠

## Setup

1.  **Install:** Download the APK from the [Releases](https://github.com/Zbrooklyn/whisperclick-android/releases) page.
2.  **Enable:** Go to `Settings > System > Languages & input > On-screen keyboard` and enable **WhisperClick**.
3.  **API Key:**
    *   Open the **WhisperClick App** (launcher icon).
    *   Scroll down to **Advanced**.
    *   Enter your **Gemini API Key** (Get one from [Google AI Studio](https://aistudio.google.com/)).
4.  **Download Model:** The app will download the Whisper model on first run (approx 40MB).

## Development

### Build
This project uses GitHub Actions for CI/CD.
*   **Push to `main`:** Triggers a debug build.
*   **Artifacts:** Download the APK from the Actions tab.

### Tech Stack
*   **Language:** Kotlin + C++ (JNI)
*   **UI:** Jetpack Compose
*   **AI Engine:** `whisper.cpp` (Local) + Gemini API (Cloud)

## License
BSD-3-Clause
