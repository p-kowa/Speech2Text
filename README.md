# Speech2Text (Android)

A small Android demo app to convert speech to text using multiple backends. The app provides a simple UI to choose a recognition method and a language, start and stop recording, and display recognition results.

Supported recognition methods (UI):
- Google (cloud / Android SpeechRecognizer)
- Local Whisper (placeholder — coming soon)
- Vosk (offline speech recognition using Vosk models)

Notes about this repository
- This project was developed in Android Studio and uses Gradle Kotlin DSL (build.gradle.kts).
- The repository's `.gitignore` already excludes typical Android local files such as `local.properties` and `/build` directories. Inspect `gradle.properties` for secrets before publishing.

Requirements
- Android Studio (recommended) or command-line Gradle
- JDK 11+ (as required by your Android Gradle Plugin)
- An Android device or emulator (API level matching project configuration)

Quick start (Android Studio)
1. Open Android Studio and choose "Open" -> select the repository folder (the project root).
2. Let Android Studio sync and download Gradle dependencies.
3. Run the app on an emulator or device via the Run button.

Quick start (command line / PowerShell on Windows)
```powershell
cd 'C:\Daten\Android\Speech2Text'
# Build debug APK
./gradlew assembleDebug
# Install to a connected device (requires adb and a connected device)
./gradlew installDebug
```

If you are on Windows and the wrapper is `gradlew.bat`, replace `./gradlew` with `\gradlew.bat` or run `.\gradlew.bat assembleDebug` from PowerShell.

Usage
1. Select a recognition method from the dropdown (Google, Local Whisper, Vosk).
2. Choose a language using the radio buttons.
3. Toggle the microphone button to start/stop recognition; results and status messages are displayed in the main text view.

Vosk models
- Vosk uses offline models. The app references model names from `R.array.vosk_models` and loads models via the app's `VoskHelper`.
- Download the appropriate Vosk model for the language you want to support (check the Vosk website or GitHub for model downloads). Follow the integration instructions in `VoskHelper` or place the model files in the location expected by your implementation (for example, an `assets` folder or app internal storage). Exact placement depends on how `VoskHelper` is implemented.

Security & sensitive files
- Ensure you do NOT commit secrets (API keys, keystore passwords, service account keys) to the repository.
- `local.properties` is already in `.gitignore` (should remain untracked). If you accidentally committed sensitive files, remove them from the index before pushing:

```powershell
cd 'C:\Daten\Android\Speech2Text'
# Example: remove a tracked local.properties (if it was committed)
git rm --cached local.properties
# Remove or rotate any secrets stored in gradle.properties if necessary
```

Publishing to GitHub
- If you get `fatal: repository 'https://github.com/USER/REPO.git/' not found`, the remote repository does not exist or the URL is incorrect.
- Create a new repository on GitHub (https://github.com/new) or use the GitHub CLI to create one.

Add remote and push (HTTPS)
```powershell
cd 'C:\Daten\Android\Speech2Text'
git remote add origin https://github.com/<USERNAME>/Speech2Text.git
git branch -M main
git push -u origin main
```

Notes about authentication
- GitHub no longer accepts account passwords for Git operations over HTTPS. Use a Personal Access Token (PAT) as the password or configure SSH keys and push over SSH.
- To create a PAT: GitHub > Settings > Developer settings > Personal access tokens. Grant `repo` scope for private repos or `public_repo` for public-only pushes.

Using GitHub from Android Studio
- Use: VCS > Import into Version Control > Share Project on GitHub. Android Studio can create the remote and push for you after you sign in.

Optional: create repo via GitHub CLI (if you install `gh`)
```powershell
# Install via winget (Windows)
winget install --id GitHub.cli -e --source winget
# Login and create repo from the project folder
gh auth login
cd 'C:\Daten\Android\Speech2Text'
gh repo create <USERNAME>/Speech2Text --public --source=. --remote=origin --push
```

Troubleshooting
- Build fails on missing SDK/NDK: open Android Studio SDK Manager, install the required SDK platform and build-tools.
- Vosk issues: ensure the model is compatible with the Vosk library version used and placed where `VoskHelper` expects it.
- Permission denied for microphone: grant RECORD_AUDIO permission in settings or accept the runtime prompt shown by the app.

License & contributing
- Add a `LICENSE` file (e.g. MIT) if you want this project to be open-source.
- Add a `CONTRIBUTING.md` if you plan to accept contributions. Include build steps and code style guidelines.

Contact / Notes
- Local Whisper is listed in the UI but not implemented in the provided code; the app shows a placeholder message.
- If you want, I can also create a `LICENSE` and a short `CONTRIBUTING.md`, or help you create a GitHub repo and push the project from here.

Enjoy! If you want me to customize the README (add badges, screenshots, or CI instructions), tell me what you prefer.

License

This project is licensed under the MIT License — see the `LICENSE` file for details.
