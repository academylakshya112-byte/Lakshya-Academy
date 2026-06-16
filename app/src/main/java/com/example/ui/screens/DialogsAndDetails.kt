package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.testTag
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.AcademyViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
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
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Lakshya Library: $type", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BrandBluePrimary)
                Spacer(modifier = Modifier.height(12.dp))
                if (docs.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No materials available currently.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(docs) { doc ->
                            ListItem(
                                headlineContent = { Text(doc.title) },
                                supportingContent = { Text(doc.fileSize) },
                                leadingContent = { Icon(Icons.Default.FilePresent, contentDescription = null, tint = BrandBluePrimary) },
                                modifier = Modifier.clickable { selectedDocForReading = doc }
                            )
                        }
                    }
                }
            }
        }
    }
    if (selectedDocForReading != null) {
        val link = selectedDocForReading!!.fileContent
        if (link.startsWith("content://") || link.startsWith("file://")) {
            NativePdfViewerScreen(
                pdfUri = link,
                pdfName = selectedDocForReading!!.title,
                fileSize = selectedDocForReading!!.fileSize,
                onDismiss = { selectedDocForReading = null }
            )
        } else {
            MockPdfViewerScreen(
                pdfName = selectedDocForReading!!.title,
                customContent = selectedDocForReading!!.fileContent,
                fileSize = selectedDocForReading!!.fileSize,
                onDismiss = { selectedDocForReading = null }
            )
        }
    }
}

@Composable
fun CourseDetailEnrollmentDialog(
    course: CourseEntity,
    viewModel: AcademyViewModel,
    onEnrollSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    val enrollments by viewModel.allEnrollments.collectAsStateWithLifecycle()
    val isEnrolled = enrollments.any { it.userEmail == viewModel.currentUser?.email && it.courseId == course.id }
    var showRazorpaySimulator by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                    if (course.imageUrl.isNotEmpty()) {
                        AsyncImage(model = course.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(BrandBluePrimary), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(60.dp), tint = Color.White)
                        }
                    }
                }
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(course.title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text(course.category, fontSize = 13.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("This batch includes direct access to all lectures, handwritten PDF notes, and MCQ test series for 2026 prep.", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                    if (isEnrolled) {
                        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Already Enrolled")
                        }
                    } else {
                        Button(
                            onClick = { if (course.isFree) { viewModel.enrollInCourse(course.id); onEnrollSuccess() } else { showRazorpaySimulator = true } },
                            modifier = Modifier.fillMaxWidth().testTag("enroll_button")
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
                if (success) { viewModel.enrollInCourse(course.id); onEnrollSuccess() }
            },
            onDismiss = { showRazorpaySimulator = false }
        )
    }
}

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
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1938))) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                    Icon(Icons.Default.Payment, contentDescription = null, tint = Color(0xFF3B82F6))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Razorpay Secure", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Text("Lakshya Academy Ghazipur Gateway", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                Text(courseTitle, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Box(modifier = Modifier.padding(16.dp).background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp)).padding(10.dp)) {
                    Text("Total Payable: ₹${coursePrice.toInt()}", color = Color(0xFF10B981), fontWeight = FontWeight.Black)
                }
                if (showProcessing) {
                    CircularProgressIndicator(color = Color(0xFF3B82F6))
                    LaunchedEffect(Unit) { delay(2000); onPaymentResult(true) }
                } else {
                    OutlinedTextField(value = phoneInput, onValueChange = { phoneInput = it }, label = { Text("Billing Phone", color = Color.LightGray) }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { showProcessing = true }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("razorpay_success_btn")) {
                        Text("Pay via Sandbox")
                    }
                }
            }
        }
    }
}

@Composable
fun EditProfileDialog(
    viewModel: AcademyViewModel,
    onDismiss: () -> Unit
) {
    val user = viewModel.currentUser ?: return
    var name by remember { mutableStateOf(user.name) }
    var mobile by remember { mutableStateOf(user.mobile) }
    var email by remember { mutableStateOf(user.email) }
    var photoUri by remember { mutableStateOf(user.photoUri) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            photoUri = uri.toString()
        }
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Edit Profile", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(16.dp))
                
                Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(Color.LightGray).clickable { 
                    launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, contentAlignment = Alignment.Center) {
                    if (photoUri.isNotEmpty()) {
                        AsyncImage(model = photoUri, contentDescription = "Profile Photo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Text(user.avatarEmoji, fontSize = 32.sp)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = mobile, onValueChange = { mobile = it }, label = { Text("Mobile") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(onClick = { 
                    viewModel.updateProfile(name, mobile, photoUri, email)
                    onDismiss() 
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Save")
                }
                TextButton(onClick = { 
                    viewModel.logout()
                    onDismiss() 
                }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)) {
                    Text("Logout")
                }
            }
        }
    }
}
