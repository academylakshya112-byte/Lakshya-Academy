package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.LiveClassEntity
import com.example.service.MediaStorageServiceFactory
import com.example.ui.theme.BrandBluePrimary
import com.example.ui.theme.BrandBlueSecondary
import com.example.ui.viewmodel.AcademyViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Robust Youtube Video ID Extractor Helper
fun extractYoutubeId(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return ""
    
    android.util.Log.d("LivePlayerDebug", "Extracting ID from input: $trimmed")
    
    // Check if it's already a clean raw ID (11 chars, common for YouTube)
    if (trimmed.length == 11 && !trimmed.contains("/") && !trimmed.contains("?") && !trimmed.contains("=") && !trimmed.contains("&")) {
        android.util.Log.d("LivePlayerDebug", "Detected raw 11-char Video ID: $trimmed")
        return trimmed
    }
    
    // Pattern checks for /shorts/, /live/, watch?v=, youtu.be/, /embed/, /v/
    val patterns = listOf(
        Regex("""youtube\.com/shorts/([^?&/#\s]+)""", RegexOption.IGNORE_CASE),
        Regex("""youtube\.com/live/([^?&/#\s]+)""", RegexOption.IGNORE_CASE),
        Regex("""youtube\.com/embed/([^?&/#\s]+)""", RegexOption.IGNORE_CASE),
        Regex("""youtube\.com/v/([^?&/#\s]+)""", RegexOption.IGNORE_CASE),
        Regex("""youtu\.be/([^?&/#\s]+)""", RegexOption.IGNORE_CASE),
        Regex("""[?&]v=([^?&/#\s]+)""", RegexOption.IGNORE_CASE)
    )

    for (pattern in patterns) {
        val match = pattern.find(trimmed)
        if (match != null && match.groupValues.size > 1) {
            val id = match.groupValues[1].trim()
            if (id.isNotBlank()) {
                android.util.Log.d("LivePlayerDebug", "Extracted Video ID ($id) using pattern: ${pattern.pattern}")
                return id
            }
        }
    }

    // Substring fallback
    val fallbackId = when {
        trimmed.contains("watch?v=") -> trimmed.substringAfter("watch?v=").substringBefore("&").substringBefore("?").substringBefore("#")
        trimmed.contains("youtu.be/") -> trimmed.substringAfter("youtu.be/").substringBefore("?").substringBefore("&").substringBefore("#")
        trimmed.contains("youtube.com/live/") -> trimmed.substringAfter("youtube.com/live/").substringBefore("?").substringBefore("&").substringBefore("#")
        trimmed.contains("youtube.com/shorts/") -> trimmed.substringAfter("youtube.com/shorts/").substringBefore("?").substringBefore("&").substringBefore("#")
        trimmed.contains("youtube.com/embed/") -> trimmed.substringAfter("youtube.com/embed/").substringBefore("?").substringBefore("&").substringBefore("#")
        !trimmed.contains("/") && !trimmed.contains("?") -> trimmed
        else -> {
            if (trimmed.contains("/")) {
                val lastSegment = trimmed.substringAfterLast("/").substringBefore("?").substringBefore("&")
                if (lastSegment.length == 11) lastSegment else ""
            } else ""
        }
    }.trim()

    android.util.Log.d("LivePlayerDebug", "Fallback extracted ID: $fallbackId")
    return fallbackId
}

// Helper to open video in YouTube app or default browser fallback
fun openInYouTubeAppOrBrowser(context: Context, videoId: String) {
    if (videoId.isBlank()) return
    val webUri = Uri.parse("https://www.youtube.com/watch?v=$videoId")
    val appUri = Uri.parse("vnd.youtube:$videoId")

    val youtubePackageIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
        setPackage("com.google.android.youtube")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    val youtubeUriIntent = Intent(Intent.ACTION_VIEW, appUri).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    val browserIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    try {
        android.util.Log.d("LivePlayerDebug", "Attempting to launch YouTube app via package...")
        context.startActivity(youtubePackageIntent)
    } catch (e: Exception) {
        try {
            android.util.Log.d("LivePlayerDebug", "Attempting to launch YouTube app via vnd.youtube URI...")
            context.startActivity(youtubeUriIntent)
        } catch (e2: Exception) {
            try {
                android.util.Log.d("LivePlayerDebug", "YouTube app unavailable, launching default browser...")
                context.startActivity(browserIntent)
            } catch (e3: Exception) {
                android.util.Log.e("LivePlayerDebug", "Failed to open browser: ${e3.localizedMessage}")
            }
        }
    }
}

