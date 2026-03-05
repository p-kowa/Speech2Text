# Google Drive Upload – Verification and Usage

## ✅ Upload successful – What was uploaded?

### Uploaded files

After a successful upload you will find the following files in your Google Drive:

1. **CSV file**: `training_data_YYYYMMDD_HHMMSS.csv`
   - Contains the training data in the format: `filename|text`
   - Example:
     ```
     voice_🇩🇪 German_1772638221271.wav|This is the spoken text.
     voice_🇩🇪 German_1772638233456.wav|Another example sentence.
     ```

2. **WAV audio files**: `voice_🇩🇪 German_TIMESTAMP.wav`
   - Format: 16‑bit PCM
   - Sample rate: 22050 Hz
   - Mono (1 channel)
   - Optimized for Piper TTS training

## 📱 How to verify the upload

### Method 1: Google Drive in the browser (desktop)
1. Open https://drive.google.com
2. Sign in with the **same Google account** that you used in the app
3. Search for `training_data` or `voice_`
4. The files should appear directly in the root folder (or in the folder you configured)

### Method 2: Google Drive app (phone)
1. Open the Google Drive app
2. After a successful upload, the app can be opened via the dialog
3. Search for the files or scroll to the newest uploads

### Method 3: Inside the app (Logcat)
1. In Android Studio: **View → Tool Windows → Logcat**
2. Set the filter to `GoogleHelper`
3. After a successful upload you will see lines similar to:
   ```
   ✅ Uploaded: training_data_20260305_143022.csv - Link: https://drive.google.com/...
   ✅ Uploaded: voice_🇩🇪 German_1772638221271.wav - Link: https://drive.google.com/...
   ```

### Method 4: Upload status in the app UI
After a successful upload the app shows something like:
```text
✅ Upload successful!

📁 5 files uploaded
📄 1 CSV file
🎤 4 audio files

Check your Google Drive!
```

A dialog then asks if you want to open Google Drive.

## 🎯 Next steps: Voice cloning training

### Option 1: Piper TTS (recommended for local training)
The exported files can be used directly for **Piper TTS** training.

**Setup:**
1. Download the files from Google Drive
2. Create the following directory structure:
   ```
   training_data/
   ├── metadata.csv        # renamed training_data_*.csv
   ├── wavs/
   │   ├── voice_001.wav
   │   ├── voice_002.wav
   │   └── ...
   ```

3. Follow the Piper TTS training documentation:
   https://github.com/rhasspy/piper/blob/master/TRAINING.md

### Option 2: Coqui TTS
The exported data is also compatible with **Coqui TTS** in LJSpeech-style format.

### Option 3: Google Colab training
**Easiest way** – no local setup required.

1. Your data is already in Google Drive
2. Create a Google Colab notebook
3. Mount Google Drive:
   ```python
   from google.colab import drive
   drive.mount('/content/drive')
   ```
4. Use any TTS training notebook (e.g. Tacotron2, FastSpeech2, VITS) and point it to your `training_data` folder.

## 📊 Recommended amount of training data

For good quality voices:
- **Minimum**: 20–30 minutes of audio (approx. 300–500 samples)
- **Good**: 1–2 hours of audio (approx. 1000–2000 samples)
- **Very good**: 5+ hours of audio (approx. 5000+ samples)

Currently you can see how much you have recorded via the **Statistics / Recordings counter** in the app.

## 🔧 Troubleshooting

### Upload does not work
1. Check your internet connection
2. Ensure your Google account is added as a **test user** in the Google Cloud Console
3. Check Logcat for error messages from `GoogleHelper`

### Files do not appear in Drive
1. Wait 1–2 minutes (synchronization delay)
2. Refresh the Drive view (pull-to-refresh in the app or F5 in browser)
3. Check that you are using the **same Google account** as in the app

### Wrong text in CSV
This was previously a bug with `selectedLanguageName` – now fixed.
The CSV now always contains the **actual spoken text** from `textToRead.text` at the time of recording.

## 📝 CSV format details

**Standard LJSpeech format:**
```text
filename|text
```

**Example:**
```text
voice_🇩🇪 German_1772638221271.wav|This is an example sentence in German.
voice_🇵🇱 Polish_1772638233456.wav|To jest przykładowy tekst po polsku.
```

**Important:**
- Separator: `|` (pipe)
- Encoding: UTF‑8
- No header line
- One sample per line

## 🎤 Tips for better recordings

1. **Quiet environment** – minimize background noise
2. **Consistent loudness** – not too loud, not too quiet
3. **Natural speech** – do not speak like a robot
4. **Varied sentences** – diverse content, not repetitive
5. **Clear articulation** – but still natural and relaxed
6. **Vary emotions** – different intonations, questions, neutral statements, etc.

## 🚀 Status

✅ Google Drive integration is fully working
✅ WAV format is optimized for voice cloning (22050 Hz, mono, 16‑bit PCM)
✅ Metadata is stored correctly (filename + text)
✅ Multi-language support (DE, EN, PL)
✅ Automatic upload dialog
✅ Detailed logs available in Logcat

**The app is ready for real voice cloning training!** 🎉
