package com.example.speech2text

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiHelper(private val context: Context) {

    private var apiKey: String = ""
    private var model: GenerativeModel? = null
    private var currentModelName: String = DEFAULT_MODEL_NAME

    companion object {
        private const val TAG = "GeminiHelper"
        private const val DEFAULT_MODEL_NAME = "gemini-2.5-flash"

        // Fallback configuration if model info cannot be fetched
        private const val FALLBACK_TEMPERATURE = 1.0f
        private const val FALLBACK_TOP_K = 64
        private const val FALLBACK_TOP_P = 0.95f
        private const val FALLBACK_MAX_OUTPUT_TOKENS = 8192
    }

    init {
        loadApiKey()
    }

    private fun loadApiKey() {
        val sharedPrefs = context.getSharedPreferences("GeminiSettings", Context.MODE_PRIVATE)
        apiKey = sharedPrefs.getString("api_key", "") ?: ""
        currentModelName = sharedPrefs.getString("model_name", DEFAULT_MODEL_NAME) ?: DEFAULT_MODEL_NAME

        if (apiKey.isNotEmpty()) {
            initializeModel()
        }
    }

    fun setApiKey(key: String) {
        apiKey = key
        val sharedPrefs = context.getSharedPreferences("GeminiSettings", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("api_key", key).apply()
        initializeModel()
    }

    fun setModel(modelName: String) {
        currentModelName = modelName
        val sharedPrefs = context.getSharedPreferences("GeminiSettings", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("model_name", modelName).apply()
        initializeModel()
    }

    fun hasApiKey(): Boolean = apiKey.isNotEmpty()

    fun getCurrentModel(): String = currentModelName

    private fun initializeModel() {
        try {
            // Try to create model with dynamic configuration
            model = GenerativeModel(
                modelName = currentModelName,
                apiKey = apiKey,
                generationConfig = generationConfig {
                    // Use fallback values - these will be the defaults
                    // In a real implementation, you could fetch model info from the API
                    // and adjust these dynamically
                    temperature = FALLBACK_TEMPERATURE
                    topK = FALLBACK_TOP_K
                    topP = FALLBACK_TOP_P
                    maxOutputTokens = FALLBACK_MAX_OUTPUT_TOKENS
                }
            )

            Log.d(TAG, "Model initialized: $currentModelName with config: " +
                    "temperature=$FALLBACK_TEMPERATURE, topK=$FALLBACK_TOP_K, " +
                    "topP=$FALLBACK_TOP_P, maxOutputTokens=$FALLBACK_MAX_OUTPUT_TOKENS")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing model: ${e.message}")
        }
    }

    /**
     * Improve transcribed text (grammar, punctuation, formatting)
     */
    suspend fun improveText(text: String): Result<String> = withContext(Dispatchers.IO) {
        if (!hasApiKey()) {
            return@withContext Result.failure(Exception("API key not set"))
        }

        try {
            val prompt = """
                Improve the following transcribed text by:
                - Correcting grammar and spelling
                - Adding proper punctuation
                - Formatting it nicely
                - Keeping the original meaning
                
                Text: $text
                
                Return only the improved text without any additional comments.
            """.trimIndent()

            val response = model?.generateContent(prompt)
            val resultText = response?.text ?: throw Exception("No response from Gemini")

            Result.success(resultText.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Error improving text: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Summarize the transcribed text
     */
    suspend fun summarizeText(text: String): Result<String> = withContext(Dispatchers.IO) {
        if (!hasApiKey()) {
            return@withContext Result.failure(Exception("API key not set"))
        }

        try {
            val prompt = """
                Summarize the following text in a concise way, keeping the main points:
                
                $text
                
                Return only the summary without additional comments.
            """.trimIndent()

            val response = model?.generateContent(prompt)
            val resultText = response?.text ?: throw Exception("No response from Gemini")

            Result.success(resultText.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Error summarizing text: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Translate text to specified language
     */
    suspend fun translateText(text: String, targetLanguage: String): Result<String> = withContext(Dispatchers.IO) {
        if (!hasApiKey()) {
            return@withContext Result.failure(Exception("API key not set"))
        }

        try {
            val prompt = """
                Translate the following text to $targetLanguage:
                
                $text
                
                Return only the translated text without additional comments.
            """.trimIndent()

            val response = model?.generateContent(prompt)
            val resultText = response?.text ?: throw Exception("No response from Gemini")

            Result.success(resultText.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Error translating text: ${e.message}")
            Result.failure(e)
        }
    }


    /**
     * Answer questions about the provided text (for continuous chat)
     */
    suspend fun answerQuestion(text: String): Result<String> = withContext(Dispatchers.IO) {

        if (!hasApiKey()) {
            return@withContext Result.failure(Exception("API key not set"))
        }

        try {
            val prompt = """
                You are a helpful assistant. The user has provided the following text or conversation.
                If it contains a question at the end, answer it clearly and concisely.
                If there's no clear question, provide helpful insights or ask if they need anything clarified.
                
                Text/Conversation:
                $text
                
                Provide your response:
            """.trimIndent()

            val response = model?.generateContent(prompt)
            val resultText = response?.text ?: throw Exception("No response from Gemini")

            Result.success(resultText.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Error answering question: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Generate short training text for voice cloning (2-3 sentences with phonetic variety)
     * Each call generates different text to build a diverse training dataset
     */
    suspend fun generateText(language: String, previousTexts: List<String> = emptyList()): Result<String> = withContext(Dispatchers.IO) {
        if (!hasApiKey()) {
            return@withContext Result.failure(Exception("API key not set"))
        }

        try {
            val previousContext = if (previousTexts.isNotEmpty()) {
                "\nPreviously generated texts (generate something DIFFERENT):\n${previousTexts.takeLast(5).joinToString("\n")}\n"
            } else {
                ""
            }

            val prompt = """
            Generate a SHORT phonetically rich text in $language for voice cloning training.
            
            Requirements:
            - Length: EXACTLY 2-3 sentences (30-50 words)
            - Each sentence should be SHORT and natural
            - Cover diverse phonemes: mix vowels, consonants, different sounds
            - Vary sentence types: statement, question, or exclamation
            - Natural prosody with clear punctuation
            - Use everyday vocabulary (simple, conversational)
            - Make it DIFFERENT from previous texts - vary topic, style, emotion
            
            $previousContext
            
            Topics to vary: daily life, emotions, questions, observations, actions, descriptions.
            Styles to vary: casual, formal, excited, calm, curious, assertive.
            
            Return ONLY the text, no explanations or comments.
        """.trimIndent()

            val response = model?.generateContent(prompt)
            val resultText = response?.text ?: throw Exception("No response from Gemini")

            Result.success(resultText.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Error generating text: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get available Gemini models
     * Note: This requires a separate API call to list models
     * For now, returns common available models
     */
    fun getAvailableModels(): List<String> {
        return listOf(
            "gemini-2.5-flash",
            "gemini-1.5-flash",
            "gemini-1.5-pro",
            "gemini-pro"
        )
    }

    /**
     * Get model information as a formatted string
     */
    fun getModelInfo(): String {
        return "Current Model: $currentModelName\n" +
                "Temperature: $FALLBACK_TEMPERATURE\n" +
                "Top-K: $FALLBACK_TOP_K\n" +
                "Top-P: $FALLBACK_TOP_P\n" +
                "Max Output Tokens: $FALLBACK_MAX_OUTPUT_TOKENS"
    }

    /**
     * Get a short summary of model configuration for status display
     */
    fun getModelSummary(): String {
        return "🤖 $currentModelName | Max: ${FALLBACK_MAX_OUTPUT_TOKENS} tokens"
    }

    /**
     * Estimate token count for text (rough approximation: ~4 chars = 1 token)
     */
    fun estimateTokenCount(text: String): Int {
        return (text.length / 4).coerceAtLeast(1)
    }

    /**
     * Check if text length is within token limits
     */
    fun isWithinTokenLimit(text: String, maxTokens: Int = FALLBACK_MAX_OUTPUT_TOKENS): Boolean {
        val estimatedTokens = estimateTokenCount(text)
        return estimatedTokens <= maxTokens
    }

    /**
     * Get token usage info for a text
     */
    fun getTokenInfo(text: String): String {
        val estimated = estimateTokenCount(text)
        val percentage = (estimated.toFloat() / FALLBACK_MAX_OUTPUT_TOKENS * 100).toInt()
        return "📊 ~$estimated tokens used ($percentage% of max)"
    }

}
