# Android Text to Speech App

A simple Android application that converts typed text into speech using
Android's built-in `TextToSpeech` API.

Users can type text, choose a language, press **Play** to hear it spoken
aloud, press **Stop** to stop playback, and press **Clear Text** to
clear the input.

## Features

-   Type text into a multi-line text box
-   Convert text to speech
-   Choose between multiple languages:
    -   English
    -   Japanese
    -   Mandarin Chinese
    -   Cantonese Chinese
-   Play spoken text
-   Stop speech playback
-   Export the synthesized speech as a real MP3 file
-   Choose the MP3 save location with Android's system file picker
-   Clear the text box
-   Automatically avoids the Android status bar and front-camera/display
    cutout
-   Checks whether the selected TTS language is supported on the device

## Supported Languages

  Language            Android Locale
  ------------------- -----------------------------
  English             `Locale.US`
  Japanese            `Locale.JAPAN`
  Mandarin Chinese    `Locale.SIMPLIFIED_CHINESE`
  Cantonese Chinese   `Locale("zh", "HK")`

> Cantonese support depends on the Text-to-Speech engine and voice data
> installed on the Android device.

## App Layout

The app contains:

``` text
┌────────────────────────────────────┐
│            Status Bar              │
├────────────────────────────────────┤
│                                    │
│   Text to Speech                   │
│                                    │
│   ┌────────────────────────────┐   │
│   │ Type something...          │   │
│   │                            │   │
│   │                            │   │
│   └────────────────────────────┘   │
│                                    │
│   Language                         │
│   ┌────────────────────────────┐   │
│   │ English                 ▼  │   │
│   └────────────────────────────┘   │
│                                    │
│   ┌──────────┐  ┌──────────┐       │
│   │   PLAY   │  │   STOP   │       │
│   └──────────┘  └──────────┘       │
│                                    │
│   ┌────────────────────────────┐   │
│   │       EXPORT TO MP3        │   │
│   └────────────────────────────┘   │
│                                    │
│   ┌────────────────────────────┐   │
│   │         CLEAR TEXT         │   │
│   └────────────────────────────┘   │
│                                    │
└────────────────────────────────────┘
```

## Technologies Used

-   Kotlin
-   Android Studio
-   Android XML layouts
-   Android `TextToSpeech` API
-   Android Storage Access Framework
-   Android LAME MP3 encoder (`com.github.axet:lame:1.0.9`)
    -   Uses the native Android/JNI build of libmp3lame. Review the LGPL dependency license when distributing the app.
-   AndroidX Window Insets

## Requirements

-   Android Studio
-   Kotlin
-   Android device or emulator
-   Android Text-to-Speech engine installed
-   Appropriate language voice data installed on the device

## Getting Started

### 1. Clone the repository

``` bash
git clone https://github.com/YOUR_USERNAME/YOUR_REPOSITORY_NAME.git
```

Then open the project folder in Android Studio.

### 2. Build the project

Allow Android Studio to finish syncing Gradle dependencies.

Then select:

``` text
Build > Make Project
```

### 3. Run the app

Connect an Android device or start an emulator.

Press:

``` text
Run ▶
```

Select your device and wait for the application to launch.

## How to Use

1.  Enter text into the text box.
2.  Select a language from the dropdown.
3.  Press **Play** to hear the speech.
4.  Press **Stop** to stop speaking.
5.  Press **Export to MP3** to synthesize the current text without playback.
6.  Choose a filename and save location in Android's file picker.
7.  Press **Clear Text** to stop playback and clear the text box.

## Example Text

### English

``` text
Hello, how are you today?
```

### Japanese

``` text
こんにちは。元気ですか？
```

### Mandarin Chinese

``` text
你好，你今天好嗎？
```

### Cantonese Chinese

``` text
你好，你今日好嗎？
```

## Text-to-Speech Language Selection

The app stores each displayed language together with its Android
`Locale`:

``` kotlin
private val languages = listOf(
    "English" to Locale.US,
    "Japanese" to Locale.JAPAN,
    "Mandarin Chinese" to Locale.SIMPLIFIED_CHINESE,
    "Cantonese Chinese" to Locale("zh", "HK")
)
```

When the user presses **Play**, the app gets the selected locale and
checks whether it is available:

``` kotlin
val availability =
    textToSpeech.isLanguageAvailable(locale)
```

If the language is unavailable, the app displays a message instead of
attempting to speak.

## Playing Speech

Speech is started using:

``` kotlin
textToSpeech.speak(
    text,
    TextToSpeech.QUEUE_FLUSH,
    null,
    "speechId"
)
```

`QUEUE_FLUSH` clears any previous speech and immediately starts reading
the current text.

## Stopping Speech

The Stop button uses:

``` kotlin
textToSpeech.stop()
```

## Exporting MP3 Audio

The **Export to MP3** button opens Android's system save dialog. The app then
uses `synthesizeToFile(...)` together with `UtteranceProgressListener` to
capture the PCM audio produced by the current TTS engine. The PCM stream is
encoded to MP3 with Android's JNI build of libmp3lame and written to the user-selected document URI. The exporter also replaces the initial placeholder VBR frame with the final LAME/Xing header before saving, which keeps the resulting MP3 playable in standard audio players.

Because the file location is selected through Android's Storage Access
Framework, the app does not need broad storage permissions.

## Clearing Text

The Clear Text button stops any current speech and clears the text box:

``` kotlin
clearButton.setOnClickListener {

    if (::textToSpeech.isInitialized) {
        textToSpeech.stop()
    }

    editText.text.clear()
    editText.requestFocus()
}
```

## Status Bar and Display Cutout Support

The app uses Android Window Insets so that content does not overlap the
status bar, navigation bar, notch, or front-camera cutout.

``` kotlin
val insets = windowInsets.getInsets(
    WindowInsetsCompat.Type.systemBars() or
        WindowInsetsCompat.Type.displayCutout()
)
```

This makes the UI work better across different Android devices and
screen designs.

## Android Manifest

The application declares the Text-to-Speech service query:

``` xml
<queries>
    <intent>
        <action android:name="android.intent.action.TTS_SERVICE" />
    </intent>
</queries>
```

No microphone permission is required because the app does not record
audio.

## Project Structure

``` text
app/
├── manifests/
│   └── AndroidManifest.xml
├── kotlin+java/
│   └── com.example.texttospeechapp/
│       ├── MainActivity.kt
│       └── Mp3Encoder.kt
└── res/
    └── layout/
        └── activity_main.xml
```

## Known Limitations

Language availability depends on the Text-to-Speech engine installed on
the Android device.

In particular, Cantonese may not be available on every device. A device
may need additional language or voice data installed before Cantonese
speech works.

The selected language also determines pronunciation. For example,
Chinese characters can be pronounced differently when Mandarin or
Cantonese is selected.

## Possible Future Improvements

-   Add speech speed controls
-   Add pitch controls
-   Add a voice selector
-   Show only voices installed on the device
-   Save recently entered text
-   Add dark mode
-   Import text files
-   Highlight words while they are being spoken
-   Add more languages
