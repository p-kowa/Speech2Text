# Voice Cloning Base Models - Übersicht

## Was ist ein Base Model?

Ein **Base Model** ist ein vortrainiertes Piper TTS Model, das bereits gelernt hat, wie man Text in Sprache umwandelt. Beim **Fine-Tuning** nehmen wir dieses Model und passen es an deine individuelle Stimme an.

## Vorteile von Base Models:

- ✅ Bereits trainiert auf tausenden Stunden Sprachdaten
- ✅ Kennt Ausspracheregeln und Sprachmelodie
- ✅ Hohe Grundqualität
- ✅ Schnelles Fine-Tuning möglich (nur 10-20 Aufnahmen nötig)

---

## Verfügbare Base Models im Notebook

Das Colab Notebook verwendet offizielle Piper TTS Models von **Hugging Face**.

### 1. Deutsch (German)

**Model:** `de_DE-thorsten-high`

- **Quelle:** https://huggingface.co/rhasspy/piper-voices/tree/main/de/de_DE/thorsten/high
- **Qualität:** High (beste Qualität)
- **Sprecher:** Thorsten Müller
- **Trainingsdaten:** Thorsten Voice Dataset
- **Sample Rate:** 22050 Hz
- **Model-Größe:** ~63 MB

**Download URLs:**
```
Model: https://huggingface.co/rhasspy/piper-voices/resolve/main/de/de_DE/thorsten/high/de_DE-thorsten-high.onnx
Config: https://huggingface.co/rhasspy/piper-voices/resolve/main/de/de_DE/thorsten/high/de_DE-thorsten-high.onnx.json
```

### 2. Englisch (English)

**Model:** `en_US-lessac-high`

- **Quelle:** https://huggingface.co/rhasspy/piper-voices/tree/main/en/en_US/lessac/high
- **Qualität:** High (beste Qualität)
- **Sprecher:** Lessac
- **Sample Rate:** 22050 Hz
- **Model-Größe:** ~63 MB

**Download URLs:**
```
Model: https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/high/en_US-lessac-high.onnx
Config: https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/high/en_US-lessac-high.onnx.json
```

### 3. Polnisch (Polish)

**Model:** `pl_PL-darkman-medium`

- **Quelle:** https://huggingface.co/rhasspy/piper-voices/tree/main/pl/pl_PL/darkman/medium
- **Qualität:** Medium (gute Qualität, kleiner)
- **Sprecher:** Darkman
- **Sample Rate:** 22050 Hz
- **Model-Größe:** ~28 MB

**Download URLs:**
```
Model: https://huggingface.co/rhasspy/piper-voices/resolve/main/pl/pl_PL/darkman/medium/pl_PL-darkman-medium.onnx
Config: https://huggingface.co/rhasspy/piper-voices/resolve/main/pl/pl_PL/darkman/medium/pl_PL-darkman-medium.onnx.json
```

---

## Automatischer Download im Notebook

**Du musst die Models NICHT manuell herunterladen!**

Das Colab Notebook lädt sie automatisch in **Step 2** herunter:

```python
# Wähle deine Sprache
LANGUAGE = "de"  # oder "en" oder "pl"

# Das Notebook lädt automatisch das richtige Model
!wget -q {BASE_MODELS[LANGUAGE]} -O /content/base_model/base.onnx
!wget -q {MODEL_CONFIGS[LANGUAGE]} -O /content/base_model/base.onnx.json
```

---

## Weitere verfügbare Models

Falls du ein anderes Base Model verwenden möchtest, findest du alle verfügbaren Models hier:

**🔗 Piper Voices Repository:**
https://huggingface.co/rhasspy/piper-voices/tree/main

**Verfügbare Sprachen:**
- Deutsch (mehrere Stimmen)
- Englisch (US, UK, verschiedene Akzente)
- Polnisch
- Französisch
- Spanisch
- Italienisch
- Niederländisch
- Russisch
- und viele mehr...

---

## Wie wähle ich das richtige Base Model?

### 1. Nach Sprache
Wähle ein Model in der **gleichen Sprache** wie deine Aufnahmen:
- Deutsche Aufnahmen → Deutsches Base Model
- Englische Aufnahmen → Englisches Base Model
- usw.

