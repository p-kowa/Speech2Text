package com.example.speech2text

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Helper class for Google Speech-to-Text Recognition
 * Uses simple lambdas for callbacks
 */
class SpeechHelper(
    private val context: Context,
    private val onStatusChange: (String) -> Unit,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var shouldContinue = false // Flag for continuous recording
    private var currentLanguageCode = "de-DE" // Remember current language
    var entireText : String = ""

    // AudioManager to mute system sounds
    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var originalStreamVolume: Int = 0

    init {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(createRecognitionListener())
        }
    }

    /**
     * Mutes notification and system sounds to suppress beeps
     */
    private fun muteSystemSounds() {
        try {
            originalStreamVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
        } catch (_: Exception) {
            // Ignore if we can't mute
        }
    }

    /**
     * Restores system sound volume
     */
    private fun restoreSystemSounds() {
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, originalStreamVolume, 0)
        } catch (_: Exception) {
            // Ignore if we can't restore
        }
    }

    /**
     * Starts speech recognition
     */
    fun startListening(languageCode: String = "de-DE") {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech Recognition not available")
            return
        }

        shouldContinue = true // Enable continuous recording
        currentLanguageCode = languageCode // Remember language
        isListening = true
        muteSystemSounds() // Mute beeps
        onStatusChange("🎤 Starting recording...")

        speechRecognizer?.startListening(createRecognitionIntent())
    }

    /**
     * Stops speech recognition
     */
    fun stopListening() {
        shouldContinue = false // Disable continuous recording
        isListening = false
        entireText = "" // Reset text for next start
        speechRecognizer?.stopListening()
        restoreSystemSounds() // Restore volume
        onStatusChange("⏹ Recording stopped")
    }

    /**
     * Cancels speech recognition
     */
    fun cancel() {
        speechRecognizer?.cancel()
    }

    /**
     * Releases resources
     */
    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    /**
     * Creates an intent for speech recognition with beep suppression
     */
    private fun createRecognitionIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguageCode)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

            // Extended silence thresholds to prevent frequent restarts
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 15000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 15000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000L)

            // Various beep suppression flags (device-dependent)
            putExtra("android.speech.extra.DICTATION_MODE", true)
            putExtra("android.speech.extras.AUDIO_INJECTION", false)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra("calling_package", context.packageName)
        }
    }

    /**
     * Creates the RecognitionListener
     */
    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            onStatusChange("🎤 Ready... Speak now!")
        }

        override fun onBeginningOfSpeech() {
            onStatusChange("🎤 Listening...")
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Optional: Audio level visualization
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // Not needed
        }

        override fun onEndOfSpeech() {
            onStatusChange("⏳ Processing...")
        }

        override fun onError(error: Int) {
            // ERROR_CLIENT is triggered when manually stopping - ignore it!
            if (error == SpeechRecognizer.ERROR_CLIENT && !shouldContinue) {
                return
            }

            isListening = false
            val errorMessage = getErrorMessage(error)

            // Automatically restart on certain errors
            if (shouldContinue && (error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                // Short delay before restart
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (shouldContinue) {
                        isListening = true
                        speechRecognizer?.startListening(createRecognitionIntent())
                    }
                }, 100)
            } else {
                onError("❌ $errorMessage")
            }
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: ""

            if (text.isNotEmpty()) {
                entireText += if (entireText.isEmpty()) text else " $text"
                onResult(entireText)
            }

            // Automatically restart when shouldContinue is active
            if (shouldContinue) {
                // Short delay before restart (100ms)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (shouldContinue) { // Check again in case stopped in the meantime
                        isListening = true
                        speechRecognizer?.startListening(createRecognitionIntent())
                    }
                }, 100)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: ""

            if (text.isNotEmpty()) {
                onStatusChange("⏺ $text...")
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            // Not needed
        }
    }

    /**
     * Converts error codes to readable messages
     */
    private fun getErrorMessage(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Connection disconnected"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission missing"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No audio detected"
            else -> "Unknown error: $error"
        }
    }

    /**
     * Checks if speech recognition is available
     */
    fun isRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }
}
