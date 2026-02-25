# Monogatari Branding Asset Pipeline

Dieses Verzeichnis enthält das Script, um aus dem bestehenden Full-Lockup (`Monogatari-Logo.png`) die App-Assets zu erzeugen.

## Voraussetzung

- Python 3
- Pillow

```bash
pip install pillow
```

## Ausführung

### Windows (Git Bash)

```bash
python tools/brand/split_monogatari_assets.py
```

### Windows (PowerShell)

```powershell
python .\tools\brand\split_monogatari_assets.py
```

## Ergebnisdateien

Das Script schreibt:

- `android/src/main/res/drawable/monogatari_icon.png`
- `android/src/main/res/drawable/monogatari_wordmark.png`

## Wichtig

Die erzeugten PNGs werden **lokal** generiert und müssen anschließend ins Repo committed werden.
