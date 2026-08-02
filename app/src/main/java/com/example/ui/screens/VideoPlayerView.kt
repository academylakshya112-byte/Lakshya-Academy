package com.example.ui.screens

import android.os.Handler
import android.os.Looper
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.net.NetworkCapabilities
import android.media.AudioManager
import android.provider.Settings
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.data.LessonEntity
import com.example.R
import android.view.LayoutInflater

// ============================================================================
// STATE PERSISTENCE CACHE (Prevents position/state loss during rotation or fullscreen toggle)
// ============================================================================
private val videoPositionsCache = mutableMapOf<String, Long>()
private val videoPlayWhenReadyCache = mutableMapOf<String, Boolean>()
private val videoPlaybackSpeedCache = mutableMapOf<String, Float>()
private val videoFillModeCache = mutableMapOf<String, Int>()
private val videoSubtitleEnabledCache = mutableMapOf<String, Boolean>()
private val videoSelectedQualityCache = mutableMapOf<String, String>()
private val videoUserExitedFullscreenCache = mutableMapOf<String, Boolean>()

// Picture-in-Picture Bridge Helper
object PipHelper {
    var isVideoPlaying: Boolean = false
    var onPipEntered: (() -> Unit)? = null
    var onPipExited: (() -> Unit)? = null
}

/**
 * Main VideoPlayerView entry point. It dispatches to standard or YouTube-based players.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerView(
    lesson: LessonEntity,
    onFullScreenToggle: (Boolean) -> Unit = {},
    isFullScreen: Boolean = false,
    isAdmin: Boolean = false
) {
    val finalSourceType = remember(lesson.videoSourceType, lesson.videoUrl) {
        if (lesson.videoSourceType == "LOCAL" || lesson.videoUrl.startsWith("content://") || lesson.videoUrl.startsWith("file://")) {
            "LOCAL"
        } else {
            detectVideoSourceType(lesson.videoUrl)
        }
    }

    val playContext = LocalContext.current
    LaunchedEffect(lesson) {
        while (true) {
            delay(1000L)
            com.example.util.StudyTracker.addStudyTime(playContext, 1)
        }
    }

    LaunchedEffect(lesson) {
        android.util.Log.d("VideoSystem", "=== PLAYER LOAD LOGS ===")
        android.util.Log.d("VideoSystem", "Selected Source URL: ${lesson.videoUrl}")
        android.util.Log.d("VideoSystem", "Detected Type: $finalSourceType")
        if (finalSourceType == "YOUTUBE") {
            val vidId = extractYouTubeVideoId(lesson.videoUrl) ?: "N/A"
            android.util.Log.d("VideoSystem", "Video ID: $vidId")
        }
        if (finalSourceType == "GOOGLE_DRIVE") {
            val convUrl = convertDriveUrl(lesson.videoUrl)
            android.util.Log.d("VideoSystem", "Converted Drive URL: $convUrl")
        }
        android.util.Log.d("VideoSystem", "Playback Type: $finalSourceType")
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
        YouTubePlayer(
            videoUrl = lesson.videoUrl,
            modifier = if (isFullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16/9f),
            onFullScreenToggle = onFullScreenToggle
        )
    } else if (finalSourceType == "GOOGLE_DRIVE") {
        val convertedUrl = convertDriveUrl(lesson.videoUrl)
        val convertedLesson = lesson.copy(videoUrl = convertedUrl)
        StandardVideoPlayer(convertedLesson, onFullScreenToggle, isFullScreen)
    } else {
        StandardVideoPlayer(lesson, onFullScreenToggle, isFullScreen)
    }
}

/**
 * Inline YouTube Player wrapper utilizing WebView. Supports YouTube Video, Live, and Playlist.
 */
