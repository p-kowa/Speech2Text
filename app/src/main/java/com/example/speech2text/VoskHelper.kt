package com.example.speech2text

import android.content.Context
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import org.json.JSONObject

class VoskHelper(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onError: (String) -> Unit
) {

    private var speechService: SpeechService? = null
    private var model: Model? = null
    private var currentModelName: String? = null

    fun start(modelName: String) {
        // Wenn bereits das richtige Modell geladen ist, starte sofort
        if (currentModelName == modelName && model != null) {
            startListening()
        } else {
            // Stoppe aktuelle Aufnahme
            stop()

            // Wenn ein anderes Modell geladen ist, schließe es
            if (model != null) {
                model?.close()
                model = null
            }

            // Lade das neue Modell
            onStatus("⏳ Lade Vosk Modell...")
            StorageService.unpack(context, modelName, modelName,
                { loadedModel ->
                    model = loadedModel
                    currentModelName = modelName
                    onStatus("✅ Vosk bereit")
                    startListening()
                },
                { e ->
                    onError("❌ Vosk Fehler: ${e.message}")
                }
            )
        }
    }

    private fun startListening() {
        model?.let { voskModel ->
            onStatus("🎤 Vosk hört zu...")
            val rec = Recognizer(voskModel, 16000.0f)
            speechService = SpeechService(rec, 16000.0f)
            speechService?.startListening(createRecognitionListener())
        }
    }

    fun stop() {
        speechService?.stop()
        speechService = null
        onStatus("⏹ Vosk gestoppt")
    }

    fun destroy() {
        stop()
        model?.close()
        model = null
        currentModelName = null
    }


    /**
     * Erstellt den RecognitionListener für Vosk
     */
    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onResult(hypothesis: String) {
            val text = JSONObject(hypothesis).optString("text")
            if (text.isNotEmpty()) {
                this@VoskHelper.onResult("📝 $text")
            }
        }

        override fun onPartialResult(hypothesis: String) {
            val partial = JSONObject(hypothesis).optString("partial")
            if (partial.isNotEmpty()) {
                this@VoskHelper.onResult("🎙️ $partial")
            }
        }

        override fun onFinalResult(hypothesis: String) {
            val text = JSONObject(hypothesis).optString("text")
            if (text.isNotEmpty()) {
                this@VoskHelper.onResult("✅ $text")
            }
        }

        override fun onError(e: Exception) {
            this@VoskHelper.onError("❌ Vosk Fehler: ${e.message}")
        }

        override fun onTimeout() {
            this@VoskHelper.onStatus("⏱️ Vosk Timeout")
        }
    }
}
