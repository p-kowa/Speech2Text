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
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlin.math.abs
import com.google.android.material.chip.Chip
import android.widget.Toast
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale


class MainActivity : AppCompatActivity() {

    private lateinit var currentLocale: Locale
    private lateinit var addlocals : MutableList<Locale>
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
    private lateinit var chipAskQuestion: Chip
    private lateinit var chipGroupActions: ChipGroup

    private lateinit var geminiHelper: GeminiHelper

    private val translateChips = mutableListOf<Pair<Chip, Locale>>() // Chip + Zielsprache
    private var originalText: String = "" // Store original text for translations

    var modelPath : String =""
    var isRecording = false

    // Currently selected language
    private val selectedLanguage: Locale
        get() {
            val selectedId = rgLanguage.checkedRadioButtonId
            if (selectedId == -1) return currentLocale

            val index = rgLanguage.indexOfChild(findViewById(selectedId))

            // Index 0 = System language
            if (index == 0) return currentLocale

            // Index 1+ = user-selected languages from addlocals
            return addlocals.getOrElse(index - 1) { currentLocale }
        }

    // Whisper uses 2-letter ISO codes (de, en, es) instead of Android's format (de-DE, en-US)
    private val selectedWhisperLanguage: String
        get() = selectedLanguage.language.take(2).lowercase()


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
        currentLocale = Locale.getDefault()
        addlocals = loadSelectedLocales()
        initializeViews()

