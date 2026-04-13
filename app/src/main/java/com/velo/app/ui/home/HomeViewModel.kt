package com.velo.app.ui.home

import android.content.Context
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.velo.app.data.model.VideoInfo
import com.velo.app.engine.YtDlpEngine
import com.velo.app.interceptor.ClipboardWatcher
import com.velo.app.interceptor.SupportedSites
import com.velo.app.worker.DownloadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.velo.app.data.db.DownloadDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val engine: YtDlpEngine,
    private val clipboardWatcher: ClipboardWatcher,
    private val dao: DownloadDao,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    // ── URL input field state ─────────────────────────────────────────────────
    val urlInput = MutableStateFlow("")

    // ── Clipboard banner ──────────────────────────────────────────────────────
    private val _clipboardUrl = MutableStateFlow<String?>(null)
    val clipboardUrl: StateFlow<String?> = _clipboardUrl.asStateFlow()

    // Observe the process lifecycle so clipboard is checked whenever the app comes to the
    // foreground — from any source (phone home screen, recents, lock screen, another app).
    // IMPORTANT: appForegroundObserver init block MUST come after all StateFlow properties
    // are declared. addObserver() immediately dispatches the current lifecycle state (ON_START)
    // to the observer synchronously — if _clipboardUrl isn't initialized yet, onResume() crashes.
    private val appForegroundObserver = LifecycleEventObserver { _, event ->
        if (event == androidx.lifecycle.Lifecycle.Event.ON_START) onResume()
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(appForegroundObserver)
    }

    override fun onCleared() {
        super.onCleared()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(appForegroundObserver)
    }

    // ── Format fetch / load state ─────────────────────────────────────────────
    sealed class LoadState {
        object Idle : LoadState()
        object Loading : LoadState()
        data class Loaded(val info: VideoInfo) : LoadState()
        object Done : LoadState()
        // loginRequired = true when the URL is FB/IG and yt-dlp signals auth is needed
        data class Error(val message: String, val loginRequired: Boolean = false) : LoadState()
    }

    private val _loadState = MutableStateFlow<LoadState>(LoadState.Idle)
    val loadState: StateFlow<LoadState> = _loadState.asStateFlow()

    // ── Called each time the home screen becomes visible ──────────────────────
    fun onResume() {
        when (val state = clipboardWatcher.checkForNewUrl()) {
            is com.velo.app.interceptor.ClipboardState.ValidUrl -> {
                _clipboardUrl.value = state.url
            }
            is com.velo.app.interceptor.ClipboardState.Invalid -> {
                // Clipboard empty or contains non-URL text
                _clipboardUrl.value = null
            }
            is com.velo.app.interceptor.ClipboardState.Unchanged -> {
                // Do nothing, preserving the existing banner state until explicitly dismissed
            }
        }
    }

    fun dismissClipboardBanner() {
        _clipboardUrl.value = null
    }

    fun onUrlChanged(value: String) {
        urlInput.value = value
        // Reset load state when URL changes
        if (_loadState.value !is LoadState.Idle) {
            _loadState.value = LoadState.Idle
        }
    }

    // ── Fetch formats for manual URL input ──────────────────────────────────
    private var currentFetchJob: kotlinx.coroutines.Job? = null

    fun fetchFormats(rawInput: String = urlInput.value) {
        val url = SupportedSites.extractAnyUrl(rawInput)
        if (url == null) {
            _loadState.value = LoadState.Error("this link isn't supported")
            return
        }
        currentFetchJob?.cancel()
        currentFetchJob = viewModelScope.launch {
            _loadState.value = LoadState.Loading
            try {
                val info = engine.getFormats(url)
                _loadState.value = LoadState.Loaded(info)
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    val msg = e.message ?: "couldn't fetch formats"
                    val isFbOrIg = url.contains("facebook.com") || url.contains("fb.watch") ||
                        url.contains("instagram.com")
                    val loginKeywords = listOf(
                        "login", "log in", "sign in", "private", "403",
                        "authentication", "not available", "credentials", "unavailable"
                    )
                    val loginRequired = isFbOrIg && loginKeywords.any { msg.contains(it, ignoreCase = true) }
                    _loadState.value = LoadState.Error(msg, loginRequired)
                }
            }
        }
    }

    fun queueDownload(info: VideoInfo, formatId: String) {
        val format = info.formats.find { it.id == formatId } ?: return
        viewModelScope.launch {
            val existingRecords = dao.getRecords(info.url, format.label)
            
            // Check if any existing record is currently downloading or completed perfectly
            val isCurrentlyActiveOrDone = existingRecords.any { 
                it.status == com.velo.app.data.model.DownloadStatus.DOWNLOADING || 
                it.status == com.velo.app.data.model.DownloadStatus.QUEUED || 
                it.status == com.velo.app.data.model.DownloadStatus.DONE 
            }
            
            if (isCurrentlyActiveOrDone) {
                _loadState.value = LoadState.Error("You already downloaded this ${format.label}")
                return@launch
            }
            
            // Wipe out old failed attempts for a clean restart
            if (existingRecords.isNotEmpty()) {
                dao.deleteRecords(info.url, format.label)
            }
            DownloadWorker.enqueue(context, info.url, format, info.title, info.thumbnail)
            _loadState.value = LoadState.Done
            kotlinx.coroutines.delay(1200)
            _loadState.value = LoadState.Idle
            urlInput.value = ""
        }
    }

    private var isQueueingAuto = false

    /**
     * Instantly queues a background worker to figure out the metadata and best format by itself.
     * Skips the blocking UI format fetch phase completely.
     */
    fun queueAutoBestDownload(rawInput: String, isAudio: Boolean = false) {
        if (isQueueingAuto) return
        
        val url = SupportedSites.extractAnyUrl(rawInput) ?: return
        
        isQueueingAuto = true
        currentFetchJob?.cancel()
        
        viewModelScope.launch {
            val autoFormat = com.velo.app.data.model.VideoFormat(
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
            
            _loadState.value = LoadState.Done
            kotlinx.coroutines.delay(1200)
            _loadState.value = LoadState.Idle
            urlInput.value = ""
            isQueueingAuto = false
        }
    }

    /**
     * Resets the yt-dlp binary and runs the update again.
     * Use if "unable to extract uploader id" or other python errors occur.
     */
    fun forceUpdateYtDlp() {
        viewModelScope.launch {
            _loadState.value = LoadState.Loading
            try {
                engine.resetYtDlp()
                val status = engine.updateYtDlp()
                _loadState.value = LoadState.Error("updated: $status")
            } catch (e: Exception) {
                _loadState.value = LoadState.Error("reset failed: ${e.message}")
            }
        }
    }
}
