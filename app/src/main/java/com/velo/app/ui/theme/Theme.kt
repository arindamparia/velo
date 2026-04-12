package com.velo.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Extended color tokens exposed via CompositionLocal ──────────────────────
data class VeloColors(
    val bg: Color,
    val surface: Color,
    val elevated: Color,
    val surfaceHigh: Color,
    val border: Color,
    val borderSubtle: Color,
    val text: Color,
    val textMuted: Color,
    val textDim: Color,
    val accent: Color,
    val error: Color,
    val success: Color,
)

val LocalVeloColors = staticCompositionLocalOf {
    VeloColorsDark
}

val VeloColorsDark = VeloColors(
    bg = VeloBgDark,
    surface = VeloSurfaceDark,
    elevated = VeloElevatedDark,
    surfaceHigh = VeloSurfaceHighDark,
    border = VeloBorderDark,
    borderSubtle = VeloBorderSubtleDark,
    text = VeloTextDark,
    textMuted = VeloTextMutedDark,
    textDim = VeloTextDimDark,
    accent = VeloAccent,
    error = VeloError,
    success = VeloSuccess,
)

val VeloColorsLight = VeloColors(
    bg = VeloBgLight,
    surface = VeloSurfaceLight,
    elevated = VeloElevatedLight,
    surfaceHigh = VeloBorderLight,
    border = VeloBorderLight,
    borderSubtle = Color(0xFFDEDEDE),
    text = VeloTextLight,
    textMuted = VeloTextMutedLight,
    textDim = Color(0xFFAAAAAA),
    accent = VeloAccent,
    error = VeloError,
    success = VeloSuccess,
)

// ── Static Material3 Schemes (fallback for Android < 12) ───────────────────
private val DarkColorScheme = darkColorScheme(
    primary = VeloAccent,
    onPrimary = VeloTextDark,
    primaryContainer = VeloSurfaceDark,
    background = VeloBgDark,
    onBackground = VeloTextDark,
    surface = VeloSurfaceDark,
    onSurface = VeloTextDark,
    surfaceVariant = VeloElevatedDark,
    onSurfaceVariant = VeloTextMutedDark,
    outline = VeloBorderDark,
    error = VeloError,
    onError = VeloTextDark,
)

private val LightColorScheme = lightColorScheme(
    primary = VeloAccent,
    onPrimary = Color.White,
    primaryContainer = VeloBgLight,
    background = VeloBgLight,
    onBackground = VeloTextLight,
    surface = VeloSurfaceLight,
    onSurface = VeloTextLight,
    surfaceVariant = VeloElevatedLight,
    onSurfaceVariant = VeloTextMutedLight,
    outline = VeloBorderLight,
    error = VeloError,
    onError = Color.White,
)

// ── Theme Composable ────────────────────────────────────────────────────────
@Composable
fun VeloTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    // ── VeloColors ───────────────────────────────────────────────────────────
    // Strictly adhere to our curated Cobalt tokens to maintain the pure aesthetic.
    // Wallpaper dynamic colors are intentionally ignored.
    val veloColors = if (darkTheme) VeloColorsDark else VeloColorsLight

    // ── Material 3 ColorScheme ────────────────────────────────────────────────
    // Construct the Material scheme using strictly our tokens.
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    // ── Edge-to-edge status/nav bar icons ────────────────────────────────────
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalVeloColors provides veloColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = VeloTypography,
            content = content,
        )
    }
}

// Convenience accessor
val veloColors: VeloColors
    @Composable get() = LocalVeloColors.current

