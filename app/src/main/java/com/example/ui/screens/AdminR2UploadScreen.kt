package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import android.content.Context
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.api.R2SupabaseManager
import com.example.api.SupabaseVideo
import com.example.data.CourseEntity
import com.example.ui.theme.BrandBluePrimary
import com.example.ui.theme.BrandBlueSecondary
import com.example.ui.viewmodel.AcademyViewModel
import java.text.DecimalFormat

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AdminR2UploadScreen(viewModel: AcademyViewModel) {
    val context = LocalContext.current
    val mainScrollState = rememberScrollState()
    val pipelineScrollState = rememberScrollState()

    // --------------------------------------------------------
    // API / DATA SYNC STATE
    // --------------------------------------------------------
    val courses by viewModel.allCourses.collectAsStateWithLifecycle()
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
        refreshSummary()
    }

    // --------------------------------------------------------
    // LOCAL STRUCTURE REUSE PERSISTENCE
    // (Enables adding empty subjects/chapters before publishing)
    // --------------------------------------------------------
    val locallyAddedSubjects = remember { mutableStateMapOf<String, List<String>>() }
    val locallyAddedChapters = remember { mutableStateMapOf<Pair<String, String>, List<String>>() }

    // --------------------------------------------------------
    // PIPELINE STEP FIELDS
    // --------------------------------------------------------

    // STEP 1: Add New Batch
    var newBatchName by remember { mutableStateOf("") }
    var newBatchDesc by remember { mutableStateOf("") }
    var newBatchPrice by remember { mutableStateOf("0") }
    var newBatchValidity by remember { mutableStateOf("365 Days") }
    var newBatchActive by remember { mutableStateOf(true) }
    var selectedBatchThumbnailUri by remember { mutableStateOf<Uri?>(null) }
    var selectedBatchThumbnailName by remember { mutableStateOf("") }

    val batchThumbnailPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedBatchThumbnailUri = it
            val (name, _) = R2SupabaseManager.getFileInfo(context, it)
            selectedBatchThumbnailName = name
        }
    }

    // STEP 2: Add Subject
    var selectedBatchForSubject by remember { mutableStateOf("") }
    var newSubjectName by remember { mutableStateOf("") }
    var selectedSubjectIcon by remember { mutableStateOf("Science") }
    
    // STEP 3: Add Chapter
    var selectedBatchForChapter by remember { mutableStateOf("") }
    var selectedSubjectForChapter by remember { mutableStateOf("") }
    var newChapterName by remember { mutableStateOf("") }
    var selectedChapterThumbnailUri by remember { mutableStateOf<Uri?>(null) }
    var selectedChapterThumbnailName by remember { mutableStateOf("") }
    var newChapterDesc by remember { mutableStateOf("") }

    val chapterThumbnailPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedChapterThumbnailUri = it
            val (name, _) = R2SupabaseManager.getFileInfo(context, it)
            selectedChapterThumbnailName = name
        }
    }

    // STEP 4: Video Upload
    var selectedBatchForUpload by remember { mutableStateOf("") }
    var selectedSubjectForUpload by remember { mutableStateOf("") }
    var selectedChapterForUpload by remember { mutableStateOf("") }
    var videoTitle by remember { mutableStateOf("") }
    var videoDesc by remember { mutableStateOf("") }
    var videoDuration by remember { mutableStateOf("30:00") }
    var videoOrder by remember { mutableStateOf("1") }
    var videoVisibility by remember { mutableStateOf(true) }
    
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedVideoName by remember { mutableStateOf("") }
    var selectedVideoSizeStr by remember { mutableStateOf("") }
    var webVideoUrl by remember { mutableStateOf("") }
    
    var selectedVideoThumbnailUri by remember { mutableStateOf<Uri?>(null) }
    var selectedVideoThumbnailName by remember { mutableStateOf("") }
    
    var selectedPdf1Uri by remember { mutableStateOf<Uri?>(null) }
    var selectedPdf1Name by remember { mutableStateOf("") }
    
    var selectedPdf2Uri by remember { mutableStateOf<Uri?>(null) }
    var selectedPdf2Name by remember { mutableStateOf("") }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedVideoUri = it
            val (name, size) = R2SupabaseManager.getFileInfo(context, it)
            selectedVideoName = name
            val sizeMb = size.toDouble() / (1024 * 1024)
            selectedVideoSizeStr = DecimalFormat("#.##").format(sizeMb) + " MB"
        }
    }

    val videoThumbnailPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedVideoThumbnailUri = it
            val (name, _) = R2SupabaseManager.getFileInfo(context, it)
            selectedVideoThumbnailName = name
        }
    }

    val pdf1Picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedPdf1Uri = it
            val (name, _) = R2SupabaseManager.getFileInfo(context, it)
            selectedPdf1Name = name
        }
    }

    val pdf2Picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedPdf2Uri = it
            val (name, _) = R2SupabaseManager.getFileInfo(context, it)
            selectedPdf2Name = name
        }
    }

    // --------------------------------------------------------
    // PUBLISHING / PROGRESS STATE
    // --------------------------------------------------------
    var isPublishing by remember { mutableStateOf(false) }
    var publishProgressText by remember { mutableStateOf("") }
    var uploadSuccessByStep by remember { mutableStateOf(false) }

    // Credentials Expandable
    var showCredentialsSettings by remember { mutableStateOf(false) }
    var supabaseUrl by remember { mutableStateOf("") }
    var supabaseAnonKey by remember { mutableStateOf("") }
    var supabaseTable by remember { mutableStateOf("videos") }
    var r2AccountId by remember { mutableStateOf("") }
    var r2BucketName by remember { mutableStateOf("") }
    var r2AccessKeyId by remember { mutableStateOf("") }
    var r2SecretAccessKey by remember { mutableStateOf("") }
    var r2PublicUrl by remember { mutableStateOf("") }

    var b2BucketName by remember { mutableStateOf("") }
    var b2AccessKeyId by remember { mutableStateOf("") }
    var b2SecretAccessKey by remember { mutableStateOf("") }
    var b2Endpoint by remember { mutableStateOf("") }

    var activeStorageProvider by remember { mutableStateOf("BACKBLAZE_B2") }

    fun refreshCredsState() {
        val creds = R2SupabaseManager.getCredentials(context)
        supabaseUrl = creds.supabaseUrl
        supabaseAnonKey = creds.supabaseAnonKey
        supabaseTable = creds.supabaseTable
        r2AccountId = creds.r2AccountId
        r2BucketName = creds.r2BucketName
        r2AccessKeyId = creds.r2AccessKeyId
        r2SecretAccessKey = creds.r2SecretAccessKey
        r2PublicUrl = creds.r2PublicUrl

        val b2Creds = com.example.api.BackblazeB2Manager.getCredentials(context)
        b2BucketName = b2Creds.b2BucketName
        b2AccessKeyId = b2Creds.b2AccessKeyId
        b2SecretAccessKey = b2Creds.b2SecretAccessKey
        b2Endpoint = b2Creds.b2Endpoint

        val sharedPrefs = context.getSharedPreferences("r2_storage_config", Context.MODE_PRIVATE)
        val defaultProvider = com.example.BuildConfig.STORAGE_PROVIDER.ifBlank { "BACKBLAZE_B2" }
        activeStorageProvider = sharedPrefs.getString("active_provider", defaultProvider) ?: defaultProvider
    }

    LaunchedEffect(Unit) {
        refreshCredsState()
    }

    // --------------------------------------------------------
    // EDIT & DELETE DIALOG STATES
    // --------------------------------------------------------
    var editingBatch by remember { mutableStateOf<CourseEntity?>(null) }
    var editBatchName by remember { mutableStateOf("") }
    var editBatchDesc by remember { mutableStateOf("") }
    var editBatchPrice by remember { mutableStateOf("") }
    var editBatchValidity by remember { mutableStateOf("") }
    var editBatchActive by remember { mutableStateOf(true) }
    var editBatchImageUrl by remember { mutableStateOf("") }

    val editBatchImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) { e.printStackTrace() }
            android.util.Log.i("AdminR2UploadScreen", "[COVER IMAGE SELECT] Selected cover image URI: $it")
            editBatchImageUrl = it.toString()
        }
    }

    var editingSubject by remember { mutableStateOf<Pair<String, String>?>(null) } // BatchName to SubjectName
    var editSubjectName by remember { mutableStateOf("") }

    var editingChapter by remember { mutableStateOf<Triple<String, String, String>?>(null) } // Batch to Subject to Chapter
    var editChapterName by remember { mutableStateOf("") }

    var editingVideo by remember { mutableStateOf<SupabaseVideo?>(null) }
    var editVideoTitle by remember { mutableStateOf("") }
    var editVideoDesc by remember { mutableStateOf("") }
    var editVideoDuration by remember { mutableStateOf("") }
    var editVideoOrder by remember { mutableStateOf("") }
    var editVideoVisibility by remember { mutableStateOf(true) }

    // Delete Confirmation
    var deletingBatchId by remember { mutableStateOf<Int?>(null) }
    var deletingSubjectTuple by remember { mutableStateOf<Pair<String, String>?>(null) }
    var deletingChapterTriple by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var deletingVideoId by remember { mutableStateOf<Int?>(null) }

    // Reusable dropdown helpers
    val batchOptions = remember(combinedCourses) { combinedCourses.map { it.title }.distinct() }

    fun getSubjectsForBatch(batch: String): List<String> {
        val dbSubjects = summaryVideos.filter { it.classText.trim().equals(batch.trim(), ignoreCase = true) }
            .map { it.subject.trim() }.distinct()
        val local = locallyAddedSubjects[batch.trim()] ?: emptyList()
        return (dbSubjects + local).distinct().filter { it.isNotBlank() }
    }

    fun getChaptersForSubject(batch: String, subject: String): List<String> {
        val dbChapters = summaryVideos.filter {
            it.classText.trim().equals(batch.trim(), ignoreCase = true) &&
            it.subject.trim().equals(subject.trim(), ignoreCase = true)
        }.map { it.chapter.trim() }.distinct()
        val local = locallyAddedChapters[Pair(batch.trim(), subject.trim())] ?: emptyList()
        return (dbChapters + local).distinct().filter { it.isNotBlank() }
    }

    // Main layout
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .verticalScroll(mainScrollState)
            .padding(16.dp)
    ) {
        // Header Section
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Lakshya Academy CMS Dashboard",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = "Seamlessly manage batches, subjects, chapters, and publish high-quality secure video lectures.",
                    fontSize = 13.sp,
                    color = Color(0xFF64748B)
                )
            }
        }

        // Horizontal CMS Builder Steps Pipeline
        Text(
            text = "Content Creation Pipeline",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color(0xFF334155),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(pipelineScrollState)
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // STEP 1: Add New Batch
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.width(320.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StepHeader(num = "1", title = "New Batch Add करें")
                    
                    OutlinedTextField(
                        value = newBatchName,
                        onValueChange = { newBatchName = it },
                        label = { Text("Batch Name (कक्षा)") },
                        placeholder = { Text("e.g. Class 12 Boards 2026") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newBatchDesc,
                        onValueChange = { newBatchDesc = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            placeholder = { Text("e.g. 12 Months") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = newBatchActive, onCheckedChange = { newBatchActive = it })
                        Text("Active Status", fontSize = 13.sp, color = Color(0xFF334155))
                    }

                    // Thumbnail Selection
                    Button(
                        onClick = { batchThumbnailPicker.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = if (selectedBatchThumbnailUri != null) Color(0xFF10B981) else Color(0xFF475569)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Image, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (selectedBatchThumbnailUri != null) "Thumbnail Selected" else "Select Cover Image")
                    }
                    if (selectedBatchThumbnailName.isNotEmpty()) {
                        Text(selectedBatchThumbnailName, fontSize = 11.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = {
                            if (newBatchName.isBlank()) {
                                Toast.makeText(context, "Please enter batch name!", Toast.LENGTH_SHORT).show()
                            } else {
                                val isDuplicate = batchOptions.any { it.equals(newBatchName.trim(), ignoreCase = true) }
                                if (isDuplicate) {
                                    Toast.makeText(context, "Batch already exists!", Toast.LENGTH_SHORT).show()
                                } else {
                                    val priceVal = newBatchPrice.toDoubleOrNull() ?: 0.0
                                    viewModel.adminAddNewCourse(
                                        title = newBatchName.trim(),
                                        category = "Class",
                                        subject = "Mixed",
                                        desc = buildBatchDescription(newBatchValidity.trim(), newBatchActive, newBatchDesc.trim()),
                                        isFree = priceVal <= 0.0,
                                        price = priceVal,
                                        imageUrl = selectedBatchThumbnailUri?.toString() ?: ""
                                    )
                                    Toast.makeText(context, "Batch '$newBatchName' Saved successfully! 🎉", Toast.LENGTH_SHORT).show()
                                    // Set focus pre-fills
                                    selectedBatchForSubject = newBatchName.trim()
                                    selectedBatchForChapter = newBatchName.trim()
                                    selectedBatchForUpload = newBatchName.trim()
                                    // Reset fields
                                    newBatchName = ""
                                    newBatchDesc = ""
                                    newBatchPrice = "0"
                                    newBatchValidity = "365 Days"
                                    selectedBatchThumbnailUri = null
                                    selectedBatchThumbnailName = ""
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBluePrimary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save Batch", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // STEP 2: Add Subject
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.width(320.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StepHeader(num = "2", title = "Subject Add करें (Batch के अंदर)")

                    SimpleDropdownSelector(
                        label = "Select Target Batch",
                        options = batchOptions,
                        selectedOption = selectedBatchForSubject,
                        onOptionSelected = {
                            selectedBatchForSubject = it
                            selectedBatchForChapter = it
                            selectedBatchForUpload = it
                        }
                    )

                    OutlinedTextField(
                        value = newSubjectName,
                        onValueChange = { newSubjectName = it },
                        label = { Text("Subject Name (विषय)") },
                        placeholder = { Text("e.g. Physics, Math, Chemistry") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Subject Icon Selection Display Grid
                    Text("Select Subject Icon Preset:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    val iconOptions = listOf("Science", "Calculate", "Psychology", "Book", "Translate", "Computer", "Sports", "Public", "School")
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            iconOptions.forEach { name ->
                                val isSelected = selectedSubjectIcon == name
                                val icon = getIconForName(name)
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) BrandBluePrimary else Color(0xFFF1F5F9))
                                        .clickable { selectedSubjectIcon = name },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = name,
                                        tint = if (isSelected) Color.White else Color(0xFF475569),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = {
                            if (selectedBatchForSubject.isBlank()) {
                                Toast.makeText(context, "Please select target Batch first!", Toast.LENGTH_SHORT).show()
                            } else if (newSubjectName.isBlank()) {
                                Toast.makeText(context, "Please enter subject name!", Toast.LENGTH_SHORT).show()
                            } else {
                                val currentList = locallyAddedSubjects[selectedBatchForSubject.trim()] ?: emptyList()
                                if (currentList.contains(newSubjectName.trim())) {
                                    Toast.makeText(context, "Subject already exists under this batch!", Toast.LENGTH_SHORT).show()
                                } else {
                                    locallyAddedSubjects[selectedBatchForSubject.trim()] = currentList + newSubjectName.trim()
                                    Toast.makeText(context, "Subject '$newSubjectName' added to batch!", Toast.LENGTH_SHORT).show()
                                    selectedSubjectForChapter = newSubjectName.trim()
                                    selectedSubjectForUpload = newSubjectName.trim()
                                    newSubjectName = ""
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBluePrimary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save Subject", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // STEP 3: Add Chapter
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.width(320.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StepHeader(num = "3", title = "Chapter Add करें (Subject के अंदर)")

                    SimpleDropdownSelector(
                        label = "Select Target Batch",
                        options = batchOptions,
                        selectedOption = selectedBatchForChapter,
                        onOptionSelected = {
                            selectedBatchForChapter = it
                            selectedBatchForSubject = it
                            selectedBatchForUpload = it
                            selectedSubjectForChapter = ""
                        }
                    )

                    SimpleDropdownSelector(
                        label = "Select Subject",
                        options = getSubjectsForBatch(selectedBatchForChapter),
                        selectedOption = selectedSubjectForChapter,
                        onOptionSelected = {
                            selectedSubjectForChapter = it
                            selectedSubjectForUpload = it
                        }
                    )

                    OutlinedTextField(
                        value = newChapterName,
                        onValueChange = { newChapterName = it },
                        label = { Text("Chapter Name (अध्याय)") },
                        placeholder = { Text("e.g. Motion in 1 Dimension") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newChapterDesc,
                        onValueChange = { newChapterDesc = it },
                        label = { Text("Chapter Subtitle (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Optional Chapter Thumbnail
                    Button(
                        onClick = { chapterThumbnailPicker.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = if (selectedChapterThumbnailUri != null) Color(0xFF10B981) else Color(0xFF475569)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Image, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (selectedChapterThumbnailUri != null) "Thumbnail Selected" else "Choose Chapter Image")
                    }
                    if (selectedChapterThumbnailName.isNotEmpty()) {
                        Text(selectedChapterThumbnailName, fontSize = 11.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = {
                            if (selectedBatchForChapter.isBlank() || selectedSubjectForChapter.isBlank()) {
                                Toast.makeText(context, "Select Batch and Subject first!", Toast.LENGTH_SHORT).show()
                            } else if (newChapterName.isBlank()) {
                                Toast.makeText(context, "Please enter chapter name!", Toast.LENGTH_SHORT).show()
                            } else {
                                val key = Pair(selectedBatchForChapter.trim(), selectedSubjectForChapter.trim())
                                val currentList = locallyAddedChapters[key] ?: emptyList()
                                if (currentList.contains(newChapterName.trim())) {
                                    Toast.makeText(context, "Chapter already exists!", Toast.LENGTH_SHORT).show()
                                } else {
                                    locallyAddedChapters[key] = currentList + newChapterName.trim()
                                    Toast.makeText(context, "Chapter '$newChapterName' saved!", Toast.LENGTH_SHORT).show()
                                    selectedChapterForUpload = newChapterName.trim()
                                    newChapterName = ""
                                    newChapterDesc = ""
                                    selectedChapterThumbnailUri = null
                                    selectedChapterThumbnailName = ""
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBluePrimary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save Chapter", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // STEP 4: Video Upload
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.width(350.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StepHeader(num = "4", title = "Video Upload करें (Chapter के अंदर)")

                    SimpleDropdownSelector(
                        label = "Target Batch",
                        options = batchOptions,
                        selectedOption = selectedBatchForUpload,
                        onOptionSelected = {
                            selectedBatchForUpload = it
                            selectedBatchForSubject = it
                            selectedBatchForChapter = it
                            selectedSubjectForUpload = ""
                            selectedChapterForUpload = ""
                        }
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SimpleDropdownSelector(
                            label = "Subject",
                            options = getSubjectsForBatch(selectedBatchForUpload),
                            selectedOption = selectedSubjectForUpload,
                            onOptionSelected = {
                                selectedSubjectForUpload = it
                                selectedSubjectForChapter = it
                                selectedChapterForUpload = ""
                            },
                            modifier = Modifier.weight(1f)
                        )
                        SimpleDropdownSelector(
                            label = "Chapter",
                            options = getChaptersForSubject(selectedBatchForUpload, selectedSubjectForUpload),
                            selectedOption = selectedChapterForUpload,
                            onOptionSelected = { selectedChapterForUpload = it },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = videoTitle,
                        onValueChange = { videoTitle = it },
                        label = { Text("Video Lecture Title") },
                        placeholder = { Text("e.g. 01. Motion & Reference Frame") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = videoDuration,
                            onValueChange = { videoDuration = it },
                            label = { Text("Duration") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = videoOrder,
                            onValueChange = { videoOrder = it },
                            label = { Text("Lecture No.") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // FILE SELECTORS (Video, Thumbnail, PDFs)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Video file picker
                        FileSelectorBox(
                            label = "Lecture Video (MP4)",
                            isSelected = selectedVideoUri != null,
                            fileName = selectedVideoName,
                            fileSize = selectedVideoSizeStr,
                            onPick = { videoPicker.launch("video/*") },
                            onClear = {
                                selectedVideoUri = null
                                selectedVideoName = ""
                                selectedVideoSizeStr = ""
                            },
                            icon = Icons.Default.VideoCall
                        )

                        // Web Video Url backup
                        OutlinedTextField(
                            value = webVideoUrl,
                            onValueChange = { webVideoUrl = it },
                            label = { Text("Or Web Video URL (YouTube ID / HTTP)") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = selectedVideoUri == null
                        )

                        // Thumbnail file picker
                        FileSelectorBox(
                            label = "Video Cover Thumbnail (Image)",
                            isSelected = selectedVideoThumbnailUri != null,
                            fileName = selectedVideoThumbnailName,
                            onPick = { videoThumbnailPicker.launch("image/*") },
                            onClear = {
                                selectedVideoThumbnailUri = null
                                selectedVideoThumbnailName = ""
                            },
                            icon = Icons.Default.Image
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // PDF 1 picker
                            Column(modifier = Modifier.weight(1f)) {
                                FileSelectorBox(
                                    label = "PDF 1 Notes",
                                    isSelected = selectedPdf1Uri != null,
                                    fileName = selectedPdf1Name,
                                    onPick = { pdf1Picker.launch("application/pdf") },
                                    onClear = {
                                        selectedPdf1Uri = null
                                        selectedPdf1Name = ""
                                    },
                                    icon = Icons.Default.PictureAsPdf
                                )
                            }

                            // PDF 2 picker
                            Column(modifier = Modifier.weight(1f)) {
                                FileSelectorBox(
                                    label = "PDF 2 Notes",
                                    isSelected = selectedPdf2Uri != null,
                                    fileName = selectedPdf2Name,
                                    onPick = { pdf2Picker.launch("application/pdf") },
                                    onClear = {
                                        selectedPdf2Uri = null
                                        selectedPdf2Name = ""
                                    },
                                    icon = Icons.Default.PictureAsPdf
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = videoDesc,
                        onValueChange = { videoDesc = it },
                        label = { Text("Video Description") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            if (videoTitle.isBlank()) {
                                Toast.makeText(context, "Please enter lecture title!", Toast.LENGTH_SHORT).show()
                            } else if (selectedVideoUri == null && webVideoUrl.isBlank()) {
                                Toast.makeText(context, "Select video file or input video link!", Toast.LENGTH_SHORT).show()
                            } else if (!isYouTubeUrl(webVideoUrl) && selectedVideoThumbnailUri == null) {
                                Toast.makeText(context, "Thumbnail is required for uploaded video files!", Toast.LENGTH_SHORT).show()
                            } else {
                                isPublishing = true
                                publishProgressText = "Starting upload sequence..."
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
                                        classText = selectedBatchForUpload,
                                        subject = selectedSubjectForUpload,
                                        chapter = selectedChapterForUpload,
                                        description = videoDesc.trim(),
                                        duration = videoDuration,
                                        orderNumber = videoOrder.toIntOrNull() ?: 1,
                                        visibility = videoVisibility,
                                        onProgress = { progressText ->
                                            publishProgressText = progressText
                                        },
                                        onSuccess = {
                                            isPublishing = false
                                            uploadSuccessByStep = true
                                            refreshSummary()
                                            viewModel.syncFromRemote()
                                            Toast.makeText(context, "Published Successfully! 🎉", Toast.LENGTH_LONG).show()
                                        },
                                        onError = { err ->
                                            isPublishing = false
                                            Toast.makeText(context, "Error publishing: $err", Toast.LENGTH_LONG).show()
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
                                        classText = selectedBatchForUpload,
                                        subject = selectedSubjectForUpload,
                                        chapter = selectedChapterForUpload,
                                        description = videoDesc.trim(),
                                        duration = videoDuration,
                                        orderNumber = videoOrder.toIntOrNull() ?: 1,
                                        visibility = videoVisibility,
                                        onProgress = { progressText ->
                                            publishProgressText = progressText
                                        },
                                        onSuccess = {
                                            isPublishing = false
                                            uploadSuccessByStep = true
                                            refreshSummary()
                                            viewModel.syncFromRemote()
                                            Toast.makeText(context, "Published Successfully! 🎉", Toast.LENGTH_LONG).show()
                                        },
                                        onError = { err ->
                                            isPublishing = false
                                            Toast.makeText(context, "Error publishing: $err", Toast.LENGTH_LONG).show()
                                        }
                                    )
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBluePrimary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isPublishing
                    ) {
                        if (isPublishing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                        } else {
                            Text("Save & Publish Video", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // STEP 5: Storage Provider Switching
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.width(320.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    StepHeader(num = "5", title = "Storage Provider Switch")

                    Text(
                        "Manage where your heavy lectures and files are hosted dynamically. Switch backend storage cleanly.",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )

                    // Backblaze B2 Provider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val sharedPrefs = context.getSharedPreferences("r2_storage_config", Context.MODE_PRIVATE)
                                sharedPrefs.edit().putString("active_provider", "BACKBLAZE_B2").apply()
                                activeStorageProvider = "BACKBLAZE_B2"
                                Toast.makeText(context, "Switched Active Provider to Backblaze B2", Toast.LENGTH_SHORT).show()
                            }
                            .border(
                                width = if (activeStorageProvider == "BACKBLAZE_B2") 2.dp else 1.dp,
                                color = if (activeStorageProvider == "BACKBLAZE_B2") BrandBluePrimary else Color(0xFFE2E8F0),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .background(if (activeStorageProvider == "BACKBLAZE_B2") Color(0xFFEFF6FF) else Color.White)
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = null,
                                tint = if (activeStorageProvider == "BACKBLAZE_B2") BrandBluePrimary else Color(0xFF64748B),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Backblaze B2",
                                    fontWeight = FontWeight.Bold,
                                    color = if (activeStorageProvider == "BACKBLAZE_B2") BrandBluePrimary else Color(0xFF475569)
                                )
                                Text("Private S3 Bucket (Secure)", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            if (activeStorageProvider == "BACKBLAZE_B2") {
                                Icon(Icons.Default.CheckCircle, null, tint = BrandBluePrimary)
                            }
                        }
                    }

                    // Supabase Storage Provider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val sharedPrefs = context.getSharedPreferences("r2_storage_config", Context.MODE_PRIVATE)
                                sharedPrefs.edit().putString("active_provider", "SUPABASE_R2_HYBRID").apply()
                                activeStorageProvider = "SUPABASE_R2_HYBRID"
                                Toast.makeText(context, "Switched Active Provider to Supabase Storage", Toast.LENGTH_SHORT).show()
                            }
                            .border(
                                width = if (activeStorageProvider == "SUPABASE_R2_HYBRID") 2.dp else 1.dp,
                                color = if (activeStorageProvider == "SUPABASE_R2_HYBRID") Color(0xFF10B981) else Color(0xFFE2E8F0),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .background(if (activeStorageProvider == "SUPABASE_R2_HYBRID") Color(0xFFECFDF5) else Color.White)
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = null,
                                tint = if (activeStorageProvider == "SUPABASE_R2_HYBRID") Color(0xFF059669) else Color(0xFF64748B),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Supabase / R2 Hybrid",
                                    fontWeight = FontWeight.Bold,
                                    color = if (activeStorageProvider == "SUPABASE_R2_HYBRID") Color(0xFF065F46) else Color(0xFF475569)
                                )
                                Text("Legacy Public Storage", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            if (activeStorageProvider == "SUPABASE_R2_HYBRID") {
                                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF10B981))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        "* Backblaze B2 ensures your videos are private and accessed via secure signatures. Supabase handles metadata synchronization.",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        // Section 6: Upload Success Card
        if (isPublishing) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Active Publishing Process Running", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(publishProgressText, fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }

        if (uploadSuccessByStep) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                border = BorderStroke(1.dp, Color(0xFF10B981)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF10B981), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Lecture Content Uploaded Successfully! 🎉", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF065F46))
                    }
                    Text("Your video file, cover thumbnail, and study PDFs were uploaded and registered in the database.", fontSize = 13.sp, color = Color(0xFF047857))
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                // Reset variables
                                videoTitle = ""
                                videoDesc = ""
                                webVideoUrl = ""
                                selectedVideoUri = null
                                selectedVideoThumbnailUri = null
                                selectedPdf1Uri = null
                                selectedPdf2Uri = null
                                uploadSuccessByStep = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                        ) {
                            Text("Upload Another Lecture")
                        }
                    }
                }
            }
        }

        // Section 7: Live Expandable CMS Tree
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Live CMS Content Tree Hierarchy", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
                        Text("Directly edit or delete batches, subjects, chapters, and lectures on the live server.", fontSize = 12.sp, color = Color.Gray)
                    }
                    IconButton(onClick = { refreshSummary() }) {
                        Icon(Icons.Default.Refresh, "Refresh Tree")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isLoadingSummary && summaryVideos.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (combinedCourses.isEmpty()) {
                    Text("No batches found. Create your first batch to start your academy structure!", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                } else {
                    combinedCourses.forEach { batch ->
                        BatchTreeNode(
                            batch = batch,
                            summaryVideos = summaryVideos,
                            locallyAddedSubjects = locallyAddedSubjects,
                            locallyAddedChapters = locallyAddedChapters,
                            onEditBatch = {
                                editingBatch = batch
                                editBatchName = batch.title
                                editBatchDesc = batch.getRealDescription()
                                editBatchPrice = batch.price.toString()
                                editBatchValidity = batch.getValidity()
                                editBatchActive = batch.isActive()
                                editBatchImageUrl = batch.imageUrl
                                android.util.Log.i("AdminR2UploadScreen", "[EDIT BATCH CLICK] Editing batch ID: ${batch.id}, initial imageUrl: ${batch.imageUrl}")
                            },
                            onDeleteBatch = { deletingBatchId = batch.id },
                            onEditSubject = { bName, sName ->
                                editingSubject = Pair(bName, sName)
                                editSubjectName = sName
                            },
                            onDeleteSubject = { bName, sName -> deletingSubjectTuple = Pair(bName, sName) },
                            onEditChapter = { bName, sName, cName ->
                                editingChapter = Triple(bName, sName, cName)
                                editChapterName = cName
                            },
                            onDeleteChapter = { bName, sName, cName -> deletingChapterTriple = Triple(bName, sName, cName) },
                            onEditVideo = { video ->
                                editingVideo = video
                                editVideoTitle = video.title
                                editVideoDesc = video.description
                                editVideoDuration = video.duration
                                editVideoOrder = video.orderNumber.toString()
                                editVideoVisibility = video.visibility
                            },
                            onDeleteVideo = { vId -> deletingVideoId = vId }
                        )
                    }
                }
            }
        }

        // Section 8: Key Architecture Flow Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Secure Storage Architecture Flow",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF334155), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AdminPanelSettings, "Admin", tint = Color.White, modifier = Modifier.size(24.dp))
                            Text("CMS Admin", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Icon(Icons.Default.ArrowForward, null, tint = Color.Gray, modifier = Modifier.size(16.dp))

                    Box(
                        modifier = Modifier
                            .background(Color(0xFF10B981), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Cloud, "Storage", tint = Color.White, modifier = Modifier.size(24.dp))
                            Text("Supabase/R2", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Icon(Icons.Default.ArrowForward, null, tint = Color.Gray, modifier = Modifier.size(16.dp))

                    Box(
                        modifier = Modifier
                            .background(Color(0xFF4F46E5), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.School, "Student", tint = Color.White, modifier = Modifier.size(24.dp))
                            Text("Student App", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(12.dp))

                Text("✔ Easy for Teachers • One Click Automatic Video & PDF Association", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("✔ No manual database syncing needed. Changes are immediately populated inside the student app.", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Credentials Section (Bottom Collapsible)
        Spacer(modifier = Modifier.height(24.dp))
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showCredentialsSettings = !showCredentialsSettings },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Backend API Credentials Settings", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.DarkGray)
                    }
                    IconButton(onClick = { showCredentialsSettings = !showCredentialsSettings }) {
                        Icon(
                            imageVector = if (showCredentialsSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle Credentials"
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showCredentialsSettings,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Supabase API", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BrandBluePrimary)
                        OutlinedTextField(value = supabaseUrl, onValueChange = { supabaseUrl = it }, label = { Text("Supabase URL") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = supabaseAnonKey, onValueChange = { supabaseAnonKey = it }, label = { Text("Supabase Anon Key") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = supabaseTable, onValueChange = { supabaseTable = it }, label = { Text("Database Table Name") }, modifier = Modifier.fillMaxWidth())

                        Text("Cloudflare R2 API (Legacy)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BrandBluePrimary)
                        OutlinedTextField(value = r2AccountId, onValueChange = { r2AccountId = it }, label = { Text("R2 Account ID") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = r2BucketName, onValueChange = { r2BucketName = it }, label = { Text("R2 Bucket Name") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = r2AccessKeyId, onValueChange = { r2AccessKeyId = it }, label = { Text("R2 Access Key ID") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = r2SecretAccessKey, onValueChange = { r2SecretAccessKey = it }, label = { Text("R2 Secret Access Key") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = r2PublicUrl, onValueChange = { r2PublicUrl = it }, label = { Text("R2 Public Bucket URL") }, modifier = Modifier.fillMaxWidth())

                        Text("Backblaze B2 Storage API", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BrandBluePrimary)
                        OutlinedTextField(value = b2BucketName, onValueChange = { b2BucketName = it }, label = { Text("B2 Bucket Name") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = b2AccessKeyId, onValueChange = { b2AccessKeyId = it }, label = { Text("B2 Application Key ID") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = b2SecretAccessKey, onValueChange = { b2SecretAccessKey = it }, label = { Text("B2 Application Key (Secret)") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = b2Endpoint, onValueChange = { b2Endpoint = it }, label = { Text("B2 S3 Endpoint (e.g. s3.us-west-004.backblazeb2.com)") }, modifier = Modifier.fillMaxWidth())

                        Button(
                            onClick = {
                                R2SupabaseManager.saveCredentials(
                                    context,
                                    R2SupabaseManager.Credentials(
                                        supabaseUrl = supabaseUrl,
                                        supabaseAnonKey = supabaseAnonKey,
                                        supabaseTable = supabaseTable,
                                        r2AccountId = r2AccountId,
                                        r2BucketName = r2BucketName,
                                        r2AccessKeyId = r2AccessKeyId,
                                        r2SecretAccessKey = r2SecretAccessKey,
                                        r2PublicUrl = r2PublicUrl
                                    )
                                )
                                com.example.api.BackblazeB2Manager.saveCredentials(
                                    context,
                                    com.example.api.BackblazeB2Manager.Credentials(
                                        b2BucketName = b2BucketName,
                                        b2AccessKeyId = b2AccessKeyId,
                                        b2SecretAccessKey = b2SecretAccessKey,
                                        b2Endpoint = b2Endpoint
                                    )
                                )
                                Toast.makeText(context, "Credentials Saved Successfully!", Toast.LENGTH_SHORT).show()
                                showCredentialsSettings = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandBluePrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Credentials")
                        }
                    }
                }
            }
        }
    }

    // --------------------------------------------------------
    // EDIT & DELETE DIALOGS
    // --------------------------------------------------------

    // Edit Batch Dialog
    if (editingBatch != null) {
        AlertDialog(
            onDismissRequest = { editingBatch = null },
            title = { Text("Edit Batch Details", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = editBatchName, onValueChange = { editBatchName = it }, label = { Text("Batch Name") })
                    OutlinedTextField(value = editBatchDesc, onValueChange = { editBatchDesc = it }, label = { Text("Description") }, minLines = 2)
                    OutlinedTextField(value = editBatchPrice, onValueChange = { editBatchPrice = it }, label = { Text("Price (₹)") })
                    OutlinedTextField(value = editBatchValidity, onValueChange = { editBatchValidity = it }, label = { Text("Validity") })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = editBatchActive, onCheckedChange = { editBatchActive = it })
                        Text("Active status", fontSize = 13.sp)
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Cover Image", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (editBatchImageUrl.isNotEmpty()) {
                            coil.compose.AsyncImage(
                                model = editBatchImageUrl,
                                contentDescription = "Cover Image Preview",
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
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
                                android.util.Log.i("AdminR2UploadScreen", "[COVER IMAGE SELECT] Launching image picker...")
                                editBatchImagePickerLauncher.launch(arrayOf("image/*"))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandBlueSecondary)
                        ) {
                            Text("Change Cover Image", fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = editBatchPrice.toDoubleOrNull() ?: 0.0
                        android.util.Log.i("AdminR2UploadScreen", "[COVER IMAGE SAVE] Save changes clicked for ID: ${editingBatch!!.id}. Final imageUrl: $editBatchImageUrl")
                        viewModel.adminUpdateCourse(
                            id = editingBatch!!.id,
                            title = editBatchName.trim(),
                            description = buildBatchDescription(editBatchValidity.trim(), editBatchActive, editBatchDesc.trim()),
                            price = p,
                            isFree = p <= 0.0,
                            imageUrl = editBatchImageUrl
                        )
                        Toast.makeText(context, "Batch Updated!", Toast.LENGTH_SHORT).show()
                        editingBatch = null
                    }
                ) { Text("Save Changes") }
            },
            dismissButton = {
                TextButton(onClick = { editingBatch = null }) { Text("Cancel") }
            }
        )
    }

    // Edit Subject Dialog
    if (editingSubject != null) {
        AlertDialog(
            onDismissRequest = { editingSubject = null },
            title = { Text("Rename Subject", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Current Name: ${editingSubject!!.second}", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = editSubjectName, onValueChange = { editSubjectName = it }, label = { Text("New Subject Name") })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val bName = editingSubject!!.first
                        val oldSName = editingSubject!!.second
                        if (editSubjectName.isNotBlank()) {
                            R2SupabaseManager.renameSubject(context, bName, oldSName, editSubjectName.trim()) { success, err ->
                                if (success) {
                                    refreshSummary()
                                    viewModel.syncFromRemote()
                                }
                            }
                            Toast.makeText(context, "Subject Rename Request Sent!", Toast.LENGTH_SHORT).show()
                            editingSubject = null
                        }
                    }
                ) { Text("Save Changes") }
            },
            dismissButton = {
                TextButton(onClick = { editingSubject = null }) { Text("Cancel") }
            }
        )
    }

    // Edit Chapter Dialog
    if (editingChapter != null) {
        AlertDialog(
            onDismissRequest = { editingChapter = null },
            title = { Text("Rename Chapter", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Current Chapter: ${editingChapter!!.third}", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = editChapterName, onValueChange = { editChapterName = it }, label = { Text("New Chapter Name") })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val bName = editingChapter!!.first
                        val sName = editingChapter!!.second
                        val oldCName = editingChapter!!.third
                        if (editChapterName.isNotBlank()) {
                            R2SupabaseManager.renameChapter(context, bName, sName, oldCName, editChapterName.trim()) { success, err ->
                                if (success) {
                                    refreshSummary()
                                    viewModel.syncFromRemote()
                                }
                            }
                            Toast.makeText(context, "Chapter Rename Request Sent!", Toast.LENGTH_SHORT).show()
                            editingChapter = null
                        }
                    }
                ) { Text("Save Changes") }
            },
            dismissButton = {
                TextButton(onClick = { editingChapter = null }) { Text("Cancel") }
            }
        )
    }

    // Edit Video Dialog
    if (editingVideo != null) {
        AlertDialog(
            onDismissRequest = { editingVideo = null },
            title = { Text("Edit Video Metadata", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = editVideoTitle, onValueChange = { editVideoTitle = it }, label = { Text("Video Title") })
                    OutlinedTextField(value = editVideoDesc, onValueChange = { editVideoDesc = it }, label = { Text("Description") })
                    OutlinedTextField(value = editVideoDuration, onValueChange = { editVideoDuration = it }, label = { Text("Duration") })
                    OutlinedTextField(value = editVideoOrder, onValueChange = { editVideoOrder = it }, label = { Text("Lecture No.") })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = editVideoVisibility, onCheckedChange = { editVideoVisibility = it })
                        Text("Visible in student app", fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editVideoTitle.isNotBlank()) {
                            R2SupabaseManager.updateVideo(
                                context = context,
                                id = editingVideo!!.id,
                                title = editVideoTitle.trim(),
                                description = editVideoDesc.trim(),
                                duration = editVideoDuration.trim(),
                                orderNumber = editVideoOrder.toIntOrNull() ?: 1,
                                visibility = editVideoVisibility
                            ) { success, err ->
                                if (success) {
                                    refreshSummary()
                                    viewModel.syncFromRemote()
                                }
                            }
                            Toast.makeText(context, "Video Metadata Updated!", Toast.LENGTH_SHORT).show()
                            editingVideo = null
                        }
                    }
                ) { Text("Save Changes") }
            },
            dismissButton = {
                TextButton(onClick = { editingVideo = null }) { Text("Cancel") }
            }
        )
    }

    // Deletion Confirmations
    if (deletingBatchId != null) {
        AlertDialog(
            onDismissRequest = { deletingBatchId = null },
            title = { Text("Confirm Delete Batch", fontWeight = FontWeight.Bold) },
            text = { Text("Are you absolutely sure you want to delete this batch? All lessons, enrollments, and videos inside it will be permanently deleted.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.adminDeleteCourse(deletingBatchId!!)
                    Toast.makeText(context, "Batch deleted locally & remote delete sync queued!", Toast.LENGTH_SHORT).show()
                    deletingBatchId = null
                }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Delete", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { deletingBatchId = null }) { Text("Cancel") } }
        )
    }

    if (deletingSubjectTuple != null) {
        AlertDialog(
            onDismissRequest = { deletingSubjectTuple = null },
            title = { Text("Confirm Delete Subject", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete Subject '${deletingSubjectTuple!!.second}'? This will delete all lectures and chapters inside it permanently.") },
            confirmButton = {
                Button(onClick = {
                    val batch = deletingSubjectTuple!!.first
                    val sName = deletingSubjectTuple!!.second
                    R2SupabaseManager.deleteSubject(context, batch, sName) { success, err ->
                        if (success) {
                            refreshSummary()
                            viewModel.syncFromRemote()
                        }
                    }
                    Toast.makeText(context, "Subject delete query dispatched!", Toast.LENGTH_SHORT).show()
                    deletingSubjectTuple = null
                }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Delete", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { deletingSubjectTuple = null }) { Text("Cancel") } }
        )
    }

    if (deletingChapterTriple != null) {
        AlertDialog(
            onDismissRequest = { deletingChapterTriple = null },
            title = { Text("Confirm Delete Chapter", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete Chapter '${deletingChapterTriple!!.third}'? All lecture videos and materials inside this chapter will be deleted.") },
            confirmButton = {
                Button(onClick = {
                    val batch = deletingChapterTriple!!.first
                    val subject = deletingChapterTriple!!.second
                    val chapter = deletingChapterTriple!!.third
                    R2SupabaseManager.deleteChapter(context, batch, subject, chapter) { success, err ->
                        if (success) {
                            refreshSummary()
                            viewModel.syncFromRemote()
                        }
                    }
                    Toast.makeText(context, "Chapter delete query dispatched!", Toast.LENGTH_SHORT).show()
                    deletingChapterTriple = null
                }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Delete", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { deletingChapterTriple = null }) { Text("Cancel") } }
        )
    }

    if (deletingVideoId != null) {
        AlertDialog(
            onDismissRequest = { deletingVideoId = null },
            title = { Text("Confirm Delete Video", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete this video lecture? This action cannot be undone.") },
            confirmButton = {
                Button(onClick = {
                    R2SupabaseManager.deleteVideo(context, deletingVideoId!!) { success, err ->
                        if (success) {
                            refreshSummary()
                            viewModel.syncFromRemote()
                        }
                    }
                    Toast.makeText(context, "Video delete query dispatched!", Toast.LENGTH_SHORT).show()
                    deletingVideoId = null
                }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Delete", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { deletingVideoId = null }) { Text("Cancel") } }
        )
    }
}

// --------------------------------------------------------
// SUB-COMPOSABLES & COMPONENT UTILS
// --------------------------------------------------------

@Composable
fun StepHeader(num: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(BrandBluePrimary),
            contentAlignment = Alignment.Center
        ) {
            Text(num, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color(0xFF1E293B))
    }
    HorizontalDivider(color = Color(0xFFE2E8F0))
}

@Composable
fun FileSelectorBox(
    label: String,
    isSelected: Boolean,
    fileName: String,
    fileSize: String = "",
    onPick: () -> Unit,
    onClear: () -> Unit,
    icon: ImageVector
) {
    Column {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    BorderStroke(1.dp, if (isSelected) Color(0xFF10B981) else Color(0xFFCBD5E1)),
                    RoundedCornerShape(8.dp)
                )
                .background(if (isSelected) Color(0xFFF0FDF4) else Color(0xFFF8FAFC))
                .clickable { if (!isSelected) onPick() }
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) Color(0xFF10B981) else Color(0xFF64748B),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (isSelected) {
                        Text(fileName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (fileSize.isNotEmpty()) {
                            Text(fileSize, fontSize = 10.sp, color = Color.Gray)
                        }
                    } else {
                        Text("Pick File", fontSize = 12.sp, color = Color(0xFF64748B))
                    }
                }
                if (isSelected) {
                    IconButton(onClick = onClear, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.Clear, "Clear Selection", tint = Color.Red, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SimpleDropdownSelector(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var isAddingNew by remember { mutableStateOf(false) }
    var newValue by remember { mutableStateOf("") }

    Box(modifier = modifier) {
        if (isAddingNew) {
            OutlinedTextField(
                value = newValue,
                onValueChange = { 
                    newValue = it
                    onOptionSelected(it)
                },
                label = { Text("New $label") },
                trailingIcon = {
                    IconButton(onClick = { 
                        isAddingNew = false
                        newValue = ""
                        onOptionSelected("")
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            OutlinedTextField(
                value = selectedOption.ifEmpty { "Select $label" },
                onValueChange = {},
                readOnly = true,
                label = { Text(label) },
                trailingIcon = {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.clickable { expanded = !expanded }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                            isAddingNew = false
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("+ Add New $label", color = Color(0xFF6366F1), fontWeight = FontWeight.Bold) },
                    onClick = {
                        isAddingNew = true
                        expanded = false
                        onOptionSelected("")
                    }
                )
            }
        }
    }
}

// --------------------------------------------------------
// INTERACTIVE LIVE HIERARCHY TREE
// --------------------------------------------------------

@Composable
fun BatchTreeNode(
    batch: CourseEntity,
    summaryVideos: List<SupabaseVideo>,
    locallyAddedSubjects: Map<String, List<String>>,
    locallyAddedChapters: Map<Pair<String, String>, List<String>>,
    onEditBatch: () -> Unit,
    onDeleteBatch: () -> Unit,
    onEditSubject: (String, String) -> Unit,
    onDeleteSubject: (String, String) -> Unit,
    onEditChapter: (String, String, String) -> Unit,
    onDeleteChapter: (String, String, String) -> Unit,
    onEditVideo: (SupabaseVideo) -> Unit,
    onDeleteVideo: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val batchVideos = remember(summaryVideos, batch.title) {
        summaryVideos.filter { it.classText.trim().equals(batch.title.trim(), ignoreCase = true) }
    }

    val subjects = remember(batchVideos, locallyAddedSubjects, batch.title) {
        val dbSubjects = batchVideos.map { it.subject.trim() }.distinct()
        val local = locallyAddedSubjects[batch.title.trim()] ?: emptyList()
        (dbSubjects + local).distinct().filter { it.isNotBlank() }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        // Batch Row Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF1F5F9))
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = Color(0xFF475569)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.School, null, tint = BrandBluePrimary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(batch.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))
                Text("Validity: ${batch.getValidity()} • Price: ₹${batch.price}", fontSize = 11.sp, color = Color(0xFF64748B))
            }

            // Edit / Delete Batch Buttons
            IconButton(onClick = onEditBatch, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Edit, "Edit Batch", tint = BrandBluePrimary, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onDeleteBatch, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Delete, "Delete Batch", tint = Color.Red, modifier = Modifier.size(16.dp))
            }
        }

        // Expanded Subjects
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp)) {
                if (subjects.isEmpty()) {
                    Text("No subjects added to this batch yet.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(8.dp))
                } else {
                    subjects.forEach { subject ->
                        SubjectTreeNode(
                            batchName = batch.title,
                            subjectName = subject,
                            batchVideos = batchVideos,
                            locallyAddedChapters = locallyAddedChapters,
                            onEditSubject = onEditSubject,
                            onDeleteSubject = onDeleteSubject,
                            onEditChapter = onEditChapter,
                            onDeleteChapter = onDeleteChapter,
                            onEditVideo = onEditVideo,
                            onDeleteVideo = onDeleteVideo
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SubjectTreeNode(
    batchName: String,
    subjectName: String,
    batchVideos: List<SupabaseVideo>,
    locallyAddedChapters: Map<Pair<String, String>, List<String>>,
    onEditSubject: (String, String) -> Unit,
    onDeleteSubject: (String, String) -> Unit,
    onEditChapter: (String, String, String) -> Unit,
    onDeleteChapter: (String, String, String) -> Unit,
    onEditVideo: (SupabaseVideo) -> Unit,
    onDeleteVideo: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val subjectVideos = remember(batchVideos, subjectName) {
        batchVideos.filter { it.subject.trim().equals(subjectName.trim(), ignoreCase = true) }
    }

    val chapters = remember(subjectVideos, locallyAddedChapters, batchName, subjectName) {
        val dbChapters = subjectVideos.map { it.chapter.trim() }.distinct()
        val local = locallyAddedChapters[Pair(batchName.trim(), subjectName.trim())] ?: emptyList()
        (dbChapters + local).distinct().filter { it.isNotBlank() }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(getIconForName(subjectName), null, tint = Color(0xFF4F46E5), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(subjectName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF334155), modifier = Modifier.weight(1f))

            IconButton(onClick = { onEditSubject(batchName, subjectName) }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Edit, "Edit Subject", tint = BrandBluePrimary, modifier = Modifier.size(14.dp))
            }
            IconButton(onClick = { onDeleteSubject(batchName, subjectName) }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Delete, "Delete Subject", tint = Color.Red, modifier = Modifier.size(14.dp))
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 24.dp, top = 2.dp, bottom = 4.dp)) {
                if (chapters.isEmpty()) {
                    Text("No chapters added yet.", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(6.dp))
                } else {
                    chapters.forEach { chapter ->
                        ChapterTreeNode(
                            batchName = batchName,
                            subjectName = subjectName,
                            chapterName = chapter,
                            subjectVideos = subjectVideos,
                            onEditChapter = onEditChapter,
                            onDeleteChapter = onDeleteChapter,
                            onEditVideo = onEditVideo,
                            onDeleteVideo = onDeleteVideo
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChapterTreeNode(
    batchName: String,
    subjectName: String,
    chapterName: String,
    subjectVideos: List<SupabaseVideo>,
    onEditChapter: (String, String, String) -> Unit,
    onDeleteChapter: (String, String, String) -> Unit,
    onEditVideo: (SupabaseVideo) -> Unit,
    onDeleteVideo: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val videos = remember(subjectVideos, chapterName) {
        subjectVideos.filter { it.chapter.trim().equals(chapterName.trim(), ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(Icons.Default.FolderOpen, null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(chapterName, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Color(0xFF475569), modifier = Modifier.weight(1f))

            IconButton(onClick = { onEditChapter(batchName, subjectName, chapterName) }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Edit, "Edit Chapter", tint = BrandBluePrimary, modifier = Modifier.size(14.dp))
            }
            IconButton(onClick = { onDeleteChapter(batchName, subjectName, chapterName) }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Delete, "Delete Chapter", tint = Color.Red, modifier = Modifier.size(14.dp))
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 24.dp, top = 2.dp, bottom = 4.dp)) {
                if (videos.isEmpty()) {
                    Text("No video lectures uploaded yet.", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(4.dp))
                } else {
                    videos.forEach { video ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PlayCircle, null, tint = Color(0xFF0EA5E9), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Lec ${video.orderNumber}: ${video.title}",
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 12.sp,
                                    color = Color(0xFF1E293B)
                                )
                                // Render attachment indicators
                                val attachments = mutableListOf<String>()
                                if (!video.pdfUrl.isNullOrBlank()) {
                                    val count = video.pdfUrl.split("|").filter { it.isNotBlank() }.size
                                    attachments.add("📂 $count PDFs")
                                }
                                if (video.duration.isNotEmpty()) {
                                    attachments.add("⏱ ${video.duration}")
                                }
                                if (attachments.isNotEmpty()) {
                                    Text(attachments.joinToString(" • "), fontSize = 10.sp, color = Color.Gray)
                                }
                            }

                            // Visibility Status Badge
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 6.dp)
                                    .background(if (video.visibility) Color(0xFFDCFCE7) else Color(0xFFF1F5F9), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    if (video.visibility) "Live" else "Hidden",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (video.visibility) Color(0xFF15803D) else Color(0xFF64748B)
                                )
                            }

                            IconButton(onClick = { onEditVideo(video) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Edit, "Edit Video", tint = BrandBluePrimary, modifier = Modifier.size(12.dp))
                            }
                            IconButton(onClick = { onDeleteVideo(video.id) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, "Delete Video", tint = Color.Red, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

fun getIconForName(name: String): ImageVector {
    return when (name.trim()) {
        "Science" -> Icons.Default.Science
        "Calculate" -> Icons.Default.Calculate
        "Psychology" -> Icons.Default.Psychology
        "Book" -> Icons.Default.Book
        "Translate" -> Icons.Default.Translate
        "Computer" -> Icons.Default.Computer
        "Sports" -> Icons.Default.SportsBasketball
        "Public" -> Icons.Default.Public
        "School" -> Icons.Default.School
        else -> Icons.Default.School
    }
}
