#include <jni.h>
#include <string>
#include <vector>
#include <fstream>
#include <android/log.h>
#include <thread>
#include <unistd.h>
#include "whisper.h"

#define LOG_TAG "WhisperNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Get optimal thread count for device
static int get_optimal_thread_count() {
    int cpu_count = (int)std::thread::hardware_concurrency();
    if (cpu_count <= 0) {
        cpu_count = (int)sysconf(_SC_NPROCESSORS_ONLN);
    }
    if (cpu_count <= 0) {
        cpu_count = 4; // fallback
    }

    // Use most cores, but leave some headroom
    // For Exynos 1280 (8 cores): use 6-7 threads
    int optimal = std::max(1, std::min(cpu_count - 1, cpu_count));
    LOGD("CPU cores: %d, using %d threads", cpu_count, optimal);
    return optimal;
}

// Global Whisper context - initialized once, reused for all transcriptions
static whisper_context* g_whisper_ctx = nullptr;

// Common transcription function used by both WAV and buffer modes
std::string transcribe_pcm_data(const std::vector<float>& pcmf32, const char* language_code) {
    std::string result = "Transcription failed";

    if (g_whisper_ctx == nullptr) {
        LOGE("Whisper context not initialized");
        return "Error: Whisper not initialized";
    }

    if (pcmf32.empty()) {
        LOGE("PCM data is empty");
        return "Error: No audio data";
    }

    try {
        LOGD("Audio data: %zu samples", pcmf32.size());

        // Set up parameters for transcription - Optimized for multi-core Android devices
        struct whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);

        // CRITICAL: Set language explicitly (no auto-detection)
        wparams.language = language_code;
        wparams.detect_language = false;

        // PERFORMANCE: Use optimal thread count based on CPU cores
        wparams.n_threads = get_optimal_thread_count();
        wparams.offset_ms = 0;
        wparams.duration_ms = 0;

        // Disable optional features that can cause crashes
        wparams.print_progress = false;
        wparams.print_special = false;
        wparams.print_realtime = false;
        wparams.print_timestamps = false;
        wparams.token_timestamps = false;
        wparams.translate = false;
        wparams.no_context = true;
        wparams.single_segment = false;

        // CRITICAL: Set audio_ctx to 512 (not 0) - prevents segfault on some devices
        wparams.audio_ctx = 512;

        // Thresholds
        wparams.entropy_thold = 2.4f;
        wparams.logprob_thold = -1.0f;
        wparams.no_speech_thold = 0.6f;

        // Suppress non-speech tokens
        wparams.suppress_blank = true;
        wparams.suppress_nst = true;

        // Temperature settings (greedy = single temperature)
        wparams.temperature = 0.0f;
        wparams.temperature_inc = 0.0f;
        wparams.max_initial_ts = 1.0f;

        LOGD("Whisper params configured: lang=%s, threads=%d, audio_ctx=%d",
             wparams.language, wparams.n_threads, wparams.audio_ctx);

        // Run transcription
        LOGD("Starting transcription...");
        int ret = whisper_full(g_whisper_ctx, wparams, pcmf32.data(), pcmf32.size());

        if (ret != 0) {
            LOGE("Transcription failed with error code: %d", ret);
            result = "Error: Transcription failed";
        } else {
            LOGD("Transcription completed successfully");

            // Extract transcribed text
            const int n_segments = whisper_full_n_segments(g_whisper_ctx);
            LOGD("Number of segments: %d", n_segments);

            std::string transcription;
            for (int i = 0; i < n_segments; ++i) {
                const char* text = whisper_full_get_segment_text(g_whisper_ctx, i);
                transcription += text;
            }

            if (transcription.empty()) {
                result = "[No speech detected]";
            } else {
                result = transcription;
            }

            LOGD("=== Final transcription: %s ===", result.c_str());
        }
    } catch (const std::exception& e) {
        LOGE("Exception: %s", e.what());
        result = std::string("Error: ") + e.what();
    }

    return result;
}

