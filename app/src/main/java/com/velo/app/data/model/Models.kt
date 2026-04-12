package com.velo.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single downloadable format returned by yt-dlp for a URL.
 */
data class VideoFormat(
    val id: String,               // yt-dlp format_id
    val label: String,            // Human-readable: "1080p MP4", "Audio MP3"
    val qualityHeight: Int,       // 0 for audio-only
    val ext: String,              // "mp4", "webm", "m4a", "mp3"
    val fileSizeBytes: Long?,     // null = unknown
    val isAudioOnly: Boolean,
    val isVideoOnly: Boolean,     // true = needs merging with audio stream
    val vcodec: String?,
    val acodec: String?,
    val fps: Float?,
    val tbr: Float?,             // Total bitrate kbps
    val url: String? = null,      // Direct CDN media stream proxy URL (for ExoPlayer preview)
) {
    val fileSizeLabel: String get() = when {
        fileSizeBytes == null   -> ""
        fileSizeBytes < 1_000_000L -> "${fileSizeBytes / 1024} kb"
        else -> "${"%.1f".format(fileSizeBytes / 1_000_000.0)} mb"
    }
}

/**
 * Video info returned after fetching formats for a URL.
 */
data class VideoInfo(
    val url: String,
    val title: String,
    val thumbnail: String?,
    val duration: Int?,           // seconds
    val uploader: String?,
    val siteName: String,
    val formats: List<VideoFormat>,
) {
    /** Video formats: return all valid video streams, removing any filesize requirements */
    val videoFormats: List<VideoFormat> get() {
        return formats.filter { !it.isAudioOnly && !it.label.contains("storyboard", ignoreCase = true) }
    }
    
    /** Audio formats: return all valid audio streams, removing any filesize requirements */
    val audioFormats: List<VideoFormat> get() {
        return formats.filter { it.isAudioOnly }
    }
}

/**
 * A record of a download task, stored in Room database.
 */
@Entity(tableName = "downloads")
data class DownloadRecord(
    @PrimaryKey val id: String,           // UUID
    val url: String,
    val title: String,
    val thumbnail: String?,
    val formatLabel: String,
    val filePath: String?,
    val fileSizeBytes: Long?,
    val siteName: String,
    val formatId: String = "",       // Used specifically for Retry feature
    val ext: String = "mp4",         // Used specifically for Retry feature
    val vcodec: String? = null,      // Used specifically for Retry feature
    val acodec: String? = null,      // Used specifically for Retry feature
    val timestampMs: Long = System.currentTimeMillis(),
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
)

enum class DownloadStatus { QUEUED, DOWNLOADING, DONE, FAILED }
