# Voice Cloning Training Data Guide

## 📋 Overview
This app helps you create a training dataset for Piper TTS voice cloning. Each recording is paired with its transcription text for optimal training results.

## 🎯 How It Works

### 1. Generate Text (✍️ Button)
- AI generates short, phonetically rich sentences (2-3 sentences)
- Each text is **different** to create variety in your dataset
- Texts are optimized for voice cloning (diverse phonemes, natural prosody)

### 2. Record Audio (🎙️ Button)
- Read the generated text aloud
- Recording format: **WAV, 22050 Hz, Mono, 16-bit** (perfect for Piper TTS)
- Press 🎙️ again to stop
- Text is automatically saved with the recording

### 3. Repeat Process
- Generate new text → Record → Repeat
- Aim for **20+ recordings** for good results
- The AI ensures each text is different from previous ones

## 📊 Training Statistics
Tap the **📊 Statistics** icon to see:
- Total number of recordings
- Recordings with/without text metadata
- Total dataset size and estimated duration
- Quality recommendations

## 💾 Export Training Data

### Export Options
Tap the **💾 Export** icon to create a CSV file containing:
```
audio_filename|transcription_text
voice_German_1234567890.wav|This is the first sentence. How are you today?
voice_German_1234567891.wav|The weather is beautiful. Let's go outside!
```

### File Structure
After export, you'll have:
```
/data/data/com.example.speech2text/files/
├── voice_German_1234567890.wav
├── voice_German_1234567890.txt
├── voice_German_1234567891.wav
├── voice_German_1234567891.txt
├── training_data_German_1234567892.csv
```

## 🚀 Using Your Dataset

### Option 1: Google Colab (Recommended)
1. Export training data
2. Share/Upload all files to Google Drive
3. Use Piper TTS training notebook on Colab
4. Train your custom voice model

### Option 2: Local Training
1. Export training data
2. Copy files from device:
   ```bash
   adb pull /data/data/com.example.speech2text/files/ ./training_data/
   ```
3. Follow Piper TTS training documentation

### Option 3: Transfer via USB
1. Connect device to computer
2. Enable USB file transfer
3. Navigate to internal storage → Android → data → com.example.speech2text → files
4. Copy all .wav and .txt files plus the .csv file

## 📐 Training Recommendations

### Dataset Size
- **Minimum**: 10 recordings (~2-3 minutes)
- **Good**: 20-50 recordings (~5-15 minutes)
- **Excellent**: 100+ recordings (~30+ minutes)

### Quality Tips
1. **Environment**: Record in a quiet room
2. **Microphone**: Use consistent distance
3. **Voice**: Natural reading, clear pronunciation
4. **Variety**: Use the AI-generated texts (already optimized)
5. **Consistency**: Same recording setup for all samples

### What Makes Good Training Data
✅ Clear audio (no background noise)  
✅ Natural prosody (not robotic)  
✅ Consistent volume  
✅ Diverse phonetic content (handled by AI)  
✅ Proper text-audio alignment (automatic)  

## 🔧 Technical Details

### Audio Format
- **Sample Rate**: 22050 Hz
- **Channels**: Mono (1 channel)
- **Bit Depth**: 16-bit PCM
- **Format**: WAV (uncompressed)

### Text Format
- Encoding: UTF-8
- Line breaks: Unix (LF)
- CSV delimiter: `|` (pipe character)

### Metadata Structure
Each audio file has a companion `.txt` file:
```
voice_German_1234567890.wav  →  voice_German_1234567890.txt
```

The CSV file maps all recordings:
```csv
audio_filename|text
voice_German_1234567890.wav|First sentence here.
voice_German_1234567891.wav|Second sentence here.
```

## 🌐 Google Colab Training

### Step-by-Step
1. **Prepare Data**
   - Export training data from app
   - Upload to Google Drive

2. **Setup Colab Notebook**
   ```python
   # Mount Google Drive
   from google.colab import drive
   drive.mount('/content/drive')
   
   # Install Piper TTS
   !pip install piper-tts
   ```

3. **Load Your Dataset**
   ```python
   import pandas as pd
   
   # Load CSV
   df = pd.read_csv('/content/drive/MyDrive/training_data_German_xxx.csv', 
                    sep='|', 
                    names=['audio', 'text'])
   ```

4. **Train Model**
   - Follow Piper TTS training documentation
   - Use GPU runtime for faster training

## 📝 Tips & Tricks

### Generate Better Training Texts
- The AI automatically creates variety
- Each generation considers previous texts
- Covers different:
  - Topics (daily life, emotions, questions)
  - Styles (casual, formal, excited)
  - Sentence structures

### Improve Recording Quality
- Use headphones to monitor
- Do a test recording first
- Re-record if you make mistakes
- Keep consistent speaking pace

### Manage Your Dataset
- Delete bad recordings (long-press on recording)
- Review statistics regularly
- Aim for 30+ good recordings

## ❓ FAQ

**Q: How many recordings do I need?**  
A: Minimum 10, recommended 20-50 for good quality voice cloning.

**Q: Can I record in multiple languages?**  
A: Yes! Each language gets separate training data.

**Q: What if I make a mistake while recording?**  
A: Just delete the recording and record again with the same text.

**Q: Do I need to read the text exactly?**  
A: Yes, for best results match the text exactly as shown.

**Q: Can I edit the generated text?**  
A: Currently no, but you can generate new text until you get one you like.

## 🔗 Resources
- [Piper TTS GitHub](https://github.com/rhasspy/piper)
- [Piper Training Docs](https://github.com/rhasspy/piper/blob/master/TRAINING.md)
- [Google Colab](https://colab.research.google.com/)

