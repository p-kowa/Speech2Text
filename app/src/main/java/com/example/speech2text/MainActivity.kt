package com.example.speech2text

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.GestureDetector
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlin.math.abs
import android.content.Context
import android.widget.Toast
import java.io.File


class MainActivity : AppCompatActivity() {

    private lateinit var micToggle : FloatingActionButton
    private lateinit var methodSelector : AutoCompleteTextView
    private lateinit var trTV : TextView
    private lateinit var tiTV : TextView
    private lateinit var rgLanguage : RadioGroup
    private lateinit var speechHelper: SpeechHelper

    private lateinit var shareButton : ImageButton

    private lateinit var whisperHelper: WhisperHelper

    private lateinit var voskHelper: VoskHelper
    private val permissionManager = PermissionManager(activityResultRegistry, this)

    var modelPath : String =""



    // Track recording state
    private var isRecording = false

    // Gesture detector for swipe actions (initialized in onCreate)
    private lateinit var gestureDetector: GestureDetector

    // Currently selected language
    private val selectedLanguage: String
        get() {
            val selectedId = rgLanguage.checkedRadioButtonId
            if (selectedId == -1) return "de-DE" // Fallback

            val index = rgLanguage.indexOfChild(findViewById(selectedId))
            val languageCodes = resources.getStringArray(R.array.language_codes)
            return languageCodes.getOrElse(index) { "de-DE" }
        }

