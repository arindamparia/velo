package com.velo.app.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager

/**
 * Receives the "Cancel" action from a download progress notification.
 * Cancels the WorkManager task (tagged with the record ID) and dismisses the notification.
 */
class CancelDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val recordId = intent.getStringExtra(EXTRA_RECORD_ID) ?: return
        val notifId  = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
        WorkManager.getInstance(context).cancelAllWorkByTag(recordId)
        if (notifId != -1) NotificationManagerCompat.from(context).cancel(notifId)
    }

    companion object {
        const val ACTION_CANCEL   = "com.velo.app.action.CANCEL_DOWNLOAD"
        const val EXTRA_RECORD_ID = "record_id"
        const val EXTRA_NOTIF_ID  = "notif_id"
    }
}
