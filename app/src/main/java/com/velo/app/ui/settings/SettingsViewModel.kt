package com.velo.app.ui.settings

import android.app.AppOpsManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.velo.app.settings.SettingsRepository
import com.velo.app.update.UpdateChecker
import com.velo.app.update.UpdateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class Available(val info: UpdateChecker.UpdateInfo) : UpdateState()
    object UpToDate : UpdateState()
    object Downloading : UpdateState()
    data class NeedsInstallPermission(val info: UpdateChecker.UpdateInfo) : UpdateState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    val backgroundAudioEnabled = settings.backgroundAudioEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val backgroundVideoEnabled = settings.backgroundVideoEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState = _updateState.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress = _downloadProgress.asStateFlow()

    private var appOpsListener: AppOpsManager.OnOpChangedListener? = null
    private var appOpsManager: AppOpsManager? = null

    fun setBackgroundAudio(enabled: Boolean) {
        viewModelScope.launch { settings.setBackgroundAudio(enabled) }
    }

    fun setBackgroundVideo(enabled: Boolean) {
        viewModelScope.launch { settings.setBackgroundVideo(enabled) }
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            val info = UpdateChecker.checkForUpdate()
            _updateState.value = if (info != null) UpdateState.Available(info) else UpdateState.UpToDate
        }
    }

    fun downloadAndInstall(context: Context, info: UpdateChecker.UpdateInfo) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            _updateState.value = UpdateState.Downloading
            _downloadProgress.value = 0
            val installed = UpdateManager.downloadAndInstall(appContext, info.downloadUrl) { progress ->
                _downloadProgress.value = progress
            }
            _updateState.value = if (installed) UpdateState.Idle
                                  else UpdateState.NeedsInstallPermission(info)
        }
    }

    /**
     * Opens the "Install unknown apps" settings page and registers an AppOps watcher.
     * The moment the user enables the toggle, the watcher fires and triggers the install
     * immediately — no back press needed.
     */
    fun openPermissionSettings(context: Context, info: UpdateChecker.UpdateInfo) {
        val appContext = context.applicationContext
        stopWatchingOps()

        val ops = appContext.getSystemService(AppOpsManager::class.java)
        appOpsManager = ops

        val listener = AppOpsManager.OnOpChangedListener { _, _ ->
            if (appContext.packageManager.canRequestPackageInstalls()) {
                stopWatchingOps()
                viewModelScope.launch(Dispatchers.Main) {
                    UpdateManager.triggerInstall(appContext)
                    _updateState.value = UpdateState.Idle
                }
            }
        }
        appOpsListener = listener
        ops.startWatchingMode(
            "android:request_install_packages",
            appContext.packageName,
            listener,
        )

        // Open the settings page
        appContext.startActivity(
            UpdateManager.installPermissionIntent(appContext).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    fun dismissUpdate() {
        stopWatchingOps()
        _updateState.value = UpdateState.Idle
    }

    private fun stopWatchingOps() {
        appOpsListener?.let { appOpsManager?.stopWatchingMode(it) }
        appOpsListener = null
        appOpsManager = null
    }

    override fun onCleared() {
        super.onCleared()
        stopWatchingOps()
    }
}
