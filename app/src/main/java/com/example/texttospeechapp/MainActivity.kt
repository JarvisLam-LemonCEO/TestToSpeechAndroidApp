package com.example.texttospeechapp

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var textToSpeech: TextToSpeech

    private lateinit var editText: EditText
    private lateinit var languageSpinner: Spinner
    private lateinit var playButton: Button
    private lateinit var stopButton: Button
    private lateinit var clearButton: Button

    private var ttsReady = false

    private val languages = listOf(
        "English" to Locale.US,
        "Japanese" to Locale.JAPAN,
        "Mandarin Chinese" to Locale.SIMPLIFIED_CHINESE,
        "Cantonese Chinese" to Locale("zh", "HK")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        // ------------------------------------
        // Safe area for status bar / camera
        // ------------------------------------

        val rootLayout = findViewById<View>(R.id.rootLayout)

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, windowInsets ->

            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout()
            )

            val normalPadding = dpToPx(24)

            view.setPadding(
                insets.left + normalPadding,
                insets.top + normalPadding,
                insets.right + normalPadding,
                insets.bottom + normalPadding
            )

            windowInsets
        }

        // ------------------------------------
        // Find views
        // ------------------------------------

        editText = findViewById(R.id.editText)
        languageSpinner = findViewById(R.id.languageSpinner)

        playButton = findViewById(R.id.playButton)
        stopButton = findViewById(R.id.stopButton)
        clearButton = findViewById(R.id.clearButton)

        // ------------------------------------
        // Language dropdown
        // ------------------------------------

        setupLanguageSpinner()

        // ------------------------------------
        // Initialize Text-to-Speech
        // ------------------------------------

        playButton.isEnabled = false

        textToSpeech = TextToSpeech(this, this)

        // ------------------------------------
        // Play button
        // ------------------------------------

        playButton.setOnClickListener {
            speakText()
        }

        // ------------------------------------
        // Stop button
        // ------------------------------------

        stopButton.setOnClickListener {
            stopSpeaking()
        }

        // ------------------------------------
        // Clear button
        // ------------------------------------

        clearButton.setOnClickListener {

            // Stop speech first
            if (::textToSpeech.isInitialized) {
                textToSpeech.stop()
            }

            // Clear text box
            editText.text.clear()

            // Put cursor/focus back into text box
            editText.requestFocus()
        }
    }

    // ----------------------------------------
    // Language Spinner
    // ----------------------------------------

    private fun setupLanguageSpinner() {

        val languageNames = languages.map { it.first }

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            languageNames
        )

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        languageSpinner.adapter = adapter
    }

    // ----------------------------------------
    // TTS Initialization
    // ----------------------------------------

    override fun onInit(status: Int) {

        if (status == TextToSpeech.SUCCESS) {

            ttsReady = true
            playButton.isEnabled = true

            Toast.makeText(
                this,
                "Text-to-Speech is ready.",
                Toast.LENGTH_SHORT
            ).show()

        } else {

            ttsReady = false
            playButton.isEnabled = false

            Toast.makeText(
                this,
                "Text-to-Speech initialization failed.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ----------------------------------------
    // Speak Text
    // ----------------------------------------

    private fun speakText() {

        val text = editText.text.toString().trim()

        // Check for empty text
        if (text.isEmpty()) {

            Toast.makeText(
                this,
                "Please enter some text.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        // Check TTS
        if (!ttsReady) {

            Toast.makeText(
                this,
                "Text-to-Speech is not ready.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        // Get selected language
        val selectedPosition =
            languageSpinner.selectedItemPosition

        val selectedLanguage =
            languages[selectedPosition]

        val languageName =
            selectedLanguage.first

        val locale =
            selectedLanguage.second

        // Check language support
        val availability =
            textToSpeech.isLanguageAvailable(locale)

        if (
            availability == TextToSpeech.LANG_MISSING_DATA ||
            availability == TextToSpeech.LANG_NOT_SUPPORTED
        ) {

            Toast.makeText(
                this,
                "$languageName is not available on this device.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        // Set language
        val result = textToSpeech.setLanguage(locale)

        if (
            result == TextToSpeech.LANG_MISSING_DATA ||
            result == TextToSpeech.LANG_NOT_SUPPORTED
        ) {

            Toast.makeText(
                this,
                "$languageName could not be selected.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        // Normal speech speed
        textToSpeech.setSpeechRate(1.0f)

        // Normal pitch
        textToSpeech.setPitch(1.0f)

        // Speak
        textToSpeech.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "speechId"
        )
    }

    // ----------------------------------------
    // Stop
    // ----------------------------------------

    private fun stopSpeaking() {

        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
        }
    }

    // ----------------------------------------
    // Convert dp to pixels
    // ----------------------------------------

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    // ----------------------------------------
    // Clean up TTS
    // ----------------------------------------

    override fun onDestroy() {

        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }

        super.onDestroy()
    }
}