@Composable
fun YouTubePlayer(
    videoUrl: String,
    modifier: Modifier = Modifier,
    onFullScreenToggle: ((Boolean) -> Unit)? = null
) {
    val embedUrl = remember(videoUrl) {
        getYouTubeEmbedUrl(videoUrl)
    }

    val embedHtml = remember(embedUrl) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                body, html {
                    margin: 0;
                    padding: 0;
                    width: 100%;
                    height: 100%;
                    background-color: black;
                    overflow: hidden;
                }
                .video-container {
                    position: relative;
                    width: 100%;
                    height: 100%;
                }
                iframe {
                    position: absolute;
                    top: 0;
                    left: 0;
                    width: 100%;
                    height: 100%;
                    border: 0;
                }
            </style>
        </head>
        <body>
            <div class="video-container">
                <iframe id="player" src="$embedUrl" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>
            </div>
        </body>
        </html>
        """.trimIndent()
    }

    var customView by remember { mutableStateOf<android.view.View?>(null) }
    var customViewCallback by remember { mutableStateOf<android.webkit.WebChromeClient.CustomViewCallback?>(null) }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    DisposableEffect(Unit) {
        onDispose {
            customViewCallback?.onCustomViewHidden()
        }
    }

    if (customView != null) {
        AndroidView(
            factory = { customView!! },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        AndroidView(
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                    val cookieManager = android.webkit.CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        allowFileAccess = true
                        allowContentAccess = true
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        }
                    }

                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onShowCustomView(view: android.view.View?, callback: CustomViewCallback?) {
                            customView = view
                            customViewCallback = callback
                            onFullScreenToggle?.invoke(true)
                            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }

                        override fun onHideCustomView() {
                            customView = null
                            customViewCallback = null
                            onFullScreenToggle?.invoke(false)
                            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        }

                        override fun getDefaultVideoPoster(): android.graphics.Bitmap? {
                            return android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
                        }

                        override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                            android.util.Log.d("YouTubePlayerView", "JS Console: ${consoleMessage?.message()}")
                            return true
                        }
                    }

                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                            return false
                        }
                    }

                    loadDataWithBaseURL("https://www.youtube.com", embedHtml, "text/html", "UTF-8", null)
                }
            },
            update = { webView ->
                // Ensure fresh content loaded if embedHtml changes
            },
            modifier = modifier
        )
    }
}

/**
 * Standard AndroidX Media3 ExoPlayer based Video Player.
 * Rebuilt using clean production-quality architecture.
 */
@OptIn(UnstableApi::class)
@Composable
fun StandardVideoPlayer(
    lesson: LessonEntity,
    onFullScreenToggle: (Boolean) -> Unit = {},
    isFullScreen: Boolean = false
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sharedPrefs = remember(context) { context.getSharedPreferences("video_player_prefs", Context.MODE_PRIVATE) }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val activity = remember(context) { context.findActivity() }

    // Read stored playback parameters
    val savedSpeed = remember(lesson.videoUrl) {
        videoPlaybackSpeedCache[lesson.videoUrl] ?: sharedPrefs.getFloat("last_selected_speed", 1.0f)
    }

    // Initialize ExoPlayer correctly with lifecycle awareness
    val resolvedUrl = remember(lesson.videoUrl) {
        com.example.service.MediaStorageServiceFactory.getService(context).resolveMediaUrl(lesson.videoUrl)
    }

    val exoPlayer = remember(lesson.videoUrl, resolvedUrl) {
        ExoPlayer.Builder(context).build().apply {
            val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            setAudioAttributes(audioAttributes, true)
            setMediaItem(MediaItem.fromUri(resolvedUrl))
            
            // Seamless state restoring
            val savedPos = videoPositionsCache[lesson.videoUrl] ?: 0L
            val savedPlayWhenReady = videoPlayWhenReadyCache[lesson.videoUrl] ?: true
            if (savedPos > 0L) {
                seekTo(savedPos)
            }
            prepare()
            playbackParameters = PlaybackParameters(savedSpeed)
            playWhenReady = savedPlayWhenReady
        }
    }

    // Primary Playback states
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(savedSpeed) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var controlsVisibleTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Control and lock parameters
    var isLocked by remember { mutableStateOf(false) }
    var hasSubtitles by remember { mutableStateOf(false) }
    var subtitlesEnabled by remember(lesson.videoUrl) {
        mutableStateOf(videoSubtitleEnabledCache[lesson.videoUrl] ?: true)
    }

    // Subtitle toggler logic
    val toggleSubtitles = {
        subtitlesEnabled = !subtitlesEnabled
        val builder = exoPlayer.trackSelectionParameters.buildUpon()
        builder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, !subtitlesEnabled)
        exoPlayer.trackSelectionParameters = builder.build()
    }

    // Dynamic states
    var isInPipMode by remember { mutableStateOf(false) }
    var selectedQuality by remember(lesson.videoUrl) {
        mutableStateOf(videoSelectedQualityCache[lesson.videoUrl] ?: "Auto")
    }
    var aspectMode by remember(lesson.videoUrl) {
        mutableStateOf(videoFillModeCache[lesson.videoUrl] ?: (if (isFullScreen) 1 else 0))
    }
    var aspectModeText by remember { mutableStateOf("") }
    var qualityMenuExpanded by remember { mutableStateOf(false) }
    var speedMenuExpanded by remember { mutableStateOf(false) }
    var isChangingQuality by remember { mutableStateOf(false) }
    var availableQualities by remember { mutableStateOf(listOf<String>()) }

    // Swiping (Volume/Brightness HUD) parameters
    var isDragging by remember { mutableStateOf(false) }
    var draggedOnLeft by remember { mutableStateOf(false) }
    var draggedValue by remember { mutableIntStateOf(0) }
    var initialBrightness by remember { mutableFloatStateOf(0.5f) }
    var initialVolume by remember { mutableFloatStateOf(0f) }

    // Double-tap visual feedbacks
    var showSeekFeedbackLeft by remember { mutableStateOf(false) }
    var showSeekFeedbackRight by remember { mutableStateOf(false) }

    // Connectivity tracking state
    var isNetworkConnected by remember { mutableStateOf(true) }

    // Synchronize PipHelper play status
    LaunchedEffect(isPlaying) {
        PipHelper.isVideoPlaying = isPlaying
    }

    // Keeps screen on while using the player
    DisposableEffect(Unit) {
        val window = activity?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // PiP System listeners registration
    DisposableEffect(Unit) {
        PipHelper.onPipEntered = {
            isInPipMode = true
            showControls = false
        }
        PipHelper.onPipExited = {
            isInPipMode = false
        }
        onDispose {
            PipHelper.onPipEntered = null
            PipHelper.onPipExited = null
        }
    }

    // Persist position and parameters cache on disposal
    DisposableEffect(lesson.videoUrl) {
        onDispose {
            videoPositionsCache[lesson.videoUrl] = exoPlayer.currentPosition
            videoPlayWhenReadyCache[lesson.videoUrl] = exoPlayer.playWhenReady
            videoPlaybackSpeedCache[lesson.videoUrl] = playbackSpeed
            videoFillModeCache[lesson.videoUrl] = aspectMode
            videoSubtitleEnabledCache[lesson.videoUrl] = subtitlesEnabled
            videoSelectedQualityCache[lesson.videoUrl] = selectedQuality
        }
    }

    // Dynamic network listener to ensure seamless resume upon reconnect
    DisposableEffect(context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isNetworkConnected = true
                activity?.runOnUiThread {
                    if (exoPlayer.playbackState == Player.STATE_READY && !exoPlayer.isPlaying && videoPlayWhenReadyCache[lesson.videoUrl] != false) {
                        exoPlayer.play()
                    }
                }
            }
            override fun onLost(network: Network) {
                isNetworkConnected = false
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            connectivityManager.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            // Callback registration fallback
        }
        onDispose {
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (e: Exception) {}
        }
    }

    // Connect Track & Player state listeners
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                isPlaying = exoPlayer.isPlaying
                duration = exoPlayer.duration.coerceAtLeast(0L)
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                playbackSpeed = playbackParameters.speed
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val qualities = mutableListOf<String>()
                var textTrackExists = false
                
                for (group in tracks.groups) {
                    if (group.type == androidx.media3.common.C.TRACK_TYPE_VIDEO) {
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            val height = format.height
                            if (height > 0) {
                                val label = "${height}p"
                                if (!qualities.contains(label)) {
                                    qualities.add(label)
                                }
                            }
                        }
                    } else if (group.type == androidx.media3.common.C.TRACK_TYPE_TEXT) {
                        textTrackExists = true
                    }
                }
                
                hasSubtitles = textTrackExists
                
                if (qualities.isNotEmpty()) {
                    qualities.sortBy { it.replace("p", "").toIntOrNull() ?: 0 }
                    if (qualities.size > 1 && !qualities.contains("Auto")) {
                        qualities.add(0, "Auto")
                    }
                }
                availableQualities = qualities
            }
        }
        exoPlayer.addListener(listener)
        
        // Initial setup sync
        isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING
        isPlaying = exoPlayer.isPlaying
        duration = exoPlayer.duration.coerceAtLeast(0L)
        playbackSpeed = exoPlayer.playbackParameters.speed

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    var hasTriggeredView by remember(lesson.videoUrl) { mutableStateOf(false) }
    val savedEmail = remember(context) {
        context.getSharedPreferences("lakshya_app_prefs", Context.MODE_PRIVATE)
            .getString("logged_in_user_email", "anonymous") ?: "anonymous"
    }

    // Auto-update playback progress values
    LaunchedEffect(exoPlayer, lesson.videoUrl) {
        while (true) {
            if (exoPlayer.isPlaying) {
                currentPosition = exoPlayer.currentPosition
                duration = exoPlayer.duration.coerceAtLeast(0L)
                videoPositionsCache[lesson.videoUrl] = currentPosition
                videoPlayWhenReadyCache[lesson.videoUrl] = exoPlayer.playWhenReady

                if (!hasTriggeredView && (currentPosition >= 10000L || (duration > 0 && currentPosition >= duration * 0.5f))) {
                    hasTriggeredView = true
                    coroutineScope.launch {
                        try {
                            com.example.api.VideoViewManager.recordView(context, lesson.videoUrl, savedEmail)
                        } catch (e: Exception) {
                            android.util.Log.e("VideoSystem", "Failed to record view: ${e.localizedMessage}")
                        }
                    }
                }
            }
            delay(500)
        }
    }

    // Auto-hide control panel loops
    LaunchedEffect(showControls, controlsVisibleTime) {
        if (showControls) {
            delay(4000)
            if (!isLocked && !isDragging) {
                showControls = false
            }
        }
    }

    // Sizing, orientation listener and physical orientation lock
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val orientationListener = remember(context) {
        object : android.view.OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val isPhysicallyPortrait = (orientation in 0..45) || (orientation in 315..359) || (orientation in 135..225)
                if (isPhysicallyPortrait) {
                    videoUserExitedFullscreenCache[lesson.videoUrl] = false
                }
            }
        }
    }

    DisposableEffect(orientationListener) {
        if (orientationListener.canDetectOrientation()) {
            orientationListener.enable()
        }
        onDispose {
            orientationListener.disable()
        }
    }

    LaunchedEffect(isLandscape) {
        if (!isLandscape) {
            videoUserExitedFullscreenCache[lesson.videoUrl] = false
        } else if (isLandscape && !isFullScreen) {
            val exited = videoUserExitedFullscreenCache[lesson.videoUrl] ?: false
            if (!exited) {
                onFullScreenToggle(true)
            }
        }
    }

    LaunchedEffect(isFullScreen) {
        if (isFullScreen) {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            delay(1000)
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Zoom and pan transformations setup
    var playerSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        if (scale > 1f) {
            val maxOffsetX = (playerSize.width * (scale - 1)) / 2f
            val maxOffsetY = (playerSize.height * (scale - 1)) / 2f
            val newX = (offset.x + offsetChange.x).coerceIn(-maxOffsetX, maxOffsetX)
            val newY = (offset.y + offsetChange.y).coerceIn(-maxOffsetY, maxOffsetY)
            offset = Offset(newX, newY)
        } else {
            offset = Offset.Zero
        }
    }

    // Double-tap visual dismissers
    LaunchedEffect(showSeekFeedbackLeft) {
        if (showSeekFeedbackLeft) {
            delay(650)
            showSeekFeedbackLeft = false
        }
    }
    LaunchedEffect(showSeekFeedbackRight) {
        if (showSeekFeedbackRight) {
            delay(650)
            showSeekFeedbackRight = false
        }
    }

    val exitFullscreen: () -> Unit = {
        videoUserExitedFullscreenCache[lesson.videoUrl] = true
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onFullScreenToggle(false)
    }

    // Back triggers handling
    val handleBack: () -> Unit = {
        if (isFullScreen) {
            exitFullscreen()
        } else {
            val componentActivity = activity as? androidx.activity.ComponentActivity
            if (componentActivity != null) {
                componentActivity.onBackPressedDispatcher.onBackPressed()
            } else {
                activity?.onBackPressed()
            }
        }
    }

    androidx.activity.compose.BackHandler(enabled = isFullScreen) {
        exitFullscreen()
    }

    // Dispose listener to clean up orientations
    DisposableEffect(isFullScreen) {
        onDispose {
            if (isFullScreen) {
                activity?.let { act ->
                    act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            }
        }
    }

    // System bars status controller
    DisposableEffect(isFullScreen) {
        val window = activity?.window
        if (window != null) {
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            if (isFullScreen) {
                controller.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars() or androidx.core.view.WindowInsetsCompat.Type.navigationBars())
                controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    window.attributes = window.attributes.apply {
                        layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
            } else {
                controller.show(androidx.core.view.WindowInsetsCompat.Type.statusBars() or androidx.core.view.WindowInsetsCompat.Type.navigationBars())
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    window.attributes = window.attributes.apply {
                        layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                    }
                }
            }
        }
        onDispose {
            window?.let { w ->
                val controller = androidx.core.view.WindowCompat.getInsetsController(w, w.decorView)
                controller.show(androidx.core.view.WindowInsetsCompat.Type.statusBars() or androidx.core.view.WindowInsetsCompat.Type.navigationBars())
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    w.attributes = w.attributes.apply {
                        layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                    }
                }
            }
        }
    }

    // Quality manual picker selection override
    val selectQuality = { quality: String ->
        val builder = exoPlayer.trackSelectionParameters.buildUpon()
        if (quality == "Auto") {
            builder.clearOverrides()
                .setMaxVideoSizeSd()
                .clearVideoSizeConstraints()
        } else {
            val heightLimit = quality.replace("p", "").toIntOrNull() ?: 720
            var found = false
            for (group in exoPlayer.currentTracks.groups) {
                if (group.type == androidx.media3.common.C.TRACK_TYPE_VIDEO) {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        if (format.height == heightLimit) {
                            builder.setOverrideForType(
                                androidx.media3.common.TrackSelectionOverride(
                                    group.mediaTrackGroup,
                                    i
                                )
                            )
                            found = true
                            break
                        }
                    }
                }
                if (found) break
            }
        }
        exoPlayer.trackSelectionParameters = builder.build()
    }

    LaunchedEffect(availableQualities) {
        if (availableQualities.isNotEmpty()) {
            val cachedQuality = videoSelectedQualityCache[lesson.videoUrl]
            if (cachedQuality != null && availableQualities.contains(cachedQuality)) {
                selectedQuality = cachedQuality
                selectQuality(cachedQuality)
            }
        }
    }

    LaunchedEffect(hasSubtitles) {
        if (hasSubtitles) {
            val cachedSubtitleEnabled = videoSubtitleEnabledCache[lesson.videoUrl]
            if (cachedSubtitleEnabled != null) {
                subtitlesEnabled = cachedSubtitleEnabled
                val builder = exoPlayer.trackSelectionParameters.buildUpon()
                builder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, !subtitlesEnabled)
                exoPlayer.trackSelectionParameters = builder.build()
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
            .onSizeChanged { playerSize = it }
    ) {
        // Player surface
        AndroidView(
            factory = { ctx ->
                val view = LayoutInflater.from(ctx).inflate(R.layout.custom_player_view, null) as PlayerView
                view.apply {
                    player = exoPlayer
                    useController = false
                    setBackgroundColor(android.graphics.Color.BLACK)
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                view.resizeMode = when (aspectMode) {
                    1 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    2 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                    else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
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

        // 1. Gesture touch detector overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(playerSize) {
                    if (!isLocked && !isInPipMode) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (playerSize.width > 0) {
                                    draggedOnLeft = offset.x < playerSize.width / 2
                                    isDragging = true
                                    if (draggedOnLeft) {
                                        val lp = activity?.window?.attributes
                                        initialBrightness = lp?.screenBrightness ?: -1f
                                        if (initialBrightness < 0f) {
                                            try {
                                                val sysBrightness = Settings.System.getInt(
                                                    context.contentResolver,
                                                    Settings.System.SCREEN_BRIGHTNESS
                                                )
                                                initialBrightness = sysBrightness / 255f
                                            } catch (e: Exception) {
                                                initialBrightness = 0.5f
                                            }
                                        }
                                        draggedValue = (initialBrightness * 100).toInt()
                                    } else {
                                        initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                        draggedValue = if (maxVol > 0) ((initialVolume / maxVol) * 100).toInt() else 0
                                    }
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (playerSize.height > 0) {
                                    val delta = -dragAmount.y / playerSize.height.toFloat()
                                    if (draggedOnLeft) {
                                        val lp = activity?.window?.attributes
                                        if (lp != null) {
                                            val target = (initialBrightness + delta).coerceIn(0.01f, 1.0f)
                                            lp.screenBrightness = target
                                            activity.window.attributes = lp
                                            initialBrightness = target
                                            draggedValue = (target * 100).toInt()
                                        }
                                    } else {
                                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                        val volDelta = delta * maxVol
                                        val targetVol = (initialVolume + volDelta).coerceIn(0f, maxVol.toFloat())
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol.toInt(), 0)
                                        initialVolume = targetVol
                                        draggedValue = if (maxVol > 0) ((targetVol / maxVol) * 100).toInt() else 0
                                    }
                                }
                            },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false }
                        )
                    }
                }
                .pointerInput(playerSize) {
                    detectTapGestures(
                        onTap = {
                            if (!isInPipMode) {
                                showControls = !showControls
                                controlsVisibleTime = System.currentTimeMillis()
                            }
                        },
                        onDoubleTap = { offset ->
                            if (!isLocked && !isInPipMode && playerSize.width > 0) {
                                val isLeft = offset.x < playerSize.width / 2
                                val seekAmount = 10000L
                                if (isLeft) {
                                    val target = (exoPlayer.currentPosition - seekAmount).coerceAtLeast(0L)
                                    exoPlayer.seekTo(target)
                                    currentPosition = target
                                    showSeekFeedbackLeft = true
                                } else {
                                    val target = (exoPlayer.currentPosition + seekAmount).coerceAtMost(duration)
                                    exoPlayer.seekTo(target)
                                    currentPosition = target
                                    showSeekFeedbackRight = true
                                }
                                controlsVisibleTime = System.currentTimeMillis()
                            }
                        }
                    )
                }
        )

        // 2. Double-tap seek visually animated panels
        if (showSeekFeedbackLeft) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.35f)
                    .align(Alignment.CenterStart)
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.Replay10, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("-10s", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        if (showSeekFeedbackRight) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.35f)
                    .align(Alignment.CenterEnd)
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.Forward10, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("+10s", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // 3. Brightness & Volume dragging progress indicator HUD
        if (isDragging && !isLocked && !isInPipMode) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.8f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (draggedOnLeft) Icons.Default.Brightness5 else Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = if (draggedOnLeft) "Brightness" else "Volume",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { draggedValue / 100f },
                            modifier = Modifier.width(100.dp).height(5.dp),
                            color = Color.Red,
                            trackColor = Color.White.copy(alpha = 0.3f),
                        )
                    }
                    Text(
                        text = "$draggedValue%",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 4. Online state error banner
        if (!isNetworkConnected) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.9f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 70.dp, start = 16.dp, end = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Connection Lost. Reconnecting automatically...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // 5. Controls lock state button
        if (showControls && !isInPipMode) {
            IconButton(
                onClick = {
                    isLocked = !isLocked
                    controlsVisibleTime = System.currentTimeMillis()
                },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = "Lock controls state",
                    tint = if (isLocked) Color.Red else Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 6. Action/Controllers HUD overlays
        if (showControls && !isLocked && !isInPipMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                // Top controls overlay
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = handleBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = lesson.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${lesson.chapterName} • ${lesson.folder} • Class Batch #${lesson.courseId}",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))

                    // Override manual Quality overrides
                    if (availableQualities.size > 1) {
                        Box {
                            IconButton(onClick = { qualityMenuExpanded = true }) {
                                Icon(imageVector = Icons.Default.Settings, contentDescription = "Quality Selector", tint = Color.White)
                            }
                            DropdownMenu(
                                expanded = qualityMenuExpanded,
                                onDismissRequest = { qualityMenuExpanded = false },
                                modifier = Modifier.background(Color(0xFF222222))
                            ) {
                                availableQualities.forEach { qual ->
                                    DropdownMenuItem(
                                        text = { Text(qual, color = if (selectedQuality == qual) Color.Red else Color.White, fontSize = 14.sp) },
                                        onClick = {
                                            qualityMenuExpanded = false
                                            if (selectedQuality != qual) {
                                                selectedQuality = qual
                                                isChangingQuality = true
                                                selectQuality(qual)
                                                coroutineScope.launch {
                                                    delay(800)
                                                    isChangingQuality = false
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Subtitles on/off
                    if (hasSubtitles) {
                        IconButton(onClick = toggleSubtitles) {
                            Icon(
                                imageVector = Icons.Default.Subtitles,
                                contentDescription = "Subtitles toggle",
                                tint = if (subtitlesEnabled) Color.Red else Color.White
                            )
                        }
                    }

                    // Speed controller
                    Box {
                        IconButton(onClick = { speedMenuExpanded = true }) {
                            Icon(imageVector = Icons.Default.Speed, contentDescription = "Speed selector", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = speedMenuExpanded,
                            onDismissRequest = { speedMenuExpanded = false },
                            modifier = Modifier.background(Color(0xFF222222))
                        ) {
                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { spd ->
                                DropdownMenuItem(
                                    text = { Text("${spd}x", color = if (playbackSpeed == spd) Color.Red else Color.White, fontSize = 14.sp) },
                                    onClick = {
                                        speedMenuExpanded = false
                                        playbackSpeed = spd
                                        sharedPrefs.edit().putFloat("last_selected_speed", spd).apply()
                                        exoPlayer.playbackParameters = PlaybackParameters(spd)
                                    }
                                )
                            }
                        }
                    }

                    // Picture-in-Picture trigger
                    IconButton(onClick = {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            val params = android.app.PictureInPictureParams.Builder()
                                .setAspectRatio(android.util.Rational(16, 9))
                                .build()
                            activity?.enterPictureInPictureMode(params)
                        }
                    }) {
                        Icon(imageVector = Icons.Default.PictureInPicture, contentDescription = "Picture in picture toggle", tint = Color.White)
                    }

                    // Scale fit/fill crop override
                    IconButton(onClick = {
                        aspectMode = (aspectMode + 1) % 3
                        aspectModeText = when (aspectMode) {
                            0 -> "Fit Mode (No Crop)"
                            1 -> "Zoom Mode (Crop Edges)"
                            else -> "Full Screen Stretch (No Bars)"
                        }
                    }) {
                        Icon(
                            imageVector = when (aspectMode) {
                                0 -> Icons.Default.AspectRatio
                                1 -> Icons.Default.ZoomOutMap
                                else -> Icons.Default.CropFree
                            },
                            contentDescription = "Toggle Aspect Ratio",
                            tint = Color.White
                        )
                    }

                    // Orientation manual toggle
                    IconButton(
                        onClick = {
                            if (isFullScreen) {
                                exitFullscreen()
                            } else {
                                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                onFullScreenToggle(true)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = "Toggle Fullscreen",
                            tint = Color.White
                        )
                    }
                }

                // Playback media buttons
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0L)) }) {
                        Icon(imageVector = Icons.Default.Replay10, contentDescription = "10 seconds backward", tint = Color.White, modifier = Modifier.size(36.dp))
                    }

                    IconButton(
                        onClick = {
                            if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                            controlsVisibleTime = System.currentTimeMillis()
                        },
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color.Red.copy(alpha = 0.85f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause video" else "Play video",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    IconButton(onClick = { exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(duration)) }) {
                        Icon(imageVector = Icons.Default.Forward10, contentDescription = "10 seconds forward", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }

                // Slider progress row
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
                    Slider(
                        value = progress,
                        onValueChange = {
                            val newPos = (it * duration).toLong()
                            exoPlayer.seekTo(newPos)
                            currentPosition = newPos
                            controlsVisibleTime = System.currentTimeMillis()
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.Red,
                            activeTrackColor = Color.Red,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(currentPosition), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(formatTime(duration), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        // Processing quality change optimizations overlay loader
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

        // Standard player buffering loader
        if (isBuffering && !isChangingQuality) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color.Red,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Buffering...",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (aspectModeText.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = aspectModeText,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            LaunchedEffect(aspectModeText) {
                delay(1500)
                aspectModeText = ""
            }
        }
    }
}

/**
 * Format milliseconds into standard MM:SS string
 */
private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

/**
 * Helper to traverse context up to retrieve top Activity wrapper.
 */
fun android.content.Context.findActivity(): android.app.Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is android.app.Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
fun VideoViewCountDisplay(videoUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var viewCountText by remember(videoUrl) { mutableStateOf("👁 -- Views") }

    LaunchedEffect(videoUrl) {
        try {
            val resolved = com.example.api.VideoViewManager.resolveViewCount(context, videoUrl, videoUrl)
            viewCountText = resolved
        } catch (e: Exception) {
            android.util.Log.e("VideoViewCountDisplay", "Error resolving view count: ${e.localizedMessage}")
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            imageVector = Icons.Default.RemoveRedEye,
            contentDescription = "View count",
            tint = Color(0xFF64748B),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = viewCountText.replace("👁 ", ""),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF475569)
        )
    }
}

