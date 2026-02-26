# Cortex Library Branding Asset Pipeline

Dieses Verzeichnis enthält das Script, um aus dem bestehenden Full-Lockup (`Cortex Library-Logo.png`) die App-Assets zu erzeugen.

## Voraussetzung

- Python 3
- Pillow

```bash
pip install pillow
```

## Ausführung

### Windows (Git Bash)

```bash
python tools/brand/split_cortex_assets.py
```

### Windows (PowerShell)

```powershell
python .\tools\brand\split_cortex_assets.py
```

## Ergebnisdateien

Das Script schreibt:

- `android/src/main/res/drawable/cortex_icon.png`
- `android/src/main/res/drawable/cortex_wordmark.png`

## Wichtig

Die erzeugten PNGs werden **lokal** generiert und müssen anschließend ins Repo committed werden.
