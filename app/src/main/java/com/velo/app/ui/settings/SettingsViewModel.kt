package com.velo.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.velo.app.settings.SettingsRepository
import com.velo.app.update.UpdateChecker
import com.velo.app.update.UpdateManager
import dagger.hilt.android.lifecycle.HiltViewModel
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

    fun downloadAndInstall(context: Context, downloadUrl: String) {
        viewModelScope.launch {
            _updateState.value = UpdateState.Downloading
            _downloadProgress.value = 0
            UpdateManager.downloadAndInstall(context, downloadUrl) { progress ->
                _downloadProgress.value = progress
            }
            _updateState.value = UpdateState.Idle
        }
    }

    fun dismissUpdate() {
        _updateState.value = UpdateState.Idle
    }
}
