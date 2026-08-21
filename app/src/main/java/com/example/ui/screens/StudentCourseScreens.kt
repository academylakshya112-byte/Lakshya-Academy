package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.CourseEntity
import com.example.api.R2SupabaseManager
import com.example.api.SupabaseVideo
import com.example.ui.theme.*
import com.example.ui.viewmodel.AcademyViewModel

@Composable
fun StudentHomeDashboard(
    viewModel: AcademyViewModel,
    onTabSelect: (String) -> Unit,
    onPlayCourse: (CourseEntity) -> Unit,
    onOpenWebsite: (String) -> Unit = {}
) {
    val enrollments by viewModel.allEnrollments.collectAsStateWithLifecycle()
    val courses by viewModel.allCourses.collectAsStateWithLifecycle()
    val banners by viewModel.allBanners.collectAsStateWithLifecycle()
    val studentName = viewModel.currentUser?.name ?: "Learner"
    val userEmail = viewModel.currentUser?.email ?: ""

    var showCommunityPopup by remember { mutableStateOf(true) }
    val popupConfig = viewModel.communityPopupConfig

    var supabaseVideos by remember { mutableStateOf<List<SupabaseVideo>>(emptyList()) }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.syncFromRemote()
        viewModel.fetchCommunityPopup()
        R2SupabaseManager.fetchVideos(context) { list, error ->
            if (error == null && list != null) {
                supabaseVideos = list
            }
        }
    }

    if (showCommunityPopup && popupConfig.enabled) {
        CommunityPopupDialog(
            config = popupConfig,
            onDismiss = { showCommunityPopup = false }
        )
    }

    val activeAndSortedBanners = remember(banners) {
        banners.filter { it.isActive }.sortedBy { it.displayOrder }
    }

    val r2Batches = remember(supabaseVideos) {
        supabaseVideos
            .map { it.classText.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    val combinedCourses = remember(courses, r2Batches, supabaseVideos) {
        val list = mutableListOf<CourseEntity>()
        
        // Add real courses directly, no fallback
        list.addAll(courses)
        
        // Add virtual courses if they don't exist
        for (batchName in r2Batches) {
            val exists = list.any { 
                java.text.Normalizer.normalize(it.title, java.text.Normalizer.Form.NFC).lowercase()
                    .replace(Regex("[^\\p{L}\\p{N}]"), "").trim() == 
                java.text.Normalizer.normalize(batchName, java.text.Normalizer.Form.NFC).lowercase()
                    .replace(Regex("[^\\p{L}\\p{N}]"), "").trim()
            }
            if (!exists) {
                val sampleVideo = supabaseVideos.firstOrNull { 
                    it.classText.trim().equals(batchName, ignoreCase = true) 
                }
                val category = sampleVideo?.subject ?: "R2 Lectures"
                val virtualId = -(batchName.hashCode().coerceAtLeast(1))
                list.add(
                    CourseEntity(
                        id = virtualId,
                        title = batchName,
                        category = category,
                        subject = sampleVideo?.subject ?: "R2 Lectures",
                        description = sampleVideo?.description ?: "Dynamic video lectures and PDF study materials for $batchName.",
                        isFree = true,
                        price = 0.0,
                        totalLessons = supabaseVideos.count { it.classText.trim().equals(batchName, ignoreCase = true) },
                        imageUrl = ""
                    )
                )
            }
        }
        list
    }

    var searchQuery by remember { mutableStateOf("") }
    val filteredCourses = if (searchQuery.isBlank()) combinedCourses else combinedCourses.filter {
        it.title.contains(searchQuery, ignoreCase = true) || it.category.contains(searchQuery, ignoreCase = true)
    }

    var selectedCourseForDetail by remember { mutableStateOf<CourseEntity?>(null) }
    var showMaterialTypeDialog by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            val userPhotoUri = viewModel.currentUser?.photoUri ?: ""
            DashboardBrandHeader(
                studentName = studentName,
                photoUri = userPhotoUri,
                onNotificationClick = { onTabSelect("ALERTS") }
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Official Announcements (महत्वपूर्ण सूचना पट्ट)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            BannerCarousel(banners = activeAndSortedBanners, onTabSelect = onTabSelect)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search Courses / Batches (कोर्स खोजें)") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().testTag("search_indicator"),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(title = "Lakshya Academic Portals (अकादमिक पोर्टल)")
            Academic3x3GridDashboard(onTabSelect = { tab ->
                when (tab) {
                    "COURSES" -> onTabSelect("COURSES")
                    "TESTS" -> onTabSelect("TESTS")
                    "books", "timetable", "previous_papers", "current_affairs", "syllabus" -> showMaterialTypeDialog = tab.replaceFirstChar { it.uppercase() }
                    else -> onTabSelect(tab)
                }
            })
            
            Spacer(modifier = Modifier.height(8.dp))
            // Motivation Tag
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
                Text(
                    "👑 Mere Boss Rahul Bhai | Future Army Boy 🪖",
                    fontSize = 11.sp,
                    color = BrandBlueSecondary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(BrandBluePrimary.copy(alpha = 0.05f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader(title = "Active Enrollment Batches (सक्रिय बैच)")
        }

        if (filteredCourses.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Text("No batches available.", color = Color.Gray, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            items(filteredCourses) { course ->
                val isEnrolled = course.id < 0 || enrollments.any { it.userEmail == userEmail && it.courseId == course.id }
                CourseCard(
                    course = course,
                    isEnrolled = isEnrolled,
                    onPlayClick = { onPlayCourse(course) },
                    onEnrollClick = { selectedCourseForDetail = course }
                )
            }
        }
    }

    if (showMaterialTypeDialog != null) {
        MaterialDocumentViewerDialog(
            type = showMaterialTypeDialog!!,
            viewModel = viewModel,
            onDismiss = { showMaterialTypeDialog = null }
        )
    }

    if (selectedCourseForDetail != null) {
        CourseDetailEnrollmentDialog(
            course = selectedCourseForDetail!!,
            viewModel = viewModel,
            onEnrollSuccess = { selectedCourseForDetail = null },
            onDismiss = { selectedCourseForDetail = null }
        )
    }
}

@Composable
fun CourseCard(
    course: CourseEntity,
    isEnrolled: Boolean,
    onPlayClick: () -> Unit,
    onEnrollClick: () -> Unit
) {
    var isFavorite by remember { mutableStateOf(false) }
    val context = LocalContext.current
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
            .clickable {
                if (isEnrolled) {
                    onPlayClick()
                } else {
                    onEnrollClick()
                }
            },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(0.5.dp, Color.LightGray.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header with Title and Favorite Icon
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
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
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Image Section
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
                                android.util.Log.e("CourseCard", "[IMAGE ERROR] Failed to load: $displayImageUrl", state.result.throwable)
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
                
                // Overlay Badge for Category
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

            // Footer Section with Price and Button
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
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
                            onPlayClick()
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("My Enrolled Batches", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(16.dp))
        if (myEnrolledCourses.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                    Text("You haven't enrolled in any batch yet.", color = Color.Gray, modifier = Modifier.padding(top = 12.dp))
                }
            }
        } else {
            LazyColumn {
                items(myEnrolledCourses) { course ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(course.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BrandBluePrimary)
                            Text(course.category, fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(12.dp))

                            // Note: real progress tracking needs a database table. Mocking for UI:
                            val progressFloat = 0.4f
                            LinearProgressIndicator(
                                progress = { progressFloat },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
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
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
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

fun openExternalUrl(context: android.content.Context, url: String) {
    if (url.isBlank()) {
        Toast.makeText(context, "Link is empty", Toast.LENGTH_SHORT).show()
        return
    }
    val cleanUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
        "https://$url"
    } else {
        url
    }
    try {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(cleanUrl))
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open link: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityPopupDialog(
    config: com.example.data.CommunityPopupEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.70f))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = true,
                enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) + androidx.compose.animation.scaleIn(initialScale = 0.85f, animationSpec = androidx.compose.animation.core.tween(300)),
                exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(200)) + androidx.compose.animation.scaleOut(targetScale = 0.85f, animationSpec = androidx.compose.animation.core.tween(200))
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.90f)
                        .widthIn(max = 420.dp)
                        .padding(16.dp)
                        .clickable(enabled = false, onClick = {}),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Top Image
                            if (config.imageUrl.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    AsyncImage(
                                        model = config.imageUrl,
                                        contentDescription = "Community Banner",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                                        .background(
                                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                                colors = listOf(
                                                    Color(0xFF1E293B),
                                                    Color(0xFF0F172A)
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Groups,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(52.dp)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (config.title.isNotBlank()) config.title else "SHADOWXRAHUL",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = if (config.description.isNotBlank()) config.description else "Join our Official Community to receive the latest updates, study materials, notices, announcements, and important information.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 18.sp
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                // 1. Green Button - Join WhatsApp Channel
                                Button(
                                    onClick = {
                                        openExternalUrl(context, config.whatsappUrl)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF25D366),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Chat,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Join WhatsApp Channel",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // 2. Blue Button - Join Telegram Channel
                                Button(
                                    onClick = {
                                        openExternalUrl(context, config.telegramUrl)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF0088CC),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Join Telegram Channel",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // 3. Gray Button - Continue to App
                                Surface(
                                    onClick = onDismiss,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "Continue to App",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        // Close (X) button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


