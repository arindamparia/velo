package com.velo.app.ui.share

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.velo.app.data.model.VideoFormat
import com.velo.app.data.model.VideoInfo
import com.velo.app.ui.theme.veloColors
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * The quality picker bottom sheet — the core UX of Velo.
 *
 * States:
 *  Loading → Loaded (Video tab / Audio tab with quality pills) → Done ✓
 *  Error → shows retry button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualityBottomSheetContent(
    url: String,
    viewModel: ShareViewModel = hiltViewModel(),
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = veloColors
    val sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)

    // Dynamic Accent
    val lower = url.lowercase()
    val targetAccent = when {
        lower.contains("youtube.com") || lower.contains("youtu.be") -> Color(0xFFE52D27)
        lower.contains("instagram.com") -> Color(0xFFE1306C)
        lower.contains("facebook.com") || lower.contains("fb.watch") -> Color(0xFF1877F2)
        lower.contains("twitter.com") || lower.contains("x.com") -> Color(0xFF1DA1F2)
        lower.contains("tiktok.com") -> Color(0xFF00F2FE)
        else -> colors.accent
    }
    val dynamicAccent by androidx.compose.animation.animateColorAsState(targetValue = targetAccent, label = "accentColor")
    val haptic = LocalHapticFeedback.current

    // Sheet always fills bottom, wrapped in a transparent Box
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss), // tap outside dismisses
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .clip(sheetShape)
                .background(colors.surface)
                .clickable(enabled = false) {} // prevent tap through
                .navigationBarsPadding()
        ) {
            AnimatedContent(
                targetState = state,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) },
                label = "sheet_state",
            ) { currentState ->
                when (currentState) {
                    is ShareViewModel.UiState.Idle,
                    is ShareViewModel.UiState.Loading -> LoadingState(
                        dynamicAccent = dynamicAccent,
                        haptic = haptic,
                        onAutoBest = { isAudio -> viewModel.queueAutoBestDownload(url, isAudio) }
                    )

                    is ShareViewModel.UiState.Loaded -> LoadedState(
                        info = currentState.info,
                        dynamicAccent = dynamicAccent,
                        haptic = haptic,
                        onSelectFormat = { format ->
                            viewModel.startDownload(currentState.info, format)
                        },
                        onDismiss = onDismiss,
                    )

                    is ShareViewModel.UiState.Downloading -> DownloadingState(
                        format = currentState.format,
                        progress = currentState.progress,
                    )

                    is ShareViewModel.UiState.Done -> DoneState()

                    is ShareViewModel.UiState.Error -> ErrorState(
                        message = currentState.message,
                        onRetry = { viewModel.loadFormats(url) },
                        onDismiss = onDismiss,
                    )
                }
            }
        }
    }
}

// ── Loading skeleton ─────────────────────────────────────────────────────────
@Composable
private fun LoadingState(
    dynamicAccent: Color, 
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback, 
    onAutoBest: (Boolean) -> Unit
) {
    val colors = veloColors
    
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "breathing")
    val breathingMulti by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween<Float>(800), 
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SheetHandle()
        Spacer(Modifier.height(24.dp))
        CircularProgressIndicator(
            strokeWidth = 2.dp,
            color = colors.textMuted,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "fetching available formats…",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(dynamicAccent.copy(alpha = 0.15f * breathingMulti))
                    .clickable { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAutoBest(false) 
                    }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "⚡ best video",
                    style = MaterialTheme.typography.labelMedium,
                    color = dynamicAccent,
                )
            }
            
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.text.copy(alpha = 0.1f * breathingMulti))
                    .clickable { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAutoBest(true) 
                    }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "🎧 best audio",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.text,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ── Loaded — quality picker ──────────────────────────────────────────────────
