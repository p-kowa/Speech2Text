package com.example.speech2text

import java.io.File

/**
 * Data class to store voice training pairs (text + audio)
 * for Piper TTS voice cloning
 */
data class VoiceTrainingData(
    val audioFile: File,
    val text: String,
    val language: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Generate metadata filename for this recording
     * Format: voice_[language]_[timestamp].txt
     */
    fun getMetadataFileName(): String {
        return audioFile.nameWithoutExtension + ".txt"
    }

    /**
     * Save text metadata to companion .txt file
     */
    fun saveMetadata() {
        val metadataFile = File(audioFile.parentFile, getMetadataFileName())
        metadataFile.writeText(text)
    }

    /**
     * Export as CSV line for training
     * Format: audio_filename|text
     */
    fun toCsvLine(): String {
        return "${audioFile.name}|$text"
    }

    companion object {
        /**
         * Load training data from audio file and its metadata
         * Extracts language from filename: voice_[Language]_[timestamp].wav
         */
        fun fromAudioFile(audioFile: File): VoiceTrainingData? {
            val metadataFile = File(audioFile.parentFile, audioFile.nameWithoutExtension + ".txt")

            return if (metadataFile.exists()) {
                val text = metadataFile.readText()

                // Extract language from filename: voice_Polish_1234567890.wav
                val language = extractLanguageFromFilename(audioFile.name)

                VoiceTrainingData(
                    audioFile = audioFile,
                    text = text,
                    language = language,
                    timestamp = audioFile.lastModified()
                )
            } else {
                null
            }
        }

        /**
         * Extract language from filename
         * Format: voice_[Language]_[timestamp].wav
         * Example: voice_Polish_1772649705682.wav -> Polish
         */
        private fun extractLanguageFromFilename(filename: String): String {
            return try {
                // Remove extension and split by underscore
                val parts = filename.substringBeforeLast(".").split("_")

                // Format: voice_[Language]_[timestamp]
                if (parts.size >= 3 && parts[0] == "voice") {
                    // Join all parts between "voice" and timestamp (handles "🇩🇪 Deutsch" etc)
                    parts.subList(1, parts.size - 1).joinToString("_")
                } else {
                    "Unknown"
                }
            } catch (e: Exception) {
                "Unknown"
            }
        }
    }
}

