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
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.os.Bundle
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.Manifest
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.DialogProperties
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
    var uncroppedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showCropDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isGeneratingVideo by remember { mutableStateOf(false) }
    var videoProgress by remember { mutableStateOf(0f) }
    var teacherVideoMessage by remember { mutableStateOf<String?>(null) }
    var selectedLanguage by remember { mutableStateOf("Hindi") }
    var videoQuotaRemaining by remember { mutableStateOf(viewModel.getVideoQuotaRemaining()) }
    
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    
    LaunchedEffect(Unit) {
        var textToSpeech: TextToSpeech? = null
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = if (selectedLanguage == "Hindi") Locale("hi", "IN") else Locale.US
            }
        }
        tts = textToSpeech
    }

    LaunchedEffect(selectedLanguage, tts) {
        tts?.language = if (selectedLanguage == "Hindi") Locale("hi", "IN") else Locale.US
    }
    
    DisposableEffect(Unit) {
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    var isListening by remember { mutableStateOf(false) }
    var currentVolumeDb by remember { mutableFloatStateOf(0f) }
    
    val speechContext = remember(context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            context.createAttributionContext("voice_input")
        } else {
            context
        }
    }
    
    val speechRecognizer = remember {
        try {
            if (android.speech.SpeechRecognizer.isRecognitionAvailable(speechContext)) {
                android.speech.SpeechRecognizer.createSpeechRecognizer(speechContext)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    val recognitionListener = remember {
        object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {
                isListening = true
                currentVolumeDb = 0f
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                currentVolumeDb = rmsdB.coerceIn(0f, 12f)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
            }
            override fun onError(error: Int) {
                isListening = false
                val errMsg = when(error) {
                    android.speech.SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    android.speech.SpeechRecognizer.ERROR_CLIENT -> "Client error"
                    android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    android.speech.SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    android.speech.SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Try again."
                    android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy. Please wait."
                    android.speech.SpeechRecognizer.ERROR_SERVER -> "Server error"
                    android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Try again."
                    else -> "Voice input error"
                }
                Toast.makeText(context, errMsg, Toast.LENGTH_SHORT).show()
            }
            override fun onResults(results: android.os.Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val spokenText = matches[0]
                    if (spokenText.isNotBlank()) {
                        inputQuery = if (inputQuery.isBlank()) spokenText else "$inputQuery $spokenText"
                    }
                }
            }
            override fun onPartialResults(partialResults: android.os.Bundle?) {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        }
    }

    DisposableEffect(speechRecognizer) {
        speechRecognizer?.setRecognitionListener(recognitionListener)
        onDispose {
            try {
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            uncroppedBitmap = originalBitmap
                            showCropDialog = true
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } catch (e: OutOfMemoryError) {
                    e.printStackTrace()
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            uncroppedBitmap = bitmap
            showCropDialog = true
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(context, "Camera permission is required to capture photos.", Toast.LENGTH_SHORT).show()
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, if (selectedLanguage == "Hindi") "hi-IN" else "en-US")
                putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            try {
                speechRecognizer?.startListening(intent)
                isListening = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            Toast.makeText(context, "Microphone permission is required for voice input.", Toast.LENGTH_SHORT).show()
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
                actions = {
                    Row(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .background(Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                            .padding(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (selectedLanguage == "Hindi") Color(0xFF6366F1) else Color.Transparent)
                                .clickable { selectedLanguage = "Hindi" }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "हिन्दी",
                                color = if (selectedLanguage == "Hindi") Color.White else Color.DarkGray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (selectedLanguage == "English") Color(0xFF6366F1) else Color.Transparent)
                                .clickable { selectedLanguage = "English" }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "Eng",
                                color = if (selectedLanguage == "English") Color.White else Color.DarkGray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
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
                    OutlinedTextField(
                        value = inputQuery,
                        onValueChange = { inputQuery = it },
                        placeholder = { Text("Ask your doubt...") },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        shape = RoundedCornerShape(24.dp),
                        leadingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                                IconButton(
                                    onClick = { photoPickerLauncher.launch("image/*") },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Gallery", tint = Color(0xFF6366F1), modifier = Modifier.size(20.dp))
                                }
                                IconButton(
                                    onClick = {
                                        val hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                                        if (hasCameraPermission) {
                                            cameraLauncher.launch(null)
                                        } else {
                                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.PhotoCamera, contentDescription = "Camera", tint = Color(0xFF6366F1), modifier = Modifier.size(20.dp))
                                }
                            }
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val hasMicPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                    if (hasMicPermission) {
                                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (selectedLanguage == "Hindi") "hi-IN" else "en-US")
                                            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                                        }
                                        try {
                                            speechRecognizer?.startListening(intent)
                                            isListening = true
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            Toast.makeText(context, "Voice error. Try again.", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = "Voice Input", tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                            }
                        },
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
                                        
                                        val systemPrompt = if (selectedLanguage == "Hindi") {
                                            "You are an expert AI tutor for Lakshya Academy. Help the student with their academic doubt politely and accurately. You MUST explain the concept clearly and entirely in simple, easy-to-understand Hindi (using Hindi Devanagari script). Solve mathematical or logic problems step-by-step. Keep responses structured and clean. If an image is provided, analyze the question or diagram and help."
                                        } else {
                                            "You are an expert AI tutor for Lakshya Academy. Help the student with their academic doubt politely and accurately. You MUST explain the concept clearly and entirely in clean, easy-to-understand English. Solve mathematical or logic problems step-by-step. Keep responses structured and clean. If an image is provided, analyze the question or diagram and help."
                                        }
                                        val req = GenerateContentRequest(
                                            contents = listOf(Content(parts = partsList)),
                                            systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
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
                                                videoQuotaRemaining = viewModel.getVideoQuotaRemaining()
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
                                        Text("Explain with Video (Left: $videoQuotaRemaining)", fontSize = 12.sp)
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
                val cleanSpeechText = remember(teacherVideoMessage) {
                    sanitizeTextForCleanSpeechAndSubtitles(teacherVideoMessage ?: "")
                }
                val words = remember(cleanSpeechText) { cleanSpeechText.split(" ") }

                LaunchedEffect(teacherVideoMessage, selectedLanguage) {
                    val currentTts = tts
                    if (currentTts != null) {
                        if (selectedLanguage == "Hindi") {
                            currentTts.language = Locale("hi", "IN")
                        } else {
                            currentTts.language = Locale.US
                        }
                        currentTts.speak(cleanSpeechText, TextToSpeech.QUEUE_FLUSH, null, "teacher_speech")
                    }
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

    if (showCropDialog && uncroppedBitmap != null) {
        ImageCropperDialog(
            bitmap = uncroppedBitmap!!,
            onCropSuccess = { cropped ->
                selectedBitmap = cropped
                showCropDialog = false
                uncroppedBitmap = null
            },
            onDismiss = {
                showCropDialog = false
                uncroppedBitmap = null
            }
        )
    }

    if (isListening) {
        Dialog(
            onDismissRequest = {
                try { speechRecognizer?.cancel() } catch (e: Exception) {}
                isListening = false
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val infiniteTransition = rememberInfiniteTransition()
            val pulseScale1 by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.3f,
                animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse)
            )
            val pulseScale2 by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.6f,
                animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse)
            )
            val rotationAngle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing))
            )

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black.copy(alpha = 0.8f)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (selectedLanguage == "Hindi") "सुन रहा हूँ... प्रश्न पूछें" else "Listening... ask your doubt",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (selectedLanguage == "Hindi") "कृपया स्पष्ट और ऊंची आवाज में बोलें" else "Please speak clearly",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    Box(
                        modifier = Modifier.size(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val voiceGlowFactor = 1f + (currentVolumeDb / 15f)
                        
                        // Outermost aura
                        Box(
                            modifier = Modifier
                                .size(180.dp)
                                .graphicsLayer {
                                    scaleX = pulseScale2 * voiceGlowFactor
                                    scaleY = pulseScale2 * voiceGlowFactor
                                    alpha = 0.15f
                                }
                                .background(Brush.radialGradient(listOf(Color(0xFF6366F1), Color.Transparent)), CircleShape)
                        )

                        // Middle aura
                        Box(
                            modifier = Modifier
                                .size(130.dp)
                                .graphicsLayer {
                                    scaleX = pulseScale1 * voiceGlowFactor
                                    scaleY = pulseScale1 * voiceGlowFactor
                                    alpha = 0.35f
                                }
                                .background(Brush.radialGradient(listOf(Color(0xFF818CF8), Color.Transparent)), CircleShape)
                        )

                        // Swirling ring
                        Canvas(
                            modifier = Modifier
                                .size(110.dp)
                                .graphicsLayer {
                                    rotationZ = rotationAngle
                                }
                        ) {
                            drawCircle(
                                brush = Brush.sweepGradient(
                                    colors = listOf(
                                        Color(0xFF6366F1),
                                        Color(0xFF34D399),
                                        Color(0xFF60A5FA),
                                        Color(0xFF818CF8),
                                        Color(0xFF6366F1)
                                    )
                                ),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx()),
                                radius = size.width / 2f
                            )
                        }

                        // Mic icon
                        Surface(
                            modifier = Modifier.size(80.dp),
                            shape = CircleShape,
                            color = Color(0xFF6366F1),
                            tonalElevation = 8.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Mic Icon",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(48.dp))

                    // Bouncing visualizer bars
                    Row(
                        modifier = Modifier
                            .height(40.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val numBars = 7
                        for (i in 0 until numBars) {
                            val barDelay = i * 100
                            val barScale by infiniteTransition.animateFloat(
                                initialValue = 0.15f,
                                targetValue = 0.85f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(350, delayMillis = barDelay, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                )
                            )
                            val adjustedHeight = (12.dp + (currentVolumeDb * 2f).dp) * barScale
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .width(5.dp)
                                    .height(adjustedHeight)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        brush = Brush.verticalGradient(
                                            listOf(Color(0xFF34D399), Color(0xFF6366F1))
                                        )
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(48.dp))

                    Button(
                        onClick = {
                            try { speechRecognizer?.stopListening() } catch (e: Exception) {}
                            isListening = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Stop", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun ImageCropperDialog(
    bitmap: Bitmap,
    onCropSuccess: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    var cropLeft by remember { mutableFloatStateOf(0.1f) }
    var cropTop by remember { mutableFloatStateOf(0.1f) }
    var cropRight by remember { mutableFloatStateOf(0.9f) }
    var cropBottom by remember { mutableFloatStateOf(0.9f) }

    var activeHandle by remember { mutableStateOf<String?>(null) }
    val imageAspectRatio = remember(bitmap) { bitmap.width.toFloat() / bitmap.height.toFloat() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Crop Photo",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(imageAspectRatio)
                            .pointerInput(cropLeft, cropTop, cropRight, cropBottom) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val touchX = offset.x / size.width
                                        val touchY = offset.y / size.height
                                        val distTL = Math.hypot((touchX - cropLeft).toDouble(), (touchY - cropTop).toDouble())
                                        val distTR = Math.hypot((touchX - cropRight).toDouble(), (touchY - cropTop).toDouble())
                                        val distBL = Math.hypot((touchX - cropLeft).toDouble(), (touchY - cropBottom).toDouble())
                                        val distBR = Math.hypot((touchX - cropRight).toDouble(), (touchY - cropBottom).toDouble())
                                        val threshold = 0.08f
                                        activeHandle = when {
                                            distTL < threshold -> "TL"
                                            distTR < threshold -> "TR"
                                            distBL < threshold -> "BL"
                                            distBR < threshold -> "BR"
                                            touchX in cropLeft..cropRight && touchY in cropTop..cropBottom -> "CENTER"
                                            else -> null
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val dx = dragAmount.x / size.width
                                        val dy = dragAmount.y / size.height
                                        when (activeHandle) {
                                            "TL" -> {
                                                cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - 0.15f)
                                                cropTop = (cropTop + dy).coerceIn(0f, cropBottom - 0.15f)
                                            }
                                            "TR" -> {
                                                cropRight = (cropRight + dx).coerceIn(cropLeft + 0.15f, 1f)
                                                cropTop = (cropTop + dy).coerceIn(0f, cropBottom - 0.15f)
                                            }
                                            "BL" -> {
                                                cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - 0.15f)
                                                cropBottom = (cropBottom + dy).coerceIn(cropTop + 0.15f, 1f)
                                            }
                                            "BR" -> {
                                                cropRight = (cropRight + dx).coerceIn(cropLeft + 0.15f, 1f)
                                                cropBottom = (cropBottom + dy).coerceIn(cropTop + 0.15f, 1f)
                                            }
                                            "CENTER" -> {
                                                val w = cropRight - cropLeft
                                                val h = cropBottom - cropTop
                                                var newLeft = cropLeft + dx
                                                var newTop = cropTop + dy
                                                if (newLeft < 0f) newLeft = 0f
                                                if (newLeft + w > 1f) newLeft = 1f - w
                                                if (newTop < 0f) newTop = 0f
                                                if (newTop + h > 1f) newTop = 1f - h
                                                cropLeft = newLeft
                                                cropRight = newLeft + w
                                                cropTop = newTop
                                                cropBottom = newTop + h
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        activeHandle = null
                                    }
                                )
                            }
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Uncropped Image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds
                        )

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height

                            val leftPx = cropLeft * w
                            val topPx = cropTop * h
                            val rightPx = cropRight * w
                            val bottomPx = cropBottom * h

                            drawRect(color = Color.Black.copy(alpha = 0.6f), topLeft = androidx.compose.ui.geometry.Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(w, topPx))
                            drawRect(color = Color.Black.copy(alpha = 0.6f), topLeft = androidx.compose.ui.geometry.Offset(0f, bottomPx), size = androidx.compose.ui.geometry.Size(w, h - bottomPx))
                            drawRect(color = Color.Black.copy(alpha = 0.6f), topLeft = androidx.compose.ui.geometry.Offset(0f, topPx), size = androidx.compose.ui.geometry.Size(leftPx, bottomPx - topPx))
                            drawRect(color = Color.Black.copy(alpha = 0.6f), topLeft = androidx.compose.ui.geometry.Offset(rightPx, topPx), size = androidx.compose.ui.geometry.Size(w - rightPx, bottomPx - topPx))

                            drawRect(
                                color = Color.White,
                                topLeft = androidx.compose.ui.geometry.Offset(leftPx, topPx),
                                size = androidx.compose.ui.geometry.Size(rightPx - leftPx, bottomPx - topPx),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                            )

                            val handleRadius = 10.dp.toPx()
                            drawCircle(color = Color(0xFF6366F1), radius = handleRadius, center = androidx.compose.ui.geometry.Offset(leftPx, topPx))
                            drawCircle(color = Color(0xFF6366F1), radius = handleRadius, center = androidx.compose.ui.geometry.Offset(rightPx, topPx))
                            drawCircle(color = Color(0xFF6366F1), radius = handleRadius, center = androidx.compose.ui.geometry.Offset(leftPx, bottomPx))
                            drawCircle(color = Color(0xFF6366F1), radius = handleRadius, center = androidx.compose.ui.geometry.Offset(rightPx, bottomPx))
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            val x = (cropLeft * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                            val y = (cropTop * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                            var width = ((cropRight - cropLeft) * bitmap.width).toInt()
                            var height = ((cropBottom - cropTop) * bitmap.height).toInt()

                            if (width <= 0) width = 1
                            if (height <= 0) height = 1

                            val finalWidth = if (x + width > bitmap.width) bitmap.width - x else width
                            val finalHeight = if (y + height > bitmap.height) bitmap.height - y else height

                            try {
                                val cropped = Bitmap.createBitmap(bitmap, x, y, finalWidth, finalHeight)
                                if (cropped != null) {
                                    onCropSuccess(cropped)
                                } else {
                                    onDismiss()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                    ) {
                        Text("Save Crop", color = Color.White)
                    }
                }
            }
        }
    }
}

fun sanitizeTextForCleanSpeechAndSubtitles(text: String): String {
    var cleaned = text
        .replace("**", "")
        .replace("*", "")
        .replace("##", "")
        .replace("#", "")
        .replace("__", "")
        .replace("_", "")
        .replace("`", "")
        .replace("[", "")
        .replace("]", "")
        .replace("(", "")
        .replace(")", "")
        .replace("{", "")
        .replace("}", "")
        .replace("/", " ")
        .replace("\\", " ")
        .replace("•", " ")
        .replace("-", " ")
        .replace("+", " ")
        .replace("=", " ")
        .replace("~", " ")
    
    // Replace multiple spaces with a single space
    return cleaned.replace(Regex("\\s+"), " ").trim()
}