        val sharedPrefs = getSharedPreferences("ModelSettings", MODE_PRIVATE)
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
        chipAskQuestion = findViewById(R.id.chip_ask_question)
        chipGroupActions = findViewById(R.id.chip_group_actions)
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
                    val intent = android.content.Intent(this, VoiceCloning::class.java)
                    intent.putExtra("locale", selectedLanguage)
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Please select a language first", Toast.LENGTH_SHORT).show()
                }
            }
            if (item.itemId == R.id.mGemini) {
                openGeminiKeyDialog()
            }
            if(item.itemId == R.id.mLanugage){
                openLanguageSelectionDialog()
            }

        } catch (ex: Exception) {
            tiTV.text = getString(R.string.error_str, ex.message ?: "Unknown error")
        }
        return true
    }
    private fun startRecording(method: String) {
        when (method) {
            "Google" -> speechHelper.startListening(selectedLanguage.language)
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

        val allLanguages : MutableList<Locale> = mutableListOf()
        allLanguages.add(currentLocale) // System language als erste Option
        allLanguages.addAll(addlocals) // Benutzerdefinierte Sprachen aus Einstellungen

        allLanguages.forEachIndexed { index, language ->
            val radioButton = RadioButton(this).apply {
                id = View.generateViewId()
                text = "${getCountryFlag(language)} ${language.displayLanguage}"
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
        val sharedPrefs = getSharedPreferences("ModelSettings", MODE_PRIVATE)
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
        chipAskQuestion.isEnabled = enabled
        // Dynamische Translate-Chips
        translateChips.forEach { (chip, _) ->
            chip.isEnabled = enabled
        }
    }

    private fun configureTranslateButtons() {
        // Entferne alte Translate-Chips
        translateChips.forEach { (chip, _) ->
            chipGroupActions.removeView(chip)
        }
        translateChips.clear()

        // Sammle alle verfügbaren Sprachen: currentLocale + addlocals (max 3 insgesamt)
        val allLanguages = mutableListOf<Locale>()
        allLanguages.add(currentLocale)
        allLanguages.addAll(addlocals)

        // Filtere die aktuell ausgewählte Sprache heraus -> die anderen sind für die Translate-Chips
        val otherLanguages = allLanguages.filter { it.language != selectedLanguage.language }

        // Erstelle dynamisch einen Chip für jede verfügbare Zielsprache
        otherLanguages.forEach { targetLocale ->
            val chip = Chip(this).apply {
                text = "${getCountryFlag(targetLocale)} ${targetLocale.displayLanguage}"
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.white))
                isClickable = true
                isFocusable = true
                chipIcon = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.ic_menu_compass)
                chipIconTint = ContextCompat.getColorStateList(this@MainActivity, R.color.white)
                chipBackgroundColor = ContextCompat.getColorStateList(this@MainActivity, R.color.blue)
                chipMinHeight = 120f

                setOnClickListener {
                    if (!geminiHelper.hasApiKey()) {
                        openGeminiKeyDialog()
                    } else {
                        val text = if (originalText.isEmpty()) erTV.text.toString().trim() else originalText
                        if (text.isEmpty()) {
                            Toast.makeText(this@MainActivity, R.string.input_text_empty, Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        executeTranslate(text, targetLocale.displayLanguage)
                    }
                }
            }

            // Füge Chip zur ChipGroup hinzu (nach Summarize, vor AskQuestion)
            val askQuestionIndex = chipGroupActions.indexOfChild(chipAskQuestion)
            chipGroupActions.addView(chip, askQuestionIndex)

            // Speichere Referenz
            translateChips.add(Pair(chip, targetLocale))
        }
    }

    private fun openLanguageSelectionDialog() {
        val builder = MaterialAlertDialogBuilder(this)
        val dialogView = layoutInflater.inflate(R.layout.language_selector, null)

        val autoCompleteTextView = dialogView.findViewById<AutoCompleteTextView>(R.id.language_autocomplete)
        val chipGroup = dialogView.findViewById<ChipGroup>(R.id.language_chip_group)
        val inputLayout = autoCompleteTextView.parent.parent as? TextInputLayout

        val selectedLocales = loadSelectedLocales()

        selectedLocales.forEach { locale ->
            addChip(locale, chipGroup, selectedLocales, inputLayout)
        }
        if (selectedLocales.size >= 2) inputLayout?.isEnabled = false

        val allLocales = Locale.getAvailableLocales()
            .filter { it.displayLanguage.isNotEmpty() }
            .distinctBy { it.displayLanguage }
            .sortedBy { it.getDisplayName(it) }

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, allLocales.map { it.getDisplayName(it) })
        autoCompleteTextView.setAdapter(adapter)

        autoCompleteTextView.setOnItemClickListener { _, _, position, _ ->
            val selectedName = adapter.getItem(position)
            val locale = allLocales.find { it.getDisplayName(it) == selectedName }

            if (locale != null && !selectedLocales.contains(locale) && selectedLocales.size < 2) {
                selectedLocales.add(locale)
                addChip(locale, chipGroup, selectedLocales, inputLayout)
                autoCompleteTextView.setText("", false)
                if (selectedLocales.size >= 2) inputLayout?.isEnabled = false
            }
        }

        builder.setView(dialogView)
            .setTitle("Select Languages")
            .setPositiveButton("OK") { _, _ ->
                saveLanguages(selectedLocales)

                // Update addlocals with new selection
                addlocals = loadSelectedLocales()

                // Rebuild RadioButtons with new languages
                rgLanguage.removeAllViews()
                setupLanguageRadioButtons()

                // Update translate chip buttons
                configureTranslateButtons()

                Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addChip(locale: Locale, group: ChipGroup, list: MutableList<Locale>, layout: TextInputLayout?) {
        val chip = Chip(this).apply {
            text = locale.displayLanguage.replaceFirstChar { it.uppercase() }
            isCloseIconVisible = true
            setOnCloseIconClickListener {
                group.removeView(this)
                list.remove(locale)
                layout?.isEnabled = true
            }
        }
        group.addView(chip)
    }

    private fun saveLanguages(locales: List<Locale>) {
        val sharedPref = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        sharedPref.edit {
            putString("lang_1", locales.getOrNull(0)?.language ?: "")
            putString("lang_2", locales.getOrNull(1)?.language ?: "")
        }
    }

    private fun loadSelectedLocales(): MutableList<Locale> {
        val sharedPref = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val lang1 = sharedPref.getString("lang_1", "") ?: ""
        val lang2 = sharedPref.getString("lang_2", "") ?: ""

        val list = mutableListOf<Locale>()
        if (lang1.isNotEmpty()) list.add(Locale.forLanguageTag(lang1))
        if (lang2.isNotEmpty()) list.add(Locale.forLanguageTag(lang2))
        return list
    }

    private fun getCountryFlag(locale: Locale): String {
        // Versuche zuerst den Country-Code vom Locale zu nehmen
        var countryCode = locale.country.uppercase()

        // Falls kein Country-Code vorhanden, mappe Sprache zu typischem Land
        if (countryCode.isEmpty()) {
            countryCode = when (locale.language.lowercase()) {
                "de" -> "DE"  // Deutsch -> Deutschland
                "en" -> "GB"  // English -> Großbritannien (alternativ: "US")
                "pl" -> "PL"  // Polski -> Polen
                "es" -> "ES"  // Español -> Spanien
                "fr" -> "FR"  // Français -> Frankreich
                "it" -> "IT"  // Italiano -> Italien
                "pt" -> "PT"  // Português -> Portugal
                "ru" -> "RU"  // Русский -> Russland
                "ja" -> "JP"  // 日本語 -> Japan
                "ko" -> "KR"  // 한국어 -> Korea
                "zh" -> "CN"  // 中文 -> China
                "ar" -> "SA"  // العربية -> Saudi-Arabien
                "nl" -> "NL"  // Nederlands -> Niederlande
                "sv" -> "SE"  // Svenska -> Schweden
                "no" -> "NO"  // Norsk -> Norwegen
                "da" -> "DK"  // Dansk -> Dänemark
                "fi" -> "FI"  // Suomi -> Finnland
                "tr" -> "TR"  // Türkçe -> Türkei
                "cs" -> "CZ"  // Čeština -> Tschechien
                "hu" -> "HU"  // Magyar -> Ungarn
                "ro" -> "RO"  // Română -> Rumänien
                "el" -> "GR"  // Ελληνικά -> Griechenland
                "uk" -> "UA"  // Українська -> Ukraine
                "hi" -> "IN"  // हिन्दी -> Indien
                "th" -> "TH"  // ไทย -> Thailand
                "vi" -> "VN"  // Tiếng Việt -> Vietnam
                "id" -> "ID"  // Bahasa Indonesia -> Indonesien
                else -> return "🌐"  // Fallback für unbekannte Sprachen
            }
        }

        // Validiere Country-Code
        if (countryCode.length != 2) return "🌐"

        // Konvertiere zu Unicode-Flag-Emoji
        val firstChar = Character.codePointAt(countryCode, 0) - 0x41 + 0x1F1E6
        val secondChar = Character.codePointAt(countryCode, 1) - 0x41 + 0x1F1E6

        return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
    }








}