package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CourseEntity
import com.example.ui.theme.BrandBlueSecondary
import com.example.ui.viewmodel.AcademyViewModel

@Composable
fun AdminCourseManager(viewModel: AcademyViewModel) {
    val courses by viewModel.allCourses.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var batchTitle by remember { mutableStateOf("") }
    var batchCategory by remember { mutableStateOf("") }
    var batchPrice by remember { mutableStateOf("") }
    var batchThumbnailUrl by remember { mutableStateOf("") }

    var folderName by remember { mutableStateOf("All video") }
    var chapName by remember { mutableStateOf("") }
    var lessonTitle by remember { mutableStateOf("") }
    var videoLinkInput by remember { mutableStateOf("") }
    var thumbnailUrlInput by remember { mutableStateOf("") }

    var pdfNameInput by remember { mutableStateOf("") }
    var pdfContentInput by remember { mutableStateOf("") }
    var pdfSizeInput by remember { mutableStateOf("10.5 MB") }

    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedVideoName by remember { mutableStateOf("") }
    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var selectedPdfName by remember { mutableStateOf("") }
    var selectedLessonThumbnailUri by remember { mutableStateOf<Uri?>(null) }
    var selectedLessonThumbnailName by remember { mutableStateOf("") }
    var selectedBatchThumbnailUri by remember { mutableStateOf<Uri?>(null) }
    var selectedBatchThumbnailName by remember { mutableStateOf("") }

    var showAddLessonToCourseId by remember { mutableIntStateOf(-1) }
    var updatingThumbnailCourseId by remember { mutableIntStateOf(-1) }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            selectedVideoUri = it
            selectedVideoName = getFileNameFromUri(context, it)
            videoLinkInput = it.toString()
        }
    }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            selectedPdfUri = it
            selectedPdfName = getFileNameFromUri(context, it)
            pdfSizeInput = getFileSizeFromUri(context, it)
        }
    }

    val lessonThumbnailPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            selectedLessonThumbnailUri = it
            selectedLessonThumbnailName = getFileNameFromUri(context, it)
            thumbnailUrlInput = it.toString()
        }
    }

    val batchThumbnailPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (updatingThumbnailCourseId != -1) {
                viewModel.updateCourseThumbnail(updatingThumbnailCourseId, it.toString())
                updatingThumbnailCourseId = -1
                Toast.makeText(context, "Thumbnail Updated!", Toast.LENGTH_SHORT).show()
            } else {
                selectedBatchThumbnailUri = it
                selectedBatchThumbnailName = getFileNameFromUri(context, it)
                batchThumbnailUrl = it.toString()
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Course & Batch Management", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color(0xFF1E293B))
            Spacer(modifier = Modifier.height(16.dp))
            
            // AI TOOLS SECTION
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4FF)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF6366F1))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI Exam Tools (AI मॉक टेस्ट जनरेटर)", fontWeight = FontWeight.Bold, color = Color(0xFF6366F1))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    var aiExamInput by remember { mutableStateOf("") }
                    var aiQuestionCount by remember { mutableStateOf("50") }
                    val aiStatus = viewModel.aiProcessingStatus
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = aiExamInput,
                            onValueChange = { aiExamInput = it },
                            label = { Text("Exam Name (e.g. UPSC Prelims, UP Police)") },
                            modifier = Modifier.weight(1f),
                            enabled = aiStatus == null
                        )
                        OutlinedTextField(
                            value = aiQuestionCount,
                            onValueChange = { aiQuestionCount = it },
                            label = { Text("Questions") },
                            modifier = Modifier.width(100.dp),
                            enabled = aiStatus == null
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    if (aiStatus != null) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color(0xFF6366F1))
                        Text(aiStatus, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                    } else {
                        // Mock test generation moved to new module.
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Add New Batch (Academic / Competitive)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(value = batchTitle, onValueChange = { batchTitle = it }, label = { Text("Batch Name (e.g. SSC GD 2026)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = batchCategory, onValueChange = { batchCategory = it }, label = { Text("Category (e.g. Police, Class 10)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = batchPrice, onValueChange = { batchPrice = it }, label = { Text("Price in ₹ (0 for Free)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Batch Display Thumbnail (Cover Image)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Button(
                        onClick = { batchThumbnailPicker.launch(arrayOf("image/*")) },
                        colors = ButtonDefaults.buttonColors(containerColor = if (selectedBatchThumbnailUri != null) Color(0xFF10B981) else Color(0xFF64748B)),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (selectedBatchThumbnailUri != null) "Thumbnail Selected" else "Choose Batch Cover Image")
                    }
                    if (selectedBatchThumbnailName.isNotBlank()) {
                        Text("Selected: $selectedBatchThumbnailName", fontSize = 11.sp, color = Color.Gray)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (batchTitle.isBlank()) {
                                Toast.makeText(context, "Please enter batch name", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.adminAddNewCourse(
                                    title = batchTitle,
                                    category = batchCategory,
                                    subject = batchCategory,
                                    desc = "Batch for $batchCategory preparation.",
                                    isFree = (batchPrice.toDoubleOrNull() ?: 0.0) <= 0.0,
                                    price = batchPrice.toDoubleOrNull() ?: 0.0,
                                    imageUrl = batchThumbnailUrl
                                )
                                Toast.makeText(context, "New Batch Created: $batchTitle", Toast.LENGTH_SHORT).show()
                                batchTitle = ""
                                batchCategory = ""
                                batchPrice = ""
                                selectedBatchThumbnailUri = null
                                selectedBatchThumbnailName = ""
                                batchThumbnailUrl = ""
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
                        Spacer(modifier = Modifier.width(12.dp))
                        TextButton(onClick = {
                            updatingThumbnailCourseId = course.id
                            batchThumbnailPicker.launch(arrayOf("image/*"))
                        }) {
                            Text("Change Thumbnail", color = Color(0xFF64748B))
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
                                OutlinedTextField(value = chapName, onValueChange = { chapName = it }, label = { Text("Topic/Chapter (e.g. Science)") }, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(value = folderName, onValueChange = { folderName = it }, label = { Text("Folder (e.g. Video, Notes)") }, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(value = lessonTitle, onValueChange = { lessonTitle = it }, label = { Text("Lesson Name (लेसन का नाम)") }, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Thumbnail Picker
                                Button(
                                    onClick = { lessonThumbnailPicker.launch(arrayOf("image/*")) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedLessonThumbnailUri != null) Color(0xFF10B981) else BrandBlueSecondary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = if (selectedLessonThumbnailUri != null) Icons.Default.CheckCircle else Icons.Default.Image,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (selectedLessonThumbnailUri != null) "Lesson Thumbnail Ready" else "Browse Lesson Preview Thumbnail")
                                }
                                if (selectedLessonThumbnailName.isNotBlank()) {
                                    Text("Image: $selectedLessonThumbnailName", fontSize = 11.sp, color = Color.DarkGray, modifier = Modifier.padding(start = 4.dp))
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                
                                // VIDEO UPLOADER
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = Color.Red)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Video Source (MP4 / Web Link)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Button(
                                    onClick = { videoPicker.launch(arrayOf("video/*")) },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (selectedVideoUri != null) Color(0xFF10B981) else Color(0xFF334155)),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Text(if (selectedVideoUri != null) "Video File Attached 🎥" else "Pick Video from Phone Storage")
                                }
                                if (selectedVideoName.isNotBlank()) {
                                    Text("Attached: $selectedVideoName", fontSize = 11.sp, color = Color.Gray)
                                }
                                OutlinedTextField(value = videoLinkInput, onValueChange = { videoLinkInput = it }, label = { Text("Manual Video URL (YouTube/Drive/Direct Link)") }, modifier = Modifier.fillMaxWidth())

                                Spacer(modifier = Modifier.height(16.dp))

                                // PDF UPLOADER
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFFD97706))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Study Materials (PDF / Document)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Button(
                                    onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (selectedPdfUri != null) Color(0xFF10B981) else Color(0xFFD97706)),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Text(if (selectedPdfUri != null) "PDF Document Attached 📄" else "Pick Study PDF from Phone")
                                }
                                
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                    OutlinedTextField(value = pdfNameInput, onValueChange = { pdfNameInput = it }, label = { Text("Display PDF Name") }, modifier = Modifier.weight(1f))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedTextField(value = pdfSizeInput, onValueChange = { pdfSizeInput = it }, label = { Text("Size") }, modifier = Modifier.width(100.dp))
                                }
                                
                                if (selectedPdfName.isNotBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                                        modifier = Modifier.size(24.dp).align(Alignment.End)
                                    ) {
                                        Icon(Icons.Default.Cancel, contentDescription = null, tint = Color.Red)
                                    }
                                }

                                OutlinedTextField(value = pdfContentInput, onValueChange = { pdfContentInput = it }, label = { Text("Brief Content / Notes Summary") }, modifier = Modifier.fillMaxWidth(), minLines = 2)

                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        if (chapName.isBlank() || lessonTitle.isBlank()) {
                                            Toast.makeText(context, "Please enter both Chapter Title and Lesson Name!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            viewModel.adminAddLessonToCourse(
                                                courseId = course.id,
                                                chapter = chapName,
                                                folder = folderName,
                                                title = lessonTitle,
                                                videoLink = videoLinkInput,
                                                pdfLink = if (selectedPdfUri != null) selectedPdfUri.toString() else (if (pdfNameInput.isNotBlank()) "${pdfNameInput.trim()}.pdf" else "Class_Handout.pdf"),
                                                pdfName = if (pdfNameInput.isNotBlank()) pdfNameInput.trim() else "Study notes compilation",
                                                pdfContent = if (pdfContentInput.isNotBlank()) pdfContentInput.trim() else (if (selectedPdfName.isNotBlank()) "Loaded Local PDF Notes: $selectedPdfName" else ""),
                                                fileSize = pdfSizeInput,
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
                                            selectedLessonThumbnailUri = null
                                            selectedLessonThumbnailName = ""
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
