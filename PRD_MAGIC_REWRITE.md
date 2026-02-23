# Product Requirements Document (PRD): WhisperClick "Magic Rewrite"

**Status:** Draft
**Target Release:** v0.2 (Alpha)

## 1. Overview
The "Magic Rewrite" feature is the core differentiator of WhisperClick. It allows users to select text they have typed (or dictated) and instantly rewrite it using an LLM (Gemini) directly within the keyboard interface.

## 2. User Stories
*   **As a professional:** I want to rewrite a hastily dictated email to sound formal and polite.
*   **As a casual user:** I want to fix grammar and spelling mistakes in my text messages without leaving the app.
*   **As a developer:** I want to summarize a long error log I just pasted into a chat.

## 3. User Interface (UI)
*   **Entry Point:** A "Sparkle" icon (✨) added to the keyboard toolbar (next to the microphone).
*   **Interaction Flow:**
    1.  User types/dictates text.
    2.  User taps the ✨ button.
    3.  A **Magic Menu** (Popup/Overlay) appears with options:
        *   **Fix Grammar** (Default)
        *   **Professional**
        *   **Casual**
        *   **Concise**
        *   **Emojify**
    4.  User selects an option.
    5.  A loading indicator (spinner) appears on the button.
    6.  The text in the input field is replaced by the AI-generated version.

## 4. Technical Implementation

### 4.1 Context Retrieval
*   Use `InputConnection.getTextBeforeCursor(n, 0)` to grab the last `n` characters (e.g., 2000 chars).
*   *Edge Case:* If text is selected, use `getSelectedText()`.

### 4.2 API Integration (Gemini)
*   **Endpoint:** `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent`
*   **Authentication:** User-provided API Key (stored in EncryptedSharedPreferences).
*   **Prompt Engineering:**
    ```text
    System: You are a writing assistant. Rewrite the user's text according to the requested style. Output ONLY the rewritten text. No preamble.
    User: [Style: Professional] Text: "hey boss i cant come in today sick"
    Model: "Dear Manager, I am writing to inform you that I am unable to attend work today due to illness."
    ```

### 4.3 Text Replacement
*   **Method:** `InputConnection.setComposingText()` (for live preview) or `commitText()` (final).
*   **Safety:** Ensure we don't delete text if the API fails.

## 5. Settings & Configuration
*   **API Key Management:** A settings screen to input/paste the Gemini API Key.
*   **Model Selection:** Toggle between `gemini-2.0-flash` (fast) and `gemini-2.0-pro` (smart).

## 6. Future Scope (v0.3+)
*   **Streaming:** Show the text being rewritten character-by-character.
*   **Local LLM:** Use Gemini Nano on supported devices to remove the API key requirement.
