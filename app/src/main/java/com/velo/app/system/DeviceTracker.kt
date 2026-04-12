package com.velo.app.system

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.velo.app.BuildConfig
import com.velo.app.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object DeviceTracker {

    // Endpoint is defined in build.gradle.kts defaultConfig as a BuildConfig field so it
    // is not hardcoded in source and can be overridden per build type (e.g. staging in debug).
    //
    // To add certificate pinning in production (recommended), uncomment and fill in the hash:
    //   .certificatePinner(
    //       CertificatePinner.Builder()
    //           .add("<hostname>", "sha256/<BASE64_HASH>")
    //           .build()
    //   )
    // Get the hash via: `openssl s_client -connect <host>:443 | openssl x509 -pubkey -noout |
    //   openssl pkey -pubin -outform DER | openssl dgst -sha256 -binary | base64`
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private const val PREFS_NAME = "velo_access_prefs"
    private const val KEY_IS_ACTIVE = "is_active"

    @SuppressLint("HardwareIds")
    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "UNKNOWN_ID"
    }

    private fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        
        return if (model.lowercase().startsWith(manufacturer.lowercase())) {
            model.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        } else {
            "${manufacturer.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }} $model"
        }
    }

    /**
     * Reads the cached `is_active` boolean gracefully. 
     * Defaults to true identically so offline users preserve their session organically.
     */
    fun isDeviceActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IS_ACTIVE, true)
    }

    /**
     * Overwrites the memory block securely when the DB pushes an update lock.
     */
    fun setDeviceStatus(context: Context, isActive: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_IS_ACTIVE, isActive).apply()
        Logger.d("DeviceTracker", "Saved local `is_active` gate cache: $isActive")
    }

    /**
     * POSTs device info to the verification endpoint.
     * Returns true if active, false if explicitly blocked, or null on network failure
     * (null = preserve cached value so offline users aren't locked out).
     */
    suspend fun verifyServerStatus(context: Context): Boolean? = withContext(Dispatchers.IO) {
        try {
            val deviceId = getDeviceId(context)
            Logger.i("DeviceTracker", "Initiating backend validation check. Device ID: $deviceId")

            val body = JSONObject().apply {
                put("device_id", deviceId)
                put("device_name", getDeviceName())
                put("os_version", Build.VERSION.RELEASE)
            }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(BuildConfig.NETLIFY_VERIFY_ENDPOINT)
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    val serverIsActive = json.optBoolean("is_active", true)
                    Logger.i("DeviceTracker", "Server replied: is_active=$serverIsActive")
                    serverIsActive
                } else {
                    Logger.w("DeviceTracker", "Non-2xx response from verification endpoint: ${response.code}")
                    null // Fallback to cache
                }
            }
        } catch (e: Exception) {
            Logger.e("DeviceTracker", "Verification request failed (offline or unreachable)", e)
            null // Network failure = preserve cached value
        }
    }
}
