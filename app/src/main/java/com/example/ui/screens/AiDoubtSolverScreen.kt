package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.api.RetrofitClient
import com.example.data.*
import com.example.ui.viewmodel.AcademyViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import android.speech.tts.TextToSpeech
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.util.Locale

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val image: Bitmap? = null,
    val isVideoExplanation: Boolean = false
)

fun cleanTextForSpeech(text: String): String {
    var result = text.replace(Regex("[\\*\\#\\_\\[\\]\\{\\}\\(\\)\\|\\<\\>\\`\\~]"), " ")
    result = result.replace(Regex("<[^>]*>"), " ")
    result = result.replace(Regex("[\\x{1F600}-\\x{1F64F}\\x{1F300}-\\x{1F5FF}\\x{1F680}-\\x{1F6FF}\\x{1F700}-\\x{1F77F}\\x{1F800}-\\x{1F8FF}\\x{1F900}-\\x{1F9FF}\\x{1FA00}-\\x{1FA6F}\\x{1FA70}-\\x{1FAFF}\\x{2600}-\\x{26FF}\\x{2700}-\\x{27BF}]"), " ")
    result = result.replace("+", " plus ")
                   .replace("-", " minus ")
                   .replace("=", " equals ")
                   .replace("/", " divided by ")
                   .replace("\n", ". ")
                   .replace(Regex("\\s+"), " ")
    return result.trim()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiDoubtSolverScreen(
    viewModel: AcademyViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf(ChatMessage("Namaste! 🙏 I am your AI Study Friend. Ask me anything in English or Hindi, and I'll help you score better! 🚀", false))) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var responseLanguage by remember { mutableStateOf("Bilingual") } // English, Hindi, Bilingual
    var remainingVideos by remember { mutableIntStateOf(3) }
    
    val listState = rememberLazyListState()

    // Load video limits
    LaunchedEffect(Unit) {
        remainingVideos = viewModel.getRemainingVideoCount()
    }

    var tts: TextToSpeech? by remember { mutableStateOf(null) }

    DisposableEffect(context) {
        var textToSpeech: TextToSpeech? = null
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val voices = textToSpeech?.voices
                val femaleVoice = voices?.firstOrNull { it.name.contains("female", ignoreCase = true) || it.name.contains("en-us-x-sfg", ignoreCase = true) }
                if (femaleVoice != null) {
                    textToSpeech?.voice = femaleVoice
                }
            }
        }
        tts = textToSpeech
        onDispose {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        }
    }

    // Speech Recognizer setup
    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    val speechIntent = remember(responseLanguage) {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (responseLanguage == "Hindi" || responseLanguage == "Bilingual") "hi-IN" else "en-US")
        }
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            speechRecognizer.startListening(speechIntent)
            isListening = true
        }
    }

    DisposableEffect(Unit) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) { isListening = false }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val spokenText = matches[0]
                    inputText = spokenText
                    // AUTO SEND
                    sendMessage(spokenText, selectedImageBitmap, responseLanguage, viewModel, coroutineScope, tts, onProcessing = { isProcessing = it }) { msg ->
                        messages = messages + msg
                    }
                    inputText = ""
                    selectedImageBitmap = null
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        speechRecognizer.setRecognitionListener(listener)
        onDispose { speechRecognizer.destroy() }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedImageUri = uri
        uri?.let {
            val inputStream = context.contentResolver.openInputStream(it)
            selectedImageBitmap = BitmapFactory.decodeStream(inputStream)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("AI Study Friend", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Bilingual Support Enabled", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Language Switcher
                    TextButton(onClick = {
                        responseLanguage = when(responseLanguage) {
                            "English" -> "Hindi"
                            "Hindi" -> "Bilingual"
                            else -> "English"
                        }
                    }) {
                        Text(responseLanguage, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF6366F1),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8FAFC))
        ) {
            // Weekly Video Limit Info
            Surface(
                color = Color(0xFFE0E7FF),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF4338CA))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Weekly Visual Explanations Remaining: $remainingVideos / 3",
                        fontSize = 11.sp,
                        color = Color(0xFF4338CA),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages) { message ->
                    ChatBubble(message)
                }
                if (isProcessing) {
                    item {
                        ThinkingAnimation()
                    }
                }
            }

            // Input Area
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = Color.White
            ) {
                Column {
                    if (selectedImageBitmap != null) {
                        Box(modifier = Modifier.padding(8.dp)) {
                            Image(
                                bitmap = selectedImageBitmap!!.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = { selectedImageBitmap = null; selectedImageUri = null },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { imagePicker.launch("image/*") }) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Attach Image", tint = Color(0xFF6366F1))
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                placeholder = { Text(if(isListening) "Listening..." else "Ask your doubt...") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                maxLines = 4,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF6366F1),
                                    unfocusedBorderColor = Color.LightGray
                                )
                            )
                            
                            // Voice Indicator
                            if (isListening) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 8.dp)
                                        .fillMaxWidth(0.8f)
                                        .height(2.dp),
                                    color = Color(0xFF6366F1)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Mic Button
                        IconButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    if (isListening) {
                                        speechRecognizer.stopListening()
                                        isListening = false
                                    } else {
                                        speechRecognizer.startListening(speechIntent)
                                        isListening = true
                                    }
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(if (isListening) Color.Red.copy(alpha = 0.1f) else Color(0xFFF1F5F9), CircleShape)
                        ) {
                            Icon(
                                if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
                                contentDescription = "Voice Input",
                                tint = if (isListening) Color.Red else Color(0xFF6366F1)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        FloatingActionButton(
                            onClick = {
                                sendMessage(inputText, selectedImageBitmap, responseLanguage, viewModel, coroutineScope, tts, onProcessing = { isProcessing = it }) { msg ->
                                    messages = messages + msg
                                }
                                inputText = ""
                                selectedImageBitmap = null
                                selectedImageUri = null
                            },
                            containerColor = Color(0xFF6366F1),
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        }
    }
}

