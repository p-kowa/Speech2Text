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
    private val onPartialResult : (String) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onError: (String) -> Unit
) {

    private var speechService: SpeechService? = null
    private var model: Model? = null
    private var currentModelName: String? = null

    private var entireText : String = ""

    fun start(modelName: String) {

        if (currentModelName == modelName && model != null) {
            startListening()
        } else {
            stop()

            if (model != null) {
                model?.close()
                model = null
            }

            onStatus("⏳ Loading Vosk Modell...")
            StorageService.unpack(context, modelName, modelName,
                { loadedModel ->
                    model = loadedModel
                    currentModelName = modelName
                    onStatus("✅ Vosk ready")
                    startListening()
                },
                { e ->
                    onError("❌ Vosk error: ${e.message}")
                }
            )
        }
    }

    private fun startListening() {
        model?.let { voskModel ->
            onStatus("🎤 Vosk is hearing...")
            val rec = Recognizer(voskModel, 16000.0f)
            speechService = SpeechService(rec, 16000.0f)
            speechService?.startListening(createRecognitionListener())
        }
    }

    fun stop() {
        speechService?.stop()
        speechService = null
        entireText = "" // Reset text for next start
        onStatus("⏹ Vosk stopped")
    }

    fun destroy() {
        stop()
        model?.close()
        model = null
        currentModelName = null
    }


    /**
     * Creates the RecognitionListener for Vosk
     */
    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onResult(hypothesis: String) {
            val text = JSONObject(hypothesis).optString("text")
            if (text.isNotEmpty()) {
                entireText += if (entireText.isEmpty()) text else " $text"
                this@VoskHelper.onResult(entireText)
            }
        }

        override fun onPartialResult(hypothesis: String) {
            val partial = JSONObject(hypothesis).optString("partial")
            if (partial.isNotEmpty()) {
                this@VoskHelper.onPartialResult("🎙️ $partial")
            }
        }

        override fun onFinalResult(hypothesis: String) {
            val text = JSONObject(hypothesis).optString("text")
            if (text.isNotEmpty()) {
                entireText += if (entireText.isEmpty()) text else " $text"
                this@VoskHelper.onResult(entireText)
            }
        }

        override fun onError(e: Exception) {
            this@VoskHelper.onError("❌ Vosk error: ${e.message}")
        }

        override fun onTimeout() {
            this@VoskHelper.onStatus("⏱️ Vosk timeout")
        }
    }
}
