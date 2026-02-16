# Whisper.cpp Integration for Android

This guide explains how to integrate Whisper.cpp (OpenAI's speech recognition) into your Android app.

## What is Whisper.cpp?

Whisper.cpp is a high-quality, **offline** speech recognition engine by OpenAI. It's more accurate than Vosk but requires more resources.

## Integration Methods

### Option 1: Use Pre-built Library (RECOMMENDED - Easiest)

✅ **Already added to your project!** I've added the dependency:
```kotlin
implementation("com.whispercpp:whisper:1.5.5")
```

### Option 2: Build from Source (Advanced)

If you need the latest version or want to customize:

#### Prerequisites:
1. **Android NDK** (Install via Android Studio SDK Manager)
2. **CMake** (Install via Android Studio SDK Manager)
3. **Git**

#### Steps:

1. **Clone whisper.cpp repository:**
```bash
git clone https://github.com/ggerganov/whisper.cpp.git
cd whisper.cpp
```

2. **Build Android libraries:**
```bash
# For all architectures
./examples/whisper.android/build.sh

# Or specify architectures:
./examples/whisper.android/build.sh arm64-v8a armeabi-v7a
```

3. **Copy libraries to your project:**
```
whisper.cpp/examples/whisper.android/app/src/main/jniLibs/
  ├── arm64-v8a/
  │   └── libwhisper.so
  ├── armeabi-v7a/
  │   └── libwhisper.so
  ├── x86_64/
  │   └── libwhisper.so
  └── x86/
      └── libwhisper.so
```

Copy these to: `app/src/main/jniLibs/`

4. **Create JNI wrapper** (optional if you want custom bindings)

## Download Whisper Models

Whisper models are available in different sizes:

| Model | Size | Speed | Quality |
|-------|------|-------|---------|
| tiny  | 75 MB | Fastest | Basic |
| base  | 142 MB | Fast | Good |
| small | 466 MB | Medium | Better |
| medium | 1.5 GB | Slow | Very Good |
| large | 2.9 GB | Slowest | Best |

### Download Models:

**Option A: Direct Download**
```bash
# Download base model (English)
curl -L https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin -o ggml-base.en.bin

# Download base model (Multilingual)
curl -L https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin -o ggml-base.bin

# For German specifically, use multilingual model
curl -L https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin -o ggml-small.bin
```

**Option B: All models available at:**
https://huggingface.co/ggerganov/whisper.cpp/tree/main

### Place Models in Your App:

1. Create folder: `app/src/main/assets/models/`
2. Copy model file (e.g., `ggml-base.en.bin`) there
3. Or place on device storage: `/sdcard/Download/whisper/`

## Usage in Your App

### 1. Initialize WhisperHelper

```kotlin
// In MainActivity.kt
private lateinit var whisperHelper: WhisperHelper

whisperHelper = WhisperHelper(
    context = this,
    onResult = { text -> trTV.text = text },
    onPartialResult = { text -> tiTV.text = text },
    onStatus = { status -> tiTV.text = status },
    onError = { error ->
        tiTV.text = error
        micToggle.isChecked = false
        methodSelector.isEnabled = true
    }
)

// Initialize with model
val modelPath = "${getExternalFilesDir(null)}/models/ggml-base.en.bin"
whisperHelper.initialize(modelPath)
```

### 2. Copy Model to Device Storage

Add this helper function:

```kotlin
private fun copyModelToStorage() {
    val modelDir = File(getExternalFilesDir(null), "models")
    modelDir.mkdirs()
    
    val modelFile = File(modelDir, "ggml-base.en.bin")
    
    if (!modelFile.exists()) {
        // Copy from assets
        assets.open("models/ggml-base.en.bin").use { input ->
            modelFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}
```

### 3. Add to MainActivity

```kotlin
// Add Whisper to your method selector
when (selectedMethod) {
    "google" -> speechHelper.startListening(languageCode)
    "vosk" -> voskHelper.startListening()
    "whisper" -> whisperHelper.startListening()
}

// Stop
when (currentMethod) {
    "whisper" -> whisperHelper.stopListening()
    // ... other methods
}
```

## Comparison: Whisper vs Vosk vs Google

| Feature | Google | Vosk | Whisper |
|---------|--------|------|---------|
| **Offline** | ❌ No | ✅ Yes | ✅ Yes |
| **Quality** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **Speed** | ⚡⚡⚡ | ⚡⚡⚡ | ⚡⚡ |
| **Size** | 0 MB | 50 MB | 150-3000 MB |
| **Languages** | 100+ | 20+ | 99 |
| **No Beeps** | ❌ | ✅ | ✅ |
| **Battery** | Low | Low | High |
| **Best For** | Online use | Offline, low-end devices | Offline, high quality |

## Recommended Model for Your App

For German + English support:
- **ggml-small.bin** (466 MB) - Good balance of quality and speed
- Or **ggml-base.bin** (142 MB) - Faster, good enough for most use cases

## Performance Tips

1. **Use smaller models on older devices** (base or tiny)
2. **Use CoreML/GPU acceleration** if available (automatic in library)
3. **Transcribe in chunks** (3-5 seconds) for better UX
4. **Show progress indicator** during transcription
5. **Cache models** - don't re-initialize unnecessarily

## Troubleshooting

### Model not loading?
- Check file path is correct
- Ensure model file is complete (check file size)
- Try re-downloading the model

### Out of memory?
- Use a smaller model (tiny or base)
- Reduce chunk size in WhisperHelper
- Close other apps

### Slow transcription?
- Use base or tiny model instead of large
- Reduce audio quality (already at 16kHz)
- Use GPU acceleration (automatic)

## Next Steps

1. Sync Gradle to download dependencies
2. Download a Whisper model
3. Place model in assets or device storage
4. Add Whisper to your method selector
5. Test it!

## Resources

- **Whisper.cpp GitHub**: https://github.com/ggerganov/whisper.cpp
- **Android Example**: https://github.com/ggerganov/whisper.cpp/tree/master/examples/whisper.android
- **Models**: https://huggingface.co/ggerganov/whisper.cpp
- **Documentation**: https://github.com/ggerganov/whisper.cpp/blob/master/README.md

