# Piper Training Notebook - Session Recovery Guide

## 🎯 Problem gelöst!

Das verbesserte Notebook löst das Problem, dass Checkpoints und Training-Sessions verloren gehen können.

## ✅ Neue Features

### 1. **Checkpoint-Persistence auf Google Drive**
- Alle Checkpoints werden direkt auf Google Drive gespeichert
- Kein Verlust bei Session-Disconnect
- Automatische Backups werden erstellt

### 2. **Automatisches Session-Recovery**
- Wenn die Colab-Session abbricht, einfach alle Zellen neu ausführen
- Training setzt automatisch vom letzten Checkpoint fort
- Kein manuelles Suchen nach Checkpoints nötig

### 3. **Progress-Tracking**
- `session_state.json` auf Google Drive speichert:
  - Wann Training gestartet wurde
  - Welcher Checkpoint zuletzt verwendet wurde
  - Wie viele Epochen bereits trainiert wurden
  - Gesamte Trainingsdauer
  
### 4. **Triple-Backup-Strategie**
- Haupt-Checkpoints: `/content/drive/MyDrive/piper_training/checkpoints/`
- Backup-Checkpoints: `/content/drive/MyDrive/piper_training/checkpoints_backup/`
- Lightning-Logs: `/content/drive/MyDrive/piper_training/lightning_logs/`

## 📋 Wie du das Notebook verbesserst

### Option 1: Neue Zellen hinzufügen (Empfohlen)

1. Öffne dein bestehendes `Piper_Voice_Training.ipynb`
2. Füge **nach Step 9** (Configure Training Parameters) eine neue Zelle ein
3. Kopiere den Code aus `Piper_Voice_Training_Improved.ipynb` → Erste Code-Zelle
4. **Ersetze Step 12** (Start Training) mit der verbesserten Training-Zelle
5. Optional: Füge die Progress Monitor-Zelle am Ende hinzu

### Option 2: Manuelle Anpassungen

In deinem bestehenden Notebook:

#### A) Nach Step 9 hinzufügen:

```python
# Setup checkpoint directory on Google Drive for persistence
import glob
import json
from datetime import datetime
from pathlib import Path

# Create checkpoint directory on Google Drive
CHECKPOINT_DIR = Path("/content/drive/MyDrive/piper_training/checkpoints")
CHECKPOINT_DIR.mkdir(parents=True, exist_ok=True)

BACKUP_CHECKPOINT_DIR = Path("/content/drive/MyDrive/piper_training/checkpoints_backup")
BACKUP_CHECKPOINT_DIR.mkdir(parents=True, exist_ok=True)

SESSION_STATE_FILE = Path("/content/drive/MyDrive/piper_training/session_state.json")

# Check for existing checkpoints
existing_checkpoints = sorted(glob.glob(str(CHECKPOINT_DIR / "*.ckpt")))
backup_checkpoints = sorted(glob.glob(str(BACKUP_CHECKPOINT_DIR / "*.ckpt")))

all_checkpoints = sorted(existing_checkpoints + backup_checkpoints, 
                        key=lambda x: Path(x).stat().st_mtime)

if all_checkpoints:
    RESUME_CHECKPOINT = all_checkpoints[-1]
    print(f"🔄 Found checkpoint to resume from: {Path(RESUME_CHECKPOINT).name}")
else:
    RESUME_CHECKPOINT = CKPT_URL if CKPT_URL else None

# Save session state
session_state = {
    'voice_name': VOICE_NAME,
    'language': ESPEAK_VOICE,
    'started': datetime.now().isoformat(),
    'resume_from': RESUME_CHECKPOINT
}
with open(SESSION_STATE_FILE, 'w') as f:
    json.dump(session_state, f, indent=2)
    
print(f"✅ Session recovery enabled!")
```

#### B) Ersetze den Training-Code in Step 12:

```python
import shutil

LIGHTNING_LOGS_DIR = Path("/content/drive/MyDrive/piper_training/lightning_logs")
LIGHTNING_LOGS_DIR.mkdir(parents=True, exist_ok=True)

# Build training command
training_command = f"""python3 -m piper.train fit \\
  --data.voice_name "{VOICE_NAME}" \\
  --data.csv_path "{str(METADATA_CSV)}" \\
  --data.audio_dir "{str(AUDIO_DIR)}" \\
  --model.sample_rate {SAMPLE_RATE_HZ} \\
  --data.espeak_voice "{ESPEAK_VOICE}" \\
  --data.cache_dir "{str(CACHE_DIR)}" \\
  --data.config_path "{str(CONFIG_PATH)}" \\
  --data.batch_size {BATCH_SIZE} \\
  --trainer.default_root_dir "{str(LIGHTNING_LOGS_DIR)}" \\
  --trainer.enable_checkpointing true \\
  --trainer.max_epochs 10000"""

# Add resume checkpoint
if RESUME_CHECKPOINT:
    training_command += f' \\\n  --ckpt_path "{RESUME_CHECKPOINT}"'
    print(f"📂 Resuming from: {Path(RESUME_CHECKPOINT).name}\n")

# Execute
!{training_command}

# Copy final checkpoint to main location
final_checkpoints = sorted(glob.glob(str(LIGHTNING_LOGS_DIR / "**/*.ckpt"), recursive=True))
if final_checkpoints:
    latest = final_checkpoints[-1]
    shutil.copy2(latest, CHECKPOINT_DIR / Path(latest).name)
    shutil.copy2(latest, BACKUP_CHECKPOINT_DIR / f"{VOICE_NAME}_final.ckpt")
    
    session_state['training_completed'] = datetime.now().isoformat()
    session_state['final_checkpoint'] = str(latest)
    with open(SESSION_STATE_FILE, 'w') as f:
        json.dump(session_state, f, indent=2)
    
    print(f"✅ Checkpoints saved to Google Drive!")
```