// Read WAV file and convert to PCM float data
std::vector<float> read_wav(const std::string & fname, int & sample_rate) {
    LOGD("Reading WAV file: %s", fname.c_str());
    std::ifstream file(fname, std::ios::binary);
    if (!file.is_open()) {
        LOGE("Failed to open WAV file: %s", fname.c_str());
        return {};
    }

    // Read WAV header
    char header[44];
    file.read(header, 44);

    if (std::string(header, 4) != "RIFF" || std::string(header + 8, 4) != "WAVE") {
        LOGE("Invalid WAV file format");
        return {};
    }

    // Extract sample rate from header
    sample_rate = *reinterpret_cast<int*>(header + 24);
    LOGD("WAV sample rate: %d Hz", sample_rate);

    // Read audio data
    std::vector<int16_t> pcm16;
    int16_t sample;
    while (file.read(reinterpret_cast<char*>(&sample), sizeof(int16_t))) {
        pcm16.push_back(sample);
    }

    file.close();
    LOGD("Read %zu samples", pcm16.size());

    // Convert int16 to float [-1.0, 1.0]
    std::vector<float> pcmf32(pcm16.size());
    for (size_t i = 0; i < pcm16.size(); i++) {
        pcmf32[i] = static_cast<float>(pcm16[i]) / 32768.0f;
    }

    return pcmf32;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_speech2text_WhisperHelper_initWhisper(
        JNIEnv* env,
        jobject /* this */,
        jstring model_path) {

    const char* model_path_c = env->GetStringUTFChars(model_path, nullptr);
    LOGD("Initializing Whisper with model: %s", model_path_c);

    std::string result = "OK";

    try {
        // Free existing context if any
        if (g_whisper_ctx != nullptr) {
            LOGD("Freeing existing Whisper context");
            whisper_free(g_whisper_ctx);
            g_whisper_ctx = nullptr;
        }

        // Initialize Whisper context
        struct whisper_context_params cparams = whisper_context_default_params();
        cparams.use_gpu = false;
        cparams.flash_attn = false; // CRITICAL: Disable flash attention (crashes on Android)

        LOGD("Loading Whisper model with flash_attn=false...");
        g_whisper_ctx = whisper_init_from_file_with_params(model_path_c, cparams);

        if (g_whisper_ctx == nullptr) {
            LOGE("Failed to initialize Whisper context");
            result = "Error: Could not load Whisper model";
        } else {
            LOGD("Whisper context initialized successfully");
        }
    } catch (const std::exception& e) {
        LOGE("Exception during init: %s", e.what());
        result = std::string("Error: ") + e.what();
    }

    env->ReleaseStringUTFChars(model_path, model_path_c);
    return env->NewStringUTF(result.c_str());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_speech2text_WhisperHelper_transcribeAudio(
        JNIEnv* env,
        jobject /* this */,
        jstring audio_path,
        jstring language_code) {

    const char* audio_path_c = env->GetStringUTFChars(audio_path, nullptr);
    const char* language_code_c = env->GetStringUTFChars(language_code, nullptr);

    LOGD("=== Transcribing audio file: %s with language: %s ===", audio_path_c, language_code_c);

    std::string result = "Transcription failed";

    try {
        // Read WAV file
        int sample_rate = 0;
        std::vector<float> pcmf32 = read_wav(audio_path_c, sample_rate);

        if (pcmf32.empty()) {
            LOGE("Failed to read audio data");
            result = "Error: Could not read audio file";
        } else {
            LOGD("Audio file loaded: %zu samples at %d Hz", pcmf32.size(), sample_rate);
            // Use common transcription function
            result = transcribe_pcm_data(pcmf32, language_code_c);
        }
    } catch (const std::exception& e) {
        LOGE("Exception: %s", e.what());
        result = std::string("Error: ") + e.what();
    }

    env->ReleaseStringUTFChars(audio_path, audio_path_c);
    env->ReleaseStringUTFChars(language_code, language_code_c);
    return env->NewStringUTF(result.c_str());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_speech2text_WhisperHelper_transcribeAudioBuffer(
        JNIEnv* env,
        jobject /* this */,
        jfloatArray audio_buffer,
        jstring language_code) {

    const char* language_code_c = env->GetStringUTFChars(language_code, nullptr);
    LOGD("=== Transcribing audio buffer with language: %s ===", language_code_c);

    std::string result = "Transcription failed";

    try {
        // Get audio data from Java float array
        jsize buffer_length = env->GetArrayLength(audio_buffer);
        jfloat* buffer_data = env->GetFloatArrayElements(audio_buffer, nullptr);

        if (buffer_data == nullptr || buffer_length == 0) {
            LOGE("Failed to get audio buffer data");
            result = "Error: Invalid audio buffer";
        } else {
            LOGD("Audio buffer loaded: %d samples", buffer_length);

            // Convert jfloat* to std::vector<float>
            std::vector<float> pcmf32(buffer_data, buffer_data + buffer_length);

            // Release the buffer early
            env->ReleaseFloatArrayElements(audio_buffer, buffer_data, JNI_ABORT);

            // Use common transcription function
            result = transcribe_pcm_data(pcmf32, language_code_c);
        }
    } catch (const std::exception& e) {
        LOGE("Exception: %s", e.what());
        result = std::string("Error: ") + e.what();
    }

    env->ReleaseStringUTFChars(language_code, language_code_c);
    return env->NewStringUTF(result.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_speech2text_WhisperHelper_freeWhisper(
        JNIEnv* env,
        jobject /* this */) {
    LOGD("Freeing Whisper context");
    if (g_whisper_ctx != nullptr) {
        whisper_free(g_whisper_ctx);
        g_whisper_ctx = nullptr;
    }
}

// Keep the old function for system info (optional)
extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_speech2text_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {

    return env->NewStringUTF(whisper_print_system_info());
}
