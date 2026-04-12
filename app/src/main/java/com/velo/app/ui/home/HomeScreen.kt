package com.velo.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.velo.app.data.model.VideoInfo
import com.velo.app.ui.theme.veloColors
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalView

internal fun getDynamicAccentColor(url: String, defaultColor: Color): Color {
    val lower = url.lowercase()
    return when {
        lower.contains("youtube.com") || lower.contains("youtu.be") -> Color(0xFFE52D27)
        lower.contains("instagram.com") -> Color(0xFFE1306C)
        lower.contains("facebook.com") || lower.contains("fb.watch") -> Color(0xFF1877F2)
        lower.contains("twitter.com") || lower.contains("x.com") -> Color(0xFF1DA1F2)
        lower.contains("tiktok.com") -> Color(0xFF00F2FE)
        else -> defaultColor
    }
}

@Composable
fun HomeScreen(
    initialUrl: String? = null,
    onNavigateToDownloads: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val colors = veloColors
    val urlInput by viewModel.urlInput.collectAsStateWithLifecycle()
    val clipboardUrl by viewModel.clipboardUrl.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    
    val targetAccent = getDynamicAccentColor(urlInput, colors.accent)
    val dynamicAccent by animateColorAsState(targetValue = targetAccent, label = "accentColor")
    val haptic = LocalHapticFeedback.current

    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val breathingMulti by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Reverse),
        label = "alpha"
    )

    // If launched via deep link, pre-fill URL and fetch
    LaunchedEffect(initialUrl) {
        if (initialUrl != null) {
            viewModel.onUrlChanged(initialUrl)
            viewModel.fetchFormats(initialUrl)
        }
    }

    // ── Clipboard detection ────────────────────────────────────────────────
    // LaunchedEffect(Unit): runs on first composition — catches the initial open/launch
    // because DisposableEffect registers its observer AFTER ON_RESUME has already fired.
    LaunchedEffect(Unit) {
        viewModel.onResume()
    }
    // DisposableEffect: handles every subsequent ON_RESUME (e.g. returning from Downloads)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // Also detect clipboard changes while HomeScreen is already in the foreground
    val clipboardManager = LocalContext.current.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    DisposableEffect(clipboardManager) {
        val listener = android.content.ClipboardManager.OnPrimaryClipChangedListener {
            viewModel.onResume()
        }
        clipboardManager.addPrimaryClipChangedListener(listener)
        onDispose { clipboardManager.removePrimaryClipChangedListener(listener) }
    }
    // Window focus listener: most reliable signal for "user returned from another app".
    // LocalLifecycleOwner inside NavHost is the NavBackStackEntry — its ON_RESUME may not
    // propagate correctly when returning from a different app (only intra-app navigation).
    // Window focus gain is guaranteed when the Activity window comes back to the top.
    val view = LocalView.current
    DisposableEffect(view) {
        val focusListener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) viewModel.onResume()
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
        onDispose {
            if (view.viewTreeObserver.isAlive) {
                view.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // ── Top bar ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "velo",
                style = MaterialTheme.typography.titleLarge,
                color = colors.text,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNavigateToAccounts) {
                Icon(
                    Icons.Rounded.Person,
                    contentDescription = "accounts",
                    tint = colors.textMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
            TextButton(onClick = onNavigateToDownloads) {
                Text("downloads", color = colors.textMuted, style = MaterialTheme.typography.labelMedium)
            }
        }

        // ── Clipboard banner ────────────────────────────────────────────────
        AnimatedVisibility(
            visible = clipboardUrl != null,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            clipboardUrl?.let { url ->
                ClipboardBanner(
                    url = url,
                    onDownload = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.dismissClipboardBanner()
                        viewModel.onUrlChanged(url)
                        viewModel.fetchFormats(url)
                    },
                    onDismiss = { viewModel.dismissClipboardBanner() },
                )
            }
        }

        // ── Main content ────────────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "paste · share · download",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDim,
                )
                Spacer(Modifier.height(20.dp))
            }

            // ── URL Input ─────────────────────────────────────────────────
            item {
                UrlInputField(
                    value = urlInput,
                    onValueChange = viewModel::onUrlChanged,
                    onDone = { viewModel.fetchFormats() },
                )
            }

            // ── Download button ───────────────────────────────────────────
            item {
                Button(
                    onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.fetchFormats() 
                    },
                    enabled = urlInput.isNotBlank() && loadState !is HomeViewModel.LoadState.Loading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.text,
                        contentColor = colors.bg,
                        disabledContainerColor = colors.elevated,
                        disabledContentColor = colors.textDim,
                    ),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) {
                    if (loadState is HomeViewModel.LoadState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = colors.textDim,
                        )
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Rounded.Download, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("download", style = MaterialTheme.typography.labelLarge)
                }
            }

            // ── Error ─────────────────────────────────────────────────────
            if (loadState is HomeViewModel.LoadState.Error) {
                item {
                    Column {
                        Text(
                            text = (loadState as HomeViewModel.LoadState.Error).message.lowercase(),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.error,
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = { viewModel.forceUpdateYtDlp() },
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text("force update yt-dlp", color = colors.textDim, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // ── Temporary auto format picker (during loading) ────────────
            if (loadState is HomeViewModel.LoadState.Loading) {
                item {
                    Spacer(Modifier.height(2.dp))
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
                                    viewModel.queueAutoBestDownload(urlInput, isAudio = false) 
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
                                    viewModel.queueAutoBestDownload(urlInput, isAudio = true) 
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
                }
            }

            // ── Format picker (inline, after fetch) ───────────────────────
            if (loadState is HomeViewModel.LoadState.Loaded) {
                val info = (loadState as HomeViewModel.LoadState.Loaded).info
                item {
                    InlineFormatPicker(
                        info = info,
                        dynamicAccent = dynamicAccent,
                        haptic = haptic,
                        onSelect = { formatId -> viewModel.queueDownload(info, formatId) },
                    )
                }
            }
            
            // ── Download queued indicator (Bounce animation) ──────────────────
            if (loadState is HomeViewModel.LoadState.Done) {
                item {
                    InlineDoneState()
                }
            }
        }

        // ── Footer pinned at bottom ───────────────────────────────────────
        CreatorFooter()
    }
}

// ── Clipboard detection banner ────────────────────────────────────────────────
@Composable
private fun ClipboardBanner(url: String, onDownload: () -> Unit, onDismiss: () -> Unit) {
    val colors = veloColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.elevated)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Link, null, tint = colors.textMuted, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = "link detected",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDownload, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text("download it", color = colors.accent, style = MaterialTheme.typography.labelMedium)
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Rounded.Close, "dismiss", tint = colors.textDim, modifier = Modifier.size(14.dp))
        }
    }
}

