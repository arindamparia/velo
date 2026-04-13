package com.velo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.velo.app.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    val backgroundAudioEnabled = settings.backgroundAudioEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val backgroundVideoEnabled = settings.backgroundVideoEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setBackgroundAudio(enabled: Boolean) {
        viewModelScope.launch { settings.setBackgroundAudio(enabled) }
    }

    fun setBackgroundVideo(enabled: Boolean) {
        viewModelScope.launch { settings.setBackgroundVideo(enabled) }
    }
}
