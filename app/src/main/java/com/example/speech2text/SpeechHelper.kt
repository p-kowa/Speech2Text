package com.example.speech2text

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Helper-Klasse für Google Speech-to-Text Recognition
 * Verwendet einfache Lambdas für Callbacks
 */
class SpeechHelper(
    private val context: Context,
    private val onStatusChange: (String) -> Unit,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false  // Track ob wir aktiv zuhören

    init {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(createRecognitionListener())
        }
    }

    /**
     * Startet die Spracherkennung
     */
    fun startListening(languageCode: String = "de-DE") {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech Recognition nicht verfügbar")
            return
        }

        isListening = true
        onStatusChange("🎤 Starte Aufnahme...")

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.startListening(intent)
    }

    /**
     * Stoppt die Spracherkennung
     */
    fun stopListening() {
        isListening = false
        speechRecognizer?.stopListening()
        onStatusChange("⏹ Aufnahme gestoppt")
    }

    /**
     * Bricht die Spracherkennung ab
     */
    fun cancel() {
        speechRecognizer?.cancel()
    }

    /**
     * Gibt Ressourcen frei
     */
    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    /**
     * Erstellt den RecognitionListener
     */
    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            onStatusChange("🎤 Bereit... Sprechen Sie jetzt!")
        }

        override fun onBeginningOfSpeech() {
            onStatusChange("🎤 Höre zu...")
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Optional: Audio-Level Visualisierung
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // Nicht benötigt
        }

        override fun onEndOfSpeech() {
            onStatusChange("⏳ Verarbeite...")
        }

        override fun onError(error: Int) {
            // ERROR_CLIENT wird beim manuellen Stoppen ausgelöst - ignorieren!
            if (error == SpeechRecognizer.ERROR_CLIENT && !isListening) {
                return
            }

            isListening = false
            val errorMessage = getErrorMessage(error)
            onError("❌ $errorMessage")
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: ""

            if (text.isNotEmpty()) {
                onResult(text)
            } else {
                onError("Kein Text erkannt")
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
            // Nicht benötigt
        }
    }

    /**
     * Konvertiert Error-Codes zu lesbaren Nachrichten
     */
    private fun getErrorMessage(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio-Aufnahme Fehler"
            SpeechRecognizer.ERROR_CLIENT -> "Verbindung getrennt"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mikrofon-Berechtigung fehlt"
            SpeechRecognizer.ERROR_NETWORK -> "Netzwerk-Fehler"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Netzwerk-Timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "Keine Übereinstimmung gefunden"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer beschäftigt"
            SpeechRecognizer.ERROR_SERVER -> "Server-Fehler"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Kein Audio erkannt"
            else -> "Unbekannter Fehler: $error"
        }
    }

    /**
     * Prüft ob Speech Recognition verfügbar ist
     */
    fun isRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }
}