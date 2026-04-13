package com.velo.app.ui.downloads

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.velo.app.data.db.DownloadDao
import com.velo.app.data.model.DownloadRecord
import com.velo.app.data.model.DownloadStatus
import com.velo.app.settings.SettingsRepository
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
    private val settings: SettingsRepository,
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
    val totalStorageBytes: StateFlow<Long> = dao.getTotalStorageBytes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    // ── Background playback settings ──────────────────────────────────────────
    val backgroundAudioEnabled: StateFlow<Boolean> = settings.backgroundAudioEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val backgroundVideoEnabled: StateFlow<Boolean> = settings.backgroundVideoEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // ── Active download count (for queue cap) ─────────────────────────────────
    fun activeDownloadCount(): Int = _allDownloads.value.count {
        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
    }

    private var lastPruneMs = 0L
    private val PRUNE_COOLDOWN_MS = 5 * 60 * 1000L // 5 minutes

    /**
     * Verifies physical files still exist and drops DB records for any that are missing.
     * Throttled to run at most once every 5 minutes.
     *
     * IMPORTANT: clearSandboxCache is called ONCE after the loop, with protected paths from all
     * DONE records. Previously it was called inside the loop which wiped sandbox files right after
     * the existence check passed — causing completed downloads to vanish on the next prune cycle.
     */
    fun pruneMissingFiles() {
        val now = System.currentTimeMillis()
        if (now - lastPruneMs < PRUNE_COOLDOWN_MS) return
        lastPruneMs = now
        viewModelScope.launch(Dispatchers.IO) {
            val currentList = downloads.value

            // Collect file paths of DONE records stored in the sandbox (non-content:// paths).
            // These must be protected so clearSandboxCache doesn't delete user's completed files.
            val doneFilePaths = currentList
                .filter { it.status == DownloadStatus.DONE && it.filePath?.startsWith("content://") == false }
                .mapNotNull { it.filePath }
                .toSet()

            for (record in currentList) {
                if (record.status == DownloadStatus.DONE) {
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
            }

            // Clean up orphaned temp files in the sandbox — but never touch completed download files.
            clearSandboxCache(context, protectedPaths = doneFilePaths)
        }
    }

    /**
     * Deletes only orphaned temp files from the sandbox directory.
     * Files referenced by DONE records (protectedPaths) are never touched.
     */
    private fun clearSandboxCache(context: Context, protectedPaths: Set<String> = emptySet()) {
        try {
            val appSandbox = File(context.getExternalFilesDir(null), "Velo Downloads")
            if (appSandbox.exists()) {
                appSandbox.listFiles()?.forEach { file ->
                    if (file.absolutePath !in protectedPaths) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            com.velo.app.utils.Logger.e("DownloadsViewModel", "Failed to clear sandbox cache.", e)
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
                if (record.status == DownloadStatus.FAILED) {
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
            if (record.status == DownloadStatus.DOWNLOADING || record.status == DownloadStatus.QUEUED) {
                WorkManager.getInstance(context).cancelAllWorkByTag(record.id)
            }

            // Collect protected paths from other DONE records BEFORE modifying the DB.
            val protectedPaths = _allDownloads.value
                .filter { it.id != record.id && it.status == DownloadStatus.DONE && it.filePath?.startsWith("content://") == false }
                .mapNotNull { it.filePath }
                .toSet()

            withContext(Dispatchers.IO) {
                dao.deleteById(record.id)

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

                // Clean up sandbox orphans, protecting other users' completed downloads.
                clearSandboxCache(context, protectedPaths)
            }
        }
    }

    fun deleteAll(context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val snapshot = downloads.value

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

                dao.deleteAllRecords()
                // All records deleted — sandbox can be wiped completely.
                clearSandboxCache(context)
            }
        }
    }

    private val retryingIds = mutableSetOf<String>()

    fun retryDownload(record: DownloadRecord) {
        if (!retryingIds.add(record.id)) return

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.deleteById(record.id)
            }

            val isAudio = record.ext == "mp3" || record.ext == "m4a" || (record.vcodec == null && record.acodec != null)
            val vFormat = com.velo.app.data.model.VideoFormat(
                id = record.formatId,
                label = record.formatLabel,
                qualityHeight = 0,
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
