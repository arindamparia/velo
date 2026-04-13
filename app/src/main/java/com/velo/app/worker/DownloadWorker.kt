package com.velo.app.worker

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.velo.app.MainActivity
import com.velo.app.R
import com.velo.app.VeloApp
import com.velo.app.data.db.DownloadDao
import com.velo.app.data.model.DownloadRecord
import com.velo.app.data.model.DownloadStatus
import com.velo.app.data.model.VideoFormat
import com.velo.app.engine.YtDlpEngine
import com.velo.app.interceptor.SupportedSites
import com.velo.app.utils.Logger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import javax.net.ssl.SSLException


/**
 * WorkManager worker that performs the actual download in the background.
 * Survives app closure. Shows a persistent notification with progress.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val engine: YtDlpEngine,
    private val dao: DownloadDao,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val url         = inputData.getString(KEY_URL)         ?: return Result.failure()
        
        Logger.i("DownloadWorker", "WorkManager spun up natively to parse download payload for: $url")
        
        var formatId    = inputData.getString(KEY_FORMAT_ID)   ?: return Result.failure()
        var formatLabel = inputData.getString(KEY_FORMAT_LABEL) ?: ""
        var title       = inputData.getString(KEY_TITLE)       ?: "video"
        var isAudio     = inputData.getBoolean(KEY_IS_AUDIO, false)
        var ext         = inputData.getString(KEY_EXT)         ?: "mp4"
        var vcodec      = inputData.getString(KEY_VCODEC)
        var acodec      = inputData.getString(KEY_ACODEC)
        var thumbnail   = inputData.getString(KEY_THUMBNAIL)

        // Extract recordId first — needed for notification cancel action and DB record.
        val recordId = inputData.getString(KEY_RECORD_ID) ?: UUID.randomUUID().toString()
        // Stable across retries: same recordId → same notifId → notify() UPDATES existing notification
        // instead of creating a new one each time WorkManager re-runs the worker.
        val notifId = recordId.hashCode()

        // Show initial notification
        showProgressNotification(notifId, recordId, title, formatLabel, 0)

        val outputDir = File(context.getExternalFilesDir(null), "Velo Downloads")
        
        var record = com.velo.app.data.model.DownloadRecord(
            id = recordId,
            url = url,
            title = title,
            siteName = SupportedSites.siteName(url),
            thumbnail = thumbnail,
            formatLabel = formatLabel,
            filePath = null,
            fileSizeBytes = null,
            formatId = formatId,
            ext = ext,
            vcodec = vcodec,
            acodec = acodec,
            status = com.velo.app.data.model.DownloadStatus.DOWNLOADING,
            timestampMs = System.currentTimeMillis()
        )
        // If WorkManager restarts the loop natively, this elegantly overwrites the existing row via Upsert natively safely!
        dao.insert(record)

        var progressScope: CoroutineScope? = null
        return try {
            var format = com.velo.app.data.model.VideoFormat(
                id = formatId,
                label = formatLabel,
                qualityHeight = 0,
                ext = ext,
                fileSizeBytes = null,
                isAudioOnly = isAudio,
                isVideoOnly = vcodec != null && acodec == null,
                vcodec = vcodec,
                acodec = acodec,
                fps = null,
                tbr = null,
            )

            // Intercept the AUTO zero-latency queue request natively. 
            // Resolve formats securely on the background thread without blocking UI!
            if (formatId == "AUTO" || formatId == "AUTO_VIDEO" || formatId == "AUTO_AUDIO") {
                Logger.i("DownloadWorker", "Intercepted AUTO queue. Resolving background metadata natively...")
                val info = engine.getFormats(url)
                val best = if (formatId == "AUTO_AUDIO") {
                    info.formats.firstOrNull { it.isAudioOnly } ?: throw Exception("audio stream not available")
                } else {
                    info.formats.firstOrNull { !it.isAudioOnly } ?: info.formats.firstOrNull() ?: throw Exception("Failed to resolve auto-best format")
                }
                
                formatId = best.id
                formatLabel = best.label
                title = info.title
                thumbnail = info.thumbnail
                isAudio = best.isAudioOnly
                ext = best.ext
                vcodec = best.vcodec
                acodec = best.acodec
                format = best
                
                record = record.copy(
                    title = title,
                    thumbnail = thumbnail,
                    formatLabel = formatLabel,
                    formatId = formatId,
                    ext = ext,
                    vcodec = vcodec,
                    acodec = acodec
                )
                dao.update(record)
            }

            var lastUpdateMs = 0L
            // Scope whose lifetime is tied to this worker's Job — cancelled in the finally block
            // below (success or failure) so no DB updates leak after the worker stops.
            progressScope = CoroutineScope(Dispatchers.IO + SupervisorJob(coroutineContext[kotlinx.coroutines.Job]))

            val progressCallback: (Float) -> Unit = { progress ->
                val now = System.currentTimeMillis()
                if (now - lastUpdateMs > 500) {
                    lastUpdateMs = now
                    progressScope.launch {
                        dao.update(record.copy(progress = progress))
                    }
                    showProgressNotification(notifId, recordId, title, formatLabel, (progress * 100).toInt())
                }
                setProgressAsync(workDataOf(KEY_PROGRESS to progress))
            }

            val filePath = engine.download(url, format, outputDir, progressCallback)

            val downloadedFile = File(filePath)
            val fileSize = downloadedFile.length() // capture before copyToMediaStore deletes the local file
            val publicPath = copyToMediaStore(context, downloadedFile, title, isAudio, ext)

            showCompletionNotification(notifId, title, formatLabel, publicPath)
            dao.update(record.copy(
                status = com.velo.app.data.model.DownloadStatus.DONE,
                filePath = publicPath,
                fileSizeBytes = fileSize.takeIf { it > 0 }
            ))
            Logger.i("DownloadWorker", "Download cycle completed natively natively safely. Saved to: $publicPath")
            Result.success(androidx.work.workDataOf(KEY_FILE_PATH to publicPath))
        } catch (e: Exception) {
            // CancellationException signals structured-concurrency cancellation (e.g. user
            // cancelled the download, WorkManager constraints not met). Must be re-thrown —
            // catching it as a failure would mark the download FAILED and log a bogus error.
            if (e is kotlinx.coroutines.CancellationException) {
                NotificationManagerCompat.from(context).cancel(notifId)
                throw e
            }

            // Check both the exception type and the full cause chain, because yt-dlp
            // wraps underlying Java network exceptions in its own exception type.
            val isNetworkDrop = isNetworkException(e) ||
                run {
                    // Fallback string check for yt-dlp messages that don't preserve the
                    // original exception type (e.g. subprocess stderr output).
                    val msg = e.message?.lowercase() ?: ""
                    msg.contains("timeout") || msg.contains("resolve host") ||
                    msg.contains("unreachable") || msg.contains("reset by peer") ||
                    msg.contains("eof") || msg.contains("failed to connect") ||
                    msg.contains("hostname") || msg.contains("no address")  // [Errno 7] DNS failure
                }

            if (isNetworkDrop && runAttemptCount < 5) {
                Logger.w("DownloadWorker", "Network socket dropped mid-flight! Pausing worker for Android OS auto-retry upon reconnection... ($runAttemptCount/5)")
                // Returning retry hooks into the existing NetworkType.CONNECTED WorkManager constraint
                // effectively freezing the job until the OS verifies an active internet connection.
                return Result.retry()
            }
                        
            Logger.e("DownloadWorker", "Worker failed instantly or during execution natively", e)
            showErrorNotification(notifId, title, e.message ?: "download failed")
            dao.deleteById(record.id)
            Result.failure(workDataOf("error" to e.message))
        } finally {
            progressScope?.cancel()
        }
    }

    // ── Notifications ──────────────────────────────────────────────────────

    private fun showProgressNotification(notifId: Int, recordId: String, title: String, format: String, progress: Int) {
        val cancelIntent = Intent(context, CancelDownloadReceiver::class.java).apply {
            action = CancelDownloadReceiver.ACTION_CANCEL
            putExtra(CancelDownloadReceiver.EXTRA_RECORD_ID, recordId)
            putExtra(CancelDownloadReceiver.EXTRA_NOTIF_ID, notifId)
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            notifId,
            cancelIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, VeloApp.CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_download_notif)
            .setContentTitle(title.lowercase())
            .setContentText("$format · $progress%")
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "cancel", cancelPendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notifId, notification)
    }

    private fun showCompletionNotification(notifId: Int, title: String, format: String, filePath: String) {
        // Build an intent that opens the downloaded file directly in the media player
        val fileUri = android.net.Uri.parse(filePath)
        val isAudio = format.startsWith("audio", ignoreCase = true)
        val mimeType = if (isAudio) "audio/*" else "video/*"
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val openIntent = PendingIntent.getActivity(
            context,
            notifId, // use notifId as request code to keep each notification's intent unique
            viewIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, VeloApp.CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_download_notif)
            .setContentTitle("download complete")
            .setContentText("${title.lowercase()} · $format")
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notifId, notification)
    }

    private fun showErrorNotification(notifId: Int, title: String, error: String) {
        val notification = NotificationCompat.Builder(context, VeloApp.CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_download_notif)
            .setContentTitle("download failed")
            .setContentText(title.lowercase())
            .setSubText(error.lowercase())
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(notifId, notification)
    }

    private suspend fun copyToMediaStore(context: Context, sourceFile: File, title: String, isAudio: Boolean, fallbackExt: String): String {
        return withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (isAudio) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                if (isAudio) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val mimeExt = sourceFile.extension.takeIf { it.isNotEmpty() }?.lowercase() ?: fallbackExt.lowercase()
            val mimeType = if (isAudio) "audio/$mimeExt" else "video/$mimeExt"

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, sourceFile.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.TITLE, title)
                put(MediaStore.MediaColumns.SIZE, sourceFile.length())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                    val dir = if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$dir/Velo")
                }
            }

            val uri = try {
                resolver.insert(collection, values) ?: return@withContext sourceFile.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext sourceFile.absolutePath
            }

            try {
                resolver.openOutputStream(uri)?.use { outStream ->
                    sourceFile.inputStream().use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }

                // Free up internal storage after migration
                sourceFile.delete()
                
                uri.toString()
            } catch (e: Exception) {
                e.printStackTrace()
                sourceFile.absolutePath
            }
        }
    }


    // ── Static helpers ─────────────────────────────────────────────────────

    /** Walks the full cause chain to detect network-related exceptions. */
    private fun isNetworkException(e: Throwable?): Boolean {
        if (e == null) return false
        return when (e) {
            is SocketTimeoutException,
            is UnknownHostException,
            is ConnectException,
            is SocketException,
            is SSLException -> true
            else -> isNetworkException(e.cause)
        }
    }

    companion object {
        const val KEY_URL           = "url"
        const val KEY_FORMAT_ID     = "format_id"
        const val KEY_FORMAT_LABEL  = "format_label"
        const val KEY_TITLE         = "title"
        const val KEY_THUMBNAIL     = "thumbnail"
        const val KEY_IS_AUDIO      = "is_audio"
        const val KEY_EXT           = "ext"
        const val KEY_VCODEC        = "vcodec"
        const val KEY_ACODEC        = "acodec"
        const val KEY_PROGRESS      = "progress"
        const val KEY_FILE_PATH     = "file_path"
        const val KEY_RECORD_ID     = "record_id"

        /**
         * Enqueues a download. Call from ViewModel/ShareViewModel.
         * The application context is accessed via WorkManager so no context needed.
         */
        fun enqueue(
            context: android.content.Context,
            url: String,
            format: VideoFormat,
            title: String,
            thumbnail: String?,
        ) {
            val persistentUUID = UUID.randomUUID().toString()
            
            val data = workDataOf(
                KEY_RECORD_ID    to persistentUUID,
                KEY_URL          to url,
                KEY_FORMAT_ID    to format.id,
                KEY_FORMAT_LABEL to format.label,
                KEY_TITLE        to title,
                KEY_THUMBNAIL    to thumbnail,
                KEY_IS_AUDIO     to format.isAudioOnly,
                KEY_EXT          to format.ext,
                KEY_VCODEC       to format.vcodec,
                KEY_ACODEC       to format.acodec,
            )

            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setId(UUID.randomUUID())
                .addTag(persistentUUID)   // allows cancelAllWorkByTag(record.id)
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
