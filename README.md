# Velo — Android Video Downloader

A beautiful, on-device video downloader for Android built with Kotlin, Jetpack Compose and yt-dlp. Cobalt-inspired design.

## Features
- 🌐 **1000+ sites** — YouTube, Instagram, Facebook, TikTok, Twitter, Reddit, and more via yt-dlp
- 📋 **Clipboard detection** — copy a video URL, open Velo, see instant download prompt
- 📤 **Share intercept** — Share → Velo shows quality picker *without* opening the main app
- 🎬 **Quality selection** — all available resolutions + audio-only options
- ⬇️ **Background downloads** — WorkManager keeps downloading even if you close the app
- 🎨 **Cobalt-inspired design** — dark mode, IBM Plex Mono, flat UI, no shadows

## Setup in Android Studio

### 1. Open the project
File → Open → select the `URL Downloader` folder

### 2. Add IBM Plex Mono fonts
Download from https://fonts.google.com/specimen/IBM+Plex+Mono and place in:
```
app/src/main/res/font/
  ibm_plex_mono_light.ttf       (weight 300)
  ibm_plex_mono_regular.ttf     (weight 400)
  ibm_plex_mono_medium.ttf      (weight 500)
  ibm_plex_mono_bold.ttf        (weight 700)
```

### 3. Add launcher icons
Right-click `res` → New → Image Asset → generate `ic_launcher` and `ic_launcher_round`

### 4. Build and run
```bash
./gradlew assembleDebug
```
Or press Run in Android Studio.

## Architecture

```
Kotlin + Jetpack Compose
    ↓
ShareActivity (transparent) → QualityBottomSheet
MainActivity → HomeScreen + DownloadsScreen
    ↓
YtDlpEngine (youtubedl-android library)
    ↓
DownloadWorker (WorkManager) → Notification
    ↓
Room DB (download history)
```

## Key files
| File | Purpose |
|---|---|
| `ShareActivity.kt` | Catches share intents, shows quality sheet without opening app |
| `QualityBottomSheetContent.kt` | The quality picker UI |
| `ClipboardWatcher.kt` | Detects copied URLs on app resume |
| `SupportedSites.kt` | Regex patterns for 15+ sites |
| `YtDlpEngine.kt` | yt-dlp wrapper, format parsing, download |
| `DownloadWorker.kt` | Background download with notifications |

## Android version support
- Min: Android 8.0 (API 26)
- Target: Android 16 (API 36)
- Kotlin: 2.1.20
- Compose BOM: 2025.02.00