// ── URL input field ───────────────────────────────────────────────────────────
@Composable
private fun UrlInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    val colors = veloColors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text("paste a link here", color = colors.textDim, style = MaterialTheme.typography.bodyMedium)
        },
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.text),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.border,
            unfocusedBorderColor = colors.borderSubtle,
            focusedContainerColor = colors.elevated,
            unfocusedContainerColor = colors.surface,
            cursorColor = colors.text,
        ),
        trailingIcon = if (value.isNotBlank()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Rounded.Close, "clear", tint = colors.textDim, modifier = Modifier.size(16.dp))
                }
            }
        } else null,
    )
}


// ── Inline format picker shown inside the home screen ────────────────────────
@Composable
private fun InlineFormatPicker(
    info: VideoInfo, 
    dynamicAccent: Color, 
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback, 
    onSelect: (String) -> Unit
) {
    val colors = veloColors
    var selectedTab by remember { mutableIntStateOf(0) }

    Column {
        HorizontalDivider(color = colors.border, thickness = 0.5.dp)
        Spacer(Modifier.height(12.dp))

        // Tab row
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(colors.elevated)
                .padding(3.dp),
        ) {
            listOf("video", "audio").forEachIndexed { idx, label ->
                val selected = selectedTab == idx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (selected) colors.border else Color.Transparent)
                        .clickable { 
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedTab = idx 
                        }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium,
                        color = if (selected) colors.text else colors.textMuted)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        val formats = if (selectedTab == 0) info.videoFormats else info.audioFormats

        // ── Best Quality quick button ─────────────────────────────────────────
        val bestFormat = formats.firstOrNull()
        if (bestFormat != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
            ) {
                Button(
                    onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelect(bestFormat.id) 
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = dynamicAccent.copy(alpha = 0.15f),
                        contentColor = dynamicAccent
                    ),
                ) {
                    val sizeLabel = if (bestFormat.fileSizeLabel.isNotBlank()) " (${bestFormat.fileSizeLabel})" else ""
                    Text(
                        text = "⚡ best quality — ${bestFormat.label}$sizeLabel",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(formats) { format ->
                val tierColor = qualityTierColor(format.label)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                        .background(colors.elevated)
                        .clickable { 
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSelect(format.id) 
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(format.label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp), color = colors.text,
                            textAlign = TextAlign.Center)
                        if (format.fileSizeLabel.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(format.fileSizeLabel, style = MaterialTheme.typography.labelSmall,
                                color = colors.textDim, textAlign = TextAlign.Center)
                        } else {
                            Spacer(Modifier.height(3.dp))
                            Text("size unknown", style = MaterialTheme.typography.labelSmall,
                                color = colors.textDim.copy(alpha = 0.5f), textAlign = TextAlign.Center)
                        }
                    }
                    // Tier dot in top-right corner
                    if (tierColor != Color.Transparent) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(tierColor)
                        )
                    }
                }
            }
        }
    }
}

// ── Format pill helpers ──────────────────────────────────────────────────────
private fun qualityTierColor(label: String): Color {
    val height = label.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
    return when {
        height >= 720 -> Color(0xFF4CAF50) // green — HD
        height >= 360 -> Color(0xFFFFC107) // amber — SD
        height > 0   -> Color(0xFFF44336) // red — low
        else         -> Color.Transparent
    }
}

// ── Inline Done state ───────────────────────────────────────────────────────────────
@Composable
private fun InlineDoneState() {
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
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .scale(scale)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.success.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, "done", tint = colors.success, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text("download queued", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
    }
}

// ── Creator attribution footer ────────────────────────────────────────────────
@Composable
private fun CreatorFooter() {
    val colors = veloColors
    val uriHandler = LocalUriHandler.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "made with ",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textDim,
        )
        Icon(
            Icons.Rounded.Favorite,
            contentDescription = null,
            tint = colors.error,
            modifier = Modifier.size(10.dp),
        )
        Text(
            text = " by ",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textDim,
        )
        Text(
            text = "arindam",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted,
            modifier = Modifier.clickable {
                uriHandler.openUri("https://arindamparia.in")
            },
        )
    }
}
