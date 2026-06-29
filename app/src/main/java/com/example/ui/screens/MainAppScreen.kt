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
import com.example.R
import com.example.data.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.AcademyViewModel
import kotlinx.coroutines.delay

fun formatQuestionOrOptionText(text: String, language: String): String {
    if (!text.contains(" / ")) {
        return text
    }
    val parts = text.split(" / ", limit = 2)
    return when (language) {
        "ENG" -> parts[0].trim()
        "HIN" -> parts.getOrNull(1)?.trim() ?: parts[0].trim()
        else -> text
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

    MyApplicationTheme(darkTheme = darkTheme) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (!isInternetAvailable) {
                NoInternetScreen(onRetry = { viewModel.checkInternetStatus() })
            } else if (currentUser == null) {
                AuthScreen(
                    onLogin = { email, name, role, isSignUp -> viewModel.login(email, name, role, isSignUp) },
                    authError = viewModel.authError
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

// === MAIN STUDENT CONTAINER ===
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentMainContainer(viewModel: AcademyViewModel) {
    var studentTab by remember { mutableStateOf("home") } // home, courses, profile
    var activeStudyCourse by remember { mutableStateOf<CourseEntity?>(null) }


    val context = LocalContext.current
    LaunchedEffect(viewModel.currentUser) {
        val u = viewModel.currentUser
        if (u != null) {
        }
    }

    Scaffold(
        bottomBar = {
            if (activeStudyCourse == null) {
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
        Box(modifier = Modifier.padding(innerPadding)) {
            if (activeStudyCourse != null) {
                StudentLessonsMediaWorkspace(
                    course = activeStudyCourse!!,
                    completedCount = 0,
                    viewModel = viewModel,
                    onDismiss = { activeStudyCourse = null }
                )
            } else {
                when (studentTab) {
                    "home" -> StudentHomeDashboard(
                        viewModel = viewModel,
                        onTabSelect = { studentTab = it },
                        onPlayCourse = { activeStudyCourse = it }
                    )
                    "courses" -> StudentMyCourses(
                        viewModel = viewModel,
                        onPlayCourse = { activeStudyCourse = it }
                    )
                    "profile" -> StudentProfileView(viewModel = viewModel)
                    "TESTS" -> StudentTestHub(viewModel = viewModel)
                    "doubt_solver" -> LakshyaAiScreen(viewModel = viewModel, onBack = { studentTab = "home" })
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
                    Triple("BANNERS", "Banners", Icons.Default.Image),
                    Triple("ALERTS", "Alerts", Icons.Default.Notifications)
                )
                tabs.forEach { (route, label, icon) ->
                    NavigationBarItem(
                        selected = adminTab == route,
                        onClick = { adminTab = route },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 10.sp) }
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
                "BANNERS" -> AdminBannerManager(viewModel = viewModel)
                "ALERTS" -> AdminNotificationAlerts(viewModel = viewModel)
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
                    Image(painter = painterResource(id = R.drawable.lakshya_logo), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
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
fun DashboardBrandHeader(studentName: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Lakshya Academy", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("Namaste $studentName! 👋", fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
            Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(Color.White)) {
                Image(painter = painterResource(id = R.drawable.lakshya_logo), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
    }
}

@Composable
fun BannerCarousel(banners: List<BannerEntity>, onTabSelect: (String) -> Unit) {
    if (banners.isEmpty()) return
    var currentIndex by remember { mutableIntStateOf(0) }
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(Unit) { while(true) { delay(5000); currentIndex = (currentIndex + 1) % banners.size } }
    val banner = banners[currentIndex]
    Card(modifier = Modifier.fillMaxWidth().height(160.dp), shape = RoundedCornerShape(16.dp)) {
        Box {
            val imageModel = if (banner.imageUrl.startsWith("/")) java.io.File(banner.imageUrl) else banner.imageUrl
            Image(painter = rememberAsyncImagePainter(imageModel), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Bottom) {
                Text(banner.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Button(onClick = { 
                    if (banner.linkUrl.startsWith("http")) uriHandler.openUri(banner.linkUrl) 
                    else onTabSelect(banner.linkUrl) 
                }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))) {
                    Text(banner.buttonText, color = Color.White)
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
                val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                if (isFullScreen) {
                    window.setDimAmount(0f)
                    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
                    controller.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars() or androidx.core.view.WindowInsetsCompat.Type.navigationBars())
                    controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    window.setDimAmount(0.5f)
                    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, true)
                    controller.show(androidx.core.view.WindowInsetsCompat.Type.statusBars() or androidx.core.view.WindowInsetsCompat.Type.navigationBars())
                }
            }
        }
        
        Scaffold(
            contentWindowInsets = if (isFullScreen) WindowInsets(0.dp) else ScaffoldDefaults.contentWindowInsets,
            topBar = {
                if (!isFullScreen) {
                    TopAppBar(
                        title = { Text(course.title) },
                        navigationIcon = { IconButton(onClick = { if (navStack.size > 1) navStack = navStack.dropLast(1) else onDismiss() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
                    )
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(if (isFullScreen) PaddingValues(0.dp) else padding)) {
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
                        Column(modifier = if(isFullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
                            VideoPlayerView(
                                lesson = currentScreen.lesson, 
                                isFullScreen = isFullScreen,
                                onFullScreenToggle = { isFullScreen = it }
                            )
                            if (!isFullScreen) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(currentScreen.lesson.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Text(currentScreen.lesson.chapterName, fontSize = 14.sp, color = Color.Gray)
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

    val tests by viewModel.allTests.collectAsStateWithLifecycle()
    val scores by viewModel.allScores.collectAsStateWithLifecycle()
    val userScoreMap = scores.filter { it.userEmail == viewModel.currentUser?.email }.associateBy { it.testId }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF6366F1), RoundedCornerShape(12.dp)).padding(16.dp)) {
            Column {
                Text("Mock Test & Test Series Hub", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Auto-generated weekly mock tests for Class 5th to 12th now available!", color = Color.White.copy(alpha=0.8f), fontSize = 13.sp)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        if (tests.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading or no tests available...", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(tests) { test ->
                    val userScore = userScoreMap[test.id]
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(test.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Timer, null, modifier = Modifier.size(16.dp), tint=Color.Gray)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${test.durationMinutes} mins | ${test.type}", color = Color.Gray, fontSize=12.sp)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            if (userScore != null) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Score: ${userScore.score} (Correct: ${userScore.correctAnswers})", color = Color(0xFF10B981), fontWeight = FontWeight.SemiBold)
                                    Button(onClick = { viewModel.startTest(test) }, shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray)) {
                                        Text("View Result", color=Color.Black)
                                    }
                                }
                            } else {
                                Button(onClick = { viewModel.startTest(test) }, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Text("Start Test")
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
    
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(progress.test.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize=16.sp) },
            navigationIcon = {
                IconButton(onClick = { viewModel.exitTest() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit Test")
                }
            },
            actions = {
                Text(
                    text = String.format("%02d:%02d", progress.secondsRemaining / 60, progress.secondsRemaining % 60),
                    modifier = Modifier.padding(end = 16.dp),
                    fontWeight = FontWeight.Bold,
                    color = Color.Red
                )
            }
        )
        LinearProgressIndicator(
            progress = { if (progress.questions.isNotEmpty()) (progress.currentQuestionIndex + 1) / progress.questions.size.toFloat() else 0f },
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF6366F1)
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
        
        if (currentQuestion != null) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                Text("Question ${progress.currentQuestionIndex + 1} of ${progress.questions.size}", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(formatQuestionOrOptionText(currentQuestion.questionText, testLanguage), fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(24.dp))
                
                val options = listOf(currentQuestion.optionA, currentQuestion.optionB, currentQuestion.optionC, currentQuestion.optionD)
                options.forEachIndexed { index, optionText ->
                    val isSelected = progress.selectedAnswers[currentQuestion.id] == index
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                            viewModel.selectTestAnswer(currentQuestion.id, index)
                        },
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFEEF2FF) else Color.White),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF6366F1) else Color.LightGray)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = isSelected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor=Color(0xFF6366F1)))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(formatQuestionOrOptionText(optionText, testLanguage))
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(
                        onClick = { viewModel.selectTestAnswer(currentQuestion.id, progress.selectedAnswers[currentQuestion.id] ?: -1); viewModel.updateTestQuestionIndex(progress.currentQuestionIndex - 1) },
                        enabled = progress.currentQuestionIndex > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray, contentColor = Color.Black)
                    ) { Text("Previous") }
                    
                    if (progress.currentQuestionIndex < progress.questions.size - 1) {
                        Button(onClick = { viewModel.updateTestQuestionIndex(progress.currentQuestionIndex + 1) }) { Text("Next") }
                    } else {
                        Button(
                            onClick = { viewModel.submitActiveTest() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) { Text("Submit Test") }
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
    var testLanguage by remember { mutableStateOf("BOTH") } // "ENG", "HIN", "BOTH"
    
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Performance Report") }, navigationIcon = { IconButton(onClick = { viewModel.exitTest() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } })
        
        Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB))) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total Score: ${score.score}", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color(0xFF6366F1))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Correct: ${score.correctAnswers}  |  Wrong: ${score.wrongAnswers}  |  Unattempted: ${score.totalQuestions - (score.correctAnswers + score.wrongAnswers)}", color = Color.Gray, fontSize=14.sp)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

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

            Spacer(modifier = Modifier.height(16.dp))
            Text("Detailed Question Review:", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(16.dp))
            
            progress.questions.forEachIndexed { i, q ->
                val selectedIdx = progress.selectedAnswers[q.id]
                val isCorrect = selectedIdx == q.correctIndex
                
                Card(modifier = Modifier.fillMaxWidth().padding(vertical=4.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color.LightGray)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Q${i+1}. ${formatQuestionOrOptionText(q.questionText, testLanguage)}", fontWeight=FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        
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
                Text("Follow Us", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF6366F1))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)
                
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        uriHandler.openUri("https://www.instagram.com/lakshya_academy_sirgitha_gzpr?igsh=MXU5eHVicWRhNmgwag==")
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
