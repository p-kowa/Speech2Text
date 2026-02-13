package com.example.speech2text

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.ToggleButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat


class MainActivity : AppCompatActivity() {

    private lateinit var micToggle : ToggleButton
    private lateinit var methodSelector : AutoCompleteTextView
    private lateinit var trTV : TextView
    private lateinit var tiTV : TextView
    private lateinit var rgLanguage : RadioGroup
    private lateinit var speechHelper: SpeechHelper

    private lateinit var shareButton : ImageButton

    private lateinit var voskHelper: VoskHelper
    private val permissionManager = PermissionManager(activityResultRegistry, this)

    // Currently selected language
    private val selectedLanguage: String
        get() {
            val selectedId = rgLanguage.checkedRadioButtonId
            if (selectedId == -1) return "de-DE" // Fallback

            val index = rgLanguage.indexOfChild(findViewById(selectedId))
            val languageCodes = resources.getStringArray(R.array.language_codes)
            return languageCodes.getOrElse(index) { "de-DE" }
        }

    // Currently selected Vosk model (based on the same index as selectedLanguage)
    private val selectedVoskModel: String
        get() {
            val selectedId = rgLanguage.checkedRadioButtonId
            if (selectedId == -1) return "vosk-model-small-de-0.15" // Fallback

            val index = rgLanguage.indexOfChild(findViewById(selectedId))
            val voskModels = resources.getStringArray(R.array.vosk_models)
            return voskModels.getOrElse(index) { "vosk-model-small-de-0.15" }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.activity_main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val methods = resources.getStringArray(R.array.methods)
        micToggle = findViewById(R.id.microSwitch)
        methodSelector = findViewById(R.id.spMethod)
        trTV = findViewById(R.id.tvResult)
        tiTV = findViewById(R.id.tvInfo)
        rgLanguage = findViewById(R.id.rgLanguage)
        shareButton = findViewById(R.id.btnShare)

        permissionManager.register("audio_permission_key")
        @Suppress("DEPRECATION")
        window.navigationBarColor = getColor(R.color.blueLight)

        voskHelper = VoskHelper(
            context = this,
            onResult = { text -> trTV.text = text },
            onPartialResult = { text -> tiTV.text = text },
            onStatus = { status -> tiTV.text = status },
            onError = { error ->
                tiTV.text = error
                micToggle.isChecked = false
                methodSelector.isEnabled = true
            }
        )

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, methods)

        methodSelector.setAdapter(adapter)
        if (methods.isNotEmpty()) {
            methodSelector.setText(methods[0], false)
        }


        // Dynamically create RadioButtons from array
        setupLanguageRadioButtons()

        // SpeechHelper with Lambda callbacks (UI logic stays in Helper!)
        speechHelper = SpeechHelper(
            context = this,
            onStatusChange = { status -> tiTV.text = status },
            onResult = { text -> trTV.text = text },
            onError = { error ->
                tiTV.text = error
                micToggle.isChecked = false
                methodSelector.isEnabled = true
            }
        )

        micToggle.setOnClickListener {
            val method = methodSelector.text.toString()
            permissionManager.checkAudioPermission(
                onGranted = {
                    when (micToggle.isChecked) {
                        true -> {
                            startRecording(method)
                            methodSelector.isEnabled=false
                            enableLanguageRadioButtons(false)

                        }
                        false -> {
                            stopRecording(method)
                            methodSelector.isEnabled=true
                            enableLanguageRadioButtons(true)

                        }
                    }
                },
                onDenied = {
                    trTV.text = getString(R.string.error_microphone_permission_denied)
                }
            )
        }

        shareButton.setOnClickListener {
            val textToShare = trTV.text.toString()
            if (textToShare.isNotBlank()) {
                val shareIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_TEXT, textToShare)
                    type = "text/plain"
                }
                startActivity(android.content.Intent.createChooser(shareIntent, "Share transcription via"))
            } else {
                tiTV.text = getString(R.string.info_no_transcription_to_share)
            }
        }
    }
    private fun startRecording(method: String) {
        when (method) {
            "Google" -> speechHelper.startListening(selectedLanguage)
            "Local Whisper" -> trTV.text = "Local Whisper coming soon..."
            "Vosk" -> voskHelper.start(selectedVoskModel)
            else -> trTV.text = getString(R.string.unknown_method_selected)
        }
    }

    private fun stopRecording(method: String) {
        when (method) {
            "Google" -> speechHelper.stopListening()
            "Local Whisper" -> trTV.text = "Local Whisper coming soon..."
            "Vosk" -> voskHelper.stop()
            else -> trTV.text = getString(R.string.unknown_method_selected)
        }
    }

    private fun setupLanguageRadioButtons() {
        val languages = resources.getStringArray(R.array.languages)

        languages.forEachIndexed { index, language ->
            val radioButton = RadioButton(this).apply {
                id = View.generateViewId()
                text = language
                textSize = 16f
                setTextColor(ContextCompat.getColor(context, R.color.white))
                isChecked = (index == 0) // German is preselected
                setPadding(0, 8, 32, 8)
            }
            rgLanguage.addView(radioButton)
        }
    }

    private fun enableLanguageRadioButtons(enabled: Boolean) {
        for (i in 0 until rgLanguage.childCount) {
            val child = rgLanguage.getChildAt(i)
            child.isEnabled = enabled
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechHelper.destroy()
        voskHelper.destroy()
    }


}