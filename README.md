# Android Text to Speech App - Enhanced

An Android text-to-speech app that discovers the languages and voices exposed by the Text-to-Speech engine installed on the device.

## Enhanced Features

- Dynamically lists installed/supported TTS languages instead of a four-language hard-coded list.
- Remembers the last language the user selected and restores it the next time the app opens.
- Does not show language-discovery or voice-install notification popups during normal startup.
- Displays locale tags such as `en-US`, `ja-JP`, and `zh-HK` next to language names.
- Provides a voice selector for the currently selected language.
- Includes a safe `Default voice for this language` fallback.
- Shows whether a named voice is offline or network-based.
- Shows the Android TTS quality rating for named voices.
- Adds adjustable pitch from 0.50x to 2.00x.
- Adds adjustable speech speed from 0.50x to 2.00x.
- Applies the selected language, voice, pitch, and speed to normal playback.
- Applies the same settings when exporting MP3 audio.
- Keeps the existing Play, Stop, Clear Text, and MP3 export workflow.
- Uses a scrollable layout so the extra voice controls fit on smaller screens.

## Important Language and Voice Behavior

The app does not pretend that every Android phone has every language. It asks the active Android TTS engine what languages and voices are actually available and displays those choices.

The result therefore depends on:

- The TTS engine installed on the device.
- Voice/language packs installed for that engine.
- Whether a particular voice requires a network connection.

Different engines can expose different numbers of voices for the same language.

Android's standard `Voice` API does not provide a universal male/female/emotion property. The app exposes the actual engine voice names plus quality/network information and allows pitch and speed adjustment.

## Requirements

- Android 7.0 (API 24) or later.
- An Android Text-to-Speech engine.
- Language/voice data installed for the languages you want to use.
- Android Studio with access to the repositories required by this project.

## Using the App

1. Enter text.
2. Select a language. The app remembers this choice for the next launch.
3. Select the default voice or a named voice for that locale.
4. Adjust Pitch and Speed if desired.
5. Press **Play** to listen.
6. Press **Stop** to stop speech.
7. Press **Export to MP3** to save audio with the same language, voice, pitch, and speed settings.
8. Press **Clear Text** to clear the input.

## Main Implementation

The dynamic TTS functionality is implemented in:

```text
app/src/main/java/com/example/texttospeechapp/MainActivity.kt
```

The enhanced interface is in:

```text
app/src/main/res/layout/activity_main.xml
```

MP3 encoding remains in:

```text
app/src/main/java/com/example/texttospeechapp/Mp3Encoder.kt
```

## Build

Open the project in Android Studio, allow Gradle sync to complete, and use:

```text
Build > Make Project
```

or run `./gradlew assembleDebug` in an environment with Gradle/Maven repository access.
