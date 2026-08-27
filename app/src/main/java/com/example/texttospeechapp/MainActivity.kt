package com.example.texttospeechapp

import android.media.AudioFormat
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var textToSpeech: TextToSpeech

    private lateinit var editText: EditText
    private lateinit var languageSpinner: Spinner
    private lateinit var playButton: Button
    private lateinit var stopButton: Button
    private lateinit var exportButton: Button
    private lateinit var clearButton: Button

    private var ttsReady = false
    private var pendingExportText: String? = null

    private val exportLock = Any()
    private var exportCapture: ExportCapture? = null
    private val mp3Executor = Executors.newSingleThreadExecutor()

    private val languages = listOf(
        "English" to Locale.US,
        "Japanese" to Locale.JAPAN,
        "Mandarin Chinese" to Locale.SIMPLIFIED_CHINESE,
        "Cantonese Chinese" to Locale("zh", "HK")
    )

    private val createMp3Document = registerForActivityResult(
        ActivityResultContracts.CreateDocument("audio/mpeg")
    ) { uri: Uri? ->
        if (uri == null) {
            pendingExportText = null
            setExportButtonIdle()
        } else {
            val text = pendingExportText
            pendingExportText = null
            if (text == null) {
                setExportButtonIdle()
            } else {
                beginMp3Synthesis(text, uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val rootLayout = findViewById<View>(R.id.rootLayout)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
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

        editText = findViewById(R.id.editText)
        languageSpinner = findViewById(R.id.languageSpinner)
        playButton = findViewById(R.id.playButton)
        stopButton = findViewById(R.id.stopButton)
        exportButton = findViewById(R.id.exportButton)
        clearButton = findViewById(R.id.clearButton)

        setupLanguageSpinner()

        playButton.isEnabled = false
        exportButton.isEnabled = false
        textToSpeech = TextToSpeech(this, this)

        playButton.setOnClickListener { speakText() }
        stopButton.setOnClickListener { stopSpeaking() }
        exportButton.setOnClickListener { chooseMp3Destination() }

        clearButton.setOnClickListener {
            if (::textToSpeech.isInitialized) {
                textToSpeech.stop()
            }
            editText.text.clear()
            editText.requestFocus()
        }
    }

    private fun setupLanguageSpinner() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            languages.map { it.first }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        languageSpinner.adapter = adapter
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            textToSpeech.setOnUtteranceProgressListener(ttsProgressListener)
            playButton.isEnabled = true
            exportButton.isEnabled = true
            Toast.makeText(this, "Text-to-Speech is ready.", Toast.LENGTH_SHORT).show()
        } else {
            ttsReady = false
            playButton.isEnabled = false
            exportButton.isEnabled = false
            Toast.makeText(
                this,
                "Text-to-Speech initialization failed.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun speakText() {
        val text = validateTextAndSelectLanguage() ?: return

        textToSpeech.setSpeechRate(1.0f)
        textToSpeech.setPitch(1.0f)
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "speechId")
    }

    private fun chooseMp3Destination() {
        val text = validateTextAndSelectLanguage() ?: return

        pendingExportText = text
        exportButton.isEnabled = false
        exportButton.text = "Choose Save Location..."

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        createMp3Document.launch("tts_$timestamp.mp3")
    }

    private fun beginMp3Synthesis(text: String, destination: Uri) {
        if (!ttsReady) {
            setExportButtonIdle()
            Toast.makeText(this, "Text-to-Speech is not ready.", Toast.LENGTH_SHORT).show()
            return
        }

        // Re-apply the selected language after returning from the document picker.
        if (!selectCurrentLanguage()) {
            setExportButtonIdle()
            return
        }

        textToSpeech.stop()
        textToSpeech.setSpeechRate(1.0f)
        textToSpeech.setPitch(1.0f)

        val utteranceId = "mp3_export_${System.currentTimeMillis()}"
        val tempFile = File.createTempFile("tts_export_", ".wav", cacheDir)

        synchronized(exportLock) {
            exportCapture = ExportCapture(
                utteranceId = utteranceId,
                destination = destination,
                tempFile = tempFile
            )
        }

        exportButton.isEnabled = false
        exportButton.text = "Synthesizing..."

        val result = textToSpeech.synthesizeToFile(
            text,
            Bundle(),
            tempFile,
            utteranceId
        )

        if (result != TextToSpeech.SUCCESS) {
            failExport(utteranceId, "Could not start speech synthesis.")
        }
    }

    private val ttsProgressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onBeginSynthesis(
            utteranceId: String?,
            sampleRateInHz: Int,
            audioFormat: Int,
            channelCount: Int
        ) {
            if (utteranceId == null) return
            synchronized(exportLock) {
                val capture = exportCapture
                if (capture?.utteranceId == utteranceId) {
                    capture.sampleRate = sampleRateInHz
                    capture.audioFormat = audioFormat
                    capture.channelCount = channelCount
                }
            }
        }

        override fun onAudioAvailable(utteranceId: String?, audio: ByteArray?) {
            if (utteranceId == null || audio == null) return
            synchronized(exportLock) {
                val capture = exportCapture
                if (capture?.utteranceId == utteranceId) {
                    capture.audio.write(audio)
                }
            }
        }

        override fun onDone(utteranceId: String?) {
            if (utteranceId == null) return

            val completed = synchronized(exportLock) {
                val capture = exportCapture
                if (capture?.utteranceId != utteranceId) {
                    null
                } else {
                    exportCapture = null
                    capture
                }
            } ?: return

            completed.tempFile.delete()

            if (completed.sampleRate <= 0 || completed.channelCount !in 1..2) {
                finishExportWithError(
                    completed.destination,
                    "The TTS engine did not provide a supported audio format."
                )
                return
            }

            val pcmBytes = completed.audio.toByteArray()
            if (pcmBytes.isEmpty()) {
                finishExportWithError(
                    completed.destination,
                    "The TTS engine returned no audio data."
                )
                return
            }

            runOnUiThread { exportButton.text = "Encoding MP3..." }

            mp3Executor.execute {
                try {
                    contentResolver.openOutputStream(completed.destination, "w")?.use { output ->
                        Mp3Encoder.encode(
                            pcmBytes = pcmBytes,
                            sampleRate = completed.sampleRate,
                            audioFormat = completed.audioFormat,
                            channelCount = completed.channelCount,
                            output = output
                        )
                    } ?: error("Could not open the selected file for writing.")

                    runOnUiThread {
                        setExportButtonIdle()
                        Toast.makeText(
                            this@MainActivity,
                            "MP3 saved successfully.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    finishExportWithError(
                        completed.destination,
                        e.message ?: "MP3 export failed."
                    )
                }
            }
        }

        @Deprecated("Deprecated in Android API 21")
        override fun onError(utteranceId: String?) {
            if (utteranceId != null) {
                failExport(utteranceId, "Text-to-Speech synthesis failed.")
            }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            if (utteranceId != null) {
                failExport(utteranceId, "Text-to-Speech synthesis failed (code $errorCode).")
            }
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            if (utteranceId != null) {
                failExport(utteranceId, "MP3 export was stopped.")
            }
        }
    }

    private fun validateTextAndSelectLanguage(): String? {
        val text = editText.text.toString().trim()

        if (text.isEmpty()) {
            Toast.makeText(this, "Please enter some text.", Toast.LENGTH_SHORT).show()
            return null
        }

        if (text.length > TextToSpeech.getMaxSpeechInputLength()) {
            Toast.makeText(
                this,
                "The text is too long for one TTS request. Please shorten it.",
                Toast.LENGTH_LONG
            ).show()
            return null
        }

        if (!ttsReady) {
            Toast.makeText(this, "Text-to-Speech is not ready.", Toast.LENGTH_SHORT).show()
            return null
        }

        if (!selectCurrentLanguage()) return null
        return text
    }

    private fun selectCurrentLanguage(): Boolean {
        val selectedLanguage = languages[languageSpinner.selectedItemPosition]
        val languageName = selectedLanguage.first
        val locale = selectedLanguage.second

        val availability = textToSpeech.isLanguageAvailable(locale)
        if (
            availability == TextToSpeech.LANG_MISSING_DATA ||
            availability == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            Toast.makeText(
                this,
                "$languageName is not available on this device.",
                Toast.LENGTH_LONG
            ).show()
            return false
        }

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
            return false
        }

        return true
    }

    private fun failExport(
        utteranceId: String,
        message: String
    ) {
        val failed = synchronized(exportLock) {
            val capture = exportCapture
            if (capture?.utteranceId != utteranceId) {
                null
            } else {
                exportCapture = null
                capture
            }
        } ?: return

        failed.tempFile.delete()
        tryDelete(failed.destination)

        runOnUiThread {
            setExportButtonIdle()
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun finishExportWithError(destination: Uri, message: String) {
        tryDelete(destination)
        runOnUiThread {
            setExportButtonIdle()
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun tryDelete(uri: Uri) {
        try {
            contentResolver.delete(uri, null, null)
        } catch (_: Exception) {
            // Some document providers do not allow delete; leave the empty/partial file in place.
        }
    }

    private fun setExportButtonIdle() {
        if (!::exportButton.isInitialized) return
        exportButton.text = "Export to MP3"
        exportButton.isEnabled = ttsReady
    }

    private fun stopSpeaking() {
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        mp3Executor.shutdownNow()
        super.onDestroy()
    }

    private data class ExportCapture(
        val utteranceId: String,
        val destination: Uri,
        val tempFile: File,
        val audio: ByteArrayOutputStream = ByteArrayOutputStream(),
        var sampleRate: Int = 0,
        var audioFormat: Int = AudioFormat.ENCODING_INVALID,
        var channelCount: Int = 0
    )
}
