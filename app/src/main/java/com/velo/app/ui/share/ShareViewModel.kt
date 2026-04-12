package com.velo.app.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.velo.app.data.model.DownloadStatus
import com.velo.app.data.model.VideoFormat
import com.velo.app.data.model.VideoInfo
import com.velo.app.engine.YtDlpEngine
import com.velo.app.worker.DownloadWorker
import com.velo.app.utils.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.velo.app.data.db.DownloadDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ShareViewModel @Inject constructor(
    private val engine: YtDlpEngine,
    private val dao: DownloadDao,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class Loaded(val info: VideoInfo) : UiState()
        data class Downloading(val format: VideoFormat, val progress: Float) : UiState()
        object Done : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()
    
    private var currentFetchJob: kotlinx.coroutines.Job? = null

    fun loadFormats(url: String) {
        Logger.i("ShareViewModel", "User initiated Share Intent URL Format search: $url")
        currentFetchJob?.cancel()
        currentFetchJob = viewModelScope.launch {
            _state.emit(UiState.Loading)
            try {
                val info = engine.getFormats(url)
                _state.emit(UiState.Loaded(info))
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Logger.e("ShareViewModel", "Caught extraction failure inside ViewModel explicitly!", e)
                    _state.emit(UiState.Error(e.message ?: "couldn't fetch formats"))
                }
            }
        }
    }

    fun startDownload(info: VideoInfo, format: VideoFormat) {
        viewModelScope.launch {
            // Queue cap: max 3 active downloads at once
            val activeCount = withContext(Dispatchers.IO) {
                dao.getAllDownloads().first().count {
                    it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
                }
            }
            if (activeCount >= 3) {
                _state.value = UiState.Error("max 3 downloads at a time — please wait")
                return@launch
            }

            val existingRecords = dao.getRecords(info.url, format.label)
            
            // Check if any existing record is currently downloading or completed perfectly
            val isCurrentlyActiveOrDone = existingRecords.any { 
                it.status == DownloadStatus.DOWNLOADING || 
                it.status == DownloadStatus.QUEUED || 
                it.status == DownloadStatus.DONE 
            }
            
            if (isCurrentlyActiveOrDone) {
                _state.value = UiState.Error("You already downloaded this ${format.label}")
                return@launch
            }
            
            // If everything remaining is just FAILED entries, cleanly wipe them out so user can seamlessly restart tracking
            if (existingRecords.isNotEmpty()) {
                dao.deleteRecords(info.url, format.label)
            }
            // Queue via WorkManager so download survives app closure
            DownloadWorker.enqueue(context, info.url, format, info.title, info.thumbnail)
            _state.value = UiState.Done
        }
    }

    private var isQueueingAuto = false

    fun queueAutoBestDownload(url: String, isAudio: Boolean = false) {
        if (isQueueingAuto) return
        
        isQueueingAuto = true
        currentFetchJob?.cancel()
        
        viewModelScope.launch {
            val autoFormat = VideoFormat(
                id = if (isAudio) "AUTO_AUDIO" else "AUTO_VIDEO",
                label = if (isAudio) "best audio" else "best quality",
                qualityHeight = 0,
                ext = if (isAudio) "oops" else "mp4", // This placeholder doesn't matter, backend overwrites it
                fileSizeBytes = null,
                isAudioOnly = isAudio,
                isVideoOnly = false,
                vcodec = null,
                acodec = null,
                fps = null,
                tbr = null
            )
            
            DownloadWorker.enqueue(context, url, autoFormat, if (isAudio) "Fetching audio details..." else "Fetching video details...", null)
            _state.value = UiState.Done
            isQueueingAuto = false
        }
    }
}
