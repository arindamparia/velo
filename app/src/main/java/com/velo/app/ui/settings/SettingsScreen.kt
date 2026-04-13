package com.velo.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.velo.app.ui.theme.veloColors
import java.io.File

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val colors = veloColors

    val backgroundAudioEnabled by viewModel.backgroundAudioEnabled.collectAsStateWithLifecycle()
    val backgroundVideoEnabled by viewModel.backgroundVideoEnabled.collectAsStateWithLifecycle()

    // Account auth status — refreshed every time this screen resumes (e.g. returning from Accounts)
    val facebookCookieFile = remember { File(context.filesDir, "velo_cookies_facebook.txt") }
    val instagramCookieFile = remember { File(context.filesDir, "velo_cookies_instagram.txt") }
    var facebookLoggedIn by remember { mutableStateOf(facebookCookieFile.exists()) }
    var instagramLoggedIn by remember { mutableStateOf(instagramCookieFile.exists()) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                facebookLoggedIn = facebookCookieFile.exists()
                instagramLoggedIn = instagramCookieFile.exists()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ── Top bar ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "back", tint = colors.text)
            }
            Text(
                text = "settings",
                style = MaterialTheme.typography.titleMedium,
                color = colors.text,
            )
        }

        HorizontalDivider(color = colors.border, thickness = 0.5.dp)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            // ── Account section ───────────────────────────────────────────────
            SettingsSectionHeader(title = "account")

            SettingsClickRow(
                title = "accounts",
                subtitle = buildAccountSubtitle(facebookLoggedIn, instagramLoggedIn),
                onClick = onNavigateToAccounts,
            )

            Spacer(Modifier.height(8.dp))

            // ── Playback section ──────────────────────────────────────────────
            SettingsSectionHeader(title = "playback")

            SettingsToggleRow(
                title = "background audio",
                subtitle = "audio keeps playing when you switch apps or lock the screen",
                checked = backgroundAudioEnabled,
                onCheckedChange = viewModel::setBackgroundAudio,
            )

            HorizontalDivider(
                color = colors.borderSubtle,
                thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 2.dp)
            )

            SettingsToggleRow(
                title = "background video",
                subtitle = "video keeps playing when you switch to another app",
                checked = backgroundVideoEnabled,
                onCheckedChange = viewModel::setBackgroundVideo,
            )
        }
    }
}

private fun buildAccountSubtitle(fbLoggedIn: Boolean, igLoggedIn: Boolean): String = when {
    fbLoggedIn && igLoggedIn -> "facebook · instagram connected"
    fbLoggedIn -> "facebook connected"
    igLoggedIn -> "instagram connected"
    else -> "log in for private content"
}

@Composable
private fun SettingsSectionHeader(title: String) {
    val colors = veloColors
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = colors.textDim,
        modifier = Modifier.padding(top = 24.dp, bottom = 6.dp),
    )
}

@Composable
private fun SettingsClickRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val colors = veloColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.text)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textDim,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = veloColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.text)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.bg,
                checkedTrackColor = colors.accent,
                uncheckedThumbColor = colors.textDim,
                uncheckedTrackColor = colors.elevated,
                uncheckedBorderColor = colors.border,
            ),
        )
    }
}
