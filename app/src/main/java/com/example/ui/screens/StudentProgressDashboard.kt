package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*
import com.example.ui.viewmodel.AcademyViewModel
import com.example.util.StudyTracker
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentProgressDashboard(
    viewModel: AcademyViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val enrollments by viewModel.allEnrollments.collectAsStateWithLifecycle()
    val courses by viewModel.allCourses.collectAsStateWithLifecycle()
    val scores by viewModel.allScores.collectAsStateWithLifecycle()
    val userEmail = viewModel.currentUser?.email ?: ""
    val studentName = viewModel.currentUser?.name ?: "Learner"

    // Real-time state that updates periodically
    var todaySec by remember { mutableIntStateOf(StudyTracker.getTodayStudySeconds(context)) }
    var weeklyMin by remember { mutableIntStateOf(StudyTracker.getWeeklyStudyMinutes(context)) }
    var monthlyMin by remember { mutableIntStateOf(StudyTracker.getMonthlyStudyMinutes(context)) }
    var streak by remember { mutableIntStateOf(StudyTracker.getStudyStreak(context)) }
    var dailyGoalMin by remember { mutableIntStateOf(StudyTracker.getDailyStudyGoalMinutes(context)) }
    var graphData by remember { mutableStateOf(StudyTracker.getLast7DaysStudyMinutes(context)) }

    var showGoalDialog by remember { mutableStateOf(false) }

    // Periodic state updater and Supabase fetch
    LaunchedEffect(userEmail) {
        if (userEmail.isNotBlank()) {
            StudyTracker.fetchAndMergeFromSupabase(context, userEmail)
        }
        while (true) {
            delay(2000L) // Refresh every 2 seconds for immediate feedback
            todaySec = StudyTracker.getTodayStudySeconds(context)
            weeklyMin = StudyTracker.getWeeklyStudyMinutes(context)
            monthlyMin = StudyTracker.getMonthlyStudyMinutes(context)
            streak = StudyTracker.getStudyStreak(context)
            dailyGoalMin = StudyTracker.getDailyStudyGoalMinutes(context)
            graphData = StudyTracker.getLast7DaysStudyMinutes(context)
        }
    }

    // Calculations based on database state
    val enrolledCoursesWithProgress = remember(enrollments, courses, userEmail) {
        enrollments.filter { it.userEmail == userEmail }.mapNotNull { enrollment ->
            val course = courses.find { it.id == enrollment.courseId }
            if (course != null) {
                val total = course.totalLessons.coerceAtLeast(1)
                val percent = (enrollment.completedLessonsCount.toFloat() / total.toFloat() * 100).coerceAtMost(100f)
                Triple(course, enrollment.completedLessonsCount, percent)
            } else null
        }
    }

    val overallCourseProgress = remember(enrolledCoursesWithProgress) {
        if (enrolledCoursesWithProgress.isNotEmpty()) {
            enrolledCoursesWithProgress.map { it.third }.average().toFloat()
        } else 0f
    }

    val totalVideosWatched = remember(enrolledCoursesWithProgress) {
        enrolledCoursesWithProgress.sumOf { it.second }
    }

    val totalEnrolledLessons = remember(enrolledCoursesWithProgress) {
        enrolledCoursesWithProgress.sumOf { it.first.totalLessons }
    }

    val remainingVideos = remember(totalEnrolledLessons, totalVideosWatched) {
        (totalEnrolledLessons - totalVideosWatched).coerceAtLeast(0)
    }

    // Mock test calculations
    val userScores = remember(scores, userEmail) {
        scores.filter { it.userEmail == userEmail }
    }

    val testAttemptedCount = userScores.size

    val testAverageScore = remember(userScores) {
        if (userScores.isNotEmpty()) {
            userScores.map { it.score }.average().toFloat()
        } else 0f
    }

    val testHighestScore = remember(userScores) {
        if (userScores.isNotEmpty()) {
            userScores.maxOf { it.score }
        } else 0f
    }

    val testAccuracy = remember(userScores) {
        if (userScores.isNotEmpty()) {
            val totalCorrect = userScores.sumOf { it.correctAnswers }
            val totalQs = userScores.sumOf { it.totalQuestions }.coerceAtLeast(1)
            (totalCorrect.toFloat() / totalQs.toFloat() * 100).coerceIn(0f, 100f)
        } else 0f
    }

    val badges = remember(todaySec, weeklyMin, totalVideosWatched, testAttemptedCount, testHighestScore, dailyGoalMin, streak) {
        StudyTracker.getBadges(context, totalVideosWatched, testAttemptedCount, testHighestScore)
    }

    val isDashboardEmpty = enrolledCoursesWithProgress.isEmpty() && testAttemptedCount == 0 && todaySec == 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Study Progress", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("dashboard_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF8FAFC)), // Warm eye-safe off-white background
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Friendly Empty State / Welcome Onboarding Banner
            if (isDashboardEmpty) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Welcome to your Lakshya Dashboard! 🌟",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Let's kickstart your learning journey! Study your course lessons, watch video lectures, or attempt a weekly mock test to automatically track your real-time analytics, daily goals, learning streaks, and unlock prestigious achievements!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onBack,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Go to Study Portals", color = Color.White)
                            }
                        }
                    }
                }
            } else {
                // Header Welcome Panel
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Hello, $studentName!",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Keep up the momentum! Today's study progress is updated in real-time.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray,
                                    lineHeight = 18.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            // Streak Flame Badge
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(Color(0xFFFF9800), Color(0xFFFF5722))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Whatshot,
                                        contentDescription = "Streak",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "$streak Days",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Daily Study Goal Card
            item {
                val todayMin = todaySec / 60
                val goalProgress = if (dailyGoalMin > 0) (todayMin.toFloat() / dailyGoalMin.toFloat()).coerceIn(0f, 1f) else 0f
                val percentString = (goalProgress * 100).toInt()

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.TrackChanges,
                                    contentDescription = "Goal",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Daily Study Goal",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(onClick = { showGoalDialog = true }) {
                                Icon(Icons.Default.Settings, contentDescription = "Configure Goal", tint = Color.Gray)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(80.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    progress = { 1.0f },
                                    modifier = Modifier.fillMaxSize(),
                                    color = Color(0xFFE2E8F0),
                                    strokeWidth = 8.dp
                                )
                                CircularProgressIndicator(
                                    progress = { goalProgress },
                                    modifier = Modifier.fillMaxSize(),
                                    color = BrandBluePrimary,
                                    strokeWidth = 8.dp
                                )
                                Text(
                                    text = "$percentString%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandBluePrimary
                                )
                            }

                            Spacer(modifier = Modifier.width(20.dp))

                            Column {
                                Text(
                                    text = "Today's Study: $todayMin mins",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Daily Goal: $dailyGoalMin mins",
                                    color = Color.Gray,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { goalProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = BrandBluePrimary,
                                    trackColor = Color(0xFFF1F5F9)
                                )
                            }
                        }
                    }
                }
            }

            // Study Time Summaries Row (Daily / Weekly / Monthly / Total)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Weekly Summary Card
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Icon(
                                    Icons.Default.DateRange,
                                    contentDescription = "Weekly",
                                    tint = Color(0xFF3B82F6),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Weekly Study", color = Color.Gray, fontSize = 12.sp)
                                Text(
                                    "$weeklyMin mins",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.Black
                                )
                                Text("Last 7 days", color = Color.Gray, fontSize = 10.sp)
                            }
                        }

                        // Monthly Summary Card
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Icon(
                                    Icons.Default.CalendarMonth,
                                    contentDescription = "Monthly",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Monthly Study", color = Color.Gray, fontSize = 12.sp)
                                Text(
                                    "$monthlyMin mins",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.Black
                                )
                                Text("Last 30 days", color = Color.Gray, fontSize = 10.sp)
                            }
                        }
                    }

                    // Total All-Time Study Time Card
                    val totalAllTimeMin = remember(todaySec, weeklyMin) { StudyTracker.getTotalStudyMinutesAllTime(context) }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = "Total Study Time",
                                    tint = Color(0xFF8B5CF6),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Total Study Time", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("All-time active study duration", color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                            Text(
                                text = if (totalAllTimeMin >= 60) "${totalAllTimeMin / 60}h ${totalAllTimeMin % 60}m" else "$totalAllTimeMin mins",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF8B5CF6)
                            )
                        }
                    }
                }
            }

            // Performance Graph Card (Last 7 Days Study Time)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = "Analytics",
                                tint = Color(0xFF8B5CF6)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Weekly Performance Graph",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        // Render a beautiful custom styled Bar Chart
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                val maxMinutes = graphData.map { it.second }.maxOrNull()?.coerceAtLeast(1) ?: 1
                                graphData.forEach { (dayLabel, minutes) ->
                                    val barHeightPct = minutes.toFloat() / maxMinutes.toFloat()
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "${minutes}m",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (minutes > 0) BrandBluePrimary else Color.Gray
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .width(18.dp)
                                                .fillMaxHeight(0.7f * barHeightPct.coerceAtLeast(0.05f))
                                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                                .background(
                                                    if (minutes > 0) {
                                                        Brush.verticalGradient(
                                                            colors = listOf(Color(0xFF8B5CF6), Color(0xFF6366F1))
                                                        )
                                                    } else {
                                                        SolidColor(Color(0xFFE2E8F0))
                                                    }
                                                )
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = dayLabel,
                                            fontSize = 11.sp,
                                            color = Color.DarkGray,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Module Analytics Breakdowns Card
            item {
                val modulesList = listOf(
                    Pair("Live Classes", StudyTracker.MODULE_LIVE_CLASSES),
                    Pair("Course Syllabus", StudyTracker.MODULE_COURSE_SYLLABUS),
                    Pair("Lakshya AI Coach", StudyTracker.MODULE_AI_COACH),
                    Pair("Current Affairs", StudyTracker.MODULE_CURRENT_AFFAIRS),
                    Pair("Test Series", StudyTracker.MODULE_TEST_SERIES),
                    Pair("Previous Papers", StudyTracker.MODULE_PREVIOUS_PAPERS),
                    Pair("Exam Alerts", StudyTracker.MODULE_EXAM_ALERTS),
                    Pair("Free Books", StudyTracker.MODULE_FREE_BOOKS),
                    Pair("Time Table", StudyTracker.MODULE_TIME_TABLE),
                    Pair("Study Apps", StudyTracker.MODULE_STUDY_WEBSITES)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Category,
                                contentDescription = "Module Analytics",
                                tint = BrandBluePrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Module Analytics",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            modulesList.forEach { (label, keyName) ->
                                val seconds = remember(todaySec) { StudyTracker.getModuleStudySeconds(context, keyName) }
                                val mins = seconds / 60
                                val secRem = seconds % 60
                                val displayTime = if (mins > 0) "${mins}m ${secRem}s" else "${seconds}s"

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.Black
                                    )
                                    Text(
                                        text = displayTime,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (seconds > 0) BrandBluePrimary else Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Overall Course Progress Panel
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = "Course Progress",
                                tint = Color(0xFF3B82F6),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Overall Course Progress",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Course Completion Score: ${overallCourseProgress.toInt()}%",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { overallCourseProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF3B82F6),
                            trackColor = Color(0xFFE2E8F0)
                        )

                        if (enrolledCoursesWithProgress.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "Enrolled Batches:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.DarkGray
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            enrolledCoursesWithProgress.forEach { (course, completed, percent) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = course.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "$completed/${course.totalLessons} lectures (${percent.toInt()}%)",
                                        fontSize = 12.sp,
                                        color = BrandBluePrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "No enrolled courses found. Visit Course Syllabus to enroll and play chapters to automatically update progress.",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // Video / Lecture Progress Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = "Videos",
                                tint = Color(0xFFEC4899),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Video Progress & Lecture Stats",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Column 1: Watched
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "$totalVideosWatched",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEC4899)
                                )
                                Text("Lectures Watched", color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
                            }

                            // Column 2: Remaining
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "$remainingVideos",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.DarkGray
                                )
                                Text("Lectures Remaining", color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
                            }

                            // Column 3: Play Time
                            val totalVideoWatchTimeMinutes = totalVideosWatched * 15 + (todaySec / 60)
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "${totalVideoWatchTimeMinutes}m",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981)
                                )
                                Text("Lecture Watch Time", color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }

            // Mock Test Statistics Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Quiz,
                                contentDescription = "Mock Tests",
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Mock Test Statistics",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (testAttemptedCount > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Text("$testAttemptedCount", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Black)
                                    Text("Attempted", color = Color.Gray, fontSize = 11.sp)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Text(String.format("%.1f", testAverageScore), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF3B82F6))
                                    Text("Avg Marks", color = Color.Gray, fontSize = 11.sp)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Text(String.format("%.1f", testHighestScore), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF10B981))
                                    Text("Highest Marks", color = Color.Gray, fontSize = 11.sp)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Text("${testAccuracy.toInt()}%", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF8B5CF6))
                                    Text("Accuracy", color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                        } else {
                            Text(
                                "No mock tests attempted yet. Attempt weekly mock tests in Test Series tab to view score trends, highest marks, and test accuracy.",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // Achievements & Badges Panel
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                        Icon(
                            imageVector = Icons.Default.MilitaryTech,
                            contentDescription = "Badges",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Achievements & Badges",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Grid-like layout for achievements
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        badges.chunked(2).forEach { rowBadges ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowBadges.forEach { badge ->
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(110.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (badge.isUnlocked) Color.White else Color(0xFFF1F5F9).copy(alpha = 0.8f)
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            if (badge.isUnlocked) Color(0xFFFFD700).copy(alpha = 0.3f) else Color.LightGray.copy(alpha = 0.2f)
                                        ),
                                        elevation = CardDefaults.cardElevation(
                                            defaultElevation = if (badge.isUnlocked) 2.dp else 0.dp
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (badge.isUnlocked) Color(0xFFFFFDF0) else Color(0xFFE2E8F0)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(badge.icon, fontSize = 20.sp)
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = badge.title,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (badge.isUnlocked) Color.Black else Color.Gray,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = badge.description,
                                                    fontSize = 9.sp,
                                                    color = Color.Gray,
                                                    lineHeight = 11.sp,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = badge.progressText,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (badge.isUnlocked) Color(0xFF10B981) else Color.Gray
                                                )
                                            }
                                        }
                                    }
                                }
                                if (rowBadges.size < 2) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Set Daily Goal Dialog
    if (showGoalDialog) {
        var tempGoal by remember { mutableStateOf(dailyGoalMin.toString()) }
        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            title = { Text("Set Daily Study Goal") },
            text = {
                Column {
                    Text(
                        "Set your daily targeted study time (in minutes). Learn and watch video lectures daily to complete your progress indicator!",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempGoal,
                        onValueChange = { tempGoal = it.filter { char -> char.isDigit() } },
                        label = { Text("Daily Goal (Minutes)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(15, 30, 45, 60, 90, 120).forEach { mins ->
                            Button(
                                onClick = { tempGoal = mins.toString() },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (tempGoal == mins.toString()) BrandBluePrimary else Color(0xFFF1F5F9),
                                    contentColor = if (tempGoal == mins.toString()) Color.White else Color.Black
                                )
                            ) {
                                Text("$mins m", fontSize = 10.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalMins = tempGoal.toIntOrNull() ?: dailyGoalMin
                        StudyTracker.setDailyStudyGoalMinutes(context, finalMins)
                        dailyGoalMin = finalMins
                        showGoalDialog = false
                    }
                ) {
                    Text("Save Goal")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoalDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
