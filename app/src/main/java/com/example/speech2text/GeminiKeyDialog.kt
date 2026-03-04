package com.example.speech2text

import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import android.app.Dialog
import android.content.Context
import android.widget.TextView

class GeminiKeyDialog(
    private val geminiHelper: GeminiHelper,
    private val onKeySaved: (() -> Unit)? = null
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val dialogView = layoutInflater.inflate(R.layout.dialog_api_key, null)
        val apiKeyInput = dialogView.findViewById<EditText>(R.id.et_api_key)
        val tvApiKeyStatus = dialogView.findViewById<TextView>(R.id.tv_api_key_status)
        val tvModelInfo = dialogView.findViewById<TextView>(R.id.tv_model_info)

        // Pre-fill with existing key if available
        val sharedPrefs = context.getSharedPreferences("GeminiSettings", Context.MODE_PRIVATE)
        val existingKey = sharedPrefs.getString("api_key", "") ?: ""
        apiKeyInput.setText(existingKey)

        // Show status and model info
        if (existingKey.isNotEmpty()) {
            tvApiKeyStatus.text = getString(R.string.api_key_configured)
            tvApiKeyStatus.setTextColor(requireContext().getColor(android.R.color.holo_green_dark))
            tvModelInfo?.text = geminiHelper.getModelInfo()
        } else {
            tvApiKeyStatus.text = getString(R.string.api_key_not_configured)
            tvApiKeyStatus.setTextColor(requireContext().getColor(android.R.color.holo_red_dark))
            tvModelInfo?.text = "Configure API key first"
        }

        return AlertDialog.Builder(context)
            .setTitle(getString(R.string.gemini_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val newKey = apiKeyInput.text.toString().trim()
                if (newKey.isNotEmpty()) {
                    geminiHelper.setApiKey(newKey)
                    Toast.makeText(context, getString(R.string.api_key_saved), Toast.LENGTH_SHORT).show()
                    onKeySaved?.invoke()
                } else {
                    Toast.makeText(context, getString(R.string.api_key_empty), Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Change Model") { _, _ ->
                showModelSelectionDialog()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
    }

    private fun showModelSelectionDialog() {
        val context = requireContext()
        val models = geminiHelper.getAvailableModels()
        val currentModel = geminiHelper.getCurrentModel()
        val currentIndex = models.indexOf(currentModel).takeIf { it >= 0 } ?: 0

        AlertDialog.Builder(context)
            .setTitle("Select Gemini Model")
            .setSingleChoiceItems(models.toTypedArray(), currentIndex) { dialog, which ->
                val selectedModel = models[which]
                geminiHelper.setModel(selectedModel)
                Toast.makeText(context,
                    "Model changed to: $selectedModel",
                    Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                onKeySaved?.invoke()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}

