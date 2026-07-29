package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CourseEntity
import com.example.api.R2SupabaseManager
import com.example.api.SupabaseVideo
import com.example.ui.viewmodel.AcademyViewModel
import java.text.SimpleDateFormat
import java.util.*

// Helper metadata parser extensions for CourseEntity
fun CourseEntity.getValidity(): String {
    if (this.description.contains("Validity: ")) {
        return this.description.substringAfter("Validity: ").substringBefore(" |").trim()
    }
    return "Lifetime"
}

fun CourseEntity.isActive(): Boolean {
    if (this.description.contains("Status: ")) {
        val status = this.description.substringAfter("Status: ").substringBefore(" |").trim()
        return status.equals("Active", ignoreCase = true)
    }
    return true
}

fun CourseEntity.getRealDescription(): String {
    if (this.description.contains(" | ")) {
        return this.description.substringAfterLast(" | ").trim()
    }
    return this.description
}

fun buildBatchDescription(validity: String, isActive: Boolean, realDesc: String): String {
    val status = if (isActive) "Active" else "Inactive"
    return "Validity: $validity | Status: $status | $realDesc"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminCourseManager(viewModel: AcademyViewModel) {
    val courses by viewModel.allCourses.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Summary videos list from Supabase videos table (to build Subject/Chapter dynamic hierarchy)
    var summaryVideos by remember { mutableStateOf<List<SupabaseVideo>>(emptyList()) }
    var isLoadingSummary by remember { mutableStateOf(false) }

    fun refreshSummary() {
        isLoadingSummary = true
        R2SupabaseManager.fetchVideos(context) { list, error ->
            isLoadingSummary = false
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

    LaunchedEffect(Unit) {
        android.util.Log.i("AdminCourseManager", "[BATCH SYNC] Admin opened Course Manager. Triggering background batch sync...")
        viewModel.syncFromRemote()
        refreshSummary()
    }

    // Tab Selection: 0 = Publish Wizard, 1 = Batches, 2 = Subjects, 3 = Chapters, 4 = Videos
    var activeTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        Triple("Publish Wizard", Icons.Default.Publish, 0),
        Triple("Manage Batches", Icons.Default.Layers, 1),
        Triple("Manage Subjects", Icons.Default.AutoStories, 2),
        Triple("Manage Chapters", Icons.Default.Folder, 3),
        Triple("Manage Videos", Icons.Default.PlayCircle, 4)
    )

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Academy CMS Center",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Centralized Content Management Panel",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    IconButton(
                        onClick = {
                            refreshSummary()
                            Toast.makeText(context, "CMS Synced & Refreshed", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh CMS")
                    }
                }
                
                ScrollableTabRow(
                    selectedTabIndex = activeTab,
                    edgePadding = 16.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    tabs.forEach { (title, icon, index) ->
                        Tab(
                            selected = activeTab == index,
                            onClick = { activeTab = index },
                            text = { Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                            icon = { Icon(icon, contentDescription = title, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF8FAFC))
        ) {
            when (activeTab) {
                0 -> PublishWizardScreen(combinedCourses, summaryVideos, viewModel, ::refreshSummary)
                1 -> ManageBatchesScreen(combinedCourses, viewModel)
                2 -> ManageSubjectsScreen(combinedCourses, summaryVideos, viewModel, ::refreshSummary)
                3 -> ManageChaptersScreen(combinedCourses, summaryVideos, viewModel, ::refreshSummary)
                4 -> ManageVideosScreen(combinedCourses, summaryVideos, viewModel, ::refreshSummary)
            }
        }
    }
}

// ==========================================
// 1. PUBLISH WIZARD SCREEN (STEP-BY-STEP)
// ==========================================
@Composable
fun PublishWizardScreen(
    courses: List<CourseEntity>,
    summaryVideos: List<SupabaseVideo>,
    viewModel: AcademyViewModel,
    onPublishSuccess: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableIntStateOf(1) }

    // State Variables
    var selectedBatchName by remember { mutableStateOf("") }
    var isNewBatch by remember { mutableStateOf(false) }
    
    // New Batch Fields
    var newBatchName by remember { mutableStateOf("") }
    var newBatchDesc by remember { mutableStateOf("") }
    var newBatchPrice by remember { mutableStateOf("0") }
    var newBatchValidity by remember { mutableStateOf("365 Days") }
    var newBatchActive by remember { mutableStateOf(true) }
    var selectedBatchThumbnailUri by remember { mutableStateOf<Uri?>(null) }
    var selectedBatchThumbnailName by remember { mutableStateOf("") }

    // Subject Fields
    var selectedSubjectName by remember { mutableStateOf("") }
    var isNewSubject by remember { mutableStateOf(false) }
    var newSubjectName by remember { mutableStateOf("") }

    // Chapter Fields
    var selectedChapterName by remember { mutableStateOf("") }
    var isNewChapter by remember { mutableStateOf(false) }
    var newChapterName by remember { mutableStateOf("") }

    // Video & Metadata Fields
    var videoTitle by remember { mutableStateOf("") }
    var videoDesc by remember { mutableStateOf("") }
    var videoDuration by remember { mutableStateOf("30:00") }
    var videoOrder by remember { mutableStateOf("1") }
    var videoVisibility by remember { mutableStateOf(true) }
    
    // File Picking State
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedVideoName by remember { mutableStateOf("") }
    var webVideoUrl by remember { mutableStateOf("") }
    
    var selectedVideoThumbnailUri by remember { mutableStateOf<Uri?>(null) }
    var selectedVideoThumbnailName by remember { mutableStateOf("") }
    
    var selectedPdf1Uri by remember { mutableStateOf<Uri?>(null) }
    var selectedPdf1Name by remember { mutableStateOf("") }
    
    var selectedPdf2Uri by remember { mutableStateOf<Uri?>(null) }
    var selectedPdf2Name by remember { mutableStateOf("") }

    // Progress
    var isPublishing by remember { mutableStateOf(false) }
    var publishProgressText by remember { mutableStateOf("") }

    // Pickers
    val batchThumbnailPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) { e.printStackTrace() }
            selectedBatchThumbnailUri = it
            selectedBatchThumbnailName = getFileNameFromUri(context, it)
        }
    }

    val videoThumbnailPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) { e.printStackTrace() }
            selectedVideoThumbnailUri = it
            selectedVideoThumbnailName = getFileNameFromUri(context, it)
        }
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) { e.printStackTrace() }
            selectedVideoUri = it
            selectedVideoName = getFileNameFromUri(context, it)
        }
    }

    val pdf1Picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) { e.printStackTrace() }
            selectedPdf1Uri = it
            selectedPdf1Name = getFileNameFromUri(context, it)
        }
    }

    val pdf2Picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) { e.printStackTrace() }
            selectedPdf2Uri = it
            selectedPdf2Name = getFileNameFromUri(context, it)
        }
    }

    // Dynamic Lists based on selectors
    val existingBatches = remember(courses) { courses.map { it.title.trim() }.distinct().filter { it.isNotBlank() } }
    
    val existingSubjects = remember(summaryVideos, selectedBatchName) {
        summaryVideos.filter { it.classText.trim().equals(selectedBatchName.trim(), ignoreCase = true) }
            .map { it.subject.trim() }.distinct().filter { it.isNotBlank() }
    }

    val existingChapters = remember(summaryVideos, selectedBatchName, selectedSubjectName) {
        summaryVideos.filter {
            it.classText.trim().equals(selectedBatchName.trim(), ignoreCase = true) &&
            it.subject.trim().equals(selectedSubjectName.trim(), ignoreCase = true)
        }.map { it.chapter.trim() }.distinct().filter { it.isNotBlank() }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Step Indicator Heading
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$currentStep",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        val stepTitle = when (currentStep) {
                            1 -> "Select / Create Batch"
                            2 -> "Select / Create Subject"
                            3 -> "Select / Create Chapter"
                            else -> "Upload Lecture & Resources"
                        }
                        Text(text = "Publish Wizard - Step $currentStep of 4", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                        Text(text = stepTitle, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }

        // ==================== STEP 1: BATCH ====================
        if (currentStep == 1) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Select Target Batch/Class", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        
                        // Select existing batch
                        if (!isNewBatch) {
                            if (existingBatches.isEmpty()) {
                                Text("No batches created yet. Please create a new batch.", color = Color.Gray, fontSize = 12.sp)
                                isNewBatch = true
                            } else {
                                Text("Choose an existing Batch:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                FlowRowHelper(
                                    items = existingBatches,
                                    selectedItem = selectedBatchName,
                                    onSelected = { selectedBatchName = it }
                                )
                            }
                        }

                        if (isNewBatch) {
                            Text("New Batch Information:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            OutlinedTextField(
                                value = newBatchName,
                                onValueChange = { newBatchName = it },
                                label = { Text("Batch Name (e.g. Class 12 Boards)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = newBatchDesc,
                                onValueChange = { newBatchDesc = it },
                                label = { Text("Batch Description") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = newBatchPrice,
                                    onValueChange = { newBatchPrice = it },
                                    label = { Text("Price (₹)") },
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = newBatchValidity,
                                    onValueChange = { newBatchValidity = it },
                                    label = { Text("Validity") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = newBatchActive, onCheckedChange = { newBatchActive = it })
                                Text("Mark Batch as Active / Published", fontSize = 12.sp)
                            }
                            
                            // Batch cover Image Picker
                            Button(
                                onClick = { batchThumbnailPicker.launch(arrayOf("image/*")) },
                                colors = ButtonDefaults.buttonColors(containerColor = if (selectedBatchThumbnailUri != null) Color(0xFF10B981) else Color(0xFF64748B))
                            ) {
                                Icon(Icons.Default.Image, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (selectedBatchThumbnailUri != null) "Cover Selected" else "Select Cover Thumbnail")
                            }
                            if (selectedBatchThumbnailName.isNotEmpty()) {
                                Text("File: $selectedBatchThumbnailName", fontSize = 11.sp, color = Color.Gray)
                            }
                        }

                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { isNewBatch = !isNewBatch }
                            ) {
                                Text(if (isNewBatch) "Use Existing Batch" else "+ Create New Batch")
                            }
                            
                            Button(
                                onClick = {
                                    if (isNewBatch) {
                                        if (newBatchName.isBlank()) {
                                            Toast.makeText(context, "Please enter batch name!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            // Check duplicate batch names
                                            val isDuplicate = existingBatches.any { it.equals(newBatchName.trim(), ignoreCase = true) }
                                            if (isDuplicate) {
                                                Toast.makeText(context, "A batch with this name already exists. Reusing it.", Toast.LENGTH_SHORT).show()
                                                selectedBatchName = newBatchName.trim()
                                                isNewBatch = false
                                                currentStep = 2
                                            } else {
                                                // Register batch in local db
                                                viewModel.adminAddNewCourse(
                                                    title = newBatchName.trim(),
                                                    category = "Class",
                                                    subject = "Mixed",
                                                    desc = buildBatchDescription(newBatchValidity, newBatchActive, newBatchDesc),
                                                    isFree = (newBatchPrice.toDoubleOrNull() ?: 0.0) <= 0.0,
                                                    price = newBatchPrice.toDoubleOrNull() ?: 0.0,
                                                    imageUrl = selectedBatchThumbnailUri?.toString() ?: ""
                                                )
                                                selectedBatchName = newBatchName.trim()
                                                currentStep = 2
                                            }
                                        }
                                    } else {
                                        if (selectedBatchName.isBlank()) {
                                            Toast.makeText(context, "Please select a Batch first!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            currentStep = 2
                                        }
                                    }
                                }
                            ) {
                                Text("Next Step")
                                Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // ==================== STEP 2: SUBJECT ====================
        if (currentStep == 2) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Selected Batch: ", fontSize = 12.sp, color = Color.Gray)
                            Text(selectedBatchName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                        Text("Select Target Subject", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                        if (!isNewSubject) {
                            if (existingSubjects.isEmpty()) {
                                Text("No subjects added to this batch yet. Create a new one below.", color = Color.Gray, fontSize = 12.sp)
                                isNewSubject = true
                            } else {
                                Text("Choose an existing Subject:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                FlowRowHelper(
                                    items = existingSubjects,
                                    selectedItem = selectedSubjectName,
                                    onSelected = { selectedSubjectName = it }
                                )
                            }
                        }

                        if (isNewSubject) {
                            Text("New Subject Information:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            OutlinedTextField(
                                value = newSubjectName,
                                onValueChange = { newSubjectName = it },
                                label = { Text("Subject Name (e.g. Physics, Mathematics)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row {
                                TextButton(onClick = { currentStep = 1 }) {
                                    Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(16.dp))
                                    Text("Back")
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                TextButton(onClick = { isNewSubject = !isNewSubject }) {
                                    Text(if (isNewSubject) "Use Existing" else "+ Create New Subject")
                                }
                            }
                            
                            Button(
                                onClick = {
                                    if (isNewSubject) {
                                        if (newSubjectName.isBlank()) {
                                            Toast.makeText(context, "Please enter subject name!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val isDuplicate = existingSubjects.any { it.equals(newSubjectName.trim(), ignoreCase = true) }
                                            if (isDuplicate) {
                                                Toast.makeText(context, "Subject already exists. Reusing it.", Toast.LENGTH_SHORT).show()
                                                selectedSubjectName = newSubjectName.trim()
                                                isNewSubject = false
                                            } else {
                                                selectedSubjectName = newSubjectName.trim()
                                            }
                                            currentStep = 3
                                        }
                                    } else {
                                        if (selectedSubjectName.isBlank()) {
                                            Toast.makeText(context, "Please select a Subject!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            currentStep = 3
                                        }
                                    }
                                }
                            ) {
                                Text("Next Step")
                                Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // ==================== STEP 3: CHAPTER ====================
        if (currentStep == 3) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Selected Batch & Subject: ", fontSize = 11.sp, color = Color.Gray)
                            Text("$selectedBatchName › $selectedSubjectName", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                        Text("Select Target Chapter", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                        if (!isNewChapter) {
                            if (existingChapters.isEmpty()) {
                                Text("No chapters added to this subject yet. Create a new chapter below.", color = Color.Gray, fontSize = 12.sp)
                                isNewChapter = true
                            } else {
                                Text("Choose an existing Chapter:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                FlowRowHelper(
                                    items = existingChapters,
                                    selectedItem = selectedChapterName,
                                    onSelected = { selectedChapterName = it }
                                )
                            }
                        }

                        if (isNewChapter) {
                            Text("New Chapter Information:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            OutlinedTextField(
                                value = newChapterName,
                                onValueChange = { newChapterName = it },
                                label = { Text("Chapter Name (e.g. Chapter 1: Electrostatics)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row {
                                TextButton(onClick = { currentStep = 2 }) {
                                    Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(16.dp))
                                    Text("Back")
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                TextButton(onClick = { isNewChapter = !isNewChapter }) {
                                    Text(if (isNewChapter) "Use Existing" else "+ Create New Chapter")
                                }
                            }
                            
                            Button(
                                onClick = {
                                    if (isNewChapter) {
                                        if (newChapterName.isBlank()) {
                                            Toast.makeText(context, "Please enter chapter name!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val isDuplicate = existingChapters.any { it.equals(newChapterName.trim(), ignoreCase = true) }
                                            if (isDuplicate) {
                                                Toast.makeText(context, "Chapter already exists. Reusing it.", Toast.LENGTH_SHORT).show()
                                                selectedChapterName = newChapterName.trim()
                                                isNewChapter = false
                                            } else {
                                                selectedChapterName = newChapterName.trim()
                                            }
                                            currentStep = 4
                                        }
                                    } else {
                                        if (selectedChapterName.isBlank()) {
                                            Toast.makeText(context, "Please select a Chapter!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            currentStep = 4
                                        }
                                    }
                                }
                            ) {
                                Text("Next Step")
                                Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // ==================== STEP 4: PUBLISH FORM ====================
        if (currentStep == 4) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Selected Target: ", fontSize = 11.sp, color = Color.Gray)
                            Text("$selectedBatchName › $selectedSubjectName › $selectedChapterName", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                        Text("Lecture Information & Files", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                        OutlinedTextField(
                            value = videoTitle,
                            onValueChange = { videoTitle = it },
                            label = { Text("Video / Lecture Title") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = videoDesc,
                            onValueChange = { videoDesc = it },
                            label = { Text("Video Description / Syllabus Summary") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = videoDuration,
                                onValueChange = { videoDuration = it },
                                label = { Text("Duration") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = videoOrder,
                                onValueChange = { videoOrder = it },
                                label = { Text("Order No.") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = videoVisibility, onCheckedChange = { videoVisibility = it })
                            Text("Publish and Make Visible to Students", fontSize = 12.sp)
                        }

                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                        
                        // 1. Pick Lecture Video
                        Text("1. Select Lecture Video:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        OutlinedTextField(
                            value = webVideoUrl,
                            onValueChange = { webVideoUrl = it },
                            label = { Text("Web URL / YouTube / Drive Video Link (Recommended)") },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("https://...") },
                            leadingIcon = { Icon(Icons.Default.Link, null) }
                        )
                        Text("OR select video file from your device:", fontSize = 11.sp, color = Color.Gray)
                        Button(
                            onClick = { videoPicker.launch(arrayOf("video/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = if (selectedVideoUri != null) Color(0xFF10B981) else Color(0xFF64748B))
                        ) {
                            Icon(Icons.Default.Upload, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (selectedVideoUri != null) "Video Selected" else "Browse MP4 Video File")
                        }
                        if (selectedVideoName.isNotEmpty()) {
                            Text("File: $selectedVideoName", fontSize = 11.sp, color = Color.DarkGray)
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // 2. Pick Video Thumbnail
                        Text("2. Select Video Thumbnail Image:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Button(
                            onClick = { videoThumbnailPicker.launch(arrayOf("image/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = if (selectedVideoThumbnailUri != null) Color(0xFF10B981) else Color(0xFF64748B))
                        ) {
                            Icon(Icons.Default.Image, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (selectedVideoThumbnailUri != null) "Thumbnail Selected" else "Browse Thumbnail Image")
                        }
                        if (selectedVideoThumbnailName.isNotEmpty()) {
                            Text("File: $selectedVideoThumbnailName", fontSize = 11.sp, color = Color.DarkGray)
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // 3. Pick PDF 1 (Handwritten Notes)
                        Text("3. Attach PDF 1 (Handwritten Notes):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Button(
                            onClick = { pdf1Picker.launch(arrayOf("application/pdf")) },
                            colors = ButtonDefaults.buttonColors(containerColor = if (selectedPdf1Uri != null) Color(0xFF10B981) else Color(0xFF64748B))
                        ) {
                            Icon(Icons.Default.Description, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (selectedPdf1Uri != null) "PDF 1 Attached" else "Browse PDF 1 File")
                        }
                        if (selectedPdf1Name.isNotEmpty()) {
                            Text("File: $selectedPdf1Name", fontSize = 11.sp, color = Color.DarkGray)
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // 4. Pick PDF 2 (Board/Class Notes)
                        Text("4. Attach PDF 2 (Board/Class Notes):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Button(
                            onClick = { pdf2Picker.launch(arrayOf("application/pdf")) },
                            colors = ButtonDefaults.buttonColors(containerColor = if (selectedPdf2Uri != null) Color(0xFF10B981) else Color(0xFF64748B))
                        ) {
                            Icon(Icons.Default.BorderColor, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (selectedPdf2Uri != null) "PDF 2 Attached" else "Browse PDF 2 File")
                        }
                        if (selectedPdf2Name.isNotEmpty()) {
                            Text("File: $selectedPdf2Name", fontSize = 11.sp, color = Color.DarkGray)
                        }

                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))

                        if (isPublishing) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = publishProgressText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    enabled = !isPublishing,
                                    onClick = { currentStep = 3 }
                                ) {
                                    Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(16.dp))
                                    Text("Back")
                                }
                                
                                Button(
                                    enabled = !isPublishing,
                                    onClick = {
                                        if (videoTitle.isBlank()) {
                                            Toast.makeText(context, "Please enter lecture title!", Toast.LENGTH_SHORT).show()
                                        } else if (selectedVideoUri == null && webVideoUrl.isBlank()) {
                                            Toast.makeText(context, "Please enter web video URL or pick a video file!", Toast.LENGTH_SHORT).show()
                                        } else if (!com.example.ui.screens.isYouTubeUrl(webVideoUrl) && selectedVideoThumbnailUri == null) {
                                            Toast.makeText(context, "Please select a Thumbnail image file!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            isPublishing = true
                                            publishProgressText = "Publishing content, please wait..."
                                            val activeProvider = com.example.service.MediaStorageServiceFactory.getService(context).getStorageType()
                                            if (activeProvider == "BACKBLAZE_B2") {
                                                com.example.api.BackblazeB2Manager.publishContentUnified(
                                                    context = context,
                                                    videoUri = selectedVideoUri,
                                                    videoUrlInput = webVideoUrl,
                                                    thumbnailUri = selectedVideoThumbnailUri,
                                                    pdf1Uri = selectedPdf1Uri,
                                                    pdf2Uri = selectedPdf2Uri,
                                                    title = videoTitle.trim(),
                                                    classText = selectedBatchName,
                                                    subject = selectedSubjectName,
                                                    chapter = selectedChapterName,
                                                    description = videoDesc.trim(),
                                                    duration = videoDuration.trim(),
                                                    orderNumber = videoOrder.toIntOrNull() ?: 1,
                                                    visibility = videoVisibility,
                                                    onProgress = { progressMsg ->
                                                        publishProgressText = progressMsg
                                                    },
                                                    onSuccess = {
                                                        isPublishing = false
                                                        Toast.makeText(context, "Published Successfully! 🚀", Toast.LENGTH_LONG).show()
                                                        onPublishSuccess()
                                                        // Reset step 4 values
                                                        videoTitle = ""
                                                        videoDesc = ""
                                                        videoDuration = "30:00"
                                                        videoOrder = "1"
                                                        videoVisibility = true
                                                        selectedVideoUri = null
                                                        selectedVideoName = ""
                                                        webVideoUrl = ""
                                                        selectedVideoThumbnailUri = null
                                                        selectedVideoThumbnailName = ""
                                                        selectedPdf1Uri = null
                                                        selectedPdf1Name = ""
                                                        selectedPdf2Uri = null
                                                        selectedPdf2Name = ""
                                                        currentStep = 1
                                                    },
                                                    onError = { errorMsg ->
                                                        isPublishing = false
                                                        Toast.makeText(context, "Publish Failed: $errorMsg", Toast.LENGTH_LONG).show()
                                                    }
                                                )
                                            } else {
                                                R2SupabaseManager.publishContentUnified(
                                                    context = context,
                                                    videoUri = selectedVideoUri,
                                                    videoUrlInput = webVideoUrl,
                                                    thumbnailUri = selectedVideoThumbnailUri,
                                                    pdf1Uri = selectedPdf1Uri,
                                                    pdf2Uri = selectedPdf2Uri,
                                                    title = videoTitle.trim(),
                                                    classText = selectedBatchName,
                                                    subject = selectedSubjectName,
                                                    chapter = selectedChapterName,
                                                    description = videoDesc.trim(),
                                                    duration = videoDuration.trim(),
                                                    orderNumber = videoOrder.toIntOrNull() ?: 1,
                                                    visibility = videoVisibility,
                                                    onProgress = { progressMsg ->
                                                        publishProgressText = progressMsg
                                                    },
                                                    onSuccess = {
                                                        isPublishing = false
                                                        Toast.makeText(context, "Published Successfully! 🚀", Toast.LENGTH_LONG).show()
                                                        onPublishSuccess()
                                                        // Reset step 4 values
                                                        videoTitle = ""
                                                        videoDesc = ""
                                                        videoDuration = "30:00"
                                                        videoOrder = "1"
                                                        videoVisibility = true
                                                        selectedVideoUri = null
                                                        selectedVideoName = ""
                                                        webVideoUrl = ""
                                                        selectedVideoThumbnailUri = null
                                                        selectedVideoThumbnailName = ""
                                                        selectedPdf1Uri = null
                                                        selectedPdf1Name = ""
                                                        selectedPdf2Uri = null
                                                        selectedPdf2Name = ""
                                                        currentStep = 1
                                                    },
                                                    onError = { errorMsg ->
                                                        isPublishing = false
                                                        Toast.makeText(context, "Publish Failed: $errorMsg", Toast.LENGTH_LONG).show()
                                                    }
                                                )
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                                ) {
                                    Icon(Icons.Default.Publish, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Publish Content")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper wrapper flowrow for Compose
@Composable
fun FlowRowHelper(
    items: List<String>,
    selectedItem: String,
    onSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { item ->
            val isSelected = selectedItem.trim().equals(item.trim(), ignoreCase = true)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.4f))
                    .clickable { onSelected(item) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = item,
                    color = if (isSelected) Color.White else Color.DarkGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ==========================================
// 2. MANAGE BATCHES SCREEN
// ==========================================
@Composable
fun ManageBatchesScreen(
    courses: List<CourseEntity>,
    viewModel: AcademyViewModel
) {
    val context = LocalContext.current
    var editingCourse by remember { mutableStateOf<CourseEntity?>(null) }
    
    // Edit Form states
    var editName by remember { mutableStateOf("") }
    var editDesc by remember { mutableStateOf("") }
    var editPrice by remember { mutableStateOf("") }
    var editValidity by remember { mutableStateOf("") }
    var editActive by remember { mutableStateOf(true) }
    var editImageUrl by remember { mutableStateOf("") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) { e.printStackTrace() }
            android.util.Log.i("AdminCourseManager", "[COVER IMAGE SELECT] Selected cover image URI: $it")
            editImageUrl = it.toString()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("All Batches (${courses.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))
            Spacer(modifier = Modifier.height(4.dp))
        }

        items(courses) { batch ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (batch.imageUrl.isNotBlank()) {
                            coil.compose.AsyncImage(
                                model = batch.imageUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF1F5F9)),
                                contentScale = ContentScale.Crop,
                                onState = { state ->
                                    if (state is coil.compose.AsyncImagePainter.State.Error) {
                                        android.util.Log.e("AdminCourseManager", "[IMAGE ERROR] Failed to load: ${batch.imageUrl}", state.result.throwable)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF1F5F9)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.School, null, tint = Color.LightGray, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(batch.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
                                Text("Validity: ${batch.getValidity()} • Price: ₹${batch.price}", fontSize = 12.sp, color = Color.Gray)
                            }
                            
                            // Status badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (batch.isActive()) Color(0xFFDCFCE7) else Color(0xFFFEE2E2))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (batch.isActive()) "Active" else "Inactive",
                                    color = if (batch.isActive()) Color(0xFF15803D) else Color(0xFFB91C1C),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(batch.getRealDescription(), fontSize = 12.sp, color = Color.Gray, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                editingCourse = batch
                                editName = batch.title
                                editDesc = batch.getRealDescription()
                                editPrice = batch.price.toString()
                                editValidity = batch.getValidity()
                                editActive = batch.isActive()
                                editImageUrl = batch.imageUrl
                                android.util.Log.i("AdminCourseManager", "[EDIT BATCH CLICK] Editing batch ID: ${batch.id}, initial imageUrl: ${batch.imageUrl}")
                            }
                        ) {
                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                viewModel.adminDeleteCourse(batch.id)
                                Toast.makeText(context, "Batch deleted!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(14.dp), tint = Color.Red)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete", color = Color.Red)
                        }
                    }
                }
            }
        }
    }

    // Edit Dialog
    if (editingCourse != null) {
        AlertDialog(
            onDismissRequest = { editingCourse = null },
            title = { Text("Edit Batch Details", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("Batch Name") })
                    OutlinedTextField(value = editDesc, onValueChange = { editDesc = it }, label = { Text("Batch Description") }, minLines = 2)
                    OutlinedTextField(value = editPrice, onValueChange = { editPrice = it }, label = { Text("Price (₹)") })
                    OutlinedTextField(value = editValidity, onValueChange = { editValidity = it }, label = { Text("Validity") })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = editActive, onCheckedChange = { editActive = it })
                        Text("Active status", fontSize = 13.sp)
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Cover Image", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (editImageUrl.isNotEmpty()) {
                            coil.compose.AsyncImage(
                                model = editImageUrl,
                                contentDescription = "Cover Image Preview",
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                onState = { state ->
                                    when (state) {
                                        is coil.compose.AsyncImagePainter.State.Success -> {
                                            android.util.Log.i("AdminCourseManager", "[IMAGE LOAD SUCCESS] URL: $editImageUrl")
                                        }
                                        is coil.compose.AsyncImagePainter.State.Error -> {
                                            android.util.Log.e("AdminCourseManager", "[IMAGE LOAD FAILED] URL: $editImageUrl", state.result.throwable)
                                        }
                                        else -> {}
                                    }
                                }
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.LightGray.copy(alpha = 0.3f))
                                    .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, tint = Color.Gray)
                            }
                        }

                        Button(
                            onClick = {
                                android.util.Log.i("AdminCourseManager", "[COVER IMAGE SELECT] Launching image picker...")
                                imagePickerLauncher.launch(arrayOf("image/*"))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Change Cover Image", fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = editPrice.toDoubleOrNull() ?: 0.0
                        android.util.Log.i("AdminCourseManager", "[COVER IMAGE SAVE] Save changes clicked for ID: ${editingCourse!!.id}. Final imageUrl: $editImageUrl")
                        viewModel.adminUpdateCourse(
                            id = editingCourse!!.id,
                            title = editName.trim(),
                            description = buildBatchDescription(editValidity.trim(), editActive, editDesc.trim()),
                            price = p,
                            isFree = p <= 0.0,
                            imageUrl = editImageUrl
                        )
                        Toast.makeText(context, "Batch Updated Successfully!", Toast.LENGTH_SHORT).show()
                        editingCourse = null
                    }
                ) { Text("Save Changes") }
            },
            dismissButton = {
                TextButton(onClick = { editingCourse = null }) { Text("Cancel") }
            }
        )
    }
}

// ==========================================
// 3. MANAGE SUBJECTS SCREEN
// ==========================================
@Composable
fun ManageSubjectsScreen(
    courses: List<CourseEntity>,
    summaryVideos: List<SupabaseVideo>,
    viewModel: AcademyViewModel,
    onRenameDeleteSuccess: () -> Unit
) {
    val context = LocalContext.current
    var selectedBatchName by remember { mutableStateOf("") }
    
    val existingBatches = remember(courses) { courses.map { it.title.trim() }.distinct().filter { it.isNotBlank() } }
    
    val existingSubjects = remember(summaryVideos, selectedBatchName) {
        summaryVideos.filter { it.classText.trim().equals(selectedBatchName.trim(), ignoreCase = true) }
            .map { it.subject.trim() }.distinct().filter { it.isNotBlank() }
    }

    var renamingSubjectOld by remember { mutableStateOf("") }
    var renamingSubjectNew by remember { mutableStateOf("") }
    var isOperating by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Subject Management", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))
            Text("Select a batch to manage its subjects.", fontSize = 12.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(4.dp))
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Choose Batch:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    if (existingBatches.isEmpty()) {
                        Text("No batches found.", fontSize = 12.sp, color = Color.Gray)
                    } else {
                        FlowRowHelper(items = existingBatches, selectedItem = selectedBatchName, onSelected = { selectedBatchName = it })
                    }
                }
            }
        }

        if (selectedBatchName.isNotEmpty()) {
            item {
                Text("Subjects in $selectedBatchName (${existingSubjects.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            if (existingSubjects.isEmpty()) {
                item {
                    Text("No subjects created under this batch yet.", fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
                }
            } else {
                items(existingSubjects) { subject ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(subject, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF334155))
                            
                            Row {
                                IconButton(
                                    onClick = {
                                        renamingSubjectOld = subject
                                        renamingSubjectNew = subject
                                    }
                                ) { Icon(Icons.Default.Edit, "Rename", tint = MaterialTheme.colorScheme.primary) }
                                
                                IconButton(
                                    onClick = {
                                        isOperating = true
                                        R2SupabaseManager.deleteSubject(context, selectedBatchName, subject) { success, err ->
                                            isOperating = false
                                            if (success) {
                                                Toast.makeText(context, "Subject deleted successfully!", Toast.LENGTH_SHORT).show()
                                                onRenameDeleteSuccess()
                                            } else {
                                                Toast.makeText(context, "Deletion failed: $err", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                ) { Icon(Icons.Default.Delete, "Delete", tint = Color.Red) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (renamingSubjectOld.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { renamingSubjectOld = "" },
            title = { Text("Rename Subject", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter new name for Subject '$renamingSubjectOld':", fontSize = 13.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(value = renamingSubjectNew, onValueChange = { renamingSubjectNew = it }, label = { Text("Subject Name") })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renamingSubjectNew.isBlank()) {
                            Toast.makeText(context, "Cannot be empty", Toast.LENGTH_SHORT).show()
                        } else {
                            isOperating = true
                            R2SupabaseManager.renameSubject(context, selectedBatchName, renamingSubjectOld, renamingSubjectNew.trim()) { success, err ->
                                isOperating = false
                                renamingSubjectOld = ""
                                if (success) {
                                    Toast.makeText(context, "Subject renamed globally! 🎉", Toast.LENGTH_SHORT).show()
                                    onRenameDeleteSuccess()
                                } else {
                                    Toast.makeText(context, "Rename failed: $err", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { renamingSubjectOld = "" }) { Text("Cancel") }
            }
        )
    }

    if (isOperating) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

// ==========================================
// 4. MANAGE CHAPTERS SCREEN
// ==========================================
@Composable
fun ManageChaptersScreen(
    courses: List<CourseEntity>,
    summaryVideos: List<SupabaseVideo>,
    viewModel: AcademyViewModel,
    onRenameDeleteSuccess: () -> Unit
) {
    val context = LocalContext.current
    var selectedBatchName by remember { mutableStateOf("") }
    var selectedSubjectName by remember { mutableStateOf("") }

    val existingBatches = remember(courses) { courses.map { it.title.trim() }.distinct().filter { it.isNotBlank() } }
    
    val existingSubjects = remember(summaryVideos, selectedBatchName) {
        summaryVideos.filter { it.classText.trim().equals(selectedBatchName.trim(), ignoreCase = true) }
            .map { it.subject.trim() }.distinct().filter { it.isNotBlank() }
    }

    val existingChapters = remember(summaryVideos, selectedBatchName, selectedSubjectName) {
        summaryVideos.filter {
            it.classText.trim().equals(selectedBatchName.trim(), ignoreCase = true) &&
            it.subject.trim().equals(selectedSubjectName.trim(), ignoreCase = true)
        }.map { it.chapter.trim() }.distinct().filter { it.isNotBlank() }
    }

    var renamingChapterOld by remember { mutableStateOf("") }
    var renamingChapterNew by remember { mutableStateOf("") }
    var isOperating by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Chapter Management", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))
            Text("Select Batch & Subject to load chapters.", fontSize = 12.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Selection card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("1. Choose Batch:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    FlowRowHelper(items = existingBatches, selectedItem = selectedBatchName, onSelected = {
                        selectedBatchName = it
                        selectedSubjectName = ""
                    })

                    if (selectedBatchName.isNotEmpty()) {
                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                        Text("2. Choose Subject:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        if (existingSubjects.isEmpty()) {
                            Text("No subjects found.", fontSize = 11.sp, color = Color.Gray)
                        } else {
                            FlowRowHelper(items = existingSubjects, selectedItem = selectedSubjectName, onSelected = { selectedSubjectName = it })
                        }
                    }
                }
            }
        }

        if (selectedBatchName.isNotEmpty() && selectedSubjectName.isNotEmpty()) {
            item {
                Text("Chapters in $selectedSubjectName (${existingChapters.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            if (existingChapters.isEmpty()) {
                item {
                    Text("No chapters found.", fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
                }
            } else {
                items(existingChapters) { chapter ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(chapter, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF334155), modifier = Modifier.weight(1f))
                            
                            Row {
                                IconButton(
                                    onClick = {
                                        renamingChapterOld = chapter
                                        renamingChapterNew = chapter
                                    }
                                ) { Icon(Icons.Default.Edit, "Rename", tint = MaterialTheme.colorScheme.primary) }
                                
                                IconButton(
                                    onClick = {
                                        isOperating = true
                                        R2SupabaseManager.deleteChapter(context, selectedBatchName, selectedSubjectName, chapter) { success, err ->
                                            isOperating = false
                                            if (success) {
                                                Toast.makeText(context, "Chapter deleted successfully!", Toast.LENGTH_SHORT).show()
                                                onRenameDeleteSuccess()
                                            } else {
                                                Toast.makeText(context, "Deletion failed: $err", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                ) { Icon(Icons.Default.Delete, "Delete", tint = Color.Red) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (renamingChapterOld.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { renamingChapterOld = "" },
            title = { Text("Rename Chapter", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter new name for Chapter '$renamingChapterOld':", fontSize = 13.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(value = renamingChapterNew, onValueChange = { renamingChapterNew = it }, label = { Text("Chapter Name") })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renamingChapterNew.isBlank()) {
                            Toast.makeText(context, "Cannot be empty", Toast.LENGTH_SHORT).show()
                        } else {
                            isOperating = true
                            R2SupabaseManager.renameChapter(context, selectedBatchName, selectedSubjectName, renamingChapterOld, renamingChapterNew.trim()) { success, err ->
                                isOperating = false
                                renamingChapterOld = ""
                                if (success) {
                                    Toast.makeText(context, "Chapter renamed successfully!", Toast.LENGTH_SHORT).show()
                                    onRenameDeleteSuccess()
                                } else {
                                    Toast.makeText(context, "Rename failed: $err", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { renamingChapterOld = "" }) { Text("Cancel") }
            }
        )
    }

    if (isOperating) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

// ==========================================
// 5. MANAGE VIDEOS SCREEN
// ==========================================
@Composable
fun ManageVideosScreen(
    courses: List<CourseEntity>,
    summaryVideos: List<SupabaseVideo>,
    viewModel: AcademyViewModel,
    onVideoDeleteSuccess: () -> Unit
) {
    val context = LocalContext.current
    var selectedBatchName by remember { mutableStateOf("") }
    var selectedSubjectName by remember { mutableStateOf("") }
    var selectedChapterName by remember { mutableStateOf("") }

    // State of actually fetched complete video entries
    var videoListDetailed by remember { mutableStateOf<List<SupabaseVideo>>(emptyList()) }
    var isLoadingDetailed by remember { mutableStateOf(false) }

    fun loadDetailedVideos() {
        if (selectedBatchName.isNotEmpty() && selectedSubjectName.isNotEmpty() && selectedChapterName.isNotEmpty()) {
            isLoadingDetailed = true
            R2SupabaseManager.fetchVideosForChapter(context, selectedBatchName, selectedSubjectName, selectedChapterName) { list, error ->
                isLoadingDetailed = false
                if (list != null) {
                    videoListDetailed = list
                }
            }
        }
    }

    LaunchedEffect(selectedBatchName, selectedSubjectName, selectedChapterName) {
        loadDetailedVideos()
    }

    val existingBatches = remember(courses) { courses.map { it.title.trim() }.distinct().filter { it.isNotBlank() } }
    
    val existingSubjects = remember(summaryVideos, selectedBatchName) {
        summaryVideos.filter { it.classText.trim().equals(selectedBatchName.trim(), ignoreCase = true) }
            .map { it.subject.trim() }.distinct().filter { it.isNotBlank() }
    }

    val existingChapters = remember(summaryVideos, selectedBatchName, selectedSubjectName) {
        summaryVideos.filter {
            it.classText.trim().equals(selectedBatchName.trim(), ignoreCase = true) &&
            it.subject.trim().equals(selectedSubjectName.trim(), ignoreCase = true)
        }.map { it.chapter.trim() }.distinct().filter { it.isNotBlank() }
    }

    // Video Editing Form Modal
    var editingVideo by remember { mutableStateOf<SupabaseVideo?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editDesc by remember { mutableStateOf("") }
    var editDuration by remember { mutableStateOf("") }
    var editOrder by remember { mutableStateOf("") }
    var editVisible by remember { mutableStateOf(true) }
    var isOperating by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Video & Lecture Management", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))
            Text("Select Batch, Subject & Chapter to view lectures.", fontSize = 12.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(4.dp))
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("1. Batch:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    FlowRowHelper(items = existingBatches, selectedItem = selectedBatchName, onSelected = {
                        selectedBatchName = it
                        selectedSubjectName = ""
                        selectedChapterName = ""
                        videoListDetailed = emptyList()
                    })

                    if (selectedBatchName.isNotEmpty()) {
                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                        Text("2. Subject:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        FlowRowHelper(items = existingSubjects, selectedItem = selectedSubjectName, onSelected = {
                            selectedSubjectName = it
                            selectedChapterName = ""
                            videoListDetailed = emptyList()
                        })
                    }

                    if (selectedBatchName.isNotEmpty() && selectedSubjectName.isNotEmpty()) {
                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                        Text("3. Chapter:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        FlowRowHelper(items = existingChapters, selectedItem = selectedChapterName, onSelected = {
                            selectedChapterName = it
                            videoListDetailed = emptyList()
                        })
                    }
                }
            }
        }

        if (selectedBatchName.isNotEmpty() && selectedSubjectName.isNotEmpty() && selectedChapterName.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Videos in $selectedChapterName", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (isLoadingDetailed) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                }
            }

            if (videoListDetailed.isEmpty() && !isLoadingDetailed) {
                item {
                    Text("No videos found in this chapter.", fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
                }
            } else {
                items(videoListDetailed) { video ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(video.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))
                                    Text("Order No: ${video.orderNumber} • Duration: ${video.duration}", fontSize = 11.sp, color = Color.Gray)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (video.visibility) Color(0xFFDCFCE7) else Color(0xFFFEE2E2))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (video.visibility) "Published" else "Hidden",
                                        color = if (video.visibility) Color(0xFF15803D) else Color(0xFFB91C1C),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            if (video.description.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(video.description, fontSize = 12.sp, color = Color.Gray)
                            }

                            // Show linked PDFs summary
                            if (!video.pdfUrl.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFF1F5F9))
                                        .padding(8.dp)
                                ) {
                                    val pdfs = video.pdfUrl.split("|")
                                    Column {
                                        Text("Linked Study Materials:", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color.DarkGray)
                                        pdfs.forEachIndexed { idx, url ->
                                            if (url.isNotBlank()) {
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                                                    Icon(Icons.Default.PictureAsPdf, null, tint = Color(0xFFEF4444), modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("PDF ${idx + 1}: Active Handout", fontSize = 10.sp, color = Color.Gray)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        editingVideo = video
                                        editTitle = video.title
                                        editDesc = video.description
                                        editDuration = video.duration
                                        editOrder = video.orderNumber.toString()
                                        editVisible = video.visibility
                                    }
                                ) {
                                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Edit")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(
                                    onClick = {
                                        isOperating = true
                                        R2SupabaseManager.deleteVideo(context, video.id) { success, err ->
                                            isOperating = false
                                            if (success) {
                                                Toast.makeText(context, "Video Deleted!", Toast.LENGTH_SHORT).show()
                                                loadDetailedVideos()
                                                onVideoDeleteSuccess()
                                            } else {
                                                Toast.makeText(context, "Failed: $err", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(13.dp), tint = Color.Red)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Delete", color = Color.Red)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingVideo != null) {
        AlertDialog(
            onDismissRequest = { editingVideo = null },
            title = { Text("Edit Lecture Details", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = editTitle, onValueChange = { editTitle = it }, label = { Text("Lecture Title") })
                    OutlinedTextField(value = editDesc, onValueChange = { editDesc = it }, label = { Text("Description") }, minLines = 2)
                    OutlinedTextField(value = editDuration, onValueChange = { editDuration = it }, label = { Text("Duration (e.g. 45:00)") })
                    OutlinedTextField(value = editOrder, onValueChange = { editOrder = it }, label = { Text("Order number") })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = editVisible, onCheckedChange = { editVisible = it })
                        Text("Visible to Students", fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editTitle.isBlank()) {
                            Toast.makeText(context, "Title required", Toast.LENGTH_SHORT).show()
                        } else {
                            isOperating = true
                            R2SupabaseManager.updateVideo(
                                context = context,
                                id = editingVideo!!.id,
                                title = editTitle.trim(),
                                description = editDesc.trim(),
                                duration = editDuration.trim(),
                                orderNumber = editOrder.toIntOrNull() ?: 1,
                                visibility = editVisible
                            ) { success, err ->
                                isOperating = false
                                editingVideo = null
                                if (success) {
                                    Toast.makeText(context, "Lecture updated successfully!", Toast.LENGTH_SHORT).show()
                                    loadDetailedVideos()
                                } else {
                                    Toast.makeText(context, "Update failed: $err", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingVideo = null }) { Text("Cancel") }
            }
        )
    }

    if (isOperating) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}
