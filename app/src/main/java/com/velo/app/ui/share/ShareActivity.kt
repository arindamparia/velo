package com.velo.app.ui.share

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import com.velo.app.interceptor.SupportedSites
import com.velo.app.system.AccessGateway
import com.velo.app.ui.theme.VeloTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Transparent activity launched when user taps "velo" in the Android sharesheet.
 *
 * Design intent:
 * - Uses Theme.Velo.BottomSheet → transparent background, dim behind it
 * - The main app is NEVER opened — user stays in original app
 * - Bottom sheet slides up, user picks quality, sheet dismisses
 * - Download queues silently in WorkManager
 */
@AndroidEntryPoint
class ShareActivity : ComponentActivity() {

    private val viewModel: ShareViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Extract URL from share intent text
        val sharedText = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val url = SupportedSites.extractUrl(sharedText)

        if (url == null) {
            // Unsupported URL — dismiss immediately
            finish()
            return
        }

        // Kick off format fetching immediately
        viewModel.loadFormats(url)

        setContent {
            VeloTheme(darkTheme = true) {
                AccessGateway {
                    QualityBottomSheetContent(
                        url = url,
                        viewModel = viewModel,
                        onDismiss = ::finish,
                    )
                }
            }

            // Auto-dismiss after download is queued
            val state = viewModel.state
            LaunchedEffect(Unit) {
                state.collect { s ->
                    if (s is ShareViewModel.UiState.Done) {
                        // Small delay so user sees success state
                        kotlinx.coroutines.delay(1200)
                        finish()
                    }
                }
            }
        }
    }
}
