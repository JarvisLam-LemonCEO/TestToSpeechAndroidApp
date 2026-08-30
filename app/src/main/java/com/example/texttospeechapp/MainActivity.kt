package com.example.texttospeechapp

import android.media.AudioFormat
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var textToSpeech: TextToSpeech

    private lateinit var editText: EditText
    private lateinit var languageSpinner: Spinner
    private lateinit var voiceSpinner: Spinner
    private lateinit var voiceInfoText: TextView
    private lateinit var pitchSeekBar: SeekBar
    private lateinit var pitchValueText: TextView
    private lateinit var rateSeekBar: SeekBar
    private lateinit var rateValueText: TextView
    private lateinit var playButton: Button
    private lateinit var stopButton: Button
    private lateinit var exportButton: Button
    private lateinit var clearButton: Button

    private var ttsReady = false
    private var pendingExportText: String? = null
    private var suppressSpinnerCallbacks = false

    private var languages: List<LanguageOption> = emptyList()
    private var voices: List<VoiceOption> = emptyList()

    private val exportLock = Any()
    private var exportCapture: ExportCapture? = null
    private val mp3Executor = Executors.newSingleThreadExecutor()

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
            val horizontalPadding = dpToPx(24)
            val verticalPadding = dpToPx(16)
            view.setPadding(
                insets.left + horizontalPadding,
                insets.top + verticalPadding,
                insets.right + horizontalPadding,
                insets.bottom + verticalPadding
            )
            windowInsets
        }

        editText = findViewById(R.id.editText)
        languageSpinner = findViewById(R.id.languageSpinner)
        voiceSpinner = findViewById(R.id.voiceSpinner)
        voiceInfoText = findViewById(R.id.voiceInfoText)
        pitchSeekBar = findViewById(R.id.pitchSeekBar)
        pitchValueText = findViewById(R.id.pitchValueText)
        rateSeekBar = findViewById(R.id.rateSeekBar)
        rateValueText = findViewById(R.id.rateValueText)
        playButton = findViewById(R.id.playButton)
        stopButton = findViewById(R.id.stopButton)
        exportButton = findViewById(R.id.exportButton)
        clearButton = findViewById(R.id.clearButton)

        setupLoadingSpinners()
        setupPitchAndRateControls()
        setupSpinnerListeners()

        playButton.isEnabled = false
        exportButton.isEnabled = false
        voiceSpinner.isEnabled = false
        languageSpinner.isEnabled = false

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

    private fun setupLoadingSpinners() {
        setSpinnerItems(languageSpinner, listOf("Loading languages..."))
        setSpinnerItems(voiceSpinner, listOf("Loading voices..."))
        voiceInfoText.text = "Voice details will appear here."
    }

    private fun setupSpinnerListeners() {
        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressSpinnerCallbacks || !ttsReady || position !in languages.indices) return
                val selectedLocale = languages[position].locale
                saveSelectedLanguage(selectedLocale)
                populateVoicesForLanguage(selectedLocale)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        voiceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressSpinnerCallbacks || position !in voices.indices) return
                updateVoiceInfo(voices[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun setupPitchAndRateControls() {
        pitchSeekBar.max = CONTROL_RANGE
        pitchSeekBar.progress = DEFAULT_CONTROL_PROGRESS
        rateSeekBar.max = CONTROL_RANGE
        rateSeekBar.progress = DEFAULT_CONTROL_PROGRESS

        updatePitchLabel()
        updateRateLabel()

        pitchSeekBar.setOnSeekBarChangeListener(simpleSeekBarListener {
            updatePitchLabel()
        })
        rateSeekBar.setOnSeekBarChangeListener(simpleSeekBarListener {
            updateRateLabel()
        })
    }

    private fun simpleSeekBarListener(onChanged: () -> Unit): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                onChanged()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            textToSpeech.setOnUtteranceProgressListener(ttsProgressListener)
            populateLanguages()
        } else {
            ttsReady = false
            playButton.isEnabled = false
            exportButton.isEnabled = false
            languageSpinner.isEnabled = false
            voiceSpinner.isEnabled = false
            setSpinnerItems(languageSpinner, listOf("TTS unavailable"))
            setSpinnerItems(voiceSpinner, listOf("TTS unavailable"))
            voiceInfoText.text = "Text-to-Speech initialization failed."
            Toast.makeText(
                this,
                "Text-to-Speech initialization failed.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun populateLanguages() {
        val locales = linkedSetOf<Locale>()

        try {
            textToSpeech.availableLanguages
                ?.filter { locale -> isLocaleUsable(locale) }
                ?.forEach(locales::add)
        } catch (_: Exception) {
            // Some third-party engines do not implement availableLanguages reliably.
        }

        try {
            textToSpeech.voices
                ?.map { it.locale }
                ?.filter { locale -> isLocaleUsable(locale) }
                ?.forEach(locales::add)
        } catch (_: Exception) {
            // Keep any locales already discovered above.
        }

        val uniqueByTag = linkedMapOf<String, Locale>()
        locales.forEach { locale ->
            uniqueByTag.putIfAbsent(locale.toLanguageTag(), locale)
        }

        val collator = Collator.getInstance(Locale.getDefault())
        languages = uniqueByTag.values
            .map { LanguageOption(it, buildLanguageLabel(it)) }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }

        if (languages.isEmpty()) {
            languageSpinner.isEnabled = false
            voiceSpinner.isEnabled = false
            playButton.isEnabled = false
            exportButton.isEnabled = false
            setSpinnerItems(languageSpinner, listOf("No TTS languages available"))
            setSpinnerItems(voiceSpinner, listOf("No voices available"))
            voiceInfoText.text = "No Text-to-Speech languages are currently available."
            return
        }

        suppressSpinnerCallbacks = true
        setSpinnerItems(languageSpinner, languages.map { it.label })
        languageSpinner.isEnabled = true

        val savedLocale = getSavedLanguage()
        val defaultLocale = textToSpeech.voice?.locale ?: Locale.getDefault()
        val selectedIndex = if (savedLocale != null) {
            findBestLanguageIndex(savedLocale)
        } else {
            findBestLanguageIndex(defaultLocale)
        }
        languageSpinner.setSelection(selectedIndex, false)
        suppressSpinnerCallbacks = false

        populateVoicesForLanguage(languages[selectedIndex].locale)
        playButton.isEnabled = true
        exportButton.isEnabled = true
    }

    private fun populateVoicesForLanguage(locale: Locale) {
        val engineVoices = try {
            textToSpeech.voices?.toList().orEmpty()
        } catch (_: Exception) {
            emptyList()
        }

        val exactVoices = engineVoices
            .filter { voice -> voice.locale.toLanguageTag() == locale.toLanguageTag() }
            .sortedWith(compareBy<Voice>({ it.isNetworkConnectionRequired }, { -it.quality }, { it.name }))

        voices = buildList {
            add(VoiceOption(null, "Default voice for this language"))
            exactVoices.forEachIndexed { index, voice ->
                add(VoiceOption(voice, buildVoiceLabel(voice, index + 1)))
            }
        }

        suppressSpinnerCallbacks = true
        setSpinnerItems(voiceSpinner, voices.map { it.label })
        voiceSpinner.isEnabled = true

        val currentVoiceName = textToSpeech.voice?.name
        val currentIndex = voices.indexOfFirst { it.voice?.name == currentVoiceName }
        voiceSpinner.setSelection(if (currentIndex >= 0) currentIndex else 0, false)
        suppressSpinnerCallbacks = false

        updateVoiceInfo(voices[voiceSpinner.selectedItemPosition.coerceIn(voices.indices)])
    }

    private fun buildLanguageLabel(locale: Locale): String {
        val displayName = locale.getDisplayName(Locale.getDefault()).ifBlank { locale.toLanguageTag() }
        return "$displayName (${locale.toLanguageTag()})"
    }

    private fun buildVoiceLabel(voice: Voice, number: Int): String {
        val connection = if (voice.isNetworkConnectionRequired) "Online" else "Offline"
        val quality = qualityLabel(voice.quality)
        return "Voice $number - $connection - $quality - ${voice.name}"
    }

    private fun updateVoiceInfo(option: VoiceOption) {
        val voice = option.voice
        voiceInfoText.text = if (voice == null) {
            "Uses the TTS engine's default voice for the selected language."
        } else {
            val connection = if (voice.isNetworkConnectionRequired) {
                "Requires network"
            } else {
                "Available offline"
            }
            "${qualityLabel(voice.quality)} quality - $connection - ${voice.locale.toLanguageTag()}"
        }
    }

    private fun qualityLabel(quality: Int): String {
        return when {
            quality >= Voice.QUALITY_VERY_HIGH -> "Very high"
            quality >= Voice.QUALITY_HIGH -> "High"
            quality >= Voice.QUALITY_NORMAL -> "Normal"
            quality >= Voice.QUALITY_LOW -> "Low"
            else -> "Very low"
        }
    }

    private fun findBestLanguageIndex(locale: Locale): Int {
        val exactTag = locale.toLanguageTag()
        val exact = languages.indexOfFirst { it.locale.toLanguageTag() == exactTag }
        if (exact >= 0) return exact

        val languageAndCountry = languages.indexOfFirst {
            it.locale.language == locale.language && it.locale.country == locale.country
        }
        if (languageAndCountry >= 0) return languageAndCountry

        val languageOnly = languages.indexOfFirst { it.locale.language == locale.language }
        return if (languageOnly >= 0) languageOnly else 0
    }

    private fun saveSelectedLanguage(locale: Locale) {
        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            .edit()
            .putString(PREF_LAST_LANGUAGE_TAG, locale.toLanguageTag())
            .apply()
    }

    private fun getSavedLanguage(): Locale? {
        val languageTag = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            .getString(PREF_LAST_LANGUAGE_TAG, null)
            ?.trim()
            .orEmpty()

        if (languageTag.isBlank()) return null

        return Locale.forLanguageTag(languageTag)
            .takeIf { locale -> locale.language.isNotBlank() }
    }

    private fun isLocaleUsable(locale: Locale): Boolean {
        return when (textToSpeech.isLanguageAvailable(locale)) {
            TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> false
            else -> true
        }
    }

    private fun speakText() {
        val text = validateTextAndApplySettings() ?: return
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "speech_${System.currentTimeMillis()}")
    }

    private fun chooseMp3Destination() {
        val text = validateTextAndApplySettings() ?: return

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

        // Re-apply all settings after returning from the document picker.
        if (!applyCurrentTtsSettings()) {
            setExportButtonIdle()
            tryDelete(destination)
            return
        }

        textToSpeech.stop()

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

    private fun validateTextAndApplySettings(): String? {
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

        if (!applyCurrentTtsSettings()) return null
        return text
    }

    private fun applyCurrentTtsSettings(): Boolean {
        val languageIndex = languageSpinner.selectedItemPosition
        if (languageIndex !in languages.indices) {
            Toast.makeText(this, "Please select a language.", Toast.LENGTH_SHORT).show()
            return false
        }

        val selectedLanguage = languages[languageIndex]
        val availability = textToSpeech.isLanguageAvailable(selectedLanguage.locale)
        if (
            availability == TextToSpeech.LANG_MISSING_DATA ||
            availability == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            Toast.makeText(
                this,
                "${selectedLanguage.label} is not available on this device.",
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        val languageResult = textToSpeech.setLanguage(selectedLanguage.locale)
        if (
            languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            Toast.makeText(
                this,
                "${selectedLanguage.label} could not be selected.",
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        val voiceIndex = voiceSpinner.selectedItemPosition
        val selectedVoice = voices.getOrNull(voiceIndex)?.voice
        if (selectedVoice != null) {
            val voiceResult = textToSpeech.setVoice(selectedVoice)
            if (voiceResult != TextToSpeech.SUCCESS) {
                Toast.makeText(
                    this,
                    "The selected voice could not be applied. Try the default voice.",
                    Toast.LENGTH_LONG
                ).show()
                return false
            }
        }

        val pitchResult = textToSpeech.setPitch(currentPitch())
        val rateResult = textToSpeech.setSpeechRate(currentRate())
        if (pitchResult != TextToSpeech.SUCCESS || rateResult != TextToSpeech.SUCCESS) {
            Toast.makeText(
                this,
                "The selected pitch or speed is not supported by this TTS engine.",
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        return true
    }

    private fun currentPitch(): Float = controlValue(pitchSeekBar.progress)
    private fun currentRate(): Float = controlValue(rateSeekBar.progress)

    private fun controlValue(progress: Int): Float {
        return (progress + MIN_CONTROL_PERCENT) / 100f
    }

    private fun updatePitchLabel() {
        pitchValueText.text = String.format(Locale.getDefault(), "%.2fx", currentPitch())
    }

    private fun updateRateLabel() {
        rateValueText.text = String.format(Locale.getDefault(), "%.2fx", currentRate())
    }

    private fun setSpinnerItems(spinner: Spinner, items: List<String>) {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            items
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    private fun failExport(utteranceId: String, message: String) {
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
            // Some document providers do not allow delete; leave the empty or partial file in place.
        }
    }

    private fun setExportButtonIdle() {
        if (!::exportButton.isInitialized) return
        exportButton.text = "Export to MP3"
        exportButton.isEnabled = ttsReady && languages.isNotEmpty()
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

    private data class LanguageOption(
        val locale: Locale,
        val label: String
    )

    private data class VoiceOption(
        val voice: Voice?,
        val label: String
    )

    private data class ExportCapture(
        val utteranceId: String,
        val destination: Uri,
        val tempFile: File,
        val audio: ByteArrayOutputStream = ByteArrayOutputStream(),
        var sampleRate: Int = 0,
        var audioFormat: Int = AudioFormat.ENCODING_INVALID,
        var channelCount: Int = 0
    )

    companion object {
        private const val PREFERENCES_NAME = "tts_preferences"
        private const val PREF_LAST_LANGUAGE_TAG = "last_language_tag"
        private const val MIN_CONTROL_PERCENT = 50
        private const val MAX_CONTROL_PERCENT = 200
        private const val CONTROL_RANGE = MAX_CONTROL_PERCENT - MIN_CONTROL_PERCENT
        private const val DEFAULT_CONTROL_PROGRESS = 100 - MIN_CONTROL_PERCENT
    }
}
