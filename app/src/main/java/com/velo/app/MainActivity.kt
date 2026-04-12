package com.velo.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.velo.app.ui.downloads.DownloadsScreen
import com.velo.app.ui.home.HomeScreen
import com.velo.app.ui.accounts.AccountsScreen
import com.velo.app.ui.theme.VeloTheme
import com.velo.app.ui.theme.veloColors
import com.velo.app.system.AccessGateway
import dagger.hilt.android.AndroidEntryPoint

@androidx.compose.foundation.ExperimentalFoundationApi
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Adaptive refresh rate: hint the display to use the highest available
        // refresh rate (e.g. 120Hz) for smoother scrolling and animations.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes = window.attributes.also {
                it.preferredDisplayModeId = 0  // 0 = highest available on device
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            VeloTheme(darkTheme = isSystemInDarkTheme()) {
                AccessGateway {
                    val initialUrl = intent?.data?.toString() 
                        ?: intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                    VeloNavHost(
                        // Pass VIEW intent or PROCESS_TEXT intent URL if launched via deep link or text highlight
                        startUrl = initialUrl
                    )
                }
            }
        }
    }

    // Handle new intents when activity is already running (singleTop)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // The HomeViewModel's clipboard/URL watcher will pick this up
    }
}

// ── Navigation ───────────────────────────────────────────────────────────────
sealed class VeloRoute(val route: String) {
    object Home : VeloRoute("home")
    object Downloads : VeloRoute("downloads")
    object Accounts : VeloRoute("accounts")
}

@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
fun VeloNavHost(startUrl: String? = null) {
    val navController = rememberNavController()
    val colors = veloColors

    NavHost(
        navController = navController,
        startDestination = VeloRoute.Home.route,
        modifier = Modifier.fillMaxSize(),
        // Snappy, snappy transitions mimicking the native Android Settings app (225ms FastOutSlowIn)
        // Default Compose springs are notoriously mushy/laggy for predictive back.
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(225, easing = FastOutSlowInEasing)
            )
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(225, easing = FastOutSlowInEasing)
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(225, easing = FastOutSlowInEasing)
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(225, easing = FastOutSlowInEasing)
            )
        },
    ) {
        composable(VeloRoute.Home.route) {
            HomeScreen(
                initialUrl = startUrl,
                onNavigateToDownloads = { navController.navigate(VeloRoute.Downloads.route) },
                onNavigateToAccounts = { navController.navigate(VeloRoute.Accounts.route) },
            )
        }
        composable(VeloRoute.Downloads.route) {
            DownloadsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(VeloRoute.Accounts.route) {
            AccountsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
