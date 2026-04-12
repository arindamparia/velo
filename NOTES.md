# Velo — Engineering Notes

> **For AI assistants:** This file is the authoritative guide for Velo's critical subsystems.
> Read this BEFORE modifying `YtDlpEngine.kt`, `DownloadsScreen.kt`, `DownloadWorker.kt`, or
> `HomeScreen.kt`. Many of the patterns here were arrived at after hours of debugging — do not
> "simplify" or "improve" them without understanding why they exist.

---

## yt-dlp YouTube player_client

### Problem
Forcing `--extractor-args "youtube:player_client=web"` returns only **180p or 360p**.

**Root cause:** Since late 2024, YouTube's `web` client switched to **SABR** (Server-Adaptive
Bitrate) — a proprietary streaming protocol yt-dlp cannot yet download. The only usable fallback
is itag 18 = 360p muxed MP4.

Sources:
- https://github.com/yt-dlp/yt-dlp/issues/12482 — web client has only SABR, no HTTPS DASH
- https://github.com/yt-dlp/yt-dlp/issues/12963 — formats capped at 360p (missing PO token)
- https://github.com/yt-dlp/yt-dlp/issues/13453 — no formats above 360p

### Fix (getFormats — quality picker)
**Same player_client and UA rules apply as download.**

- Do NOT override `--user-agent` for YouTube in getFormats — Chrome UA causes yt-dlp to
  select the `web` client → SABR-only → 360p max in the format list.
- DO set `player_client=tv,android_vr,web_embedded;player_js_version=actual` for YouTube
  so that getFormats returns the same full 1080p/4K list that download uses.

### Fix (download — HTTP 403 on actual download)
Two separate issues caused 403 on download even when getFormats shows full quality:

**1. User-Agent mismatch (fixed April 2026)**
Do not override `--user-agent` for YouTube downloads. yt-dlp's clients set their own
UA for innertube API calls. Desktop Chrome UA + android_vr client = CDN rejects with 403.

**2. Wrong player_client — PO token required (fixed April 2026)**
As of yt-dlp 2025.11+, `ios`, `web_safari`, `android`, `mweb`, `tv_simply` all require
GVS PO tokens. Without cookies/auth, they 403 on actual download.

