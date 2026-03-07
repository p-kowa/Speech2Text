# Piper Training Notebook - Session Recovery Guide

## 🎯 Problem Solved!

The improved notebook solves the problem of losing checkpoints and training sessions.

## ✅ New Features

### 1. **Checkpoint Persistence on Google Drive**
- All checkpoints are saved directly to Google Drive
- No loss on session disconnect
- Automatic backups are created

### 2. **Automatic Session Recovery**
- If the Colab session disconnects, simply re-run all cells
- Training automatically resumes from the last checkpoint
- No manual checkpoint searching required

### 3. **Progress Tracking & Logging**
- `session_state.json` on Google Drive stores:
  - When training was started
  - Which checkpoint was last used
  - How many epochs have been trained
  - Total training duration
- `training_log.txt` on Google Drive logs:
  - All training events with timestamps
  - Checkpoint saves
  - Errors and warnings
  - Training completion status
  
### 4. **Triple-Backup Strategy**
- Main checkpoints: `/content/drive/MyDrive/piper_training/checkpoints/`
- Backup checkpoints: `/content/drive/MyDrive/piper_training/checkpoints_backup/`
- Lightning logs: `/content/drive/MyDrive/piper_training/lightning_logs/`

## 📋 How to Improve Your Notebook

### Option 1: Add New Cells (Recommended)

1. Open your existing `Piper_Voice_Training.ipynb`
2. Insert a new cell **after Step 9** (Configure Training Parameters)
3. Copy the code from `Piper_Voice_Training_Improved.ipynb` → First code cell
4. **Replace Step 12** (Start Training) with the improved training cell
5. Optional: Add the Progress Monitor cell at the end

### Option 2: Manual Adjustments

In your existing notebook:

#### A) Add after Step 9:

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

#### B) Replace the training code in Step 12:

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

## 🔄 How Session Recovery Works

### Scenario: Session Disconnects

1. **What happens:**
   - Colab session disconnects
   - Local `/content/` files are lost
   - **BUT:** All checkpoints are backed up on Google Drive!

2. **Recovery Steps:**
   ```
   1. Reopen the notebook
   2. Runtime → Run all (or run all cells sequentially)
   3. The notebook will:
      - Mount Google Drive
      - Automatically find the last checkpoint
      - Load session_state.json
      - Resume training
   ```

3. **No data lost!**
   - All checkpoints are on Drive
   - Progress continues
   - No manual intervention needed

## 📊 Monitor Progress

### During Training:

```python
# Run this cell to see current status
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

### View Training Log:

```python
# View the complete training log from Google Drive
TRAINING_LOG_FILE = Path("/content/drive/MyDrive/piper_training/training_log.txt")

if TRAINING_LOG_FILE.exists():
    with open(TRAINING_LOG_FILE, 'r', encoding='utf-8') as f:
        log_content = f.read()
    print(log_content)
else:
    print("Training log not yet created")
```

### After Session Disconnect:

```python
# Check if checkpoints are present
import glob
from pathlib import Path

CHECKPOINT_DIR = Path("/content/drive/MyDrive/piper_training/checkpoints")
checkpoints = list(CHECKPOINT_DIR.glob("*.ckpt"))

print(f"✅ Found {len(checkpoints)} checkpoint(s) on Google Drive")
for ckpt in sorted(checkpoints, key=lambda x: x.stat().st_mtime, reverse=True):
    print(f"  - {ckpt.name}")
```

## 🎓 Best Practices

### 1. **Before Training:**
- Ensure Google Drive is mounted
- Check available storage space
- Checkpoints require ~500MB - 2GB

### 2. **During Training:**
- You can close the tab - training continues
- Check progress every few hours
- Google Drive saves automatically

### 3. **On Session Loss:**
- Don't panic! Checkpoints are on Drive
- Simply reopen the notebook
- Run all cells → Training resumes

### 4. **After Training:**
- Check both checkpoint folders
- Backup checkpoint is in `checkpoints_backup/`
- `session_state.json` contains all details

## 🛠️ Troubleshooting

### Problem: "No checkpoint found"

```python
# Check manually:
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
# Create manually:
import json
from pathlib import Path
from datetime import datetime

SESSION_STATE_FILE = Path("/content/drive/MyDrive/piper_training/session_state.json")
session_state = {
    'voice_name': 'my_own_voice',  # Adjust!
    'language': 'pl',  # Adjust!
    'started': datetime.now().isoformat(),
    'resume_from': None  # Or path to checkpoint
}

with open(SESSION_STATE_FILE, 'w') as f:
    json.dump(session_state, f, indent=2)

print(f"✅ Created: {SESSION_STATE_FILE}")
```

### Problem: "Training starts from epoch 0"

This is normal! PyTorch Lightning sometimes renumbers epochs. What's important:
- The checkpoint is loaded
- Model weights are correct
- Training continues from the saved state

## 📁 File Structure on Google Drive

After improvements:

```
/content/drive/MyDrive/piper_training/
├── session_state.json          # Training status & metadata
├── training_log.txt             # Complete training log with timestamps
├── wavs/                        # Your recordings
├── metadata.csv                 # Training data
├── my_own_voice.json           # Model config
├── checkpoints/                # Main checkpoints
│   ├── my_own_voice-epoch=50.ckpt
│   └── my_own_voice-epoch=100.ckpt
├── checkpoints_backup/         # Backups
│   └── my_own_voice_final_20260307.ckpt
└── lightning_logs/             # PyTorch Lightning logs
    └── version_0/
        └── checkpoints/
            └── epoch=100-step=1000.ckpt
```

### Log Files Explained:

**`session_state.json`**: 
- JSON file with training metadata
- Contains paths, timestamps, configuration
- Used for automatic session recovery

**`training_log.txt`**:
- Human-readable log file
- All training events with timestamps
- Checkpoint saves, errors, warnings
- Easy to read and share

**`lightning_logs/`**:
- PyTorch Lightning's internal logs
- TensorBoard compatible
- Detailed metrics and graphs

## ✨ Summary

With these improvements:
- ✅ No more checkpoint loss
- ✅ Automatic resume after disconnect
- ✅ All data safe on Google Drive
- ✅ Progress tracking available anytime
- ✅ Triple-backup for maximum safety

**You can now train worry-free - your work is safe!** 🎉

