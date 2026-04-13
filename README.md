# Velo — Android Video Downloader

A beautiful, on-device video downloader for Android built with Kotlin, Jetpack Compose and yt-dlp. Cobalt-inspired design.

## Download

[<img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" height="48">](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22:%22com.velo.app%22,%22url%22:%22https://github.com/arindamparia/velo%22,%22source%22:%22GitHub%22,%22prefer_oldest_version%22:false%7D)

Or grab the APK directly from the [**Releases**](../../releases/latest) page.

> **Note:** Android will show a "Play Protect" warning because Velo isn't on the Play Store.
> This is expected for any GitHub-distributed app. Tap **Install anyway** — the APK is signed
> with a consistent certificate and the full source code is public.

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