// ==========================================
// 1. STUDENT LIVE CLASSES DASHBOARD SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveClassScreen(
    viewModel: AcademyViewModel,
    onBack: () -> Unit
) {
    com.example.util.TrackStudyModule(com.example.util.StudyTracker.MODULE_LIVE_CLASSES)

    val liveClasses by viewModel.allLiveClasses.collectAsStateWithLifecycle(emptyList())
    var activeClassForPlayer by remember { mutableStateOf<LiveClassEntity?>(null) }

    LaunchedEffect(Unit) {
        viewModel.syncFromRemote()
    }

    if (activeClassForPlayer != null) {
        LivePlayerScreen(
            liveClass = activeClassForPlayer!!,
            viewModel = viewModel,
            onBack = {
                viewModel.leaveLiveClass()
                activeClassForPlayer = null
            }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("🔴 Live Classes (लाइव कक्षाएं)", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.White
                    )
                )
            },
            containerColor = Color(0xFFF8FAFC)
        ) { innerPadding ->
            if (liveClasses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(
                            Icons.Default.LiveTv,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = Color.LightGray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No Live Classes scheduled yet.",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.Gray
                        )
                        Text(
                            "Check back soon or stay tuned for push notifications!",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(liveClasses) { liveClass ->
                        LiveClassItem(
                            liveClass = liveClass,
                            viewModel = viewModel,
                            onJoin = {
                                activeClassForPlayer = liveClass
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LiveClassItem(
    liveClass: LiveClassEntity,
    viewModel: AcademyViewModel,
    onJoin: () -> Unit
) {
    val context = LocalContext.current
    val storageService = remember { MediaStorageServiceFactory.getService(context) }
    val thumbUrl = liveClass.effectiveThumbnailUrl
    val resolvedThumbnail = remember(thumbUrl) {
        storageService.resolveMediaUrl(thumbUrl)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("live_class_card_${liveClass.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Header: Status Badge & Category/Subject info
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(Color(0xFFE2E8F0))
            ) {
                if (resolvedThumbnail.isNotBlank()) {
                    AsyncImage(
                        model = resolvedThumbnail,
                        contentDescription = "Live Class Thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1E293B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.LiveTv, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Lakshya Live Smart Classroom", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
                        }
                    }
                }

                // Blinking or static Badge on top-left
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    when (liveClass.status) {
                        "Live" -> {
                            Surface(
                                color = Color.Red,
                                shape = RoundedCornerShape(6.dp),
                                shadowElevation = 4.dp
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.LiveTv,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "🔴 LIVE NOW",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                        "Ended" -> {
                            Surface(
                                color = Color(0xFF64748B),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "RECORDED",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        else -> { // Scheduled
                            Surface(
                                color = Color(0xFF6366F1),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "📅 SCHEDULED",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Subject Pill top-right
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    Text(
                        liveClass.subject.uppercase(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Text Info Section
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = liveClass.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF0F172A),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (liveClass.description.isNotBlank()) {
                    Text(
                        text = liveClass.description,
                        fontSize = 13.sp,
                        color = Color.Gray,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Divider(color = Color.LightGray.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        // Teacher Name with mini Icon
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = BrandBluePrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = liveClass.teacherName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF334155)
                            )
                        }
                        
                        // Chapter
                        if (liveClass.chapter.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                                Icon(
                                    Icons.Default.Book,
                                    contentDescription = null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Chapter: ${liveClass.chapter}",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }

                    // Date & Time
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = liveClass.scheduledDate,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = Color(0xFF475569)
                        )
                        Text(
                            text = liveClass.scheduledTime,
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Primary Action Button
                when (liveClass.status) {
                    "Live" -> {
                        Button(
                            onClick = {
                                viewModel.joinLiveClass()
                                onJoin()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_join_live_${liveClass.id}"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("JOIN LIVE CLASS NOW", fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
                            }
                        }
                    }
                    "Ended" -> {
                        val videoUrl = liveClass.recordingUri.ifBlank { "https://www.youtube.com/watch?v=${liveClass.effectiveYoutubeId}" }
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open video URL", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("btn_watch_recording_${liveClass.id}"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("WATCH LECTURE RECORDING", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    else -> { // Scheduled
                        var isReminded by remember { mutableStateOf(false) }
                        Button(
                            onClick = {
                                isReminded = !isReminded
                                Toast.makeText(
                                    context,
                                    if (isReminded) "⏰ Notification alert set for ${liveClass.title}!" else "Notification alert cancelled",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("btn_set_reminder_${liveClass.id}"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isReminded) Color(0xFF10B981) else Color(0xFF6366F1)
                            )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (isReminded) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (isReminded) "NOTIFICATION SET" else "SET SCHEDULE REMINDER",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


// ==========================================
// 2. EMBEDDED YOUTUBE LIVE PLAYER & STATE INTERFACES
// ==========================================
enum class LivePlayerStatus {
    LOADING_URL,
    INITIALIZING_PLAYER,
    READY,
    PLAYING,
    ERROR_INVALID_URL,
    ERROR_NO_VIDEO_ID,
    ERROR_PLAYER_FAILED
}

class YouTubeWebInterface(
    private val onReady: () -> Unit,
    private val onPlaying: () -> Unit,
    private val onError: (code: Int, msg: String) -> Unit,
    private val view: WebView
) {
    @android.webkit.JavascriptInterface
    fun playerReady() {
        android.util.Log.d("LivePlayerDebug", "onReady(): JS Player Ready Callback")
        view.post { onReady() }
    }

    @android.webkit.JavascriptInterface
    fun playerPlaying() {
        android.util.Log.d("LivePlayerDebug", "Playback started: JS Player Playing Callback")
        view.post { onPlaying() }
    }

    @android.webkit.JavascriptInterface
    fun playerError(code: Int) {
        android.util.Log.d("LivePlayerDebug", "onError(): JS Player Error Callback: $code")
        val msg = when (code) {
            2 -> "Invalid HTML5 parameter"
            5 -> "HTML5 player error"
            100 -> "Video not found or removed"
            101, 150, 152 -> "Embedding restricted by video owner (Error $code)"
            else -> "YouTube error code $code"
        }
        view.post { onError(code, msg) }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EmbeddedYoutubePlayer(
    videoId: String,
    originalUrl: String = "",
    onReady: () -> Unit,
    onPlaying: () -> Unit,
    onError: (code: Int, msg: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val embedUrl = "https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1"
    
    // Log original URL, extracted Video ID, and generated embed URL as required
    android.util.Log.d("LivePlayerDebug", "Original URL: $originalUrl")
    android.util.Log.d("LivePlayerDebug", "Extracted Video ID: $videoId")
    android.util.Log.d("LivePlayerDebug", "Generated Embed URL: $embedUrl")

    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                webViewRef.value = this
                setLayerType(android.view.View.LAYER_TYPE_NONE, null)

                val cookieManager = android.webkit.CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        android.util.Log.d("LivePlayerDebug", "WebView: Page Finished: $url | HW Accel=${view?.isHardwareAccelerated}")
                    }

                    override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                        super.onReceivedError(view, request, error)
                        val msg = "WebView Error: ${error?.description}"
                        android.util.Log.e("LivePlayerDebug", "$msg for URL: ${request?.url}")
                        if (request?.isForMainFrame == true) {
                            post { onError(-1, msg) }
                        }
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun getDefaultVideoPoster(): android.graphics.Bitmap? {
                        return android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
                    }

                    override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                        android.util.Log.d("LivePlayerDebug", "JS Console: ${consoleMessage?.message()}")
                        return true
                    }
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                    WebView.setWebContentsDebuggingEnabled(true)
                }
                settings.apply {
                    javaScriptEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    domStorageEnabled = true
                    databaseEnabled = true
                    javaScriptCanOpenWindowsAutomatically = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    allowFileAccess = true
                    allowContentAccess = true
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }
                }
                
                addJavascriptInterface(YouTubeWebInterface(
                    onReady = onReady,
                    onPlaying = onPlaying,
                    onError = onError,
                    view = this
                ), "Android")

                val embedHtml = """
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                        <style>
                            body, html { margin:0; padding:0; background-color:#000; width:100%; height:100%; overflow:hidden; }
                            #player { width:100%; height:100%; }
                        </style>
                    </head>
                    <body>
                        <div id="player"></div>
                        <script>
                            console.log("YouTube API Loading for Video ID: $videoId");
                            var tag = document.createElement('script');
                            tag.src = "https://www.youtube.com/iframe_api";
                            var firstScriptTag = document.getElementsByTagName('script')[0];
                            firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

                            var player;
                            function onYouTubeIframeAPIReady() {
                                console.log("YouTube API Ready. Initializing Player for: $videoId");
                                player = new YT.Player('player', {
                                    height: '100%',
                                    width: '100%',
                                    videoId: '$videoId',
                                    playerVars: {
                                        'autoplay': 1,
                                        'controls': 1,
                                        'rel': 0,
                                        'playsinline': 1,
                                        'modestbranding': 1,
                                        'enablejsapi': 1,
                                        'origin': 'https://www.youtube.com'
                                    },
                                    events: {
                                        'onReady': onPlayerReady,
                                        'onStateChange': onPlayerStateChange,
                                        'onError': onPlayerError
                                    }
                                });
                            }

                            function onPlayerReady(event) {
                                console.log("Player Object Ready");
                                event.target.playVideo();
                                if (window.Android) {
                                    window.Android.playerReady();
                                }
                            }

                            function onPlayerStateChange(event) {
                                console.log("Player State Change: " + event.data);
                                if (event.data == YT.PlayerState.PLAYING) {
                                    if (window.Android) {
                                        window.Android.playerPlaying();
                                    }
                                }
                            }

                            function onPlayerError(event) {
                                console.log("Player Error Code: " + event.data);
                                if (window.Android) {
                                    window.Android.playerError(event.data);
                                }
                            }
                        </script>
                    </body>
                    </html>
                """.trimIndent()
                loadDataWithBaseURL("https://www.youtube.com", embedHtml, "text/html", "UTF-8", null)
            }
        },
        modifier = modifier,
        update = { /* WebView internally managed */ }
    )
}

@Composable
fun LivePlayerScreen(
    liveClass: LiveClassEntity,
    viewModel: AcademyViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var playerStatus by remember { mutableStateOf(LivePlayerStatus.LOADING_URL) }
    var exactErrorMessage by remember { mutableStateOf("") }
    var extractedVideoId by remember { mutableStateOf("") }
    var originalLiveUrl by remember { mutableStateOf("") }

    // Logcat helper
    fun logDebug(msg: String) {
        android.util.Log.d("LivePlayerDebug", msg)
    }

    // Auto-load Live URL from Supabase on start
    LaunchedEffect(liveClass.id) {
        logDebug("Live Player Screen Opened")
        logDebug("Join Live Clicked for ID: ${liveClass.id}")
        playerStatus = LivePlayerStatus.LOADING_URL
        try {
            val freshClass = viewModel.getLiveClassFromSupabase(liveClass.id)
            val liveUrl = freshClass?.effectiveYoutubeId ?: liveClass.effectiveYoutubeId
            originalLiveUrl = liveUrl
            logDebug("Live URL Loaded: $liveUrl")

            if (liveUrl.isBlank()) {
                playerStatus = LivePlayerStatus.ERROR_INVALID_URL
                exactErrorMessage = "Invalid Live URL"
                logDebug("Playback Failed: Live URL is blank")
                return@LaunchedEffect
            }

            val vidId = extractYoutubeId(liveUrl)
            if (vidId.isBlank()) {
                playerStatus = LivePlayerStatus.ERROR_NO_VIDEO_ID
                exactErrorMessage = "Unable to detect Video ID"
                logDebug("Playback Failed: Video ID cannot be extracted from $liveUrl")
                return@LaunchedEffect
            }

            extractedVideoId = vidId
            val embedUrl = "https://www.youtube.com/embed/$vidId?autoplay=1&playsinline=1"

            logDebug("Original URL: $liveUrl")
            logDebug("Extracted Video ID: $vidId")
            logDebug("Generated Embed URL: $embedUrl")

            playerStatus = LivePlayerStatus.INITIALIZING_PLAYER
            logDebug("Detected player implementation: WebView + YouTube IFrame API")
            logDebug("Player Initializing for Video ID: $extractedVideoId")

        } catch (e: Exception) {
            playerStatus = LivePlayerStatus.ERROR_PLAYER_FAILED
            exactErrorMessage = e.localizedMessage ?: "Unknown error"
            logDebug("Playback Failed: Exact Exception: ${e.localizedMessage}")
            e.printStackTrace()
        }
    }

    // Handle orientation lock dynamically
    var isLandscapeFullScreen by remember { mutableStateOf(false) }
    val activity = context as? android.app.Activity
    DisposableEffect(isLandscapeFullScreen) {
        if (isLandscapeFullScreen) {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        // Player container (fixed 16:9 in Portrait, fillMaxSize in Landscape)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (isLandscapeFullScreen) 2.1f else 1.77f)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (extractedVideoId.isNotBlank() && (playerStatus == LivePlayerStatus.INITIALIZING_PLAYER || playerStatus == LivePlayerStatus.READY || playerStatus == LivePlayerStatus.PLAYING)) {
                EmbeddedYoutubePlayer(
                    videoId = extractedVideoId,
                    originalUrl = originalLiveUrl,
                    onReady = {
                        playerStatus = LivePlayerStatus.READY
                        logDebug("Player Ready")
                    },
                    onPlaying = {
                        playerStatus = LivePlayerStatus.PLAYING
                        logDebug("Playback Started")
                    },
                    onError = { errorCode, errorMsg ->
                        playerStatus = LivePlayerStatus.ERROR_PLAYER_FAILED
                        exactErrorMessage = errorMsg
                        logDebug("Playback Failed (Code $errorCode): $errorMsg")
                        
                        if (errorCode == 101 || errorCode == 150 || errorCode == 152) {
                            logDebug("Embedding disabled (Error $errorCode). Automatically launching YouTube app or browser...")
                            Toast.makeText(context, "Embedding restricted. Opening in YouTube...", Toast.LENGTH_LONG).show()
                            openInYouTubeAppOrBrowser(context, extractedVideoId)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            when (playerStatus) {
                LivePlayerStatus.LOADING_URL, LivePlayerStatus.INITIALIZING_PLAYER -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.background(Color.Black)) {
                        CircularProgressIndicator(color = Color.Red)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (playerStatus == LivePlayerStatus.LOADING_URL) "Loading Live URL..." else "Initializing Live Player...",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
                LivePlayerStatus.ERROR_INVALID_URL -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(Icons.Default.Error, contentDescription = "Error", tint = Color.Red, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Invalid Live URL", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
                LivePlayerStatus.ERROR_NO_VIDEO_ID -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(Icons.Default.Error, contentDescription = "Error", tint = Color.Red, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Unable to detect Video ID", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
                LivePlayerStatus.ERROR_PLAYER_FAILED -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(Icons.Default.Error, contentDescription = "Error", tint = Color.Red, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Playback Failed", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(exactErrorMessage, color = Color.LightGray, fontSize = 12.sp, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { openInYouTubeAppOrBrowser(context, extractedVideoId) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open in YouTube App", color = Color.White)
                        }
                    }
                }
                else -> { /* Rendered by EmbeddedYoutubePlayer above or nothing needed */ }
            }

            // Header overlays (back, fullscreen toggle, connection recovery)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Refresh Stream recovery button
                    IconButton(
                        onClick = {
                            Toast.makeText(context, "Reconnecting stream player...", Toast.LENGTH_SHORT).show()
                            playerStatus = LivePlayerStatus.LOADING_URL
                            scope.launch {
                                val freshClass = viewModel.getLiveClassFromSupabase(liveClass.id)
                                val liveUrl = freshClass?.effectiveYoutubeId ?: liveClass.effectiveYoutubeId
                                val vidId = extractYoutubeId(liveUrl)
                                if (vidId.isNotBlank()) {
                                    extractedVideoId = ""
                                    delay(100)
                                    extractedVideoId = vidId
                                    playerStatus = LivePlayerStatus.INITIALIZING_PLAYER
                                }
                            }
                        },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Auto-Reconnect", tint = Color.White)
                    }

                    // Fullscreen toggle
                    IconButton(
                        onClick = { isLandscapeFullScreen = !isLandscapeFullScreen },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isLandscapeFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = "Fullscreen",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        if (!isLandscapeFullScreen) {
            // Meta Info Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color.Red,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "LIVE",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Live Streaming",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = liveClass.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = "${liveClass.subject} | Teacher: ${liveClass.teacherName}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Live Chat is not implemented, section hidden per instructions.
        }
    }
}


// ==========================================
// 3. ADMIN LIVE CLASS CMS PANEL
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLiveClassScreen(viewModel: AcademyViewModel) {
    val liveClasses by viewModel.allLiveClasses.collectAsStateWithLifecycle(emptyList())
    val context = LocalContext.current

    var showCreateDialog by remember { mutableStateOf(false) }
    var classToEdit by remember { mutableStateOf<LiveClassEntity?>(null) }
    
    LaunchedEffect(Unit) {
        viewModel.syncFromRemote()
        viewModel.refreshLiveClassesStatus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📡 Live Class CMS", fontWeight = FontWeight.Bold, color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A)
                ),
                actions = {
                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = "Create", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Schedule Live", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        },
        containerColor = Color(0xFFF1F5F9)
    ) { innerPadding ->
        if (liveClasses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Podcasts, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No live lectures configured.", color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text("Tap 'Schedule Live' to establish your first stream session.", fontSize = 12.sp, color = Color.Gray)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(liveClasses) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = when (item.status) {
                                        "Live" -> Color.Red
                                        "Ended" -> Color.DarkGray
                                        else -> Color(0xFF6366F1)
                                    },
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        when (item.status) {
                                            "Live" -> "🔴 LIVE NOW"
                                            "Ended" -> "ENDED"
                                            else -> "📅 UPCOMING"
                                        },
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    // Start Live action (if scheduled or ended)
                                    if (item.status != "Live") {
                                        IconButton(onClick = {
                                            viewModel.startLiveClass(item)
                                            Toast.makeText(context, "Class live stream initiated!", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Start Stream", tint = Color(0xFF10B981))
                                        }
                                    } else {
                                        // End Live action
                                        IconButton(onClick = {
                                            viewModel.endLiveClass(item)
                                            Toast.makeText(context, "Class live stream ended.", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(Icons.Default.Stop, contentDescription = "Stop Stream", tint = Color.Red)
                                        }
                                    }

                                    IconButton(onClick = { classToEdit = item }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF4F46E5))
                                    }

                                    IconButton(onClick = {
                                        viewModel.deleteLiveClass(item.id)
                                        Toast.makeText(context, "Live class deleted", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(item.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("YouTube Link: https://www.youtube.com/watch?v=${item.effectiveYoutubeId}", fontSize = 11.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }

    // CREATE DIALOG
    if (showCreateDialog) {
        LiveClassFormDialog(
            onDismiss = { showCreateDialog = false },
            onSubmit = { url ->
                viewModel.createLiveClassFromUrl(url) { success, message ->
                    showCreateDialog = false
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    // EDIT DIALOG
    if (classToEdit != null) {
        LiveClassFormDialog(
            liveClass = classToEdit,
            onDismiss = { classToEdit = null },
            onSubmit = { url ->
                viewModel.updateLiveClassFromUrl(classToEdit!!.id, url) { success, message ->
                    classToEdit = null
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}

@Composable
fun LiveClassFormDialog(
    liveClass: LiveClassEntity? = null,
    onDismiss: () -> Unit,
    onSubmit: (youtubeUrl: String) -> Unit
) {
    var youtubeUrl by remember {
        mutableStateOf(
            if (liveClass != null && liveClass.effectiveYoutubeId.isNotBlank())
                "https://www.youtube.com/watch?v=${liveClass.effectiveYoutubeId}"
            else ""
        )
    }
    var isProcessing by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = { Text(if (liveClass == null) "Schedule New Live Class" else "Edit Live Class URL", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Paste YouTube Live URL (or Video URL). Title, thumbnail, and live status will be fetched automatically.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                OutlinedTextField(
                    value = youtubeUrl,
                    onValueChange = { youtubeUrl = it },
                    label = { Text("YouTube Live URL (यूट्यूब लाइव लिंक)") },
                    placeholder = { Text("https://www.youtube.com/watch?v=... or live ID") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isProcessing,
                    singleLine = true
                )

                if (isProcessing) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Fetching details from YouTube...", fontSize = 12.sp, color = Color.DarkGray)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (youtubeUrl.isNotBlank()) {
                        isProcessing = true
                        onSubmit(youtubeUrl.trim())
                    }
                },
                enabled = youtubeUrl.isNotBlank() && !isProcessing,
                colors = ButtonDefaults.buttonColors(containerColor = BrandBluePrimary)
            ) {
                Text("Go Live 🚀")
            }
        },
        dismissButton = {
            if (!isProcessing) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
