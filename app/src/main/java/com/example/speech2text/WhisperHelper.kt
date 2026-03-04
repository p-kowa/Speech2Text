package com.example.speech2text

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import java.io.File

/**
 * Helper class for Whisper.cpp Speech-to-Text Recognition
 * Offline, high-quality speech recognition by OpenAI
 */
class WhisperHelper(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onPartialResult: (String) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private var recordingJob: Job? = null
    private var isRecording = false
    var entireText: String = ""
    private var isWhisperInitialized = false
    private var currentLanguage: String ="de"// Hardcoded to German for testing

    // Recording chunk duration (in milliseconds)
    // Longer chunks = better context and quality, but slightly longer wait time
    // Recommended: 5000-10000ms (5-10 seconds)
    var chunkDurationMs: Long = 7000L // 7 seconds - good balance

    // Voice Activity Detection (VAD) settings
    var enableVAD: Boolean = true // Automatically stop when silence is detected
    var silenceThresholdDb: Double = -40.0 // dB threshold for silence (lower = more sensitive)
    var silenceDurationMs: Long = 2000L // Stop after 2 seconds of silence
    var minRecordingDurationMs: Long = 1000L // Minimum recording time before VAD can trigger
    // Toggle between WAV file mode (false) and buffer mode (true)
    var useBufferMode: Boolean = true // Buffer mode is more efficient


    private lateinit var modelFile : File

    private external fun initWhisper(modelPath: String): String
    private external fun transcribeAudio(audioPath: String, languageCode: String): String
    private external fun transcribeAudioBuffer(audioBuffer: FloatArray, languageCode: String): String
    private external fun freeWhisper()

    companion object {
        init {
            System.loadLibrary("native-lib")
        }
    }


    fun start(languageCode: String = "de") {

        if (isRecording) return

        // Prüfe ob Modell-Datei gesetzt wurde
        if (!::modelFile.isInitialized) {
            android.util.Log.e("WhisperHelper", "No model file set! Call setModelFile() first.")
            onError("❌ No model file selected. Please download a model first.")
            return
        }

        if (!modelFile.exists()) {
            android.util.Log.e("WhisperHelper", "Model file does not exist: ${modelFile.absolutePath}")
            onError("❌ Model file not found: ${modelFile.name}")
            return
        }

        currentLanguage = languageCode
        android.util.Log.d("WhisperHelper", "Starting Whisper with language: $currentLanguage, buffer mode: $useBufferMode")
        android.util.Log.d("WhisperHelper", "Using model: ${modelFile.absolutePath}")

        CoroutineScope(Dispatchers.IO).launch {
            try {

                if (!isWhisperInitialized) {
                    withContext(Dispatchers.Main) {
                        onStatus("⏳ Loading Whisper model...")
                    }

                    val initResult = initWhisper(modelFile.absolutePath)
                    if (initResult != "OK") {
                        withContext(Dispatchers.Main) {
                            onError(initResult)
                        }
                        return@launch
                    }
                    isWhisperInitialized = true
                    android.util.Log.d("WhisperHelper", "Whisper initialized successfully")
                }

                withContext(Dispatchers.Main) {
                    onStatus("✅ Whisper ready")
                }

                // Choose recording method based on mode
                if (useBufferMode) {
                    android.util.Log.d("WhisperHelper", "Using buffer mode (direct PCM)")
                    startRecordingWithBuffer()
                } else {
                    android.util.Log.d("WhisperHelper", "Using WAV file mode")
                    startRecording()
                }
            } catch (e: Exception) {
                android.util.Log.e("WhisperHelper", "Failed to start Whisper", e)
                withContext(Dispatchers.Main) {
                    onError("Error: ${e.message}")
                }
            }
        }
    }

    fun setModelFile(path: String) {
        if (path.isNotEmpty() && File(path).exists()) {
            modelFile = File(path)
            isWhisperInitialized = false // Force re-initialization with new model
            android.util.Log.d("WhisperHelper", "Custom model path set: $path")
        } else {
            android.util.Log.w("WhisperHelper", "Invalid model path: $path")
        }
    }


    private fun startRecording() {
        isRecording = true
        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) {
                    onStatus("🎤 Recording...")
                }
                val audioFile = recordAudio()  // Record 3 seconds

                withContext(Dispatchers.Main) {
                    onStatus("🔄 Transcribing...")
                }

                // Log file paths for debugging
                android.util.Log.d("WhisperHelper", "Audio path: ${audioFile.absolutePath}")
                android.util.Log.d("WhisperHelper", "Audio exists: ${audioFile.exists()}")
                android.util.Log.d("WhisperHelper", "Audio size: ${audioFile.length()} bytes")
                android.util.Log.d("WhisperHelper", "Using language: $currentLanguage")

                // Call native Whisper function with timeout
                val result = withTimeout(60000L) { // 60 second timeout
                    transcribeAudio(audioFile.absolutePath, currentLanguage)
                }

                android.util.Log.d("WhisperHelper", "Transcription result: $result")

                entireText += "$result "

                withContext(Dispatchers.Main) {
                    onPartialResult(result)
                    onResult(entireText)
                    onStatus("✅ Done")
                }

                // Clean up audio file
                audioFile.delete()

            } catch (e: TimeoutCancellationException) {
                android.util.Log.e("WhisperHelper", "Transcription timeout", e)
                withContext(Dispatchers.Main) {
                    onError("Error: Transcription timed out (60s)")
                }
            } catch (e: UnsatisfiedLinkError) {
                android.util.Log.e("WhisperHelper", "Native library not loaded", e)
                withContext(Dispatchers.Main) {
                    onError("Error: Native library not loaded - ${e.message}")
                }
            } catch (e: Exception) {
                android.util.Log.e("WhisperHelper", "Transcription error", e)
                withContext(Dispatchers.Main) {
                    onError("Error: ${e.message}")
                }
            } finally {
                isRecording = false
            }
        }
    }

    /**
     * Alternative recording method using buffer-based approach (more efficient)
     * Bypasses WAV file creation and directly passes PCM float data to Whisper
     * Continuously records in 3-second chunks until stop() is called
     */
    private fun startRecordingWithBuffer() {
        isRecording = true
        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                // Continuous recording loop
                while (isRecording) {
                    withContext(Dispatchers.Main) {
                        onStatus("🎤 Recording...")
                    }

                    val audioBuffer = recordAudioBuffer()  // Record 3 seconds as float buffer

                    // Only transcribe if still recording (user might have stopped during recording)
                    if (!isRecording) break

                    withContext(Dispatchers.Main) {
                        onStatus("🔄 Transcribing...")
                    }

                    android.util.Log.d("WhisperHelper", "Audio buffer size: ${audioBuffer.size} samples")
                    android.util.Log.d("WhisperHelper", "Using language: $currentLanguage")

                    // Call native Whisper function with buffer directly
                    val result = withTimeout(60000L) { // 60 second timeout
                        transcribeAudioBuffer(audioBuffer, currentLanguage)
                    }

                    android.util.Log.d("WhisperHelper", "Transcription result: $result")

                    // Only add result if it's not empty
                    if (result.isNotBlank()) {
                        entireText += "$result "

                        withContext(Dispatchers.Main) {
                            onPartialResult(result)
                            onResult(entireText)
                        }
                    }
                }

                // Final status when recording stops
                withContext(Dispatchers.Main) {
                    onStatus("✅ Recording stopped")
                }

            } catch (e: TimeoutCancellationException) {
                android.util.Log.e("WhisperHelper", "Transcription timeout", e)
                withContext(Dispatchers.Main) {
                    onError("Error: Transcription timed out (60s)")
                }
            } catch (e: UnsatisfiedLinkError) {
                android.util.Log.e("WhisperHelper", "Native library not loaded", e)
                withContext(Dispatchers.Main) {
                    onError("Error: Native library not loaded - ${e.message}")
                }
            } catch (e: Exception) {
                android.util.Log.e("WhisperHelper", "Transcription error", e)
                withContext(Dispatchers.Main) {
                    onError("Error: ${e.message}")
                }
            } finally {
                isRecording = false
            }
        }
    }

    @Suppress("MissingPermission")
    private suspend fun recordAudio(): File = withContext(Dispatchers.IO) {
        val audioFile = File(context.cacheDir, "recording_${System.currentTimeMillis()}.wav")

        // Audio-Recording (16kHz mono for Whisper)
        val sampleRate = 16000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        // Permission is already checked in MainActivity before calling start()
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        audioRecord.startRecording()

        // Record for configured duration (default 7 seconds)
        val recordingData = mutableListOf<Short>()
        val buffer = ShortArray(bufferSize)
        val startTime = System.currentTimeMillis()

        // VAD: Track silence duration
        var lastVoiceDetectedTime = System.currentTimeMillis()
        var hasDetectedVoiceOnce = false

        while (System.currentTimeMillis() - startTime < chunkDurationMs && isRecording) {
            val read = audioRecord.read(buffer, 0, buffer.size)
            if (read > 0) {
                recordingData.addAll(buffer.take(read))

                // Voice Activity Detection (VAD)
                if (enableVAD) {
                    val currentTime = System.currentTimeMillis()
                    val recordingDuration = currentTime - startTime

                    // Only apply VAD after minimum recording duration
                    if (recordingDuration > minRecordingDurationMs) {
                        val amplitude = calculateAmplitude(buffer.take(read))
                        val amplitudeDb = amplitudeToDb(amplitude)

                        if (amplitudeDb > silenceThresholdDb) {
                            lastVoiceDetectedTime = currentTime
                            hasDetectedVoiceOnce = true
                        } else {
                            val silenceDuration = currentTime - lastVoiceDetectedTime
                            if (hasDetectedVoiceOnce && silenceDuration > silenceDurationMs) {
                                android.util.Log.d("WhisperHelper", "Silence detected - stopping WAV recording")
                                break
                            }
                        }
                    }
                }
            }
        }

        audioRecord.stop()
        audioRecord.release()

        // Save as WAV
        saveAsWav(audioFile, recordingData, sampleRate)

        return@withContext audioFile
    }

    /**
     * Alternative recording method that returns PCM float buffer directly
     * without creating WAV file (more efficient)
     */
    @Suppress("MissingPermission")
    private suspend fun recordAudioBuffer(): FloatArray = withContext(Dispatchers.IO) {
        // Audio-Recording (16kHz mono for Whisper)
        val sampleRate = 16000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        audioRecord.startRecording()

        // Record for configured duration (default 7 seconds)
        val recordingData = mutableListOf<Short>()
        val buffer = ShortArray(bufferSize)
        val startTime = System.currentTimeMillis()

        // VAD: Track silence duration
        var lastVoiceDetectedTime = System.currentTimeMillis()
        var hasDetectedVoiceOnce = false

        while (System.currentTimeMillis() - startTime < chunkDurationMs && isRecording) {
            val read = audioRecord.read(buffer, 0, buffer.size)
            if (read > 0) {
                recordingData.addAll(buffer.take(read))

                // Voice Activity Detection (VAD)
                if (enableVAD) {
                    val currentTime = System.currentTimeMillis()
                    val recordingDuration = currentTime - startTime

                    // Only apply VAD after minimum recording duration
                    if (recordingDuration > minRecordingDurationMs) {
                        val amplitude = calculateAmplitude(buffer.take(read))
                        val amplitudeDb = amplitudeToDb(amplitude)

                        if (amplitudeDb > silenceThresholdDb) {
                            // Voice detected!
                            lastVoiceDetectedTime = currentTime
                            hasDetectedVoiceOnce = true
                            android.util.Log.d("WhisperHelper", "Voice: ${amplitudeDb.toInt()} dB")
                        } else {
                            // Silence
                            val silenceDuration = currentTime - lastVoiceDetectedTime

                            // Only stop if we detected voice before and now it's been silent for a while
                            if (hasDetectedVoiceOnce && silenceDuration > silenceDurationMs) {
                                android.util.Log.d("WhisperHelper", "Silence for ${silenceDuration}ms - stopping chunk")
                                break
                            }
                        }
                    }
                }
            }
        }

        audioRecord.stop()
        audioRecord.release()

        // Convert Short (PCM16) to Float [-1.0, 1.0] for Whisper
        return@withContext FloatArray(recordingData.size) { i ->
            recordingData[i].toFloat() / 32768.0f
        }
    }

    /**
     * Calculate RMS (Root Mean Square) amplitude from audio samples
     * Returns value between 0.0 and 1.0
     */
    private fun calculateAmplitude(samples: List<Short>): Double {
        if (samples.isEmpty()) return 0.0

        var sum = 0.0
        samples.forEach { sample ->
            val normalized = sample.toDouble() / 32768.0
            sum += normalized * normalized
        }

        return kotlin.math.sqrt(sum / samples.size)
    }

    /**
     * Convert amplitude to decibels (dB)
     * Reference: 0 dB = maximum amplitude (1.0)
     * Typical values: -60 dB (very quiet) to 0 dB (loud)
     */
    private fun amplitudeToDb(amplitude: Double): Double {
        if (amplitude <= 0.0) return -100.0
        return 20 * kotlin.math.log10(amplitude)
    }

    private fun saveAsWav(file: File, data: List<Short>, sampleRate: Int) {
        file.outputStream().use { output ->
            // Write WAV Header
            val dataSize = data.size * 2
            output.write("RIFF".toByteArray())
            output.write(intToBytes(dataSize + 36))
            output.write("WAVE".toByteArray())
            output.write("fmt ".toByteArray())
            output.write(intToBytes(16))  // Subchunk1Size
            output.write(shortToBytes(1)) // AudioFormat (PCM)
            output.write(shortToBytes(1)) // NumChannels (Mono)
            output.write(intToBytes(sampleRate))
            output.write(intToBytes(sampleRate * 2)) // ByteRate
            output.write(shortToBytes(2)) // BlockAlign
            output.write(shortToBytes(16)) // BitsPerSample
            output.write("data".toByteArray())
            output.write(intToBytes(dataSize))

            // Write audio data
            data.forEach { sample ->
                output.write(sample.toInt() and 0xFF)
                output.write((sample.toInt() shr 8) and 0xFF)
            }
        }
    }

    private fun intToBytes(value: Int) = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte()
    )

    private fun shortToBytes(value: Int) = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte()
    )

    fun stop() {
        android.util.Log.d("WhisperHelper", "stop() called - stopping recording")
        isRecording = false
        recordingJob?.cancel()
        onStatus("🎤 Whisper stopped")
    }

    fun destroy() {
        stop()
        if (isWhisperInitialized) {
            freeWhisper()
            isWhisperInitialized = false
        }
    }
}

