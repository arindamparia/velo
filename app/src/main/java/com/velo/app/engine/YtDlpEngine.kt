package com.velo.app.engine

import com.velo.app.data.model.VideoFormat
import com.velo.app.data.model.VideoInfo
import com.velo.app.interceptor.SupportedSites
import com.velo.app.utils.Logger
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Engine that wraps youtubedl-android (yt-dlp) for:
 *  1. Fetching all available formats for a URL (used to populate quality picker)
 *  2. Downloading a specific format to device storage
 *
 * Uses yt-dlp subprocess under the hood — 1000+ sites supported on-device.
 */
@Singleton
class YtDlpEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    // ── Format Fetching ───────────────────────────────────────────────────────

    /**
     * Fetches all available formats for the given URL.
     * Runs on IO dispatcher. Typically takes 2–4s after pre-warm (vs 7–8s cold).
     */
    suspend fun getFormats(url: String): VideoInfo = withContext(Dispatchers.IO) {
        Logger.i("YtDlpEngine", "Fetching formats for: $url")

        val isYouTube = url.contains("youtube.com") || url.contains("youtu.be")

        val request = YoutubeDLRequest(url).apply {
            addOption("--dump-json")
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--socket-timeout", "30")
            addOption("--force-ipv4")
            addOption("--retries", "5")
            addOption("--fragment-retries", "10")
            addOption("--no-check-certificates")
            addOption("--geo-bypass")
            addOption("--match-filter", "!is_live")
            addOption("--compat-options", "no-youtube-channel-redirect")
            addOption("--no-write-comments")
            addOption("--no-write-playlist-metafiles")
            // Fix: yt-dlp's _check_formats() tries to create temp files in /tmp which is
            // read-only on Android. --no-check-formats skips that validation entirely.
            addOption("--no-check-formats")
            // Don't crash when a site has no downloadable formats (live, DRM, etc.)
            addOption("--ignore-no-formats-error")
            // Redirect yt-dlp's cache dir to the app's writable cache directory
            addOption("--paths", "temp:${context.cacheDir.absolutePath}")
            addOption("--cache-dir", context.cacheDir.absolutePath)

            // CRITICAL: Do NOT override --user-agent for YouTube.
            // Chrome UA causes yt-dlp to use the web client → SABR-only → 360p max.
            // For non-YouTube sites the desktop UA improves extraction on FB, IG, gated CDNs.
            if (!isYouTube) {
                addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
            }

            // Force PO-token-free clients for YouTube so full 1080p/4K format list is returned.
            // Without this, yt-dlp may fall back to clients that only return SABR (360p).
            if (isYouTube) {
                addOption("--extractor-args", "youtube:player_client=tv,android_vr,web_embedded;player_js_version=actual")
            }

            val fbCookies = File(context.getExternalFilesDir(null), "velo_cookies_facebook.txt")
            val igCookies = File(context.getExternalFilesDir(null), "velo_cookies_instagram.txt")
            val cookieFile = when {
                fbCookies.exists() && (url.contains("facebook.com") || url.contains("fb.watch")) -> fbCookies
                igCookies.exists() && url.contains("instagram.com") -> igCookies
                else -> null
            }
            cookieFile?.let { addOption("--cookies", it.absolutePath) }

        }

        try {
            val response = YoutubeDL.getInstance().execute(request)
            Logger.d("YtDlpEngine", "Format fetch complete. Parsing…")
            parseVideoInfo(url, JSONObject(response.out))
        } catch (e: Exception) {
            // "Cannot parse data / please report this issue" == outdated yt-dlp extractor.
            // Trigger an inline update and retry once before surfacing the error.
            val msg = e.message ?: ""
            val isExtractorBug = msg.contains("Cannot parse") ||
                msg.contains("please report") ||
                msg.contains("Unsupported URL") ||
                msg.contains("Unable to extract")
            if (isExtractorBug) {
                Logger.w("YtDlpEngine", "Extractor error — updating yt-dlp binary and retrying…")
                try {
                    YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.NIGHTLY)
                    val retryResponse = YoutubeDL.getInstance().execute(request)
                    Logger.d("YtDlpEngine", "Retry after update succeeded. Parsing…")
                    return@withContext parseVideoInfo(url, JSONObject(retryResponse.out))
                } catch (retryEx: Exception) {
                    Logger.e("YtDlpEngine", "Retry after update also failed", retryEx)
                    throw retryEx
                }
            }
            Logger.e("YtDlpEngine", "Failed to fetch formats", e)
            throw e
        }
    }


    private fun parseVideoInfo(url: String, json: JSONObject): VideoInfo {
        val title = json.optString("title", "untitled")
        val thumbnail = json.optString("thumbnail").takeIf { it.isNotBlank() }
        val duration = json.optInt("duration", 0).takeIf { it > 0 }
        val uploader = json.optString("uploader").takeIf { it.isNotBlank() }
        val formatsArray = json.optJSONArray("formats")

        val formats = mutableListOf<VideoFormat>()

        if (formatsArray != null) {
            for (i in 0 until formatsArray.length()) {
                val f = formatsArray.getJSONObject(i)
                val formatId = f.optString("format_id")
                val ext = f.optString("ext", "mp4")
                var height = f.optInt("height", 0)
                val resolution = f.optString("resolution", "").takeIf { it.isNotBlank() && it != "audio only" }
                
                // If native height integer is missing, explicitly intercept and parse dimensions manually (e.g., "1920x960" -> 960)
                if (height == 0 && resolution != null) {
                    val parsed = resolution.split("x", "X", "*").lastOrNull()?.trim()?.toIntOrNull()
                    if (parsed != null) height = parsed
                }
                
                val formatNote = f.optString("format_note").takeIf { it.isNotBlank() && it != "null" }
                
                val vcodec = f.optString("vcodec").takeIf { it != "none" }
                val acodec = f.optString("acodec").takeIf { it != "none" }
                val isAudioOnly = vcodec == null && acodec != null
                val isVideoOnly = vcodec != null && acodec == null
                
                val exactSize = f.optLong("filesize", 0L).takeIf { it > 0 }
                val approxSize = f.optLong("filesize_approx", 0L).takeIf { it > 0 }
                val filesize = exactSize ?: approxSize
                
                val fps = f.optDouble("fps", 0.0).toFloat().takeIf { it > 0 }
                val tbr = f.optDouble("tbr", 0.0).toFloat().takeIf { it > 0 }
                val streamUrl = f.optString("url").takeIf { it.isNotBlank() }

                // ALWAYS normalize video labels to standard "1080p" format using resolved height.
                // Never use raw "1920x1080" or CDN note strings — they bypass deduplication!
                val label = when {
                    isAudioOnly      -> buildAudioLabel(ext, tbr)
                    height > 0       -> buildVideoLabel(height, ext)
                    formatNote != null -> formatNote
                    else             -> formatId
                }

                formats.add(VideoFormat(
                    id = formatId,
                    label = label,
                    qualityHeight = height,
                    ext = ext,
                    fileSizeBytes = filesize,
                    isAudioOnly = isAudioOnly,
                    isVideoOnly = isVideoOnly,
                    vcodec = vcodec,
                    acodec = acodec,
                    fps = fps,
                    tbr = tbr,
                    url = streamUrl,
                ))
            }
        }

        // De-duplicate by quality label, keeping highest for each resolution
        val deduplicated = deduplicateFormats(formats)

        return VideoInfo(
            url = url,
            title = title,
            thumbnail = thumbnail,
            duration = duration,
            uploader = uploader,
            siteName = SupportedSites.siteName(url),
            formats = deduplicated,
        )
    }

    private fun buildVideoLabel(height: Int, ext: String): String = when (height) {
        2160 -> "4k"
        1440 -> "1440p"
        1080 -> "1080p"
        720  -> "720p"
        480  -> "480p"
        360  -> "360p"
        240  -> "240p"
        else -> "${height}p"
    }

    private fun buildAudioLabel(ext: String, tbr: Float?): String {
        val quality = tbr?.let { " · ${it.toInt()}kbps" } ?: ""
        return "audio$quality"
    }

    private fun deduplicateFormats(formats: List<VideoFormat>): List<VideoFormat> {
        val videoMap = linkedMapOf<String, VideoFormat>()
        val audioList = mutableListOf<VideoFormat>()

        for (f in formats) {
            if (f.isAudioOnly) {
                audioList.add(f)
            } else {
                val existing = videoMap[f.label]
                if (existing == null) {
                    videoMap[f.label] = f
                } else {
                    // AUDIO SAFETY RULE: Never create a fallback chain where one format has audio
                    // and the other doesn't. If yt-dlp falls through to the silent fallback, the
                    // user gets a video with no sound.
                    // → If one is pre-muxed (has audio) and other is video-only: keep ONLY the muxed one.
                    // → Only merge two formats into a fallback chain if they have the SAME isVideoOnly status.
                    val fMuxed = !f.isVideoOnly
                    val existingMuxed = !existing.isVideoOnly

                    val winner: VideoFormat = when {
                        fMuxed && !existingMuxed -> f            // f has audio, existing doesn't → keep f
                        !fMuxed && existingMuxed -> existing     // existing has audio, f doesn't → keep existing
                        else -> {
                            // Both same type — create fallback chain, prefer format WITH known file size as primary
                            val fHasSize = f.fileSizeBytes != null
                            val existingHasSize = existing.fileSizeBytes != null
                            val (primary, fallback) = when {
                                fHasSize && !existingHasSize -> f to existing
                                !fHasSize && existingHasSize -> existing to f
                                (f.fileSizeBytes ?: 0) >= (existing.fileSizeBytes ?: 0) -> f to existing
                                else -> existing to f
                            }
                            primary.copy(id = "${primary.id}/${fallback.id}")
                        }
                    }
                    videoMap[f.label] = winner
                }
            }
        }

        // Sort by resolution descending; sized formats naturally bubble up within same height due to merge logic
        val sortedVideo = videoMap.values.sortedByDescending { it.qualityHeight }
        val sortedAudio = audioList.sortedByDescending { it.tbr ?: 0f }

        return sortedVideo + sortedAudio
    }


    // ── Download ──────────────────────────────────────────────────────────────

    /**
     * Downloads the given URL using the specified format.
     * Reports progress 0.0–1.0 via [onProgress] callback.
     * Returns the path of the downloaded file.
     */
    suspend fun download(
        url: String,
        format: VideoFormat,
        outputDir: File,
        onProgress: (Float) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        outputDir.mkdirs()

        Logger.i("YtDlpEngine", "Kicking off deep backend engine execution for ${format.id} -> $url")
        
        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--socket-timeout", "30")

            // Global Failure Mitigation Suite
            addOption("--retries", "15")                    // Robustly tolerate standard HTTP socket connection drops
            addOption("--fragment-retries", "30")           // Robustly tolerate scattered HLS/m3u8 404 packet drops
            addOption("--retry-sleep", "1")                 // Sleep between retries on unstable connections
            addOption("--abort-on-unavailable-fragment")    // Never skip broken HLS fragments; force fail so WorkManager can retry
            addOption("--no-check-certificates")            // Fix SSL chain errors on older Android APIs
            addOption("--geo-bypass")                       // Spoof HTTP headers to bypass regional video blocks
            addOption("--force-ipv4")                       // Prevent IPv6 routing issues that can cause 403 on some networks

            // Fix: yt-dlp's _check_formats() tries to create temp files in /tmp which is
            // read-only on Android. --no-check-formats skips that validation entirely.
            addOption("--no-check-formats")
            // Redirect yt-dlp cache/temp to the app's writable cache directory
            addOption("--paths", "temp:${context.cacheDir.absolutePath}")
            addOption("--cache-dir", context.cacheDir.absolutePath)
            // Resume .part files left by a previous interrupted attempt.
            // On WorkManager retry, cacheDir is preserved so yt-dlp picks up where it left off.
            addOption("--continue")

            // Hard OS Crash Fix: Instagram/FB heavily spam hashtags as the raw title format.
            // If the raw filename crosses 255 bytes, Android's EXT4 native layer will hard-crash with [Errno 2].
            // This aggressively enforces a strict 100-character string hardcap directly inside the python parser slice
            // and forcefully sanitizes unsafe FAT32 characters (like slashes, colons, stars).
            addOption("--windows-filenames")
            addOption("-o", "${outputDir.absolutePath}/%(title).100s.%(ext)s")

            val isYouTube = url.contains("youtube.com") || url.contains("youtu.be")

            // CRITICAL: Do NOT override --user-agent for YouTube.
            // yt-dlp's android_vr/ios_downgraded clients set their own UA internally for innertube
            // API calls that generate signed CDN download URLs. Overriding with a desktop Chrome UA
            // causes a client/UA mismatch → YouTube CDN rejects the download with HTTP 403.
            // For non-YouTube sites (FB, IG, gated CDNs) the desktop Chrome UA helps extraction.
            if (!isYouTube) {
                addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
            }

            if (isYouTube) {
                // Clients confirmed to work WITHOUT PO tokens (as of yt-dlp 2026.03.13):
                //   tv           — most reliable; yt-dlp pins to player 9f4cc5e4; no PO token
                //   android_vr   — no PO token; fixed in 2026.03.13 (issue #16168)
                //   web_embedded — no PO token; embeddable videos only (issue #16177)
                //
                // Dead / PO-token-required clients to avoid:
                //   ios, web_safari, android, mweb, tv_simply → all now require GVS PO tokens
                //   web → SABR-only since late 2024
                //
                // player_js_version=actual — stopgap from PR #14693; prevents yt-dlp from
                // requesting nsig-protected formats that Duktape/jsinterp cannot decode.
                // Do NOT use --rm-cache-dir — irrelevant to 403 fixes, wastes nsig cache.
                addOption("--extractor-args", "youtube:player_client=tv,android_vr,web_embedded;player_js_version=actual")
            }
            
            // Inject per-platform session cookies if user authenticated via Accounts screen
            val fbCookies = File(context.getExternalFilesDir(null), "velo_cookies_facebook.txt")
            val igCookies = File(context.getExternalFilesDir(null), "velo_cookies_instagram.txt")
            val cookieFile = when {
                fbCookies.exists() && (url.contains("facebook.com") || url.contains("fb.watch")) -> fbCookies
                igCookies.exists() && url.contains("instagram.com") -> igCookies
                else -> null
            }
            cookieFile?.let { addOption("--cookies", it.absolutePath) }

            // CRITICAL SEEK FIX: Multi-threaded fragment downloading MUST be sandboxed to YouTube DASH streams.
            // On alternative HLS sites (like XHamster), native fragmenting destroys identical PTS streaming headers.
            if (isYouTube) {
                // 4 threads: fast enough for DASH, but safe from CDN rate-limit 403/429
                addOption("--concurrent-fragments", "4")
            } else {
                // Instead, securely route HLS chunking through the local ffmpeg instance. This is moderately slower 
                // but mathematically forces a sequential timeline map, completely curing the \"seek resets to 0:00\" bug!
                addOption("--hls-prefer-ffmpeg")
            }

            if (format.isAudioOnly) {
                addOption("-f", format.id)
                addOption("-x")
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", "0")
            } else if (format.isVideoOnly) {
                // DASH video-only stream — merge with best available audio.
                // Critical Fix: format.id may contain multiple fallback resolutions (e.g., "137/248").
                // If we do "137/248+bestaudio", yt-dlp's syntax parser evaluates `/` OR with lowest precedence:
                // "137" OR "248+bestaudio" -> Resulting in 137 downloading immediately stripped of all audio!
                // We must unroll the IDs and apply the audio modifiers distributively beforehand.
                val ids = format.id.split("/")
                val fallbacks = mutableListOf<String>()
                
                // Pass 1: Try every ID with m4a audio (cleanest native mux)
                ids.forEach { fallbacks.add("$it+bestaudio[ext=m4a]") }
                // Pass 2: Try every ID with any best audio format 
                ids.forEach { fallbacks.add("$it+bestaudio") }
                // Pass 3: Desperation terminal fallback — download just the video silently
                ids.forEach { fallbacks.add(it) }
                
                addOption("-f", fallbacks.joinToString("/"))

                // MKV is the only container that accepts ALL codec combos (AV1, VP9, H264 + Opus, AAC, MP3)
                // without requiring re-encoding. mp4 rejects VP9+Opus silently; MKV never does.
                addOption("--merge-output-format", "mkv")
                // Android natively plays MKV since API 21+
            } else {
                // Pre-muxed stream — raw download, no merge attempt.
                // We must NOT append +bestaudio here; it would cause double-audio or corrupt output.
                addOption("-f", format.id)
                if (!isYouTube) {
                    // SEEK FIX: Remux HLS .ts chunks into mp4 to fix PTS timestamps on non-YouTube sites.
                    addOption("--remux-video", "mp4")
                }
            }



            // Embed thumbnail in audio files
            if (format.isAudioOnly) {
                addOption("--embed-thumbnail")
                addOption("--add-metadata")
            }
        }

        var lastPath = ""

        // Extract the progress/path parsing callback once so it can be reused on retry
        val progressHandler: (Float, Long, String) -> Unit = { progress, _, line ->
            Logger.d("YtDlpEngine", "Raw Output Line: $line")
            onProgress(progress / 100f)
            // Extract output filename from various yt-dlp output log flavors
            if (line.contains("Destination:")) {
                lastPath = line.substringAfter("Destination:").trim()
            } else if (line.contains("Merging formats into")) {
                lastPath = line.substringAfter("into \"").substringBeforeLast("\"").trim()
            } else if (line.contains("has already been downloaded")) {
                lastPath = line.substringAfter("[download]").substringBefore("has already been downloaded").trim()
            }
        }

        try {
            YoutubeDL.getInstance().execute(request, null, progressHandler)
            Logger.i("YtDlpEngine", "Download completed.")
        } catch (e: Exception) {
            // Retryable errors: 403 (nsig/UA mismatch), extractor bugs (outdated binary)
            val msg = e.message ?: ""
            val isRetryable = msg.contains("403") || msg.contains("Forbidden") ||
                msg.contains("Cannot parse") || msg.contains("please report") ||
                msg.contains("Unable to extract")
            if (isRetryable) {
                Logger.w("YtDlpEngine", "Retryable error — updating yt-dlp binary and retrying: ${msg.take(100)}")
                try {
                    YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.NIGHTLY)
                    YoutubeDL.getInstance().execute(request, null, progressHandler)
                    Logger.i("YtDlpEngine", "Retry after yt-dlp update succeeded.")
                } catch (retryEx: Exception) {
                    Logger.e("YtDlpEngine", "Retry after update also failed", retryEx)
                    throw retryEx
                }
            } else {
                Logger.e("YtDlpEngine", "yt-dlp execution failed", e)
                throw e
            }
        }

        // Fallback: yt-dlp occasionally skips the "Destination:" line (e.g. already-downloaded,
        // remux-only). Scan the output directory for the most recently modified file.
        if (lastPath.isBlank()) {
            lastPath = outputDir.listFiles()
                ?.filter { it.isFile }
                ?.maxByOrNull { it.lastModified() }
                ?.absolutePath
                ?: throw Exception("Download completed but output file not found in ${outputDir.absolutePath}")
            Logger.w("YtDlpEngine", "lastPath resolved via directory scan: $lastPath")
        }

        lastPath
    }

    // ── yt-dlp self-update ────────────────────────────────────────────────────

    /**
     * Updates the bundled yt-dlp binary to the latest version.
     * Returns the new version string or status.
     */
    suspend fun updateYtDlp(): String = withContext(Dispatchers.IO) {
        YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.NIGHTLY).toString()
    }

    /**
     * Clears the internal yt-dlp directory to fix potential corruption.
     * Useful when updates fail or extraction errors persist.
     */
    suspend fun resetYtDlp() = withContext(Dispatchers.IO) {
        val ytdlDir = File(context.noBackupFilesDir, "youtubedl-android")
        if (ytdlDir.exists()) {
            ytdlDir.deleteRecursively()
        }
        // Re-initialize after deletion
        YoutubeDL.getInstance().init(context)
    }
}