### 2. Nach Qualität

**High Quality Models:**
- ✅ Bessere Aussprache
- ✅ Natürlicherer Klang
- ❌ Größer (~63 MB)
- ❌ Etwas langsamer

**Medium Quality Models:**
- ✅ Kleiner (~28 MB)
- ✅ Schneller
- ❌ Etwas weniger natürlich

**Empfehlung:** Verwende **High Quality** für beste Ergebnisse.

### 3. Nach Geschlecht/Stimmtyp

Wähle ein Base Model, das deiner Stimme ähnelt:
- Männliche Stimme → Männliches Base Model
- Weibliche Stimme → Weibliches Base Model

**Für Deutsch:**
- `thorsten` = Männliche Stimme ✅ (im Notebook verwendet)
- `eva_k` = Weibliche Stimme (alternative)

---

## Custom Base Model verwenden

Falls du ein anderes Base Model aus der Hugging Face Sammlung verwenden möchtest, ändere im Notebook Step 2:

```python
# Beispiel: Weibliche deutsche Stimme
CUSTOM_MODEL = "https://huggingface.co/rhasspy/piper-voices/resolve/main/de/de_DE/eva_k/x_low/de_DE-eva_k-x_low.onnx"
CUSTOM_CONFIG = "https://huggingface.co/rhasspy/piper-voices/resolve/main/de/de_DE/eva_k/x_low/de_DE-eva_k-x_low.onnx.json"

!wget -q {CUSTOM_MODEL} -O /content/base_model/base.onnx
!wget -q {CUSTOM_CONFIG} -O /content/base_model/base.onnx.json
```

---

## Model Format: ONNX

Alle Piper Models verwenden das **ONNX** Format:
- **O**pen **N**eural **N**etwork e**X**change
- Plattformunabhängig
- Optimiert für schnelle Inferenz
- Funktioniert auf Android, Desktop, Server

**Dateien pro Model:**
1. `.onnx` - Das eigentliche Neural Network Model
2. `.onnx.json` - Konfiguration (Phoneme, Sample Rate, etc.)

---

## Lizenz

Alle Piper Models sind **Open Source** und frei verwendbar:
- Lizenz: MIT / CC0
- Kommerzielle Nutzung erlaubt ✅
- Keine Attribution erforderlich ✅

Quelle: https://github.com/rhasspy/piper

---

## Zusammenfassung

| Sprache | Model | Qualität | Größe | Geschlecht |
|---------|-------|----------|-------|------------|
| Deutsch | `de_DE-thorsten-high` | High | 63 MB | Männlich |
| Englisch | `en_US-lessac-high` | High | 63 MB | Männlich |
| Polnisch | `pl_PL-darkman-medium` | Medium | 28 MB | Männlich |

**Im Notebook wird automatisch das richtige Model basierend auf deiner `LANGUAGE` Einstellung heruntergeladen!**

---

## Häufige Fragen (FAQ)

### Q: Muss ich das Base Model manuell herunterladen?
**A:** Nein! Das Notebook macht das automatisch in Step 2.

### Q: Kann ich mehrere Sprachen gleichzeitig trainieren?
**A:** Nein, wähle eine Sprache. Du kannst aber mehrere Models für verschiedene Sprachen erstellen.

### Q: Wie groß ist das finale fine-getunte Model?
**A:** Gleich groß wie das Base Model (~28-63 MB je nach Qualität).

### Q: Funktioniert das Model offline?
**A:** Ja! Einmal trainiert, funktioniert das Model komplett offline.

### Q: Kann ich mein eigenes Base Model trainieren?
**A:** Ja, aber das erfordert sehr viel mehr Daten (100+ Stunden) und ist deutlich aufwändiger. Fine-Tuning ist der empfohlene Weg.

---

## Nächste Schritte

1. ✅ Öffne das Colab Notebook `Voice_Cloning_Training.ipynb`
2. ✅ Setze `LANGUAGE = "de"` (oder deine gewünschte Sprache)
3. ✅ Führe Step 2 aus → Base Model wird automatisch heruntergeladen
4. ✅ Fahre mit dem Training fort

**Das war's! Du musst nichts manuell herunterladen.** 🎉

