package com.velo.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

object UpdateManager {

    private val client = OkHttpClient.Builder().build()

    fun cachedApk(context: Context): File = File(context.cacheDir, "velo-update.apk")

    /** Returns true if install was launched, false if the user must first grant install permission. */
    suspend fun downloadAndInstall(
        context: Context,
        downloadUrl: String,
        onProgress: (Int) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        val apkFile = cachedApk(context)

        // Skip download if we already have the file cached
        if (!apkFile.exists()) {
            val request = Request.Builder().url(downloadUrl).build()
            client.newCall(request).execute().use { response ->
                val body = response.body ?: return@withContext false
                val total = body.contentLength()
                var downloaded = 0L
                apkFile.outputStream().use { out ->
                    body.byteStream().use { inp ->
                        val buf = ByteArray(8192)
                        var n: Int
                        while (inp.read(buf).also { n = it } != -1) {
                            out.write(buf, 0, n)
                            downloaded += n
                            if (total > 0) onProgress(((downloaded * 100) / total).toInt())
                        }
                    }
                }
            }
        }

        withContext(Dispatchers.Main) {
            triggerInstall(context, apkFile)
        }
    }

    /**
     * Returns true if the install intent was fired.
     * Returns false if the user needs to enable "Install unknown apps" — opens that settings screen.
     */
    private fun triggerInstall(context: Context, apkFile: File): Boolean {
        // Android 8+: check if the user has granted install-unknown-apps for this app
        if (!context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            return false
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }
}
