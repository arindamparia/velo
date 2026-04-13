package com.velo.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.velo.app.utils.Logger
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class VeloApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        createNotificationChannels()
        initYtDlp()
        updateYtDlpAsync()
        startDaemonAsync()
    }

    // ── yt-dlp engine initialisation ─────────────────────────────────────────
    private fun initYtDlp() {
        // Initialize immediately on the main thread or a dedicated thread
        // to avoid "instance not initialized" when the user clicks download quickly.
        try {
            YoutubeDL.getInstance().init(applicationContext)
            FFmpeg.getInstance().init(applicationContext)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startDaemonAsync() {
        // The junkfood02 fork 0.18.x runs Python entirely through JNI (no subprocess),
        // so a persistent Python server is not possible. Instead, we pre-warm the
        // yt-dlp module cache on app launch:
        //   Cold call (flash): 6-8s  →  Pre-warm call: 1s  →  Subsequent calls: 2-3s
        // This works because Android's page cache keeps bytecode in RAM after first load.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val req = com.yausername.youtubedl_android.YoutubeDLRequest("--version")
                com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(req)
                Logger.i("VeloApp", "yt-dlp pre-warm complete ✓ — subsequent fetches will be faster")
            } catch (e: Exception) {
                Logger.w("VeloApp", "Pre-warm silently failed (non-critical): ${e.message}")
            }
        }
    }

    private fun updateYtDlpAsync() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val currentVersion = YoutubeDL.getInstance().version(applicationContext)
                Log.d("VeloApp", "Current yt-dlp version: $currentVersion")

                // NIGHTLY channel: picks up YouTube fixes within hours instead of weeks.
                // Critical for keeping tv/android_vr client nsig handling current.
                val status = YoutubeDL.getInstance().updateYoutubeDL(applicationContext, YoutubeDL.UpdateChannel.NIGHTLY)
                val newVersion = YoutubeDL.getInstance().version(applicationContext)
                Log.d("VeloApp", "Update status: $status")
                Log.d("VeloApp", "New yt-dlp version: $newVersion")
            } catch (e: Exception) {
                Log.e("VeloApp", "Failed to update yt-dlp", e)
            }
        }
    }

    // ── Notification channels ─────────────────────────────────────────────────
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val downloadChannel = NotificationChannel(
                CHANNEL_DOWNLOADS,
                getString(R.string.notif_channel_downloads),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_downloads_desc)
                setShowBadge(false)
            }

            manager.createNotificationChannel(downloadChannel)
        }
    }

    companion object {
        const val CHANNEL_DOWNLOADS = "velo_downloads"
    }
}
