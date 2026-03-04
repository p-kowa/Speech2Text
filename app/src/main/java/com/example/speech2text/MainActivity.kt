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
import android.widget.EditText
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
import com.google.android.material.chip.Chip
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MainActivity : AppCompatActivity() {

    private lateinit var micToggle : FloatingActionButton
    private lateinit var methodSelector : AutoCompleteTextView
    private lateinit var erTV : EditText
    private lateinit var tiTV : TextView
    private lateinit var rgLanguage : RadioGroup
    private lateinit var speechHelper: SpeechHelper
    private lateinit var shareButton : ImageButton
    private lateinit var whisperHelper: WhisperHelper
    private lateinit var voskHelper: VoskHelper
    private val permissionManager = PermissionManager(activityResultRegistry, this)
    private lateinit var gestureDetector: GestureDetector

    private lateinit var chipImprove: Chip
    private lateinit var chipSummarize: Chip
    private lateinit var chipTranslate1: Chip
    private lateinit var chipTranslate2: Chip
    private lateinit var chipAskQuestion: Chip

    private lateinit var geminiHelper: GeminiHelper

    private var translate1Language: String = "English"
    private var translate2Language: String = "German"
    private var originalText: String = "" // Store original text for translations

    var modelPath : String =""
    var isRecording = false

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

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar_main)
        val methods = resources.getStringArray(R.array.methods)

        initializeViews()



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
                        erTV.setText("")
                        speechHelper.entireText = ""
                        voskHelper.entireText = ""
                        whisperHelper.entireText = ""
                        tiTV.text = getString(R.string.text_cleared)
                    } else {
                        // Swipe LEFT - Share text
                        val textToShare = erTV.text.toString()
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
            onResult = { text -> erTV.setText(text) },
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
            onResult = { text -> erTV.setText(text) },
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
            onResult = { text -> erTV.setText(text) },
            onPartialResult = { text -> tiTV.text = text },
            onError = { error ->
                tiTV.text = error
                isRecording = false
                updateFabIcon()
                methodSelector.isEnabled = true
            }
        )
        initializeListeners()
        geminiHelper = GeminiHelper(this)
        configureTranslateButtons()

        // Show Gemini model info if API key is configured
        if (geminiHelper.hasApiKey()) {
            tiTV.text = geminiHelper.getModelSummary()
        }

    }

    private fun initializeViews() {
        micToggle = findViewById(R.id.microSwitch)
        methodSelector = findViewById(R.id.spMethod)
        erTV = findViewById(R.id.evResult)
        tiTV = findViewById(R.id.tvInfo)
        rgLanguage = findViewById(R.id.rgLanguage)
        shareButton = findViewById(R.id.btnShare)
        chipImprove = findViewById(R.id.chip_improve)
        chipSummarize = findViewById(R.id.chip_summarize)
        chipTranslate1 = findViewById(R.id.chip_translate_1)
        chipTranslate2 = findViewById(R.id.chip_translate_2)
        chipAskQuestion = findViewById(R.id.chip_ask_question)
    }

    private fun initializeListeners(){
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
                    erTV.setText(getString(R.string.error_microphone_permission_denied))
                }
            )
        }

        shareButton.setOnClickListener {
            val textToShare = erTV.text.toString()
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

        // Setup swipe gestures on the transcription EditText
        @SuppressLint("ClickableViewAccessibility")
        erTV.setOnTouchListener { view, event ->
            val handled = gestureDetector.onTouchEvent(event)

            // If gesture was not handled (no swipe), let EditText handle it normally for editing
            if (!handled) {
                view.onTouchEvent(event)
            }
            false // Don't consume the event, let EditText handle it for editing
        }

        chipImprove.setOnClickListener {
            if(!geminiHelper.hasApiKey()) {
                openGeminiKeyDialog()
            } else {
                val text = erTV.text.toString().trim()
                if (text.isEmpty()) {
                    Toast.makeText(this, R.string.input_text_empty, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                executeGeminiTask(updateOriginal = true) {
                    geminiHelper.improveText(text)
                }
            }
        }

        chipSummarize.setOnClickListener {
            if(!geminiHelper.hasApiKey()) {
                openGeminiKeyDialog()
            } else {
                val text = erTV.text.toString().trim()
                if (text.isEmpty()) {
                    Toast.makeText(this, R.string.input_text_empty, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                executeGeminiTask(updateOriginal = true) {
                    geminiHelper.summarizeText(text)
                }
            }
        }

        chipTranslate1.setOnClickListener {
            if(!geminiHelper.hasApiKey()) {
                openGeminiKeyDialog()
            } else {
                val text = if (originalText.isEmpty()) erTV.text.toString().trim() else originalText
                if (text.isEmpty()) {
                    Toast.makeText(this, R.string.input_text_empty, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                executeTranslate(text, translate1Language)
            }
        }

        chipTranslate2.setOnClickListener {
            if(!geminiHelper.hasApiKey()) {
                openGeminiKeyDialog()
            } else {
                val text = if (originalText.isEmpty()) erTV.text.toString().trim() else originalText
                if (text.isEmpty()) {
                    Toast.makeText(this, R.string.input_text_empty, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                executeTranslate(text, translate2Language)
            }
        }

        chipAskQuestion.setOnClickListener {
            if(!geminiHelper.hasApiKey()) {
                openGeminiKeyDialog()
            } else {
                val text = erTV.text.toString().trim()
                if (text.isEmpty()) {
                    Toast.makeText(this, R.string.input_text_empty, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                executeAskQuestion(text)
            }
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
            if (item.itemId == R.id.mVoice){
                val selectedId = rgLanguage.checkedRadioButtonId
                if (selectedId != -1) {
                    val index = rgLanguage.indexOfChild(findViewById(selectedId))
                    val languageName = resources.getStringArray(R.array.languages)[index]
                    val intent = android.content.Intent(this, VoiceCloning::class.java)
                    intent.putExtra("language_name", languageName)
                    intent.putExtra("language_code", selectedLanguage)
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Please select a language first", Toast.LENGTH_SHORT).show()
                }
            }
            if (item.itemId == R.id.mGemini) {
                openGeminiKeyDialog()
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
            else -> erTV.setText(getString(R.string.unknown_method_selected))
        }
    }

    private fun stopRecording(method: String) {
        when (method) {
            "Google" -> speechHelper.stopListening()
            "Whisper" -> whisperHelper.stop()
            "Vosk" -> voskHelper.stop()
            else -> erTV.setText(getString(R.string.unknown_method_selected))
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

        // Add listener to update translate buttons when language changes
        rgLanguage.setOnCheckedChangeListener { _, _ ->
            configureTranslateButtons()
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

    private fun openGeminiKeyDialog() {
        val dialog = GeminiKeyDialog(geminiHelper) {
            // Callback after key is saved - you can add any additional logic here
            tiTV.text = getString(R.string.api_key_saved)
        }
        dialog.show(supportFragmentManager, "GeminiKeyDialog")
    }

    private fun executeGeminiTask(updateOriginal: Boolean = false, task: suspend () -> Result<String>) {
        if (!geminiHelper.hasApiKey()) {
            Toast.makeText(this, R.string.api_key_not_configured, Toast.LENGTH_LONG).show()
            return
        }

        // Show progress
        setChipsEnabled(false)
        tiTV.text = getString(R.string.processing)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    task()
                }

                result.onSuccess { text ->
                    erTV.setText(text)

                    // Show token info and operation status
                    val tokenInfo = geminiHelper.getTokenInfo(text)
                    tiTV.text = "✅ ${getString(R.string.operation_successful)} | $tokenInfo"

                    // Update original text if needed (for Improve/Summarize)
                    if (updateOriginal) {
                        originalText = text
                    }
                }.onFailure { error ->
                    val errorMsg = error.message ?: "Unknown error"
                    tiTV.text = getString(R.string.error_prefix) + " $errorMsg"

                    // Check if it's a token limit error
                    if (errorMsg.contains("MAX_TOKENS", ignoreCase = true)) {
                        Toast.makeText(this@MainActivity,
                            "⚠️ Token limit reached! Try shorter text or increase maxOutputTokens.",
                            Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@MainActivity,
                            getString(R.string.gemini_error) + ": $errorMsg",
                            Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                tiTV.text = getString(R.string.error_prefix) + " ${e.message}"
                Toast.makeText(this@MainActivity,
                    getString(R.string.unexpected_error) + ": ${e.message}",
                    Toast.LENGTH_LONG).show()
            } finally {
                setChipsEnabled(true)
            }
        }
    }

    private fun executeTranslate(text: String, targetLanguage: String) {
        executeGeminiTask {
            geminiHelper.translateText(text, targetLanguage)
        }
    }

    private fun executeAskQuestion(text: String) {
        if (!geminiHelper.hasApiKey()) {
            Toast.makeText(this, R.string.api_key_not_configured, Toast.LENGTH_LONG).show()
            return
        }

        // Disable button during processing
        chipAskQuestion.isEnabled = false
        tiTV.text = getString(R.string.processing)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    geminiHelper.answerQuestion(text)
                }

                result.onSuccess { answer ->
                    // Antwort ersetzt den Inhalt von erTV
                    erTV.setText(answer)

                    // Show token info and success status
                    val tokenInfo = geminiHelper.getTokenInfo(answer)
                    tiTV.text = "✅ ${getString(R.string.answer_added)} | $tokenInfo"
                }.onFailure { error ->
                    val errorMsg = error.message ?: "Unknown error"
                    tiTV.text = getString(R.string.error_prefix) + " $errorMsg"

                    // Check if it's a token limit error
                    if (errorMsg.contains("MAX_TOKENS", ignoreCase = true)) {
                        Toast.makeText(this@MainActivity,
                            "⚠️ Token limit reached! Answer was too long. Try a shorter question or increase maxOutputTokens.",
                            Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@MainActivity,
                            getString(R.string.gemini_error) + ": $errorMsg",
                            Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                tiTV.text = getString(R.string.error_prefix) + " ${e.message}"
                Toast.makeText(this@MainActivity,
                    getString(R.string.unexpected_error) + ": ${e.message}",
                    Toast.LENGTH_LONG).show()
            } finally {
                chipAskQuestion.isEnabled = true
            }
        }
    }

    private fun setChipsEnabled(enabled: Boolean) {
        chipImprove.isEnabled = enabled
        chipSummarize.isEnabled = enabled
        chipTranslate1.isEnabled = enabled
        chipTranslate2.isEnabled = enabled
        chipAskQuestion.isEnabled = enabled
    }

    private fun configureTranslateButtons() {
        when {
            selectedLanguage.startsWith("de") -> {
                // Deutsch gewählt -> Übersetze zu English und Polski
                translate1Language = "English"
                translate2Language = "Polish"
                chipTranslate1.text = getString(R.string.translate_to_en)
                chipTranslate2.text = getString(R.string.translate_to_pl)
            }
            selectedLanguage.startsWith("en") -> {
                // English gewählt -> Übersetze zu Deutsch und Polski
                translate1Language = "German"
                translate2Language = "Polish"
                chipTranslate1.text = getString(R.string.translate_to_de)
                chipTranslate2.text = getString(R.string.translate_to_pl)
            }
            selectedLanguage.startsWith("pl") -> {
                // Polski gewählt -> Übersetze zu English und Deutsch
                translate1Language = "English"
                translate2Language = "German"
                chipTranslate1.text = getString(R.string.translate_to_en)
                chipTranslate2.text = getString(R.string.translate_to_de)
            }
            else -> {
                // Fallback
                translate1Language = "English"
                translate2Language = "German"
                chipTranslate1.text = getString(R.string.translate_to_en)
                chipTranslate2.text = getString(R.string.translate_to_de)
            }
        }
    }

}