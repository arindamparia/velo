package com.velo.app.ui.downloads

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.velo.app.data.db.DownloadDao
import com.velo.app.data.model.DownloadRecord
import com.velo.app.data.model.DownloadStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val dao: DownloadDao,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) : ViewModel() {

    private val _allDownloads: StateFlow<List<DownloadRecord>> = dao
        .getAllDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Search ────────────────────────────────────────────────────────────────
    val searchQuery = MutableStateFlow("")

    val downloads: StateFlow<List<DownloadRecord>> = combine(_allDownloads, searchQuery) { list, query ->
        if (query.isBlank()) list
        else list.filter { it.title.contains(query, ignoreCase = true) || it.siteName.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Storage usage ─────────────────────────────────────────────────────────
    // Query the DB directly so the total is accurate even when getAllDownloads() is limited to 500.
    val totalStorageBytes: StateFlow<Long> = dao.getTotalStorageBytes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    // ── Active download count (for queue cap) ─────────────────────────────────
    fun activeDownloadCount(): Int = _allDownloads.value.count {
        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
    }

    private var lastPruneMs = 0L
    private val PRUNE_COOLDOWN_MS = 5 * 60 * 1000L // 5 minutes

    /**
     * Verifies physical files still exist and drops DB records for any that are missing.
     * Throttled to run at most once every 5 minutes to avoid repeated file-system scans.
     */
    fun pruneMissingFiles() {
        val now = System.currentTimeMillis()
        if (now - lastPruneMs < PRUNE_COOLDOWN_MS) return
        lastPruneMs = now
        viewModelScope.launch(Dispatchers.IO) {
            val currentList = downloads.value
            for (record in currentList) {
                if (record.status == com.velo.app.data.model.DownloadStatus.DONE) {
                    val p = record.filePath
                    if (p != null && !p.startsWith("content://")) {
                        if (!File(p).exists()) {
                            dao.deleteById(record.id)
                        }
                    } else if (p != null && p.startsWith("content://")) {
                        var actuallyExists = true
                        try {
                            context.contentResolver.query(Uri.parse(p), arrayOf(android.provider.MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val absolutePath = cursor.getString(0)
                                    if (absolutePath != null && !File(absolutePath).exists()) {
                                        actuallyExists = false
                                    }
                                }
                            }
                        } catch (e: Exception) {}
                        if (!actuallyExists) {
                            dao.deleteById(record.id)
                        }
                    }
                }
                clearSandboxCache(context)
            }
        }
    }

    private fun clearSandboxCache(context: Context) {
        try {
            val appSandbox = File(context.getExternalFilesDir(null), "Velo Downloads")
            if (appSandbox.exists()) {
                val sweptFiles = appSandbox.listFiles()?.count { it.delete() } ?: 0
                com.velo.app.utils.Logger.i("DownloadsViewModel", "Vigorously sanitized sandbox application memory. Removed $sweptFiles broken cache fragments.")
            }
        } catch (e: Exception) {
            com.velo.app.utils.Logger.e("DownloadsViewModel", "Critical failure wiping internal app sandbox directory natively.", e)
        }
    }

    /**
     * The Broom Sweeper functionality. Deletes all FAILED downloads from the DB
     * and their associated fragmentary payload structures, then runs the standard missing prune.
     */
    fun sweepBroom(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentList = downloads.value
            for (record in currentList) {
                if (record.status == com.velo.app.data.model.DownloadStatus.FAILED) {
                    deleteRecord(context, record)
                }
            }
            pruneMissingFiles()
        }
    }

    /**
     * Cancels an in-progress or queued download. Removes the DB record immediately
     * and cancels the associated WorkManager task (tagged with record.id).
     */
    fun cancelDownload(record: DownloadRecord) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { dao.deleteById(record.id) }
            WorkManager.getInstance(context).cancelAllWorkByTag(record.id)
        }
    }

    fun deleteRecord(context: Context, record: DownloadRecord) {
        viewModelScope.launch {
            // Cancel WorkManager task if the download is still active
            if (record.status == DownloadStatus.DOWNLOADING || record.status == DownloadStatus.QUEUED) {
                WorkManager.getInstance(context).cancelAllWorkByTag(record.id)
            }
            withContext(Dispatchers.IO) {
                // 1. Unconditionally delete from our internal tracking DB first so UI updates instantly
                dao.deleteById(record.id)

                // 2. Attempt to gracefully catch and delete physical files if they still exist
                record.filePath?.let { path ->
                    try {
                        if (path.startsWith("content://")) {
                            val uri = Uri.parse(path)
                            var absolutePath: String? = null
                            context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    absolutePath = cursor.getString(0)
                                }
                            }
                            context.contentResolver.delete(uri, null, null)
                            absolutePath?.let { p ->
                                val f = File(p)
                                if (f.exists()) f.delete()
                            }
                        } else {
                            val file = File(path)
                            if (file.exists()) file.delete()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                // Aggressively wipe app memory sandbox cache
                clearSandboxCache(context)
            }
        }
    }
    fun deleteAll(context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val snapshot = downloads.value

                // Phase 1: delete all physical files. Failures are logged but don't abort.
                snapshot.forEach { record ->
                    try {
                        record.filePath?.let { path ->
                            if (path.startsWith("content://")) {
                                val uri = Uri.parse(path)
                                var absolutePath: String? = null
                                context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                                    if (cursor.moveToFirst()) absolutePath = cursor.getString(0)
                                }
                                context.contentResolver.delete(uri, null, null)
                                absolutePath?.let { p -> val f = File(p); if (f.exists()) f.delete() }
                            } else {
                                val f = File(path)
                                if (f.exists()) f.delete()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Phase 2: single batch delete — runs only after all file deletions have been attempted.
                dao.deleteAllRecords()
                clearSandboxCache(context)
            }
        }
    }

    private val retryingIds = mutableSetOf<String>()

    /**
     * Instantly resuscitates a dynamically failed download using identical parameters,
     * quietly deleting the dead ghost record from the screen and seamlessly triggering a clean run!
     */
    fun retryDownload(record: DownloadRecord) {
        if (!retryingIds.add(record.id)) return // Block UI spam click natively

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.deleteById(record.id)
            }
            
            val isAudio = record.ext == "mp3" || record.ext == "m4a" || (record.vcodec == null && record.acodec != null)
            val vFormat = com.velo.app.data.model.VideoFormat(
                id = record.formatId,
                label = record.formatLabel,
                qualityHeight = 0, // immaterial for retry
                ext = record.ext,
                fileSizeBytes = null,
                isAudioOnly = isAudio,
                isVideoOnly = !isAudio && record.acodec == null,
                vcodec = record.vcodec,
                acodec = record.acodec,
                fps = null,
                tbr = null
            )
            
            com.velo.app.worker.DownloadWorker.enqueue(
                context = context,
                url = record.url,
                format = vFormat,
                title = record.title,
                thumbnail = record.thumbnail
            )
        }
    }
}
