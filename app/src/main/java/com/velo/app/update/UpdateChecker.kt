package com.velo.app.update

import com.velo.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object UpdateChecker {

    private const val RELEASES_URL =
        "https://api.github.com/repos/arindamparia/velo/releases/latest"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    data class UpdateInfo(
        val tagName: String,
        val downloadUrl: String,
        val changelog: String,
    )

    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(RELEASES_URL)
                .header("Accept", "application/vnd.github+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)

                val tag = json.optString("tag_name").ifEmpty { return@withContext null }
                val changelog = json.optString("body", "")

                val assets = json.optJSONArray("assets") ?: return@withContext null
                val downloadUrl = (0 until assets.length())
                    .map { assets.getJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(".apk") }
                    ?.optString("browser_download_url")
                    ?: return@withContext null

                val remoteVersion = tag.trimStart('v')
                if (isNewer(remoteVersion, BuildConfig.VERSION_NAME)) {
                    UpdateInfo(tag, downloadUrl, changelog)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun isNewer(remote: String, current: String): Boolean {
        val r = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, c.size)) {
            val ri = r.getOrElse(i) { 0 }
            val ci = c.getOrElse(i) { 0 }
            if (ri > ci) return true
            if (ri < ci) return false
        }
        return false
    }
}
