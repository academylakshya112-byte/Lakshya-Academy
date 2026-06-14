package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import android.content.Intent
import android.net.Uri
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.VideoView
import android.widget.MediaController
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.R
import com.example.data.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

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
                NoInternetScreen(
                    onRetry = {
                        viewModel.checkInternetStatus()
                    }
                )
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

// ================= AUTHENTICATION SCREEN =================
@Composable
fun AuthScreen(
    onLogin: (String, String, String, Boolean) -> Unit,
    authError: String?
) {
    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("STUDENT") } // STUDENT or ADMIN
    var showForgotPasswordDialog by remember { mutableStateOf(false) }

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(BrandBluePrimary, BrandBlueSecondary)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Welcoming Header Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(gradientBrush),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(BorderStroke(2.dp, Color.White), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.lakshya_logo),
                        contentDescription = "Lakshya Logo",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Lakshya Academy",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Ghazipur • Competitive Prep Center",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isSignUp) "Student Registration" else "Welcome Back",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("auth_title")
                )
                Text(
                    text = if (isSignUp) "Create your online profile to begin" else "Access your notes, tests, and videos",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                if (authError != null) {
                    Text(
                        text = authError,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("email_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("name_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("password_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Role Selector (STUDENT or ADMIN)
                Text(
                    text = "Select Login Workspace:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { selectedRole = "STUDENT" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedRole == "STUDENT") MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.3f),
                            contentColor = if (selectedRole == "STUDENT") Color.White else MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("role_student"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Student App")
                    }
                    Button(
                        onClick = { selectedRole = "ADMIN" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedRole == "ADMIN") MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.3f),
                            contentColor = if (selectedRole == "ADMIN") Color.White else MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("role_admin"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Admin portal")
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { onLogin(email, name, selectedRole, isSignUp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("login_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isSignUp) "Register Account" else "Authenticate Securely",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(onClick = { showForgotPasswordDialog = true }) {
                    Text("Forgot Password?", color = BrandBlueSecondary)
                }

                TextButton(onClick = { isSignUp = !isSignUp }) {
                    Text(
                        text = if (isSignUp) "Already have an account? Login" else "Don't have an account? Sign Up",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Direct Quick-Login sandbox shortcuts for testing convenience!
        Text(text = "Sandbox Demonstration Logins:", fontSize = 12.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
            Button(
                onClick = { onLogin("student@lakshya.com", "Anand Yadav", "STUDENT", false) },
                colors = ButtonDefaults.buttonColors(containerColor = BrandBlueSecondary),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("Demo Student", fontSize = 12.sp)
            }
            Button(
                onClick = { onLogin("admin@lakshya.com", "Director Sir", "ADMIN", false) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("Demo Administrator", fontSize = 12.sp)
            }
        }
    }

    if (showForgotPasswordDialog) {
        Dialog(onDismissRequest = { showForgotPasswordDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Forgot Password", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BrandBluePrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Enter your email address to receive password reset notification link details.", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    var resetEmail by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        label = { Text("Email Address") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showForgotPasswordDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = { showForgotPasswordDialog = false }) {
                            Text("Send Link")
                        }
                    }
                }
            }
        }
    }
}

// ================= STUDENT MAIN WORKSPACE INTERFACES =================
@Composable
fun StudentMainContainer(viewModel: AcademyViewModel) {
    var selectedTab by remember { mutableStateOf("home") } // home, courses, test, doubt, profile
    val activeTest = viewModel.activeTestProgress
    val liveActive = viewModel.liveClassRoomActive
    var activeStudyCourse by remember { mutableStateOf<CourseEntity?>(null) }

    val enrollments by viewModel.allEnrollments.collectAsStateWithLifecycle()
    val userEmail = viewModel.currentUser?.email ?: ""

    Scaffold(
        bottomBar = {
            if (activeTest == null && !liveActive) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    val tabs = listOf(
                        Triple("home", "Home", Icons.Default.Home),
                        Triple("courses", "My Study", Icons.Default.Book),
                        Triple("test", "Tests", Icons.Default.Assignment),
                        Triple("doubt", "Doubts", Icons.Default.Chat),
                        Triple("profile", "Profile", Icons.Default.Person)
                    )
                    tabs.forEach { (route, label, icon) ->
                        NavigationBarItem(
                            selected = selectedTab == route,
                            onClick = { selectedTab = route },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("bottom_nav_$route")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                activeTest != null -> {
                    StudentActiveTestScreen(viewModel = viewModel)
                }
                liveActive -> {
                    StudentLiveClassRoom(viewModel = viewModel)
                }
                else -> {
                    when (selectedTab) {
                        "home" -> StudentHomeDashboard(
                            viewModel = viewModel,
                            onTabSelect = { selectedTab = it },
                            onPlayCourse = { activeStudyCourse = it }
                        )
                        "courses" -> StudentMyCourses(
                            viewModel = viewModel,
                            onPlayCourse = { activeStudyCourse = it }
                        )
                        "test" -> StudentTestHub(viewModel = viewModel)
                        "doubt" -> StudentDoubtForum(viewModel = viewModel)
                        "profile" -> StudentProfileScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }

    if (activeStudyCourse != null) {
        val course = activeStudyCourse!!
        val isEnrolled = enrollments.any { it.courseId == course.id && it.userEmail == userEmail }

        // Automatically enroll student on-the-fly to enable direct study in classroom demo
        LaunchedEffect(course.id) {
            if (!isEnrolled) {
                viewModel.enrollInCourse(course.id)
            }
        }

        val enrollment = enrollments.find { it.courseId == course.id && it.userEmail == userEmail }
        StudentLessonsMediaWorkspace(
            course = course,
            completedCount = enrollment?.completedLessonsCount ?: 0,
            viewModel = viewModel,
            onDismiss = { activeStudyCourse = null }
        )
    }
}

// === Student Home Dashboard ===
@Composable
fun StudentHomeDashboard(
    viewModel: AcademyViewModel,
    onTabSelect: (String) -> Unit,
    onPlayCourse: (CourseEntity) -> Unit
) {
    val courses by viewModel.allCourses.collectAsStateWithLifecycle()
    val search = viewModel.searchQuery
    val category = viewModel.selectedCategory
    var showMaterialTypeDialog by remember { mutableStateOf<String?>(null) }
    var selectedCourseForDetail by remember { mutableStateOf<CourseEntity?>(null) }

    // Filter controls for Paid / Free classes
    var showPaidOnlyFilter by remember { mutableStateOf<Boolean?>(null) }
    var showWeeklyTestsDialog by remember { mutableStateOf(false) }
    val allTests by viewModel.allTests.collectAsStateWithLifecycle()

    // Filter courses base on category, query and paid/free attributes
    val filteredCourses = courses.filter {
        (category == "All" || it.category.equals(category, ignoreCase = true)) &&
                it.title.contains(search, ignoreCase = true) &&
                (showPaidOnlyFilter == null || it.isFree != showPaidOnlyFilter)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        item {
            // Academy Brand Card
            DashboardBrandHeader(viewModel.currentUser?.name ?: "Student")
            Spacer(modifier = Modifier.height(12.dp))

            // Dynamic Poster Slides Carousel Card
            BannerCarousel(viewModel = viewModel, onTabSelect = onTabSelect)
            Spacer(modifier = Modifier.height(16.dp))

            // Live Class Badge Card
            LiveClassPromoCard(
                title = viewModel.activeLiveStreamTitle,
                teacher = viewModel.activeLiveStreamTeacher,
                onJoinLive = { viewModel.joinLiveClass() }
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Search and Category Panel
            DashboardSearchSection(
                query = viewModel.searchQuery,
                onQueryChange = { viewModel.searchQuery = it },
                selectedCategory = viewModel.selectedCategory,
                onCategorySelect = { viewModel.selectedCategory = it }
            )
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Academic Drawer & Support materials",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Dynamic 3x3 Grid Dashboard matching screenshot
            Academic3x3GridDashboard(
                onPaidSelect = { showPaidOnlyFilter = true },
                onFreeSelect = { showPaidOnlyFilter = false },
                onWeeklyTestSelect = { showWeeklyTestsDialog = true },
                onTestSeriesSelect = { onTabSelect("test") },
                onPdfSelect = { showMaterialTypeDialog = "Current Affairs" },
                onBookSelect = { showMaterialTypeDialog = "Book" },
                onSyllabusSelect = { showMaterialTypeDialog = "Syllabus" },
                onTimetableSelect = { showMaterialTypeDialog = "Timetable" },
                onPypSelect = { showMaterialTypeDialog = "Previous Year Paper" }
            )
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Target Exam preparation batches",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (showPaidOnlyFilter != null) {
                    AssistChip(
                        onClick = { showPaidOnlyFilter = null },
                        label = { Text("Reset Filter x", fontSize = 11.sp) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    )
                }
            }
            Text(
                text = "Purchase premium classes with complete structured test series",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        if (filteredCourses.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.HourglassEmpty,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color.LightGray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No courses found. Try resetting custom filters.", color = Color.Gray, fontSize = 13.sp)
                    }
                }
            }
        } else {
            items(filteredCourses) { course ->
                CourseGridCard(
                    course = course,
                    onStudyClick = { onPlayCourse(course) },
                    onDetailClick = { selectedCourseForDetail = course }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Material Dialog drawer display
    if (showMaterialTypeDialog != null) {
        val type = showMaterialTypeDialog ?: ""
        MaterialDocumentViewerDialog(
            type = type,
            viewModel = viewModel,
            onDismiss = { showMaterialTypeDialog = null }
        )
    }

    // Weekly Tests Dialog trigger
    if (showWeeklyTestsDialog) {
        WeeklyTestsDialog(
            tests = allTests,
            onStartTest = { viewModel.startTest(it) },
            onDismiss = { showWeeklyTestsDialog = false }
        )
    }

    // Course detail / enrollment bottom sheet trigger
    if (selectedCourseForDetail != null) {
        val course = selectedCourseForDetail!!
        CourseDetailEnrollmentDialog(
            course = course,
            viewModel = viewModel,
            onDismiss = { selectedCourseForDetail = null },
            onEnrollSuccess = {
                selectedCourseForDetail = null
                onTabSelect("courses")
            }
        )
    }
}

// === 3x3 Dynamic Grid custom dashboard matching design layout ===
@Composable
fun Academic3x3GridDashboard(
    onPaidSelect: () -> Unit,
    onFreeSelect: () -> Unit,
    onWeeklyTestSelect: () -> Unit,
    onTestSeriesSelect: () -> Unit,
    onPdfSelect: () -> Unit,
    onBookSelect: () -> Unit,
    onSyllabusSelect: () -> Unit,
    onTimetableSelect: () -> Unit,
    onPypSelect: () -> Unit
) {
    val items = listOf(
        GridItemData(label = "Paid Classes", icon = Icons.Default.Star, color = Color(0xFFFF9800)),
        GridItemData(label = "Free Courses", icon = Icons.Default.School, color = Color(0xFF4CAF50)),
        GridItemData(label = "Weekly Tests", icon = Icons.Default.Schedule, color = Color(0xFFF44336)),
        GridItemData(label = "Test Series", icon = Icons.Default.Assignment, color = Color(0xFF2196F3)),
        GridItemData(label = "PDF Notes", icon = Icons.Default.Article, color = Color(0xFFE91E63)),
        GridItemData(label = "Books", icon = Icons.Default.MenuBook, color = Color(0xFF00BCD4)),
        GridItemData(label = "Syllabus", icon = Icons.Default.List, color = Color(0xFF8BC34A)),
        GridItemData(label = "Timetable", icon = Icons.Default.DateRange, color = Color(0xFF9C27B0)),
        GridItemData(label = "Previous Year", icon = Icons.Default.WorkspacePremium, color = Color(0xFF673AB7))
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(vertical = 12.dp, horizontal = 4.dp)
    ) {
        val actions = listOf(
            onPaidSelect,
            onFreeSelect,
            onWeeklyTestSelect,
            onTestSeriesSelect,
            onPdfSelect,
            onBookSelect,
            onSyllabusSelect,
            onTimetableSelect,
            onPypSelect
        )
        for (i in 0 until 3) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (j in 0 until 3) {
                    val idx = i * 3 + j
                    if (idx < items.size) {
                        val item = items[idx]
                        val action = actions[idx]
                        AcademicGridItem(
                            label = item.label,
                            icon = item.icon,
                            bgColor = item.color,
                            onClick = action,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

data class GridItemData(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color,
    val iconColor: Color = Color.White
)

@Composable
fun AcademicGridItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    bgColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(bgColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = bgColor,
                modifier = Modifier.size(23.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun WeeklyTestsDialog(
    tests: List<TestEntity>,
    onStartTest: (TestEntity) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.72f),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Weekly Live Test Series",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandBluePrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Compete live with exam mock parameters",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(16.dp))

                val weekly = tests.filter { it.type == "Weekly Test" }
                if (weekly.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No weekly tests active right now.", color = Color.Gray, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(weekly) { test ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onStartTest(test)
                                        onDismiss()
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(BrandBluePrimary.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Assignment, contentDescription = null, tint = BrandBluePrimary)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(test.title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text("${test.durationMinutes} Mins • Correct: +${test.marksPerCorrect} • Neg: ${test.marksPerWrong}", fontSize = 11.sp, color = Color.Gray)
                                    }
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Start", tint = BrandBluePrimary)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun BannerCarousel(
    viewModel: AcademyViewModel,
    onTabSelect: (String) -> Unit
) {
    val banners by viewModel.allBanners.collectAsStateWithLifecycle()
    val context = LocalContext.current

    if (banners.isEmpty()) return

    var currentIndex by remember { mutableIntStateOf(0) }

    // Carousel auto-slide timer (shifts cards every 5s)
    LaunchedEffect(banners.size) {
        if (banners.size > 1) {
            while (true) {
                delay(5000)
                currentIndex = (currentIndex + 1) % banners.size
            }
        }
    }

    val activeBanner = if (currentIndex < banners.size) banners[currentIndex] else banners.firstOrNull() ?: return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(vertical = 4.dp)
            .testTag("banner_carousel"),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background Image
            if (activeBanner.imageUrl.isNotEmpty()) {
                val imagePainter = rememberAsyncImagePainter(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(activeBanner.imageUrl)
                        .crossfade(true)
                        .build()
                )
                Image(
                    painter = imagePainter,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Linear Dark shade overlay for high legibility
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                                startY = 150f
                            )
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF3B82F6),
                                    Color(0xFF8B5CF6),
                                    Color(0xFFEC4899)
                                )
                            )
                        )
                )
            }

            // Foreground layout Content elements
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Announcement Header Tag
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFFF0055).copy(alpha = 0.9f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "OFFICIAL LAKSHYA BULLETIN",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // Dynamic Footer (Title, indicators & Action buttons matched to image mockup)
                Column {
                    Text(
                        text = activeBanner.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (activeBanner.description.isNotEmpty()) {
                        Text(
                            text = activeBanner.description,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Interactive Indicators - dots (double up as quick-tap anchors)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            banners.forEachIndexed { idx, _ ->
                                val isSelected = idx == currentIndex
                                Box(
                                    modifier = Modifier
                                        .size(if (isSelected) 18.dp else 8.dp, 8.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color(0xFF6366F1) else Color.White.copy(alpha = 0.5f))
                                        .clickable { currentIndex = idx }
                                )
                            }
                        }

                        // VIEW/ACTION interactive Button
                        Button(
                            onClick = {
                                val link = activeBanner.linkUrl
                                if (link.startsWith("http")) {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Cannot open: $link", Toast.LENGTH_SHORT).show()
                                    }
                                } else if (link == "COURSES") {
                                    onTabSelect("COURSES")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = activeBanner.buttonText.uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardBrandHeader(studentName: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Lakshya Academy",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Namaste $studentName! 👋",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Target Gazipur central online batches",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.lakshya_logo),
                    contentDescription = "Lakshya Logo",
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
fun LiveClassPromoCard(
    title: String,
    teacher: String,
    onJoinLive: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFFDC2626), Color(0xFFEF4444))))
            .clickable { onJoinLive() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("LIVE", color = Color.Red, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("By $teacher Sir • Interactive live class", color = Color.White, fontSize = 11.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
    }
}

@Composable
fun DashboardSearchSection(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedCategory: String,
    onCategorySelect: (String) -> Unit
) {
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Search competitive batches") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("dashboard_search"),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val categories = listOf("All", "Class 6", "Class 7", "Class 8", "Class 9", "Class 10", "Class 11", "Class 12", "UPSC", "UP Police", "SSC", "NEET")
            items(categories) { cat ->
                val active = selectedCategory == cat
                FilterChip(
                    selected = active,
                    onClick = { onCategorySelect(cat) },
                    label = { Text(cat) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("category_chip_$cat")
                )
            }
        }
    }
}

@Composable
fun QuickDrawerShortcuts(onDocSelect: (String) -> Unit) {
    val items = listOf(
        Triple("Book", "PDF Books", Icons.Default.LibraryBooks),
        Triple("Syllabus", "Syllabuses", Icons.Default.Description),
        Triple("Timetable", "Timetable", Icons.Default.CalendarMonth),
        Triple("Previous Year Paper", "Exams PYPs", Icons.Default.Verified),
        Triple("Current Affairs", "GK Capsule", Icons.Default.Newspaper)
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(items) { (type, label, icon) ->
            Card(
                modifier = Modifier
                    .width(110.dp)
                    .height(90.dp)
                    .clickable { onDocSelect(type) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(icon, contentDescription = label, tint = BrandBluePrimary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun CourseGridCard(
    course: CourseEntity,
    onStudyClick: () -> Unit,
    onDetailClick: () -> Unit
) {
    var isFavorite by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDetailClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Category, Title and heart icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF6366F1).copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = course.category,
                                color = Color(0xFF4F46E5),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (!course.isFree) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFF59E0B).copy(alpha = 0.12f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "PREMIUM",
                                    color = Color(0xFFD97706),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = course.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = { isFavorite = !isFavorite },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Color.Red else Color.Gray,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Body: Card containing custom high-fidelity banner & features list
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(if (course.imageUrl.isNotEmpty()) course.imageUrl else "https://picsum.photos/seed/ndashaurya/600/350")
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // Tint Overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                                )
                            )
                    )

                    // Overlay Features & Price Badge
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Features row
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("🎥 Live Class", "📝 Notes PDF", "🎓 Expert Faculty").forEach { feat ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.Black.copy(alpha = 0.5f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(feat, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Bottom row: Course statistics & price tag
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                text = "📅 Validity: 1 Year • ${course.totalLessons} Lectures",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (course.isFree) Color(0xFF10B981) else Color(0xFF6366F1))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (course.isFree) "FREE" else "₹${course.price.toInt()}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Study Button exactly matching Screen 1 image style
            Button(
                onClick = onStudyClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("study_course_${course.id}"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Let's Study (चलो पढ़ाई करें)",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// === Materials Document list viewer Dialog ===
@Composable
fun MaterialDocumentViewerDialog(
    type: String,
    viewModel: AcademyViewModel,
    onDismiss: () -> Unit
) {
    val documentsFlow = when (type) {
        "Book" -> viewModel.booksList
        "Syllabus" -> viewModel.syllabusList
        "Timetable" -> viewModel.timetableList
        "Previous Year Paper" -> viewModel.pypList
        else -> viewModel.currentAffairsList
    }
    val docs by documentsFlow.collectAsStateWithLifecycle()
    var selectedDocForReading by remember { mutableStateOf<MaterialEntity?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Lakshya Library: $type",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandBluePrimary
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (docs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No items loaded. Admin can publish documents here.", color = Color.Gray, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(docs) { doc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, LightGridLines, RoundedCornerShape(8.dp))
                                    .clickable { selectedDocForReading = doc }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.Red)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(doc.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    if (doc.description.isNotBlank()) {
                                        Text(doc.description, fontSize = 11.sp, color = Color.Gray)
                                    }
                                    Text("Size: ${doc.fileSize}", fontSize = 10.sp, color = Color.Gray)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.LightGray.copy(alpha = 0.3f))
                                        .clickable {
                                            selectedDocForReading = doc
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = "read", tint = BrandBluePrimary, modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Close library")
                    }
                }
            }
        }
    }

    if (selectedDocForReading != null) {
        MockPdfViewerScreen(
            pdfName = selectedDocForReading!!.title,
            customContent = selectedDocForReading!!.fileContent,
            fileSize = selectedDocForReading!!.fileSize,
            onDismiss = { selectedDocForReading = null }
        )
    }
}

// === Course Detail Dialog / Simulated Razorpay Payment ===
@Composable
fun CourseDetailEnrollmentDialog(
    course: CourseEntity,
    viewModel: AcademyViewModel,
    onDismiss: () -> Unit,
    onEnrollSuccess: () -> Unit
) {
    val enrollments by viewModel.allEnrollments.collectAsStateWithLifecycle()
    val userEmail = viewModel.currentUser?.email ?: ""
    val isEnrolled = enrollments.any { it.courseId == course.id && it.userEmail == userEmail }
    var showRazorpaySimulator by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Header details
                Text(course.category, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandBluePrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(course.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                Text("Course Description", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Spacer(modifier = Modifier.height(4.dp))
                Text(course.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))

                // Stats row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.LightGray.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Lessons", fontSize = 10.sp, color = Color.Gray)
                        Text("${course.totalLessons} lectures", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Duration", fontSize = 10.sp, color = Color.Gray)
                        Text("30 Days", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Tutor", fontSize = 10.sp, color = Color.Gray)
                        Text("Chief Faculty", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (course.isFree) {
                        Text("Price: FREE", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFF22C55E))
                    } else {
                        Text("Price: ₹${course.price.toInt()}", fontWeight = FontWeight.Black, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                    }

                    if (isEnrolled) {
                        Button(
                            onClick = onEnrollSuccess,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Open Course Lectures")
                        }
                    } else {
                        Button(
                            onClick = {
                                if (course.isFree) {
                                    viewModel.enrollInCourse(course.id)
                                    onEnrollSuccess()
                                } else {
                                    showRazorpaySimulator = true
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("enroll_button")
                        ) {
                            Text(if (course.isFree) "Enroll Free" else "Unlock via Razorpay")
                        }
                    }
                }
            }
        }
    }

    if (showRazorpaySimulator) {
        MockRazorpayCheckoutDialog(
            courseTitle = course.title,
            coursePrice = course.price,
            onPaymentResult = { success ->
                showRazorpaySimulator = false
                if (success) {
                    viewModel.enrollInCourse(course.id)
                    onEnrollSuccess()
                }
            },
            onDismiss = { showRazorpaySimulator = false }
        )
    }
}

// === INTERACTIVE RAZORPAY SIMULATOR DIALOG ===
@Composable
fun MockRazorpayCheckoutDialog(
    courseTitle: String,
    coursePrice: Double,
    onPaymentResult: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var phoneInput by remember { mutableStateOf("") }
    var upiInput by remember { mutableStateOf("") }
    var showProcessing by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1938)) // Razorpay Dark Blue Theme
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // Razorpay branding
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Icon(Icons.Default.Payment, contentDescription = null, tint = Color(0xFF3B82F6))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Razorpay Secure", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = Color.White.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(12.dp))

                Text("Lakshya Academy Ghazipur Gateway", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(courseTitle, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .padding(vertical = 10.dp, horizontal = 20.dp)
                ) {
                    Text("Total Payable: ₹${coursePrice.toInt()}", color = Color(0xFF10B981), fontWeight = FontWeight.Black, fontSize = 18.sp)
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (showProcessing) {
                    CircularProgressIndicator(color = Color(0xFF3B82F6))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Authorizing sandbox payment...", color = Color.White)
                    LaunchedEffect(Unit) {
                        delay(2000)
                        onPaymentResult(true)
                    }
                } else {
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        label = { Text("Billing Phone", color = Color.LightGray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = upiInput,
                        onValueChange = { upiInput = it },
                        label = { Text("UPI ID (e.g. upi@sbi)", color = Color.LightGray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reject", color = Color.Red)
                        }
                        Button(
                            onClick = { showProcessing = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .weight(1.5f)
                                .testTag("razorpay_success_btn")
                        ) {
                            Text("Pay via Sandbox", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// === Student My Courses tab ===
@Composable
fun StudentMyCourses(
    viewModel: AcademyViewModel,
    onPlayCourse: (CourseEntity) -> Unit
) {
    val enrollments by viewModel.allEnrollments.collectAsStateWithLifecycle()
    val courses by viewModel.allCourses.collectAsStateWithLifecycle()
    val userEmail = viewModel.currentUser?.email ?: ""

    val myEnrollments = enrollments.filter { it.userEmail == userEmail }
    val myEnrolledCourses = courses.filter { course ->
        myEnrollments.any { it.courseId == course.id }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(BrandBluePrimary, BrandBlueSecondary)))
                .padding(24.dp)
        ) {
            Column {
                Text("My Learning Center", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Access your purchased online classes and books", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            }
        }

        if (myEnrolledCourses.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No active courses enrolled yet.", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Browse the Home Tab, find a batch, and sign up.", color = Color.Gray, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(16.dp)) {
                items(myEnrolledCourses) { course ->
                    val enrollment = myEnrollments.find { it.courseId == course.id }
                    val completed = enrollment?.completedLessonsCount ?: 0
                    val total = course.totalLessons
                    val progressFloat = if (total > 0) completed.toFloat() / total else 0f

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayCourse(course) }
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(course.category, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BrandBluePrimary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(course.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Lectures: $completed/$total watched", fontSize = 11.sp, color = Color.Gray)
                                Text("${(progressFloat * 100).toInt()}% progress", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = BrandBlueSecondary)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { progressFloat },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = BrandBluePrimary,
                                trackColor = Color.LightGray.copy(alpha = 0.3f)
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { onPlayCourse(course) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.align(Alignment.End),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Let's Study (पढ़ाई चालू करें)")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// === Lessons media play centers ===
sealed class ClassroomState {
    object SubjectList : ClassroomState()
    data class TopicList(val subjectName: String) : ClassroomState()
    data class TopicFolderList(val subjectName: String, val topicName: String) : ClassroomState()
    data class VideoLectureList(val subjectName: String, val topicName: String, val folderName: String) : ClassroomState()
    data class LectureDetail(val lesson: LessonEntity) : ClassroomState()
    data class VideoPlayerFullScreen(val lesson: LessonEntity, val quality: String) : ClassroomState()
}

@Composable
fun StudentLessonsMediaWorkspace(
    course: CourseEntity,
    completedCount: Int,
    viewModel: AcademyViewModel,
    onDismiss: () -> Unit
) {
    val lessons by viewModel.currentLessonList.collectAsStateWithLifecycle()
    var navStack by remember { mutableStateOf(listOf<ClassroomState>(ClassroomState.SubjectList)) }
    
    var activePdfReadingLesson by remember { mutableStateOf<LessonEntity?>(null) }
    var showQualityDialogForLesson by remember { mutableStateOf<LessonEntity?>(null) }

    val currentScreen = navStack.lastOrNull() ?: ClassroomState.SubjectList

    LaunchedEffect(course.id) {
        viewModel.selectCourse(course)
    }

    // Custom helper to parse custom chapter format "Subject|Topic"
    fun getSubjectName(chapterName: String): String {
        return chapterName.substringBefore("|").trim()
    }

    fun getTopicName(chapterName: String): String {
        return chapterName.substringAfter("|", "Worksheet").trim()
    }

    // Dialog full-screen container
    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)) // Light gray aesthetic background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                
                // HEADER BAR (Shows dynamic titles and back actions based on current nested node)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (navStack.size > 1) {
                                navStack = navStack.dropLast(1)
                            } else {
                                onDismiss()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.Black
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = when (currentScreen) {
                            is ClassroomState.SubjectList -> course.title
                            is ClassroomState.TopicList -> currentScreen.subjectName
                            is ClassroomState.TopicFolderList -> currentScreen.topicName
                            is ClassroomState.VideoLectureList -> currentScreen.folderName
                            is ClassroomState.LectureDetail -> currentScreen.lesson.title
                            is ClassroomState.VideoPlayerFullScreen -> currentScreen.lesson.title
                        },
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Divider(color = Color.LightGray.copy(alpha = 0.5f))

                // RENDERING CORRESPONDING HIGH-FIDELITY SCREEN
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (lessons.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color(0xFF6366F1))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Loading syllabus lectures...", color = Color.Gray, fontSize = 13.sp)
                            }
                        }
                    } else {
                        when (currentScreen) {
                            // SCREEN 2: Subject List
                            is ClassroomState.SubjectList -> {
                                val subjectsList = remember(lessons) {
                                    lessons.map { getSubjectName(it.chapterName) }.distinct()
                                }
                                
                                Column(modifier = Modifier.fillMaxSize()) {
                                    // Custom visual subnavigation bar tabs from Image 2
                                    var activeSubTab by remember { mutableStateOf("recorded") } // live, recorded, test
                                    TabRow(
                                        selectedTabIndex = when (activeSubTab) {
                                            "recorded" -> 1
                                            "test" -> 2
                                            else -> 0
                                        },
                                        containerColor = Color.White,
                                        contentColor = Color(0xFF6366F1),
                                        indicator = { tabPositions ->
                                            TabRowDefaults.SecondaryIndicator(
                                                modifier = Modifier.tabIndicatorOffset(tabPositions[if (activeSubTab=="recorded") 1 else if (activeSubTab=="test") 2 else 0]),
                                                color = Color(0xFF6366F1)
                                            )
                                        }
                                    ) {
                                        Tab(
                                            selected = activeSubTab == "live",
                                            onClick = { activeSubTab = "live" },
                                            text = { Text("Live & Upcoming", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                                        )
                                        Tab(
                                            selected = activeSubTab == "recorded",
                                            onClick = { activeSubTab = "recorded" },
                                            text = { Text("Recorded", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                                        )
                                        Tab(
                                            selected = activeSubTab == "test",
                                            onClick = { activeSubTab = "test" },
                                            text = { Text("Tests Quiz", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(14.dp))
                                    
                                    if (activeSubTab == "recorded") {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            items(subjectsList) { subject ->
                                                // High-fidelity Folder card matching Image 2
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(72.dp)
                                                        .clickable {
                                                            navStack = navStack + ClassroomState.TopicList(subject)
                                                        },
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxSize(),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        // Thick Indigo indicator bar at left edge
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxHeight()
                                                                .width(6.dp)
                                                                .background(Color(0xFF6366F1))
                                                        )
                                                        
                                                        Spacer(modifier = Modifier.width(16.dp))
                                                        
                                                        // Folder Icon & Title
                                                        Icon(
                                                            imageVector = Icons.Default.Folder,
                                                            contentDescription = null,
                                                            tint = Color(0xFF818CF8),
                                                            modifier = Modifier.size(26.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                        Text(
                                                            text = subject,
                                                            fontSize = 15.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.DarkGray
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        // Sandbox placeholder for other tabs
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(
                                                    imageVector = Icons.Default.School,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(48.dp),
                                                    tint = Color.LightGray
                                                )
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Text(
                                                    text = "No live lessons scheduled right now.",
                                                    color = Color.Gray,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // SCREEN 3: Topic List (e.g. Worksheet, The Point, Straight Line)
                            is ClassroomState.TopicList -> {
                                val subjectLessons = remember(lessons, currentScreen.subjectName) {
                                    lessons.filter { getSubjectName(it.chapterName) == currentScreen.subjectName }
                                }
                                val topicsList = remember(subjectLessons) {
                                    subjectLessons.map { getTopicName(it.chapterName) }.distinct()
                                }
                                
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize().padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(topicsList) { topic ->
                                        // Thick vertical indicator purple strip on left (Image 3)
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(72.dp)
                                                .clickable {
                                                    navStack = navStack + ClassroomState.TopicFolderList(currentScreen.subjectName, topic)
                                                },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Thick Indigo indicator bar
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .width(6.dp)
                                                        .background(Color(0xFF6366F1))
                                                )
                                                Spacer(modifier = Modifier.width(16.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Folder,
                                                    contentDescription = null,
                                                    tint = Color(0xFFF59E0B),
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = topic,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.DarkGray
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // SCREEN 4: Topic Folder List (Shows "All video" folder)
                            is ClassroomState.TopicFolderList -> {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // High-fidelity card that says "All video" as in Image 4
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(72.dp)
                                            .clickable {
                                                navStack = navStack + ClassroomState.VideoLectureList(
                                                    currentScreen.subjectName,
                                                    currentScreen.topicName,
                                                    "All video"
                                                )
                                            },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .width(6.dp)
                                                    .background(Color(0xFF6366F1))
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = Color(0xFF10B981),
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = "All video",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color.DarkGray
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // SCREEN 5: Video Lectures list (Video card, name, PDF 01/PDF 02 buttons and red border box)
                            is ClassroomState.VideoLectureList -> {
                                val matchLessons = remember(lessons, currentScreen.subjectName, currentScreen.topicName) {
                                    lessons.filter {
                                        getSubjectName(it.chapterName) == currentScreen.subjectName &&
                                                getTopicName(it.chapterName) == currentScreen.topicName
                                    }
                                }
                                
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize().padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(matchLessons) { lesson ->
                                        // Custom high fidelity Lecture Card from Image 5
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .wrapContentHeight()
                                                .clickable {
                                                    navStack = navStack + ClassroomState.LectureDetail(lesson)
                                                },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Left side rectangular thumbnail
                                                Card(
                                                    modifier = Modifier
                                                        .size(width = 100.dp, height = 66.dp)
                                                        .clip(RoundedCornerShape(8.dp)),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (lesson.thumbnailUrl.isNotEmpty()) {
                                                            AsyncImage(
                                                                model = ImageRequest.Builder(LocalContext.current)
                                                                    .data(lesson.thumbnailUrl)
                                                                    .crossfade(true)
                                                                    .build(),
                                                                contentDescription = null,
                                                                modifier = Modifier.fillMaxSize(),
                                                                contentScale = ContentScale.Crop
                                                            )
                                                        } else {
                                                            // Beautiful solid purple placeholder matching Screen 5
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxSize()
                                                                    .background(Color(0xFF6366F1)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.School,
                                                                    contentDescription = null,
                                                                    tint = Color.White,
                                                                    modifier = Modifier.size(24.dp)
                                                                )
                                                            }
                                                        }
                                                        
                                                        // Play Icon overlap on thumbnail
                                                        Box(
                                                            modifier = Modifier
                                                                .size(24.dp)
                                                                .clip(CircleShape)
                                                                .background(Color.Black.copy(alpha = 0.6f)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.PlayArrow,
                                                                contentDescription = null,
                                                                tint = Color.White,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                
                                                Spacer(modifier = Modifier.width(12.dp))
                                                
                                                // Right side content
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = lesson.title,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.Black,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    
                                                    // Blue PDF attachment badges (clickable to read immediately!)
                                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(Color(0xFF6366F1))
                                                                .clickable { activePdfReadingLesson = lesson }
                                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                        ) {
                                                            Text("View PDF 01", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(Color(0xFF3F83F8))
                                                                .clickable { activePdfReadingLesson = lesson }
                                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                        ) {
                                                            Text("View PDF 02", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                    
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    
                                                    // Highlighted red clock box at bottom (Image 5)
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .border(BorderStroke(1.dp, Color.Red), RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(
                                                                imageVector = Icons.Default.Timer,
                                                                contentDescription = null,
                                                                tint = Color.Red,
                                                                modifier = Modifier.size(10.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = "15-06-2026 at 09:00 am",
                                                                fontSize = 9.sp,
                                                                color = Color.Red,
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
                            
                            // SCREEN 6: Lecture details page (Play overlay, PDFs, Watch Now big purple button)
                            is ClassroomState.LectureDetail -> {
                                val lesson = currentScreen.lesson
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp)
                                    ) {
                                        // Video player preview mockup container
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(180.dp),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize()) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(LocalContext.current)
                                                        .data(if (lesson.thumbnailUrl.isNotEmpty()) lesson.thumbnailUrl else "https://picsum.photos/seed/ndashaurya/600/350")
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                                // Black overlay with custom watch info row inside
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(Color.Black.copy(alpha = 0.4f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier
                                                            .size(56.dp)
                                                            .clickable { showQualityDialogForLesson = lesson }
                                                    )
                                                }
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.height(14.dp))
                                        
                                        // Purple layout action detail frame (Image 6)
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2F6))
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    text = lesson.title,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.Black
                                                )
                                                
                                                Spacer(modifier = Modifier.height(10.dp))
                                                
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = Icons.Default.Timer,
                                                            contentDescription = null,
                                                            tint = Color.Gray,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("15 June 2026", fontSize = 11.sp, color = Color.Gray)
                                                    }
                                                    
                                                    // Telegram channel capsule badge
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .background(Color(0xFF38BDF8))
                                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(
                                                                imageVector = Icons.Default.School,
                                                                contentDescription = null,
                                                                tint = Color.White,
                                                                modifier = Modifier.size(10.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text("@ASMultiverseAppZ", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.height(16.dp))
                                        
                                        Text(
                                            text = "Subject Study Materials & Class Notes",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        // First Class PDF note document card
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(60.dp)
                                                .clickable { activePdfReadingLesson = lesson },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    // Purple PDF icon circle container
                                                    Box(
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFFFFE4E6)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.PictureAsPdf,
                                                            contentDescription = null,
                                                            tint = Color.Red,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column {
                                                        Text("Maths Lecture PDF Document - I", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                        Text("Size: ${lesson.fileSize}", fontSize = 10.sp, color = Color.Gray)
                                                    }
                                                }
                                                Icon(
                                                    imageVector = Icons.Default.FileDownload,
                                                    contentDescription = null,
                                                    tint = Color.Gray,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        // Second Class PDF note card
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(60.dp)
                                                .clickable { activePdfReadingLesson = lesson },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFFFFE4E6)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.PictureAsPdf,
                                                            contentDescription = null,
                                                            tint = Color.Red,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column {
                                                        Text("Study Workbook Practice - II", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                        Text("Size: 1.6 MB", fontSize = 10.sp, color = Color.Gray)
                                                    }
                                                }
                                                Icon(
                                                    imageVector = Icons.Default.FileDownload,
                                                    contentDescription = null,
                                                    tint = Color.Gray,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                    
                                    // Watch Now solid big purple button (Image 6)
                                    Button(
                                        onClick = { showQualityDialogForLesson = lesson },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.BottomCenter)
                                            .padding(16.dp)
                                            .height(50.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Watch Now (अभी क्लास देखें)", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                            
                            // SCREEN 8: Full Screen Simulated Video Player (Controls, rewind, forward, seeker)
                            is ClassroomState.VideoPlayerFullScreen -> {
                                val lesson = currentScreen.lesson
                                val quality = currentScreen.quality
                                
                                var isPlayingVideo by remember { mutableStateOf(true) }
                                var currentSeekFloat by remember { mutableStateOf(0.42f) }
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black)
                                ) {
                                    // Custom Landscape video playback view simulation (Image 8)
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(16.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        // Top row overlay: Back left, Title center, stats right
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = {
                                                        navStack = navStack.dropLast(1)
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowBack,
                                                        contentDescription = "Back",
                                                        tint = Color.White
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = lesson.title,
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.width(180.dp)
                                                )
                                            }
                                            
                                            // Stats badge
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color.Black.copy(alpha = 0.5f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text("Quality: $quality", color = Color.White, fontSize = 8.sp)
                                            }
                                        }
                                        
                                        // Center Row: Control play buttons (Rewind 10, Play/Pause, Forward 10)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Rewind 10s button
                                            IconButton(
                                                onClick = { if (currentSeekFloat > 0.1f) currentSeekFloat -= 0.1f },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(
                                                        imageVector = Icons.Default.RotateRight,
                                                        contentDescription = "Rewind",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                    Text("10s", color = Color.White, fontSize = 8.sp)
                                                }
                                            }
                                            
                                            Spacer(modifier = Modifier.width(36.dp))
                                            
                                            // Main play/pause button
                                            IconButton(
                                                onClick = { isPlayingVideo = !isPlayingVideo },
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White.copy(alpha = 0.25f))
                                            ) {
                                                Icon(
                                                    imageVector = if (isPlayingVideo) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                                    contentDescription = "Play/Pause",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(36.dp)
                                                )
                                            }
                                            
                                            Spacer(modifier = Modifier.width(36.dp))
                                            
                                            // Fast Forward 10s button
                                            IconButton(
                                                onClick = { if (currentSeekFloat < 0.9f) currentSeekFloat += 0.1f },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = "Forward",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                    Text("10s", color = Color.White, fontSize = 8.sp)
                                                }
                                            }
                                        }
                                        
                                        // Bottom Row: Seeker, timers, settings, full screen
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Slider(
                                                value = currentSeekFloat,
                                                onValueChange = { currentSeekFloat = it },
                                                colors = SliderDefaults.colors(
                                                    thumbColor = Color(0xFF6366F1),
                                                    activeTrackColor = Color(0xFF6366F1),
                                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                                ),
                                                modifier = Modifier.fillMaxWidth().height(16.dp)
                                            )
                                            
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("14:32 / 34:10", color = Color.White, fontSize = 9.sp)
                                                
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Icon(
                                                        imageVector = Icons.Default.Settings,
                                                        contentDescription = "Settings",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Icon(
                                                        imageVector = Icons.Default.Fullscreen,
                                                        contentDescription = "Fullscreen",
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
            }
        }
    }

    // SCREEN 7: Choose Quality customized Dialog Sheet (from Image 7)
    if (showQualityDialogForLesson != null) {
        val lesson = showQualityDialogForLesson!!
        Dialog(onDismissRequest = { showQualityDialogForLesson = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Choose quality",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Select video quality to watch online ( by MadXABhi )",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(18.dp))
                    
                    // Quality option lists (white container style with thin purple outlines)
                    listOf("720p", "480p", "360p", "240p", "144p").forEach { quality ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clickable {
                                    showQualityDialogForLesson = null
                                    // Open high-fidelity player screen with selected quality!
                                    navStack = navStack + ClassroomState.VideoPlayerFullScreen(lesson, quality)
                                }
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFF818CF8).copy(alpha = 0.5f))
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(text = quality, color = Color(0xFF6366F1), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Purple CANCEL button exactly like Image 7
                    Button(
                        onClick = { showQualityDialogForLesson = null },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancel", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    if (activePdfReadingLesson != null) {
        MockPdfViewerScreen(
            pdfName = activePdfReadingLesson!!.pdfName,
            customContent = activePdfReadingLesson!!.pdfContent,
            fileSize = activePdfReadingLesson!!.fileSize,
            onDismiss = { activePdfReadingLesson = null }
        )
    }
}

// === HIGH-FIDELITY VIDEO PLAYER SCREEN OVERLAY ===
@Composable
fun MockVideoPlayerScreen(
    title: String,
    videoUrl: String,
    onDismiss: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(true) }
    var speed by remember { mutableStateOf(1f) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            colors = CardDefaults.cardColors(containerColor = Color.Black)
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // Video Screen Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    var hasError by remember { mutableStateOf(false) }
                    
                    if (hasError) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = Color.Red,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("वीडियो लोड करने में त्रुटि या फ़ाइल नहीं मिली", color = Color.White, fontSize = 12.sp)
                            Text("Video Load Error / File Not Found", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                        }
                    } else {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    try {
                                        val controller = MediaController(ctx)
                                        controller.setAnchorView(this)
                                        setMediaController(controller)
                                        setVideoURI(Uri.parse(videoUrl))
                                        setOnPreparedListener { mp ->
                                            mp.start()
                                            isPlaying = true
                                        }
                                        setOnErrorListener { _, _, _ ->
                                            hasError = true
                                            true
                                        }
                                    } catch (e: Exception) {
                                        hasError = true
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            update = { view ->
                                try {
                                    if (isPlaying) {
                                        if (!view.isPlaying) view.start()
                                    } else {
                                        if (view.isPlaying) view.pause()
                                    }
                                } catch (e: Exception) {
                                    // Handle gracefully
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(modifier = Modifier.height(16.dp))

                // Media Controls Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { speed = if (speed == 1f) 1.5f else if (speed == 1.5f) 2.0f else 1f }) {
                        Text("${speed}x", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    IconButton(onClick = { isPlaying = !isPlaying }) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }
    }
}

// === SIMULATED PDF VIEWER SCREEN OVERLAY ===
@Composable
fun MockPdfViewerScreen(
    pdfName: String,
    customContent: String = "",
    fileSize: String = "",
    onDismiss: () -> Unit
) {
    val pages = remember(customContent) {
        if (customContent.isBlank()) {
            listOf(
                "Section 1: Core Syllabus Definitions\n\n1. Historical constitutional developments of Indian Administration.\n2. Comparative exam questions patterns on regional Gazipur history.\n\nSection 2: Fundamental Principles\n\nEvery citizen is guaranteed protection. No person shall be deprived of their life or personal liberty except according to procedure established by law.",
                "Section 3: Solved Mock Analysis\n\nQ1. Basic structure Kesavananda Bharati verdict was passed by largest bench (13 judges ratio 7:6).\n\nQ2. Ghazipur opium factory is historically significant which was set up under British rule in 1820."
            )
        } else {
            val list = mutableListOf<String>()
            var remaining = customContent
            while (remaining.isNotEmpty()) {
                if (remaining.length <= 550) {
                    list.add(remaining)
                    break
                } else {
                    var splitIndex = remaining.lastIndexOf(' ', 550)
                    if (splitIndex < 400) {
                        splitIndex = 500
                    }
                    if (splitIndex >= remaining.length) {
                        splitIndex = remaining.length - 1
                    }
                    list.add(remaining.substring(0, splitIndex))
                    remaining = remaining.substring(splitIndex).trim()
                }
            }
            list.ifEmpty { listOf(customContent) }
        }
    }
    
    var currentPage by remember { mutableIntStateOf(1) }
    val totalPages = pages.size

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.Red)
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(pdfName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black, maxLines = 1)
                        if (fileSize.isNotBlank()) {
                            Text("Dedicated Size: $fileSize (Ready Offline)", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.Black)
                    }
                }

                Divider(color = Color.LightGray.copy(alpha = 0.5f))

                // Realistic Study Sheet Layout
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                        .background(Color(0xFFFCFDF2)) // Nice paperback color
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text("LAKSHYA LIVE LEARNING APP: $pdfName", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        val activeContent = pages.getOrNull(currentPage - 1) ?: "No content on this page."
                        Text(
                            text = activeContent,
                            fontSize = 13.sp,
                            color = Color.Black,
                            lineHeight = 18.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { if (currentPage > 1) currentPage-- },
                        enabled = currentPage > 1,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Prev Page", fontSize = 12.sp)
                    }
                    Text("Page $currentPage of $totalPages", color = Color.Black, fontSize = 13.sp)
                    Button(
                        onClick = { if (currentPage < totalPages) currentPage++ },
                        enabled = currentPage < totalPages,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Next Page", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// === STUDENT TEST SERIES / MOCK TEST SCREEN ===
@Composable
fun StudentTestHub(viewModel: AcademyViewModel) {
    val tests by viewModel.allTests.collectAsStateWithLifecycle()
    val scores by viewModel.allScores.collectAsStateWithLifecycle()
    val currentEmail = viewModel.currentUser?.email ?: ""
    val userScores = scores.filter { it.userEmail == currentEmail }

    var selectedTab by remember { mutableStateOf("LIVE_TEST") } // LIVE_TEST, SCORE_CARD
    val examCategories = listOf("All", "Class 6", "Class 7", "Class 8", "Class 9", "Class 10", "Class 11", "Class 12", "Sarkari Exams")
    var selectedExamCategory by remember { mutableStateOf("All") }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(BrandBluePrimary, BrandBlueSecondary)))
                .padding(24.dp)
        ) {
            Column {
                Text("Lakshya Test Hub", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Practice online mock exams and examine results", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            }
        }

        TabRow(
            selectedTabIndex = if (selectedTab == "LIVE_TEST") 0 else 1,
            containerColor = Color.White
        ) {
            Tab(selected = selectedTab == "LIVE_TEST", onClick = { selectedTab = "LIVE_TEST" }) {
                Text("Active Exams", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
            }
            Tab(selected = selectedTab == "SCORE_CARD", onClick = { selectedTab = "SCORE_CARD" }) {
                Text("Progress Report Card", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
            }
        }

        if (selectedTab == "LIVE_TEST") {
            // Horizontal Class/Exam Category Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(examCategories) { category ->
                    val isSelected = selectedExamCategory == category
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) BrandBluePrimary else Color(0xFFF1F5F9))
                            .border(1.dp, if (isSelected) BrandBluePrimary else Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .clickable { selectedExamCategory = category }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = category,
                            color = if (isSelected) Color.White else Color(0xFF334155),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            val filteredTests = remember(tests, selectedExamCategory) {
                tests.filter { test ->
                    when (selectedExamCategory) {
                        "All" -> true
                        "Sarkari Exams" -> {
                            !test.title.contains("Class 6", ignoreCase = true) &&
                            !test.title.contains("Class 7", ignoreCase = true) &&
                            !test.title.contains("Class 8", ignoreCase = true) &&
                            !test.title.contains("Class 9", ignoreCase = true) &&
                            !test.title.contains("Class 10", ignoreCase = true) &&
                            !test.title.contains("Class 11", ignoreCase = true) &&
                            !test.title.contains("Class 12", ignoreCase = true) &&
                            !test.title.contains("कक्षा ", ignoreCase = true)
                        }
                        else -> {
                            val num = selectedExamCategory.replace("Class ", "")
                            test.title.contains(selectedExamCategory, ignoreCase = true) || 
                            test.title.contains("Class $num", ignoreCase = true) ||
                            test.title.contains("कक्षा $num", ignoreCase = true)
                        }
                    }
                }
            }

            if (filteredTests.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No tests live in this category.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    items(filteredTests) { test ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(BrandGold.copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(test.type, color = Color(0xFFD97706), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    if (test.hasNegativeMarking) {
                                        Text("Negative Marks Enabled", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(test.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Duration: ${test.durationMinutes} mins", fontSize = 12.sp, color = Color.Gray)
                                    Text("Marks: +${test.marksPerCorrect} / ${test.marksPerWrong}", fontSize = 12.sp, color = Color.Gray)
                                    
                                    val isBilingual = test.title.contains("Bilingual", ignoreCase = true) || 
                                                     test.title.contains("Mock Test", ignoreCase = true) || 
                                                     test.title.contains("Class", ignoreCase = true)
                                    
                                    val qCountStr = if (test.title.contains("Class 10")) "50 Qs (Hindi & English)" else "Bilingual Qs"
                                    
                                    if (isBilingual) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFFECFDF5))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(qCountStr, color = Color(0xFF059669), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = { viewModel.startTest(test) },
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .testTag("start_test_${test.id}"),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Attempt Test Now")
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // SCORE CARD REPORT VIEW
            if (userScores.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.HistoryEdu,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color.LightGray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No test stats saved. Take your first mocked paper!", color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.padding(16.dp)) {
                    items(userScores) { score ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(score.testTitle, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(SimpleDateFormat("dd MMMM yyyy, hh:mm a", Locale.getDefault()).format(Date(score.timestamp)), fontSize = 11.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text("Total Questions", fontSize = 10.sp, color = Color.Gray)
                                        Text("${score.totalQuestions}", fontWeight = FontWeight.Bold)
                                    }
                                    Column {
                                        Text("Correct Answers", fontSize = 10.sp, color = Color.Gray)
                                        Text("${score.correctAnswers}", fontWeight = FontWeight.Bold, color = Color(0xFF22C55E))
                                    }
                                    Column {
                                        Text("Wrong Answers", fontSize = 10.sp, color = Color.Gray)
                                        Text("${score.wrongAnswers}", fontWeight = FontWeight.Bold, color = Color.Red)
                                    }
                                    Column {
                                        Text("Your Score", fontSize = 10.sp, color = Color.Gray)
                                        Text("${score.score}", fontWeight = FontWeight.Black, color = BrandBluePrimary, fontSize = 15.sp)
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

// === INTERACTIVE TEST SOLVER SCREEN ===
@Composable
fun StudentActiveTestScreen(viewModel: AcademyViewModel) {
    val progress = viewModel.activeTestProgress ?: return
    val quest = progress.questions
    val index = progress.currentQuestionIndex

    if (quest.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Assembling exam questions...", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { viewModel.exitTest() }) {
                    Text("Exit Solver")
                }
            }
        }
        return
    }

    val activeQuestion = quest[index]
    val selectedOptionIndex = progress.selectedAnswers[activeQuestion.id]

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BrandBluePrimary)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.exitTest() }) {
                        Icon(Icons.Default.Close, contentDescription = "Exit", tint = Color.White)
                    }
                    Text(
                        text = progress.test.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    // Timer Badge
                    val minutes = progress.secondsRemaining / 60
                    val seconds = progress.secondsRemaining % 60
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = String.format("%02d:%02d", minutes, seconds),
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                // Progress count
                Text("Question ${index + 1} of ${quest.size}", fontWeight = FontWeight.Bold, color = BrandBluePrimary)
                Spacer(modifier = Modifier.height(12.dp))

                // Question Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        activeQuestion.questionText,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Options Buttons
                val options = listOf(activeQuestion.optionA, activeQuestion.optionB, activeQuestion.optionC, activeQuestion.optionD)
                options.forEachIndexed { i, option ->
                    val isActive = selectedOptionIndex == i
                    Button(
                        onClick = { progress.selectedAnswers[activeQuestion.id] = i },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("option_${i}"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            contentColor = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(1.dp, Color.LightGray)
                    ) {
                        Text(option, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                    }
                }
            }

            // Footer navigation buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = { if (index > 0) progress.currentQuestionIndex-- },
                    enabled = index > 0,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Previous")
                }

                if (index == quest.size - 1) {
                    Button(
                        onClick = { viewModel.submitActiveTest() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("submit_test_btn")
                    ) {
                        Text("Submit Exam")
                    }
                } else {
                    Button(
                        onClick = { progress.currentQuestionIndex++ },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Next Question")
                    }
                }
            }
        }
    }

    // Show Auto result scorecard dialog after submit
    if (progress.isSubmitted && progress.testScore != null) {
        val score = progress.testScore!!
        Dialog(onDismissRequest = { viewModel.exitTest() }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Exam grading scorecard Result", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981), textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("You Scored:", fontSize = 12.sp, color = Color.Gray)
                    Text("${score.score}", fontSize = 36.sp, fontWeight = FontWeight.Black, color = BrandBluePrimary)

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = Color.LightGray)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total questions attempted:")
                        Text("${score.correctAnswers + score.wrongAnswers} / ${score.totalQuestions}", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Correct:")
                        Text("${score.correctAnswers}", fontWeight = FontWeight.Bold, color = Color(0xFF22C55E))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Wrong (Negative deducted):")
                        Text("${score.wrongAnswers}", fontWeight = FontWeight.Bold, color = Color.Red)
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { viewModel.exitTest() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Got it, Close Dashboard")
                    }
                }
            }
        }
    }
}

// === LIVE STREAM CLASSROOM VIEW ===
@Composable
fun StudentLiveClassRoom(viewModel: AcademyViewModel) {
    val logs = viewModel.liveChatMessageList
    var chatMessage by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔴 Lakshya Live Lecture channel", color = Color.White, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { viewModel.leaveLiveClass() }) {
                        Icon(Icons.Default.Close, contentDescription = "Exit Stream", tint = Color.White)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Simulated active camera lecture feed
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(viewModel.activeLiveStreamTitle, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Live watchers from Ghazipur: ${viewModel.liveViewerCount}", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                }
            }

            // Interactive live chats feed
            Text("Classrooms Live chats", fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
            Divider(color = Color.LightGray.copy(alpha = 0.3f))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                LazyColumn(modifier = Modifier.weight(1f), reverseLayout = false) {
                    items(logs.values.toList()) { (name, msg) ->
                        Row(modifier = Modifier.padding(top = 8.dp)) {
                            Text(name, fontWeight = FontWeight.Bold, color = BrandBluePrimary, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(msg, fontSize = 12.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = chatMessage,
                        onValueChange = { chatMessage = it },
                        placeholder = { Text("Ask teacher doubt live") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("live_chat_input"),
                        shape = RoundedCornerShape(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            viewModel.sendLiveMessage(chatMessage)
                            chatMessage = ""
                        },
                        modifier = Modifier.testTag("live_chat_send")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = BrandBluePrimary)
                    }
                }
            }
        }
    }
}

// === STUDENT DOUBT & CHAT INTERACTIVE BOARD ===
@Composable
fun StudentDoubtForum(viewModel: AcademyViewModel) {
    val doubts by viewModel.allDoubts.collectAsStateWithLifecycle()
    val chats by viewModel.allChatMessages.collectAsStateWithLifecycle()

    var activeSubTab by remember { mutableStateOf("DOUBT") } // DOUBT, CHAT
    var subject by remember { mutableStateOf("") }
    var doubtText by remember { mutableStateOf("") }
    var chatMsg by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(BrandBluePrimary, BrandBlueSecondary)))
                .padding(24.dp)
        ) {
            Column {
                Text("Doubt & Support Center", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Ask doubts to chief faculty or connect with technical help desk", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            }
        }

        TabRow(selectedTabIndex = if (activeSubTab == "DOUBT") 0 else 1) {
            Tab(selected = activeSubTab == "DOUBT", onClick = { activeSubTab = "DOUBT" }) {
                Text("Doubt Forum", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
            }
            Tab(selected = activeSubTab == "CHAT", onClick = { activeSubTab = "CHAT" }) {
                Text("Help Support Chat", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
            }
        }

        if (activeSubTab == "DOUBT") {
            LazyColumn(modifier = Modifier.padding(16.dp)) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Post clear new academic doubt", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = subject,
                                onValueChange = { subject = it },
                                placeholder = { Text("Subject (e.g., History, Math)") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("doubt_subject"),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = doubtText,
                                onValueChange = { doubtText = it },
                                placeholder = { Text("Describe your doubt or question...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("doubt_question"),
                                minLines = 2
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    viewModel.postDoubt(subject, doubtText)
                                    subject = ""
                                    doubtText = ""
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .testTag("post_doubt_btn")
                            ) {
                                Text("Post Question")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Academy Resolved Doubts List", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = BrandBluePrimary)
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (doubts.isEmpty()) {
                    item {
                        Text("No discussions started. Type above to post first query.", color = Color.Gray, fontSize = 12.sp)
                    }
                } else {
                    items(doubts) { doubt ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(doubt.subject, fontWeight = FontWeight.Black, fontSize = 13.sp, color = BrandBluePrimary)
                                    Text("By ${doubt.userName}", fontSize = 11.sp, color = Color.Gray)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Question: ${doubt.questionText}", fontSize = 13.sp)
                                if (doubt.replyText.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp))
                                            .padding(10.dp)
                                    ) {
                                        Column {
                                            Text("Answer Keys • Resolved by ${doubt.answeredBy}:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF10B981))
                                            Text(doubt.replyText, fontSize = 12.sp)
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Pending response from Academy sir...", color = Color.DarkGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // HELP SUPPORT CHAT SCREEN
            Column(modifier = Modifier.padding(16.dp)) {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(chats) { chat ->
                        val fromMe = chat.senderEmail == viewModel.currentUser?.email
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = if (fromMe) Arrangement.End else Arrangement.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 240.dp)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 12.dp,
                                            topEnd = 12.dp,
                                            bottomStart = if (fromMe) 12.dp else 0.dp,
                                            bottomEnd = if (fromMe) 0.dp else 12.dp
                                        )
                                    )
                                    .background(if (fromMe) BrandBluePrimary else Color(0xFFE2E8F0))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text(chat.senderName, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = if (fromMe) Color.White.copy(alpha = 0.8f) else Color.DarkGray)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(chat.text, color = if (fromMe) Color.White else Color.Black, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = chatMsg,
                        onValueChange = { chatMsg = it },
                        placeholder = { Text("Type messaging request...") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("support_input"),
                        shape = RoundedCornerShape(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            viewModel.sendSupportChatMessage(chatMsg)
                            chatMsg = ""
                        },
                        modifier = Modifier.testTag("support_send")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = BrandBluePrimary)
                    }
                }
            }
        }
    }
}

// === STUDENT PROFILE CARD & CLAIM GRADUATION CERTIFICATE ===
@Composable
fun StudentProfileScreen(viewModel: AcademyViewModel) {
    val context = LocalContext.current
    val enrollments by viewModel.allEnrollments.collectAsStateWithLifecycle()
    val courses by viewModel.allCourses.collectAsStateWithLifecycle()
    val notes by viewModel.allNotifications.collectAsStateWithLifecycle()
    val userEmail = viewModel.currentUser?.email ?: ""

    val userEnrollments = enrollments.filter { it.userEmail == userEmail }
    val completedCountCount = userEnrollments.count { it.isCompleted }

    var showCertificateForCourse by remember { mutableStateOf<CourseEntity?>(null) }
    var activeSubSection by remember { mutableStateOf("NOTIF") } // NOTIF, INVOICE

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // High profile colored top card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(BrandBluePrimary, BrandBlueSecondary)))
                .padding(vertical = 32.dp, horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎓", fontSize = 48.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(viewModel.currentUser?.name ?: "Student", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(viewModel.currentUser?.email ?: "", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)

                Spacer(modifier = Modifier.height(16.dp))
                // Quick toggles for in-memory DarkTheme
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier
                            .clickable { viewModel.darkThemeEnabled = !viewModel.darkThemeEnabled }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (viewModel.darkThemeEnabled) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (viewModel.darkThemeEnabled) "Dark Mode Active" else "Change Dark Style", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Column(modifier = Modifier.padding(20.dp)) {
            // Certificate unlocks area
            Text("Claim Course Graduation Certificates", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = BrandBluePrimary)
            Spacer(modifier = Modifier.height(8.dp))

            if (userEnrollments.isEmpty()) {
                Text("Ensure enrollment and complete watch progress to unlock certificate downloads.", fontSize = 12.sp, color = Color.Gray)
            } else {
                userEnrollments.forEach { enroll ->
                    val matchedCourse = courses.find { it.id == enroll.courseId }
                    if (matchedCourse != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, LightGridLines, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(matchedCourse.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                Text(if (enroll.isCompleted) "Status: 100% watch complete" else "Watch Progress: ${enroll.completedLessonsCount} / ${matchedCourse.totalLessons} watched", fontSize = 11.sp, color = Color.Gray)
                            }
                            Button(
                                onClick = { showCertificateForCourse = matchedCourse },
                                enabled = enroll.isCompleted,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Get Seal", fontSize = 10.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sub Navigation for receipts and alerts
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { activeSubSection = "NOTIF" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeSubSection == "NOTIF") MaterialTheme.colorScheme.primaryContainer else Color.LightGray.copy(alpha = 0.2f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Academy Announcements")
                }
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = { activeSubSection = "INVOICE" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeSubSection == "INVOICE") MaterialTheme.colorScheme.primaryContainer else Color.LightGray.copy(alpha = 0.2f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Payment Invoice Records")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (activeSubSection == "NOTIF") {
                notes.forEach { note ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(note.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BrandBluePrimary)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(note.message, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                userEnrollments.forEach { enroll ->
                    val course = courses.find { it.id == enroll.courseId }
                    if (course != null && !course.isFree) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("INV-LAKSHYA-${enroll.id}S", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("SUCCESS", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Text("Course: ${course.title}", fontSize = 13.sp)
                                Text("Amount Paid: ₹${course.price.toInt()} via Razorpay Sandbox", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Support & Contact Card ---
            Card(
                modifier = Modifier.fillMaxWidth().testTag("support_contact_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📞 Help & Support (सहायता और संपर्क)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "प्रवेश (Admissions), नए बैच, टेस्ट सीरीज या कोर्स से संबंधित किसी भी सहायता के लिए सीधे संपर्क करें:",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Phone support row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                try {
                                    val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                                        data = Uri.parse("tel:8090756962")
                                    }
                                    context.startActivity(dialIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Cannot open dialer: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Calling support",
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Call / WhatsApp Support (फ़ोन करें)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "8090756962",
                                fontSize = 14.sp,
                                color = Color(0xFF16A34A),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Email support row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                try {
                                    val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = Uri.parse("mailto:academylakshya112@gmail.com")
                                        putExtra(Intent.EXTRA_SUBJECT, "Lakshya Academy Student Support Query")
                                    }
                                    context.startActivity(emailIntent)
                                } catch (e: Exception) {
                                    try {
                                        val genericIntent = Intent(Intent.ACTION_VIEW, Uri.parse("mailto:academylakshya112@gmail.com"))
                                        context.startActivity(genericIntent)
                                    } catch (ex: Exception) {
                                        Toast.makeText(context, "Cannot open email client: ${ex.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "Email support",
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Email Support (ईमेल भेजें)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "academylakshya112@gmail.com",
                                fontSize = 12.sp,
                                color = Color(0xFF2563EB),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Danger zone Card for delete account
            var showDeleteConfirmation by remember { mutableStateOf(false) }

            if (showDeleteConfirmation) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmation = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = Color.Red)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete Account? (खाता हटाएं?)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Text(
                            text = "Are you absolutely sure you want to delete your Lakshya Academy account permanently? Your profile progress, course completions, and active certificates will be completely deleted and this action cannot be undone.\n\nक्या आप सच में अपना लक्ष्य एकेडमी खाता हमेशा के लिए हटाना चाहते हैं? आपकी प्रगति और कोर्स इतिहास सब डिलीट हो जाएगा। यह प्रक्रिया वापस नहीं की जा सकती।",
                            fontSize = 13.sp
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteConfirmation = false
                                viewModel.deleteAccount()
                                Toast.makeText(context, "Your account has been deleted permanently! 🗑️", Toast.LENGTH_LONG).show()
                            }
                        ) {
                            Text("Yes, Delete (हाँ, हटाएँ)", color = Color.Red, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirmation = false }) {
                            Text("Cancel (रद्द करें)", color = Color.Gray)
                        }
                    }
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth().testTag("delete_account_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)), // light red background
                border = BorderStroke(1.dp, Color(0xFFFCA5A5))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "⚠️ Danger Zone (खतरे का क्षेत्र)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFFDC2626)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Once you delete your account, there is no going back. All progress will be permanently cleared from this device and our record.",
                        fontSize = 11.sp,
                        color = Color(0xFF991B1B)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showDeleteConfirmation = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        modifier = Modifier.fillMaxWidth().testTag("delete_account_btn"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete My Account permanently (अकाउंट डिलीट करें)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { viewModel.logout() },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("logout_btn"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Log Out Safe Secure", color = Color.White)
            }
        }
    }

    // Modal Certificate Generation display
    if (showCertificateForCourse != null) {
        MockCertificateSheet(
            studentName = viewModel.currentUser?.name ?: "Student Honors",
            courseTitle = showCertificateForCourse!!.title,
            onDismiss = { showCertificateForCourse = null }
        )
    }
}

// === HIGH FIDELITY GRADUATION CERTIFICATE SHEET ===
@Composable
fun MockCertificateSheet(
    studentName: String,
    courseTitle: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFA)), // Parchment luxury white
            border = BorderStroke(4.dp, BrandGold)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("LAKSHYA ACADEMY GHAZIPUR", fontSize = 18.sp, fontWeight = FontWeight.Black, color = BrandBluePrimary)
                Text("An ISO Certified Educational Online Center of India", fontSize = 10.sp, color = Color.Gray)

                Spacer(modifier = Modifier.height(24.dp))
                Text("CERTIFICATE OF GRADUATION", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = BrandGold)
                Spacer(modifier = Modifier.height(14.dp))

                Text("This is proudly awarded to:", fontSize = 11.sp)
                Text(studentName, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.Black)

                Spacer(modifier = Modifier.height(14.dp))
                Text("for completing watched lessons, interactive tests scoring limits, and offline syllabus guidelines of standard batch:", fontSize = 11.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(8.dp))
                Text(courseTitle, fontSize = 14.sp, fontWeight = FontWeight.Black, color = BrandBluePrimary, textAlign = TextAlign.Center)

                Spacer(modifier = Modifier.height(24.dp))
                Divider(color = Color.LightGray.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Director Sir", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Black)
                        Text("Ghazipur, UP Office", fontSize = 10.sp, color = Color.Gray)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("12-June-2026", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Black)
                        Text("Date Issued", fontSize = 10.sp, color = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close & Back to Study Profile")
                }
            }
        }
    }
}

// ================= ADMIN CONSOLE PANEL =================
@Composable
fun AdminMainContainer(viewModel: AcademyViewModel) {
    var adminTab by remember { mutableStateOf("DASHBOARD") } // DASHBOARD, COURSES, TESTS, ALERTS, BANNERS
    val context = LocalContext.current

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                val tabs = listOf(
                    Triple("DASHBOARD", "Analytics", Icons.Default.Analytics),
                    Triple("COURSES", "Courses", Icons.Default.LibraryAdd),
                    Triple("TESTS", "MCQ Maker", Icons.Default.AddTask),
                    Triple("ALERTS", "Push Alerts", Icons.Default.Notifications),
                    Triple("BANNERS", "Banners", Icons.Default.Image)
                )
                tabs.forEach { (route, label, icon) ->
                    NavigationBarItem(
                        selected = adminTab == route,
                        onClick = { adminTab = route },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 10.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AdminTopAnnouncer(logout = { viewModel.logout() })

            when (adminTab) {
                "DASHBOARD" -> AdminAnalyticsDashboard(viewModel = viewModel)
                "COURSES" -> AdminCourseManager(viewModel = viewModel)
                "TESTS" -> AdminTestCreator(viewModel = viewModel)
                "ALERTS" -> AdminNotificationAlerts(viewModel = viewModel)
                "BANNERS" -> AdminBannerManager(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun AdminTopAnnouncer(logout: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(BorderStroke(1.dp, Color.White), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.lakshya_logo),
                        contentDescription = "Lakshya Logo",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Lakshya Ghazipur Admin Console", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Manage learning paths of 2026 batches", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                }
            }
            IconButton(onClick = logout) {
                Icon(Icons.Default.ExitToApp, contentDescription = "logout", tint = Color.LightGray)
            }
        }
    }
}

// === Admin visual charts dashboard ===
@Composable
fun AdminAnalyticsDashboard(viewModel: AcademyViewModel) {
    val courses by viewModel.allCourses.collectAsStateWithLifecycle()
    val doubts by viewModel.allDoubts.collectAsStateWithLifecycle()

    val totalCourses = courses.size
    val pendingDoubts = doubts.count { it.replyText.isBlank() }

    LazyColumn(modifier = Modifier.padding(16.dp)) {
        item {
            Text("Academy Sales & Analytics Indicators", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(14.dp))

            // Stats grid cards
            Row(modifier = Modifier.fillMaxWidth()) {
                Card(modifier = Modifier.weight(1f).padding(4.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Active Courses", fontSize = 11.sp, color = Color.Gray)
                        Text("$totalCourses", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Card(modifier = Modifier.weight(1f).padding(4.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Pending Doubts", fontSize = 11.sp, color = Color.Gray)
                        Text("$pendingDoubts", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.Red)
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Card(modifier = Modifier.weight(1f).padding(4.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Mock Sales volume", fontSize = 11.sp, color = Color.Gray)
                        Text("₹4,999", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF22C55E))
                    }
                }
                Card(modifier = Modifier.weight(1f).padding(4.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Total Enrolled Students", fontSize = 11.sp, color = Color.Gray)
                        Text("2", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Mock Sales Dynamic Flow Charts", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(10.dp))

            // Beautiful Custom graphical canvas chart representing sales progress
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height
                        // Draw light grid guide lines
                        drawLine(Color.Gray.copy(alpha = 0.2f), start = androidx.compose.ui.geometry.Offset(0f, height * 0.5f), end = androidx.compose.ui.geometry.Offset(width, height * 0.5f), strokeWidth = 2f)
                        drawLine(Color.Gray.copy(alpha = 0.2f), start = androidx.compose.ui.geometry.Offset(0f, height * 0.75f), end = androidx.compose.ui.geometry.Offset(width, height * 0.75f), strokeWidth = 2f)

                        // Draw visual custom wave progress representing academic sales boost
                        val points = listOf(
                            androidx.compose.ui.geometry.Offset(0f, height * 0.8f),
                            androidx.compose.ui.geometry.Offset(width * 0.25f, height * 0.65f),
                            androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.4f),
                            androidx.compose.ui.geometry.Offset(width * 0.75f, height * 0.5f),
                            androidx.compose.ui.geometry.Offset(width, height * 0.1f)
                        )
                        for (i in 0 until points.size - 1) {
                            drawLine(BrandBluePrimary, points[i], points[i + 1], strokeWidth = 6f)
                        }
                    }
                    Text("Daily course analytics curve (June)", color = BrandBlueSecondary, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopEnd))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Interactive Doubt Resolver Desk", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = BrandBluePrimary)
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (doubts.isEmpty()) {
            item {
                Text("No doubt tickets logged by students yet.", color = Color.Gray, fontSize = 13.sp)
            }
        } else {
            items(doubts) { doubt ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(doubt.subject, fontWeight = FontWeight.Bold, color = BrandBluePrimary)
                            Text("By: ${doubt.userName}", fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Question: ${doubt.questionText}", fontSize = 13.sp)

                        if (doubt.replyText.isBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            var replyInputText by remember { mutableStateOf("") }
                            Row(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = replyInputText,
                                    onValueChange = { replyInputText = it },
                                    placeholder = { Text("Faculty Answer keys reply...") },
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = { viewModel.submitDoubtReply(doubt, replyInputText) },
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("Reply")
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.LightGray.copy(alpha = 0.3f))
                                    .padding(8.dp)
                            ) {
                                Text("Your Answer: ${doubt.replyText}", fontSize = 12.sp, color = Color.DarkGray)
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// === Admin add/delete Courses desk ===
@Composable
fun AdminCourseManager(viewModel: AcademyViewModel) {
    val context = LocalContext.current
    val courses by viewModel.allCourses.collectAsStateWithLifecycle()
    var title by remember { mutableStateOf("") }
    var itemCategory by remember { mutableStateOf("UPSC") }
    var subject by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var isFree by remember { mutableStateOf(false) }
    var price by remember { mutableStateOf("") }

    // State for uploading secondary lessons to active course
    var showAddLessonToCourseId by remember { mutableIntStateOf(-1) }
    var chapName by remember { mutableStateOf("") }
    var lessonTitle by remember { mutableStateOf("") }
    var videoLinkInput by remember { mutableStateOf("") }
    var pdfNameInput by remember { mutableStateOf("") }
    var pdfContentInput by remember { mutableStateOf("") }
    var pdfSizeInput by remember { mutableStateOf("10.5 MB") }
    var thumbnailUrlInput by remember { mutableStateOf("") }

    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedVideoName by remember { mutableStateOf("") }

    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var selectedPdfName by remember { mutableStateOf("") }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedVideoUri = uri
            selectedVideoName = getFileNameFromUri(context, uri) ?: "Selected_Video.mp4"
            videoLinkInput = uri.toString()
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedPdfUri = uri
            selectedPdfName = getFileNameFromUri(context, uri) ?: "Selected_Notes.pdf"
            pdfNameInput = selectedPdfName.substringBeforeLast(".")
            pdfSizeInput = getFileSizeFromUri(context, uri)
        }
    }

    LazyColumn(modifier = Modifier.padding(16.dp)) {
        item {
            Text("Create Competitive Course Batch", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = BrandBluePrimary)
            Spacer(modifier = Modifier.height(12.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Course Title") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Core Subjects") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Target Batch syllabus details") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Course category:")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        val cats = listOf("Class 6", "Class 7", "Class 8", "Class 9", "Class 10", "Class 11", "Class 12", "UPSC", "UP Police", "SSC", "NEET")
                        items(cats) { cat ->
                            Button(
                                onClick = { itemCategory = cat },
                                colors = ButtonDefaults.buttonColors(containerColor = if (itemCategory == cat) BrandBluePrimary else Color.Gray),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(cat, fontSize = 11.sp)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isFree, onCheckedChange = { isFree = it })
                            Text("Free Course")
                        }
                        if (!isFree) {
                            OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Amt (₹)") }, modifier = Modifier.width(110.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (title.isBlank() || subject.isBlank() || desc.isBlank() || (!isFree && price.isBlank())) {
                                Toast.makeText(context, "Please enter all course fields properly!", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.adminAddNewCourse(
                                    title = title.trim(),
                                    category = itemCategory,
                                    subject = subject.trim(),
                                    desc = desc.trim(),
                                    isFree = isFree,
                                    price = price.toDoubleOrNull() ?: 0.0
                                )
                                Toast.makeText(context, "New competitive batch published successfully! 🎓", Toast.LENGTH_SHORT).show()
                                title = ""
                                subject = ""
                                desc = ""
                                price = ""
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Add Batch Online")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Delete or Add Lessons Syllabus", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(10.dp))
        }

        items(courses) { course ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(course.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Category: ${course.category} • Total lessons: ${course.totalLessons}", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                if (showAddLessonToCourseId == course.id) {
                                    showAddLessonToCourseId = -1
                                } else {
                                    showAddLessonToCourseId = course.id
                                }
                            }
                        ) {
                            Text(if (showAddLessonToCourseId == course.id) "Collapse" else "+ Add Lesson", color = BrandBlueSecondary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        TextButton(onClick = { viewModel.adminDeleteCourse(course.id) }) {
                            Text("Delete Course", color = Color.Red)
                        }
                    }

                    if (showAddLessonToCourseId == course.id) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.LightGray.copy(alpha = 0.2f))
                                .padding(12.dp)
                        ) {
                            Column {
                                OutlinedTextField(value = chapName, onValueChange = { chapName = it }, label = { Text("Chapter Title") }, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(value = lessonTitle, onValueChange = { lessonTitle = it }, label = { Text("Lesson Name") }, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = videoLinkInput,
                                    onValueChange = { 
                                        videoLinkInput = it
                                        if (it.isBlank() || !it.startsWith("content://")) {
                                            selectedVideoUri = null
                                            selectedVideoName = ""
                                        }
                                    },
                                    label = { Text("Video Link (Optional)") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = thumbnailUrlInput,
                                    onValueChange = { thumbnailUrlInput = it },
                                    label = { Text("Lecture Thumbnail Image URL (Optional - थंबनेल लिंक)") },
                                    placeholder = { Text("e.g. https://picsum.photos/seed/thumb/300/200") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Divider(
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                    )
                                    Text(
                                        text = " या / OR ",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                    Divider(
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(6.dp))
                                
                                Button(
                                    onClick = { videoPickerLauncher.launch("video/*") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedVideoUri != null) Color(0xFF10B981) else BrandBlueSecondary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = if (selectedVideoUri != null) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (selectedVideoUri != null) "Change Selected Video" else "Upload Local Video File (वीडियो चुनें)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                
                                if (selectedVideoUri != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFE0F2FE), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = BrandBluePrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = selectedVideoName,
                                                fontSize = 12.sp,
                                                color = Color(0xFF0369A1),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                selectedVideoUri = null
                                                selectedVideoName = ""
                                                videoLinkInput = ""
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear",
                                                tint = Color.Red,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = pdfNameInput,
                                    onValueChange = { 
                                        pdfNameInput = it
                                        if (it.isBlank()) {
                                            selectedPdfUri = null
                                            selectedPdfName = ""
                                        }
                                    },
                                    label = { Text("PDF Notes label (Optional)") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Divider(
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                    )
                                    Text(
                                        text = " या / OR ",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                    Divider(
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(6.dp))
                                
                                Button(
                                    onClick = { pdfPickerLauncher.launch("application/pdf") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedPdfUri != null) Color(0xFF10B981) else BrandBlueSecondary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = if (selectedPdfUri != null) Icons.Default.CheckCircle else Icons.Default.PictureAsPdf,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (selectedPdfUri != null) "Change Selected PDF" else "Upload Local PDF File (PDF चुनें)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                
                                if (selectedPdfUri != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFFEF3C7), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PictureAsPdf,
                                                contentDescription = null,
                                                tint = Color(0xFFD97706),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = selectedPdfName,
                                                fontSize = 12.sp,
                                                color = Color(0xFFB45309),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                selectedPdfUri = null
                                                selectedPdfName = ""
                                                pdfNameInput = ""
                                                pdfSizeInput = "10.5 MB"
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear",
                                                tint = Color.Red,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = pdfSizeInput,
                                        onValueChange = { pdfSizeInput = it },
                                        label = { Text("Note File Size (any MB)") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = pdfContentInput,
                                    onValueChange = { pdfContentInput = it },
                                    label = { Text("Write/Paste Note Content Wordings (Read In-App)") },
                                    placeholder = { Text("e.g. Chapter 1 study material details...") },
                                    modifier = Modifier.fillMaxWidth().height(110.dp),
                                    maxLines = 10
                                )
                                Spacer(modifier = Modifier.height(8.dp))
 
                                Button(
                                    onClick = {
                                        if (chapName.isBlank() || lessonTitle.isBlank()) {
                                            Toast.makeText(context, "Please enter both Chapter Title and Lesson Name!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            viewModel.adminAddLessonToCourse(
                                                courseId = course.id,
                                                chapter = chapName,
                                                title = lessonTitle,
                                                videoLink = videoLinkInput,
                                                pdfLink = if (selectedPdfUri != null) selectedPdfUri.toString() else (if (pdfNameInput.isNotBlank()) "${pdfNameInput.trim()}.pdf" else "Class_Handout.pdf"),
                                                pdfName = if (pdfNameInput.isNotBlank()) pdfNameInput.trim() else "Study notes compilation",
                                                pdfContent = if (pdfContentInput.isNotBlank()) pdfContentInput.trim() else (if (selectedPdfName.isNotBlank()) "Loaded Local PDF Notes: $selectedPdfName" else ""),
                                                fileSize = pdfSizeInput.trim(),
                                                thumbnailUrl = thumbnailUrlInput
                                            )
                                            Toast.makeText(context, "Lesson Notes & Lecture Add successful! 📂", Toast.LENGTH_SHORT).show()
                                            chapName = ""
                                            lessonTitle = ""
                                            videoLinkInput = ""
                                            thumbnailUrlInput = ""
                                            selectedVideoUri = null
                                            selectedVideoName = ""
                                            pdfNameInput = ""
                                            pdfContentInput = ""
                                            pdfSizeInput = "10.5 MB"
                                            selectedPdfUri = null
                                            selectedPdfName = ""
                                            showAddLessonToCourseId = -1
                                        }
                                    },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Upload Notes & Lecture")
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// === MCQ Maker and Test series generator ===
@Composable
fun AdminTestCreator(viewModel: AcademyViewModel) {
    val context = LocalContext.current
    val tests by viewModel.allTests.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("Mock Test") } // Mock Test, Weekly Test, Test Series
    var durationMinutesVal by remember { mutableStateOf("10") }
    var negativeMarking by remember { mutableStateOf(true) }

    // Upload Questions State
    var targetTestIdToAddQuestion by remember { mutableIntStateOf(-1) }
    var questionTextVal by remember { mutableStateOf("") }
    var optA by remember { mutableStateOf("") }
    var optB by remember { mutableStateOf("") }
    var optC by remember { mutableStateOf("") }
    var optD by remember { mutableStateOf("") }
    var correctIndexInput by remember { mutableIntStateOf(0) }

    LazyColumn(modifier = Modifier.padding(16.dp)) {
        item {
            Text("Create Online Mock Test / Exam", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = BrandBluePrimary)
            Spacer(modifier = Modifier.height(12.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Test Name Title") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(value = durationMinutesVal, onValueChange = { durationMinutesVal = it }, label = { Text("Timer duration (Minutes)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Mock Category category:")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        val options = listOf("Mock Test", "Weekly Test", "Test Series")
                        options.forEach { opt ->
                            Button(
                                onClick = { kind = opt },
                                colors = ButtonDefaults.buttonColors(containerColor = if (kind == opt) BrandBluePrimary else Color.Gray),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(opt, fontSize = 11.sp)
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = negativeMarking, onCheckedChange = { negativeMarking = it })
                        Text("Enable Negative marking Deductions (-0.5 Marks)")
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            viewModel.adminCreateNewTest(
                                title = name,
                                type = kind,
                                duration = durationMinutesVal.toIntOrNull() ?: 10,
                                negativeMarking = negativeMarking,
                                correctMark = 2,
                                penaltyMark = -0.5f
                            )
                            name = ""
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Create Exam series")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // AI Mock Test Auto-Generator Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)), // very soft green
                border = BorderStroke(1.dp, Color(0xFF86EFAC))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "AI",
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "✨ Lakshya AI Weekly Mock Test Generator",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF15803D)
                        )
                    }
                    Text(
                        text = "साप्ताहिक ऑल-सब्जेक्ट 50 प्रश्नों का सम्पूर्ण मॉक टेस्ट अपने आप जोड़ें (Bilingual - हिन्दी और English). इसमें गणित, विज्ञान, सामाजिक विज्ञान, हिंदी व्याकरण और English Grammar के प्रश्न शामिल होंगे।",
                        fontSize = 11.sp,
                        color = Color(0xFF166534),
                        modifier = Modifier.padding(vertical = 6.dp)
                    )

                    var aiTestClass by remember { mutableStateOf("Class 10") }
                    val aiClassOptions = listOf("Class 9", "Class 10", "Class 11", "Class 12", "Sarkari Exams")

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Select Target Class / Section (वर्ग चुनें):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        aiClassOptions.forEach { opt ->
                            val isSel = aiTestClass == opt
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSel) Color(0xFF16A34A) else Color.White)
                                    .border(1.dp, if (isSel) Color(0xFF16A34A) else Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    .clickable { aiTestClass = opt }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = opt,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color.White else Color(0xFF374151)
                                )
                            }
                        }
                    }

                    var aiTestNumber by remember { mutableStateOf("1") }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = aiTestNumber,
                        onValueChange = { aiTestNumber = it },
                        label = { Text("Mock/Weekly Test Number (e.g. 1, 2, 3..)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF16A34A),
                            unfocusedBorderColor = Color.LightGray
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val finalTitle = "Weekly AI Mock Test #$aiTestNumber - All Subjects ($aiTestClass) Bilingual"
                            viewModel.adminAutoGenerateWeeklyMockTest(testTitle = finalTitle, durationMins = 45)
                            Toast.makeText(context, "Weekly Mock Test series with 50 Bilingual MCQs auto-generated successfully! 📂✨", Toast.LENGTH_LONG).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Auto Add 50 Bilingual Questions (टेम्पलेट जनरेट करें)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Add MCQ questions to exam papers", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(10.dp))
        }

        items(tests) { test ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(test.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("${test.type} • Duration: ${test.durationMinutes} mins", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                if (targetTestIdToAddQuestion == test.id) {
                                    targetTestIdToAddQuestion = -1
                                } else {
                                    targetTestIdToAddQuestion = test.id
                                }
                            }
                        ) {
                            Text(if (targetTestIdToAddQuestion == test.id) "Collapse" else "+ Attach MCQ", color = BrandBlueSecondary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        TextButton(onClick = { viewModel.adminDeleteTest(test.id) }) {
                            Text("Delete Test", color = Color.Red)
                        }
                    }

                    if (targetTestIdToAddQuestion == test.id) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.LightGray.copy(alpha = 0.2f))
                                .padding(12.dp)
                        ) {
                            Column {
                                OutlinedTextField(value = questionTextVal, onValueChange = { questionTextVal = it }, label = { Text("MCQ Question Text") }, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(value = optA, onValueChange = { optA = it }, label = { Text("Option A") }, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(value = optB, onValueChange = { optB = it }, label = { Text("Option B") }, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(value = optC, onValueChange = { optC = it }, label = { Text("Option C") }, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(value = optD, onValueChange = { optD = it }, label = { Text("Option D") }, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(6.dp))

                                Text("Correct Answer option Index:")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val idxOptions = listOf("A", "B", "C", "D")
                                    idxOptions.forEachIndexed { i, label ->
                                        Button(
                                            onClick = { correctIndexInput = i },
                                            colors = ButtonDefaults.buttonColors(containerColor = if (correctIndexInput == i) Color(0xFF10B981) else Color.Gray)
                                        ) {
                                            Text(label)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = {
                                        viewModel.adminAddQuestionToTest(
                                            testId = test.id,
                                            quest = questionTextVal,
                                            a = optA,
                                            b = optB,
                                            c = optC,
                                            d = optD,
                                            correct = correctIndexInput
                                        )
                                        questionTextVal = ""
                                        optA = ""
                                        optB = ""
                                        optC = ""
                                        optD = ""
                                        correctIndexInput = 0
                                        targetTestIdToAddQuestion = -1
                                    },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Add Question keys")
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// === Push alerts transmitter panel ===
@Composable
fun AdminNotificationAlerts(viewModel: AcademyViewModel) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    // State for uploading new study guide documents
    var docType by remember { mutableStateOf("Book") }
    var docTitle by remember { mutableStateOf("") }
    var docDesc by remember { mutableStateOf("") }
    var docContentInput by remember { mutableStateOf("") }
    var docSizeInput by remember { mutableStateOf("12.5 MB") }

    // State for live class launcher/scheduler
    var liveTitleInput by remember { mutableStateOf("") }
    var liveTeacherInput by remember { mutableStateOf("") }

    val context = LocalContext.current

    LazyColumn(modifier = Modifier.padding(16.dp)) {
        item {
            Text("Send Academy Push notification", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = BrandBluePrimary)
            Spacer(modifier = Modifier.height(10.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("This triggers dynamic alert inside the student profile Drawer notifications stream.", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Alert Title") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Alert body details") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (title.isBlank() || body.isBlank()) {
                                Toast.makeText(context, "Please enter notification title and body!", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.adminSendPushNotification(title, body)
                                Toast.makeText(context, "Notification Broadcast Sent! 🔔", Toast.LENGTH_SHORT).show()
                                title = ""
                                body = ""
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Broadcast notification Alerts")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Schedule & Go Live Interactive Class", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = BrandBluePrimary)
            Spacer(modifier = Modifier.height(10.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Design details for the active Live batch stream that students can join immediately.", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = liveTitleInput,
                        onValueChange = { liveTitleInput = it },
                        label = { Text("Live Class Lecture Topic") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = liveTeacherInput,
                        onValueChange = { liveTeacherInput = it },
                        label = { Text("Faculty Teacher Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (liveTitleInput.isBlank() || liveTeacherInput.isBlank()) {
                                Toast.makeText(context, "Please enter Live Class Topic and Teacher Name!", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.adminScheduleLiveClass(
                                    title = liveTitleInput,
                                    teacher = liveTeacherInput
                                )
                                Toast.makeText(context, "Live Interactive Class Scheduled Successfully! 🔴", Toast.LENGTH_SHORT).show()
                                liveTitleInput = ""
                                liveTeacherInput = ""
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Launch Dynamic Live class")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Publish materials inside library", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BrandBluePrimary)
            Spacer(modifier = Modifier.height(10.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(value = docTitle, onValueChange = { docTitle = it }, label = { Text("Document Document Title") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(value = docDesc, onValueChange = { docDesc = it }, label = { Text("Study guidelines description") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(value = docSizeInput, onValueChange = { docSizeInput = it }, label = { Text("File Size to display (e.g. 15.5 MB)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = docContentInput,
                        onValueChange = { docContentInput = it },
                        label = { Text("Write/Paste study material note text (View In-App)") },
                        placeholder = { Text("e.g. This is detailed syllabus or book pages with notes...") },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        maxLines = 10
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Document Type drawer location:")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        val types = listOf("Book", "Syllabus", "Timetable", "Previous Year Paper", "Current Affairs")
                        LazyRow {
                            items(types) { opt ->
                                Button(
                                    onClick = { docType = opt },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (docType == opt) BrandBluePrimary else Color.Gray),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                ) {
                                    Text(opt, fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (docTitle.isBlank() || docDesc.isBlank()) {
                                Toast.makeText(context, "Please fill in document title and guidelines description!", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.adminUploadMaterial(
                                    type = docType,
                                    title = docTitle,
                                    desc = docDesc,
                                    size = docSizeInput.trim(),
                                    content = docContentInput.trim()
                                )
                                Toast.makeText(context, "$docType published to student library! 📚", Toast.LENGTH_SHORT).show()
                                docTitle = ""
                                docDesc = ""
                                docContentInput = ""
                                docSizeInput = "12.5 MB"
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Publish Document to Library")
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// === Premium High-Fidelity Internet Status Guard Overlay ===
@Composable
fun NoInternetScreen(onRetry: () -> Unit) {
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(BrandBluePrimary, Color(0xFF0F172A))
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBrush)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.WifiOff,
                contentDescription = "Offline",
                tint = Color.White,
                modifier = Modifier.size(64.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "सक्रिय इंटरनेट नहीं मिला",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Active Internet Connection Required",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.90f),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Lakshya App को शुरू करने, लेक्चर्स देखने और टेस्ट सीरीज़ हल करने के लिए कृपया अपना इंटरनेट (Wi-Fi या Mobile Data) चालू करें।",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.70f),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(40.dp))
        
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = BrandBluePrimary
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(52.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Retry",
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "पुनः प्रयास करें (Retry Check)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// Helper to get selected file name from ContentResolver Uri
fun getFileNameFromUri(context: android.content.Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result.substring(cut + 1)
        }
    }
    return result
}

// Helper to get formatted file size of selected content Uri
fun getFileSizeFromUri(context: android.content.Context, uri: Uri): String {
    var result: Long = 0
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (index != -1) {
                    result = cursor.getLong(index)
                }
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
    }
    if (result <= 0) {
        return "2.4 MB" // Generic default
    }
    val kb = result / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        String.format(java.util.Locale.US, "%.1f MB", mb)
    } else {
        String.format(java.util.Locale.US, "%.1f KB", kb)
    }
}

@Composable
fun AdminBannerManager(viewModel: AcademyViewModel) {
    val banners by viewModel.allBanners.collectAsStateWithLifecycle()
    var title by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var linkUrl by remember { mutableStateOf("") }
    var buttonText by remember { mutableStateOf("VIEW") }
    var description by remember { mutableStateOf("") }

    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Home Screen Banner Manager (बैनर मैनेजर)",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Add clickable promotional sliders (batches, Instagram, Telegram) directly to the student home screen.",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "✨ Create New Banner / Poster",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Banner Title / Headline (जैसे: UP Police New Batch)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Short Description / Subtitle (विवरण - वैकल्पिक)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = imageUrl,
                        onValueChange = { imageUrl = it },
                        label = { Text("Banner Image URL or Resource Link (इमेज लिंक)") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://example.com/banner.png") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = linkUrl,
                        onValueChange = { linkUrl = it },
                        label = { Text("Redirect Intent URL or Tab (जैसे: COURSES या Web Link)") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://t.me/lakshya_academy या COURSES") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = buttonText,
                        onValueChange = { buttonText = it },
                        label = { Text("Button Text Action (जैसे: VIEW, JOIN, ENROLL)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                Toast.makeText(context, "Please enter banner title!", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.adminAddBanner(
                                    title = title,
                                    imageUrl = imageUrl,
                                    linkUrl = linkUrl,
                                    buttonText = buttonText,
                                    description = description
                                )
                                Toast.makeText(context, "New Promo Banner Added to home screen successfully! 🖼️✨", Toast.LENGTH_SHORT).show()
                                title = ""
                                description = ""
                                imageUrl = ""
                                linkUrl = ""
                                buttonText = "VIEW"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Banner to Student Dashboard", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Text(
                text = "Active Banners (${banners.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (banners.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No banners active. Add one above to display!", color = Color.Gray, fontSize = 13.sp)
                }
            }
        } else {
            items(banners) { banner ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = banner.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            if (banner.description.isNotEmpty()) {
                                Text(text = banner.description, fontSize = 11.sp, color = Color.Gray, maxLines = 1)
                            }
                            Text(
                                text = "Action: ${banner.buttonText} ➜ ${if (banner.linkUrl.isEmpty()) "None" else banner.linkUrl}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        IconButton(
                            onClick = {
                                viewModel.adminDeleteBanner(banner.id)
                                Toast.makeText(context, "Banner deleted! 🗑️", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Banner", tint = Color.Red)
                        }
                    }
                }
            }
        }
    }
}
