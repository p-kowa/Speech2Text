package com.example.speech2text

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import android.app.DownloadManager
import android.content.Context
import android.view.LayoutInflater
import androidx.core.net.toUri
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.io.File
import android.net.Uri

class ModelHelper : AppCompatActivity() {

    private lateinit var getModels: Button
    private lateinit var saveModel: Button
    private lateinit var editUrl: EditText
    private lateinit var modelListView: RecyclerView
    private lateinit var modelAdapter: ModelListAdapter
    private lateinit var sharedPrefs: android.content.SharedPreferences

    private lateinit var cgModelType: ChipGroup
    private lateinit var chipWhisper: Chip
    private lateinit var chipVosk: Chip
    private lateinit var layoutWhisper: LinearLayout
    private lateinit var layoutVosk: LinearLayout
    private lateinit var acvVoskLanguage: AutoCompleteTextView
    private lateinit var rvInstalledModels: RecyclerView
    private lateinit var tvInstalledLabel: TextView
    private lateinit var installedAdapter: InstalledModelAdapter

    // Supported Vosk languages: display name -> language code -> model folder prefix
    private val voskLanguages = linkedMapOf(
        "German (Deutsch)" to "de",
        "English" to "en",
        "Polish (Polski)" to "pl",
        "French (Français)" to "fr",
        "Spanish (Español)" to "es",
        "Italian (Italiano)" to "it",
        "Portuguese (Português)" to "pt",
        "Russian (Русский)" to "ru",
        "Chinese (中文)" to "zh",
        "Dutch (Nederlands)" to "nl",
        "Turkish (Türkçe)" to "tr",
        "Ukrainian (Українська)" to "uk",
        "Hindi (हिन्दी)" to "hi",
    )

    // Vosk alphacephei download base URL
    private val voskBaseUrl = "https://alphacephei.com/vosk/models"

    // Known Vosk model names per language (alphacephei.com official models)
    private val voskModelMap = mapOf(
        "de" to listOf("vosk-model-small-de-0.15", "vosk-model-de-0.21"),
        "en" to listOf("vosk-model-small-en-us-0.15", "vosk-model-en-us-0.22", "vosk-model-small-en-in-0.4"),
        "pl" to listOf("vosk-model-small-pl-0.22"),
        "fr" to listOf("vosk-model-small-fr-0.22", "vosk-model-fr-0.22"),
        "es" to listOf("vosk-model-small-es-0.42", "vosk-model-es-0.42"),
        "it" to listOf("vosk-model-small-it-0.22", "vosk-model-it-0.22"),
        "pt" to listOf("vosk-model-small-pt-0.3"),
        "ru" to listOf("vosk-model-small-ru-0.22", "vosk-model-ru-0.42"),
        "zh" to listOf("vosk-model-small-cn-0.22", "vosk-model-cn-0.22"),
        "nl" to listOf("vosk-model-small-nl-0.22"),
        "tr" to listOf("vosk-model-small-tr-0.3"),
        "uk" to listOf("vosk-model-small-uk-v3-nano"),
        "hi" to listOf("vosk-model-small-hi-0.22"),
    )

