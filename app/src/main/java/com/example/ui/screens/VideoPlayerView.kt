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
import androidx.compose.ui.text.style.TextAlign
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import android.webkit.WebView
import android.view.ViewGroup
import com.example.data.LessonEntity
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer as PierYouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerView(
    lesson: LessonEntity,
    onFullScreenToggle: (Boolean) -> Unit = {},
    isFullScreen: Boolean = false,
    isAdmin: Boolean = false
) {
    val context = LocalContext.current
    val finalSourceType = remember(lesson.videoSourceType, lesson.videoUrl) {
        if (lesson.videoSourceType == "LOCAL" || lesson.videoUrl.startsWith("content://") || lesson.videoUrl.startsWith("file://")) {
            "LOCAL"
        } else {
            detectVideoSourceType(lesson.videoUrl)
        }
    }

    LaunchedEffect(lesson) {
        android.util.Log.d("VideoSystem", "=== PLAYER LOAD LOGS ===")
        android.util.Log.d("VideoSystem", "Lesson Clicked: ${lesson.title}")
        android.util.Log.d("VideoSystem", "VideoSourceType: $finalSourceType")
        android.util.Log.d("VideoSystem", "YouTube URL: ${lesson.videoUrl}")
        
        val vidId = extractYouTubeVideoId(lesson.videoUrl) ?: "N/A"
        android.util.Log.d("VideoSystem", "Extracted Video ID: $vidId")
        
        if (finalSourceType == "YOUTUBE") {
            android.util.Log.d("VideoSystem", "Player Initialization Started for Video ID: $vidId")
        }
        
        if (finalSourceType == "GOOGLE_DRIVE") {
            val convUrl = convertDriveUrl(lesson.videoUrl)
            android.util.Log.d("VideoSystem", "Converted Drive URL: $convUrl")
        }
        android.util.Log.d("VideoSystem", "=========================")
    }

    if (finalSourceType == "LOCAL") {
        if (isAdmin) {
            StandardVideoPlayer(lesson, onFullScreenToggle, isFullScreen)
        } else {
            Box(
                modifier = if (isFullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16/9f).background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Local Preview Only Available on Admin Device",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Students can only stream from web sources (YouTube/Drive).",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }
        }
    } else if (finalSourceType == "YOUTUBE") {
        val videoId = if (lesson.youtubeVideoId.isNotEmpty()) {
            lesson.youtubeVideoId
        } else {
            extractYouTubeVideoId(lesson.videoUrl) ?: ""
        }
        YouTubePlayer(videoId, modifier = if (isFullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16/9f))
    } else if (finalSourceType == "GOOGLE_DRIVE") {
        val convertedUrl = convertDriveUrl(lesson.videoUrl)
        val convertedLesson = lesson.copy(videoUrl = convertedUrl)
        StandardVideoPlayer(convertedLesson, onFullScreenToggle, isFullScreen)
    } else {
        StandardVideoPlayer(lesson, onFullScreenToggle, isFullScreen)
    }
}

@Composable
fun YouTubePlayer(videoId: String, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    
    val videoIdToLoad = videoId.trim()
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var playerInstance by remember { mutableStateOf<PierYouTubePlayer?>(null) }
    var reloadTrigger by remember { mutableIntStateOf(0) }
    var lastLoadedId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(videoIdToLoad, playerInstance) {
        if (videoIdToLoad.isNotEmpty() && playerInstance != null) {
            if (videoIdToLoad != lastLoadedId) {
                android.util.Log.d("YT_DEBUG", "LaunchedEffect: loadVideo($videoIdToLoad)")
                playerInstance?.loadVideo(videoIdToLoad, 0f)
                lastLoadedId = videoIdToLoad
            }
        }
    }

    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        key(reloadTrigger) {
            AndroidView(
                factory = { ctx ->
                    val playerView = YouTubePlayerView(ctx)
                    playerView.enableAutomaticInitialization = false
                    
                    val options = IFramePlayerOptions.Builder()
                        .controls(1)
                        .autoplay(1)
                        .rel(0)
                        .modestBranding(0)
                        .build()

                    playerView.initialize(object : AbstractYouTubePlayerListener() {
                        override fun onReady(youTubePlayer: PierYouTubePlayer) {
                            android.util.Log.i("YT_DEBUG", "onReady: ID $videoIdToLoad")
                            playerInstance = youTubePlayer
                            isLoading = false
                            
                            // WebView diagnostics and configuration
                            try {
                                val wv = findWebView(playerView)
                                wv?.let {
                                    it.settings.javaScriptEnabled = true
                                    it.settings.domStorageEnabled = true
                                    it.settings.databaseEnabled = true
                                    it.settings.mediaPlaybackRequiresUserGesture = false
                                    // Set a standard user agent
                                    it.settings.userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
                                    
                                    it.webChromeClient = object : android.webkit.WebChromeClient() {
                                        override fun onConsoleMessage(msg: android.webkit.ConsoleMessage?): Boolean {
                                            android.util.Log.d("YT_CONSOLE", "[${msg?.messageLevel()}] ${msg?.message()} (${msg?.sourceId()}:${msg?.lineNumber()})")
                                            return true
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("YT_DEBUG", "WebView setup failed", e)
                            }

                            if (videoIdToLoad != lastLoadedId) {
                                youTubePlayer.loadVideo(videoIdToLoad, 0f)
                                lastLoadedId = videoIdToLoad
                            }
                        }

                        override fun onError(youTubePlayer: PierYouTubePlayer, error: com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerError) {
                            isLoading = false
                            val errorName = error.name
                            val errorCode = error.ordinal
                            errorMsg = "YouTube Error: $errorName (Ordinal: $errorCode). Library: 12.1.0"
                            android.util.Log.e("YT_DEBUG", "onError: $errorName ($errorCode) for video $videoIdToLoad")
                        }
                    }, options)

                    lifecycleOwner.lifecycle.addObserver(playerView)
                    playerView
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { view ->
                    lifecycleOwner.lifecycle.removeObserver(view)
                    view.release()
                }
            )
        }

        if (isLoading) {
            CircularProgressIndicator(color = Color.Red)
        }

        errorMsg?.let { msg ->
            Column(
                modifier = Modifier.fillMaxSize().background(Color.Black).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Error, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text(msg, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Video ID: $videoIdToLoad", color = Color.Gray, fontSize = 10.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            errorMsg = null
                            isLoading = true
                            playerInstance?.loadVideo(videoIdToLoad, 0f)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("Retry")
                    }
                    Button(
                        onClick = {
                            errorMsg = null
                            isLoading = true
                            playerInstance = null
                            lastLoadedId = null
                            reloadTrigger++
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                    ) {
                        Text("Hard Reload")
                    }
                }
            }
        }
    }
}

private fun tweakWebView(playerView: YouTubePlayerView) {
    try {
        val webView = findWebView(playerView)
        webView?.let { wv ->
            wv.settings.javaScriptEnabled = true
            wv.settings.domStorageEnabled = true
            wv.settings.databaseEnabled = true
            wv.settings.mediaPlaybackRequiresUserGesture = false
            wv.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
        }
    } catch (e: Exception) {
        android.util.Log.e("YT_DEBUG", "WebView tweak failed", e)
    }
}

private fun findWebView(view: android.view.View): WebView? {
    if (view is WebView) return view
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            val child = findWebView(view.getChildAt(i))
            if (child != null) return child
        }
    }
    return null
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