private fun sendMessage(
    text: String,
    image: Bitmap?,
    language: String,
    viewModel: AcademyViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    tts: TextToSpeech?,
    onProcessing: (Boolean) -> Unit,
    onNewMessages: (List<ChatMessage>) -> Unit
) {
    if (text.isBlank() && image == null) return

    val userMsg = ChatMessage(text, true, image)
    onNewMessages(listOf(userMsg))
    
    onProcessing(true)
    scope.launch {
        solveDoubt(text, image, language, viewModel) { response, isVideo ->
            onNewMessages(listOf(ChatMessage(response, false, isVideoExplanation = isVideo)))
            onProcessing(false)
            
            // Text to speech
            tts?.let {
                val cleanResponse = cleanTextForSpeech(response)
                if (language.equals("Hindi", ignoreCase = true) || language.equals("Bilingual", ignoreCase = true)) {
                    it.language = Locale("hi", "IN")
                } else {
                    it.language = Locale.US
                }
                it.speak(cleanResponse, TextToSpeech.QUEUE_FLUSH, null, "utteranceId")
            }
        }
    }
}

@Composable
fun ThinkingAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(8.dp).scale(scale)
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF6366F1).copy(alpha = alpha),
            modifier = Modifier.size(12.dp)
        ) {}
        Spacer(modifier = Modifier.width(8.dp))
        Text("Your friend is thinking...", fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
    }
}

