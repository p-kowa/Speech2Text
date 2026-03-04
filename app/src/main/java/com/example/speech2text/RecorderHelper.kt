package com.example.speech2text

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.content.Context
import kotlinx.coroutines.CoroutineScope

/**
 * RecorderHelper for Voice Cloning
 * Records audio as WAV files (22050 Hz, Mono, 16-bit PCM) for Piper TTS
 */
class RecorderHelper(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onError: (String) -> Unit,
    private val onStatus: (String) -> Unit
) {

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    @Volatile
    var isRecording: Boolean = false
        private set

    // Piper TTS requirements: 22050 Hz, Mono, 16-bit
    private val sampleRate = 22050
    private var outputFile: File? = null

    @Suppress("MissingPermission")
    fun startRecordingToFile(fileName: String) {
        if (isRecording) {
            android.util.Log.w("RecorderHelper", "Already recording")
            return
        }

        outputFile = File(context.filesDir, fileName)

        recordingJob = scope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    onStatus("Starting recording...")
                }

                val bufferSize = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    withContext(Dispatchers.Main) {
                        onError("Failed to initialize AudioRecord")
                    }
                    audioRecord = null
                    return@launch
                }

                audioRecord?.startRecording()
                isRecording = true

                withContext(Dispatchers.Main) {
                    onStatus("🎙️ Recording in progress...")
                }

                android.util.Log.d("RecorderHelper", "Starting recording loop")

                val recordingData = mutableListOf<Short>()
                val buffer = ShortArray(bufferSize)

                // Record while isRecording flag is true
                while (isRecording && audioRecord != null) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1

                    if (read > 0) {
                        recordingData.addAll(buffer.take(read))
                    } else if (read < 0) {
                        android.util.Log.w("RecorderHelper", "AudioRecord read returned $read")
                        break
                    }

                    if (!isRecording) break
                }

                android.util.Log.d("RecorderHelper", "Recording loop ended, recorded ${recordingData.size} samples")

                // Stop AudioRecord
                try {
                    audioRecord?.let { recorder ->
                        if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            recorder.stop()
                        }
                        recorder.release()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("RecorderHelper", "Error stopping AudioRecord: ${e.message}")
                }

                audioRecord = null

                // Save WAV file
                outputFile?.let { file ->
                    withContext(Dispatchers.Main) {
                        onStatus("💾 Saving WAV file...")
                    }

                    saveAsWav(file, recordingData, sampleRate)

                    android.util.Log.d("RecorderHelper", "WAV file saved: ${file.absolutePath}")
                    android.util.Log.d("RecorderHelper", "File size: ${file.length()} bytes")

                    withContext(Dispatchers.Main) {
                        if (recordingData.isEmpty()) {
                            onStatus("⚠️ No audio data recorded")
                        } else {
                            onStatus("✅ Recording saved: ${file.name}")
                        }
                    }
                }

            } catch (e: Exception) {
                android.util.Log.e("RecorderHelper", "Error during recording", e)
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
                isRecording = false

                withContext(Dispatchers.Main) {
                    onError("Recording failed: ${e.message}")
                }
            }
        }
    }

    fun stopRecordingToFile() {
        android.util.Log.d("RecorderHelper", "stopRecordingToFile() called")
        isRecording = false
        // The recording loop will exit and clean up
    }

    /**
     * Save audio data as WAV file with proper header
     */
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

    fun destroy() {
        isRecording = false
        recordingJob?.cancel()
        audioRecord?.release()
        audioRecord = null
    }

}