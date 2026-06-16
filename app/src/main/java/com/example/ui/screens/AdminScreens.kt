package com.example.ui.screens

import android.content.Context
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
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
import com.example.ui.viewmodel.AcademyViewModel

@Composable
fun AdminAnalyticsDashboard(viewModel: AcademyViewModel) {
    val courses by viewModel.allCourses.collectAsStateWithLifecycle()
    val banners by viewModel.allBanners.collectAsStateWithLifecycle()
    val tests by viewModel.allTests.collectAsStateWithLifecycle()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Admin Real-time Analytics", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AnalyticsStatCard("Total Batches", courses.size.toString(), Icons.Default.LibraryBooks, Color(0xFF6366F1), modifier = Modifier.weight(1f))
                AnalyticsStatCard("Active Tests", tests.size.toString(), Icons.Default.Quiz, Color(0xFF10B981), modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AnalyticsStatCard("Promo Banners", banners.size.toString(), Icons.Default.Image, Color(0xFFF59E0B), modifier = Modifier.weight(1f))
                AnalyticsStatCard("Handouts", "24+", Icons.Default.Description, Color(0xFFEC4899), modifier = Modifier.weight(1f))
            }
            AdminLiveClassManager(viewModel)
        }
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
    var bTitle by remember { mutableStateOf("") }
    var bUrl by remember { mutableStateOf("") }
    var bLink by remember { mutableStateOf("") }
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val file = File(context.filesDir, "banner_" + System.currentTimeMillis() + ".jpg")
                val outputStream = FileOutputStream(file)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()
                bUrl = file.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
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
                Button(onClick = { launcher.launch("image/*") }) {
                    Text("Upload\nImage", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            
            if (bUrl.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Selected Thumbnail Preview:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                val previewModel = if (bUrl.startsWith("/")) java.io.File(bUrl) else bUrl
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
            
            Box(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Button(
                    onClick = { 
                        if (bTitle.isNotBlank()) {
                            viewModel.adminAddBanner(bTitle, bUrl, if(bLink.isBlank()) "COURSES" else bLink, "VIEW NOW")
                            bTitle = ""
                            bUrl = ""
                            bLink = ""
                        }
                    }, 
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Text("Add Banner")
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
                        }
                    },
                    leadingContent = {
                        val imgModel = if (banner.imageUrl.startsWith("/")) java.io.File(banner.imageUrl) else banner.imageUrl
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
                        IconButton(onClick = { viewModel.adminDeleteBanner(banner.id) }) { 
                            Icon(Icons.Default.Delete, contentDescription = "Delete Banner", tint = Color.Red) 
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}
