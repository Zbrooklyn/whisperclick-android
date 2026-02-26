# WhisperClick ProGuard Rules

# Preserve line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- JNI (whisper.cpp native interface) ---
# WhisperLib has external (JNI) methods called from native code.
# WhisperContext wraps the native pointer. Both must keep names intact.
-keep class com.whispercpp.whisper.** { *; }

# --- API model classes (parsed from JSON via org.json) ---
# Field names must match JSON keys ("clean", "professional", etc.)
-keep class com.nefeshcore.whisperclick.api.RewriteVariants { *; }
-keep class com.nefeshcore.whisperclick.api.RewriteProvider { *; }

# --- InputMethodService ---
# IME service is declared in manifest and instantiated by the framework
-keep class com.nefeshcore.whisperclick.VoiceKeyboardInputMethodService { *; }
-keep class com.nefeshcore.whisperclick.VoiceKeyboardView { *; }

# --- Compose ---
# Compose compiler generates classes that shouldn't be renamed
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# --- Kotlin metadata (needed for reflection and Compose) ---
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keep class kotlin.Metadata { *; }

# --- Coroutines ---
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# --- Accompanist ---
-dontwarn com.google.accompanist.**
