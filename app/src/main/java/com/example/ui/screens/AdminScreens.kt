package com.example.ui.screens

import android.content.Context
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.example.util.ToastHelper
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.*
import com.example.service.SmartQuestionParser
import com.example.api.SupabaseVideo
import com.example.api.R2SupabaseManager
import com.example.service.QuestionDeduplicator
import com.example.ui.viewmodel.AcademyViewModel
import com.example.service.WeeklyMockTestGenerator
import com.example.service.MockTestGenerator20
import kotlinx.coroutines.delay
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.text.style.TextAlign
import android.util.Log

@Composable
fun AdminAnalyticsDashboard(viewModel: AcademyViewModel) {
    val courses by viewModel.allCourses.collectAsStateWithLifecycle()
    val banners by viewModel.allBanners.collectAsStateWithLifecycle()
    val testsRaw by viewModel.allTests.collectAsStateWithLifecycle()
    val tests = remember(testsRaw) {
        testsRaw.filter {
            it.title.startsWith("AI 2.0 Mock Test") || it.title.startsWith("Weekly Mock Test - ")
        }
    }
    val context = androidx.compose.ui.platform.LocalContext.current

    var summaryVideos by remember { mutableStateOf<List<SupabaseVideo>>(emptyList()) }
    LaunchedEffect(Unit) {
        R2SupabaseManager.fetchVideos(context) { list, error ->
            if (list != null) {
                summaryVideos = list
            }
        }
    }

    val r2Batches = remember(summaryVideos) {
        summaryVideos
            .map { it.classText.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    val combinedCourses = remember(courses, r2Batches, summaryVideos) {
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
                val sampleVideo = summaryVideos.firstOrNull { 
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
                        totalLessons = summaryVideos.count { it.classText.trim().equals(batchName, ignoreCase = true) },
                        imageUrl = ""
                    )
                )
            }
        }
        list
    }

    var selectedFolderModule by remember { mutableStateOf<com.example.data.FolderModule?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Admin Real-time Analytics", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AnalyticsStatCard("Total Batches", combinedCourses.size.toString(), Icons.Default.LibraryBooks, Color(0xFF6366F1), modifier = Modifier.weight(1f))
                AnalyticsStatCard("Active Tests", tests.size.toString(), Icons.Default.Quiz, Color(0xFF10B981), modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AnalyticsStatCard("Promo Banners", banners.size.toString(), Icons.Default.Image, Color(0xFFF59E0B), modifier = Modifier.weight(1f))
                AnalyticsStatCard("Handouts", "24+", Icons.Default.Description, Color(0xFFEC4899), modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Folder Management System (Admin CMS)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(
                    onClick = { selectedFolderModule = com.example.data.FolderModule.SYLLABUS },
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFF2563EB))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Course Syllabus", fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                }
                Card(
                    onClick = { selectedFolderModule = com.example.data.FolderModule.PREVIOUS_PAPERS },
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = Color(0xFFDC2626))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Previous Papers", fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                }
                Card(
                    onClick = { selectedFolderModule = com.example.data.FolderModule.FREE_BOOKS },
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = Color(0xFF16A34A))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Free Books", fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                }
            }

            AdminLiveClassManager(viewModel)
        }
    }

    if (selectedFolderModule != null) {
        FolderExplorerDialog(
            module = selectedFolderModule!!,
            viewModel = viewModel,
            onDismiss = { selectedFolderModule = null }
        )
    }
}

@Composable
fun AdminLiveClassManager(viewModel: AcademyViewModel) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Live Class Manager", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = viewModel.activeLiveStreamTitle, onValueChange = { viewModel.activeLiveStreamTitle = it }, label = { Text("Live Class Title") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = viewModel.activeLiveStreamTeacher, onValueChange = { viewModel.activeLiveStreamTeacher = it }, label = { Text("Teacher Name") }, modifier = Modifier.fillMaxWidth())
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Is Live Session Active?")
                Spacer(modifier = Modifier.width(16.dp))
                Switch(checked = viewModel.liveClassRoomActive, onCheckedChange = { viewModel.liveClassRoomActive = it })
            }
        }
    }
}

@Composable
fun TestHubMain(viewModel: AcademyViewModel) {
    Text("Mock Test Hub")
}


