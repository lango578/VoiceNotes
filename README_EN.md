# Voice Notes — Complete English Tutorial

**Voice Notes** is an Android app that **transcribes speech to text in real time while recording**, saves the recording as an audio file, and automatically creates an editable, shareable note. It also supports **sentence-by-sentence English → Chinese annotations** when English is recognized.

- Target devices: **Android 16 (API 36)** — verified on OPPO / ColorOS 16; minimum **Android 8.0 (API 26)**.
- Language: **Kotlin** + classic Views (no Compose), simple and stable to build.
- Storage: all recordings (`filesDir/recordings/*.m4a`) and notes (`filesDir/notes/*.json`) are kept **on your device** in the app's private storage.

---

## Table of Contents

1. [Features](#1-features)
2. [System Requirements](#2-system-requirements)
3. [Installation](#3-installation)
4. [Choose a Recognition Engine](#4-choose-a-recognition-engine)
5. [Recognition Language](#5-recognition-language)
6. [English → Chinese Annotations (Optional)](#6-english--chinese-annotations-optional)
7. [Self-hosted Backend Server](#7-self-hosted-backend-server)
8. [Using the App](#8-using-the-app)
9. [Notes Management](#9-notes-management)
10. [Troubleshooting / FAQ](#10-troubleshooting--faq)
11. [Architecture & Technical Notes](#11-architecture--technical-notes)
12. [Privacy & Disclaimer](#12-privacy--disclaimer)

---

## 1. Features

| Feature | Description |
|---|---|
| Real-time transcription | Words appear on screen while you speak (≈0.5–1 s latency) |
| Continuous long recording | Auto-renews sessions every 50 s (Xunfei engine) for unlimited-length dictation |
| Audio recording | 16 kHz AAC `.m4a` files (WAV fallback), stored privately |
| Background recording | Foreground service with a persistent notification; keeps working with the screen off |
| Notes management | Auto-titled notes; view, edit, play audio, share text/audio, delete |
| Multiple engines | Xunfei / Tencent Cloud / Baidu AI / **self-hosted backend (Whisper)** / system recognizer — switch in Settings |
| Language switching | Mandarin, Cantonese, English, Sichuan dialect (support depends on the engine) |
| English → Chinese notes | When English is recognized, each sentence gets a Chinese translation underneath (via a self-hosted backend or a third-party API such as DeepSeek) |

---

## 2. System Requirements

- **Android 8.0+** device (API 26); optimized for **Android 16 (API 36)**.
- One cloud ASR account (Xunfei / Tencent Cloud / Baidu AI) — each offers free quotas.
- To build from source: **Android Studio Narwhal (2025.1.1)+**, JDK 17, and the **Android SDK Platform 36**.
- For the optional self-hosted backend: a computer with **Python 3.10+** or **Docker**; a GPU is optional (CPU works with the `small` Whisper model).

---

## 3. Installation

### 3.1 Install the APK (quick start)

Download from the GitHub Release page:

- **https://github.com/lango578/VoiceNotes/releases/tag/v1.0**

Pick **`VoiceNotes-release.apk`** (formally signed, recommended) or `VoiceNotes-debug.apk`. Transfer it to your phone, open it, and allow "install from unknown sources" when prompted.

> ⚠️ The two APKs have **different signatures** — install only one variant. To upgrade, keep using the same variant (uninstall first if you switch).

### 3.2 Build from source

1. Open the project folder with Android Studio.
2. If Gradle reports `Gradle wrapper not found`, run once (Windows):
   ```powershell
   powershell -ExecutionPolicy Bypass -File setup-gradle-wrapper.ps1
   ```
3. Wait for Gradle Sync (downloads Gradle 8.11.1 and dependencies).
4. Connect your phone with USB debugging, then press **Run ▶**.
   Or build on the command line:
   ```powershell
   .\gradlew.bat assembleDebug
   # output: app\build\outputs\apk\debug\app-debug.apk
   ```
---

## 7. Self-hosted Backend Server

The `server/` folder contains a small FastAPI server that can:

1. **Translate** English sentences to Chinese (using DeepSeek or Baidu, or a simple built-in dictionary for short phrases).
2. **Transcribe** audio files with **faster-whisper** (offline, no cloud needed).

Full server documentation: [server/README.md](server/README.md).

### 7.1 Endpoints

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/health` | Health check, returns server status |
| `POST` | `/api/annotate` | JSON `{"text": "...", "source": "en", "target": "zh"}` → returns translated lines |
| `POST` | `/api/transcribe` | Multipart audio upload (`file`) → returns `{"text": "...", "language": "...", "annotations": [...]}` |
| `GET` | `/api/provider` | Returns the configured translation provider |

### 7.2 Run with Python

```bash
cd server
pip install -r requirements.txt
pip install faster-whisper        # enables the Whisper transcription endpoint
# system requirement: ffmpeg (to decode .m4a audio)
#   Windows: winget install ffmpeg
#   macOS:   brew install ffmpeg
#   Ubuntu:  sudo apt install ffmpeg
```

Create `.env` (copy from `.env.example`):

```ini
# Translation provider: deepseek | baidu
TRANSLATE_PROVIDER=deepseek
DEEPSEEK_API_KEY=sk-xxxxxxxx
# Baidu text translation credentials (only when using baidu provider):
# BAIDU_APPID=...
# BAIDU_SECRET_KEY=...

# Optional: require clients to send this token (X-Token header)
# AUTH_TOKEN=change-me

# Whisper model size: tiny | base | small | medium | large-v3
WHISPER_MODEL=small
WHISPER_DEVICE=auto          # cuda | cpu | auto
```

Start the server:

```bash
uvicorn main:app --host 0.0.0.0 --port 8000
# or with Docker:
docker build -t voice-notes-server .
docker run -p 8000:8000 --env-file .env voice-notes-server
```

### 7.3 Test it

```bash
# health
curl http://localhost:8000/api/health

# translate
curl -X POST http://localhost:8000/api/annotate \
  -H "Content-Type: application/json" \
  -d '{"text":"Hello everyone. Welcome to my channel.","source":"en","target":"zh"}'

# transcribe (requires faster-whisper installed)
curl -X POST http://localhost:8000/api/transcribe \
  -F "file=@sample.m4a"
```

### 7.4 First-time model download

The first time you transcribe, faster-whisper downloads the model (a few hundred MB for `small`). On a CPU, `small` transcribes roughly as fast as real time; on an NVIDIA GPU (`WHISPER_DEVICE=cuda`) it is several times faster. Use `base` or `tiny` for weaker machines.

---

## 8. Using the App

1. **Grant permissions** — on first launch, allow **Microphone** and **Notifications** (Android 13+ asks for notifications separately).
2. **Configure settings** (recommended before your first recording):
   - Set your recognition engine + keys (Section 4) and language (Section 5).
   - If you want English→Chinese notes, set the annotation mode (Section 6).
3. **Record** — press the **● REC** button on the main screen. The live transcript appears in the text box as you speak.
4. **Stop** — press **■ STOP**. The app saves the audio, builds a note with an auto-generated title (from the first few words) and the full transcript, then shows it.
   - With the self-hosted backend engine, the file upload + transcription takes a few seconds after you stop.
5. **Save / discard** — edit the title or text, then tap **Save**; the note appears in the list.
6. **Record again** — tap **● REC** to start a new session. Everything is stored on the device.

> While recording, the app shows a **foreground notification** with the elapsed time — this keeps Android from killing the recording when the screen is off. Stop the recording from the app or from that notification.

---

## 9. Notes Management

- **List** — every note shows its title, first line, timestamp, and audio duration; tap to open.
- **View / edit** — the note screen shows the transcript with any Chinese annotations; edit title and text freely.
- **Play** — tap the ▶ button to play the recorded audio (a small player at the bottom; audio is available when the engine saves it — always for the real-time engines, and always for the backend engine).
- **Share** — share the text, or share/export the audio file as `.m4a` via the system share sheet.
- **Delete** — remove the note (its audio file is removed too).

---

## 10. Troubleshooting / FAQ

### "No speech recognized" / empty transcript
- Check your internet connection (cloud engines need it).
- Check the microphone permission and that the mic is near you.
- Confirm the **language** matches what you are speaking (English ≠ Mandarin).
- Try the **system recognizer** engine — it needs no keys and is a good sanity test.
- Xunfei: a 60-second session limit means you must keep talking; long silences can stop the session (the app auto-renews every 50 s).

### Recording keeps stopping / service killed
- Battery optimization: add Voice Notes to the "don't optimize / auto-start" list in your phone's battery settings (OPPO: Settings → Battery → Power-saving → App battery saver → Voice Notes → don't limit).
- Keep the notification bar visible while recording (that is the foreground service indicator).

### Annotation returns nothing
- Verify the backend is running: `curl http://<ip>:8000/api/health`.
- DeepSeek direct mode needs a valid, topped-up API key.
- Network: your phone and server must be on the same network, and the server must listen on `0.0.0.0` (not `127.0.0.1`).
- If you set `AUTH_TOKEN` on the server, fill in the same **Backend token** in the app's Settings.

### Phone and server cannot talk to each other
- Use the computer's LAN IP (not `localhost`) — e.g. `http://192.168.1.20:8000`.
- Disable the computer's firewall for port 8000, or add an inbound rule.
- The token field (if used) must match exactly.

### Build fails on a clean machine
- JDK must be **17** (the repo's Gradle 8.11.1 does not work with JDK 21 in all setups; use Android Studio's bundled JDK 17).
- Install the **Android SDK Platform 36** (`sdkmanager "platforms;android-36"`).
- If you changed the signing key, `keystore.properties` in the repo only contains release build configuration; debug builds use the default debug keystore.

### How do I upgrade the APK?
- Install the new APK of the **same variant** (release over release, debug over debug) — signatures must match, otherwise uninstall first (which deletes your notes/recordings, so export important audio first).

---

## 11. Architecture & Technical Notes

- **UI**: Kotlin + classic Views; `MainActivity` (list + recorder), `RecorderActivity`, `NoteDetailActivity`, `SettingsActivity`.
- **Recording service**: `RecordingService` (foreground service) + `RecorderSession` + `AudioRecord`; audio written to `.m4a` via `MediaCodec`/`MediaMuxer` (`AacFileWriter`) with a WAV fallback (`WavFileWriter`).
- **Real-time engines**: streaming WebSocket/HTTP clients — `XunfeiIatClient`, `TencentIatClient`, `BaiduIatClient`, `SystemRecognizerClient` — each producing a live transcript that streams into the recorder UI.
- **Backend engine**: `BackendAsrClient` uploads the finished `.m4a` to the server's `/api/transcribe`.
- **Translation**: `TranslationClient` (DeepSeek direct), `BackendAsrClient`-integrated server translation, and `BaiduApp` annotation modes.
- **Storage**: notes as JSON in `filesDir/notes/`; audio in `filesDir/recordings/`; `NoteStore` handles CRUD; preferences in `Prefs` (SharedPreferences).
- **Android 16 target**: `targetSdk = 36`, edge-to-edge with `Insets` handling, notification permission flow, foreground service type `microphone`.
- **Build**: Gradle 8.11.1, AGP 8.9.x, Kotlin 2.x, `com.android.application` only (no extra dependencies, no network libraries beyond platform APIs).

## 12. Privacy & Disclaimer

- All notes and audio stay on your device unless you enable a cloud engine or the annotation feature, which necessarily sends audio/text to the provider you chose.
- The cloud engines and DeepSeek are third-party services; their terms apply. The self-hosted backend keeps everything inside your own network.
- API keys are stored only on the phone (SharedPreferences) and never uploaded anywhere.
- This project is provided as-is for educational and personal use; verify speech-to-text and translation output before relying on it.

