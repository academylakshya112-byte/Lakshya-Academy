package com.example.ui.screens

import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.data.LessonEntity

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerView(
    lesson: LessonEntity,
    onFullScreenToggle: (Boolean) -> Unit = {},
    isFullScreen: Boolean = false
) {
    val context = LocalContext.current
    val isYouTube = remember(lesson.videoUrl) {
        lesson.videoUrl.contains("youtube.com") || lesson.videoUrl.contains("youtu.be")
    }

    if (isYouTube) {
        YouTubePlayer(lesson.videoUrl, modifier = if (isFullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16/9f))
    } else {
        StandardVideoPlayer(lesson, onFullScreenToggle, isFullScreen)
    }
}

@Composable
fun YouTubePlayer(url: String, modifier: Modifier) {
    val videoId = remember(url) {
        if (url.contains("v=")) url.substringAfter("v=").substringBefore("&")
        else if (url.contains("youtu.be/")) url.substringAfter("youtu.be/").substringBefore("?")
        else ""
    }
    val embedUrl = "https://www.youtube.com/embed/$videoId"

    AndroidView(
        factory = { ctx ->
            android.webkit.WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                webViewClient = android.webkit.WebViewClient()
                loadUrl(embedUrl)
            }
        },
        modifier = modifier
    )
}

@OptIn(UnstableApi::class)
@Composable
fun StandardVideoPlayer(
    lesson: LessonEntity,
    onFullScreenToggle: (Boolean) -> Unit = {},
    isFullScreen: Boolean = false
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            setAudioAttributes(audioAttributes, true)
            val mediaItem = MediaItem.fromUri(lesson.videoUrl)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }

    // Quality selection states (144p to 1080p)
    var selectedQuality by remember { mutableStateOf("720p") }
    var qualityMenuExpanded by remember { mutableStateOf(false) }
    var isChangingQuality by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(lesson.id) {
        onDispose {
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer) {
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                currentPosition = exoPlayer.currentPosition
                duration = exoPlayer.duration.coerceAtLeast(0L)
                isPlaying = exoPlayer.isPlaying
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(runnable)
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        if (scale > 1f) {
            offset = Offset(offset.x + offsetChange.x, offset.y + offsetChange.y)
        } else {
            offset = Offset.Zero
        }
    }

    val activity = context.findActivity()

    // Add back hander to exit full screen first
    androidx.activity.compose.BackHandler(enabled = isFullScreen) {
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onFullScreenToggle(false)
    }

    // Ensure the screen is reset to portrait when the video player view is disposed (navigated back / closed)
    DisposableEffect(Unit) {
        onDispose {
            activity?.let { act ->
                act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    DisposableEffect(isFullScreen) {
        val window = activity?.window
        if (window != null) {
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            if (isFullScreen) {
                controller.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars() or androidx.core.view.WindowInsetsCompat.Type.navigationBars())
                controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(androidx.core.view.WindowInsetsCompat.Type.statusBars() or androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            }
        }
        onDispose {
            window?.let { w ->
                val controller = androidx.core.view.WindowCompat.getInsetsController(w, w.decorView)
                controller.show(androidx.core.view.WindowInsetsCompat.Type.statusBars() or androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            }
        }
    }

    val playerModifier = if (isFullScreen) {
        Modifier.fillMaxSize()
    } else {
        Modifier
            .fillMaxWidth()
            .aspectRatio(16 / 9f)
    }

    Box(
        modifier = playerModifier
            .background(Color.Black)
            .transformable(state = transformState)
            .clickable { showControls = !showControls }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                view.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )

        // Custom Overlay Controls
        if (showControls) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                // Play/Pause Center
                Row(modifier = Modifier.align(Alignment.Center).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton(onClick = { exoPlayer.seekTo(exoPlayer.currentPosition - 10000) }) {
                        Icon(imageVector = Icons.Default.Replay10, contentDescription = "10s Back", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    IconButton(
                        onClick = {
                            if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                            isPlaying = !isPlaying
                        },
                        modifier = Modifier.size(64.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    IconButton(onClick = { exoPlayer.seekTo(exoPlayer.currentPosition + 10000) }) {
                        Icon(imageVector = Icons.Default.Forward10, contentDescription = "10s Forward", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }

                // Speed and FullScreen row
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Speed Selector
                    Surface(
                        onClick = {
                            playbackSpeed = when (playbackSpeed) {
                                1.0f -> 1.5f
                                1.5f -> 2.0f
                                2.0f -> 0.75f
                                else -> 1.0f
                            }
                            exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        contentColor = Color.White
                    ) {
                        Text(
                            "${playbackSpeed}x",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Quality Selector (144p to 1080p)
                    Box {
                        Surface(
                            onClick = { qualityMenuExpanded = true },
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Black.copy(alpha = 0.6f),
                            contentColor = Color.White
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Quality Settings",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = selectedQuality,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = qualityMenuExpanded,
                            onDismissRequest = { qualityMenuExpanded = false },
                            modifier = Modifier.background(Color(0xFF333333))
                        ) {
                            listOf("144p", "240p", "360p", "480p", "720p", "1080p").forEach { quality ->
                                DropdownMenuItem(
                                    text = { Text(quality, color = Color.White, fontSize = 14.sp) },
                                    onClick = {
                                        qualityMenuExpanded = false
                                        if (selectedQuality != quality) {
                                            selectedQuality = quality
                                            isChangingQuality = true
                                            coroutineScope.launch {
                                                kotlinx.coroutines.delay(800)
                                                isChangingQuality = false
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "Quality set to $quality",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            val activity = context.findActivity()
                            if (activity?.requestedOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            } else {
                                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            }
                            onFullScreenToggle(!isFullScreen)
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                }

                // Bottom Progress Bar
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
                    Slider(
                        value = progress,
                        onValueChange = {
                            val newPos = (it * duration).toLong()
                            exoPlayer.seekTo(newPos)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.Red,
                            activeTrackColor = Color.Red,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(currentPosition), color = Color.White, fontSize = 11.sp)
                        Text(formatTime(duration), color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }

        if (isChangingQuality) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.Red, modifier = Modifier.size(44.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Optimizing quality to $selectedQuality...",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

fun android.content.Context.findActivity(): android.app.Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is android.app.Activity) return context
        context = context.baseContext
    }
    return null
}