Clients that work WITHOUT PO tokens (confirmed yt-dlp 2026.03.13+):
- `tv` — most reliable; pinned to player `9f4cc5e4`; no PO token (#16162)
- `android_vr` — fixed in 2026.03.13 (#16168); no PO token
- `web_embedded` — fixed in 2026.03.13 (#16177); no PO token; embeddable videos only

`player_js_version=actual` — stopgap from PR #14693; prevents yt-dlp from requesting
nsig-protected formats that Android's JS engine cannot decode.

Do NOT use `--rm-cache-dir` in download — confirmed irrelevant to 403 fixes.
Do NOT use STABLE update channel — use NIGHTLY for faster YouTube fix pickup.

```kotlin
// YtDlpEngine.kt — BOTH getFormats AND download must have this exact block:
val isYouTube = url.contains("youtube.com") || url.contains("youtu.be")

// RULE 1: Never override --user-agent for YouTube
if (!isYouTube) {
    addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) ...")
}

// RULE 2: Always force PO-token-free clients for YouTube
if (isYouTube) {
    addOption("--extractor-args", "youtube:player_client=tv,android_vr,web_embedded;player_js_version=actual")
}

// RULE 3: All updateYoutubeDL calls must use NIGHTLY channel
YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.NIGHTLY)
```

**Sources:** #14456 (tv_simply dead), #14680 (player_js_version=actual), #14693 (PR stopgap),
#15012 (JS runtime), #16155 (android client dead), #16168 (android_vr fix), #16177 (web_embedded fix)

### What was tried (and failed)
| Setting | Result |
|---|---|
| `player_client=web` | 180p — SABR only |
| `player_client=tv_embedded,android,mweb` | 360p max |
| No override + `--user-agent Chrome` | 403 (UA mismatch) |
| No override + `player_client=ios,web_safari` | 403 (PO token required) |
| `player_client=tv,android_vr,web_embedded` + `player_js_version=actual` + NIGHTLY ✅ | Works |

### Client Quick-Reference (2026)
| Client | Quality | PO Token | Status |
|---|---|---|---|
| `tv` | DASH 1080p | **No** | ✅ Primary for download |
| `android_vr` | DASH 1080p–4K | **No** | ✅ Fixed 2026.03.13 |
| `web_embedded` | DASH 1080p | **No** | ✅ Embeddable only |
| `ios` | DASH 1080p | Yes (GVS) | ❌ 403 without cookies |
| `web_safari` | HLS ~1080p | Yes (GVS) | ❌ 403 without cookies |
| `android` | — | — | ❌ Dead (#16155) |
| `tv_simply` | DASH | Yes (GVS) | ❌ Dead (#14456) |
| `web` | SABR → 360p | Yes (GVS) | ❌ SABR since late 2024 |

---

## yt-dlp Android Flags — Required

These flags must be present in **both** `getFormats` and `download`. Do not remove them.

### `--no-check-formats`
yt-dlp's `_check_formats()` writes temp files to `/tmp` which is read-only on Android.
Without this, format fetching crashes silently on many devices.
**Always include in both `getFormats` and `download`.**

### `--force-ipv4`
Prevents IPv6 routing issues that cause 403 on some Android networks/carriers.
**Always include in both `getFormats` and `download`.**

### `--paths temp:<cacheDir>` + `--cache-dir <cacheDir>`
Redirects all yt-dlp temp/cache writes to the app's writable internal cache directory.
Without this, yt-dlp tries to write to `/tmp` or `~/.cache`, both read-only on Android.

### `--continue` (download only)
Tells yt-dlp to resume `.part` files from `cacheDir` on WorkManager retry.
`cacheDir` is NOT cleared between retries — so yt-dlp picks up where it left off.
**Only in `download`, not `getFormats`.**

### `--windows-filenames` + `-o "%(title).100s.%(ext)s"` (download only)
Instagram/Facebook titles can contain 500+ character hashtag chains.
Android's EXT4 filesystem hard-crashes with `[Errno 2]` if a filename exceeds 255 bytes.
The `.100s` truncation + `--windows-filenames` prevents this.

### `--abort-on-error` — REMOVED from download
Was killing the entire job on a single fragment drop. Removed.
`--abort-on-unavailable-fragment` kept — never silently skip broken HLS fragments.

### Facebook "Cannot parse data"
Caused by outdated yt-dlp binary. Auto-retry with inline update:
```kotlin
if (msg.contains("Cannot parse") || msg.contains("please report")) {
    YoutubeDL.getInstance().updateYoutubeDL(context)
    // retry same request
}
```

### Auto-update at startup
`VeloApp.updateYtDlpAsync()` runs every launch on the NIGHTLY channel — keeps binary current
so extractors stay in sync with YouTube/Facebook API changes.

---

## DownloadWorker — Critical Patterns

### CancellationException MUST be re-thrown
```kotlin
catch (e: Exception) {
    // MUST come first — do not catch as failure
    if (e is kotlinx.coroutines.CancellationException) {
        NotificationManagerCompat.from(context).cancel(notifId)
        throw e  // re-throw — WorkManager uses this for structured cancellation
    }
    // ... rest of error handling
}
```
**Why:** WorkManager signals cancellation via `CancellationException`. Catching it as a
failure marks the download FAILED in the DB and logs a bogus error.

### Stable notifId (no duplicate notifications on retry)
```kotlin
val notifId = recordId.hashCode()  // NOT System.currentTimeMillis().toInt()
```
`recordId` is a UUID set once at enqueue time and passed through all WorkManager retries via
`inputData`. Using a stable hash means `NotificationManagerCompat.notify()` UPDATES the
existing notification instead of posting a new one each retry.

### WorkManager tag = recordId
```kotlin
OneTimeWorkRequestBuilder<DownloadWorker>()
    .addTag(persistentUUID)  // same as record.id — enables cancelAllWorkByTag(record.id)
```
This is how both in-app cancel and notification cancel action target the right worker.

### Network drop → retry, not failure
```kotlin
val isNetworkDrop = isNetworkException(e) || run {
    val msg = e.message?.lowercase() ?: ""
    msg.contains("timeout") || msg.contains("resolve host") ||
    msg.contains("unreachable") || msg.contains("reset by peer") ||
    msg.contains("eof") || msg.contains("failed to connect") ||
    msg.contains("hostname") || msg.contains("no address")
}
if (isNetworkDrop && runAttemptCount < 5) return Result.retry()
```
`NetworkType.CONNECTED` constraint on the work request means retry waits for connectivity.

### Failed downloads auto-delete from DB
On permanent failure (non-network, after all retries): `dao.deleteById(record.id)`.
Do NOT set `status = FAILED` — the record disappears from the UI automatically.
The error notification still shows so the user knows what happened.

### DownloadRecord.progress is 0f–1f (NOT 0–100)
Progress is stored as a fraction (e.g. `0.75f` = 75%). Display with:
```kotlin
"${(record.progress * 100).toInt()}%"         // text badge
record.progress.coerceIn(0f, 1f)              // LinearProgressIndicator
```
Do NOT divide by 100 again. Do NOT call `.toInt()` without multiplying first.

---

## ExoPlayer Inline Player — Architecture

The inline player in `DownloadsScreen.kt` uses a custom Compose overlay instead of ExoPlayer's
built-in controller (`useController = false`). Do not revert to `useController = true`.

### Layout structure
```
Card
└── Column
    ├── [thumbnail / info row]  ← card header, always visible
    ├── [progress bar]          ← only when DOWNLOADING
    └── [player Box]            ← only when isPlaying == true
        ├── AndroidView(PlayerView)    ← video surface, fills Box
        └── Box overlay (alpha fade)  ← custom controls
            ├── IconButton(Fullscreen) ← top-right, video only
            ├── Row(rewind/play/fwd)   ← center
            └── Slider                ← bottom, no time labels
```

### Rules
- **`useController = false`** — always. Built-in controller is disabled; the Compose overlay
  is the only control surface.
- **Box width = `fillMaxWidth`, height = `aspectRatio(16f / 9f)`** — applies to BOTH video
  and audio. Audio has no visual track but uses the same 16:9 black canvas. Do not use a
  fixed `height(80.dp)` for audio.
- **Tap absorption** — the player `Box` uses `.clickable(indication=null)` to absorb taps.
  Without this, tapping the player surface propagates to the parent `Card` click and closes
  the player.
- **Controls auto-hide** — `LaunchedEffect(controlsVisible, isSeeking)` hides after 3 s.
  Scrubbing (isSeeking = true) keeps controls pinned visible.
- **Fullscreen** — opens a `Dialog` with `fillMaxSize` + `systemBars` hidden via
  `WindowInsetsControllerCompat`. Exit fullscreen dismisses the dialog (does not stop player).
- **Background pause** — `LifecycleEventObserver ON_PAUSE` → `exoPlayer.pause()`.
  There is no background audio/video playback; this is intentional.
- **Player lifecycle** — `DisposableEffect(record.filePath) { onDispose { exoPlayer.release() } }`.
  Player is released when the item leaves composition (user scrolls away or closes player).
- **Progress polling** — `LaunchedEffect(exoPlayer)` polls `currentPosition`/`duration` every
  250 ms. This drives the seekbar. No `Player.Listener` needed for position tracking.

### Controls state machine
```
isPlaying = false  →  player section not rendered at all
isPlaying = true   →  player section rendered
  controlsVisible = true   →  overlay alpha = 1f (visible)
    isSeeking = true   →  auto-hide timer suspended (controls stay)
    isSeeking = false  →  3 s timer → controlsVisible = false
  controlsVisible = false  →  overlay alpha = 0f (hidden)
  tap on surface  →  controlsVisible = !controlsVisible
```

---

## Clipboard Detection — HomeScreen

Clipboard is checked via four detection paths — each covers a different scenario.
Do NOT remove any of them.

| Path | Trigger | Covers |
|---|---|---|
| `LaunchedEffect(Unit)` | First composition | App launched fresh or screen first drawn |
| `DisposableEffect(lifecycle)` + `ON_RESUME` | NavBackStackEntry resumes | Returning from Downloads/Accounts screen |
| `DisposableEffect(view)` + `OnWindowFocusChangeListener` | Window gains focus | **Returning from another app (YouTube, browser, etc.)** |
| `DisposableEffect(clipboardManager)` + `OnPrimaryClipChangedListener` | Clipboard changes | Copying while HomeScreen is already in foreground |

**Why `ON_RESUME` alone is not enough for returning from other apps:**
`LocalLifecycleOwner` inside a `NavHost` destination is the `NavBackStackEntry`, not the
Activity. When the Activity returns from background, the `ON_RESUME` event may not propagate
reliably through the NavBackStackEntry on all devices/Android versions. Window focus gain
(`onWindowFocusChanged(hasFocus=true)`) is a direct Activity callback that is always guaranteed
to fire when the Activity's window comes back to the top.

```kotlin
// HomeScreen.kt — all four must exist together:
LaunchedEffect(Unit) { viewModel.onResume() }

val lifecycle = LocalLifecycleOwner.current.lifecycle
DisposableEffect(lifecycle) {
    val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume()
    }
    lifecycle.addObserver(observer)
    onDispose { lifecycle.removeObserver(observer) }
}

val view = LocalView.current
DisposableEffect(view) {
    val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
        if (hasFocus) viewModel.onResume()
    }
    view.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
    onDispose {
        if (view.viewTreeObserver.isAlive) {
            view.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
        }
    }
}

val clipboardManager = LocalContext.current.getSystemService(...) as ClipboardManager
DisposableEffect(clipboardManager) {
    val listener = ClipboardManager.OnPrimaryClipChangedListener { viewModel.onResume() }
    clipboardManager.addPrimaryClipChangedListener(listener)
    onDispose { clipboardManager.removePrimaryClipChangedListener(listener) }
}
```

`ClipboardWatcher` uses `SharedPreferences` to track the last seen clipboard text so the same
URL is not shown twice even when multiple paths fire at once. `reset()` clears this so a
re-copy of the same URL is re-detected.

### ProcessLifecycleOwner — property declaration order matters (NPE trap)

`addObserver()` dispatches the **current lifecycle state synchronously** to the new observer.
If `init` calls `addObserver` before the StateFlow properties are initialized, `onResume()`
will crash with `NullPointerException` on `_clipboardUrl.value = ...`.

**Rule:** In `HomeViewModel`, declare ALL `MutableStateFlow` properties BEFORE the
`appForegroundObserver` property and its `init` block. Kotlin initializes fields in
declaration order — the observer must be last so all flows exist when `ON_START` fires.
