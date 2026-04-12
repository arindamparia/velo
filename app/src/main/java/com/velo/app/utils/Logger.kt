package com.velo.app.utils

import android.util.Log

/**
 * Global static logging utility strictly mirroring standard Android Log syntax.
 * Errors and warnings are deduplicated — the same message is only ever logged once per session.
 */
object Logger {
    private const val GLOBAL_TAG = "VeloApp"
    private const val MAX_CACHE = 50

    // Stores fingerprints of already-seen errors/warnings to suppress duplicates
    private val seenErrors = LinkedHashSet<String>()

    fun d(tag: String, message: String) {
        Log.d("$GLOBAL_TAG:$tag", message)
    }

    fun i(tag: String, message: String) {
        Log.i("$GLOBAL_TAG:$tag", message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val fingerprint = "E|$tag|$message|${throwable?.javaClass?.simpleName}"
        if (!seenErrors.add(fingerprint)) return // duplicate — skip
        if (seenErrors.size > MAX_CACHE) seenErrors.iterator().apply { next(); remove() }
        if (throwable != null) Log.e("$GLOBAL_TAG:$tag", message, throwable)
        else Log.e("$GLOBAL_TAG:$tag", message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        val fingerprint = "W|$tag|$message|${throwable?.javaClass?.simpleName}"
        if (!seenErrors.add(fingerprint)) return // duplicate — skip
        if (seenErrors.size > MAX_CACHE) seenErrors.iterator().apply { next(); remove() }
        if (throwable != null) Log.w("$GLOBAL_TAG:$tag", message, throwable)
        else Log.w("$GLOBAL_TAG:$tag", message)
    }

    /** Reset deduplication cache — call this on user-triggered retries if needed. */
    fun clearCache() {
        seenErrors.clear()
    }
}