    private var selectedVoskLanguageCode: String = ""
    private var isVoskMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_modelhelper)
        window.navigationBarColor = getColor(R.color.blueLight)

        val mainLayout = findViewById<LinearLayout>(R.id.activity_modelhelper)
        ViewCompat.setOnApplyWindowInsetsListener(mainLayout) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(left = insets.left, top = insets.top, right = insets.right, bottom = insets.bottom)
            windowInsets
        }

        sharedPrefs = getSharedPreferences("ModelSettings", Context.MODE_PRIVATE)

        getModels = findViewById(R.id.btn_get)
        saveModel = findViewById(R.id.btn_save_model)
        editUrl = findViewById(R.id.et_url)
        modelListView = findViewById(R.id.rv_models)
        modelListView.layoutManager = LinearLayoutManager(this)

        cgModelType = findViewById(R.id.cg_model_type)
        chipWhisper = findViewById(R.id.chip_whisper)
        chipVosk = findViewById(R.id.chip_vosk)
        layoutWhisper = findViewById(R.id.layout_whisper)
        layoutVosk = findViewById(R.id.layout_vosk)
        acvVoskLanguage = findViewById(R.id.acv_vosk_language)
        rvInstalledModels = findViewById(R.id.rv_installed_models)
        tvInstalledLabel = findViewById(R.id.tv_installed_label)

        // Default: Whisper selected
        chipWhisper.isChecked = true

        // Setup installed models RecyclerView
        installedAdapter = InstalledModelAdapter { langCode, modelPath ->
            activateVoskModel(langCode, modelPath)
        }
        rvInstalledModels.layoutManager = LinearLayoutManager(this)
        rvInstalledModels.adapter = installedAdapter

        // Vosk language dropdown
        val langNames = voskLanguages.keys.toList()
        val langAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, langNames)
        acvVoskLanguage.setAdapter(langAdapter)
        acvVoskLanguage.setOnItemClickListener { _, _, position, _ ->
            selectedVoskLanguageCode = voskLanguages.values.toList()[position]
            showInstalledVoskModels(selectedVoskLanguageCode)
            showVoskDownloadModels(selectedVoskLanguageCode)
        }

        // Model type toggle
        cgModelType.setOnCheckedStateChangeListener { _, checkedIds ->
            isVoskMode = checkedIds.contains(R.id.chip_vosk)
            layoutWhisper.visibility = if (isVoskMode) View.GONE else View.VISIBLE
            layoutVosk.visibility = if (isVoskMode) View.VISIBLE else View.GONE
            // Clear the download list when switching modes
            modelAdapter = ModelListAdapter(saveModel) {}
            modelListView.adapter = modelAdapter
            saveModel.isEnabled = false
        }

        getModels.setOnClickListener {
            if (isVoskMode) {
                if (selectedVoskLanguageCode.isEmpty()) {
                    Toast.makeText(this, getString(R.string.vosk_language_hint), Toast.LENGTH_SHORT).show()
                } else {
                    showVoskDownloadModels(selectedVoskLanguageCode)
                }
            } else {
                getModelsFromServer()
            }
        }

        saveModel.setOnClickListener {
            val selectedModel = modelAdapter.getSelectedModel()
            if (selectedModel != null) {
                if (isVoskMode) {
                    val downloadPath = downloadVoskModel(selectedModel, selectedVoskLanguageCode)
                    sharedPrefs.edit().putString("vosk_model_$selectedVoskLanguageCode", downloadPath).apply()
                    val langName = voskLanguages.entries.find { it.value == selectedVoskLanguageCode }?.key ?: selectedVoskLanguageCode
                    Toast.makeText(this, getString(R.string.vosk_model_saved, langName), Toast.LENGTH_SHORT).show()
                } else {
                    val downloadPath = downloadModel(selectedModel)
                    sharedPrefs.edit().putString("selected_model_path", downloadPath).apply()
                    Toast.makeText(this, "Whisper model saved: $selectedModel", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showInstalledVoskModels(langCode: String) {
        // Find all installed vosk models for this language stored in SharedPrefs
        val installedList = mutableListOf<Pair<String, String>>() // langCode to path

        // Check all keys matching vosk_model_*
        sharedPrefs.all.forEach { (key, value) ->
            if (key == "vosk_model_$langCode" && value is String && value.isNotEmpty()) {
                val file = File(value)
                if (file.exists()) {
                    installedList.add(Pair(langCode, value))
                }
            }
        }

        // Also scan external files dir for vosk folders matching this language
        val externalDir = getExternalFilesDir(null)
        externalDir?.listFiles()?.forEach { file ->
            if (file.isDirectory && file.name.contains("-$langCode-", ignoreCase = true)) {
                val alreadyListed = installedList.any { it.second == file.absolutePath }
                if (!alreadyListed) installedList.add(Pair(langCode, file.absolutePath))
            }
            // Also .zip files not yet extracted
            if (file.isFile && file.name.endsWith(".zip") && file.name.contains("-$langCode-", ignoreCase = true)) {
                val alreadyListed = installedList.any { it.second == file.absolutePath }
                if (!alreadyListed) installedList.add(Pair(langCode, file.absolutePath))
            }
        }

        val activeModelPath = sharedPrefs.getString("vosk_model_$langCode", "") ?: ""

        if (installedList.isNotEmpty()) {
            tvInstalledLabel.visibility = View.VISIBLE
            rvInstalledModels.visibility = View.VISIBLE
            installedAdapter.submitList(installedList, activeModelPath)
        } else {
            tvInstalledLabel.visibility = View.GONE
            rvInstalledModels.visibility = View.GONE
        }
    }

    private fun showVoskDownloadModels(langCode: String) {
        val models = voskModelMap[langCode] ?: emptyList()
        if (models.isEmpty()) {
            Toast.makeText(this, "No known models for this language", Toast.LENGTH_SHORT).show()
            return
        }
        updateRecyclerView(models)
        Toast.makeText(this, "${models.size} model(s) available for download", Toast.LENGTH_SHORT).show()
    }

    private fun activateVoskModel(langCode: String, modelPath: String) {
        sharedPrefs.edit().putString("vosk_model_$langCode", modelPath).apply()
        val langName = voskLanguages.entries.find { it.value == langCode }?.key ?: langCode
        Toast.makeText(this, getString(R.string.vosk_model_saved, langName), Toast.LENGTH_SHORT).show()
        showInstalledVoskModels(langCode)
    }

    private fun getModelsFromServer() {
        val url = editUrl.text.toString()
        if (url.isEmpty()) {
            Toast.makeText(this, getString(R.string.please_enter_a_url), Toast.LENGTH_SHORT).show()
            return
        }
        val repoId = url
            .replace("https://huggingface.co/", "")
            .split("/tree/")[0]
            .split("/blob/")[0]
            .trimEnd('/')
        fetchJsonFromApi("https://huggingface.co/api/models/$repoId")
    }

    private fun fetchJsonFromApi(apiUrl: String) {
        Toast.makeText(this, "Loading model list...", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonResponse = java.net.URL(apiUrl).readText()
                val siblings = org.json.JSONObject(jsonResponse).getJSONArray("siblings")
                val modelFiles = mutableListOf<String>()
                for (i in 0 until siblings.length()) {
                    val fileName = siblings.getJSONObject(i).getString("rfilename")
                    if (fileName.endsWith(".bin")) modelFiles.add(fileName)
                }
                withContext(Dispatchers.Main) {
                    if (modelFiles.isEmpty()) Toast.makeText(this@ModelHelper, "No models found", Toast.LENGTH_SHORT).show()
                    else updateRecyclerView(modelFiles)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ModelHelper, "Loading error!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateRecyclerView(modelFiles: List<String>) {
        modelAdapter = ModelListAdapter(saveModel) {}
        modelListView.adapter = modelAdapter
        modelAdapter.submitList(modelFiles)
    }

    private fun downloadVoskModel(modelName: String, langCode: String): String {
        val downloadUrl = "$voskBaseUrl/$modelName.zip"
        val file = File(getExternalFilesDir(null), "$modelName.zip")
        val request = DownloadManager.Request(downloadUrl.toUri())
            .setTitle("Download Vosk model: $modelName")
            .setDescription("Vosk model is downloading...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationUri(Uri.fromFile(file))
        (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        Toast.makeText(this, "Vosk download started: $modelName", Toast.LENGTH_SHORT).show()
        return file.absolutePath
    }

    private fun downloadModel(modelName: String): String {
        val inputUrl = editUrl.text.toString()
        val baseUrl = inputUrl
            .replace("/tree/", "/resolve/")
            .replace("/blob/", "/resolve/")
            .trimEnd('/')
        val file = File(getExternalFilesDir(null), modelName)
        val request = DownloadManager.Request("$baseUrl/$modelName".toUri())
            .setTitle("Download model: $modelName")
            .setDescription("Whisper model is downloading...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationUri(Uri.fromFile(file))
        (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        Toast.makeText(this, "Download started...", Toast.LENGTH_SHORT).show()
        return file.absolutePath
    }
}

// ─── Adapter: Download list ───────────────────────────────────────────────────

class ModelListAdapter(
    private val saveButton: Button,
    private val onModelSelected: (String?) -> Unit
) : RecyclerView.Adapter<ModelListAdapter.ModelViewHolder>() {

    private var models = listOf<String>()
    var selectedPosition = -1

    fun submitList(newList: List<String>) {
        models = newList
        selectedPosition = -1
        notifyDataSetChanged()
    }

    fun getSelectedModel(): String? = if (selectedPosition != -1) models[selectedPosition] else null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModelViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_model, parent, false)
        return ModelViewHolder(view)
    }

    override fun getItemCount() = models.size

    override fun onBindViewHolder(holder: ModelViewHolder, position: Int) {
        holder.bind(models[position], position == selectedPosition)
    }

    inner class ModelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tv_model_name)

        fun bind(name: String, isSelected: Boolean) {
            tvName.text = name
            itemView.isSelected = isSelected
            itemView.setOnClickListener {
                val prev = selectedPosition
                selectedPosition = adapterPosition
                notifyItemChanged(prev)
                notifyItemChanged(selectedPosition)
                saveButton.isEnabled = true
                onModelSelected(models[selectedPosition])
            }
        }
    }
}

// ─── Adapter: Installed Vosk models ──────────────────────────────────────────

class InstalledModelAdapter(
    private val onActivate: (langCode: String, modelPath: String) -> Unit
) : RecyclerView.Adapter<InstalledModelAdapter.InstalledViewHolder>() {

    private var items = listOf<Pair<String, String>>() // langCode, path
    private var activeModelPath = ""

    fun submitList(newItems: List<Pair<String, String>>, activePath: String) {
        items = newItems
        activeModelPath = activePath
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InstalledViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_installed_model, parent, false)
        return InstalledViewHolder(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: InstalledViewHolder, position: Int) {
        val (langCode, path) = items[position]
        holder.bind(langCode, path, path == activeModelPath)
    }

    inner class InstalledViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvPath: TextView = itemView.findViewById(R.id.tv_installed_model_path)
        private val btnActivate: Button = itemView.findViewById(R.id.btn_activate_model)

        fun bind(langCode: String, path: String, isActive: Boolean) {
            tvPath.text = File(path).name
            if (isActive) {
                btnActivate.text = itemView.context.getString(R.string.model_active)
                btnActivate.isEnabled = false
            } else {
                btnActivate.text = itemView.context.getString(R.string.activate_model)
                btnActivate.isEnabled = true
                btnActivate.setOnClickListener { onActivate(langCode, path) }
            }
        }
    }
}
