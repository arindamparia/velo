package com.velo.app.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

object UpdateManager {

    private val client = OkHttpClient.Builder().build()

    suspend fun downloadAndInstall(
        context: Context,
        downloadUrl: String,
        onProgress: (Int) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(downloadUrl).build()
        client.newCall(request).execute().use { response ->
            val body = response.body ?: return@withContext
            val total = body.contentLength()
            val apkFile = File(context.cacheDir, "velo-update.apk")

            var downloaded = 0L
            apkFile.outputStream().use { out ->
                body.byteStream().use { inp ->
                    val buf = ByteArray(8192)
                    var n: Int
                    while (inp.read(buf).also { n = it } != -1) {
                        out.write(buf, 0, n)
                        downloaded += n
                        if (total > 0) {
                            onProgress(((downloaded * 100) / total).toInt())
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                triggerInstall(context, apkFile)
            }
        }
    }

    private fun triggerInstall(context: Context, apkFile: File) {
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
    }
}