    // Whisper uses 2-letter ISO codes (de, en, es) instead of Android's format (de-DE, en-US)
    private val selectedWhisperLanguage: String
        get() {
            val androidCode = selectedLanguage
            return when {
                androidCode.startsWith("de") -> "de"
                androidCode.startsWith("en") -> "en"
                androidCode.startsWith("es") -> "es"
                androidCode.startsWith("fr") -> "fr"
                androidCode.startsWith("it") -> "it"
                androidCode.startsWith("pl") -> "pl"
                else -> "en" // Fallback to English
            }
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

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.activity_main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        val methods = resources.getStringArray(R.array.methods)
        micToggle = findViewById(R.id.microSwitch)
        methodSelector = findViewById(R.id.spMethod)
        trTV = findViewById(R.id.tvResult)
        tiTV = findViewById(R.id.tvInfo)
        rgLanguage = findViewById(R.id.rgLanguage)
        shareButton = findViewById(R.id.btnShare)

        val sharedPrefs = this.getSharedPreferences("ModelSettings", Context.MODE_PRIVATE)
        modelPath = sharedPrefs.getString("selected_model_path", "") ?: ""

        permissionManager.register("audio_permission_key")
        @Suppress("DEPRECATION")
        window.navigationBarColor = getColor(R.color.blueLight)
        window.statusBarColor = getColor(R.color.blueLight)
        setSupportActionBar(toolbar)
        // Initialize FAB icon
        updateFabIcon()

        // Initialize gesture detector for swipe actions
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100 // Minimum distance in pixels
            private val SWIPE_VELOCITY_THRESHOLD = 100 // Minimum velocity

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val diffX = e2.x - (e1?.x ?: 0f)

                if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) {
                        // Swipe RIGHT - Clear text
                        trTV.text = ""
                        speechHelper.entireText = ""
                        voskHelper.entireText = ""
                        whisperHelper.entireText = ""
                        tiTV.text = getString(R.string.text_cleared)
                    } else {
                        // Swipe LEFT - Share text
                        val textToShare = trTV.text.toString()
                        if (textToShare.isNotBlank()) {
                            val shareIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                putExtra(android.content.Intent.EXTRA_TEXT, textToShare)
                                type = "text/plain"
                            }
                            startActivity(android.content.Intent.createChooser(shareIntent, getString(R.string.share_chooser_title)))
                            tiTV.text = getString(R.string.sharing_text)
                        } else {
                            tiTV.text = getString(R.string.info_no_transcription_to_share)
                        }
                    }
                    return true
                }
                return false
            }
        })

        voskHelper = VoskHelper(
            context = this,
            onResult = { text -> trTV.text = text },
            onPartialResult = { text -> tiTV.text = text },
            onStatus = { status -> tiTV.text = status },
            onError = { error ->
                tiTV.text = error
                isRecording = false
                updateFabIcon()
                methodSelector.isEnabled = true
            }
        )

        // Benutzerdefinierten Adapter mit dunklem Hintergrund für bessere Lesbarkeit
        val adapter = ArrayAdapter(this, R.layout.dropdown_item, methods)

        methodSelector.setAdapter(adapter)

        // Setze Dropdown-Hintergrund programmatisch (funktioniert auf allen Themes)
        methodSelector.setDropDownBackgroundDrawable(
            ContextCompat.getDrawable(this, android.R.color.transparent)?.apply {
                setTint(ContextCompat.getColor(this@MainActivity, R.color.blueDarker))
            }
        )

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
                isRecording = false
                updateFabIcon()
                methodSelector.isEnabled = true
            }
        )

        whisperHelper = WhisperHelper(
            context = this,
            onStatus = { status -> tiTV.text = status },
            onResult = { text -> trTV.text = text },
            onPartialResult = { text -> tiTV.text = text },
            onError = { error ->
                tiTV.text = error
                isRecording = false
                updateFabIcon()
                methodSelector.isEnabled = true
            }
        )

        micToggle.setOnClickListener {
            val method = methodSelector.text.toString()
            permissionManager.checkAudioPermission(
                onGranted = {
                    isRecording = !isRecording
                    when (isRecording) {
                        true -> {
                            startRecording(method)
                            methodSelector.isEnabled = false
                            enableLanguageRadioButtons(false)
                        }
                        false -> {
                            stopRecording(method)
                            methodSelector.isEnabled = true
                            enableLanguageRadioButtons(true)
                        }
                    }
                    updateFabIcon()
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

        // Setup swipe gestures on the transcription TextView
        trTV.setOnTouchListener { view, event ->
            val handled = gestureDetector.onTouchEvent(event)

            if (!handled && event.action == MotionEvent.ACTION_UP) {
                view.performClick()
            }
            true // Consume the event
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.s2t_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        try {
            if (item.itemId == R.id.mSetup) {
                val intent = android.content.Intent(this, ModelHelper::class.java)
                startActivity(intent)
            }

        } catch (ex: Exception) {
            tiTV.text = getString(R.string.error_str, ex.message ?: "Unknown error")
        }
        return true
    }
    private fun startRecording(method: String) {
        when (method) {
            "Google" -> speechHelper.startListening(selectedLanguage)
            "Whisper" -> {
                if (modelPath.isEmpty()) {
                    tiTV.text = getString(R.string.no_model_selected)
                    val intent = android.content.Intent(this, ModelHelper::class.java)
                    startActivity(intent)
                    isRecording = false
                    updateFabIcon()
                    methodSelector.isEnabled = true
                } else {
                    whisperHelper.setModelFile(modelPath)
                    whisperHelper.start(selectedWhisperLanguage)
                }
            }
            "Vosk" -> voskHelper.start(selectedVoskModel)
            else -> trTV.text = getString(R.string.unknown_method_selected)
        }
    }

    private fun stopRecording(method: String) {
        when (method) {
            "Google" -> speechHelper.stopListening()
            "Whisper" -> whisperHelper.stop()
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

    private fun updateFabIcon() {
        if (isRecording) {
            micToggle.setImageResource(R.drawable.microphone)
            micToggle.backgroundTintList = ContextCompat.getColorStateList(this, R.color.red)
        } else {
            micToggle.setImageResource(R.drawable.microphone_off)
            micToggle.backgroundTintList = ContextCompat.getColorStateList(this, R.color.blue)
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload model path in case it was changed in ModelHelper
        val sharedPrefs = getSharedPreferences("ModelSettings", Context.MODE_PRIVATE)
        modelPath = sharedPrefs.getString("selected_model_path", "") ?: ""
        android.util.Log.d("MainActivity", "Model path reloaded: $modelPath")
    }

    override fun onDestroy() {
        super.onDestroy()
        speechHelper.destroy()
        voskHelper.destroy()
        whisperHelper.destroy()
    }

}