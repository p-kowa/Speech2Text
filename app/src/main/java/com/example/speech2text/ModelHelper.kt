package com.example.speech2text

import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.LayoutInflater
import android.widget.TextView
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import android.app.DownloadManager
import android.content.Context
import androidx.core.net.toUri
import java.io.File
import android.net.Uri

class ModelHelper : AppCompatActivity() {

    private lateinit var getModels : Button
    private lateinit var saveModel : Button
    private lateinit var editUrl : EditText
    private lateinit var modelListView : RecyclerView
    private lateinit var modelAdapter: ModelListAdapter
    private lateinit var sharedPrefs : android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_modelhelper)
        window.navigationBarColor = getColor(R.color.blueLight)

        val mainLayout =
            findViewById<LinearLayout>(R.id.activity_modelhelper)

        ViewCompat.setOnApplyWindowInsetsListener(mainLayout) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            v.updatePadding(
                left = insets.left,
                top = insets.top,
                right = insets.right,
                bottom = insets.bottom
            )

            windowInsets
        }
        getModels = findViewById(R.id.btn_get)
        saveModel = findViewById(R.id.btn_save_model)
        editUrl = findViewById(R.id.et_url)
        modelListView = findViewById(R.id.rv_models)
        modelListView.layoutManager = LinearLayoutManager(this)
        sharedPrefs = this.getSharedPreferences("ModelSettings", Context.MODE_PRIVATE)

        getModels.setOnClickListener {
            getModelsFromServer()
        }

        saveModel.setOnClickListener {
            val selectedModel = modelAdapter.getSelectedModel()
            if (selectedModel != null) {


                var downloadPath = downloadModel(selectedModel)

                // Speichere den vollständigen Pfad
                sharedPrefs.edit().putString("selected_model_path", downloadPath).apply()
                Toast.makeText(this, "Model saved: $selectedModel", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getModelsFromServer(){
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

        val apiUrl = "https://huggingface.co/api/models/$repoId"
        fetchJsonFromApi(apiUrl)
    }

    private fun fetchJsonFromApi(apiUrl: String) {
        Toast.makeText(this, "Loading model list...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonResponse = java.net.URL(apiUrl).readText()

                val jsonObject = org.json.JSONObject(jsonResponse)
                val siblings = jsonObject.getJSONArray("siblings")

                val modelFiles = mutableListOf<String>()

                for (i in 0 until siblings.length()) {
                    val fileEntry = siblings.getJSONObject(i)
                    val fileName = fileEntry.getString("rfilename")

                    if (fileName.endsWith(".bin")) {
                        modelFiles.add(fileName)
                    }
                }

                withContext(Dispatchers.Main) {
                    if (modelFiles.isEmpty()) {
                        Toast.makeText(this@ModelHelper, "no models found", Toast.LENGTH_SHORT).show()
                    } else {
                        updateRecyclerView(modelFiles)
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ModelHelper, "Loading error!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateRecyclerView(modelFiles: List<String>) {
        modelAdapter = ModelListAdapter(saveModel) { modelName ->
            // Model wurde ausgewählt
        }
        modelListView.adapter = modelAdapter
        modelAdapter.submitList(modelFiles)
    }

    private fun downloadModel(modelName: String) : String {
        val inputUrl = editUrl.text.toString()

        val baseUrl = inputUrl
            .replace("/tree/", "/resolve/")
            .replace("/blob/", "/resolve/")
            .trimEnd('/')

        val downloadUrl = "$baseUrl/$modelName"

        val request = DownloadManager.Request(downloadUrl.toUri())
            .setTitle("Download model: $modelName")
            .setDescription("Whisper model is downloading...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, modelName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val file = File(getExternalFilesDir(null), modelName)
        request.setDestinationUri(Uri.fromFile(file))

        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)

        Toast.makeText(this, "Download started...", Toast.LENGTH_SHORT).show()

        return file.absolutePath
    }
}

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

    fun getSelectedModel(): String? {
        return if (selectedPosition != -1) models[selectedPosition] else null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_model, parent, false)
        return ModelViewHolder(view)
    }

    override fun getItemCount() = models.size

    override fun onBindViewHolder(holder: ModelViewHolder, position: Int) {
        val modelName = models[position]
        // Wir prüfen wieder: Ist das die gewählte Zeile?
        val isSelected = position == selectedPosition
        holder.bind(modelName, isSelected)
    }
    inner class ModelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tv_model_name)

        fun bind(name: String, isSelected: Boolean) {
            tvName.text = name
            itemView.isSelected = isSelected
            itemView.setOnClickListener {

                val previousSelected = selectedPosition
                selectedPosition = adapterPosition

                notifyItemChanged(previousSelected)
                notifyItemChanged(selectedPosition)

                // Enable den saveModel Button
                saveButton.isEnabled = true

                onModelSelected(models[selectedPosition])
            }
        }
    }
}