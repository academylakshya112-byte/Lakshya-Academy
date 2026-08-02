package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import coil.compose.AsyncImage
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import com.example.R
import com.example.data.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.AcademyViewModel
import kotlinx.coroutines.delay

fun formatQuestionOrOptionText(text: String, language: String): String {
    val cleanText = if (text.contains("---METADATA---")) {
        text.substringBefore("---METADATA---").trim()
    } else {
        text
    }
    if (!cleanText.contains(" / ")) {
        return cleanText
    }
    val parts = cleanText.split(" / ", limit = 2)
    return when (language) {
        "ENG" -> parts[0].trim()
        "HIN" -> parts.getOrNull(1)?.trim() ?: parts[0].trim()
        else -> cleanText
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: AcademyViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentUser = viewModel.currentUser
    val darkTheme = viewModel.darkThemeEnabled
    val isInternetAvailable = viewModel.isInternetConnectionAvailable

    var showSplash by remember { mutableStateOf(true) }

    MyApplicationTheme(darkTheme = darkTheme) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (showSplash) {
                PremiumSplashScreen(onAnimationComplete = { showSplash = false })
            } else {
                AppUpdateSystemHandler(viewModel = viewModel) {
                    if (!isInternetAvailable) {
                        NoInternetScreen(onRetry = { viewModel.checkInternetStatus() })
                    } else if (currentUser == null) {
                        AuthScreen(
                            viewModel = viewModel
                        )
                    } else {
                        if (currentUser.role == "ADMIN") {
                            AdminMainContainer(viewModel = viewModel)
                        } else {
                            StudentMainContainer(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}

// === MAIN STUDENT CONTAINER ===
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentMainContainer(viewModel: AcademyViewModel) {
    var studentTab by remember { mutableStateOf("home") } // home, courses, profile
    var activeStudyCourse by remember { mutableStateOf<CourseEntity?>(null) }
    var activeDoubtSubject by remember { mutableStateOf<String?>(null) }
    var activeDoubtChapter by remember { mutableStateOf<String?>(null) }
    var activeWebViewUrl by remember { mutableStateOf<String?>(null) }


    val context = LocalContext.current
    LaunchedEffect(viewModel.currentUser) {
        val u = viewModel.currentUser
        if (u != null) {
        }
    }

    Scaffold(
        bottomBar = {
            if (activeStudyCourse == null && studentTab != "doubt_solver" && activeWebViewUrl == null) {
                NavigationBar {
                    NavigationBarItem(
                        selected = studentTab == "home",
                        onClick = { studentTab = "home" },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = studentTab == "courses",
                        onClick = { studentTab = "courses" },
                        icon = { Icon(Icons.Default.LibraryBooks, contentDescription = null) },
                        label = { Text("My Study") }
                    )
                    NavigationBarItem(
                        selected = studentTab == "profile",
                        onClick = { studentTab = "profile" },
                        icon = { Icon(Icons.Default.Person, contentDescription = null) },
                        label = { Text("Profile") }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(if (studentTab == "doubt_solver" || activeWebViewUrl != null) androidx.compose.foundation.layout.PaddingValues(0.dp) else innerPadding)) {
            if (activeWebViewUrl != null) {
                StudyWebsiteWebViewScreen(
                    url = activeWebViewUrl!!,
                    onBack = { activeWebViewUrl = null }
                )
            } else if (activeStudyCourse != null) {
                StudentR2VideosScreen(
                    viewModel = viewModel,
                    initialBatch = activeStudyCourse!!.title,
                    onBack = { activeStudyCourse = null },
                    onNavigateToDoubtSolver = { subject, chapter ->
                        activeDoubtSubject = subject
                        activeDoubtChapter = chapter
                        studentTab = "doubt_solver"
                        activeStudyCourse = null
                    }
                )
            } else {
                when (studentTab) {
                    "home" -> StudentHomeDashboard(
                        viewModel = viewModel,
                        onTabSelect = { studentTab = it },
                        onPlayCourse = { activeStudyCourse = it },
                        onOpenWebsite = { activeWebViewUrl = it }
                    )
                    "courses" -> StudentMyCourses(
                        viewModel = viewModel,
                        onPlayCourse = { activeStudyCourse = it }
                    )
                    "live_classes" -> LiveClassScreen(
                        viewModel = viewModel,
                        onBack = { studentTab = "home" }
                    )
                    "r2_videos" -> StudentR2VideosScreen(
                        viewModel = viewModel,
                        onBack = { studentTab = "home" },
                        onNavigateToDoubtSolver = { subject, chapter ->
                            activeDoubtSubject = subject
                            activeDoubtChapter = chapter
                            studentTab = "doubt_solver"
                        }
                    )
                    "DASHBOARD" -> StudentProgressDashboard(
                        viewModel = viewModel,
                        onBack = { studentTab = "home" }
                    )
                    "study_websites" -> StudentStudyWebsitesScreen(
                        viewModel = viewModel,
                        onOpenWebsite = { activeWebViewUrl = it },
                        onBack = { studentTab = "home" }
                    )
                    "profile" -> StudentProfileView(viewModel = viewModel)
                    "TESTS" -> StudentTestHub(viewModel = viewModel)
                    "doubt_solver" -> LakshyaAiScreen(
                        viewModel = viewModel,
                        initialChapterContext = activeDoubtChapter,
                        initialSubjectContext = activeDoubtSubject,
                        onBack = {
                            if (activeDoubtChapter != null) {
                                activeDoubtChapter = null
                                activeDoubtSubject = null
                            }
                            studentTab = "home"
                        }
                    )
                    "firebase_auth" -> FirebaseLoginScreen(onBack = { studentTab = "home" })
                }
            }
        }
    }
}

// === MAIN ADMIN CONTAINER ===
@Composable
fun AdminMainContainer(viewModel: AcademyViewModel) {
    var adminTab by remember { mutableStateOf("DASHBOARD") }
    val context = LocalContext.current
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                val tabs = listOf(
                    Triple("DASHBOARD", "Analytics", Icons.Default.Analytics),
                    Triple("COURSES", "Courses", Icons.Default.LibraryAdd),
                    Triple("VIDEOS", "R2 Videos", Icons.Default.CloudUpload),
                    Triple("LIVE", "Live Classes", Icons.Default.LiveTv),
                    Triple("BANNERS", "Banners", Icons.Default.Image),
                    Triple("TEST_SERIES", "Test Series", Icons.Default.Quiz),
                    Triple("ALERTS", "Alerts", Icons.Default.Notifications),
                    Triple("WEBSITES", "Websites", Icons.Default.Language),
                    Triple("POPUP", "Popup", Icons.Default.Campaign)
                )
                tabs.forEach { (route, label, icon) ->
                    NavigationBarItem(
                        selected = adminTab == route,
                        onClick = { adminTab = route },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 8.sp, maxLines = 1) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            AdminTopAnnouncer(logout = { viewModel.logout() })
            when (adminTab) {
                "DASHBOARD" -> AdminAnalyticsDashboard(viewModel = viewModel)
                "COURSES" -> AdminCourseManager(viewModel = viewModel)
                "VIDEOS" -> AdminR2UploadScreen(viewModel = viewModel)
                "LIVE" -> AdminLiveClassScreen(viewModel = viewModel)
                "BANNERS" -> AdminBannerManager(viewModel = viewModel)
                "TEST_SERIES" -> AdminTestSeriesManager(viewModel = viewModel)
                "ALERTS" -> AdminNotificationAlerts(viewModel = viewModel)
                "WEBSITES" -> AdminStudyWebsiteScreen(viewModel = viewModel)
                "POPUP" -> AdminCommunityPopupScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun AdminTopAnnouncer(logout: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF0F172A)).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White)) {
                    AsyncImage(model = R.drawable.lakshya_ghazipur_hd_1785220211284, contentDescription = null, modifier = Modifier.fillMaxSize().scale(1.4f), contentScale = ContentScale.Crop)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Lakshya Admin", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Session 2026", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                }
            }
            IconButton(onClick = logout) { Icon(Icons.Default.ExitToApp, contentDescription = null, tint = Color.LightGray) }
        }
    }
}

@Composable
fun DashboardBrandHeader(studentName: String, photoUri: String = "") {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Lakshya Academy", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("Namaste $studentName! 👋", fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
            Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(Color.White).border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                if (photoUri.isNotBlank()) {
                    AsyncImage(model = photoUri, contentDescription = "Profile Photo", modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                } else {
                    AsyncImage(model = R.drawable.lakshya_ghazipur_hd_1785220211284, contentDescription = null, modifier = Modifier.fillMaxSize().scale(1.4f), contentScale = ContentScale.Crop)
                }
            }
        }
    }
}

data class PlatformInfo(val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: Color)

fun getPlatformInfo(linkUrl: String, title: String): PlatformInfo {
    val urlLower = linkUrl.lowercase()
    val titleLower = title.lowercase()
    return when {
        urlLower.contains("whatsapp") || titleLower.contains("whatsapp") -> {
            PlatformInfo(Icons.Default.Chat, Color(0xFF25D366))
        }
        urlLower.contains("t.me") || urlLower.contains("telegram") || titleLower.contains("telegram") -> {
            PlatformInfo(Icons.Default.Send, Color(0xFF229ED9))
        }
        urlLower.contains("instagram") || titleLower.contains("instagram") -> {
            PlatformInfo(Icons.Default.PhotoCamera, Color(0xFFE1306C))
        }
        urlLower.contains("youtube") || titleLower.contains("youtube") -> {
            PlatformInfo(Icons.Default.PlayArrow, Color(0xFFFF0000))
        }
        else -> {
            PlatformInfo(Icons.Default.Link, Color(0xFF6366F1))
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BannerCarousel(banners: List<BannerEntity>, onTabSelect: (String) -> Unit) {
    val context = LocalContext.current
    
    // Default banners specified by the user
    val defaultBanners = remember {
        listOf(
            BannerEntity(
                id = -1,
                title = "Join Our Official Telegram Study Channel! 💎",
                imageUrl = "drawable/img_banner_telegram_1783264931077",
                linkUrl = "https://t.me/+k9fhlPovsDE5ZDI1",
                buttonText = "JOIN NOW",
                description = "Download free PDFs, daily GK questionnaires, exam syllabus & interactive worksheets instantly."
            ),
            BannerEntity(
                id = -2,
                title = "Subscribe to Our YouTube Channel! 📺",
                imageUrl = "drawable/img_banner_youtube_1783264956533",
                linkUrl = "https://youtube.com/@lakshyaacademyofficial100?si=U-dYfGwRQndZAD7A",
                buttonText = "SUBSCRIBE",
                description = "Watch Daily Live Classes, chapter-wise concept lectures and mock-test solutions daily."
            ),
            BannerEntity(
                id = -3,
                title = "Join Our Official WhatsApp Group! 👥",
                imageUrl = "drawable/img_banner_whatsapp_1783264919547",
                linkUrl = "https://chat.whatsapp.com/JDHYEnF8rQP3D0kIeH3Qoj?s=cl&p=a&ilr=2",
                buttonText = "JOIN NOW",
                description = "Stay updated with live class announcements, free PDF notes & community chat!"
            ),
            BannerEntity(
                id = -4,
                title = "Follow Our Instagram for Daily GK Reels! 📲",
                imageUrl = "drawable/img_banner_instagram_1783264944727",
                linkUrl = "https://www.instagram.com/toon_waale_dost12?igsh=MWg1NWprNzltZjJ4dg==",
                buttonText = "FOLLOW",
                description = "Get short tricks, current affairs quiz and exam notification reels directly on Instagram!"
            )
        )
    }

    // Combine database banners with default banners
    val displayBanners = remember(banners) {
        if (banners.isEmpty()) {
            defaultBanners
        } else {
            banners + defaultBanners
        }
    }

    if (displayBanners.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .shadow(8.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF0F172A))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = Color(0xFF6366F1),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No Announcement Available",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
        return
    }

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { displayBanners.size })
    val uriHandler = LocalUriHandler.current

    // Auto-scroll effect: exactly 4 seconds
    LaunchedEffect(displayBanners.size) {
        while (true) {
            delay(4000)
            if (displayBanners.isNotEmpty() && pagerState.pageCount > 0) {
                val nextPage = (pagerState.currentPage + 1) % pagerState.pageCount
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .shadow(12.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
    ) {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val banner = displayBanners[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        if (banner.linkUrl.startsWith("http")) {
                            uriHandler.openUri(banner.linkUrl)
                        } else {
                            onTabSelect(banner.linkUrl)
                        }
                    }
            ) {
                // Background Image
                val imagePainter = when {
                    banner.imageUrl.contains("img_banner_telegram") -> painterResource(R.drawable.img_banner_telegram_1783264931077)
                    banner.imageUrl.contains("img_banner_youtube") -> painterResource(R.drawable.img_banner_youtube_1783264956533)
                    banner.imageUrl.contains("img_banner_whatsapp") -> painterResource(R.drawable.img_banner_whatsapp_1783264919547)
                    banner.imageUrl.contains("img_banner_instagram") -> painterResource(R.drawable.img_banner_instagram_1783264944727)
                    else -> {
                        val resolvedUrl = com.example.service.MediaStorageServiceFactory.getService(context).resolveMediaUrl(banner.imageUrl)
                        val imageModel = if (resolvedUrl.startsWith("/")) java.io.File(resolvedUrl) else resolvedUrl
                        android.util.Log.d("BannerCarousel", "[BANNER RENDERING] ID: ${banner.id}, Title: '${banner.title}'")
                        android.util.Log.d("BannerCarousel", "[BANNER RENDERING] Raw Image URL: '${banner.imageUrl}'")
                        android.util.Log.d("BannerCarousel", "[BANNER RENDERING] Resolved Image URL: '$resolvedUrl'")
                        
                        rememberAsyncImagePainter(
                            model = imageModel,
                            onState = { state ->
                                when (state) {
                                    is coil.compose.AsyncImagePainter.State.Success -> {
                                        android.util.Log.i("BannerCarousel", "[BANNER LOAD SUCCESS] ID: ${banner.id}, URL: '$resolvedUrl'")
                                    }
                                    is coil.compose.AsyncImagePainter.State.Error -> {
                                        android.util.Log.e("BannerCarousel", "[BANNER LOAD FAILED] ID: ${banner.id}, URL: '$resolvedUrl'")
                                        android.util.Log.e("BannerCarousel", "[BANNER LOAD ERROR] Exception: ${state.result.throwable.message}")
                                    }
                                    is coil.compose.AsyncImagePainter.State.Loading -> {
                                        android.util.Log.d("BannerCarousel", "[BANNER LOADING] ID: ${banner.id}")
                                    }
                                    else -> {}
                                }
                            }
                        )
                    }
                }
                
                Image(
                    painter = imagePainter,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // Bottom dark gradient overlay for optimal legibility
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.4f),
                                    Color.Black.copy(alpha = 0.9f)
                                ),
                                startY = 10f
                            )
                        )
                )
                
                // Bottom Overlay Bar containing info and CTA
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        val platformInfo = getPlatformInfo(banner.linkUrl, banner.title)
                        // Rounded Platform Icon Box
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .shadow(4.dp, RoundedCornerShape(14.dp))
                                .clip(RoundedCornerShape(14.dp))
                                .background(platformInfo.color)
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = platformInfo.icon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column(verticalArrangement = Arrangement.Center) {
                            Text(
                                text = banner.title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (banner.description.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = banner.description,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    // Large premium CTA button on the right
                    Box(
                        modifier = Modifier
                            .shadow(4.dp, RoundedCornerShape(50.dp))
                            .clip(RoundedCornerShape(50.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF6366F1), // Indigo
                                        Color(0xFF4F46E5)  // Deep Indigo
                                    )
                                )
                            )
                            .clickable {
                                if (banner.linkUrl.startsWith("http")) {
                                    uriHandler.openUri(banner.linkUrl)
                                } else {
                                    onTabSelect(banner.linkUrl)
                                }
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (banner.buttonText.isNotEmpty()) banner.buttonText.uppercase() else "VIEW",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }

        // Custom indicator capsule at the top-right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .clip(RoundedCornerShape(50.dp))
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(displayBanners.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .width(if (isSelected) 14.dp else 6.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (isSelected) Color(0xFF818CF8) else Color.White.copy(alpha = 0.5f))
                    )
                }
            }
        }
    }
}

@Composable
fun NoInternetScreen(onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.WifiOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
        Text("No Internet Connection", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Button(onClick = onRetry, modifier = Modifier.padding(top = 24.dp)) { Text("Retry") }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(text = title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = BrandBluePrimary, modifier = Modifier.padding(bottom = 12.dp))
}

sealed class ClassroomState {
    object SubjectList : ClassroomState()
    data class TopicFolderList(val subjectName: String) : ClassroomState()
    data class VideoLectureList(val subjectName: String, val folderName: String) : ClassroomState()
    data class LessonMediaCenter(val lesson: LessonEntity) : ClassroomState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentLessonsMediaWorkspace(course: CourseEntity, completedCount: Int, viewModel: AcademyViewModel, onDismiss: () -> Unit) {
    val lessons by viewModel.currentLessonList.collectAsStateWithLifecycle()
    var navStack by remember { mutableStateOf(listOf<ClassroomState>(ClassroomState.SubjectList)) }
    var activePdfReadingLesson by remember { mutableStateOf<LessonEntity?>(null) }
    val currentScreen = navStack.lastOrNull() ?: ClassroomState.SubjectList

    LaunchedEffect(course.id) { viewModel.selectCourse(course) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        var isFullScreen by remember { mutableStateOf(false) }

        val dialogWindow = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        LaunchedEffect(isFullScreen) {
            dialogWindow?.let { window ->
                window.setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                window.decorView.setPadding(0, 0, 0, 0)
                val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                if (isFullScreen) {
                    window.setDimAmount(0f)
                    window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
                    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
                    controller.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars() or androidx.core.view.WindowInsetsCompat.Type.navigationBars() or androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        val attrs = window.attributes
                        attrs.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                        window.attributes = attrs
                    }
                } else {
                    window.setDimAmount(0.5f)
                    window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, true)
                    controller.show(androidx.core.view.WindowInsetsCompat.Type.statusBars() or androidx.core.view.WindowInsetsCompat.Type.navigationBars())
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        val attrs = window.attributes
                        attrs.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                        window.attributes = attrs
                    }
                }
            }
        }
        
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (isFullScreen && currentScreen is ClassroomState.LessonMediaCenter) {
                VideoPlayerView(
                    lesson = currentScreen.lesson, 
                    isFullScreen = isFullScreen,
                    onFullScreenToggle = { isFullScreen = it },
                    isAdmin = (viewModel.currentUser?.role == "ADMIN")
                )
            } else {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
                    topBar = {
                        TopAppBar(
                            title = { Text(course.title) },
                            navigationIcon = { IconButton(onClick = { if (navStack.size > 1) navStack = navStack.dropLast(1) else onDismiss() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
                        )
                    }
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Transparent)
                            .padding(padding)
                    ) {
                        when (currentScreen) {
                            is ClassroomState.SubjectList -> {
                                val subjects = lessons.map { it.chapterName }.distinct()
                                LazyColumn { 
                                    items(subjects) { sub -> 
                                        ListItem(
                                            headlineContent = { Text(sub, fontWeight = FontWeight.SemiBold) },
                                            leadingContent = { Icon(Icons.Default.Folder, tint = Color(0xFFEAB308), contentDescription = null) },
                                            trailingContent = { Icon(Icons.Default.KeyboardArrowRight, null) },
                                            modifier = Modifier.clickable { navStack = navStack + ClassroomState.TopicFolderList(sub) }
                                        )
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                                    } 
                                }
                            }
                            is ClassroomState.TopicFolderList -> {
                                val foldersInSubject = lessons.filter { it.chapterName == currentScreen.subjectName }.map { it.folder }.distinct()
                                LazyColumn {
                                    item { ListItem(headlineContent = { Text(currentScreen.subjectName, color = Color.Gray, fontSize = 12.sp) }) }
                                    items(foldersInSubject) { folder ->
                                        ListItem(
                                            headlineContent = { Text(folder, fontWeight = FontWeight.SemiBold) },
                                            leadingContent = { Icon(Icons.Default.PlayCircleOutline, tint = Color(0xFF6366F1), contentDescription = null) },
                                            trailingContent = { Icon(Icons.Default.KeyboardArrowRight, null) },
                                            modifier = Modifier.clickable { navStack = navStack + ClassroomState.VideoLectureList(currentScreen.subjectName, folder) }
                                        )
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                                    }
                                }
                            }
                            is ClassroomState.VideoLectureList -> {
                                val matching = lessons.filter { it.chapterName == currentScreen.subjectName && it.folder == currentScreen.folderName }
                                LazyColumn {
                                    item { ListItem(headlineContent = { Text("${currentScreen.subjectName} > ${currentScreen.folderName}", color = Color.Gray, fontSize = 12.sp) }) }
                                    items(matching) { lesson ->
                                        LessonSelectionRow(lesson = lesson, onSelect = { navStack = navStack + ClassroomState.LessonMediaCenter(lesson) })
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                                    }
                                }
                            }
                            is ClassroomState.LessonMediaCenter -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    VideoPlayerView(
                                        lesson = currentScreen.lesson, 
                                        isFullScreen = isFullScreen,
                                        onFullScreenToggle = { isFullScreen = it },
                                        isAdmin = (viewModel.currentUser?.role == "ADMIN")
                                    )
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(currentScreen.lesson.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(currentScreen.lesson.chapterName, fontSize = 14.sp, color = Color.Gray, modifier = Modifier.weight(1f))
                                            VideoViewCountDisplay(videoUrl = currentScreen.lesson.videoUrl)
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                        val mediaContext = LocalContext.current
                                        Button(
                                            onClick = { 
                                                activePdfReadingLesson = currentScreen.lesson 
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                                        ) {
                                            Icon(Icons.Default.Description, null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Read Class Notes (PDF)")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (activePdfReadingLesson != null) {
        val link = activePdfReadingLesson!!.pdfUrl
        if (link.startsWith("content://") || link.startsWith("file://")) {
            NativePdfViewerScreen(
                pdfUri = link,
                pdfName = activePdfReadingLesson!!.pdfName,
                fileSize = activePdfReadingLesson!!.fileSize,
                onDismiss = { activePdfReadingLesson = null }
            )
        } else {
            MockPdfViewerScreen(
                pdfName = activePdfReadingLesson!!.pdfName,
                customContent = activePdfReadingLesson!!.pdfContent,
                fileSize = activePdfReadingLesson!!.fileSize,
                onDismiss = { activePdfReadingLesson = null }
            )
        }
    }
}

@Composable
fun LessonSelectionRow(lesson: LessonEntity, onSelect: () -> Unit) {
    ListItem(headlineContent = { Text(lesson.title) }, leadingContent = { Icon(Icons.Default.PlayCircle, null) }, modifier = Modifier.clickable { onSelect() })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentTestHub(viewModel: AcademyViewModel) {
    val activeTestProgress = viewModel.activeTestProgress
    
    if (activeTestProgress != null) {
        if (activeTestProgress.isSubmitted) {
            TestResultScreen(viewModel = viewModel)
        } else {
            ActiveTestScreen(viewModel = viewModel)
        }
    } else {
        StudentTestHubMain(viewModel = viewModel)
    }
}

@Composable
fun StudentTestHubMain(viewModel: AcademyViewModel) {
    LaunchedEffect(Unit) {
        viewModel.generateWeeklyMockTests()
    }

    val testsRaw by viewModel.allTests.collectAsStateWithLifecycle()
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedFilterChip by remember { mutableStateOf("All") }

    val categories = listOf(
        "All", "Weekly Test", "Monthly Test", "Chapter Test", "Unit Test",
        "Practice Test", "Full Syllabus Test", "Model Paper", "Previous Year Paper", "Competitive Mock Test"
    )

    val filterChips = listOf(
        "All", "Class 5th", "Class 6th", "Class 7th", "Class 8th", "Class 9th", "Class 10th",
        "Class 11th", "Class 12th", "Army GD", "SSC", "UP Police", "NEET", "JEE"
    )

    // Filter published tests
    val tests = remember(testsRaw, selectedCategory, selectedFilterChip) {
        testsRaw.filter { test ->
            val isPublished = !test.type.endsWith("- Unpublished")
            val catMatch = if (selectedCategory == "All") true else {
                com.example.ui.screens.getTestCategory(test) == selectedCategory
            }
            val filterMatch = if (selectedFilterChip == "All") true else {
                test.title.contains(selectedFilterChip, ignoreCase = true) ||
                test.type.contains(selectedFilterChip, ignoreCase = true)
            }
            isPublished && catMatch && filterMatch
        }
    }

    val scores by viewModel.allScores.collectAsStateWithLifecycle()
    val userScoreMap = remember(scores, viewModel.currentUser) {
        scores.filter { it.userEmail == viewModel.currentUser?.email }.associateBy { it.testId }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Test Series & Exam Portal", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Comprehensive Test Series for School & Competitive Exams", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        // Categories Scrollable Row
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { cat ->
                val isSelected = selectedCategory == cat
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Class / Exam Filter Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filterChips) { filter ->
                val isSelected = selectedFilterChip == filter
                SuggestionChip(
                    onClick = { selectedFilterChip = filter },
                    label = { Text(filter, fontSize = 11.sp) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (tests.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Quiz, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No tests published for the selected category.", color = Color.Gray, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(tests) { test ->
                    val userScore = userScoreMap[test.id]
                    val cat = com.example.ui.screens.getTestCategory(test)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(cat, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }

                                if (test.hasNegativeMarking) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFFFEE2E2))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("Negative Marking", fontSize = 10.sp, color = Color.Red, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(test.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${test.durationMinutes} Mins", color = Color.Gray, fontSize = 12.sp)
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("50 Questions", color = Color.Gray, fontSize = 12.sp)

                                Spacer(modifier = Modifier.width(16.dp))
                                Text("${test.marksPerCorrect * 50} Marks", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (userScore != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Status: Attempted", fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                                        Text("Score: ${userScore.score} / ${userScore.totalQuestions * test.marksPerCorrect}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { viewModel.startTest(test) },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Re-Attempt", fontSize = 12.sp)
                                        }
                                        Button(
                                            onClick = { viewModel.startTest(test) },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("View Result", fontSize = 12.sp)
                                        }
                                    }
                                }
                            } else {
                                Button(
                                    onClick = { viewModel.startTest(test) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Start Test Now", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveTestScreen(viewModel: AcademyViewModel) {
    val progress = viewModel.activeTestProgress ?: return
    val currentQuestion = progress.questions.getOrNull(progress.currentQuestionIndex)
    var testLanguage by remember { mutableStateOf("BOTH") } // "ENG", "HIN", "BOTH"
    var showPaletteDialog by remember { mutableStateOf(false) }
    var showSubmitConfirmDialog by remember { mutableStateOf(false) }
    
    val testContext = LocalContext.current
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            com.example.util.StudyTracker.addStudyTime(testContext, 1)
        }
    }

    if (showSubmitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSubmitConfirmDialog = false },
            title = { Text("Submit Test?") },
            text = {
                val answeredCount = progress.selectedAnswers.size
                val totalQs = progress.questions.size
                val reviewedCount = progress.reviewedQuestions.size
                Text("Answered: $answeredCount / $totalQs\nMarked for Review: $reviewedCount\n\nAre you sure you want to submit your test now?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSubmitConfirmDialog = false
                        viewModel.submitActiveTest()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("Submit Test")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSubmitConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPaletteDialog) {
        AlertDialog(
            onDismissRequest = { showPaletteDialog = false },
            title = { Text("Question Palette") },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Text("🟢 Answered", fontSize = 11.sp)
                        Text("🟣 Review", fontSize = 11.sp)
                        Text("⚪ Unanswered", fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 280.dp)
                    ) {
                        itemsIndexed(progress.questions) { idx, q ->
                            val isAnswered = progress.selectedAnswers.containsKey(q.id)
                            val isReviewed = progress.reviewedQuestions.contains(q.id)
                            val isCurrent = progress.currentQuestionIndex == idx

                            val bgColor = when {
                                isCurrent -> MaterialTheme.colorScheme.primary
                                isReviewed -> Color(0xFF8B5CF6) // Purple
                                isAnswered -> Color(0xFF10B981) // Green
                                else -> Color.LightGray
                            }

                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(bgColor)
                                    .clickable {
                                        viewModel.updateTestQuestionIndex(idx)
                                        showPaletteDialog = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPaletteDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(progress.test.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp) },
            navigationIcon = {
                IconButton(onClick = { viewModel.exitTest() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit Test")
                }
            },
            actions = {
                IconButton(onClick = { showPaletteDialog = true }) {
                    Icon(Icons.Default.GridOn, contentDescription = "Question Palette", tint = MaterialTheme.colorScheme.primary)
                }
                Box(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (progress.secondsRemaining < 300) Color(0xFFFEE2E2) else Color(0xFFEEF2FF))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (progress.secondsRemaining < 300) Color.Red else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = String.format("%02d:%02d", progress.secondsRemaining / 60, progress.secondsRemaining % 60),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (progress.secondsRemaining < 300) Color.Red else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        )

        LinearProgressIndicator(
            progress = { if (progress.questions.isNotEmpty()) (progress.currentQuestionIndex + 1) / progress.questions.size.toFloat() else 0f },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary
        )

        // Language toggle row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val languages = listOf("BOTH" to "दोनों (Eng & हिन्दी)", "ENG" to "English", "HIN" to "हिन्दी")
            languages.forEach { (langCode, label) ->
                val isSelected = testLanguage == langCode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { testLanguage = langCode }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else Color.DarkGray,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                }
            }
        }
        
        if (currentQuestion != null) {
            val isReviewed = progress.reviewedQuestions.contains(currentQuestion.id)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Question ${progress.currentQuestionIndex + 1} of ${progress.questions.size}",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    OutlinedButton(
                        onClick = { viewModel.toggleReviewQuestion(currentQuestion.id) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isReviewed) Color(0xFFF3E8FF) else Color.Transparent
                        )
                    ) {
                        Icon(
                            if (isReviewed) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (isReviewed) Color(0xFF8B5CF6) else Color.Gray
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (isReviewed) "Marked" else "Mark for Review",
                            fontSize = 11.sp,
                            color = if (isReviewed) Color(0xFF8B5CF6) else Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    formatQuestionOrOptionText(currentQuestion.questionText, testLanguage),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    lineHeight = 24.sp
                )
                Spacer(modifier = Modifier.height(20.dp))
                
                val options = listOf(currentQuestion.optionA, currentQuestion.optionB, currentQuestion.optionC, currentQuestion.optionD)
                options.forEachIndexed { index, optionText ->
                    val isSelected = progress.selectedAnswers[currentQuestion.id] == index
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                viewModel.selectTestAnswer(currentQuestion.id, index)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.White
                        ),
                        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray)
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                formatQuestionOrOptionText(optionText, testLanguage),
                                fontSize = 15.sp
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = { viewModel.clearTestAnswer(currentQuestion.id) },
                        enabled = progress.selectedAnswers.containsKey(currentQuestion.id)
                    ) {
                        Text("Clear Answer", color = Color.Red, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { showSubmitConfirmDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text("Submit Test", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(
                        onClick = { viewModel.updateTestQuestionIndex(progress.currentQuestionIndex - 1) },
                        enabled = progress.currentQuestionIndex > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray, contentColor = Color.Black)
                    ) {
                        Text("Previous")
                    }
                    
                    if (progress.currentQuestionIndex < progress.questions.size - 1) {
                        Button(onClick = { viewModel.updateTestQuestionIndex(progress.currentQuestionIndex + 1) }) {
                            Text("Next")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestResultScreen(viewModel: AcademyViewModel) {
    val progress = viewModel.activeTestProgress ?: return
    val score = progress.testScore ?: return // Must have score to view result
    val scores by viewModel.allScores.collectAsStateWithLifecycle()
    var testLanguage by remember { mutableStateOf("BOTH") } // "ENG", "HIN", "BOTH"
    
    // Performance breakdown
    val totalQs = progress.questions.size
    val correct = score.correctAnswers
    val wrong = score.wrongAnswers
    val skipped = totalQs - (correct + wrong)
    val percentage = if (totalQs > 0) (correct.toFloat() / totalQs) * 100f else 0f
    
    // Dynamic Rank calculation
    val rank = remember(scores) {
        val testScores = scores.filter { it.testId == progress.test.id }.sortedByDescending { it.score }
        val index = testScores.indexOfFirst { it.userEmail == score.userEmail && it.score == score.score }
        if (index != -1) index + 1 else 1
    }
    
    // Time Taken calculation
    val timeTakenSeconds = progress.test.durationMinutes * 60 - progress.secondsRemaining
    val timeTakenStr = "${timeTakenSeconds / 60}m ${timeTakenSeconds % 60}s"
    
    // Subject-wise performance grouping
    val subjectPerformanceList = remember(progress.questions) {
        val questionsBySubject = progress.questions.groupBy { q ->
            try {
                com.example.service.parseQuestionMetadata(q.questionText).subject.split("/")[0].trim()
            } catch (e: Exception) {
                "General"
            }
        }
        questionsBySubject.map { (subject, qList) ->
            var subCorrect = 0
            var subWrong = 0
            qList.forEach { q ->
                val selectedIdx = progress.selectedAnswers[q.id]
                if (selectedIdx == q.correctIndex) {
                    subCorrect++
                } else if (selectedIdx != null && selectedIdx != -1) {
                    subWrong++
                }
            }
            val subTotal = qList.size
            val subPercentage = if (subTotal > 0) (subCorrect.toFloat() / subTotal) * 100f else 0f
            Triple(subject, subCorrect to subTotal, subPercentage)
        }
    }
    
    val strongSubjects = subjectPerformanceList.filter { it.third >= 70f }.map { it.first }
    val weakSubjects = subjectPerformanceList.filter { it.third < 70f }.map { it.first }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Performance Report") },
            navigationIcon = {
                IconButton(onClick = { viewModel.exitTest() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                }
            }
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Dashboard overview card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Score Analysis",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Marks Scored", fontSize = 12.sp, color = Color.Gray)
                            Text("$correct / $totalQs", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color(0xFF6366F1))
                        }
                        Column {
                            Text("Percentage", fontSize = 12.sp, color = Color.Gray)
                            Text(String.format("%.1f%%", percentage), fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color(0xFF10B981))
                        }
                        Column {
                            Text("Rank", fontSize = 12.sp, color = Color.Gray)
                            Text("#$rank", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color(0xFFF59E0B))
                        }
                    }
                    
                    Divider(modifier = Modifier.padding(vertical = 12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Correct", fontSize = 11.sp, color = Color.Gray)
                            Text("$correct", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF10B981))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Wrong", fontSize = 11.sp, color = Color.Gray)
                            Text("$wrong", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.Red)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Skipped", fontSize = 11.sp, color = Color.Gray)
                            Text("$skipped", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.Gray)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Time Taken", fontSize = 11.sp, color = Color.Gray)
                            Text(timeTakenStr, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.DarkGray)
                        }
                    }
                }
            }
            
            // Subject performance card
            if (subjectPerformanceList.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Subject-wise Performance",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        subjectPerformanceList.forEach { (subj, scorePair, pct) ->
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(subj, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                    Text("${scorePair.first}/${scorePair.second} (${String.format("%.0f%%", pct)})", fontSize = 12.sp, color = Color.Gray)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { pct / 100f },
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                                    color = if (pct >= 70f) Color(0xFF10B981) else if (pct >= 40f) Color(0xFFF59E0B) else Color.Red
                                )
                            }
                        }
                    }
                }
            }
            
            // Weak / Strong Subjects tags
            if (strongSubjects.isNotEmpty() || weakSubjects.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Strengths & Focus Areas",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        if (strongSubjects.isNotEmpty()) {
                            Text("Strong Subjects (>= 70%):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF10B981))
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                strongSubjects.forEach { subj ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color(0xFFD1FAE5))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(subj, fontSize = 11.sp, color = Color(0xFF065F46), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        
                        if (weakSubjects.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Needs Focus (< 70%):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.Red)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                weakSubjects.forEach { subj ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color(0xFFFEE2E2))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(subj, fontSize = 11.sp, color = Color(0xFF991B1B), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Language toggle row for review
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val languages = listOf("BOTH" to "दोनों (Eng & हिन्दी)", "ENG" to "English", "HIN" to "हिन्दी")
                languages.forEach { (langCode, label) ->
                    val isSelected = testLanguage == langCode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) Color(0xFF6366F1) else Color.Transparent)
                            .clickable { testLanguage = langCode }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else Color.DarkGray,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Detailed Question Review:", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            
            progress.questions.forEachIndexed { i, q ->
                val selectedIdx = progress.selectedAnswers[q.id]
                val isCorrect = selectedIdx == q.correctIndex
                val metadata = remember(q.questionText) {
                    try {
                        com.example.service.parseQuestionMetadata(q.questionText)
                    } catch (e: Exception) {
                        null
                    }
                }
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Q${i+1}. ${formatQuestionOrOptionText(q.questionText, testLanguage)}", fontWeight=FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        val ops = listOf(q.optionA, q.optionB, q.optionC, q.optionD)
                        ops.forEachIndexed { opIdx, opTxt ->
                            val isCorrectAnswer = (q.correctIndex == opIdx)
                            val isUserSelected = (selectedIdx == opIdx)
                            
                            val bgColor = when {
                                isCorrectAnswer -> Color(0xFFD1FAE5) // Green background for the actual correct answer
                                isUserSelected && !isCorrectAnswer -> Color(0xFFFEE2E2) // Red background if user selected wrong
                                else -> Color.Transparent
                             }
                            
                             Row(modifier = Modifier.fillMaxWidth().background(bgColor, RoundedCornerShape(4.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                 val ic = when {
                                     isCorrectAnswer -> Icons.Default.CheckCircle
                                     isUserSelected -> Icons.Default.Cancel
                                     else -> Icons.Default.RadioButtonUnchecked
                                 }
                                 val cColor = when {
                                     isCorrectAnswer -> Color(0xFF10B981)
                                     isUserSelected -> Color.Red
                                     else -> Color.Gray
                                 }
                                 Icon(ic, null, modifier = Modifier.size(16.dp), tint = cColor)
                                 Spacer(modifier = Modifier.width(8.dp))
                                 Text(formatQuestionOrOptionText(opTxt, testLanguage), color = if(isCorrectAnswer || isUserSelected) Color.Black else Color.Gray)
                             }
                             Spacer(modifier = Modifier.height(4.dp))
                         }
                         
                         // Detailed solution & explanation
                         if (metadata != null && metadata.explanation.isNotBlank()) {
                             Spacer(modifier = Modifier.height(12.dp))
                             Box(
                                 modifier = Modifier
                                     .fillMaxWidth()
                                     .clip(RoundedCornerShape(8.dp))
                                     .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                     .padding(12.dp)
                             ) {
                                 Column {
                                     Text(
                                         text = "Detailed Solution / समाधान:",
                                         fontWeight = FontWeight.Bold,
                                         fontSize = 12.sp,
                                         color = MaterialTheme.colorScheme.primary
                                     )
                                     Spacer(modifier = Modifier.height(4.dp))
                                     Text(
                                         text = formatQuestionOrOptionText(metadata.explanation, testLanguage),
                                         fontSize = 12.sp,
                                         color = MaterialTheme.colorScheme.onSurface
                                     )
                                 }
                             }
                         }
                     }
                 }
             }
         }
     }
 }

@Composable
fun StudentProfileView(viewModel: AcademyViewModel) {
    val user = viewModel.currentUser ?: return
    var showEditDialog by remember { mutableStateOf(false) }

    if (showEditDialog) {
        EditProfileDialog(viewModel = viewModel, onDismiss = { showEditDialog = false })
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
             IconButton(onClick = { showEditDialog = true }) {
                 Icon(Icons.Default.Edit, contentDescription = "Edit Profile")
             }
        }
        Box(modifier = Modifier.size(100.dp).clip(CircleShape).background(Color(0xFFEEF2FF)), contentAlignment = Alignment.Center) {
            if (user.photoUri.isNotEmpty()) {
                 AsyncImage(model = user.photoUri, contentDescription = "Profile Photo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                 Text(user.avatarEmoji, fontSize = 48.sp)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(user.name, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text(user.email, fontSize = 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(32.dp))
        
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Contact Support & Info", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF6366F1))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Phone, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Mobile Number", fontSize = 12.sp, color = Color.Gray)
                        Text(user.mobile.ifBlank { "+91 8090756962" }, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Email, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Email Address", fontSize = 12.sp, color = Color.Gray)
                        Text("academylakshya112@gmail.com", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SupervisorAccount, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Owner Name", fontSize = 12.sp, color = Color.Gray)
                        Text("Kamlesh Sir", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        val uriHandler = LocalUriHandler.current
        
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Official Channels & Socials", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF6366F1))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)
                
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        uriHandler.openUri("https://chat.whatsapp.com/JDHYEnF8rQP3D0kIeH3Qoj?s=cl&p=a&ilr=2")
                    }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null, tint = Color(0xFF25D366), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Join WhatsApp Group", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        uriHandler.openUri("https://t.me/+k9fhlPovsDE5ZDI1")
                    }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = Color(0xFF229ED9), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Join Telegram Channel", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }

                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        uriHandler.openUri("https://www.instagram.com/toon_waale_dost12?igsh=MWg1NWprNzltZjJ4dg==")
                    }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color(0xFFE1306C), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Follow on Instagram", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        uriHandler.openUri("https://www.facebook.com/share/1Ld9zB8Khi/")
                    }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ThumbUp, contentDescription = null, tint = Color(0xFF1877F2), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Follow on Facebook", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Text("APP CREAT By RAHUL FUTURE ARMY MAN", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = { viewModel.logout() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Logout Securely")
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
