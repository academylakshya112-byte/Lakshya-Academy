package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.widget.Toast
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import com.example.R
import com.example.api.RetrofitClient
import com.example.BuildConfig
import com.example.data.Content
import com.example.data.GenerateContentRequest
import com.example.data.InlineData
import com.example.data.Part
import com.example.ui.viewmodel.AcademyViewModel

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val image: Bitmap? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiDoubtSolverScreen(viewModel: AcademyViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputQuery by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isGeneratingVideo by remember { mutableStateOf(false) }
    var videoProgress by remember { mutableStateOf(0f) }
    var teacherVideoMessage by remember { mutableStateOf<String?>(null) }
    
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    
    LaunchedEffect(Unit) {
        var textToSpeech: TextToSpeech? = null
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale("hi", "IN")
            }
        }
        tts = textToSpeech
    }
    
    DisposableEffect(Unit) {
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }
    
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
        uri?.let {
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(it)
                    val originalBitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    
                    if (originalBitmap != null) {
                        val maxDimension = 1024
                        val width = originalBitmap.width
                        val height = originalBitmap.height
                        val ratio = width.toFloat() / height.toFloat()
                        
                        var newWidth = width
                        var newHeight = height
                        if (width > maxDimension || height > maxDimension) {
                            if (ratio > 1) {
                                newWidth = maxDimension
                                newHeight = (maxDimension / ratio).toInt()
                            } else {
                                newHeight = maxDimension
                                newWidth = (maxDimension * ratio).toInt()
                            }
                        }
                        
                        val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
                        
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            selectedBitmap = scaledBitmap
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } catch (e: OutOfMemoryError) {
                    e.printStackTrace() // Catch OOM directly!
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Psychology, contentDescription = null, tint = Color(0xFF6366F1), modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Lakshya AI Desk", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Automatic Doubt Solver", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            Column {
                if (selectedBitmap != null) {
                    Box(modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                    ) {
                        Image(
                            bitmap = selectedBitmap!!.asImageBitmap(), 
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        IconButton(
                            onClick = { 
                                selectedBitmap = null
                                selectedImageUri = null
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Attach Photo", tint = Color.Gray)
                    }
                    
                    OutlinedTextField(
                        value = inputQuery,
                        onValueChange = { inputQuery = it },
                        placeholder = { Text("Ask your doubt...") },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF6366F1),
                            unfocusedBorderColor = Color.LightGray
                        )
                    )
                    
                    IconButton(
                        onClick = {
                            val q = inputQuery.trim()
                            val b = selectedBitmap
                            if (q.isNotEmpty() || b != null) {
                                messages = messages + ChatMessage(text = q, isUser = true, image = b)
                                inputQuery = ""
                                selectedBitmap = null
                                selectedImageUri = null
                                isLoading = true
                                
                                coroutineScope.launch {
                                    try {
                                        val partsList = mutableListOf<Part>()
                                        if (q.isNotEmpty()) partsList.add(Part(text = q))
                                        
                                        if (b != null) {
                                            val outputStream = ByteArrayOutputStream()
                                            b.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                                            val base64Img = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                                            partsList.add(Part(inlineData = InlineData("image/jpeg", base64Img)))
                                        }
                                        
                                        val req = GenerateContentRequest(
                                            contents = listOf(Content(parts = partsList)),
                                            systemInstruction = Content(parts = listOf(Part(text = "You are an expert AI tutor for Lakshya Academy. Help the student with their academic doubt politely and accurately. Explain the concept clearly using a mix of Hindi and English. Solve mathematical or logic problems step-by-step. Keep responses structured and clean. If an image is provided, analyze the question or diagram and help.")))
                                        )
                                        
                                        val res = RetrofitClient.service.generateContent(BuildConfig.MY_API_KEY, req)
                                        val ans = res.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "I am sorry, I could not understand the question. Could you clarify?"
                                        messages = messages + ChatMessage(text = ans, isUser = false)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        messages = messages + ChatMessage(text = "Error connecting to AI Server. Please check your connection or wait a moment.", isUser = false)
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF6366F1)),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .background(Color(0xFFF8FAFC))) {
            
            if (messages.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Psychology, null, tint = Color.LightGray, modifier = Modifier.size(80.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Lakshya AI Doubt Solver", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 20.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Send a photo of your question or type it below to get instant help.", color = Color.Gray, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(messages) { msg ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                        ) {
                            if (!msg.isUser) {
                                Icon(Icons.Default.Psychology, null, tint = Color(0xFF6366F1), modifier = Modifier.size(32.dp).padding(top = 4.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .background(
                                        if (msg.isUser) Color(0xFF6366F1) else Color.White,
                                        RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = if (msg.isUser) 16.dp else 4.dp,
                                            bottomEnd = if (msg.isUser) 4.dp else 16.dp
                                        )
                                    )
                                    .border(1.dp, if (msg.isUser) Color.Transparent else Color(0xFFE2E8F0), RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = if (msg.isUser) 16.dp else 4.dp,
                                            bottomEnd = if (msg.isUser) 4.dp else 16.dp
                                        ))
                                    .padding(12.dp)
                            ) {
                                if (msg.image != null) {
                                    Image(
                                        bitmap = msg.image.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 200.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Fit
                                    )
                                    if (msg.text.isNotEmpty()) Spacer(modifier = Modifier.height(8.dp))
                                }
                                if (msg.text.isNotEmpty()) {
                                    Text(
                                        text = msg.text,
                                        color = if (msg.isUser) Color.White else Color(0xFF334155),
                                        fontSize = 15.sp
                                    )
                                }
                                if (!msg.isUser) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextButton(
                                        onClick = {
                                            if (viewModel.incrementVideoQuota()) {
                                                coroutineScope.launch {
                                                    isGeneratingVideo = true
                                                    videoProgress = 0f
                                                    while(videoProgress < 1f) {
                                                        delay(100)
                                                        videoProgress += 0.015f
                                                    }
                                                    isGeneratingVideo = false
                                                    teacherVideoMessage = msg.text
                                                }
                                            } else {
                                                Toast.makeText(context, "Weekly video limit (5) reached!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF6366F1)),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Icon(Icons.Default.Psychology, null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Explain with Video (Left: ${viewModel.getVideoQuotaRemaining()})", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                    if (isLoading) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Psychology, null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color(0xFF6366F1))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Please wait...", color = Color.Gray)
                            }
                        }
                    }
                }
            }
            
            if (isGeneratingVideo) {
                Dialog(onDismissRequest = {}) {
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(16.dp)) {
                        Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(progress = { videoProgress }, modifier = Modifier.size(64.dp), strokeWidth = 6.dp, color = Color(0xFF6366F1), trackColor = Color.LightGray)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Generating Teacher Video...", fontWeight = FontWeight.Bold)
                            Text("${(videoProgress * 100).toInt()}%", color = Color.Gray)
                        }
                    }
                }
            }

            if (teacherVideoMessage != null) {
                val infiniteTransition = rememberInfiniteTransition()
                val imageScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.05f,
                    animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse)
                )
                val rotation by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing)))
                val pulse by infiniteTransition.animateFloat(initialValue = 0.8f, targetValue = 1.3f, animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Reverse))
                val floatY by infiniteTransition.animateFloat(initialValue = -10f, targetValue = 10f, animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse))
                val bar1 by infiniteTransition.animateFloat(initialValue = 0.3f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(300, easing = LinearEasing), RepeatMode.Reverse))
                val bar2 by infiniteTransition.animateFloat(initialValue = 0.8f, targetValue = 0.2f, animationSpec = infiniteRepeatable(tween(400, easing = LinearEasing), RepeatMode.Reverse))
                val bar3 by infiniteTransition.animateFloat(initialValue = 0.1f, targetValue = 0.9f, animationSpec = infiniteRepeatable(tween(250, easing = LinearEasing), RepeatMode.Reverse))
                val bar4 by infiniteTransition.animateFloat(initialValue = 0.6f, targetValue = 0.3f, animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse))

                var currentWordIndex by remember { mutableStateOf(0) }
                val words = teacherVideoMessage!!.split(" ")

                LaunchedEffect(teacherVideoMessage) {
                    tts?.speak(teacherVideoMessage, TextToSpeech.QUEUE_FLUSH, null, "teacher_speech")
                    for(i in words.indices) {
                        delay(400) // approx word speed
                        currentWordIndex = i
                    }
                }

                Dialog(onDismissRequest = { 
                    tts?.stop()
                    teacherVideoMessage = null 
                }) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().wrapContentHeight()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().background(Color.Black)) {
                            Box(modifier = Modifier.fillMaxWidth().height(350.dp).background(Color(0xFF0F172A))) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Icon(Icons.Default.Settings, null, tint = Color.White.copy(alpha=0.1f), modifier = Modifier.align(Alignment.TopStart).padding(32.dp).size(64.dp).graphicsLayer { rotationZ = rotation; translationY = floatY })
                                    Icon(Icons.Default.DateRange, null, tint = Color.White.copy(alpha=0.1f), modifier = Modifier.align(Alignment.BottomEnd).padding(48.dp).size(80.dp).graphicsLayer { rotationZ = -rotation; translationY = -floatY })
                                    Icon(Icons.Default.Star, null, tint = Color.White.copy(alpha=0.1f), modifier = Modifier.align(Alignment.TopEnd).padding(40.dp).size(56.dp).graphicsLayer { rotationZ = rotation * 1.2f; translationY = floatY * 0.5f })
                                    Icon(Icons.Default.AccountCircle, null, tint = Color.White.copy(alpha=0.1f), modifier = Modifier.align(Alignment.BottomStart).padding(32.dp).size(60.dp).graphicsLayer { rotationZ = -rotation * 0.8f; translationY = -floatY * 0.8f })
                                    
                                    Box(modifier = Modifier.align(Alignment.Center).size(120.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }.background(Color(0xFF6366F1).copy(alpha=0.2f), CircleShape))
                                    Box(modifier = Modifier.align(Alignment.Center).size(80.dp).graphicsLayer { scaleX = pulse * 1.1f; scaleY = pulse * 1.1f }.background(Color(0xFF6366F1).copy(alpha=0.4f), CircleShape))
                                    Icon(Icons.Default.Psychology, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(48.dp))
                                }
                                
                                // Subtitles
                                Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha=0.8f)))).padding(16.dp)) {
                                    Text(
                                        text = words.take(currentWordIndex + 1).takeLast(12).joinToString(" "),
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                // Audio Visualizer Overlay
                                Row(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).background(Color.Black.copy(alpha=0.5f), RoundedCornerShape(4.dp)).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                                    Box(modifier = Modifier.width(4.dp).height((24 * bar1).dp).background(Color(0xFF10B981), CircleShape))
                                    Box(modifier = Modifier.width(4.dp).height((24 * bar2).dp).background(Color(0xFF10B981), CircleShape))
                                    Box(modifier = Modifier.width(4.dp).height((24 * bar3).dp).background(Color(0xFF10B981), CircleShape))
                                    Box(modifier = Modifier.width(4.dp).height((24 * bar4).dp).background(Color(0xFF10B981), CircleShape))
                                }

                                // Live Tag
                                Row(modifier = Modifier.align(Alignment.TopStart).padding(16.dp).background(Color.Red, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(6.dp).background(Color.White, CircleShape))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("LIVE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }

                                // Watermark
                                Text(
                                    "rahul future army",
                                    color = Color.White.copy(alpha = 0.3f),
                                    fontSize = 10.sp,
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                                )
                            }
                            Column(modifier = Modifier.fillMaxWidth().background(Color.White).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Lakshya Audio-Visual Explainer", fontWeight = FontWeight.Bold, color = Color(0xFF6366F1))
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { 
                                    tts?.stop()
                                    teacherVideoMessage = null 
                                }) {
                                    Text("Stop Video")
                                }
                            }
                        }
                    }
                }
            }

            // Watermark
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.Center) {
                Text("rahul future army", color = Color.Gray, fontSize = 11.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
