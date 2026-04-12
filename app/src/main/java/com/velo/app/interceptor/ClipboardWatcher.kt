package com.velo.app.interceptor

import android.content.ClipboardManager
import android.content.SharedPreferences
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks the system clipboard for supported video URLs.
 * Called from HomeViewModel.onResume() — always runs in foreground so
 * Android 12+ clipboard restrictions do not apply.
 */
@Singleton
class ClipboardWatcher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("velo_clipboard", Context.MODE_PRIVATE)

    private var lastCheckedText: String?
        get() = prefs.getString("last_text", null)
        set(value) {
            prefs.edit().putString("last_text", value).apply()
        }

    /**
     * Evaluates the system clipboard to determine if a new supported URL is present.
     */
    fun checkForNewUrl(): ClipboardState {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (!clipboard.hasPrimaryClip()) return ClipboardState.Invalid

        val text = clipboard.primaryClip
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            ?.trim()
            ?: return ClipboardState.Invalid

        // If the clipboard hasn't changed since the last check, do nothing.
        if (text == lastCheckedText) return ClipboardState.Unchanged

        // Update the last checked text so we don't process it again
        lastCheckedText = text

        val url = SupportedSites.extractUrl(text) ?: return ClipboardState.Invalid

        return ClipboardState.ValidUrl(url)
    }

    /**
     * Force-resets last checked state so the next call re-evaluates.
     * Useful when user dismisses the banner and then copies a new URL.
     */
    fun reset() { lastCheckedText = null }
}

sealed class ClipboardState {
    object Unchanged : ClipboardState()
    object Invalid : ClipboardState()
    data class ValidUrl(val url: String) : ClipboardState()
}
