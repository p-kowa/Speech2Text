# Speech2Text - Android Speech Recognition App

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg)](https://kotlinlang.org)

A modern Android application that provides multiple speech-to-text recognition options with **continuous listening capability**. The app supports both online (Google) and offline (Vosk) speech recognition with seamless text concatenation.

## ✨ Features

### 🎤 Multiple Recognition Methods
- **Google Speech Recognition** - Cloud-based, high accuracy (requires internet)
- **Vosk** - Offline, local recognition (no internet required)
- **Local Whisper** - Planned for future release

### 🔄 Continuous Recording
- **Unlimited duration** - Automatic restart after each recognition cycle
- **Seamless text concatenation** - All recognized text is appended continuously
- **Smart error recovery** - Auto-restart on timeout or no-match errors
- **No interruption** - Keep speaking naturally without manual restarts

### 🌍 Multi-Language Support
- German (de-DE)
- English (en-US)
- Spanish (es-ES)
- French (fr-FR)
- Italian (it-IT)
- Portuguese (pt-PT)

### 🎨 Modern UI
- Material Design 3
- Edge-to-edge display
- Real-time status updates
- Partial results preview
- Toggle-based recording control

## 📋 Requirements

- **Android Studio** (recommended) or command-line Gradle
- **JDK 11+** (as required by Android Gradle Plugin)
- **Android SDK 26** (Android 8.0) or higher
- **Internet connection** (for Google Speech Recognition)
- **Microphone permission**

## 🚀 Quick Start

### Using Android Studio

1. Open Android Studio and choose **File → Open**
2. Select the repository folder (project root)
3. Let Android Studio sync and download Gradle dependencies
4. Run the app on an emulator or device via the **Run** button

### Using Command Line (PowerShell on Windows)

```powershell
cd 'C:\Daten\Android\Speech2Text'

# Build debug APK
./gradlew assembleDebug

# Install to a connected device (requires adb)
./gradlew installDebug
```

> **Note:** On Windows, if the wrapper is `gradlew.bat`, use `.\gradlew.bat` instead of `./gradlew`

## 📱 Usage

1. **Select Recognition Method** - Choose from the dropdown (Google, Vosk, or Local Whisper)
2. **Choose Language** - Select your preferred language using the radio buttons
3. **Start Recording** - Toggle the microphone button to start
4. **Keep Speaking** - The app will continuously transcribe without stopping
5. **Stop Recording** - Toggle the microphone button again to stop

### Real-time Feedback

- **Partial results** - See what's being transcribed in real-time (while speaking)
- **Status updates** - Visual indicators show recording state:
  - 🎤 Bereit... Sprechen Sie jetzt! (Ready)
  - 🎤 Höre zu... (Listening)
  - ⏳ Verarbeite... (Processing)
  - ⏹ Aufnahme gestoppt (Stopped)

## 🏗️ Architecture

### Project Structure

```
Speech2Text/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/example/speech2text/
│   │       │   ├── MainActivity.kt           # Main UI controller
│   │       │   ├── SpeechHelper.kt           # Google Speech Recognition
│   │       │   ├── VoskHelper.kt             # Offline Vosk recognition
│   │       │   └── PermissionManager.kt      # Permission handling
│   │       ├── res/
│   │       │   ├── layout/
│   │       │   │   └── activity_main.xml     # Main UI layout
│   │       │   └── values/
│   │       │       ├── strings.xml           # Localized strings
│   │       │       └── themes.xml            # App theming
│   │       └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/
│   ├── libs.versions.toml                     # Dependency versions
│   └── wrapper/
└── build.gradle.kts
```

### Key Components

#### `SpeechHelper.kt` - Continuous Google Speech Recognition
The heart of the continuous listening feature:

```kotlin
class SpeechHelper(
    context: Context,
    onStatusChange: (String) -> Unit,
    onResult: (String) -> Unit,
    onError: (String) -> Unit
)
```

**Features:**
- ✅ Automatic restart after each recognition cycle
- ✅ Text concatenation across multiple sessions
- ✅ Smart error handling with auto-recovery
- ✅ Language switching support
- ✅ Partial results for real-time feedback

**How it works:**
1. User starts recording → `shouldContinue = true`
2. SpeechRecognizer starts listening (Google API)
3. When speech is detected → `onResults()` is called
4. Text is appended to `entireText` variable
5. If `shouldContinue == true`, automatically restart after 100ms
6. Process repeats until user manually stops

This overcomes the typical 10-second limitation of Google Speech Recognition!

#### `VoskHelper.kt` - Offline Speech Recognition
```kotlin
class VoskHelper(
    context: Context,
    onResult: (String) -> Unit,
    onStatus: (String) -> Unit,
    onError: (String) -> Unit
)
```

**Features:**
- ✅ No internet required
- ✅ Privacy-focused (all processing on-device)
- ✅ Multiple language models supported
- ✅ Real-time transcription

#### `PermissionManager.kt` - Runtime Permissions
```kotlin
class PermissionManager(
    registry: ActivityResultRegistry,
    lifecycleOwner: LifecycleOwner
)
```

**Features:**
- ✅ Microphone permission handling
- ✅ Modern Activity Result API
- ✅ User-friendly permission requests

#### `MainActivity.kt` - UI Controller
Manages the user interface and coordinates between helpers:
- Method selection (Google/Vosk/Whisper)
- Language selection with dynamic radio buttons
- Recording control
- Result display

## 🔧 Technical Details

### Dependencies

```kotlin
// Core Android
implementation(libs.androidx.core.ktx)
implementation(libs.androidx.appcompat)
implementation(libs.material)
implementation(libs.androidx.activity)
implementation(libs.androidx.constraintlayout)

// Vosk (Offline Speech Recognition)
implementation(libs.vosk)
```

See `gradle/libs.versions.toml` for specific versions.

### Required Permissions

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
```

### Gradle Configuration

This project uses **Gradle Kotlin DSL** (`build.gradle.kts`) for type-safe build scripts.

```kotlin
android {
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        targetSdk = 35
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}
```

## 🎯 Vosk Models

Vosk requires offline models for each language. The app references model names from `R.array.vosk_models`.

### Download Models

1. Visit [Vosk Models](https://alphacephei.com/vosk/models)
2. Download the appropriate model for your language:
   - `vosk-model-small-de-0.15` (German)
   - `vosk-model-small-en-us-0.15` (English)
   - etc.
3. Place model files in your app's internal storage or assets folder
4. Update `VoskHelper` to point to the correct model location

### Model Integration

```kotlin
// Example: Loading a model
val model = Model("/path/to/vosk-model-small-de-0.15")
val recognizer = Recognizer(model, 16000.0f)
```

Check `VoskHelper.kt` for the complete implementation.

## 🔒 Security & Sensitive Files

⚠️ **Important:** Do NOT commit secrets to the repository!

- `local.properties` - Already in `.gitignore` (contains SDK paths)
- API keys, keystore passwords, service account keys - NEVER commit these
- Review `gradle.properties` before publishing

### If you accidentally committed secrets:

```powershell
cd 'C:\Daten\Android\Speech2Text'
git rm --cached local.properties
git rm --cached gradle.properties  # if it contains secrets
# Rotate/invalidate the exposed secrets immediately
git commit -m "Remove sensitive files"
```

## 🚢 Publishing to GitHub

### Create Repository on GitHub

1. Go to [GitHub](https://github.com/new) and create a new repository
2. Name it `Speech2Text`
3. Choose public or private
4. Do NOT initialize with README (we already have one)

### Add Remote and Push (HTTPS)

```powershell
cd 'C:\Daten\Android\Speech2Text'
git remote add origin https://github.com/<YOUR_USERNAME>/Speech2Text.git
git branch -M main
git push -u origin main
```

### Authentication

GitHub no longer accepts passwords for Git operations. Use one of these methods:

**Option 1: Personal Access Token (PAT)**
1. Go to GitHub → Settings → Developer settings → Personal access tokens
2. Generate new token with `repo` scope
3. Use the token as your password when pushing

**Option 2: SSH Keys**
```powershell
# Generate SSH key
ssh-keygen -t ed25519 -C "your_email@example.com"

# Add to GitHub: Settings → SSH and GPG keys → New SSH key

# Use SSH remote instead
git remote set-url origin git@github.com:<YOUR_USERNAME>/Speech2Text.git
```

**Option 3: GitHub CLI**
```powershell
# Install GitHub CLI
winget install --id GitHub.cli

# Login and create repo
gh auth login
cd 'C:\Daten\Android\Speech2Text'
gh repo create <YOUR_USERNAME>/Speech2Text --public --source=. --remote=origin --push
```

### Using Android Studio

1. Go to **VCS → Share Project on GitHub**
2. Sign in to your GitHub account
3. Android Studio will create the repo and push for you

## 🐛 Troubleshooting

### Build Fails - Missing SDK/NDK
**Solution:** Open Android Studio SDK Manager and install required SDK platform and build-tools

### Vosk Recognition Not Working
**Solution:** Ensure model is compatible with Vosk library version and placed correctly

### Permission Denied for Microphone
**Solution:** Grant `RECORD_AUDIO` permission in Android settings or accept runtime prompt

### Google Speech Recognition Stops After 10 Seconds
**Solution:** This is expected behavior - the app automatically restarts it! If it's not restarting, check that `shouldContinue` flag is set correctly in `SpeechHelper.kt`

### Text Not Appending
**Solution:** Verify that `entireText` variable is being updated in `onResults()` method

## 📝 Known Issues

- Google Speech Recognition requires internet connection
- Some devices may have vendor-specific speech recognition limitations
- Background recording may be restricted on Android 12+ (Doze mode)
- First recognition cycle may have slight delay while Google API initializes

## 🚀 Future Enhancements

- [ ] Local Whisper integration
- [ ] Export transcribed text to file (.txt, .pdf)
- [ ] Voice activity detection visualization
- [ ] Custom vocabulary support
- [ ] Punctuation restoration
- [ ] Speaker diarization (multi-speaker detection)
- [ ] Dark mode support
- [ ] Text editing capabilities
- [ ] History of previous transcriptions

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the project
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 👤 Author

Created with ❤️ using Kotlin and Android

## 🙏 Acknowledgments

- [Vosk](https://alphacephei.com/vosk/) - Offline speech recognition
- [Google Speech Recognition API](https://developer.android.com/reference/android/speech/SpeechRecognizer) - Cloud-based recognition
- [Material Design 3](https://m3.material.io/) - UI components and guidelines
- Android Open Source Project

## 📞 Support

If you find this project useful, please give it a ⭐️!

For bugs, feature requests, or questions:
- Open an issue on [GitHub Issues](https://github.com/<YOUR_USERNAME>/Speech2Text/issues)
- Check existing issues before creating a new one

---

**Made with ❤️ using Kotlin and Android**
- Add a `LICENSE` file (e.g. MIT) if you want this project to be open-source.
- Add a `CONTRIBUTING.md` if you plan to accept contributions. Include build steps and code style guidelines.

**Made with ❤️ using Kotlin and Android**
