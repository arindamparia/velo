package com.velo.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.velo.app.R

// ── IBM Plex Mono — Primary typeface (cobalt's identity font) ───────────────
// Falls back to system monospace if TTF files not yet placed in res/font/.
// To enable: place ibm_plex_mono_{light,regular,medium,bold}.ttf in res/font/
val IbmPlexMono: FontFamily = FontFamily.Monospace


// ── Typography Scale ────────────────────────────────────────────────────────
// Base: 14sp, Line height: 1.5x, all lowercase in UI copy
val VeloTypography = Typography(
    // Display / hero text
    displayLarge = TextStyle(
        fontFamily = IbmPlexMono,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
    ),
    // Section headings
    titleLarge = TextStyle(
        fontFamily = IbmPlexMono,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = IbmPlexMono,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    // Body / UI labels
    bodyLarge = TextStyle(
        fontFamily = IbmPlexMono,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = IbmPlexMono,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = IbmPlexMono,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),
    // Buttons / CTAs
    labelLarge = TextStyle(
        fontFamily = IbmPlexMono,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = IbmPlexMono,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = IbmPlexMono,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),
)