@Composable
private fun LoadedState(
    info: VideoInfo,
    dynamicAccent: Color,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onSelectFormat: (VideoFormat) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = veloColors
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("video", "audio")

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Header ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SheetHandle(modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, "close", tint = colors.textDim)
            }
        }

        // ── Thumbnail + Title ─────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (info.thumbnail != null) {
                AsyncImage(
                    model = info.thumbnail,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.elevated),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info.title.lowercase(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = info.siteName,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textDim,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Tab toggle: video / audio ─────────────────────────────────────────
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.elevated)
                .padding(3.dp),
        ) {
            tabs.forEachIndexed { index, label ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) colors.border else Color.Transparent)
                        .clickable { 
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedTab = index 
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) colors.text else colors.textMuted,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Best Quality quick button ─────────────────────────────────────────
        val bestFormat = if (selectedTab == 0) info.videoFormats.firstOrNull() else info.audioFormats.firstOrNull()
        if (bestFormat != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Button(
                    onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelectFormat(bestFormat) 
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = dynamicAccent.copy(alpha = 0.15f),
                        contentColor = dynamicAccent
                    ),
                ) {
                    Text(
                        text = "⚡ best quality — ${bestFormat.label}${bestFormat.fileSizeBytes?.let { " (${formatFileSize(it)})" } ?: ""}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // ── Quality grid ──────────────────────────────────────────────────────
        val formats = if (selectedTab == 0) info.videoFormats else info.audioFormats

        if (formats.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "no ${tabs[selectedTab]} formats found",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDim,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(formats) { format ->
                    FormatPill(
                        format = format, 
                        onClick = { 
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSelectFormat(format) 
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ── Format pill button ───────────────────────────────────────────────────────
private fun qualityTierColor(label: String): androidx.compose.ui.graphics.Color {
    val height = label.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
    return when {
        height >= 720 -> androidx.compose.ui.graphics.Color(0xFF4CAF50) // green — HD
        height >= 360 -> androidx.compose.ui.graphics.Color(0xFFFFC107) // amber — SD
        height > 0   -> androidx.compose.ui.graphics.Color(0xFFF44336) // red — low
        else         -> androidx.compose.ui.graphics.Color.Transparent
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> "%.1f gb".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000     -> "%.1f mb".format(bytes / 1_000_000.0)
        bytes >= 1_000         -> "%.0f kb".format(bytes / 1_000.0)
        else                   -> "$bytes b"
    }
}

@Composable
private fun FormatPill(format: VideoFormat, onClick: () -> Unit) {
    val colors = veloColors
    val tierColor = qualityTierColor(format.label)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
            .background(colors.elevated)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = format.label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                color = colors.text,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(3.dp))
            if (format.fileSizeBytes != null) {
                Text(
                    text = formatFileSize(format.fileSizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textDim,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    text = "size unknown",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textDim.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                )
            }
        }
        // Tier dot in top-right corner
        if (tierColor != androidx.compose.ui.graphics.Color.Transparent) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(tierColor)
                    .align(Alignment.TopEnd)
            )
        }
    }
}

// ── Downloading state ────────────────────────────────────────────────────────
@Composable
private fun DownloadingState(format: VideoFormat, progress: Float) {
    val colors = veloColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SheetHandle()
        Spacer(Modifier.height(24.dp))
        CircularProgressIndicator(
            progress = { progress },
            strokeWidth = 3.dp,
            color = colors.accent,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "downloading ${format.label}…",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
        Spacer(Modifier.height(24.dp))
    }
}

// ── Done state ───────────────────────────────────────────────────────────────
@Composable
private fun DoneState() {
    val colors = veloColors
    
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SheetHandle()
        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .scale(scale)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.success.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, "done", tint = colors.success, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text("download queued", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        Spacer(Modifier.height(24.dp))
    }
}

// ── Error state ───────────────────────────────────────────────────────────────
@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val colors = veloColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SheetHandle()
        Spacer(Modifier.height(20.dp))
        Text(
            text = message.lowercase(),
            style = MaterialTheme.typography.bodySmall,
            color = colors.error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(6.dp),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true),
            ) {
                Text("dismiss", color = colors.textMuted, style = MaterialTheme.typography.labelMedium)
            }
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.elevated),
            ) {
                Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(14.dp), tint = colors.text)
                Spacer(Modifier.width(6.dp))
                Text("retry", color = colors.text, style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ── Reusable handle bar ───────────────────────────────────────────────────────
@Composable
private fun SheetHandle(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(veloColors.border)
        )
    }
}