private suspend fun solveDoubt(
    text: String,
    image: Bitmap?,
    language: String,
    viewModel: AcademyViewModel,
    onResult: (String, Boolean) -> Unit
) {
    try {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            onResult("Error: API Key missing. Check Secrets.", false)
            return
        }

        // Check if user is asking for video explanation
        var isVideoRequest = text.lowercase().contains("video") || text.lowercase().contains("समझा") && text.length > 50
        
        val sysInstructionText = """
            You are a friendly, encouraging AI Study Assistant for school students in India. 
            Talk to students like a supportive elder sibling or friend. 
            Response Language: $language. 
            If Bilingual, provide the answer in clear English followed by a simple Hindi translation.
            Keep explanations easy to understand. 
            Use emojis to keep it engaging.
            If the user asks for a 'video' or 'detailed explanation', format your response with clear step-by-step visual descriptions, including ASCII art diagrams, flowcharts, or structured visual steps.
        """.trimIndent()

        val partList = mutableListOf<Part>()
        partList.add(Part(text = if (text.isBlank()) "Explain this doubt clearly." else text))

        image?.let {
            val baos = ByteArrayOutputStream()
            it.compress(Bitmap.CompressFormat.JPEG, 60, baos)
            val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            partList.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64)))
        }

        val req = GenerateContentRequest(
            contents = listOf(Content(parts = partList)),
            systemInstruction = Content(parts = listOf(Part(text = sysInstructionText)))
        )
        
        var success = false
        var lastError = ""
        var availableModels = listOf<String>()
        val targetVersion = "v1beta" // v1beta is required for systemInstruction
        
        try {
            android.util.Log.d("GEMINI_DEBUG", "Calling ListModels for version $targetVersion")
            val modelsResponse = RetrofitClient.service.listModels(targetVersion, apiKey)
            availableModels = modelsResponse.models
                .filter { it.supportedGenerationMethods?.contains("generateContent") == true }
                .map { it.name.removePrefix("models/") }
            android.util.Log.d("GEMINI_DEBUG", "Available models: $availableModels")
        } catch (e: Exception) {
            lastError = "Failed to list models: ${e.message}"
            android.util.Log.e("GEMINI_DEBUG", lastError)
        }

        val modelsToTry = mutableListOf<String>()
        if (availableModels.isNotEmpty()) {
            val preferredModels = listOf("gemini-1.5-flash", "gemini-1.5-pro", "gemini-2.0-flash-exp")
            for (pref in preferredModels) {
                if (availableModels.contains(pref)) {
                    modelsToTry.add(pref)
                }
            }
            if (modelsToTry.isEmpty()) {
                modelsToTry.add(availableModels.first())
            }
        } else {
            modelsToTry.add("gemini-1.5-flash")
        }

        android.util.Log.d("GEMINI_DEBUG", "Models to try: $modelsToTry")

        for (model in modelsToTry) {
            try {
                val fullPath = "$targetVersion/models/$model:generateContent"
                android.util.Log.d("GEMINI_DEBUG", "Calling generateContent: $fullPath")
                
                val response = RetrofitClient.service.generateContent(fullPath, apiKey, req)
                val responseText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                android.util.Log.d("GEMINI_DEBUG", "Response received successfully")
                    
                    if (!responseText.isNullOrBlank()) {
                        var finalOutput = responseText
                        var wasVideoGenerated = false
                        
                        if (isVideoRequest) {
                            val canGenerate = viewModel.checkAndUseVideoLimit()
                            if (canGenerate) {
                                finalOutput = "🎬 **Visual Explanation Generated**\n\n$responseText"
                                wasVideoGenerated = true
                            } else {
                                finalOutput = "⚠️ Weekly video limit reached. Providing text explanation:\n\n$responseText"
                            }
                        }
                        
                        onResult(finalOutput, wasVideoGenerated)
                        success = true
                        break
                    }
                } catch (e: retrofit2.HttpException) {
                    val code = e.code()
                    val errorBody = e.response()?.errorBody()?.string() ?: "No error body"
                    lastError = "HTTP $code ($model/$targetVersion): $errorBody"
                    android.util.Log.e("GEMINI_DEBUG", lastError)
                    if (code == 401 || code == 403) break 
                } catch (e: Exception) {
                    lastError = "Error ($model/$targetVersion): ${e.message}"
                    android.util.Log.e("GEMINI_DEBUG", lastError)
                }
        }

        if (!success) {
            onResult("Oops! I missed that. Try again? Details: $lastError", false)
        }

    } catch (e: Exception) {
        onResult("Error: ${e.localizedMessage}", false)
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val bgColor = if (message.isUser) Color(0xFF6366F1) else Color.White
    val textColor = if (message.isUser) Color.White else Color.Black
    val shape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (message.isUser) 16.dp else 2.dp,
        bottomEnd = if (message.isUser) 2.dp else 16.dp
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = bgColor,
            shape = shape,
            tonalElevation = if (message.isUser) 4.dp else 1.dp,
            shadowElevation = 1.dp,
            border = if (message.isUser) null else BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (message.image != null) {
                    Image(
                        bitmap = message.image.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .sizeIn(maxWidth = 240.dp, maxHeight = 300.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                if (message.isVideoExplanation) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                            .fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color(0xFF6366F1))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Visual Short Content", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(
                    text = message.text,
                    color = textColor,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }
        }
        Text(
            text = if (message.isUser) "You" else "Friend",
            fontSize = 10.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
        )
    }
}
