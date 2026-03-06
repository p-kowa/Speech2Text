# Voice Cloning Training - Quick Start Guide

## 🚀 Einfachste Methode: Direkt von GitHub öffnen

Da dein Projekt bereits auf GitHub ist (`p-kowa/Speech2Text`), kannst du das Notebook **direkt von dort** in Google Colab öffnen!

### So geht's:

1. **Notebook zu GitHub pushen** (bereits gemacht ✅):
   ```powershell
   git add Voice_Cloning_Training.ipynb
   git commit -m "Add voice cloning training notebook"
   git push
   ```

2. **In Google Colab öffnen**:
   
   Gehe zu dieser URL:
   ```
   https://colab.research.google.com/github/p-kowa/Speech2Text/blob/main/Voice_Cloning_Training.ipynb
   ```
   
   **Oder:**
   - Öffne https://colab.research.google.com
   - Klicke auf "GitHub" Tab
   - Gib ein: `p-kowa/Speech2Text`
   - Wähle `Voice_Cloning_Training.ipynb`

3. **Speichere eine Kopie in deinem Drive** (einmalig):
   - In Colab: `File` → `Save a copy in Drive`
   - Ab jetzt öffnest du diese Kopie aus deinem Drive

### ✅ Vorteile:
- ✅ Kein manuelles Hochladen nötig
- ✅ Immer die neueste Version
- ✅ Link kannst du bookmarken
- ✅ Änderungen bleiben in deinem Drive erhalten

---

## 📤 Alternative: Upload-Script (für Google Drive)

Falls du es lieber direkt auf Google Drive haben möchtest:

### Setup (einmalig):

1. **Google Cloud Console öffnen:**
   https://console.cloud.google.com

2. **Neues Projekt erstellen:**
   - Klicke auf Projekt-Dropdown → "New Project"
   - Name: "Speech2Text Notebook"

3. **Google Drive API aktivieren:**
   - APIs & Services → Library
   - Suche "Google Drive API"
   - Klicke "Enable"

4. **OAuth Credentials erstellen:**
   - APIs & Services → Credentials
   - "Create Credentials" → "OAuth client ID"
   - Application type: "Desktop app"
   - Name: "Notebook Uploader"
   - Download als `credentials.json`

5. **Script vorbereiten:**
   ```powershell
   # In PowerShell im Projektordner
   cd C:\Daten\Android\Speech2Text
   
   # Dependencies installieren
   pip install google-auth google-auth-oauthlib google-api-python-client
   
   # credentials.json hierher kopieren
   # (Download aus Schritt 4)
   ```

6. **Notebook hochladen:**
   ```powershell
   python upload_notebook_to_drive.py
   ```
   
   Beim ersten Mal:
   - Browser öffnet sich
   - Mit Google anmelden
   - Zugriff erlauben
   - Token wird gespeichert

### Danach:
```powershell
# Jedes Mal wenn du das Notebook änderst:
python upload_notebook_to_drive.py
```

Das Script gibt dir dann einen Link zum direkten Öffnen in Colab! 🎉

---

## 🔄 Workflow-Empfehlung:

### Option A: GitHub → Colab (EMPFOHLEN)
```
1. Notebook lokal bearbeiten
2. git push
3. In Colab von GitHub öffnen
4. "Save a copy in Drive" (einmalig)
5. Arbeiten in der Drive-Kopie
```

**Vorteil:** Einfach, keine zusätzliche Software nötig

### Option B: Direkter Drive Upload
```
1. Notebook lokal bearbeiten
2. python upload_notebook_to_drive.py
3. Link im Terminal kopieren
4. In Colab öffnen
```

**Vorteil:** Schneller, kein Git-Push nötig

---

## 💡 Tipp: Bookmark erstellen

Erstelle ein Bookmark mit diesem Link (nachdem du es einmal in Drive gespeichert hast):

```
https://colab.research.google.com/drive/[DEINE_FILE_ID]
```

Die File ID findest du, wenn du das Notebook in Google Drive öffnest - sie steht in der URL.

Dann kannst du das Notebook mit einem Klick öffnen! 🚀

---

## 📝 Zusammenfassung

| Methode | Aufwand | Empfehlung |
|---------|---------|------------|
| GitHub → Colab | Niedrig | ⭐⭐⭐⭐⭐ Beste Wahl |
| Upload Script | Mittel | ⭐⭐⭐ Gut für Profis |
| Manuelles Upload | Hoch | ⭐ Nicht empfohlen |

**Meine Empfehlung:** Nutze GitHub! Dein Projekt ist bereits dort, also ist es der einfachste Weg.

