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

    fun installPermissionIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        )

    /** Returns true if install was launched, false if install permission is not yet granted. */
    suspend fun downloadAndInstall(
        context: Context,
        downloadUrl: String,
        onProgress: (Int) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        val apkFile = cachedApk(context)

        // Skip download if already cached
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

        withContext(Dispatchers.Main) { triggerInstall(context, apkFile) }
    }

    /** Returns true if the installer was launched, false if permission is missing. */
    fun triggerInstall(context: Context, apkFile: File = cachedApk(context)): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) return false

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        context.startActivity(Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return true
    }
}
