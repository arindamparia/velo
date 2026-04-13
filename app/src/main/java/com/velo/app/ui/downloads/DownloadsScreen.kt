@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.velo.app.ui.downloads
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.velo.app.data.model.DownloadRecord
import com.velo.app.data.model.DownloadStatus
import com.velo.app.ui.theme.veloColors
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnLayout
import android.app.PendingIntent
import android.os.Build
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Replay5
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerNotificationManager
import com.velo.app.VeloApp


@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
fun DownloadsScreen(
    onNavigateBack: () -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val colors = veloColors
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val totalStorageBytes by viewModel.totalStorageBytes.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val backgroundAudioEnabled by viewModel.backgroundAudioEnabled.collectAsStateWithLifecycle()
    val backgroundVideoEnabled by viewModel.backgroundVideoEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current

    // ── Dialog state ─────────────────────────────────────────────────────────
    var recordToDelete by remember { mutableStateOf<DownloadRecord?>(null) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    // Fire an explicit physical storage sync every time this screen loads 
    // to instantly drop anything the user manually deleted via a file manager.
    LaunchedEffect(Unit) {
        viewModel.pruneMissingFiles()
    }

    // ── Delete single confirmation dialog ─────────────────────────────────────
    recordToDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            containerColor = colors.elevated,
            title = {
                Text("delete download?", style = MaterialTheme.typography.titleSmall, color = colors.text)
            },
            text = {
                Text(
                    "\"${record.title.lowercase()}\" and its file will be permanently deleted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRecord(context, record)
                    recordToDelete = null
                }) {
                    Text("delete", color = colors.error, style = MaterialTheme.typography.labelMedium)
                }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) {
                    Text("cancel", color = colors.textMuted, style = MaterialTheme.typography.labelMedium)
                }
            },
        )
    }

    // ── Delete all confirmation dialog ────────────────────────────────────────
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            containerColor = colors.elevated,
            title = {
                Text("delete everything?", style = MaterialTheme.typography.titleSmall, color = colors.text)
            },
            text = {
                Text(
                    "All ${downloads.size} downloads and their files will be permanently deleted. This cannot be undone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAll(context)
                    showDeleteAllDialog = false
                }) {
                    Text("delete all", color = colors.error, style = MaterialTheme.typography.labelMedium)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("cancel", color = colors.textMuted, style = MaterialTheme.typography.labelMedium)
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // ── Top bar ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "back",
                    tint = colors.text,
                )
            }
            Text(
                text = "downloads",
                style = MaterialTheme.typography.titleMedium,
                color = colors.text,
                modifier = Modifier.weight(1f),
            )
            // Sweeper icon to delete failed tasks and run prune
            if (downloads.any { it.status == com.velo.app.data.model.DownloadStatus.FAILED }) {
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.sweepBroom(context)
                }) {
                    Icon(
                        Icons.Rounded.CleaningServices,
                        contentDescription = "sweep",
                        tint = colors.text,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Delete All button — only visible when list is non-empty
            if (downloads.isNotEmpty()) {
                TextButton(onClick = { showDeleteAllDialog = true }) {
                    Text(
                        "delete all",
                        color = colors.error,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        HorizontalDivider(color = colors.border, thickness = 0.5.dp)

        // ── Search bar ────────────────────────────────────────────────────────
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.searchQuery.value = it },
            placeholder = { Text("search downloads…", style = MaterialTheme.typography.bodySmall, color = colors.textDim) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = colors.textDim, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { viewModel.searchQuery.value = ""; focusManager.clearFocus() }) {
                        Icon(Icons.Rounded.Clear, "clear", tint = colors.textDim, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.border,
                unfocusedBorderColor = colors.border,
                focusedTextColor = colors.text,
                unfocusedTextColor = colors.text,
                cursorColor = colors.text,
            ),
            textStyle = MaterialTheme.typography.bodySmall,
        )

        // ── Storage usage chip ────────────────────────────────────────────────
        if (totalStorageBytes > 0) {
            val storageMb = totalStorageBytes / 1_000_000.0
            val storageText = if (storageMb >= 1000) "%.1f gb used".format(storageMb / 1000) else "%.1f mb used".format(storageMb)
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    text = "\uD83D\uDCBE $storageText by velo",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textDim,
                )
            }
        }

        // Tracks which download is currently playing — only one at a time
        var playingId by remember { mutableStateOf<String?>(null) }

        if (downloads.isEmpty()) {
            // ── Empty state ────────────────────────────────────────────────
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (searchQuery.isBlank()) "nothing downloaded yet" else "no results for \"$searchQuery\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDim,
                )
            }
        } else {
            val groupedDownloads = androidx.compose.runtime.remember(downloads) {
                downloads.groupBy { record ->
                    val now = Calendar.getInstance()
                    val recordDate = Calendar.getInstance().apply { timeInMillis = record.timestampMs }
                    
                    val nowYear = now.get(java.util.Calendar.YEAR)
                    val recYear = recordDate.get(java.util.Calendar.YEAR)
                    val nowDay = now.get(java.util.Calendar.DAY_OF_YEAR)
                    val recDay = recordDate.get(java.util.Calendar.DAY_OF_YEAR)
                    
                    when {
                        nowYear == recYear && nowDay == recDay -> "today"
                        nowYear == recYear && nowDay - 1 == recDay -> "yesterday"
                        else -> "older"
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                groupedDownloads.forEach { (header, groupItems) ->
                    stickyHeader {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.bg.copy(alpha = 0.95f))
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = header,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textDim
                            )
                        }
                    }
                    items(groupItems, key = { it.id }) { record ->
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            DownloadItem(
                                record = record,
                                isPlaying = playingId == record.id,
                                backgroundAudioEnabled = backgroundAudioEnabled,
                                backgroundVideoEnabled = backgroundVideoEnabled,
                                onPlayToggle = {
                                    playingId = if (playingId == record.id) null else record.id
                                },
                                onDeleteRequest = { recordToDelete = record },
                                onRetryRequest = { viewModel.retryDownload(record) },
                                onCancelRequest = { viewModel.cancelDownload(record) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadItem(
    record: DownloadRecord,
    isPlaying: Boolean,
    backgroundAudioEnabled: Boolean,
    backgroundVideoEnabled: Boolean,
    onPlayToggle: () -> Unit,
    onDeleteRequest: () -> Unit,
    onRetryRequest: () -> Unit,
    onCancelRequest: () -> Unit,
) {
    val colors = veloColors
    val dateStr = androidx.compose.runtime.remember(record.timestampMs) {
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
            .format(Date(record.timestampMs))
            .lowercase()
    }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val isAudio = record.formatLabel.startsWith("audio", ignoreCase = true)

    // Tracks if this specific video is expanded to full screen
    var isFullscreen by remember { mutableStateOf(false) }

    val fileSizeLabel = record.fileSizeBytes?.let {
        when {
            it >= 1_000_000_000 -> "%.1f GB".format(it / 1_000_000_000.0)
            it >= 1_000_000     -> "%.1f MB".format(it / 1_000_000.0)
            it >= 1_000         -> "%.0f KB".format(it / 1_000.0)
            else                -> "$it B"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.elevated)
            .clickable(enabled = record.status == DownloadStatus.DONE && record.filePath != null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onPlayToggle()
            },
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ── Thumbnail / Icon ─────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(width = 72.dp, height = 52.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.surfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (record.thumbnail != null) {
                    coil.compose.AsyncImage(
                        model = record.thumbnail,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // Overlay play/music icon
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isAudio) Icons.Rounded.MusicNote else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            // ── Title + metadata ─────────────────────────────────────────────
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.title.lowercase(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                // Row 2: site · format chip · size
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(record.siteName, style = MaterialTheme.typography.labelSmall, color = colors.textDim)
                    Text("·", style = MaterialTheme.typography.labelSmall, color = colors.textDim)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(colors.surfaceHigh)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    ) {
                        Text(record.formatLabel, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                    }
                    if (fileSizeLabel != null) {
                        Text("·", style = MaterialTheme.typography.labelSmall, color = colors.textDim)
                        Text(fileSizeLabel, style = MaterialTheme.typography.labelSmall, color = colors.textDim)
                    }
                }
                Spacer(Modifier.height(2.dp))
                // Row 3: date + status badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textDim.copy(alpha = 0.6f),
                    )
                    when (record.status) {
                        DownloadStatus.FAILED -> Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(colors.error.copy(alpha = 0.15f))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        ) {
                            Text("failed", style = MaterialTheme.typography.labelSmall, color = colors.error)
                        }
                        DownloadStatus.QUEUED -> Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(colors.border)
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        ) {
                            Text("queued", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                        }
                        DownloadStatus.DOWNLOADING -> Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(colors.accent.copy(alpha = 0.12f))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = if (record.progress > 0f) "${(record.progress * 100).toInt()}%" else "downloading…",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.accent,
                            )
                        }
                        else -> {}
                    }
                }
            }

            Spacer(Modifier.width(4.dp))

            // ── Action buttons ───────────────────────────────────────────────
            when (record.status) {
                DownloadStatus.DOWNLOADING -> {
                    IconButton(onClick = onCancelRequest, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, "cancel", tint = colors.textDim, modifier = Modifier.size(18.dp))
                    }
                }
                DownloadStatus.DONE -> {
                    IconButton(onClick = {
                        try {
                            val uri = android.net.Uri.parse(record.filePath)
                            val mimeType = if (isAudio) "audio/*" else "video/*"
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = mimeType
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, record.title))
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Can't share this file", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Share, "share", tint = colors.textDim, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDeleteRequest, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Delete, "delete", tint = colors.textDim, modifier = Modifier.size(18.dp))
                    }
                }
                DownloadStatus.QUEUED -> {
                    IconButton(onClick = onCancelRequest, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, "cancel", tint = colors.textDim, modifier = Modifier.size(18.dp))
                    }
                }
                DownloadStatus.FAILED -> {
                    IconButton(onClick = onRetryRequest, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Refresh, "retry", tint = colors.accent, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDeleteRequest, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Delete, "delete", tint = colors.textDim, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        if (record.status == DownloadStatus.DOWNLOADING) {
            val animatedProgress by animateFloatAsState(
                targetValue = record.progress.coerceIn(0f, 1f),
                animationSpec = androidx.compose.animation.core.tween(500, easing = androidx.compose.animation.core.LinearEasing),
                label = "dl_progress",
            )
            LinearProgressIndicator(
                progress = { if (record.progress <= 0f) 0f else animatedProgress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = colors.accent,
                trackColor = colors.border,
            )
        }

        // ── In-app ExoPlayer (expands on tap) ────────────────────────────────
        // Note: fadeIn/fadeOut instead of expandVertically/shrinkVertically — the expand
        // animation passes a growing height constraint to AndroidView, causing PlayerView
        // to layout the seekbar at the wrong position until a relayout is triggered.
        AnimatedVisibility(
            visible = isPlaying && record.status == DownloadStatus.DONE && record.filePath != null,
            enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(200)),
            exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(200)),
        ) {
            val exoPlayer = remember(record.filePath) {
                ExoPlayer.Builder(context)
                    .setSeekBackIncrementMs(5_000L)
                    .setSeekForwardIncrementMs(10_000L)
                    .build().apply {
                        // Embed title + thumbnail in the MediaItem so the system media controls
                        // (Android 13+) can display them without a separate fetch.
                        val metadata = MediaMetadata.Builder()
                            .setTitle(record.title)
                            .setArtist(record.formatLabel)
                            .apply {
                                record.thumbnail?.let {
                                    setArtworkUri(android.net.Uri.parse(it))
                                }
                            }
                            .build()
                        setMediaItem(
                            MediaItem.Builder()
                                .setUri(android.net.Uri.parse(record.filePath))
                                .setMediaMetadata(metadata)
                                .build()
                        )
                        prepare()
                        playWhenReady = true
                    }
            }

            // MediaSession → Android 13+ automatically renders the native system media controls
            // card (seekbar, thumbnail, title, rewind/play-pause/forward) in the notification
            // shade. No custom notification needed on Android 13+.
            val mediaSession = remember(record.filePath) {
                val filteredPlayer = object : androidx.media3.common.ForwardingPlayer(exoPlayer) {
                    override fun getAvailableCommands(): Player.Commands {
                        return super.getAvailableCommands().buildUpon()
                            .remove(Player.COMMAND_SEEK_TO_NEXT)
                            .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                            .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                            .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                            .remove(Player.COMMAND_SET_SPEED_AND_PITCH)
                            .build()
                    }
                }
                MediaSession.Builder(context, filteredPlayer).build()
            }

            // For all Android versions (including Android 13+), we must use PlayerNotificationManager
            // to actually construct and post the media notification. On Android 13+, the OS will 
            // automatically intercept this MediaStyle notification and render it as the modern squiggly UI.
            val notifManager = remember(record.filePath) {
                PlayerNotificationManager.Builder(
                    context, VeloApp.PLAYBACK_NOTIF_ID, VeloApp.CHANNEL_PLAYBACK,
                ).setMediaDescriptionAdapter(object : PlayerNotificationManager.MediaDescriptionAdapter {
                    override fun getCurrentContentTitle(player: Player) = record.title
                    override fun getCurrentContentText(player: Player) = record.formatLabel
                    override fun createCurrentContentIntent(player: Player): PendingIntent? {
                        val intent = context.packageManager
                            .getLaunchIntentForPackage(context.packageName) ?: return null
                        return PendingIntent.getActivity(
                            context, 0, intent,
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        )
                    }
                    override fun getCurrentLargeIcon(
                        player: Player,
                        callback: PlayerNotificationManager.BitmapCallback,
                    ): android.graphics.Bitmap? = null
                }).build().also { nm ->
                    nm.setUseRewindAction(true)
                    nm.setUseFastForwardAction(true)
                    nm.setUseRewindActionInCompactView(true)
                    nm.setUseFastForwardActionInCompactView(true)
                    nm.setPlayer(exoPlayer) // PlayerNotificationManager maps controls from the session/player
                    nm.setMediaSessionToken(mediaSession.sessionCompatToken)
                }
            }

            DisposableEffect(record.filePath) {
                onDispose {
                    notifManager.setPlayer(null)
                    mediaSession.release()
                    exoPlayer.release()
                }
            }

            // Pause when app goes to background — unless the matching background setting is on.
            // rememberUpdatedState captures the latest setting without re-registering the observer.
            val latestBgEnabled by androidx.compose.runtime.rememberUpdatedState(
                if (isAudio) backgroundAudioEnabled else backgroundVideoEnabled
            )
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_PAUSE && !latestBgEnabled) {
                        exoPlayer.pause()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            LaunchedEffect(isPlaying) {
                if (!isPlaying) exoPlayer.pause()
            }

            // ── Fullscreen Overlay Mode ──────────────────────────────────────
            if (isFullscreen) {
                val activity = context as? android.app.Activity
                
                DisposableEffect(Unit) {
                    val window = activity?.window
                    if (window != null) {
                        WindowCompat.setDecorFitsSystemWindows(window, false)
                        WindowInsetsControllerCompat(window, window.decorView).apply {
                            hide(WindowInsetsCompat.Type.systemBars())
                            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        }
                    }
                    onDispose {
                        if (window != null) {
                            WindowCompat.setDecorFitsSystemWindows(window, true)
                            WindowInsetsControllerCompat(window, window.decorView)
                                .show(WindowInsetsCompat.Type.systemBars())
                        }
                    }
                }

                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { isFullscreen = false },
                    properties = androidx.compose.ui.window.DialogProperties(
                        dismissOnBackPress = true,
                        dismissOnClickOutside = false,
                        usePlatformDefaultWidth = false, 
                        decorFitsSystemWindows = false   
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(androidx.compose.ui.graphics.Color.Black)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    controllerAutoShow = false
                                    player = exoPlayer
                                    useController = true
                                    setFullscreenButtonClickListener { _ ->
                                        isFullscreen = false
                                    }
                                    doOnLayout { showController() }
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.displayCutout)
                                .padding(bottom = 48.dp)
                        )
                    }
                }
            }

            // ── Inline Mode ──────────────────────────────────────────────────
            var controlsVisible by remember { mutableStateOf(true) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        Modifier.aspectRatio(16f / 9f)
                    )
                    .background(androidx.compose.ui.graphics.Color.Black)
                    // Tap video surface to toggle controls; absorbs tap from parent card click.
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { controlsVisible = !controlsVisible }
            ) {
                if (!isFullscreen) {
                    // Video surface — built-in controller disabled; we draw our own overlay
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // ── Custom controls state ─────────────────────────────────
                    var playerPosition by remember { mutableLongStateOf(0L) }
                    var playerDuration by remember { mutableLongStateOf(1L) }
                    var isVideoPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
                    var isSeeking by remember { mutableStateOf(false) }

                    // Poll playback position every 250 ms
                    LaunchedEffect(exoPlayer) {
                        while (true) {
                            if (!isSeeking) {
                                playerPosition = exoPlayer.currentPosition
                                playerDuration = exoPlayer.duration.coerceAtLeast(1L)
                            }
                            isVideoPlaying = exoPlayer.isPlaying
                            kotlinx.coroutines.delay(250)
                        }
                    }

                    // Auto-hide controls after 3 s of inactivity; reset timer on any interaction
                    LaunchedEffect(controlsVisible, isSeeking) {
                        if (controlsVisible && !isSeeking) {
                            kotlinx.coroutines.delay(3_000)
                            controlsVisible = false
                        }
                    }

                    // ── Controls overlay (fades in/out) ───────────────────────
                    val controlsAlpha by animateFloatAsState(
                        targetValue = if (controlsVisible) 1f else 0f,
                        animationSpec = androidx.compose.animation.core.tween(150),
                        label = "controls_alpha"
                    )
                    Box(modifier = Modifier.fillMaxSize().alpha(controlsAlpha)) {
                            // Fullscreen button — top-right
                            if (!isAudio) {
                                IconButton(
                                    onClick = { isFullscreen = true },
                                    modifier = Modifier.align(Alignment.TopEnd)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Fullscreen,
                                        contentDescription = "fullscreen",
                                        tint = androidx.compose.ui.graphics.Color.White
                                    )
                                }
                            }

                            // Play controls — center
                            Row(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = {
                                    exoPlayer.seekTo((exoPlayer.currentPosition - 5_000L).coerceAtLeast(0L))
                                }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Replay5,
                                        contentDescription = "rewind 5s",
                                        tint = androidx.compose.ui.graphics.Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isVideoPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (isVideoPlaying) "pause" else "play",
                                        tint = androidx.compose.ui.graphics.Color.White,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                                IconButton(onClick = {
                                    val dur = exoPlayer.duration.coerceAtLeast(0L)
                                    exoPlayer.seekTo((exoPlayer.currentPosition + 10_000L).coerceAtMost(dur))
                                }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Forward10,
                                        contentDescription = "forward 10s",
                                        tint = androidx.compose.ui.graphics.Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            // Seekbar — absolute bottom, no time labels, no settings
                            Slider(
                                value = if (playerDuration > 0L) playerPosition.toFloat() / playerDuration.toFloat() else 0f,
                                onValueChange = { fraction ->
                                    isSeeking = true
                                    controlsVisible = true  // keep visible while scrubbing
                                    playerPosition = (fraction * playerDuration).toLong()
                                },
                                onValueChangeFinished = {
                                    exoPlayer.seekTo(playerPosition)
                                    isSeeking = false
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter),
                                colors = SliderDefaults.colors(
                                    thumbColor = androidx.compose.ui.graphics.Color.White,
                                    activeTrackColor = androidx.compose.ui.graphics.Color.White,
                                    inactiveTrackColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.3f)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
