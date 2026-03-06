package com.example.speech2text

import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.appcompat.app.AlertDialog
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class VoiceCloning : AppCompatActivity() {

    private lateinit var geminiHelper: GeminiHelper
    private lateinit var chipNewText: Chip
    private lateinit var textToRead : TextView

    private val googleHelper = GoogleHelper()

    private lateinit var textRecInfo : TextView
    private lateinit var selectedLanguage: Locale // Default
    private lateinit var selectedLanguageName: String
    private lateinit var micToggleClone : FloatingActionButton

    private val permissionManager = PermissionManager(activityResultRegistry, this)

    private lateinit var recorderHelper: RecorderHelper

    private var isRecording = false

    private lateinit var recordings : RecyclerView
    private lateinit var recordingAdapter: RecordingAdapter
    private val recordingsList = mutableListOf<File>()

    // Track generated texts to ensure variety
    private val generatedTexts = mutableListOf<String>()

    // Current text that will be recorded
    private var currentTextToRecord: String = ""


    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clonevoice)

        enableEdgeToEdge()
        var toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar_clonevoice)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.clone_voice)
        window.navigationBarColor = getColor(R.color.blueLight)
        window.statusBarColor = getColor(R.color.blueLight)

        // Get language from intent
        selectedLanguage = intent.getSerializableExtra("locale") as? Locale ?: Locale.getDefault()
        selectedLanguageName = selectedLanguage.displayLanguage

        // Initialize RecorderHelper for WAV recording (22050 Hz for Piper TTS)
        recorderHelper = RecorderHelper(
            context = this,
            scope = CoroutineScope(kotlinx.coroutines.Dispatchers.Main),
            onError = { error ->
                textRecInfo.text = getString(R.string.error_str, error)
                isRecording = false
                updateFabIcon()
            },
            onStatus = { status ->
                textRecInfo.text = status
                // When recording is complete, save metadata and reload list
                if (status.contains("saved") || status.contains("stopped")) {
                    isRecording = false
                    updateFabIcon()

                    // Save text metadata for the recording
                    saveRecordingMetadata()

                    loadRecordings()
                }
            }
        )

        permissionManager.register("audio_permission_voice_clone")

        geminiHelper = GeminiHelper(this)
        if (!geminiHelper.hasApiKey()) {
            Toast.makeText(this, "Please set your Gemini API key in the settings.", Toast.LENGTH_LONG).show()
        }

        googleHelper.registerSignInLauncher(
            activity = this,
            onSuccess = { account ->
                Toast.makeText(this, "✅ signed as ${account.email}", Toast.LENGTH_SHORT).show()
            },
            onFailure = { error ->
                Toast.makeText(this, "❌ $error", Toast.LENGTH_SHORT).show()
            }
        )

        initializeViews()
        initializeListeners()
        setupRecyclerView()
        loadRecordings()
        updateFabIcon()
    }

    override fun onResume() {
        super.onResume()
        loadRecordings()
    }

    private fun initializeListeners() {
        chipNewText.setOnClickListener {
            chipNewText.isEnabled = false

            CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                textRecInfo.text = "Generating text..."

                // Pass previous texts to ensure variety
                val result = geminiHelper.generateText(selectedLanguageName, generatedTexts)

                result.onSuccess { text ->
                    currentTextToRecord = text
                    textToRead.text = text

                    // Add to history for variety tracking
                    generatedTexts.add(text)

                    // Keep only last 10 texts in memory
                    if (generatedTexts.size > 10) {
                        generatedTexts.removeAt(0)
                    }

                    textRecInfo.text = "✅ Text generated (${generatedTexts.size} total)"
                }.onFailure { error ->
                    textRecInfo.text = getString(R.string.error_str, error.message)
                }

                chipNewText.isEnabled = true
            }
        }

        micToggleClone.setOnClickListener {

            permissionManager.checkAudioPermission(
                onGranted = {
                    isRecording = !isRecording
                    when (isRecording) {
                        true -> {
                            startRecording()
                        }
                        false -> {
                            stopRecording()
                        }
                    }
                    updateFabIcon()
                },
                onDenied = {
                    textRecInfo.text =  getString(R.string.error_microphone_permission_denied)
                }
            )
        }

    }

    private fun stopRecording() {
        // Stop the recording (identical to MainActivity)
        recorderHelper.stopRecordingToFile()
        textRecInfo.text = "⏹️ Stopping recording..."
    }

    private fun startRecording() {
        // Check if there's text to record
        if (currentTextToRecord.isEmpty()) {
            textRecInfo.text = "⚠️ Please generate text first!"
            Toast.makeText(this, "Generate text before recording", Toast.LENGTH_SHORT).show()
            isRecording = false
            updateFabIcon()
            return
        }

        val fileName = "voice_${selectedLanguageName}_${System.currentTimeMillis()}.wav"

        textRecInfo.text = "🎙️ Recording in progress..."

        // Start recording (non-blocking, just like whisperHelper.start() in MainActivity)
        recorderHelper.startRecordingToFile(
            fileName = fileName
        )
    }

    /**
     * Save the text metadata for the most recent recording
     */
    private fun saveRecordingMetadata() {
        if (currentTextToRecord.isEmpty()) return

        // Find the most recent WAV file
        val filesDir = filesDir
        val wavFiles = filesDir.listFiles { file ->
            file.isFile && file.name.endsWith(".wav") && file.name.startsWith("voice_")
        }?.sortedByDescending { it.lastModified() }

        if (wavFiles != null && wavFiles.isNotEmpty()) {
            val latestRecording = wavFiles.first()

            // Create VoiceTrainingData and save metadata
            val trainingData = VoiceTrainingData(
                audioFile = latestRecording,
                text = currentTextToRecord,
                language = selectedLanguageName
            )

            trainingData.saveMetadata()

            android.util.Log.d("VoiceCloning", "Saved metadata for ${latestRecording.name}: $currentTextToRecord")

            // Clear current text after saving
            currentTextToRecord = ""
        }
    }

    /**
     * Export all training data as CSV for Piper TTS
     */
    private fun exportTrainingData(): File? {
        val filesDir = filesDir

        // Load all training pairs
        val trainingPairs = mutableListOf<VoiceTrainingData>()

        val wavFiles = filesDir.listFiles { file ->
            file.isFile && file.name.endsWith(".wav") && file.name.startsWith("voice_")
        }

        wavFiles?.forEach { wavFile ->
            VoiceTrainingData.fromAudioFile(wavFile)?.let {
                trainingPairs.add(it)
            }
        }

        if (trainingPairs.isEmpty()) {
            return null
        }

        // Get language from first file (all should be same language in one session)
        val language = trainingPairs.firstOrNull()?.language ?: selectedLanguageName

        // Create CSV file
        val csvFile = File(filesDir, "training_data_${language}_${System.currentTimeMillis()}.csv")

        csvFile.bufferedWriter().use { writer ->
            trainingPairs.forEach { data ->
                writer.write(data.toCsvLine())
                writer.newLine()
            }
        }

        android.util.Log.d("VoiceCloning", "Exported ${trainingPairs.size} training pairs to ${csvFile.name}")

        return csvFile
    }


    private fun initializeViews() {
        textRecInfo = findViewById(R.id.tv_recording_info)
        chipNewText = findViewById(R.id.chip_new_text)
        textToRead = findViewById(R.id.text_to_read)
        micToggleClone = findViewById(R.id.microSwitchVoiceClone)
        recordings = findViewById(R.id.rv_recordings)
    }

    private fun setupRecyclerView() {
        recordingAdapter = RecordingAdapter(recordingsList) { fileToDelete ->
            showDeleteConfirmationDialog(fileToDelete)
        }
        recordings.apply {
            layoutManager = LinearLayoutManager(this@VoiceCloning)
            adapter = recordingAdapter
        }
    }

    private fun loadRecordings() {

        val filesDir = filesDir

        val wavFiles = filesDir.listFiles { file ->
            file.isFile && file.name.endsWith(".wav") && file.name.startsWith("voice_")
        }

        if (wavFiles != null) {

            recordingsList.clear()
            recordingsList.addAll(wavFiles.sortedByDescending { it.lastModified() })
            recordingAdapter.notifyDataSetChanged()

            if (recordingsList.isEmpty()) {
                textToRead.text = "No recordings yet.\n\n" +
                        "1. Click ✍️ to generate text\n" +
                        "2. Click 🎙️ to start recording\n" +
                        "3. Read the text aloud\n" +
                        "4. Click 🎙️ again to stop"
            } else {
                textRecInfo.text = "${recordingsList.size} recording(s) available"
            }
        }
    }

    private fun showDeleteConfirmationDialog(file: File) {
        AlertDialog.Builder(this)
            .setTitle("Delete Recording")
            .setMessage("Delete ${file.name}?")
            .setPositiveButton("Delete") { _, _ ->
                deleteRecording(file)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteRecording(file: File) {
        if (file.delete()) {
            val position = recordingsList.indexOf(file)
            if (position >= 0) {
                recordingsList.removeAt(position)
                recordingAdapter.removeAt(position)
                Toast.makeText(this, "✅ Recording deleted", Toast.LENGTH_SHORT).show()

                // Update text if no recordings left
                if (recordingsList.isEmpty()) {
                    textRecInfo.text = "No recordings yet.\n\n" +
                            "Click ✍️ to generate text and 🎙️ to record"
                }
            }
        } else {
            Toast.makeText(this, "❌ Failed to delete recording", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateFabIcon() {
        if (isRecording) {
            micToggleClone.setImageResource(R.drawable.microphone)
            micToggleClone.backgroundTintList = ContextCompat.getColorStateList(this, R.color.red)
        } else {
            micToggleClone.setImageResource(R.drawable.microphone_off)
            micToggleClone.backgroundTintList = ContextCompat.getColorStateList(this, R.color.blue)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::recordingAdapter.isInitialized) {
            recordingAdapter.release() // Stop any playing audio
        }
        recorderHelper.destroy()
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.voice_cloning_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_export_training_data -> {
                exportTrainingDataToFile()
                true
            }
            R.id.action_show_stats -> {
                showTrainingStats()
                true
            }
            R.id.action_export_google -> {
                exportTrainingDataToGoogle()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun exportTrainingDataToGoogle() {
        val csvFile = exportTrainingData() ?: return
        val audioFiles = recordingsList

        googleHelper.uploadTrainingDataToFolder(
            activity = this,
            trainingDataFile = csvFile,
            audioFiles = audioFiles,
            onProgress = { message ->
                textRecInfo.text = message
            },
            onSuccess = {
                Toast.makeText(this, "✅ Upload successful!", Toast.LENGTH_LONG).show()
            },
            onError = { error ->
                Toast.makeText(this, "❌ $error", Toast.LENGTH_LONG).show()
            }
        )

    }

    private fun exportTrainingDataToFile() {
        val csvFile = exportTrainingData()

        if (csvFile != null) {
            AlertDialog.Builder(this)
                .setTitle("✅ Export Successful")
                .setMessage("Training data exported to:\n${csvFile.name}\n\n" +
                        "Location: ${csvFile.absolutePath}\n\n" +
                        "You can now:\n" +
                        "1. Copy files to your computer\n" +
                        "2. Use for Piper TTS training\n" +
                        "3. Upload to Google Colab")
                .setPositiveButton("OK", null)
                .setNeutralButton("Share") { _, _ ->
                    shareTrainingData(csvFile)
                }
                .show()
        } else {
            Toast.makeText(this, "⚠️ No training data to export", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareTrainingData(csvFile: File) {
        // Create file provider URI for sharing
        val uri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            csvFile
        )

        val shareIntent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            type = "text/csv"
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(android.content.Intent.createChooser(shareIntent, "Share Training Data"))
    }

    private fun showTrainingStats() {
        val filesDir = filesDir

        // Count recordings with metadata
        val wavFiles = filesDir.listFiles { file ->
            file.isFile && file.name.endsWith(".wav") && file.name.startsWith("voice_")
        }

        var withMetadata = 0
        var withoutMetadata = 0
        var totalDuration = 0L
        val languagesFound = mutableSetOf<String>()

        wavFiles?.forEach { wavFile ->
            val metadataFile = File(filesDir, wavFile.nameWithoutExtension + ".txt")
            if (metadataFile.exists()) {
                withMetadata++
            } else {
                withoutMetadata++
            }
            totalDuration += wavFile.length()

            // Extract language from filename
            VoiceTrainingData.fromAudioFile(wavFile)?.let {
                languagesFound.add(it.language)
            }
        }

        val totalSize = totalDuration / (1024.0 * 1024.0) // MB
        val estimatedMinutes = (totalDuration / (22050.0 * 2.0)) / 60.0 // Rough estimate

        val languageInfo = if (languagesFound.isNotEmpty()) {
            "Language(s): ${languagesFound.joinToString(", ")}"
        } else {
            "Language: $selectedLanguageName"
        }

        val message = """
            📊 Training Data Statistics
            
            Total Recordings: ${wavFiles?.size ?: 0}
            ✅ With Text: $withMetadata
            ⚠️ Without Text: $withoutMetadata
            
            📦 Total Size: ${"%.2f".format(totalSize)} MB
            ⏱️ Est. Duration: ${"%.1f".format(estimatedMinutes)} minutes
            
            $languageInfo
            
            ${if (withMetadata >= 20) "✅ Good dataset size!" else "⚠️ Record more for better results (20+ recommended)"}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Training Statistics")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

}
