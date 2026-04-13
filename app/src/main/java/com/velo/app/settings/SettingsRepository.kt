package com.velo.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val BACKGROUND_AUDIO = booleanPreferencesKey("background_audio_enabled")
        val BACKGROUND_VIDEO = booleanPreferencesKey("background_video_enabled")
    }

    val backgroundAudioEnabled: Flow<Boolean> = dataStore.data
        .map { it[Keys.BACKGROUND_AUDIO] ?: false }

    val backgroundVideoEnabled: Flow<Boolean> = dataStore.data
        .map { it[Keys.BACKGROUND_VIDEO] ?: false }

    suspend fun setBackgroundAudio(enabled: Boolean) {
        dataStore.edit { it[Keys.BACKGROUND_AUDIO] = enabled }
    }

    suspend fun setBackgroundVideo(enabled: Boolean) {
        dataStore.edit { it[Keys.BACKGROUND_VIDEO] = enabled }
    }
}
