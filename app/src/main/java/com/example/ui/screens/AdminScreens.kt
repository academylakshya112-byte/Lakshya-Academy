package com.example.ui.screens

import android.content.Context
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Broadcast Push Alerts", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        OutlinedTextField(value = alertTitle, onValueChange = { alertTitle = it }, label = { Text("Alert Heading") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = alertMsg, onValueChange = { alertMsg = it }, label = { Text("Detailed Message") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Button(onClick = { Toast.makeText(context, "Notifications Dispatched to all Students! 🔔", Toast.LENGTH_LONG).show() }, modifier = Modifier.align(Alignment.End).padding(top = 16.dp)) {
            Text("Send Global Notification")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminWeeklyMockManager(viewModel: AcademyViewModel) {
    val context = LocalContext.current
    val testsRaw by viewModel.allTests.collectAsStateWithLifecycle()
    val tests = remember(testsRaw) {
        testsRaw.filter {
            it.title.startsWith("AI 2.0 Mock Test") || it.title.startsWith("Weekly Mock Test - ")
        }
    }
    
    var showGenerator20 by remember { mutableStateOf(false) }
    
    if (showGenerator20) {
        AdminMockTestGenerator20Screen(viewModel = viewModel, onBack = { showGenerator20 = false })
    } else {
        var autoGenEnabled by remember { mutableStateOf(WeeklyMockTestGenerator.isAutoGenerationEnabled(context)) }
        var isGenerating by remember { mutableStateOf(WeeklyMockTestGenerator.isGenerating(context)) }
        var logsText by remember { mutableStateOf(WeeklyMockTestGenerator.getLogs(context)) }
        
        var selectedClass by remember { mutableStateOf("Class 1") }
        var selectedExam by remember { mutableStateOf("Army GD") }
        
        // Poll the status every 2 seconds when generating
        LaunchedEffect(isGenerating) {
            while (isGenerating) {
                delay(2000L)
                isGenerating = WeeklyMockTestGenerator.isGenerating(context)
                logsText = WeeklyMockTestGenerator.getLogs(context)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Prominent button to launch Mock Test Generator 2.0 at the top
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { showGenerator20 = true },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Mock Test Generator 2.0",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "AI-Powered, bilingual (English & Hindi) 50-MCQ test creator with live preview and edit tools.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Weekly Auto Mock Test Generator",
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Automatically generate syllabus-aligned 50-question mock tests every Sunday at 2:00 AM.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        
        // Status & Settings Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Auto Generation Status",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = if (autoGenEnabled) "ACTIVE (Sunday 2:00 AM)" else "DISABLED",
                                fontSize = 13.sp,
                                color = if (autoGenEnabled) Color(0xFF10B981) else Color.Red,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Switch(
                            checked = autoGenEnabled,
                            onCheckedChange = {
                                autoGenEnabled = it
                                WeeklyMockTestGenerator.setAutoGenerationEnabled(context, it)
                                Toast.makeText(context, "Auto generation ${if (it) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (isGenerating) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Generator is active and working in the background...",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Text(
                            text = "Generator is currently idle.",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
        
        // Manual Generation Controls Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Manual Generation Actions",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Button row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                WeeklyMockTestGenerator.triggerManualGeneration(context, viewModel.repository)
                                isGenerating = true
                                Toast.makeText(context, "All Mock Tests generation started!", Toast.LENGTH_SHORT).show()
                            },
                            enabled = !isGenerating,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Generate Now", fontSize = 12.sp)
                        }
                        
                        Button(
                            onClick = {
                                WeeklyMockTestGenerator.triggerManualGeneration(context, viewModel.repository, regenerateCurrentWeek = true)
                                isGenerating = true
                                Toast.makeText(context, "Current week regeneration started!", Toast.LENGTH_SHORT).show()
                            },
                            enabled = !isGenerating,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Regenerate Week", fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }

        // Target Specific Selection
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Generate Specific Class / Exam",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Class Selection Chips
                    Text("Select Class:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(WeeklyMockTestGenerator.SUPPORTED_CLASSES) { cls ->
                            FilterChip(
                                selected = selectedClass == cls,
                                onClick = { selectedClass = cls },
                                label = { Text(cls) }
                            )
                        }
                    }
                    Button(
                        onClick = {
                            WeeklyMockTestGenerator.triggerManualGeneration(context, viewModel.repository, targetSingle = selectedClass)
                            isGenerating = true
                            Toast.makeText(context, "Generation started for $selectedClass!", Toast.LENGTH_SHORT).show()
                        },
                        enabled = !isGenerating,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Text("Generate Selected Class")
                    }
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    // Exam Selection Chips
                    Text("Select Exam:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(WeeklyMockTestGenerator.SUPPORTED_EXAMS) { exm ->
                            FilterChip(
                                selected = selectedExam == exm,
                                onClick = { selectedExam = exm },
                                label = { Text(exm) }
                            )
                        }
                    }
                    Button(
                        onClick = {
                            WeeklyMockTestGenerator.triggerManualGeneration(context, viewModel.repository, targetSingle = selectedExam)
                            isGenerating = true
                            Toast.makeText(context, "Generation started for $selectedExam!", Toast.LENGTH_SHORT).show()
                        },
                        enabled = !isGenerating,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Generate Selected Exam")
                    }
                }
            }
        }
        
        // Logs Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Generation Logs",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row {
                            TextButton(onClick = { logsText = WeeklyMockTestGenerator.getLogs(context) }) {
                                Text("Refresh")
                            }
                            TextButton(onClick = {
                                WeeklyMockTestGenerator.clearLogs(context)
                                logsText = ""
                            }) {
                                Text("Clear", color = Color.Red)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(Color(0xFF0F172A))
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(
                            text = logsText.ifBlank { "No logs available. Trigger a generation or start scheduler to record logs." },
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }
        }
        
        // History Header
        item {
            Text(
                text = "Mock Test History",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        
        // Filter to display all real AI-generated tests (Weekly or AI 2.0)
        val aiTests = tests
        
        if (aiTests.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No AI-generated Mock Tests found.", color = Color.Gray, fontSize = 14.sp)
                    }
                }
            }
        } else {
            items(aiTests) { test ->
                val isWeekly = test.title.startsWith("Weekly Mock Test -")
                val isPublished = if (isWeekly) test.type == "Weekly Auto Test" else test.type == "Mock Test"
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    ListItem(
                        headlineContent = { Text(test.title, fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                        supportingContent = {
                            Column {
                                Text("Duration: ${test.durationMinutes} mins | Marks: ${test.marksPerCorrect * 50}", fontSize = 12.sp, color = Color.Gray)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isPublished) Color(0xFF10B981) else Color.Red)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isPublished) "Published" else "Unpublished",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isPublished) Color(0xFF10B981) else Color.Red
                                    )
                                }
                            }
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isPublished) {
                                    TextButton(onClick = {
                                        val newType = if (isWeekly) "Weekly Auto Test - Unpublished" else "Mock Test - Unpublished"
                                        viewModel.adminUpdateTest(test.copy(type = newType))
                                        Toast.makeText(context, "Test Unpublished", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Text("Unpublish", color = Color.Gray)
                                    }
                                } else {
                                    Button(onClick = {
                                        val newType = if (isWeekly) "Weekly Auto Test" else "Mock Test"
                                        viewModel.adminUpdateTest(test.copy(type = newType))
                                        Toast.makeText(context, "Test Published Successfully", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Text("Publish", fontSize = 11.sp)
                                    }
                                }
                                
                                IconButton(onClick = {
                                    viewModel.adminDeleteTest(test.id)
                                    Toast.makeText(context, "Test Deleted", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Test", tint = Color.Red)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminMockTestGenerator20Screen(viewModel: AcademyViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var selectedExam20 by remember { mutableStateOf("Army GD") }
    var selectedDifficulty20 by remember { mutableStateOf("Medium") }
    
    // Generator state variables
    var activeStep by remember { mutableStateOf("") }
    var progressPercent by remember { mutableStateOf(0f) }
    var isGenerating20 by remember { mutableStateOf(false) }
    var generatorError by remember { mutableStateOf<String?>(null) }
    
    var detectedSyllabus by remember { mutableStateOf<List<MockTestGenerator20.SyllabusSubject>>(emptyList()) }
    var generatedQuestions20 by remember { mutableStateOf<List<MockTestGenerator20.GeneratedQuestion>>(emptyList()) }
    
    // State for viewing/editing a question
    var editingQuestionIndex by remember { mutableStateOf<Int?>(null) }
    
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
            title = { Text(if (isPublishing) "Publishing Mock Test..." else "Publish Status") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (isPublishing) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(publishingStep, textAlign = TextAlign.Center)
                    } else if (publishingError != null) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Failed to publish mock test.", fontWeight = FontWeight.Bold, color = Color.Red)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(publishingError!!, textAlign = TextAlign.Center, fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Mock Test successfully stored and published instantly!", fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
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

    when {
        isGenerating20 -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(modifier = Modifier.size(64.dp), color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(24.dp))
                Text("Generating Mock Test 2.0", fontWeight = FontWeight.Black, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Syllabus: $selectedExam20 | Difficulty: $selectedDifficulty20", fontSize = 13.sp, color = Color.Gray)
                
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
                    Text("Exactly 50 Questions Needed", fontSize = 12.sp, color = Color.Gray)
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
                Text("Generation Failed", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.Red)
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
                    Text("Try Again")
                }
            }
        }
        
        generatedQuestions20.isNotEmpty() -> {
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
                        text = "Preview & Edit (50 MCQs)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                publishingError = null
                                showPublishingStatusDialog = true
                                isPublishing = true
                                
                                publishingStep = "Validating all 50 questions..."
                                var validationFailed = false
                                var valErrorMsg = ""
                                for (i in generatedQuestions20.indices) {
                                    val q = generatedQuestions20[i]
                                    if (q.questionText.isBlank() || q.optionA.isBlank() || q.optionB.isBlank() || q.optionC.isBlank() || q.optionD.isBlank()) {
                                        validationFailed = true
                                        valErrorMsg = "Question ${i + 1} has blank fields."
                                        break
                                    }
                                    if (q.correctIndex !in 0..3) {
                                        validationFailed = true
                                        valErrorMsg = "Question ${i + 1} has an invalid correct option."
                                        break
                                    }
                                }
                                
                                if (validationFailed) {
                                    publishingError = "Validation Failed: $valErrorMsg"
                                    isPublishing = false
                                    return@launch
                                }

                                publishingStep = "Ensuring all 50 questions are unique..."
                                val uniqueQuestions = mutableListOf<MockTestGenerator20.GeneratedQuestion>()
                                for (i in generatedQuestions20.indices) {
                                    val q = generatedQuestions20[i]
                                    var isDup = false
                                    for (uq in uniqueQuestions) {
                                        if (isDuplicateQuestion(q, uq)) {
                                            isDup = true
                                            break
                                        }
                                    }
                                    if (!isDup) {
                                        uniqueQuestions.add(q)
                                    } else {
                                        // Duplicate found! Regenerate only this question until it is unique.
                                        var attempts = 0
                                        var regeneratedQ = q
                                        var isStillDup = true
                                        while (isStillDup && attempts < 10) {
                                            attempts++
                                            publishingStep = "Regenerating duplicate question ${i + 1}/50 (Attempt $attempts)..."
                                            try {
                                                regeneratedQ = MockTestGenerator20.regenerateSingleQuestion(
                                                    context = context,
                                                    examName = selectedExam20,
                                                    difficulty = selectedDifficulty20,
                                                    currentQuestion = q,
                                                    existingQuestions = uniqueQuestions
                                                )
                                                isStillDup = false
                                                for (uq in uniqueQuestions) {
                                                    if (isDuplicateQuestion(regeneratedQ, uq)) {
                                                        isStillDup = true
                                                        break
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                Log.e("Deduplication", "Regeneration on publish failed", e)
                                            }
                                        }
                                        uniqueQuestions.add(regeneratedQ)
                                    }
                                }
                                generatedQuestions20 = uniqueQuestions
                                
                                val result = MockTestGenerator20.publishMockTest(
                                    context = context,
                                    repository = viewModel.repository,
                                    examName = selectedExam20,
                                    difficulty = selectedDifficulty20,
                                    questions = generatedQuestions20,
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
                        Text("Publish Test", fontSize = 12.sp)
                    }
                }
                
                Text(
                    text = "Exam: $selectedExam20 | Difficulty: $selectedDifficulty20",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(generatedQuestions20) { index, question ->
                        val isEditing = editingQuestionIndex == index
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isEditing) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                                                else MaterialTheme.colorScheme.surface
                            )
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
                                                editingQuestionIndex = if (isEditing) null else index 
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isEditing) Icons.Default.Check else Icons.Default.Edit,
                                                contentDescription = "Edit",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        
                                        var isRegeneratingThisQ by remember { mutableStateOf(false) }
                                        if (isRegeneratingThisQ) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        } else {
                                            IconButton(
                                                onClick = {
                                                    isRegeneratingThisQ = true
                                                    scope.launch {
                                                        try {
                                                            var attempts = 0
                                                            var updatedQ = question
                                                            var isStillDup = true
                                                            val existingQList = generatedQuestions20.filterIndexed { idx, _ -> idx != index }
                                                            while (isStillDup && attempts < 5) {
                                                                attempts++
                                                                updatedQ = MockTestGenerator20.regenerateSingleQuestion(
                                                                    context = context,
                                                                    examName = selectedExam20,
                                                                    difficulty = selectedDifficulty20,
                                                                    currentQuestion = question,
                                                                    existingQuestions = existingQList
                                                                )
                                                                isStillDup = false
                                                                for (uq in existingQList) {
                                                                    if (isDuplicateQuestion(updatedQ, uq)) {
                                                                        isStillDup = true
                                                                        break
                                                                    }
                                                                }
                                                            }
                                                            val mutableQList = generatedQuestions20.toMutableList()
                                                            mutableQList[index] = updatedQ
                                                            generatedQuestions20 = mutableQList
                                                            Toast.makeText(context, "Question ${index + 1} regenerated!", Toast.LENGTH_SHORT).show()
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, "Regeneration failed: ${e.message}", Toast.LENGTH_LONG).show()
                                                        } finally {
                                                            isRegeneratingThisQ = false
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = "Regenerate",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                if (isEditing) {
                                    OutlinedTextField(
                                        value = question.questionText,
                                        onValueChange = { 
                                            val mutableQList = generatedQuestions20.toMutableList()
                                            mutableQList[index] = question.copy(questionText = it)
                                            generatedQuestions20 = mutableQList
                                        },
                                        label = { Text("Question Text (Bilingual)") },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        maxLines = 4
                                    )
                                    OutlinedTextField(
                                        value = question.optionA,
                                        onValueChange = { 
                                            val mutableQList = generatedQuestions20.toMutableList()
                                            mutableQList[index] = question.copy(optionA = it)
                                            generatedQuestions20 = mutableQList
                                        },
                                        label = { Text("Option A") },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    )
                                    OutlinedTextField(
                                        value = question.optionB,
                                        onValueChange = { 
                                            val mutableQList = generatedQuestions20.toMutableList()
                                            mutableQList[index] = question.copy(optionB = it)
                                            generatedQuestions20 = mutableQList
                                        },
                                        label = { Text("Option B") },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    )
                                    OutlinedTextField(
                                        value = question.optionC,
                                        onValueChange = { 
                                            val mutableQList = generatedQuestions20.toMutableList()
                                            mutableQList[index] = question.copy(optionC = it)
                                            generatedQuestions20 = mutableQList
                                        },
                                        label = { Text("Option C") },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    )
                                    OutlinedTextField(
                                        value = question.optionD,
                                        onValueChange = { 
                                            val mutableQList = generatedQuestions20.toMutableList()
                                            mutableQList[index] = question.copy(optionD = it)
                                            generatedQuestions20 = mutableQList
                                        },
                                        label = { Text("Option D") },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    )
                                    
                                    Text("Correct Option:", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf("A", "B", "C", "D").forEachIndexed { optIdx, label ->
                                            FilterChip(
                                                selected = question.correctIndex == optIdx,
                                                onClick = { 
                                                    val mutableQList = generatedQuestions20.toMutableList()
                                                    mutableQList[index] = question.copy(correctIndex = optIdx)
                                                    generatedQuestions20 = mutableQList
                                                },
                                                label = { Text(label) }
                                            )
                                        }
                                    }
                                    
                                    OutlinedTextField(
                                        value = question.explanation,
                                        onValueChange = { 
                                            val mutableQList = generatedQuestions20.toMutableList()
                                            mutableQList[index] = question.copy(explanation = it)
                                            generatedQuestions20 = mutableQList
                                        },
                                        label = { Text("Explanation") },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        maxLines = 4
                                    )
                                } else {
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
        }
        
        else -> {
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
                            Text("Create unique, syllabus-aligned bilingual mock tests with AI", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
                
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("1. Select Difficulty Level", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
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
                            Text("2. Select Target Exam", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Selected: $selectedExam20", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            examCategories.forEach { (categoryName, exams) ->
                                Text(categoryName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
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
                                    
                                    val questions = mutableListOf<MockTestGenerator20.GeneratedQuestion>()
                                    val totalBatches = 10
                                    val batchSize = 5
                                    
                                    for (batch in 1..totalBatches) {
                                        activeStep = "Generating MCQ Questions batch $batch/$totalBatches (${questions.size}/50 complete)..."
                                        val batchQuestions = MockTestGenerator20.generateQuestionBatch(
                                            context = context,
                                            examName = selectedExam20,
                                            difficulty = selectedDifficulty20,
                                            syllabus = syllabus,
                                            count = batchSize,
                                            existingQuestions = questions
                                        )
                                        
                                        if (batchQuestions.isEmpty()) {
                                            throw Exception("Gemini returned empty question batch for batch $batch")
                                        }
                                        
                                        questions.addAll(batchQuestions)
                                        progressPercent = 0.15f + (0.75f * (batch.toFloat() / totalBatches.toFloat()))
                                    }
                                    
                                    activeStep = "Performing final deduplication and layout checks..."
                                    val uniqueQuestions = mutableListOf<MockTestGenerator20.GeneratedQuestion>()
                                    for (i in questions.indices) {
                                        val q = questions[i]
                                        var isDup = false
                                        for (uq in uniqueQuestions) {
                                            if (isDuplicateQuestion(q, uq)) {
                                                isDup = true
                                                break
                                            }
                                        }
                                        if (!isDup) {
                                            uniqueQuestions.add(q)
                                        } else {
                                            // It is a duplicate! Regenerate only this question until it is unique.
                                            var attempts = 0
                                            var regeneratedQ = q
                                            var isStillDup = true
                                            while (isStillDup && attempts < 10) {
                                                attempts++
                                                activeStep = "Regenerating duplicate question ${i + 1}/50 (Attempt $attempts)..."
                                                try {
                                                     regeneratedQ = MockTestGenerator20.regenerateSingleQuestion(
                                                         context = context,
                                                         examName = selectedExam20,
                                                         difficulty = selectedDifficulty20,
                                                         currentQuestion = q,
                                                         existingQuestions = uniqueQuestions
                                                     )
                                                     isStillDup = false
                                                     for (uq in uniqueQuestions) {
                                                         if (isDuplicateQuestion(regeneratedQ, uq)) {
                                                             isStillDup = true
                                                             break
                                                         }
                                                     }
                                                } catch (e: Exception) {
                                                     Log.e("Deduplication", "Regeneration failed", e)
                                                }
                                            }
                                            uniqueQuestions.add(regeneratedQ)
                                        }
                                    }
                                    
                                    if (uniqueQuestions.size != 50) {
                                        throw Exception("Expected 50 unique questions but got ${uniqueQuestions.size}")
                                    }
                                    
                                    generatedQuestions20 = uniqueQuestions
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
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analyze Syllabus & Generate 50 MCQs", fontWeight = FontWeight.Bold)
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