@Composable
fun AnalyticsStatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(label, fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
fun AdminNotificationAlerts(viewModel: AcademyViewModel) {
    val context = LocalContext.current
    var alertTitle by remember { mutableStateOf("") }
    var alertMsg by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Broadcast Push Alerts", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = alertTitle,
            onValueChange = { alertTitle = it },
            label = { Text("Alert Heading") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSending,
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = alertMsg,
            onValueChange = { alertMsg = it },
            label = { Text("Detailed Message") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            enabled = !isSending
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val title = alertTitle.trim()
                val message = alertMsg.trim()
                if (title.isBlank() || message.isBlank()) {
                    Toast.makeText(context, "Please fill in both Alert Heading and Detailed Message", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                isSending = true
                scope.launch {
                    val success = viewModel.adminSendGlobalNotification(title, message)
                    isSending = false
                    if (success) {
                        Toast.makeText(context, "Notification Sent Successfully", Toast.LENGTH_SHORT).show()
                        alertTitle = ""
                        alertMsg = ""
                    } else {
                        Toast.makeText(context, "Failed to send notification. Please check connection.", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            enabled = !isSending && alertTitle.isNotBlank() && alertMsg.isNotBlank(),
            modifier = Modifier.align(Alignment.End)
        ) {
            if (isSending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sending...")
            } else {
                Text("Send Global Notification")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminBannerManager(viewModel: AcademyViewModel) {
    val banners by viewModel.allBanners.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedBannerId by remember { mutableStateOf<Int?>(null) }
    var bTitle by remember { mutableStateOf("") }
    var bUrl by remember { mutableStateOf("") }
    var bLink by remember { mutableStateOf("") }
    var bButtonText by remember { mutableStateOf("VIEW NOW") }
    var bDescription by remember { mutableStateOf("") }
    var bIsActive by remember { mutableStateOf(true) }
    var bDisplayOrder by remember { mutableStateOf("0") }
    var isUploadingImage by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isUploadingImage = true
                try {
                    val publicUrl = viewModel.uploadBannerImage(context, uri)
                    bUrl = publicUrl
                    Toast.makeText(context, "Image uploaded successfully to Supabase Storage!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isUploadingImage = false
                }
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Manage Carousel Ad Banners & Announcements", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = bTitle, onValueChange = { bTitle = it }, label = { Text("Banner Title") }, modifier = Modifier.fillMaxWidth())
            
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = bUrl, 
                    onValueChange = { bUrl = it }, 
                    label = { Text("Image Path/URL") }, 
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (isUploadingImage) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                } else {
                    Button(onClick = { launcher.launch("image/*") }) {
                        Text("Upload\nImage", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
            
            if (bUrl.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Selected Thumbnail Preview:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                val resolvedPreview = com.example.service.MediaStorageServiceFactory.getService(context).resolveMediaUrl(bUrl)
                val previewModel = if (resolvedPreview.startsWith("/")) java.io.File(resolvedPreview) else resolvedPreview
                AsyncImage(
                    model = previewModel,
                    contentDescription = "Selected Banner Image Preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.LightGray),
                    contentScale = ContentScale.Crop
                )
            }
            
            OutlinedTextField(value = bLink, onValueChange = { bLink = it }, label = { Text("Target Link (COURSES or URL)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            OutlinedTextField(value = bButtonText, onValueChange = { bButtonText = it }, label = { Text("Button Text (e.g. JOIN NOW)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            OutlinedTextField(value = bDescription, onValueChange = { bDescription = it }, label = { Text("Banner Description") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = bDisplayOrder, 
                    onValueChange = { bDisplayOrder = it }, 
                    label = { Text("Display Order") }, 
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = bIsActive, onCheckedChange = { bIsActive = it })
                    Text("Is Active")
                }
            }


            Box(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                    if (selectedBannerId != null) {
                        TextButton(onClick = {
                            selectedBannerId = null
                            bTitle = ""
                            bUrl = ""
                            bLink = ""
                            bButtonText = "VIEW NOW"
                            bDescription = ""
                            bIsActive = true
                            bDisplayOrder = "0"
                        }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Button(
                        onClick = { 
                            if (bTitle.isNotBlank()) {
                                if (selectedBannerId != null) {
                                    viewModel.adminUpdateBanner(
                                        id = selectedBannerId!!,
                                        title = bTitle,
                                        imageUrl = bUrl,
                                        linkUrl = if(bLink.isBlank()) "COURSES" else bLink,
                                        buttonText = bButtonText,
                                        description = bDescription,
                                        isActive = bIsActive,
                                        displayOrder = bDisplayOrder.toIntOrNull() ?: 0
                                    )
                                    Toast.makeText(context, "Banner updated successfully!", Toast.LENGTH_SHORT).show()
                                    android.util.Log.d("BannerAdmin", "Database Saved Successfully")
                                } else {
                                    viewModel.adminAddBanner(
                                        title = bTitle,
                                        imageUrl = bUrl,
                                        linkUrl = if(bLink.isBlank()) "COURSES" else bLink,
                                        buttonText = bButtonText,
                                        description = bDescription,
                                        isActive = bIsActive,
                                        displayOrder = bDisplayOrder.toIntOrNull() ?: 0
                                    )
                                    Toast.makeText(context, "Banner added successfully!", Toast.LENGTH_SHORT).show()
                                    android.util.Log.d("BannerAdmin", "Database Saved Successfully")
                                }
                                selectedBannerId = null
                                bTitle = ""
                                bUrl = ""
                                bLink = ""
                                bButtonText = "VIEW NOW"
                                bDescription = ""
                                bIsActive = true
                                bDisplayOrder = "0"
                            }
                        }, 
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(if (selectedBannerId != null) "Update Banner" else "Add Banner")
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("Active Banners (${banners.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
        }
        items(banners, key = { it.id }) { banner ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                ListItem(
                    headlineContent = { Text(banner.title, fontWeight = FontWeight.SemiBold) }, 
                    supportingContent = { 
                        Column {
                            Text("Link: ${banner.linkUrl}", fontSize = 12.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Image: ${banner.imageUrl}", fontSize = 11.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (banner.description.isNotBlank()) {
                                Text("Desc: ${banner.description}", fontSize = 11.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text("Order: ${banner.displayOrder} | Active: ${banner.isActive}", fontSize = 11.sp, color = Color.Gray)
                        }
                    },
                    leadingContent = {
                        val resolvedUrl = com.example.service.MediaStorageServiceFactory.getService(context).resolveMediaUrl(banner.imageUrl)
                        val imgModel = if (resolvedUrl.startsWith("/")) java.io.File(resolvedUrl) else resolvedUrl
                        AsyncImage(
                            model = imgModel,
                            contentDescription = "Banner Thumbnail",
                            modifier = Modifier
                                .size(80.dp, 48.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.LightGray),
                            contentScale = ContentScale.Crop
                        )
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { 
                                selectedBannerId = banner.id
                                bTitle = banner.title
                                bUrl = banner.imageUrl
                                bLink = banner.linkUrl
                                bButtonText = banner.buttonText
                                bDescription = banner.description
                                bIsActive = banner.isActive
                                bDisplayOrder = banner.displayOrder.toString()
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Banner", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { viewModel.adminDeleteBanner(banner.id) }) { 
                                Icon(Icons.Default.Delete, contentDescription = "Delete Banner", tint = Color.Red) 
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}

val TEST_CATEGORIES = listOf(
    "Weekly Test",
    "Monthly Test",
    "Chapter Test",
    "Unit Test",
    "Practice Test",
    "Full Syllabus Test",
    "Model Paper",
    "Previous Year Paper",
    "Competitive Mock Test"
)

fun getTestCategory(test: TestEntity): String {
    val typeLower = test.type.lowercase()
    val titleLower = test.title.lowercase()
    
    return when {
        typeLower.contains("weekly") || titleLower.contains("weekly") -> "Weekly Test"
        typeLower.contains("monthly") || titleLower.contains("monthly") -> "Monthly Test"
        typeLower.contains("chapter") || titleLower.contains("chapter") -> "Chapter Test"
        typeLower.contains("unit") || titleLower.contains("unit") -> "Unit Test"
        typeLower.contains("practice") || titleLower.contains("practice") -> "Practice Test"
        typeLower.contains("full syllabus") || titleLower.contains("full syllabus") -> "Full Syllabus Test"
        typeLower.contains("model paper") || titleLower.contains("model paper") -> "Model Paper"
        typeLower.contains("previous year") || typeLower.contains("pyq") || titleLower.contains("previous year") || titleLower.contains("pyq") -> "Previous Year Paper"
        typeLower.contains("competitive") || typeLower.contains("mock") || titleLower.contains("mock") -> "Competitive Mock Test"
        test.type.isNotBlank() && !test.type.contains("Draft", ignoreCase = true) && !test.type.contains("Unpublished", ignoreCase = true) -> test.type
        else -> "Competitive Mock Test"
    }
}

fun isTestPublished(test: TestEntity): Boolean {
    val typeLower = test.type.lowercase()
    return !typeLower.contains("unpublished") && !typeLower.contains("draft")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminTestSeriesManager(viewModel: AcademyViewModel) {
    val context = LocalContext.current
    val testsRaw by viewModel.allTests.collectAsStateWithLifecycle()
    
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var showGenerator20 by remember { mutableStateOf(false) }
    var showAutoWeeklyDialog by remember { mutableStateOf(false) }
    var deletingTestIds by remember { mutableStateOf(setOf<Int>()) }

    if (showGenerator20) {
        AdminMockTestGenerator20Screen(viewModel = viewModel, onBack = { showGenerator20 = false })
    } else {
        val filteredTests = remember(testsRaw, selectedCategoryFilter, searchQuery) {
            testsRaw.filter { test ->
                val cat = getTestCategory(test)
                val matchesCategory = (selectedCategoryFilter == "All") || (cat == selectedCategoryFilter)
                val matchesSearch = searchQuery.isBlank() ||
                        test.title.contains(searchQuery, ignoreCase = true) ||
                        cat.contains(searchQuery, ignoreCase = true) ||
                        test.type.contains(searchQuery, ignoreCase = true)
                matchesCategory && matchesSearch
            }
        }

        val totalTests = testsRaw.size
        val publishedCount = testsRaw.count { isTestPublished(it) }
        val draftCount = totalTests - publishedCount

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header / Control Center Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Test Series Management", fontWeight = FontWeight.Black, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                                Text("Manage All 9 Test Categories from One Hub", fontSize = 12.sp, color = Color.Gray)
                            }
                            IconButton(onClick = { showAutoWeeklyDialog = true }) {
                                Icon(Icons.Default.Settings, contentDescription = "Scheduler Settings", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Summary Badges Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Total", fontSize = 10.sp, color = Color.Gray)
                                    Text("$totalTests", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.15f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Published", fontSize = 10.sp, color = Color(0xFF047857))
                                    Text("$publishedCount", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF047857))
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Drafts", fontSize = 10.sp, color = Color(0xFFB45309))
                                    Text("$draftCount", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFB45309))
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showGenerator20 = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create Test", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { showAutoWeeklyDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Auto Generator", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search tests by name or category...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            // Category Filter Chips Row
            item {
                Column {
                    Text("Test Categories", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FilterChip(
                                selected = selectedCategoryFilter == "All",
                                onClick = { selectedCategoryFilter = "All" },
                                label = { Text("All (${testsRaw.size})") }
                            )
                        }
                        items(TEST_CATEGORIES) { category ->
                            val catCount = testsRaw.count { getTestCategory(it) == category }
                            FilterChip(
                                selected = selectedCategoryFilter == category,
                                onClick = { selectedCategoryFilter = category },
                                label = { Text("$category ($catCount)") }
                            )
                        }
                    }
                }
            }

            // Test Items Header
            item {
                Text(
                    text = "Manage Tests (${filteredTests.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (filteredTests.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No tests found in this category.", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                }
            } else {
                items(filteredTests) { test ->
                    val published = isTestPublished(test)
                    val category = getTestCategory(test)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = category,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (published) Color(0xFF10B981) else Color(0xFFF59E0B))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (published) "Published" else "Draft",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (published) Color(0xFF10B981) else Color(0xFFF59E0B)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = test.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Duration: ${test.durationMinutes} Mins | Correct: +${test.marksPerCorrect} | Negative: ${if (test.hasNegativeMarking) test.marksPerWrong else "None"}",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (published) {
                                    OutlinedButton(
                                        onClick = {
                                            val newType = "${test.type} - Unpublished"
                                            viewModel.adminUpdateTest(test.copy(type = newType))
                                            ToastHelper.showToast(context, "Test moved to Drafts")
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Unpublish", fontSize = 11.sp)
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            val cleanType = test.type.replace(" - Unpublished", "").replace("Draft - ", "")
                                            val newType = if (cleanType.isBlank()) "Mock Test" else cleanType
                                            viewModel.adminUpdateTest(test.copy(type = newType))
                                            ToastHelper.showToast(context, "Test Published Live!")
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                                    ) {
                                        Text("Publish Live", fontSize = 11.sp)
                                    }
                                }

                                val isDeleting = test.id in deletingTestIds
                                IconButton(
                                    onClick = {
                                        deletingTestIds = deletingTestIds + test.id
                                        viewModel.adminDeleteTest(
                                            id = test.id,
                                            onSuccess = {
                                                deletingTestIds = deletingTestIds - test.id
                                                ToastHelper.showToast(context, "Mock Test deleted successfully.")
                                            },
                                            onError = { errorMsg ->
                                                deletingTestIds = deletingTestIds - test.id
                                                ToastHelper.showToast(context, "Error: $errorMsg")
                                            }
                                        )
                                    },
                                    enabled = !isDeleting
                                ) {
                                    if (isDeleting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.Red
                                        )
                                    } else {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Test", tint = Color.Red)
                                    }
                                }
                                /* Deleted obsolete button */
                                if (false) {
                                    IconButton(onClick = {}) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Test")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Auto Weekly Generator Settings Dialog
        if (showAutoWeeklyDialog) {
            var autoGenEnabled by remember { mutableStateOf(WeeklyMockTestGenerator.isAutoGenerationEnabled(context)) }
            var isGenerating by remember { mutableStateOf(WeeklyMockTestGenerator.isGenerating(context)) }
            var logsText by remember { mutableStateOf(WeeklyMockTestGenerator.getLogs(context)) }
            var selectedClass by remember { mutableStateOf("Class 1") }
            var selectedExam by remember { mutableStateOf("Army GD") }

            LaunchedEffect(isGenerating) {
                while (isGenerating) {
                    delay(2000L)
                    isGenerating = WeeklyMockTestGenerator.isGenerating(context)
                    logsText = WeeklyMockTestGenerator.getLogs(context)
                }
            }

            AlertDialog(
                onDismissRequest = { showAutoWeeklyDialog = false },
                title = { Text("Weekly Auto-Generator Control", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Auto Weekly Scheduler", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Runs background auto-generator weekly", fontSize = 11.sp, color = Color.Gray)
                                }
                                Switch(
                                    checked = autoGenEnabled,
                                    onCheckedChange = {
                                        autoGenEnabled = it
                                        WeeklyMockTestGenerator.setAutoGenerationEnabled(context, it)
                                    }
                                )
                            }
                        }

                        Text("Manual Trigger Actions", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    WeeklyMockTestGenerator.triggerManualGeneration(context, viewModel.repository)
                                    isGenerating = true
                                    Toast.makeText(context, "Weekly Auto Generator Started!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !isGenerating
                            ) {
                                Text("Generate Rotation", fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    WeeklyMockTestGenerator.triggerManualGeneration(context, viewModel.repository, regenerateCurrentWeek = true)
                                    isGenerating = true
                                    Toast.makeText(context, "Regenerating Current Week...", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !isGenerating
                            ) {
                                Text("Force Re-gen", fontSize = 11.sp)
                            }
                        }

                        Text("Target Specific Class / Exam", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(WeeklyMockTestGenerator.SUPPORTED_CLASSES) { cls ->
                                FilterChip(
                                    selected = selectedClass == cls,
                                    onClick = {
                                        selectedClass = cls
                                        WeeklyMockTestGenerator.triggerManualGeneration(context, viewModel.repository, targetSingle = cls)
                                        isGenerating = true
                                    },
                                    label = { Text(cls, fontSize = 11.sp) }
                                )
                            }
                        }

                        Text("Target Exam", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(WeeklyMockTestGenerator.SUPPORTED_EXAMS) { exm ->
                                FilterChip(
                                    selected = selectedExam == exm,
                                    onClick = {
                                        selectedExam = exm
                                        WeeklyMockTestGenerator.triggerManualGeneration(context, viewModel.repository, targetSingle = exm)
                                        isGenerating = true
                                    },
                                    label = { Text(exm, fontSize = 11.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Execution Logs", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = if (logsText.isBlank()) "No logs recorded yet." else logsText,
                                color = Color(0xFF38BDF8),
                                fontSize = 10.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAutoWeeklyDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}


        




@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminMockTestGenerator20Screen(viewModel: AcademyViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Generator Mode: 0 = AI Generator, 1 = Paste Questions
    var selectedModeTab by remember { mutableIntStateOf(0) }
    
    var selectedExam20 by remember { mutableStateOf("Army GD") }
    var selectedDifficulty20 by remember { mutableStateOf("Medium") }
    var targetQuestionCount by remember { mutableIntStateOf(50) }
    var customCountInput by remember { mutableStateOf("") }
    
    // Pasted raw questions input
    var pastedRawText by remember { mutableStateOf("") }
    
    // Metadata Overrides for Test
    var testTitleOverride by remember { mutableStateOf("") }
    var testDurationOverride by remember { mutableStateOf("") }
    var testTotalMarksOverride by remember { mutableStateOf("") }
    var testPassingMarks by remember { mutableStateOf("33") }
    var enableNegativeMarking by remember { mutableStateOf(false) }
    var marksPerWrongInput by remember { mutableStateOf("0.25") }
    var testInstructionsInput by remember { mutableStateOf("Read all questions carefully. Select the best option. All questions are mandatory.") }
    var saveAsDraft by remember { mutableStateOf(false) }
    
    // Generator state variables
    var activeStep by remember { mutableStateOf("") }
    var progressPercent by remember { mutableStateOf(0f) }
    var isGenerating20 by remember { mutableStateOf(false) }
    var generatorError by remember { mutableStateOf<String?>(null) }
    
    var detectedSyllabus by remember { mutableStateOf<List<MockTestGenerator20.SyllabusSubject>>(emptyList()) }
    var generatedQuestions20 by remember { mutableStateOf<List<MockTestGenerator20.GeneratedQuestion>>(emptyList()) }
    
    // State for editing a question
    var editingQuestionIndex by remember { mutableStateOf<Int?>(null) }
    var showEditQuestionDialog by remember { mutableStateOf(false) }
    
    // Publishing state
    var isPublishing by remember { mutableStateOf(false) }
    var publishingStep by remember { mutableStateOf("") }
    var publishingError by remember { mutableStateOf<String?>(null) }
    var showPublishingStatusDialog by remember { mutableStateOf(false) }
    
    val examCategories = remember {
        mapOf(
            "Defense & Forces" to listOf("Army GD", "Army Technical", "Army Nursing Assistant", "Air Force (Group X & Y)", "Navy (SSR / MR)", "SSC GD"),
            "Police & State" to listOf("UP Police Constable", "UP SI", "Delhi Police Constable", "CISF Head Constable", "CRPF Constable", "BSF Tradesman", "ITBP Constable", "SSB Constable"),
            "Railways & JE" to listOf("Railway Group D", "RRB NTPC", "Railway Junior Engineer (JE)"),
            "School Classes (1-12)" to (1..10).map { "Class $it" } + listOf("Class 11 Science", "Class 11 Arts", "Class 11 Commerce", "Class 12 Science", "Class 12 Arts", "Class 12 Commerce"),
            "Competitive & Teaching" to listOf("NEET (Medical)", "JEE (Engineering)", "CUET (UG)", "CTET", "UPTET", "UPSSSC PET")
        )
    }

    if (showPublishingStatusDialog) {
        AlertDialog(
            onDismissRequest = { if (!isPublishing) showPublishingStatusDialog = false },
            title = { Text(if (isPublishing) "Publishing LMS Mock Test..." else "Publish Status") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (isPublishing) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(publishingStep, textAlign = TextAlign.Center, fontSize = 13.sp)
                    } else if (publishingError != null) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Failed to publish mock test.", fontWeight = FontWeight.Bold, color = Color.Red)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(publishingError!!, textAlign = TextAlign.Center, fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Mock Test successfully stored & published live!", fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                    }
                }
            },
            confirmButton = {
                if (!isPublishing) {
                    Button(onClick = { showPublishingStatusDialog = false }) {
                        Text("OK")
                    }
                }
            }
        )
    }

    // Question Edit Dialog
    if (showEditQuestionDialog && editingQuestionIndex != null && editingQuestionIndex!! in generatedQuestions20.indices) {
        val q = generatedQuestions20[editingQuestionIndex!!]
        var qText by remember { mutableStateOf(q.questionText) }
        var optA by remember { mutableStateOf(q.optionA) }
        var optB by remember { mutableStateOf(q.optionB) }
        var optC by remember { mutableStateOf(q.optionC) }
        var optD by remember { mutableStateOf(q.optionD) }
        var corrIdx by remember { mutableIntStateOf(q.correctIndex) }
        var expText by remember { mutableStateOf(q.explanation) }
        var imgUrlText by remember { mutableStateOf(q.imageUrl) }

        AlertDialog(
            onDismissRequest = { showEditQuestionDialog = false },
            title = { Text("Edit Question ${editingQuestionIndex!! + 1}") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = qText,
                        onValueChange = { qText = it },
                        label = { Text("Question Text (Bilingual / Formula)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )
                    OutlinedTextField(
                        value = optA,
                        onValueChange = { optA = it },
                        label = { Text("Option A") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = optB,
                        onValueChange = { optB = it },
                        label = { Text("Option B") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = optC,
                        onValueChange = { optC = it },
                        label = { Text("Option C") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = optD,
                        onValueChange = { optD = it },
                        label = { Text("Option D") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Correct Option:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("A", "B", "C", "D").forEachIndexed { idx, label ->
                            FilterChip(
                                selected = corrIdx == idx,
                                onClick = { corrIdx = idx },
                                label = { Text(label) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = expText,
                        onValueChange = { expText = it },
                        label = { Text("Explanation / Solution") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )

                    OutlinedTextField(
                        value = imgUrlText,
                        onValueChange = { imgUrlText = it },
                        label = { Text("Image URL (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val updatedList = generatedQuestions20.toMutableList()
                    updatedList[editingQuestionIndex!!] = q.copy(
                        questionText = qText,
                        optionA = optA,
                        optionB = optB,
                        optionC = optC,
                        optionD = optD,
                        correctIndex = corrIdx,
                        explanation = expText,
                        imageUrl = imgUrlText
                    )
                    generatedQuestions20 = updatedList
                    showEditQuestionDialog = false
                }) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditQuestionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    when {
        isGenerating20 -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(modifier = Modifier.size(64.dp), color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(24.dp))
                Text("AI Test Generator 2.0", fontWeight = FontWeight.Black, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Exam: $selectedExam20 | Difficulty: $selectedDifficulty20 | Target: $targetQuestionCount Qs", fontSize = 13.sp, color = Color.Gray)
                
                Spacer(modifier = Modifier.height(32.dp))
                LinearProgressIndicator(
                    progress = { progressPercent },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${(progressPercent * 100).toInt()}% Complete", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Target: $targetQuestionCount Questions", fontSize = 12.sp, color = Color.Gray)
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Current Action:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(activeStep, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        
        generatorError != null -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Generation Error", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.Red)
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.05f)),
                    border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = generatorError!!,
                        modifier = Modifier.padding(16.dp),
                        color = Color.Red,
                        fontSize = 12.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { generatorError = null }) {
                    Text("Back & Try Again")
                }
            }
        }
        
        generatedQuestions20.isNotEmpty() -> {
            // Preview & Customize Test Screen
            val totalQs = generatedQuestions20.size
            val defaultDuration = MockTestGenerator20.calculateAutoDuration(totalQs)
            val defaultMarks = MockTestGenerator20.calculateAutoMarks(totalQs)
            val computedTitle = if (testTitleOverride.isNotBlank()) testTitleOverride else "Mock Test - $selectedExam20"
            val computedDuration = testDurationOverride.toIntOrNull() ?: defaultDuration
            val computedMarks = testTotalMarksOverride.toIntOrNull() ?: defaultMarks
            val computedWrongMarks = marksPerWrongInput.toFloatOrNull() ?: 0.25f

            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { generatedQuestions20 = emptyList() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = "LMS Test Preview ($totalQs Questions)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                publishingError = null
                                showPublishingStatusDialog = true
                                isPublishing = true
                                
                                publishingStep = "Validating $totalQs questions..."
                                var validationFailed = false
                                var valErrorMsg = ""
                                for (i in generatedQuestions20.indices) {
                                    val q = generatedQuestions20[i]
                                    if (q.questionText.isBlank() || q.optionA.isBlank() || q.optionB.isBlank()) {
                                        validationFailed = true
                                        valErrorMsg = "Question ${i + 1} has blank text or options."
                                        break
                                    }
                                }
                                
                                if (validationFailed) {
                                    publishingError = "Validation Failed: $valErrorMsg"
                                    isPublishing = false
                                    return@launch
                                }

                                publishingStep = "Publishing Mock Test to Cloud & Local App..."
                                val result = MockTestGenerator20.publishMockTest(
                                    context = context,
                                    repository = viewModel.repository,
                                    title = computedTitle,
                                    examName = selectedExam20,
                                    difficulty = selectedDifficulty20,
                                    questions = generatedQuestions20,
                                    durationMinutes = computedDuration,
                                    marksPerCorrect = 1,
                                    marksPerWrong = if (enableNegativeMarking) computedWrongMarks else 0f,
                                    hasNegativeMarking = enableNegativeMarking,
                                    isDraft = saveAsDraft,
                                    instructions = testInstructionsInput,
                                    subject = selectedExam20,
                                    targetClass = selectedExam20,
                                    examCategory = selectedExam20,
                                    onStepUpdate = { step -> publishingStep = step }
                                )
                                
                                if (result.isSuccess) {
                                    viewModel.syncFromRemote()
                                    Toast.makeText(context, "Mock Test Published successfully!", Toast.LENGTH_LONG).show()
                                    generatedQuestions20 = emptyList()
                                    onBack()
                                    showPublishingStatusDialog = false
                                } else {
                                    publishingError = result.exceptionOrNull()?.localizedMessage ?: "Publishing failed"
                                }
                                isPublishing = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (saveAsDraft) "Save Draft" else "Publish Live", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Test Parameters Configuration Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Test Configuration & Settings", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        
                        OutlinedTextField(
                            value = computedTitle,
                            onValueChange = { testTitleOverride = it },
                            label = { Text("Mock Test Title") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = if (testDurationOverride.isNotBlank()) testDurationOverride else defaultDuration.toString(),
                                onValueChange = { testDurationOverride = it },
                                label = { Text("Duration (Mins)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = if (testTotalMarksOverride.isNotBlank()) testTotalMarksOverride else defaultMarks.toString(),
                                onValueChange = { testTotalMarksOverride = it },
                                label = { Text("Total Marks") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Negative Marking:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Switch(
                                checked = enableNegativeMarking,
                                onCheckedChange = { enableNegativeMarking = it }
                            )
                            if (enableNegativeMarking) {
                                OutlinedTextField(
                                    value = marksPerWrongInput,
                                    onValueChange = { marksPerWrongInput = it },
                                    label = { Text("Deduct / Q") },
                                    modifier = Modifier.width(100.dp),
                                    singleLine = true
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Status:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = !saveAsDraft,
                                    onClick = { saveAsDraft = false },
                                    label = { Text("Publish Live") }
                                )
                                FilterChip(
                                    selected = saveAsDraft,
                                    onClick = { saveAsDraft = true },
                                    label = { Text("Save Draft") }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Question List Preview
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(generatedQuestions20) { index, question ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Q${index + 1}: ${question.subject} > ${question.chapter}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        IconButton(
                                            onClick = { 
                                                editingQuestionIndex = index
                                                showEditQuestionDialog = true
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit Question",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                val mutableQList = generatedQuestions20.toMutableList()
                                                mutableQList.removeAt(index)
                                                generatedQuestions20 = mutableQList
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remove Question",
                                                tint = Color.Red,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text(
                                    text = question.questionText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                val optionColor = { idx: Int ->
                                    if (question.correctIndex == idx) Color(0xFF10B981) else Color.Unspecified
                                }
                                val optionWeight = { idx: Int ->
                                    if (question.correctIndex == idx) FontWeight.Bold else FontWeight.Normal
                                }
                                
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("A) ${question.optionA}", fontSize = 13.sp, color = optionColor(0), fontWeight = optionWeight(0))
                                    Text("B) ${question.optionB}", fontSize = 13.sp, color = optionColor(1), fontWeight = optionWeight(1))
                                    Text("C) ${question.optionC}", fontSize = 13.sp, color = optionColor(2), fontWeight = optionWeight(2))
                                    Text("D) ${question.optionD}", fontSize = 13.sp, color = optionColor(3), fontWeight = optionWeight(3))
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text("Explanation:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Text(question.explanation, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        else -> {
            // Setup Screen: Select Mode (AI vs Paste) & Options
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Mock Test Generator 2.0", fontWeight = FontWeight.Black, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
                            Text("Create & Publish Production LMS Mock Tests", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }

                // Mode Tabs: AI Generator vs Paste Questions
                item {
                    TabRow(selectedTabIndex = selectedModeTab) {
                        Tab(
                            selected = selectedModeTab == 0,
                            onClick = { selectedModeTab = 0 },
                            text = { Text("AI Generator", fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        Tab(
                            selected = selectedModeTab == 1,
                            onClick = { selectedModeTab = 1 },
                            text = { Text("Paste Questions", fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Default.Create, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }

                // Target Exam Selection Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Target Exam / Course", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Selected: $selectedExam20", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            examCategories.forEach { (categoryName, exams) ->
                                Text(categoryName, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(exams) { exm ->
                                        FilterChip(
                                            selected = selectedExam20 == exm,
                                            onClick = { selectedExam20 = exm },
                                            label = { Text(exm) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (selectedModeTab == 0) {
                    // AI Generator Settings
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Difficulty Level", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    listOf("Easy", "Medium", "Hard").forEach { diff ->
                                        FilterChip(
                                            selected = selectedDifficulty20 == diff,
                                            onClick = { selectedDifficulty20 = diff },
                                            label = { Text(diff) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Select Number of Questions", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(listOf(10, 20, 30, 50, 100, 200, 500)) { qCount ->
                                        FilterChip(
                                            selected = targetQuestionCount == qCount && customCountInput.isBlank(),
                                            onClick = {
                                                targetQuestionCount = qCount
                                                customCountInput = ""
                                            },
                                            label = { Text("$qCount Qs") }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = customCountInput,
                                    onValueChange = {
                                        customCountInput = it
                                        val parsed = it.toIntOrNull()
                                        if (parsed != null && parsed > 0) {
                                            targetQuestionCount = parsed
                                        }
                                    },
                                    label = { Text("Or Enter Custom Question Count") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                isGenerating20 = true
                                generatorError = null
                                progressPercent = 0.05f
                                activeStep = "Analyzing official syllabus for $selectedExam20..."
                                
                                scope.launch {
                                    try {
                                        val syllabus = MockTestGenerator20.detectSyllabus(context, selectedExam20)
                                        detectedSyllabus = syllabus
                                        progressPercent = 0.15f
                                        
                                        val questions = MockTestGenerator20.generateQuestionBatch(
                                            context = context,
                                            examName = selectedExam20,
                                            difficulty = selectedDifficulty20,
                                            syllabus = syllabus,
                                            count = targetQuestionCount,
                                            existingQuestions = emptyList(),
                                            onProgressUpdate = { current, total, status ->
                                                activeStep = status
                                                progressPercent = 0.15f + (0.80f * (current.toFloat() / total.coerceAtLeast(1).toFloat()))
                                            }
                                        )

                                        if (questions.isEmpty()) {
                                            throw Exception("AI returned empty question set.")
                                        }

                                        generatedQuestions20 = questions
                                        progressPercent = 1f
                                        activeStep = "Generation Complete!"
                                    } catch (e: Exception) {
                                        Log.e("AdminScreens", "Error generating test", e)
                                        generatorError = "Failed at step: $activeStep\nError Details: ${e.localizedMessage ?: e.message}"
                                    } finally {
                                        isGenerating20 = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate $targetQuestionCount MCQs with AI", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Paste Questions Mode
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Paste Questions Raw Text", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    "Paste any test text from ChatGPT, books, or word files. Supports formats like 1. Q, A/B/C/D options, Ans: A, Explanation.",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )

                                OutlinedTextField(
                                    value = pastedRawText,
                                    onValueChange = { pastedRawText = it },
                                    label = { Text("Paste Questions Text Here...") },
                                    modifier = Modifier.fillMaxWidth().height(220.dp),
                                    maxLines = 20
                                )

                                Button(
                                    onClick = {
                                        if (pastedRawText.isBlank()) {
                                            Toast.makeText(context, "Please paste questions text first.", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        val parsed = SmartQuestionParser.parsePastedQuestions(
                                            rawText = pastedRawText,
                                            defaultSubject = selectedExam20,
                                            defaultDifficulty = selectedDifficulty20
                                        )
                                        if (parsed.isEmpty()) {
                                            Toast.makeText(context, "Could not detect valid questions with 4 options in pasted text.", Toast.LENGTH_LONG).show()
                                        } else {
                                            generatedQuestions20 = parsed
                                            Toast.makeText(context, "Successfully parsed ${parsed.size} questions!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Parse & Load Questions", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun isDuplicateQuestion(q1: MockTestGenerator20.GeneratedQuestion, q2: MockTestGenerator20.GeneratedQuestion): Boolean {
    val cand = QuestionDeduplicator.toNormalizedQuestion(q1)
    val exist = QuestionDeduplicator.toNormalizedQuestion(q2)
    return QuestionDeduplicator.isDuplicate(cand, exist)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminCommunityPopupScreen(viewModel: AcademyViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val popupConfig = viewModel.communityPopupConfig

    // Fetch latest config from remote on launch
    LaunchedEffect(Unit) {
        viewModel.fetchCommunityPopup()
    }

    var enabled by remember(popupConfig.id) { mutableStateOf(popupConfig.enabled) }
    var title by remember(popupConfig.id) { mutableStateOf(popupConfig.title) }
    var description by remember(popupConfig.id) { mutableStateOf(popupConfig.description) }
    var imageUrl by remember(popupConfig.id) { mutableStateOf(popupConfig.imageUrl) }
    var whatsappUrl by remember(popupConfig.id) { mutableStateOf(popupConfig.whatsappUrl) }
    var telegramUrl by remember(popupConfig.id) { mutableStateOf(popupConfig.telegramUrl) }

    var isUploadingImage by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isUploadingImage = true
                try {
                    val publicUrl = viewModel.uploadBannerImage(context, uri)
                    if (publicUrl.isNotBlank()) {
                        imageUrl = publicUrl
                        Toast.makeText(context, "Image uploaded successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Image upload failed. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Image upload error: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isUploadingImage = false
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Community Popup Manager",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Configure the official community popup displayed automatically on the Student Home Screen.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Enable/Disable Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Community Popup",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = if (enabled) "Popup will automatically show on Home Screen" else "Popup is currently hidden",
                                fontSize = 12.sp,
                                color = if (enabled) Color(0xFF16A34A) else Color.Gray
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { enabled = it }
                        )
                    }

                    HorizontalDivider()

                    // Title
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Popup Title") },
                        placeholder = { Text("SHADOWXRAHUL") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Description
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Popup Description") },
                        placeholder = { Text("Join our Official Community...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Image Upload / URL
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Popup Top Image",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )

                        if (imageUrl.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                val resolvedImg = com.example.service.MediaStorageServiceFactory.getService(context).resolveMediaUrl(imageUrl)
                                AsyncImage(
                                    model = resolvedImg,
                                    contentDescription = "Popup Image Preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                IconButton(
                                    onClick = { imageUrl = "" },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { imagePickerLauncher.launch("image/*") },
                                enabled = !isUploadingImage,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (isUploadingImage) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Uploading...")
                                } else {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Upload Image")
                                }
                            }
                        }

                        OutlinedTextField(
                            value = imageUrl,
                            onValueChange = { imageUrl = it },
                            label = { Text("Image URL (Direct link)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    HorizontalDivider()

                    // WhatsApp URL
                    OutlinedTextField(
                        value = whatsappUrl,
                        onValueChange = { whatsappUrl = it },
                        label = { Text("WhatsApp Channel Link") },
                        placeholder = { Text("https://whatsapp.com/channel/...") },
                        leadingIcon = { Icon(Icons.Default.Chat, contentDescription = null, tint = Color(0xFF25D366)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Telegram URL
                    OutlinedTextField(
                        value = telegramUrl,
                        onValueChange = { telegramUrl = it },
                        label = { Text("Telegram Channel Link") },
                        placeholder = { Text("https://t.me/...") },
                        leadingIcon = { Icon(Icons.Default.Send, contentDescription = null, tint = Color(0xFF0088CC)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Save Button
                    Button(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                val updatedConfig = popupConfig.copy(
                                    enabled = enabled,
                                    title = title.trim(),
                                    description = description.trim(),
                                    imageUrl = imageUrl.trim(),
                                    whatsappUrl = whatsappUrl.trim(),
                                    telegramUrl = telegramUrl.trim(),
                                    updatedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(java.util.Date())
                                )
                                val success = viewModel.updateCommunityPopupConfig(updatedConfig)
                                isSaving = false
                                if (success) {
                                    Toast.makeText(context, "Community Popup settings saved successfully!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Saved locally. Supabase sync will retry when online.", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Saving...")
                        } else {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Popup Settings", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