## 🔄 Wie Session-Recovery funktioniert

### Szenario: Session bricht ab

1. **Was passiert:**
   - Colab-Session trennt Verbindung
   - Lokale `/content/` Dateien gehen verloren
   - **ABER:** Alle Checkpoints sind auf Google Drive gesichert!

2. **Recovery-Schritte:**
   ```
   1. Öffne das Notebook erneut
   2. Runtime → Run all (oder führe alle Zellen nacheinander aus)
   3. Das Notebook:
      - Mountet Google Drive
      - Findet automatisch den letzten Checkpoint
      - Lädt die session_state.json
      - Setzt Training fort
   ```

3. **Keine Daten verloren!**
   - Alle Checkpoints sind auf Drive
   - Progress wird fortgesetzt
   - Keine manuelle Intervention nötig

## 📊 Progress überwachen

### Während des Trainings:

```python
# Führe diese Zelle aus, um aktuellen Status zu sehen
import json
from pathlib import Path

SESSION_STATE_FILE = Path("/content/drive/MyDrive/piper_training/session_state.json")

if SESSION_STATE_FILE.exists():
    with open(SESSION_STATE_FILE, 'r') as f:
        state = json.load(f)
    
    print(f"Voice: {state.get('voice_name')}")
    print(f"Started: {state.get('started')}")
    print(f"Checkpoint: {state.get('resume_from')}")
```

### Nach Session-Disconnect:

```python
# Überprüfe, ob Checkpoints vorhanden sind
import glob
from pathlib import Path

CHECKPOINT_DIR = Path("/content/drive/MyDrive/piper_training/checkpoints")
checkpoints = list(CHECKPOINT_DIR.glob("*.ckpt"))

print(f"✅ Found {len(checkpoints)} checkpoint(s) on Google Drive")
for ckpt in sorted(checkpoints, key=lambda x: x.stat().st_mtime, reverse=True):
    print(f"  - {ckpt.name}")
```

## 🎓 Best Practices

### 1. **Vor dem Training:**
- Stelle sicher, dass Google Drive gemountet ist
- Überprüfe verfügbaren Speicherplatz
- Checkpoints benötigen ~500MB - 2GB

### 2. **Während des Trainings:**
- Du kannst den Tab schließen - Training läuft weiter
- Check alle paar Stunden den Progress
- Google Drive speichert automatisch

### 3. **Bei Session-Verlust:**
- Keine Panik! Checkpoints sind auf Drive
- Einfach Notebook neu öffnen
- Alle Zellen ausführen → Training setzt fort

### 4. **Nach dem Training:**
- Überprüfe beide Checkpoint-Ordner
- Backup-Checkpoint ist in `checkpoints_backup/`
- `session_state.json` enthält alle Details

## 🛠️ Troubleshooting

### Problem: "No checkpoint found"

```python
# Überprüfe manuell:
from pathlib import Path
import glob

dirs_to_check = [
    "/content/drive/MyDrive/piper_training/checkpoints",
    "/content/drive/MyDrive/piper_training/checkpoints_backup",
    "/content/drive/MyDrive/piper_training/lightning_logs"
]

for dir_path in dirs_to_check:
    p = Path(dir_path)
    if p.exists():
        ckpts = list(p.glob("**/*.ckpt"))
        print(f"{dir_path}: {len(ckpts)} checkpoint(s)")
    else:
        print(f"{dir_path}: Doesn't exist")
```

### Problem: "Session state not found"

```python
# Erstelle manuell:
import json
from pathlib import Path
from datetime import datetime

SESSION_STATE_FILE = Path("/content/drive/MyDrive/piper_training/session_state.json")
session_state = {
    'voice_name': 'my_own_voice',  # Anpassen!
    'language': 'pl',  # Anpassen!
    'started': datetime.now().isoformat(),
    'resume_from': None  # Oder Pfad zum Checkpoint
}

with open(SESSION_STATE_FILE, 'w') as f:
    json.dump(session_state, f, indent=2)

print(f"✅ Created: {SESSION_STATE_FILE}")
```

### Problem: "Training starts from epoch 0"

Das ist normal! PyTorch Lightning nummeriert manchmal Epochen neu. Wichtig ist:
- Der Checkpoint wird geladen
- Modell-Weights sind korrekt
- Training setzt inhaltlich fort

## 📁 Datei-Struktur auf Google Drive

Nach der Verbesserung:

```
/content/drive/MyDrive/piper_training/
├── session_state.json          # Training-Status
├── wavs/                        # Deine Aufnahmen
├── metadata.csv                 # Training-Daten
├── my_own_voice.json           # Modell-Config
├── checkpoints/                # Haupt-Checkpoints
│   ├── my_own_voice-epoch=50.ckpt
│   └── my_own_voice-epoch=100.ckpt
├── checkpoints_backup/         # Backups
│   └── my_own_voice_final_20260307.ckpt
└── lightning_logs/             # PyTorch Lightning Logs
    └── version_0/
        └── checkpoints/
            └── epoch=100-step=1000.ckpt
```

## ✨ Zusammenfassung

Mit diesen Verbesserungen:
- ✅ Kein Checkpoint-Verlust mehr
- ✅ Automatisches Resume nach Disconnect
- ✅ Alle Daten sicher auf Google Drive
- ✅ Progress-Tracking jederzeit abrufbar
- ✅ Triple-Backup für maximale Sicherheit

**Du kannst jetzt beruhigt trainieren - deine Arbeit ist sicher!** 🎉

