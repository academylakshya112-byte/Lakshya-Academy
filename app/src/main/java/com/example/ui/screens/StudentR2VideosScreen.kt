package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import com.example.BuildConfig
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.api.R2SupabaseManager
import com.example.api.SupabaseVideo
import com.example.data.LessonEntity
import com.example.ui.theme.BrandBluePrimary
import com.example.ui.theme.BrandBlueSecondary
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.BorderStroke
import com.example.data.CourseEntity
import com.example.ui.viewmodel.AcademyViewModel

sealed class LmsNavDestination {
    object BatchList : LmsNavDestination()
    data class SubjectList(val batch: String) : LmsNavDestination()
    data class ChapterList(val batch: String, val subject: String) : LmsNavDestination()
    data class VideoList(val batch: String, val subject: String, val chapter: String) : LmsNavDestination()
    data class VideoPlayer(val video: SupabaseVideo) : LmsNavDestination()
    data class ResourceHub(val batch: String, val subject: String, val chapter: String) : LmsNavDestination()
    data class ResourceCategoryDetail(val batch: String, val subject: String, val chapter: String, val category: String) : LmsNavDestination()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentR2VideosScreen(
    viewModel: AcademyViewModel,
    initialBatch: String? = null,
    onBack: () -> Unit,
    onNavigateToDoubtSolver: ((String, String) -> Unit)? = null
) {
    com.example.util.TrackStudyModule(com.example.util.StudyTracker.MODULE_COURSE_SYLLABUS)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val enrollments by viewModel.allEnrollments.collectAsStateWithLifecycle()
    val courses by viewModel.allCourses.collectAsStateWithLifecycle()
    val userEmail = viewModel.currentUser?.email ?: ""
    var selectedCourseForDetail by remember { mutableStateOf<CourseEntity?>(null) }

    var allVideos by remember { mutableStateOf<List<SupabaseVideo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var navStack by remember(initialBatch) {
        mutableStateOf<List<LmsNavDestination>>(
            if (initialBatch != null) listOf(LmsNavDestination.SubjectList(initialBatch))
            else listOf(LmsNavDestination.BatchList)
        )
    }
    val currentDestination = navStack.lastOrNull() ?: LmsNavDestination.BatchList

    var selectedVideoToPlay by remember { mutableStateOf<SupabaseVideo?>(null) }
    var selectedPdfToView by remember { mutableStateOf<SupabaseVideo?>(null) }
    var showPdfChooserForVideo by remember { mutableStateOf<SupabaseVideo?>(null) }
    var isPlayerFullScreen by remember { mutableStateOf(false) }

    // Sequential playback state
    val sharedPrefs = remember { context.getSharedPreferences("lms_sequential_watch", Context.MODE_PRIVATE) }
    var unlockedVideosCount by remember { mutableStateOf(0) } // force recompositions on watch states

    // AI Doubt Solver States inside single chapter page
    var aiInputText by remember { mutableStateOf("") }
    val aiMessages = remember { mutableStateListOf<ChatMessage>() }
    var isAiLoading by remember { mutableStateOf(false) }

    LaunchedEffect(currentDestination) {
        if (currentDestination is LmsNavDestination.ResourceHub) {
            aiMessages.clear()
            aiMessages.add(
                ChatMessage(
                    text = "Hello! I am your AI Study Coach for **${currentDestination.chapter}**. Ask me any doubt about this topic, and I will explain it instantly!",
                    isUser = false
                )
            )
        } else if (currentDestination is LmsNavDestination.VideoPlayer) {
            aiMessages.clear()
            aiMessages.add(
                ChatMessage(
                    text = "Hello! I am your AI Study Coach for **${currentDestination.video.title}**. Ask me any doubt about this topic, and I will explain it instantly!",
                    isUser = false
                )
            )
        }
    }

    fun loadVideos() {
        isLoading = true
        errorMessage = null
        android.util.Log.d("BatchDebug", "Fetching batches from Supabase")
        R2SupabaseManager.fetchVideos(context) { list, error ->
            isLoading = false
            if (error != null) {
                errorMessage = error
            } else {
                allVideos = list ?: emptyList()
                val batchesCount = allVideos.map { it.classText.trim() }.filter { it.isNotBlank() }.distinct().size
                android.util.Log.d("BatchDebug", "Supabase returned $batchesCount batches")
                android.util.Log.d("BatchDebug", "Displaying $batchesCount batches")
                android.util.Log.d("BatchDebug", "No local demo data loaded")
            }
        }
    }

    LaunchedEffect(Unit) {
        loadVideos()
    }

    // Dynamic Lists representing uploaded items only
    val batchesList = remember(allVideos) {
        allVideos.map { it.classText.trim() }.filter { it.isNotBlank() }.distinct()
    }

    fun getSubjectsForBatch(batch: String): List<String> {
        val uploadedSubjects = allVideos
            .filter { it.classText.trim().equals(batch.trim(), ignoreCase = true) }
            .map { it.subject.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        return uploadedSubjects
    }

    fun getChaptersForSubject(batch: String, subject: String): List<String> {
        val uploadedChapters = allVideos
            .filter {
                it.classText.trim().equals(batch.trim(), ignoreCase = true) &&
                it.subject.trim().equals(subject.trim(), ignoreCase = true)
            }
            .map { it.chapter.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        return uploadedChapters
    }

    // Get specific category resources uploaded in Supabase
    fun getResourcesForCategory(batch: String, subject: String, chapter: String, category: String): List<SupabaseVideo> {
        return allVideos.filter {
            it.classText.trim().equals(batch.trim(), ignoreCase = true) &&
            it.subject.trim().equals(subject.trim(), ignoreCase = true) &&
            it.chapter.trim().equals(chapter.trim(), ignoreCase = true) &&
            it.resourceType.trim().equals(category.trim(), ignoreCase = true) &&
            it.visibility
        }.sortedBy { it.orderNumber }
    }

    var chapterResources by remember { mutableStateOf<List<SupabaseVideo>>(emptyList()) }
    var isChapterLoading by remember { mutableStateOf(false) }

    fun getLocalResourcesForCategory(category: String): List<SupabaseVideo> {
        return chapterResources.filter {
            it.resourceType.trim().equals(category.trim(), ignoreCase = true) &&
            it.visibility
        }.sortedBy { it.orderNumber }
    }

    LaunchedEffect(currentDestination) {
        if (currentDestination is LmsNavDestination.ResourceHub || 
            currentDestination is LmsNavDestination.ResourceCategoryDetail ||
            currentDestination is LmsNavDestination.VideoList
        ) {
            val destBatch = when(currentDestination) {
                is LmsNavDestination.ResourceHub -> currentDestination.batch
                is LmsNavDestination.ResourceCategoryDetail -> currentDestination.batch
                is LmsNavDestination.VideoList -> currentDestination.batch
                else -> ""
            }
            val destSubject = when(currentDestination) {
                is LmsNavDestination.ResourceHub -> currentDestination.subject
                is LmsNavDestination.ResourceCategoryDetail -> currentDestination.subject
                is LmsNavDestination.VideoList -> currentDestination.subject
                else -> ""
            }
            val destChapter = when(currentDestination) {
                is LmsNavDestination.ResourceHub -> currentDestination.chapter
                is LmsNavDestination.ResourceCategoryDetail -> currentDestination.chapter
                is LmsNavDestination.VideoList -> currentDestination.chapter
                else -> ""
            }
            
            isChapterLoading = true
            R2SupabaseManager.fetchVideosForChapter(context, destBatch, destSubject, destChapter) { list, error ->
                isChapterLoading = false
                if (error == null && list != null) {
                    chapterResources = list
                    
                    if (currentDestination is LmsNavDestination.ResourceHub) {
                        val videos = list.filter {
                            it.resourceType.trim().equals("VIDEO", ignoreCase = true) && it.visibility
                        }.sortedBy { it.orderNumber }
                        if (videos.isNotEmpty() && (selectedVideoToPlay == null || selectedVideoToPlay?.chapter != destChapter)) {
                            selectedVideoToPlay = videos.first()
                        }
                    }
                }
            }
        }
    }

    BackHandler {
        if (isPlayerFullScreen) {
            isPlayerFullScreen = false
        } else if (selectedVideoToPlay != null) {
            selectedVideoToPlay = null
        } else if (selectedPdfToView != null) {
            selectedPdfToView = null
        } else if (navStack.size > 1) {
            navStack = navStack.dropLast(1)
        } else {
            onBack()
        }
    }

    // Determine AppBar Title dynamically
    val appTitle = when (currentDestination) {
        is LmsNavDestination.BatchList -> "LMS - Target Batches"
        is LmsNavDestination.SubjectList -> currentDestination.batch
        is LmsNavDestination.ChapterList -> currentDestination.subject
        is LmsNavDestination.VideoList -> currentDestination.chapter
        is LmsNavDestination.VideoPlayer -> currentDestination.video.title
        is LmsNavDestination.ResourceHub -> "Chapter Portal"
        is LmsNavDestination.ResourceCategoryDetail -> {
            val catName = when (currentDestination.category) {
                "VIDEO" -> "Video Lectures"
                "WRITTEN_NOTES" -> "Written Notes PDF"
                "BOARD_PDF" -> "Board PDF"
                "NCERT_PDF" -> "NCERT PDF"
                "QUESTION_BANK" -> "Question Bank"
                "PY_PAPER" -> "Previous Year Papers"
                "SAMPLE_PAPER" -> "Sample Papers"
                "IMPORTANT_QUESTIONS" -> "Important Questions"
                "QUIZ" -> "Quizzes"
                "MOCK_TEST" -> "Mock Tests"
                else -> currentDestination.category
            }
            catName
        }
    }

    Scaffold(
        topBar = {
            if (!isPlayerFullScreen) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = appTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            if (currentDestination !is LmsNavDestination.BatchList) {
                                Text(
                                    text = "Lakshya Academy Dynamic LMS",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (selectedVideoToPlay != null) {
                                selectedVideoToPlay = null
                            } else if (selectedPdfToView != null) {
                                selectedPdfToView = null
                            } else if (navStack.size > 1) {
                                navStack = navStack.dropLast(1)
                            } else {
                                onBack()
                            }
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { loadVideos() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isPlayerFullScreen || selectedPdfToView != null) PaddingValues(0.dp) else innerPadding)
                .background(if (isPlayerFullScreen) Color.Black else Color(0xFFF8FAFC))
        ) {
            val activeVideo = remember(selectedVideoToPlay, currentDestination) {
                if (currentDestination is LmsNavDestination.VideoPlayer) {
                    (currentDestination as LmsNavDestination.VideoPlayer).video
                } else {
                    selectedVideoToPlay
                }
            }
            if (isPlayerFullScreen && activeVideo != null) {
                // Fullscreen Player overlay
                val lesson = remember(activeVideo) {
                    LessonEntity(
                        courseId = -999,
                        chapterName = activeVideo.chapter,
                        title = activeVideo.title,
                        videoUrl = activeVideo.videoUrl,
                        pdfUrl = "",
                        pdfName = "",
                        videoSourceType = "MP4"
                    )
                }
                Dialog(
                    onDismissRequest = { isPlayerFullScreen = false },
                    properties = DialogProperties(
                        usePlatformDefaultWidth = false,
                        decorFitsSystemWindows = false
                    )
                ) {
                    val dialogWindow = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
                    LaunchedEffect(Unit) {
                        dialogWindow?.let { window ->
                            window.setLayout(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
                            window.setDimAmount(0f)
                            window.decorView.setPadding(0, 0, 0, 0)
                            val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
                            controller.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars() or androidx.core.view.WindowInsetsCompat.Type.navigationBars() or androidx.core.view.WindowInsetsCompat.Type.systemBars())
                            controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                val attrs = window.attributes
                                attrs.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                                window.attributes = attrs
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        VideoPlayerView(
                            lesson = lesson,
                            isFullScreen = true,
                            onFullScreenToggle = { isFS -> isPlayerFullScreen = isFS }
                        )
                    }
                }
            } else if (selectedPdfToView != null) {
                // In App Secure PDF Viewer
                InAppPdfViewer(
                    pdfUrl = selectedPdfToView!!.videoUrl,
                    title = selectedPdfToView!!.title,
                    onBack = { selectedPdfToView = null }
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    
                    // Display Active Playing Video if nested
                    selectedVideoToPlay?.let { activeVideo ->
                        val lesson = remember(activeVideo) {
                            LessonEntity(
                                courseId = -999,
                                chapterName = activeVideo.chapter,
                                title = activeVideo.title,
                                videoUrl = activeVideo.videoUrl,
                                pdfUrl = "",
                                pdfName = "",
                                videoSourceType = "MP4"
                            )
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Black)
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16 / 9f)
                                ) {
                                    VideoPlayerView(
                                        lesson = lesson,
                                        isFullScreen = false,
                                        onFullScreenToggle = { isFS -> isPlayerFullScreen = isFS }
                                    )
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White)
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = activeVideo.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color(0xFF0F172A),
                                            maxLines = 2,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = { selectedVideoToPlay = null }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Close Player",
                                                tint = Color.Gray
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${activeVideo.classText} • ${activeVideo.subject} • ${activeVideo.chapter}",
                                            fontSize = 11.sp,
                                            color = BrandBlueSecondary,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        VideoViewCountDisplay(videoUrl = activeVideo.videoUrl)
                                    }
                                }
                            }
                        }
                    }

                    // Render screen content based on navigation destination
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = BrandBluePrimary)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("Syncing LMS Resources...", fontSize = 13.sp, color = Color.Gray)
                            }
                        }
                    } else {
                        when (val dest = currentDestination) {
                            is LmsNavDestination.BatchList -> {
                                val myEnrollments = enrollments.filter { it.userEmail == userEmail }
                                val enrolledCourses = courses.filter { course ->
                                    myEnrollments.any { it.courseId == course.id }
                                }
                                val availableCourses = courses.filter { course ->
                                    myEnrollments.none { it.courseId == course.id }
                                }

                                if (courses.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .padding(40.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.School,
                                                contentDescription = null,
                                                modifier = Modifier.size(80.dp),
                                                tint = Color.LightGray
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = "No batches available.",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .padding(horizontal = 16.dp),
                                        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
                                    ) {
                                        if (enrolledCourses.isNotEmpty()) {
                                            item {
                                                SectionHeader(title = "Active Enrollment Batches (सक्रिय बैच)")
                                            }
                                            items(items = enrolledCourses) { course ->
                                                R2BatchCard(
                                                    course = course,
                                                    isEnrolled = true,
                                                    onClick = {
                                                        navStack = navStack + LmsNavDestination.SubjectList(course.title)
                                                    },
                                                    onEnrollClick = {}
                                                )
                                            }
                                            item {
                                                Spacer(modifier = Modifier.height(16.dp))
                                            }
                                        }

                                        if (availableCourses.isNotEmpty()) {
                                            item {
                                                SectionHeader(title = "Available Batches (उपलब्ध बैच)")
                                            }
                                            items(items = availableCourses) { course ->
                                                R2BatchCard(
                                                    course = course,
                                                    isEnrolled = false,
                                                    onClick = {
                                                        selectedCourseForDetail = course
                                                    },
                                                    onEnrollClick = {
                                                        selectedCourseForDetail = course
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            is LmsNavDestination.SubjectList -> {
                                val subjects = getSubjectsForBatch(dest.batch)
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(subjects) { subject ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    navStack = navStack + LmsNavDestination.ChapterList(dest.batch, subject)
                                                },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            border = borderHex(Color.LightGray.copy(alpha = 0.3f))
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .clip(CircleShape)
                                                        .background(BrandBlueSecondary.copy(alpha = 0.1f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Book,
                                                        contentDescription = null,
                                                        tint = BrandBlueSecondary,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Text(
                                                    text = subject,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = Color(0xFF1E293B),
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                val count = allVideos.count {
                                                    it.classText.trim().equals(dest.batch.trim(), ignoreCase = true) &&
                                                    it.subject.trim().equals(subject.trim(), ignoreCase = true)
                                                }
                                                Text(
                                                    text = "$count Chapters & PDF",
                                                    fontSize = 11.sp,
                                                    color = Color.Gray
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            is LmsNavDestination.ChapterList -> {
                                val chapters = getChaptersForSubject(dest.batch, dest.subject)
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(chapters) { chap ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    navStack = navStack + LmsNavDestination.VideoList(dest.batch, dest.subject, chap)
                                                },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            border = borderHex(Color.LightGray.copy(alpha = 0.3f))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFFF1F5F9)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Folder,
                                                        contentDescription = null,
                                                        tint = Color.DarkGray,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = chap,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        color = Color(0xFF1E293B)
                                                    )
                                                    Text(
                                                        text = "${dest.batch} • ${dest.subject}",
                                                        fontSize = 11.sp,
                                                        color = Color.Gray
                                                    )
                                                }
                                                Icon(
                                                    imageVector = Icons.Default.ChevronRight,
                                                    contentDescription = null,
                                                    tint = Color.LightGray
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            is LmsNavDestination.VideoList -> {
                                val videos = chapterResources.filter {
                                    it.resourceType.trim().equals("VIDEO", ignoreCase = true) &&
                                    it.visibility
                                }.sortedBy { it.orderNumber }

                                if (isChapterLoading && videos.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = BrandBluePrimary)
                                    }
                                } else if (videos.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(32.dp), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Default.PlayCircle, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text("No video lectures published yet for this chapter.", color = Color.Gray, fontSize = 14.sp)
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxWidth().weight(1f),
                                        contentPadding = PaddingValues(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(videos) { video ->
                                            val isLocked = remember(video, videos, unlockedVideosCount) {
                                                val indexInChapter = videos.indexOfFirst { it.id == video.id }
                                                if (indexInChapter <= 0) false else {
                                                    val prevItem = videos[indexInChapter - 1]
                                                    !sharedPrefs.getBoolean("video_completed_${prevItem.id}", false)
                                                }
                                            }

                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        if (isLocked) {
                                                            val indexInChapter = videos.indexOfFirst { it.id == video.id }
                                                            val prevTitle = if (indexInChapter > 0) videos[indexInChapter - 1].title else "previous"
                                                            Toast.makeText(
                                                                context,
                                                                "Lock 🔒: Please watch '$prevTitle' first!",
                                                                Toast.LENGTH_LONG
                                                            ).show()
                                                        } else {
                                                            navStack = navStack + LmsNavDestination.VideoPlayer(video)
                                                            // Auto unlock next video
                                                            sharedPrefs.edit().putBoolean("video_completed_${video.id}", true).apply()
                                                            unlockedVideosCount++
                                                        }
                                                    },
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                                border = borderHex(if (isLocked) Color.Red.copy(alpha = 0.2f) else Color.LightGray.copy(alpha = 0.4f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Video Thumbnail
                                                    Box(
                                                        modifier = Modifier
                                                            .size(width = 110.dp, height = 75.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(Color(0xFFF1F5F9))
                                                    ) {
                                                        val effectiveThumb = remember(video.thumbnailUrl, video.videoUrl) {
                                                            if (video.thumbnailUrl.isNotBlank()) video.thumbnailUrl
                                                            else if (isYouTubeUrl(video.videoUrl)) getYouTubeThumbnailUrl(video.videoUrl)
                                                            else ""
                                                        }
                                                        if (effectiveThumb.isNotBlank()) {
                                                            android.util.Log.i("StudentR2VideosScreen", "[THUMBNAIL RENDERING] Video ID: ${video.id}, Title: '${video.title}' is loading thumbnail from URL: '$effectiveThumb'")
                                                            AsyncImage(
                                                                model = effectiveThumb,
                                                                contentDescription = null,
                                                                modifier = Modifier.fillMaxSize(),
                                                                contentScale = ContentScale.Crop
                                                            )
                                                        } else {
                                                            Icon(
                                                                imageVector = Icons.Default.PlayCircle,
                                                                contentDescription = null,
                                                                tint = Color.LightGray,
                                                                modifier = Modifier.align(Alignment.Center).size(32.dp)
                                                            )
                                                        }

                                                        // Overlay Order/Lecture Number
                                                        Box(
                                                            modifier = Modifier
                                                                .align(Alignment.BottomStart)
                                                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(topEnd = 4.dp))
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = "Lec ${video.orderNumber}",
                                                                color = Color.White,
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.width(12.dp))

                                                    // Title, Duration, Buttons
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = video.title,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp,
                                                            color = if (isLocked) Color.Gray else Color(0xFF1E293B),
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Schedule,
                                                                contentDescription = null,
                                                                tint = Color.Gray,
                                                                modifier = Modifier.size(12.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = if (video.duration.isNotBlank()) video.duration else "N/A",
                                                                fontSize = 11.sp,
                                                                color = Color.Gray
                                                            )
                                                        }
                                                    }

                                                    // Buttons: Download & Watch
                                                    Column(
                                                        horizontalAlignment = Alignment.End,
                                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        if (isLocked) {
                                                            Icon(
                                                                imageVector = Icons.Default.Lock,
                                                                contentDescription = "Locked",
                                                                tint = Color.Red,
                                                                modifier = Modifier.size(20.dp).padding(end = 4.dp)
                                                            )
                                                        } else {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                            ) {
                                                                // Download Button (optional)
                                                                IconButton(
                                                                    onClick = {
                                                                        Toast.makeText(context, "Preparing high quality offline cache...", Toast.LENGTH_SHORT).show()
                                                                    },
                                                                    modifier = Modifier.size(32.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Download,
                                                                        contentDescription = "Download Video",
                                                                        tint = BrandBlueSecondary,
                                                                        modifier = Modifier.size(18.dp)
                                                                    )
                                                                }

                                                                // Watch Button
                                                                Button(
                                                                    onClick = {
                                                                        navStack = navStack + LmsNavDestination.VideoPlayer(video)
                                                                        sharedPrefs.edit().putBoolean("video_completed_${video.id}", true).apply()
                                                                        unlockedVideosCount++
                                                                    },
                                                                    shape = RoundedCornerShape(8.dp),
                                                                    colors = ButtonDefaults.buttonColors(containerColor = BrandBluePrimary),
                                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                                    modifier = Modifier.height(32.dp)
                                                                ) {
                                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                                        Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(12.dp))
                                                                        Spacer(modifier = Modifier.width(2.dp))
                                                                        Text("Watch", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                            }

                            is LmsNavDestination.VideoPlayer -> {
                                val video = dest.video
                                val lesson = remember(video) {
                                    LessonEntity(
                                        courseId = -999,
                                        chapterName = video.chapter,
                                        title = video.title,
                                        videoUrl = video.videoUrl,
                                        pdfUrl = video.pdfUrl ?: "",
                                        pdfName = "",
                                        videoSourceType = "MP4"
                                    )
                                }

                                var showAiDoubtChat by remember { mutableStateOf(false) }

                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                    contentPadding = PaddingValues(bottom = 32.dp)
                                ) {
                                    // 1. Video Player View
                                    item {
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.Black)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(16 / 9f)
                                            ) {
                                                VideoPlayerView(
                                                    lesson = lesson,
                                                    isFullScreen = false,
                                                    onFullScreenToggle = { isFS -> isPlayerFullScreen = isFS }
                                                )
                                            }
                                        }
                                    }

                                    // 2. Title, Meta, and Description
                                    item {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = video.title,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 18.sp,
                                                color = Color(0xFF0F172A)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${video.classText} • ${video.subject} • ${video.chapter} • Lec ${video.orderNumber}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = BrandBlueSecondary,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                VideoViewCountDisplay(videoUrl = video.videoUrl)
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = video.description.ifBlank { "No description provided for this video lecture." },
                                                fontSize = 13.sp,
                                                color = Color.DarkGray,
                                                lineHeight = 18.sp
                                            )
                                            
                                            Spacer(modifier = Modifier.height(16.dp))
                                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f), thickness = 1.dp)
                                        }
                                    }

                                    // 3. Study Materials: PDF 1 & PDF 2
                                    item {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = "Study Materials & Notes",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = Color(0xFF1E293B)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))

                                            val pdfs = video.pdfUrl?.split("|") ?: emptyList()
                                            val pdf1 = pdfs.getOrNull(0) ?: ""
                                            val pdf2 = pdfs.getOrNull(1) ?: ""

                                            if (pdf1.isBlank() && pdf2.isBlank()) {
                                                Text(
                                                    text = "No study notes attached to this lecture.",
                                                    fontSize = 12.sp,
                                                    color = Color.Gray,
                                                    modifier = Modifier.padding(vertical = 4.dp)
                                                )
                                            } else {
                                                if (pdf1.isNotBlank()) {
                                                    Card(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 6.dp)
                                                            .clickable {
                                                                selectedPdfToView = video.copy(videoUrl = pdf1, title = video.title + " (Handwritten Notes)")
                                                            },
                                                        shape = RoundedCornerShape(8.dp),
                                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                                        border = borderHex(Color.LightGray.copy(alpha = 0.4f))
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(36.dp)
                                                                    .clip(CircleShape)
                                                                    .background(Color(0xFFFEF2F2)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.PictureAsPdf,
                                                                    contentDescription = null,
                                                                    tint = Color.Red,
                                                                    modifier = Modifier.size(20.dp)
                                                                )
                                                            }
                                                            Spacer(modifier = Modifier.width(12.dp))
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text("Handwritten Notes PDF", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF1E293B))
                                                                Text("Click to view secure handwritten study material", fontSize = 11.sp, color = Color.Gray)
                                                            }
                                                            Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray)
                                                        }
                                                    }
                                                }

                                                if (pdf2.isNotBlank()) {
                                                    Card(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 6.dp)
                                                            .clickable {
                                                                selectedPdfToView = video.copy(videoUrl = pdf2, title = video.title + " (Board/Class Notes)")
                                                            },
                                                        shape = RoundedCornerShape(8.dp),
                                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                                        border = borderHex(Color.LightGray.copy(alpha = 0.4f))
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(36.dp)
                                                                    .clip(CircleShape)
                                                                    .background(Color(0xFFEFF6FF)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.BorderColor,
                                                                    contentDescription = null,
                                                                    tint = BrandBlueSecondary,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                            Spacer(modifier = Modifier.width(12.dp))
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text("Board Notes PDF", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF1E293B))
                                                                Text("Click to view class board/presentation slides", fontSize = 11.sp, color = Color.Gray)
                                                            }
                                                            Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // 4. AI Doubt solver trigger button
                                    item {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                        ) {
                                            Button(
                                                onClick = { showAiDoubtChat = !showAiDoubtChat },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (showAiDoubtChat) Color(0xFF15532D) else Color(0xFF16A34A)
                                                )
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center
                                                ) {
                                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = if (showAiDoubtChat) "Hide AI Doubt Solver" else "Ask AI Doubt (Lakshya AI Coach)",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // 5. Chat UI container (if toggled)
                                    if (showAiDoubtChat) {
                                        item {
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                                                border = borderHex(Color(0xFFBBF7D0))
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = Icons.Default.AutoAwesome,
                                                            contentDescription = null,
                                                            tint = Color(0xFF16A34A),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "Live AI Assistant: ${video.title}",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp,
                                                            color = Color(0xFF14532D)
                                                        )
                                                    }
                                                    
                                                    Spacer(modifier = Modifier.height(8.dp))

                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .background(Color.White, RoundedCornerShape(8.dp))
                                                            .padding(8.dp)
                                                    ) {
                                                        aiMessages.forEach { msg ->
                                                            val bubbleBg = if (msg.isUser) Color(0xFFDCFCE7) else Color(0xFFF1F5F9)
                                                            val bubbleAlign = if (msg.isUser) Alignment.End else Alignment.Start
                                                            val bubbleTextColor = if (msg.isUser) Color(0xFF14532D) else Color(0xFF1E293B)

                                                            Column(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(vertical = 4.dp),
                                                                horizontalAlignment = bubbleAlign
                                                            ) {
                                                                Text(
                                                                    text = if (msg.isUser) "You" else "Lakshya AI Coach",
                                                                    fontSize = 9.sp,
                                                                    color = Color.Gray,
                                                                    fontWeight = FontWeight.Bold,
                                                                    modifier = Modifier.padding(horizontal = 4.dp)
                                                                )
                                                                Box(
                                                                    modifier = Modifier
                                                                        .background(bubbleBg, RoundedCornerShape(8.dp))
                                                                        .padding(8.dp)
                                                                        .widthIn(max = 260.dp)
                                                                ) {
                                                                    Text(
                                                                        text = msg.text,
                                                                        fontSize = 11.sp,
                                                                        color = bubbleTextColor
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        if (isAiLoading) {
                                                            Row(
                                                                modifier = Modifier.padding(8.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                CircularProgressIndicator(
                                                                    modifier = Modifier.size(12.dp),
                                                                    strokeWidth = 1.5.dp,
                                                                    color = Color(0xFF16A34A)
                                                                )
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Text("AI is answering...", fontSize = 10.sp, color = Color.Gray)
                                                            }
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(8.dp))

                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        OutlinedTextField(
                                                            value = aiInputText,
                                                            onValueChange = { aiInputText = it },
                                                            placeholder = { Text("Ask a doubt about this lecture...", fontSize = 11.sp) },
                                                            modifier = Modifier.weight(1f),
                                                            maxLines = 2,
                                                            singleLine = false,
                                                            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
                                                            colors = OutlinedTextFieldDefaults.colors(
                                                                focusedBorderColor = Color(0xFF16A34A),
                                                                unfocusedBorderColor = Color.LightGray,
                                                                focusedContainerColor = Color.White,
                                                                unfocusedContainerColor = Color.White
                                                            )
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        IconButton(
                                                            onClick = {
                                                                if (aiInputText.isNotBlank()) {
                                                                    val q = aiInputText
                                                                    aiInputText = ""

                                                                    val req = com.example.data.GenerateContentRequest(
                                                                        contents = listOf(
                                                                            com.example.data.Content(
                                                                                parts = listOf(com.example.data.Part(text = q)),
                                                                                role = "user"
                                                                            )
                                                                        ),
                                                                        systemInstruction = com.example.data.Content(
                                                                            parts = listOf(
                                                                                com.example.data.Part(
                                                                                    text = "CRITICAL REQUIREMENT: The student is asking doubts specifically for the video lecture: '${video.title}' (Chapter: '${video.chapter}', Subject: '${video.subject}'). You MUST answer the student's question ONLY using the context of, and concepts taught within, the chapter: '${video.chapter}' when possible. Keep answers strictly focused and limited to this scope. You are Lakshya AI 5.0 Ultra, the smartest AI Teacher. AI Personality: Be patient. Explain politely. Never skip steps. Always motivate students. Support Hindi, English, and Hinglish natively."
                                                                                )
                                                                            )
                                                                        )
                                                                    )

                                                                    isAiLoading = true
                                                                    val userMsg = ChatMessage(text = q, isUser = true)
                                                                    aiMessages.add(userMsg)

                                                                    scope.launch {
                                                                        try {
                                                                            val rawKey = BuildConfig.GEMINI_API_KEY
                                                                            val key = rawKey.trim().removeSurrounding("\"").removeSurrounding("'").trim()
                                                                            val response = com.example.api.RetrofitClient.service.generateContent(
                                                                                fullPath = "v1beta/models/gemini-2.5-flash:generateContent",
                                                                                apiKey = key,
                                                                                request = req
                                                                            )
                                                                            val aiResponse = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "No response from AI."
                                                                            aiMessages.add(ChatMessage(text = aiResponse, isUser = false))
                                                                        } catch (e: Exception) {
                                                                            aiMessages.add(ChatMessage(text = "Error: Failed to fetch AI answer. ${e.localizedMessage}", isUser = false))
                                                                        } finally {
                                                                            isAiLoading = false
                                                                        }
                                                                    }
                                                                }
                                                            },
                                                            modifier = Modifier
                                                                .size(36.dp)
                                                                .background(Color(0xFF16A34A), CircleShape),
                                                            enabled = !isAiLoading && aiInputText.isNotBlank()
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Send,
                                                                contentDescription = "Send",
                                                                tint = Color.White,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            is LmsNavDestination.ResourceHub -> {
                                val chapterVideos = getLocalResourcesForCategory("VIDEO")

                                if (isChapterLoading && chapterVideos.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator()
                                    }
                                } else {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        // 1. AndroidX Media3 ExoPlayer at the top
                                        selectedVideoToPlay?.let { activeVideo ->
                                            val lesson = remember(activeVideo) {
                                                LessonEntity(
                                                    courseId = -999,
                                                    chapterName = activeVideo.chapter,
                                                    title = activeVideo.title,
                                                    videoUrl = activeVideo.videoUrl,
                                                    pdfUrl = "",
                                                    pdfName = "",
                                                    videoSourceType = "MP4"
                                                )
                                            }
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.Black)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(16 / 9f)
                                            ) {
                                                VideoPlayerView(
                                                    lesson = lesson,
                                                    isFullScreen = false,
                                                    onFullScreenToggle = { isFS -> isPlayerFullScreen = isFS }
                                                )
                                            }
                                        }
                                    } ?: run {
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(16 / 9f),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayCircle,
                                                        contentDescription = null,
                                                        tint = Color.White.copy(alpha = 0.5f),
                                                        modifier = Modifier.size(48.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(
                                                        text = "No videos available for this chapter.",
                                                        color = Color.White.copy(alpha = 0.7f),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Scrollable content of the Chapter page
                                    val listState = rememberLazyListState()
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        contentPadding = PaddingValues(bottom = 24.dp)
                                    ) {
                                        // 2. Info Block: Chapter Name, Subject Name, Short Description
                                        item {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                            ) {
                                                Text(
                                                    text = dest.chapter,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp,
                                                    color = Color(0xFF0F172A)
                                                )
                                                Text(
                                                    text = dest.subject,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 13.sp,
                                                    color = BrandBluePrimary
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                val shortDesc = selectedVideoToPlay?.description?.ifBlank { null }
                                                    ?: "Master this topic with standard dynamic video lessons, practice PDF resources, self-assessment tests, and personalized 24/7 AI tutor guidance."
                                                Text(
                                                    text = shortDesc,
                                                    fontSize = 12.sp,
                                                    color = Color.Gray,
                                                    lineHeight = 16.sp
                                                )
                                                Spacer(modifier = Modifier.height(16.dp))

                                                // QUICK SHORTCUTS ROW
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    // AI Doubt Shortcut
                                                    Card(
                                                        modifier = Modifier.weight(1f).clickable {
                                                            scope.launch {
                                                                listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
                                                            }
                                                        },
                                                        shape = RoundedCornerShape(8.dp),
                                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                                                        border = borderHex(Color(0xFFBBF7D0))
                                                    ) {
                                                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                            Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFF16A34A), modifier = Modifier.size(18.dp))
                                                            Text("Ask AI Doubt", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                                                        }
                                                    }

                                                    // Quiz Shortcut
                                                    Card(
                                                        modifier = Modifier.weight(1f).clickable {
                                                            scope.launch {
                                                                listState.animateScrollToItem(12)
                                                            }
                                                        },
                                                        shape = RoundedCornerShape(8.dp),
                                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
                                                        border = borderHex(Color(0xFFFFEDD5))
                                                    ) {
                                                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                            Icon(Icons.Default.Quiz, null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                                                            Text("Mock Tests", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9A3412))
                                                        }
                                                    }

                                                    // PDF Shortcut
                                                    Card(
                                                        modifier = Modifier.weight(1f).clickable {
                                                            scope.launch {
                                                                listState.animateScrollToItem(5)
                                                            }
                                                        },
                                                        shape = RoundedCornerShape(8.dp),
                                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                                                        border = borderHex(Color(0xFFDBEAFE))
                                                    ) {
                                                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                            Icon(Icons.Default.PictureAsPdf, null, tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
                                                            Text("Study PDF", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E40AF))
                                                        }
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(16.dp))
                                                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f), thickness = 1.dp)
                                            }
                                        }

                                        // 3. ALL video lectures
                                        item {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayCircle,
                                                    contentDescription = null,
                                                    tint = BrandBluePrimary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Video Lectures",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = Color(0xFF1E293B)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "(${chapterVideos.size})",
                                                    fontSize = 11.sp,
                                                    color = Color.Gray
                                                )
                                            }
                                        }

                                        if (chapterVideos.isEmpty()) {
                                            item {
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                                    border = borderHex(Color.LightGray.copy(alpha = 0.2f))
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(16.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text("No videos published yet.", fontSize = 12.sp, color = Color.Gray)
                                                    }
                                                }
                                            }
                                        } else {
                                            items(chapterVideos) { video ->
                                                val isPlaying = selectedVideoToPlay?.id == video.id
                                                val isLocked = remember(video, chapterVideos, unlockedVideosCount) {
                                                    val indexInChapter = chapterVideos.indexOfFirst { it.id == video.id }
                                                    if (indexInChapter <= 0) false else {
                                                        val prevItem = chapterVideos[indexInChapter - 1]
                                                        !sharedPrefs.getBoolean("video_completed_${prevItem.id}", false)
                                                    }
                                                }

                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                                        .clickable {
                                                            if (isLocked) {
                                                                val indexInChapter = chapterVideos.indexOfFirst { it.id == video.id }
                                                                val prevTitle = if (indexInChapter > 0) chapterVideos[indexInChapter - 1].title else "previous"
                                                                Toast.makeText(
                                                                    context,
                                                                    "Lock 🔒: Please watch '$prevTitle' first!",
                                                                    Toast.LENGTH_LONG
                                                                ).show()
                                                            } else {
                                                                selectedVideoToPlay = video
                                                                sharedPrefs.edit().putBoolean("video_completed_${video.id}", true).apply()
                                                                unlockedVideosCount++
                                                            }
                                                        },
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = if (isPlaying) BrandBluePrimary.copy(alpha = 0.05f) else Color.White
                                                    ),
                                                    border = borderHex(
                                                        if (isLocked) Color.Red.copy(alpha = 0.2f)
                                                        else if (isPlaying) BrandBluePrimary
                                                        else Color.LightGray.copy(alpha = 0.3f)
                                                    )
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(12.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(36.dp)
                                                                .clip(CircleShape)
                                                                .background(
                                                                    if (isLocked) Color(0xFFFEE2E2)
                                                                    else if (isPlaying) BrandBluePrimary
                                                                    else Color(0xFFF1F5F9)
                                                                ),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.PlayCircle,
                                                                contentDescription = null,
                                                                tint = if (isLocked) Color.Red else if (isPlaying) Color.White else BrandBluePrimary,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = video.title,
                                                                fontWeight = FontWeight.SemiBold,
                                                                fontSize = 13.sp,
                                                                color = if (isLocked) Color.Gray else Color(0xFF1E293B)
                                                            )
                                                            if (video.description.isNotBlank()) {
                                                                Text(
                                                                    text = video.description,
                                                                    fontSize = 11.sp,
                                                                    color = Color.Gray,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                        }
                                                        if (isLocked) {
                                                            Text(
                                                                text = "Locked 🔒",
                                                                fontSize = 9.sp,
                                                                color = Color.Red,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier
                                                                    .background(Color(0xFFFEE2E2), RoundedCornerShape(4.dp))
                                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        } else if (video.duration.isNotBlank()) {
                                                            Text(
                                                                text = video.duration,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = BrandBlueSecondary
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // 4. PDFs in specified order
                                        item {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f), thickness = 1.dp)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Description,
                                                    contentDescription = null,
                                                    tint = BrandBluePrimary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Chapter Study PDFs",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = Color(0xFF1E293B)
                                                )
                                            }
                                        }

                                        val pdfCategories = listOf(
                                            Triple("WRITTEN_NOTES", "Written Notes (PDF)", Icons.Default.Description),
                                            Triple("BOARD_PDF", "Board PDF", Icons.Default.BorderColor),
                                            Triple("NCERT_PDF", "NCERT PDF", Icons.Default.MenuBook),
                                            Triple("QUESTION_BANK", "Question Bank", Icons.Default.Assignment),
                                            Triple("PY_PAPER", "Previous Year Papers", Icons.Default.History),
                                            Triple("SAMPLE_PAPER", "Sample Papers", Icons.Default.FileCopy),
                                            Triple("IMPORTANT_QUESTIONS", "Important Questions", Icons.Default.Star)
                                        )

                                        var hasAnyPdf = false
                                        pdfCategories.forEach { (catType, label, icon) ->
                                            val pdfList = getLocalResourcesForCategory(catType)
                                            if (pdfList.isNotEmpty()) {
                                                hasAnyPdf = true
                                                item {
                                                    Text(
                                                        text = "• $label",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.DarkGray,
                                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                                    )
                                                }
                                                items(pdfList) { pdf ->
                                                    Card(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                                            .clickable {
                                                                selectedPdfToView = pdf
                                                            },
                                                        shape = RoundedCornerShape(8.dp),
                                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                                        border = borderHex(Color.LightGray.copy(alpha = 0.3f))
                                                    ) {
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(12.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(32.dp)
                                                                    .clip(CircleShape)
                                                                    .background(Color(0xFFF1F5F9)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = icon,
                                                                    contentDescription = null,
                                                                    tint = BrandBluePrimary,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                            Spacer(modifier = Modifier.width(12.dp))
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(
                                                                    text = pdf.title,
                                                                    fontWeight = FontWeight.Medium,
                                                                    fontSize = 12.sp,
                                                                    color = Color(0xFF0F172A)
                                                                )
                                                                if (pdf.description.isNotBlank()) {
                                                                    Text(
                                                                        text = pdf.description,
                                                                        fontSize = 10.sp,
                                                                        color = Color.Gray
                                                                    )
                                                                }
                                                            }
                                                            Icon(
                                                                imageVector = Icons.Default.PictureAsPdf,
                                                                contentDescription = null,
                                                                tint = Color.Red,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        if (!hasAnyPdf) {
                                            item {
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                                    border = borderHex(Color.LightGray.copy(alpha = 0.2f))
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(16.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text("No study PDFs published yet for this chapter.", fontSize = 12.sp, color = Color.Gray)
                                                    }
                                                }
                                            }
                                        }

                                        // 5. Quiz & Mock Test
                                        item {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f), thickness = 1.dp)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Quiz,
                                                    contentDescription = null,
                                                    tint = BrandBluePrimary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Quizzes & Mock Tests",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = Color(0xFF1E293B)
                                                )
                                            }
                                        }

                                        val quizzes = getLocalResourcesForCategory("QUIZ")
                                        val mockTests = getLocalResourcesForCategory("MOCK_TEST")

                                        if (quizzes.isEmpty() && mockTests.isEmpty()) {
                                            item {
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                                    border = borderHex(Color.LightGray.copy(alpha = 0.2f))
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(16.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text("No quizzes or mock tests published yet for this chapter.", fontSize = 12.sp, color = Color.Gray)
                                                    }
                                                }
                                            }
                                        } else {
                                            if (quizzes.isNotEmpty()) {
                                                item {
                                                    Text(
                                                        text = "• Chapter Quizzes",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.DarkGray,
                                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                                    )
                                                }
                                                items(quizzes) { quiz ->
                                                    Card(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                                            .clickable {
                                                                if (quiz.videoUrl.contains(".pdf", ignoreCase = true)) {
                                                                    selectedPdfToView = quiz
                                                                } else {
                                                                    try {
                                                                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(quiz.videoUrl))
                                                                        context.startActivity(browserIntent)
                                                                    } catch (e: Exception) {
                                                                        Toast.makeText(context, "Cannot open quiz link", Toast.LENGTH_SHORT).show()
                                                                    }
                                                                }
                                                            },
                                                        shape = RoundedCornerShape(8.dp),
                                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                                                        border = borderHex(Color(0xFFFDE68A))
                                                    ) {
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(12.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(Icons.Default.Quiz, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                                                            Spacer(modifier = Modifier.width(12.dp))
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(quiz.title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF78350F))
                                                                if (quiz.description.isNotBlank()) {
                                                                    Text(quiz.description, fontSize = 10.sp, color = Color.Gray)
                                                                }
                                                            }
                                                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                }
                                            }

                                            if (mockTests.isNotEmpty()) {
                                                item {
                                                    Text(
                                                        text = "• Mock Tests",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.DarkGray,
                                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                                    )
                                                }
                                                items(mockTests) { test ->
                                                    Card(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                                            .clickable {
                                                                if (test.videoUrl.contains(".pdf", ignoreCase = true)) {
                                                                    selectedPdfToView = test
                                                                } else {
                                                                    try {
                                                                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(test.videoUrl))
                                                                        context.startActivity(browserIntent)
                                                                    } catch (e: Exception) {
                                                                        Toast.makeText(context, "Cannot open test link", Toast.LENGTH_SHORT).show()
                                                                    }
                                                                }
                                                            },
                                                        shape = RoundedCornerShape(8.dp),
                                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF)),
                                                        border = borderHex(Color(0xFFC7D2FE))
                                                    ) {
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(12.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(18.dp))
                                                            Spacer(modifier = Modifier.width(12.dp))
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(test.title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF312E81))
                                                                if (test.description.isNotBlank()) {
                                                                    Text(test.description, fontSize = 10.sp, color = Color.Gray)
                                                                }
                                                            }
                                                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // 6. AI Doubt Solver (at the bottom)
                                        item {
                                            Spacer(modifier = Modifier.height(16.dp))
                                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f), thickness = 1.dp)
                                            Spacer(modifier = Modifier.height(12.dp))

                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                                                border = borderHex(Color(0xFFBBF7D0))
                                            ) {
                                                Column(modifier = Modifier.padding(16.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = Icons.Default.AutoAwesome,
                                                            contentDescription = null,
                                                            tint = Color(0xFF16A34A),
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = "Lakshya AI Doubt Solver",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp,
                                                            color = Color(0xFF14532D)
                                                        )
                                                    }
                                                    Text(
                                                        text = "Ask any doubt from '${dest.chapter}' below. Lakshya AI will analyze and answer instantly with step-by-step explanations.",
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF166534),
                                                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                                                    )

                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .background(Color.White, RoundedCornerShape(8.dp))
                                                            .padding(8.dp)
                                                    ) {
                                                        if (aiMessages.isEmpty()) {
                                                            Text(
                                                                text = "Ready to solve your doubts. Type your question below!",
                                                                fontSize = 11.sp,
                                                                color = Color.Gray,
                                                                modifier = Modifier.padding(8.dp)
                                                            )
                                                        } else {
                                                            aiMessages.forEach { msg ->
                                                                val bubbleBg = if (msg.isUser) Color(0xFFDCFCE7) else Color(0xFFF1F5F9)
                                                                val bubbleAlign = if (msg.isUser) Alignment.End else Alignment.Start
                                                                val bubbleTextColor = if (msg.isUser) Color(0xFF14532D) else Color(0xFF1E293B)

                                                                Column(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .padding(vertical = 4.dp),
                                                                    horizontalAlignment = bubbleAlign
                                                                ) {
                                                                    Text(
                                                                        text = if (msg.isUser) "You" else "Lakshya AI Coach",
                                                                        fontSize = 9.sp,
                                                                        color = Color.Gray,
                                                                        fontWeight = FontWeight.Bold,
                                                                        modifier = Modifier.padding(horizontal = 4.dp)
                                                                    )
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .background(bubbleBg, RoundedCornerShape(8.dp))
                                                                            .padding(8.dp)
                                                                            .widthIn(max = 260.dp)
                                                                    ) {
                                                                        Text(
                                                                            text = msg.text,
                                                                            fontSize = 11.sp,
                                                                            color = bubbleTextColor
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        if (isAiLoading) {
                                                            Row(
                                                                modifier = Modifier.padding(8.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                CircularProgressIndicator(
                                                                    modifier = Modifier.size(12.dp),
                                                                    strokeWidth = 1.5.dp,
                                                                    color = Color(0xFF16A34A)
                                                                )
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Text("AI is thinking...", fontSize = 10.sp, color = Color.Gray)
                                                            }
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(8.dp))

                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        OutlinedTextField(
                                                            value = aiInputText,
                                                            onValueChange = { aiInputText = it },
                                                            placeholder = { Text("Ask a doubt...", fontSize = 12.sp) },
                                                            modifier = Modifier.weight(1f),
                                                            maxLines = 2,
                                                            singleLine = false,
                                                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                                                            colors = OutlinedTextFieldDefaults.colors(
                                                                focusedBorderColor = Color(0xFF16A34A),
                                                                unfocusedBorderColor = Color.LightGray,
                                                                focusedContainerColor = Color.White,
                                                                unfocusedContainerColor = Color.White
                                                            )
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        IconButton(
                                                            onClick = {
                                                                if (aiInputText.isNotBlank()) {
                                                                    val q = aiInputText
                                                                    aiInputText = ""

                                                                    val req = com.example.data.GenerateContentRequest(
                                                                        contents = listOf(
                                                                            com.example.data.Content(
                                                                                parts = listOf(com.example.data.Part(text = q)),
                                                                                role = "user"
                                                                            )
                                                                        ),
                                                                        systemInstruction = com.example.data.Content(
                                                                            parts = listOf(
                                                                                com.example.data.Part(
                                                                                    text = "CRITICAL REQUIREMENT: The student is asking doubts specifically for the chapter: '${dest.chapter}' (Subject: '${dest.subject}'). You MUST answer the student's question ONLY using the context of, and concepts taught within, the chapter: '${dest.chapter}' when possible. Keep answers strictly focused and limited to this chapter's scope. You are Lakshya AI 5.0 Ultra, the smartest AI Teacher. AI Personality: Be patient. Explain politely. Never skip steps. Always motivate students. Support Hindi, English, and Hinglish natively."
                                                                                )
                                                                            )
                                                                        )
                                                                    )

                                                                    isAiLoading = true
                                                                    val userMsg = ChatMessage(text = q, isUser = true)
                                                                    aiMessages.add(userMsg)

                                                                    scope.launch {
                                                                        try {
                                                                            val rawKey = BuildConfig.GEMINI_API_KEY
                                                                            val key = rawKey.trim().removeSurrounding("\"").removeSurrounding("'").trim()
                                                                            val response = com.example.api.RetrofitClient.service.generateContent(
                                                                                fullPath = "v1beta/models/gemini-2.5-flash:generateContent",
                                                                                apiKey = key,
                                                                                request = req
                                                                            )
                                                                            val aiResponse = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "No response from AI."
                                                                            aiMessages.add(ChatMessage(text = aiResponse, isUser = false))
                                                                        } catch (e: Exception) {
                                                                            aiMessages.add(ChatMessage(text = "Error: Failed to fetch AI answer. ${e.localizedMessage}", isUser = false))
                                                                        } finally {
                                                                            isAiLoading = false
                                                                        }
                                                                    }
                                                                }
                                                            },
                                                            modifier = Modifier
                                                                .size(36.dp)
                                                                .background(Color(0xFF16A34A), CircleShape),
                                                            enabled = !isAiLoading && aiInputText.isNotBlank()
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Send,
                                                                contentDescription = "Send",
                                                                tint = Color.White,
                                                                modifier = Modifier.size(16.dp)
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

                            is LmsNavDestination.ResourceCategoryDetail -> {
                                val resources = getLocalResourcesForCategory(dest.category)

                                if (resources.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = null,
                                                tint = Color.LightGray,
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                "No resources published here yet.",
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.Gray
                                            )
                                            Text(
                                                "Admins will upload materials for ${dest.chapter} soon.",
                                                fontSize = 12.sp,
                                                color = Color.LightGray,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        contentPadding = PaddingValues(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        item {
                                            Text(
                                                text = "${dest.batch} > ${dest.subject} > ${dest.chapter}",
                                                fontSize = 12.sp,
                                                color = BrandBluePrimary,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(bottom = 6.dp)
                                            )
                                        }

                                        items(resources) { item ->
                                            val isVideo = dest.category == "VIDEO"
                                            val isPlaying = selectedVideoToPlay?.id == item.id
                                            
                                            // Handle sequential locking logic for Video types
                                            val isLocked = remember(item, resources, unlockedVideosCount) {
                                                if (!isVideo) false else {
                                                    val indexInChapter = resources.indexOfFirst { it.id == item.id }
                                                    if (indexInChapter <= 0) false else {
                                                        val prevItem = resources[indexInChapter - 1]
                                                        // Checked if marked completed in SharedPreferences
                                                        !sharedPrefs.getBoolean("video_completed_${prevItem.id}", false)
                                                    }
                                                }
                                            }

                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        if (isLocked) {
                                                            val indexInChapter = resources.indexOfFirst { it.id == item.id }
                                                            val prevTitle = if (indexInChapter > 0) resources[indexInChapter - 1].title else "previous"
                                                            Toast.makeText(
                                                                context,
                                                                "Lock 🔒: Please complete previous video: '$prevTitle' first!",
                                                                Toast.LENGTH_LONG
                                                            ).show()
                                                        } else {
                                                            if (isVideo) {
                                                                selectedVideoToPlay = item
                                                                // Automatically mark standard complete when tapped or progress hits 90% (we can auto-complete for convenience)
                                                                sharedPrefs.edit().putBoolean("video_completed_${item.id}", true).apply()
                                                                unlockedVideosCount++
                                                            } else {
                                                                // Open PDF Secure Viewer inside App
                                                                selectedPdfToView = item
                                                            }
                                                        }
                                                    },
                                                shape = RoundedCornerShape(10.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isPlaying) BrandBluePrimary.copy(alpha = 0.05f) else Color.White
                                                ),
                                                border = borderHex(
                                                    if (isLocked) Color.Red.copy(alpha = 0.2f)
                                                    else if (isPlaying) BrandBluePrimary
                                                    else Color.LightGray.copy(alpha = 0.3f)
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Leading Icon block
                                                    Box(
                                                        modifier = Modifier
                                                            .size(40.dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                if (isLocked) Color(0xFFFEE2E2)
                                                                else if (isPlaying) BrandBluePrimary
                                                                else Color(0xFFF1F5F9)
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isLocked) Icons.Default.Lock
                                                            else if (isVideo) Icons.Default.PlayCircle
                                                            else Icons.Default.PictureAsPdf,
                                                            contentDescription = null,
                                                            tint = if (isLocked) Color.Red
                                                            else if (isPlaying) Color.White
                                                            else BrandBluePrimary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.width(12.dp))

                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                text = item.title,
                                                                fontWeight = FontWeight.SemiBold,
                                                                fontSize = 13.sp,
                                                                color = if (isLocked) Color.Gray else Color(0xFF1E293B),
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            if (item.duration.isNotBlank()) {
                                                                Text(
                                                                    text = item.duration,
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = BrandBlueSecondary,
                                                                    modifier = Modifier.padding(start = 4.dp)
                                                                )
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = if (item.description.isNotBlank()) item.description else "No details added",
                                                            fontSize = 11.sp,
                                                            color = Color.Gray,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }

                                                    // Locking badge label
                                                    if (isLocked) {
                                                        Text(
                                                            text = "Sequential 🔒",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.Red,
                                                            modifier = Modifier
                                                                .padding(start = 8.dp)
                                                                .background(Color(0xFFFEE2E2), RoundedCornerShape(4.dp))
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                if (!item.pdfUrl.isNullOrBlank()) {
                                                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.2f))
                                                    TextButton(
                                                        onClick = {
                                                            if (item.pdfUrl.contains("|")) {
                                                                showPdfChooserForVideo = item
                                                            } else {
                                                                selectedPdfToView = item.copy(videoUrl = item.pdfUrl, title = item.title + " (Notes)")
                                                            }
                                                        },
                                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
                                                    ) {
                                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text("View Notes / Download PDF", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
            }
        }
    }

    if (selectedCourseForDetail != null) {
        CourseDetailEnrollmentDialog(
            course = selectedCourseForDetail!!,
            viewModel = viewModel,
            onEnrollSuccess = { selectedCourseForDetail = null },
            onDismiss = { selectedCourseForDetail = null }
        )
    }

    if (showPdfChooserForVideo != null) {
        val pdfs = showPdfChooserForVideo!!.pdfUrl?.split("|") ?: emptyList()
        val pdf1 = pdfs.getOrNull(0) ?: ""
        val pdf2 = pdfs.getOrNull(1) ?: ""
        AlertDialog(
            onDismissRequest = { showPdfChooserForVideo = null },
            title = { Text("Study Material & Notes", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Select which notes you want to view/download for this lecture:", fontSize = 14.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    if (pdf1.isNotBlank()) {
                        Button(
                            onClick = {
                                selectedPdfToView = showPdfChooserForVideo!!.copy(videoUrl = pdf1, title = showPdfChooserForVideo!!.title + " (Handwritten Notes)")
                                showPdfChooserForVideo = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("PDF 1 (Handwritten Notes)")
                        }
                    }
                    if (pdf2.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                selectedPdfToView = showPdfChooserForVideo!!.copy(videoUrl = pdf2, title = showPdfChooserForVideo!!.title + " (Board/Class Notes)")
                                showPdfChooserForVideo = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) {
                            Icon(Icons.Default.BorderColor, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("PDF 2 (Board/Class Notes)")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPdfChooserForVideo = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun R2BatchCard(
    course: CourseEntity,
    isEnrolled: Boolean,
    onClick: () -> Unit,
    onEnrollClick: () -> Unit
) {
    var isFavorite by remember { mutableStateOf(false) }
    val displayImageUrl = remember(course.imageUrl, course.id, course.title) {
        when {
            course.id == 6 || course.title.contains("Refresh Your Mind", ignoreCase = true) -> {
                "https://kugyjkowjtbbpyxsbiup.supabase.co/storage/v1/object/public/videos/WhatsApp%20Image%202026-08-10%20at%2012.29.02%20PM.jpeg"
            }
            else -> course.imageUrl
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(0.5.dp, Color.LightGray.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = course.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xFF1E293B)
                )
                IconButton(onClick = { isFavorite = !isFavorite }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFavorite) Color.Red else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF1F5F9))
            ) {
                if (displayImageUrl.isNotBlank()) {
                    AsyncImage(
                        model = displayImageUrl,
                        contentDescription = "Batch Cover",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onState = { state ->
                            if (state is coil.compose.AsyncImagePainter.State.Error) {
                                android.util.Log.e("R2BatchCard", "[IMAGE ERROR] Failed to load: $displayImageUrl", state.result.throwable)
                            }
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.School,
                                null,
                                modifier = Modifier.size(48.dp),
                                tint = Color.LightGray
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("No Cover Image", fontSize = 10.sp, color = Color.LightGray)
                        }
                    }
                }
                
                Surface(
                    modifier = Modifier.padding(12.dp).align(Alignment.TopStart),
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        course.category.uppercase(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (course.isFree) "FREE" else "₹${course.price.toInt()}",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = if (course.isFree) Color(0xFF10B981) else Color(0xFF1E293B)
                    )
                    Text("Validity: 1 Year", fontSize = 11.sp, color = Color.Gray)
                }
                
                Button(
                    onClick = {
                        if (isEnrolled) {
                            onClick()
                        } else {
                            onEnrollClick()
                        }
                    },
                    modifier = Modifier.height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isEnrolled) Color(0xFF10B981) else Color(0xFF6366F1)
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Text(
                        text = if (isEnrolled) "Continue Learning" else "Enroll Now",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

private fun extractFileNameFromUrl(url: String, defaultName: String): String {
    try {
        val uri = android.net.Uri.parse(url)
        val path = uri.path
        if (!path.isNullOrBlank()) {
            val lastSegment = path.substringAfterLast('/')
            if (lastSegment.endsWith(".pdf", ignoreCase = true)) {
                val cleanSegment = lastSegment.substringBefore('?').substringBefore('#')
                if (cleanSegment.endsWith(".pdf", ignoreCase = true)) {
                    return cleanSegment.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    val sanitized = defaultName.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
    return if (sanitized.endsWith(".pdf", ignoreCase = true)) sanitized else "$sanitized.pdf"
}

private fun checkIfFileExistsInDownloads(context: Context, fileName: String): Boolean {
    val filePublic = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), fileName)
    if (filePublic.exists()) return true

    val filePrivate = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), fileName)
    if (filePrivate.exists()) return true

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        val projection = arrayOf(android.provider.MediaStore.Downloads._ID)
        val selection = "${android.provider.MediaStore.Downloads.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(fileName)
        val contentUri = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
        try {
            context.contentResolver.query(contentUri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.count > 0) return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return false
}

private fun getDownloadedFileContentUri(context: Context, fileName: String): Uri? {
    val filePublic = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), fileName)
    if (filePublic.exists()) {
        return try {
            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", filePublic)
        } catch (e: Exception) {
            Uri.fromFile(filePublic)
        }
    }

    val filePrivate = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), fileName)
    if (filePrivate.exists()) {
        return try {
            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", filePrivate)
        } catch (e: Exception) {
            Uri.fromFile(filePrivate)
        }
    }

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        val projection = arrayOf(android.provider.MediaStore.Downloads._ID)
        val selection = "${android.provider.MediaStore.Downloads.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(fileName)
        val contentUri = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
        try {
            context.contentResolver.query(contentUri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Downloads._ID)
                    val id = cursor.getLong(idColumn)
                    return android.content.ContentUris.withAppendedId(contentUri, id)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return null
}

private fun openPdfFileUri(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "No PDF reader application found to open this file.", Toast.LENGTH_LONG).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InAppPdfViewer(pdfUrl: String, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    
    val fileName = remember(pdfUrl, title) {
        extractFileNameFromUrl(pdfUrl, title)
    }
    
    var fileExists by remember { mutableStateOf(false) }
    
    LaunchedEffect(fileName) {
        fileExists = checkIfFileExistsInDownloads(context, fileName)
    }
    
    var downloadId by remember { mutableStateOf<Long?>(null) }
    var downloadProgress by remember { mutableStateOf<Float?>(null) }
    var downloadStatusText by remember { mutableStateOf("") }
    
    LaunchedEffect(downloadId) {
        val id = downloadId ?: return@LaunchedEffect
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        var downloading = true
        while (downloading) {
            kotlinx.coroutines.delay(500)
            val query = android.app.DownloadManager.Query().setFilterById(id)
            try {
                downloadManager.query(query)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val statusIdx = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS)
                        val bytesDownloadedIdx = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val totalBytesIdx = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        val reasonIdx = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_REASON)

                        val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else -1
                        val bytesDownloaded = if (bytesDownloadedIdx >= 0) cursor.getLong(bytesDownloadedIdx) else 0L
                        val totalBytes = if (totalBytesIdx >= 0) cursor.getLong(totalBytesIdx) else 0L

                        when (status) {
                            android.app.DownloadManager.STATUS_RUNNING -> {
                                if (totalBytes > 0) {
                                    downloadProgress = bytesDownloaded.toFloat() / totalBytes.toFloat()
                                    downloadStatusText = "Downloading: ${(downloadProgress!! * 100).toInt()}%"
                                } else {
                                    downloadProgress = 0.01f
                                    downloadStatusText = "Downloading..."
                                }
                            }
                            android.app.DownloadManager.STATUS_SUCCESSFUL -> {
                                downloadProgress = 1.0f
                                downloadStatusText = "PDF downloaded successfully"
                                fileExists = true
                                Toast.makeText(context, "PDF downloaded successfully", Toast.LENGTH_SHORT).show()
                                downloading = false
                                kotlinx.coroutines.delay(2000)
                                downloadProgress = null
                                downloadStatusText = ""
                            }
                            android.app.DownloadManager.STATUS_FAILED -> {
                                val reason = if (reasonIdx >= 0) cursor.getInt(reasonIdx) else -1
                                val errorMsg = "Download failed (code $reason)"
                                downloadStatusText = errorMsg
                                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                                downloading = false
                                kotlinx.coroutines.delay(3000)
                                downloadProgress = null
                                downloadStatusText = ""
                            }
                            android.app.DownloadManager.STATUS_PAUSED -> {
                                downloadStatusText = "Download paused..."
                            }
                            android.app.DownloadManager.STATUS_PENDING -> {
                                downloadStatusText = "Download pending..."
                            }
                        }
                    } else {
                        downloading = false
                        downloadProgress = null
                        downloadStatusText = ""
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                downloading = false
                downloadProgress = null
                downloadStatusText = ""
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Check out this PDF: $pdfUrl")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share PDF Link"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        val encodedUrl = remember(pdfUrl) {
            java.net.URLEncoder.encode(pdfUrl, "UTF-8")
        }
        val googleDocsUrl = "https://docs.google.com/gview?embedded=true&url=$encodedUrl"
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AndroidView(
                    factory = { ctx ->
                        android.webkit.WebView(ctx).apply {
                            setLayerType(android.view.View.LAYER_TYPE_NONE, null)
                            android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = true
                                builtInZoomControls = true
                                displayZoomControls = false
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                }
                            }
                            webViewClient = android.webkit.WebViewClient()
                            webChromeClient = android.webkit.WebChromeClient()
                            loadUrl(googleDocsUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📄",
                            fontSize = 18.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = fileName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    downloadProgress?.let { progress ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = downloadStatusText,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (fileExists) {
                                val existingUri = getDownloadedFileContentUri(context, fileName)
                                if (existingUri != null) {
                                    openPdfFileUri(context, existingUri)
                                } else {
                                    Toast.makeText(context, "Opening existing file...", Toast.LENGTH_SHORT).show()
                                    try {
                                        val dmIntent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)
                                        context.startActivity(dmIntent)
                                    } catch (ex: Exception) {
                                        Toast.makeText(context, "Please open Downloads folder manually.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } else {
                                if (downloadProgress == null) {
                                    try {
                                        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                        val uri = Uri.parse(pdfUrl)
                                        val buildReq = { usePublicDir: Boolean ->
                                            android.app.DownloadManager.Request(uri)
                                                .setTitle(fileName)
                                                .setDescription("Downloading PDF...")
                                                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                                .setMimeType("application/pdf")
                                                .setAllowedOverMetered(true)
                                                .setAllowedOverRoaming(true)
                                                .apply {
                                                    if (usePublicDir) {
                                                        setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
                                                    } else {
                                                        setDestinationInExternalFilesDir(context, android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
                                                    }
                                                }
                                        }
                                        
                                        downloadId = try {
                                            downloadManager.enqueue(buildReq(true))
                                        } catch (ex: Exception) {
                                            downloadManager.enqueue(buildReq(false))
                                        }
                                        Toast.makeText(context, "Download started: $fileName", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                                        e.printStackTrace()
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (fileExists) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                        ),
                        enabled = downloadProgress == null
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (fileExists) "📂 Open Downloaded" else "⬇️ Download",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(pdfUrl))
                                intent.addCategory(Intent.CATEGORY_BROWSABLE)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "No web browser found to open this link.", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "🌐 Web",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun borderHex(color: Color) = androidx.compose.foundation.BorderStroke(1.dp, color)
