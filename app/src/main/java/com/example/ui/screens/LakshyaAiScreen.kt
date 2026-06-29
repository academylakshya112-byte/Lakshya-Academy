package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.ContextCompat
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.api.RetrofitClient
import com.example.data.Content
import com.example.data.GenerateContentRequest
import com.example.data.InlineData
import com.example.data.Part
import com.example.ui.viewmodel.AcademyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.*

// Cache of supported models retrieved from the API to avoid redundant calls.
private var supportedModelsCache: List<String> = emptyList()

suspend fun fetchSupportedModels(apiKey: String): List<String> {
    if (supportedModelsCache.isNotEmpty()) {
        return supportedModelsCache
    }
    return withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("GeminiDebug", "Fetching supported models list from listModels API...")
            val response = RetrofitClient.service.listModels("v1beta", apiKey)
            val models = response.models
                .filter { model ->
                    model.supportedGenerationMethods?.contains("generateContent") == true
                }
                .map { it.name.removePrefix("models/") }
            
            android.util.Log.d("GeminiDebug", "Fetched supported models list successfully: $models")
            supportedModelsCache = models
            models
        } catch (e: Exception) {
            android.util.Log.e("GeminiDebug", "Failed to fetch supported models from API: ${e.message}", e)
            emptyList()
        }
    }
}

fun getAndValidateApiKey(context: android.content.Context): String {
    val rawKey = BuildConfig.GEMINI_API_KEY
    val customApiKey = rawKey.trim().removeSurrounding("\"").removeSurrounding("'").trim()
    val exists = customApiKey.isNotEmpty() && customApiKey != "YOUR_GEMINI_API_KEY" && customApiKey != "placeholder" && customApiKey != "null"
    val length = if (exists) customApiKey.length else 0
    val first6 = if (exists && customApiKey.length >= 6) customApiKey.take(6) else if (exists) customApiKey else ""
    val masked = if (exists) "$first6${"*".repeat((length - 6).coerceAtLeast(0))}" else "N/A"
    
    android.util.Log.d("GeminiDebug", "=================== API KEY INITIALIZATION ===================")
    android.util.Log.d("GeminiDebug", "Secret exists: $exists")
    android.util.Log.d("GeminiDebug", "Key length: $length")
    android.util.Log.d("GeminiDebug", "First 6 characters: $masked")
    android.util.Log.d("GeminiDebug", "==============================================================")
    
    if (!exists) {
        val errMsg = "Missing Key: Gemini API Key is missing or set to the default placeholder. Please configure your actual API key in the Secrets panel (key icon) in the AI Studio sidebar."
        try {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, errMsg, android.widget.Toast.LENGTH_LONG).show()
            }
        } catch (ignored: Exception) {}
        throw Exception(errMsg)
    }
    return customApiKey
}

// Model definitions for Lakshya AI
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val bitmap: Bitmap? = null,
    val pdfUri: Uri? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isBookmarked: Boolean = false
)

data class MockQuestion(
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val solution: String
)

// Premium UI Color Palette
val CosmicDeepDark = Color(0xFF090616)
val CosmicMediumDark = Color(0xFF130F2C)
val CosmicLightDark = Color(0xFF1B173E)
val PrimaryNeonViolet = Color(0xFF8B5CF6)
val SecondaryNeonCyan = Color(0xFF06B6D4)
val GlowingPurpleBorder = Color(0x5E8B5CF6)
val SoftCyanBorder = Color(0x4006B6D4)
val GlassBackgroundWhite = Color(0x0EFFFFFF)
val GlassBackgroundPurple = Color(0x1F8B5CF6)

// Reusable Glassmorphism Modifier
fun Modifier.glassCard(
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp),
    bgColor: Color = GlassBackgroundWhite,
    borderColor: Color = Color(0x23FFFFFF)
) = this
    .background(bgColor, shape)
    .border(1.dp, borderColor, shape)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LakshyaAiScreen(viewModel: AcademyViewModel, onBack: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Persistent storage for preferences, counters, and progress
    val prefs = remember { context.getSharedPreferences("lakshya_ai_prefs_v5", Context.MODE_PRIVATE) }
    
    // Core states
    var selectedExamMode by remember { mutableStateOf(prefs.getString("selected_exam_mode", "NEET") ?: "NEET") }
    var selectedDifficultyMode by remember { mutableStateOf(prefs.getString("selected_difficulty_mode", "Medium") ?: "Medium") }
    
    // Counters
    var imagesCountToday by remember { mutableStateOf(prefs.getInt("images_count_today", 0)) }
    var voiceCountThisWeek by remember { mutableStateOf(prefs.getInt("voice_count_this_week", 0)) }
    var videoCountThisWeek by remember { mutableStateOf(prefs.getInt("video_count_this_week", 0)) }
    
    // Analytics
    var totalCorrectAnswers by remember { mutableStateOf(prefs.getInt("total_correct_answers", 14)) }
    var totalWrongAnswers by remember { mutableStateOf(prefs.getInt("total_wrong_answers", 6)) }
    var progressPct by remember { mutableStateOf(prefs.getFloat("learning_progress_pct", 0.65f)) }

    // TTS implementation
    var tts: TextToSpeech? by remember { mutableStateOf(null) }
    var isTtsSpeaking by remember { mutableStateOf(false) }
    var spokenText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("hi", "IN") // Hindi/English hybrid
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    val speakText: (String) -> Unit = { text ->
        if (voiceCountThisWeek >= 5) {
            Toast.makeText(context, "Weekly limit of 5 Voice conversations reached!", Toast.LENGTH_LONG).show()
        } else {
            tts?.stop()
            spokenText = text
            val speechResult = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "LakshyaTTS")
            if (speechResult == TextToSpeech.SUCCESS) {
                isTtsSpeaking = true
                voiceCountThisWeek++
                prefs.edit().putInt("voice_count_this_week", voiceCountThisWeek).apply()
            }
        }
    }

    val stopTts: () -> Unit = {
        tts?.stop()
        isTtsSpeaking = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(CosmicDeepDark, CosmicMediumDark, CosmicDeepDark)
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                    title = { 
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(SecondaryNeonCyan, CircleShape)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Lakshya AI", 
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.sp,
                                        color = Color.White
                                    )
                                )
                            }
                            Text(
                                "AI Teacher & Study Coach", 
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFFA5B4FC),
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            stopTts()
                            Toast.makeText(context, "Speech Stopped", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.VolumeMute, contentDescription = "Stop Speech", tint = Color(0xFFEF4444))
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 8.dp,
                    containerColor = Color.Transparent,
                    contentColor = PrimaryNeonViolet,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = PrimaryNeonViolet
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    val tabItems = listOf(
                        Icons.Default.School to "AI Teacher",
                        Icons.Default.Settings to "Exam Mode",
                        Icons.Default.Quiz to "Mock Test",
                        Icons.Default.BarChart to "Analytics",
                        Icons.Default.VideoLibrary to "AI Labs"
                    )
                    tabItems.forEachIndexed { index, (icon, text) ->
                        val isSelected = selectedTab == index
                        Tab(
                            selected = isSelected, 
                            onClick = { selectedTab = index }, 
                            text = { 
                                Row(verticalAlignment = Alignment.CenterVertically) { 
                                    Icon(
                                        icon, 
                                        contentDescription = null, 
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isSelected) PrimaryNeonViolet else Color(0xFF94A3B8)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text, 
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color.White else Color(0xFF94A3B8)
                                        )
                                    ) 
                                } 
                            }
                        )
                    }
                }
                
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> AiTeacherTab(
                            examMode = selectedExamMode,
                            difficultyMode = selectedDifficultyMode,
                            imagesCount = imagesCountToday,
                            onImageUsed = {
                                imagesCountToday++
                                prefs.edit().putInt("images_count_today", imagesCountToday).apply()
                            },
                            onSpeakRequest = speakText
                        )
                        1 -> ExamModeTab(
                            selectedExam = selectedExamMode,
                            onExamSelected = {
                                selectedExamMode = it
                                prefs.edit().putString("selected_exam_mode", it).apply()
                            },
                            selectedDifficulty = selectedDifficultyMode,
                            onDifficultySelected = {
                                selectedDifficultyMode = it
                                prefs.edit().putString("selected_difficulty_mode", it).apply()
                            }
                        )
                        2 -> MockTestTab(
                            examMode = selectedExamMode,
                            onScoreAdded = { correct, wrong ->
                                totalCorrectAnswers += correct
                                totalWrongAnswers += wrong
                                progressPct = (progressPct + 0.05f).coerceAtMost(1.0f)
                                prefs.edit()
                                    .putInt("total_correct_answers", totalCorrectAnswers)
                                    .putInt("total_wrong_answers", totalWrongAnswers)
                                    .putFloat("learning_progress_pct", progressPct)
                                    .apply()
                            }
                        )
                        3 -> AnalyticsTab(
                            correct = totalCorrectAnswers,
                            wrong = totalWrongAnswers,
                            progress = progressPct
                        )
                        4 -> AiLabsTab(
                            voiceCount = voiceCountThisWeek,
                            videoCount = videoCountThisWeek,
                            onVideoGenerated = {
                                videoCountThisWeek++
                                prefs.edit().putInt("video_count_this_week", videoCountThisWeek).apply()
                            },
                            onTtsRequested = speakText,
                            isTtsSpeaking = isTtsSpeaking,
                            spokenText = spokenText,
                            onStopSpeech = stopTts
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AiTeacherTab(
    examMode: String,
    difficultyMode: String,
    imagesCount: Int,
    onImageUsed: () -> Unit,
    onSpeakRequest: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("lakshya_ai_prefs_v5", Context.MODE_PRIVATE) }
    
    // Core Message List and State Persistence
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var inputText by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var loadStatusText by remember { mutableStateOf("") }
    
    // Bookmark doubts and Saved Past Sessions
    var bookmarkedDoubts by remember { mutableStateOf(prefs.getStringSet("bookmarked_doubts_set", emptySet()) ?: emptySet()) }
    var savedSessionTitles by remember { mutableStateOf(prefs.getStringSet("saved_sessions_titles", emptySet()) ?: emptySet()) }
    
    var isBookmarksExpanded by remember { mutableStateOf(false) }
    var isHistoryExpanded by remember { mutableStateOf(false) }
    
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }

    // Initial Loading of History from SharedPrefs
    LaunchedEffect(Unit) {
        val savedHistoryJson = prefs.getString("active_chat_history_json", "") ?: ""
        if (savedHistoryJson.isNotBlank()) {
            try {
                val array = JSONArray(savedHistoryJson)
                messages.clear()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    messages.add(
                        ChatMessage(
                            text = obj.getString("text"),
                            isUser = obj.getBoolean("isUser"),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            } catch (e: Exception) {
                // Fail-safe default
            }
        }
        
        if (messages.isEmpty()) {
            messages.add(
                ChatMessage(
                    text = "Hello! I am your Lakshya AI 5.0 Ultra smart teacher. I am configured for $examMode exam preparation at a $difficultyMode learning level. You can ask me any study doubts using text, handwritten snaps, voice queries, or PDFs. How can I guide you today?", 
                    isUser = false
                )
            )
        }
    }

    // Auto-save history whenever message list changes
    fun autoSaveActiveChat() {
        try {
            val array = JSONArray()
            messages.forEach { msg ->
                val obj = JSONObject()
                obj.put("text", msg.text)
                obj.put("isUser", msg.isUser)
                obj.put("timestamp", msg.timestamp)
                array.put(obj)
            }
            prefs.edit().putString("active_chat_history_json", array.toString()).apply()
        } catch (e: Exception) {
            // Ignored
        }
    }

    // Export Handout notes function
    val exportNotes: () -> Unit = {
        try {
            val sb = StringBuilder()
            sb.append("=========================================\n")
            sb.append(" LAKSHYA AI 5.0 - STUDY HANDOUT NOTES\n")
            sb.append(" Target Exam: $examMode | Difficulty: $difficultyMode\n")
            sb.append(" Exported on: ${Date()}\n")
            sb.append("=========================================\n\n")
            
            messages.forEach { msg ->
                val sender = if (msg.isUser) "Student" else "Lakshya AI Coach"
                sb.append("[$sender]:\n")
                sb.append(msg.text)
                sb.append("\n\n-----------------------------------------\n\n")
            }
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Lakshya AI Study Notes ($examMode)")
                putExtra(Intent.EXTRA_TEXT, sb.toString())
            }
            context.startActivity(Intent.createChooser(intent, "Download/Export Study Notes"))
            Toast.makeText(context, "Study hand-out ready for export!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    // Robust Retrofit call wrapper with dynamic ListModels discovery and automatic fallback
    suspend fun queryGeminiWithRetry(request: GenerateContentRequest, modelName: String): String {
        val customApiKey = getAndValidateApiKey(context)

        // Fetch available models returned by the API for this key
        loadStatusText = "Checking model list..."
        val apiModels = fetchSupportedModels(customApiKey)
        
        val preferredModels = listOf(
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-3.1-flash-lite",
            "gemini-3.5-flash",
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite"
        )
        
        val modelsToTry = mutableListOf<String>()
        // 1. If requested model is available, try it first
        if (apiModels.contains(modelName)) {
            modelsToTry.add(modelName)
        }
        // 2. Add other preferred models that are available
        for (pref in preferredModels) {
            if (apiModels.contains(pref) && pref != modelName) {
                modelsToTry.add(pref)
            }
        }
        // 3. Add any other models returned by the API
        for (model in apiModels) {
            if (!modelsToTry.contains(model)) {
                modelsToTry.add(model)
            }
        }
        // 4. Hard fallback if empty
        if (modelsToTry.isEmpty()) {
            modelsToTry.addAll(preferredModels)
        }

        android.util.Log.d("GeminiDebug", "Dynamic models list to try: $modelsToTry")

        var finalException: Exception? = null
        for (model in modelsToTry) {
            var attempts = 0
            val maxAttempts = 2 // 2 attempts per model
            
            android.util.Log.d("GeminiDebug", "Starting attempts for model ($model)...")
            
            while (attempts < maxAttempts) {
                try {
                    attempts++
                    loadStatusText = "Analyzing Concept... (Model: $model, Attempt $attempts/$maxAttempts)"
                    val response = withContext(Dispatchers.IO) {
                        RetrofitClient.service.generateContent(
                            "v1beta/models/$model:generateContent",
                            customApiKey,
                            request
                        )
                    }
                    val resultText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    if (!resultText.isNullOrBlank()) {
                        android.util.Log.d("GeminiDebug", "SUCCESS 200 OK: Generated content using model $model")
                        return resultText
                    } else {
                        throw Exception("Empty response received from model $model")
                    }
                } catch (e: retrofit2.HttpException) {
                    finalException = e
                    val code = e.code()
                    val errorBody = e.response()?.errorBody()?.string() ?: ""
                    var serverMessage = ""
                    try {
                        val errJson = org.json.JSONObject(errorBody)
                        val errObj = errJson.optJSONObject("error")
                        serverMessage = errObj?.optString("message", "") ?: ""
                    } catch (jsonEx: Exception) {
                        serverMessage = errorBody.ifBlank { e.message() }
                    }
                    
                    android.util.Log.e("GeminiDebug", "HTTP $code from model $model: $serverMessage", e)
                    
                    if (code == 400) {
                        if (serverMessage.contains("API key not valid", ignoreCase = true)) {
                            throw Exception("Doubt Solver API Error (HTTP 400): Invalid API key.")
                        }
                        android.util.Log.w("GeminiDebug", "Model $model returned HTTP 400. Moving to next model...")
                        break
                    } else if (code == 401) {
                        throw Exception("Invalid Key (401): The provided API key is unauthorized or incorrect. Check credentials.")
                    } else if (code == 403) {
                        android.util.Log.w("GeminiDebug", "Model $model is Forbidden (403). Moving to next model...")
                        break
                    } else if (code == 404) {
                        android.util.Log.w("GeminiDebug", "Model $model not found (404). Moving to next model...")
                        break
                    } else if (code == 429) {
                        android.util.Log.w("GeminiDebug", "Model $model returned Quota Limit (429). Retrying or trying next model...")
                        if (attempts >= maxAttempts) {
                            break
                        }
                        delay(1000)
                    } else if (code in listOf(500, 502, 503, 504)) {
                        android.util.Log.w("GeminiDebug", "Model $model returned Server Busy/Error ($code). Retrying or trying next model...")
                        if (attempts >= maxAttempts) {
                            break
                        }
                        delay(1000)
                    } else {
                        android.util.Log.w("GeminiDebug", "Model $model returned error ($code): $serverMessage. Moving to next...")
                        break
                    }
                } catch (e: java.io.IOException) {
                    finalException = e
                    val errorMsg = e.message ?: ""
                    android.util.Log.e("GeminiDebug", "IOException from model $model: $errorMsg", e)
                    if (errorMsg.contains("Missing API Key") || errorMsg.contains("default placeholder")) {
                        throw e
                    }
                    if (attempts >= maxAttempts) {
                        break
                    }
                    delay(1000)
                } catch (e: Exception) {
                    finalException = e
                    android.util.Log.e("GeminiDebug", "Unexpected Exception from model $model: ${e.message}", e)
                    break
                }
            }
        }
        
        val lastErrMsg = finalException?.localizedMessage ?: "All available models failed."
        throw Exception("Gemini API Error: $lastErrMsg")
    }

    // Media Launchers
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            if (imagesCount >= 5) {
                Toast.makeText(context, "Daily image limit of 5 reached!", Toast.LENGTH_LONG).show()
            } else {
                selectedBitmap = bitmap
                selectedPdfUri = null
            }
        }
    }
    
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(context, "Camera permission is required to snap questions", Toast.LENGTH_LONG).show()
        }
    }
    
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            if (imagesCount >= 5) {
                Toast.makeText(context, "Daily image limit of 5 reached!", Toast.LENGTH_LONG).show()
            } else {
                val inputStream = context.contentResolver.openInputStream(uri)
                selectedBitmap = BitmapFactory.decodeStream(inputStream)
                selectedPdfUri = null
            }
        }
    }
    
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedPdfUri = uri
            selectedBitmap = null
        }
    }
    
    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            }
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { isListening = true }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { isListening = false }
                override fun onError(error: Int) { isListening = false }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        inputText += " " + matches[0]
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            speechRecognizer.startListening(intent)
        }
    }

    // Rotating animated placeholder text setup
    val placeholders = listOf(
        "Ask your doubt...",
        "Upload your question photo...",
        "Speak your doubt...",
        "Ask Lakshya AI..."
    )
    var currentPlaceholderIdx by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3500)
            currentPlaceholderIdx = (currentPlaceholderIdx + 1) % placeholders.size
        }
    }

    // Filtering message list for dynamic chat search
    val filteredMessages = remember(searchQuery, messages) {
        if (searchQuery.isBlank()) {
            messages
        } else {
            messages.filter { it.text.contains(searchQuery, ignoreCase = true) }
        }
    }

    // Quick tool chips
    val tools = listOf(
        "Generate Summary" to "Generate a comprehensive conceptual summary of the topic: ",
        "Generate MCQs" to "Generate 5 high-quality conceptual MCQs with answers and solutions for: ",
        "Revision Notes" to "Create structured bullet-point revision notes for: ",
        "Formula Sheet" to "Provide a complete list of formula and laws regarding: ",
        "Generate Examples" to "Provide 3 step-by-step real world examples explaining: ",
        "Similar Questions" to "Create 3 practice questions similar to: ",
        "Practice Sets" to "Create a rigorous practice exercise set on: ",
        "Flash Cards" to "Generate 5 distinct educational memory flashcards for: ",
        "Generate PYQs" to "Show standard Previous Year Exam questions (PYQs) related to: "
    )

    Column(modifier = Modifier.fillMaxSize()) {
        
        // Search & Smart Utilities bar (Glassmorphic)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .glassCard(bgColor = Color(0x12FFFFFF)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search, 
                contentDescription = null, 
                tint = Color(0xFF94A3B8), 
                modifier = Modifier.padding(start = 12.dp)
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search through study chat...", color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodySmall) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            )
            if (searchQuery.isNotBlank()) {
                IconButton(onClick = { searchQuery = "" }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = Color.White)
                }
            }
            
            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp), color = Color(0x33FFFFFF))
            
            // Export notes trigger
            IconButton(onClick = exportNotes) {
                Icon(Icons.Default.Download, contentDescription = "Download study handout notes", tint = SecondaryNeonCyan)
            }
        }

        // Bookmark and History collapsible expansion toggles
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Bookmarks button
            Row(
                modifier = Modifier
                    .weight(1f)
                    .glassCard(
                        shape = RoundedCornerShape(8.dp),
                        bgColor = if (isBookmarksExpanded) Color(0x2E8B5CF6) else Color(0x0EFFFFFF)
                    )
                    .clickable { isBookmarksExpanded = !isBookmarksExpanded }
                    .padding(8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Bookmark, 
                    contentDescription = null, 
                    tint = if (bookmarkedDoubts.isNotEmpty()) Color(0xFFFFB020) else Color(0xFF94A3B8),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Favs (${bookmarkedDoubts.size})", 
                    style = MaterialTheme.typography.labelMedium.copy(color = Color.White)
                )
            }

            // History button
            Row(
                modifier = Modifier
                    .weight(1f)
                    .glassCard(
                        shape = RoundedCornerShape(8.dp),
                        bgColor = if (isHistoryExpanded) Color(0x2E8B5CF6) else Color(0x0EFFFFFF)
                    )
                    .clickable { isHistoryExpanded = !isHistoryExpanded }
                    .padding(8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.History, 
                    contentDescription = null, 
                    tint = Color(0xFF06B6D4),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Saved History", 
                    style = MaterialTheme.typography.labelMedium.copy(color = Color.White)
                )
            }
        }

        // Expanded Bookmarks Panel
        AnimatedVisibility(visible = isBookmarksExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .glassCard(bgColor = Color(0x1F130F2C))
                    .padding(12.dp)
            ) {
                Text(
                    "Bookmarked Smart Answers / Doubts", 
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                )
                Spacer(Modifier.height(8.dp))
                if (bookmarkedDoubts.isEmpty()) {
                    Text(
                        "No bookmarks saved yet. Click the ribbon icon on any Lakshya answer to access it instantly here.", 
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 140.dp)) {
                        items(bookmarkedDoubts.toList()) { book ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .glassCard(shape = RoundedCornerShape(6.dp), bgColor = Color(0x12FFFFFF))
                                    .clickable {
                                        inputText = book
                                    }
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = book,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFE2E8F0))
                                )
                                IconButton(
                                    onClick = {
                                        val updated = bookmarkedDoubts.toMutableSet()
                                        updated.remove(book)
                                        bookmarkedDoubts = updated
                                        prefs.edit().putStringSet("bookmarked_doubts_set", updated).apply()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Expanded Saved History / Continue Conversation Panel
        AnimatedVisibility(visible = isHistoryExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .glassCard(bgColor = Color(0x1F130F2C))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Saved Study Sessions", 
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                    )
                    TextButton(
                        onClick = {
                            val sessionName = "Session on ${Date().toLocaleString().substringBefore("GMT")}"
                            val titles = savedSessionTitles.toMutableSet()
                            titles.add(sessionName)
                            savedSessionTitles = titles
                            prefs.edit().putStringSet("saved_sessions_titles", titles).apply()
                            
                            // Save payload
                            val array = JSONArray()
                            messages.forEach { msg ->
                                val obj = JSONObject()
                                obj.put("text", msg.text)
                                obj.put("isUser", msg.isUser)
                                obj.put("timestamp", msg.timestamp)
                                array.put(obj)
                            }
                            prefs.edit().putString("saved_session_data_$sessionName", array.toString()).apply()
                            Toast.makeText(context, "Current study session saved!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Save Current", color = SecondaryNeonCyan, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (savedSessionTitles.isEmpty()) {
                    Text(
                        "No past study sessions archived. Save the active discussion to continue or swap topics smoothly later.", 
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 140.dp)) {
                        items(savedSessionTitles.toList()) { title ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .glassCard(shape = RoundedCornerShape(6.dp), bgColor = Color(0x12FFFFFF))
                                    .clickable {
                                        val sessionData = prefs.getString("saved_session_data_$title", "") ?: ""
                                        if (sessionData.isNotBlank()) {
                                            try {
                                                val array = JSONArray(sessionData)
                                                messages.clear()
                                                for (i in 0 until array.length()) {
                                                    val obj = array.getJSONObject(i)
                                                    messages.add(
                                                        ChatMessage(
                                                            text = obj.getString("text"),
                                                            isUser = obj.getBoolean("isUser"),
                                                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                                                        )
                                                    )
                                                }
                                                Toast.makeText(context, "Loaded: $title", Toast.LENGTH_SHORT).show()
                                                isHistoryExpanded = false
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Failed to restore session", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFE2E8F0))
                                )
                                IconButton(
                                    onClick = {
                                        val updated = savedSessionTitles.toMutableSet()
                                        updated.remove(title)
                                        savedSessionTitles = updated
                                        prefs.edit()
                                            .putStringSet("saved_sessions_titles", updated)
                                            .remove("saved_session_data_$title")
                                            .apply()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        messages.clear()
                        messages.add(
                            ChatMessage(
                                text = "Hello! Ready for a fresh study session. Ask me anything to start!", 
                                isUser = false
                            )
                        )
                        autoSaveActiveChat()
                        Toast.makeText(context, "Conversation Cleared!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    Text("Clear All Conversation", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color.White))
                }
            }
        }

        // Quick Tool suggestions (Horizontal scrolling chips)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tools) { (label, promptPrefix) ->
                FilterChip(
                    selected = false,
                    onClick = {
                        inputText = promptPrefix + (if (messages.size > 1) messages.last { it.isUser }.text else "the latest subject topic")
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color(0x19FFFFFF),
                        labelColor = Color.White,
                        iconColor = SecondaryNeonCyan
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = Color(0x23FFFFFF),
                        enabled = true,
                        selected = false
                    ),
                    label = { Text(label, style = MaterialTheme.typography.bodySmall.copy(color = Color.White)) },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp), tint = SecondaryNeonCyan) }
                )
            }
        }

        // Chat messages section with dynamic scroll
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            reverseLayout = true
        ) {
            items(filteredMessages.reversed()) { msg ->
                ChatBubble(
                    msg = msg, 
                    onSpeakRequest = onSpeakRequest,
                    isLastMessage = (filteredMessages.lastOrNull() == msg),
                    onBookmarkToggle = { text ->
                        val updated = bookmarkedDoubts.toMutableSet()
                        if (updated.contains(text)) {
                            updated.remove(text)
                            Toast.makeText(context, "Bookmark removed!", Toast.LENGTH_SHORT).show()
                        } else {
                            updated.add(text)
                            Toast.makeText(context, "Bookmarked to Favs!", Toast.LENGTH_SHORT).show()
                        }
                        bookmarkedDoubts = updated
                        prefs.edit().putStringSet("bookmarked_doubts_set", updated).apply()
                    },
                    bookmarkedSet = bookmarkedDoubts
                )
            }
        }
        
        // Typing indicator pulsing visualizer
        if (isLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp)
                    .glassCard(shape = RoundedCornerShape(12.dp), bgColor = Color(0x26130F2C)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TypingIndicator()
                Spacer(Modifier.width(12.dp))
                Text(
                    text = loadStatusText.ifBlank { "Lakshya AI formulating response..." }, 
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFA5B4FC), fontWeight = FontWeight.Medium)
                )
            }
        }
        
        // Attachment preview
        if (selectedBitmap != null || selectedPdfUri != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .glassCard(bgColor = Color(0x3B130F2C)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.width(8.dp))
                if (selectedBitmap != null) {
                    Image(
                        bitmap = selectedBitmap!!.asImageBitmap(), 
                        contentDescription = null, 
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, SecondaryNeonCyan, RoundedCornerShape(8.dp)), 
                        contentScale = ContentScale.Crop
                    )
                    Text("Question Photo Selected", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp), color = Color.White)
                } else if (selectedPdfUri != null) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color(0xFFEF4444))
                    Text("Study PDF Handout Attached", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp), color = Color.White)
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { selectedBitmap = null; selectedPdfUri = null }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear attachment", tint = Color(0xFFEF4444))
                }
            }
        }
        
        // Styled Input tray (Glassmorphism)
        Surface(
            color = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .glassCard(bgColor = Color(0x1F130F2C), borderColor = GlowingPurpleBorder)
        ) {
            Column(modifier = Modifier.padding(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        val hasCameraPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        
                        if (hasCameraPermission) {
                            cameraLauncher.launch(null)
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Camera Scan", tint = Color.White)
                    }
                    IconButton(onClick = { imagePicker.launch("image/*") }) {
                        Icon(Icons.Default.Image, contentDescription = "Gallery", tint = Color.White)
                    }
                    IconButton(onClick = { pdfPicker.launch("application/pdf") }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF Analysis", tint = Color.White)
                    }
                    
                    // Chat Input Box with rotating crossfading placeholder
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        placeholder = {
                            Crossfade(targetState = placeholders[currentPlaceholderIdx], label = "rotatingPlaceholders") { text ->
                                Text(
                                    text = text, 
                                    color = Color(0xFF94A3B8), 
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        },
                        maxLines = 4,
                        trailingIcon = {
                            IconButton(onClick = {
                                if (isListening) {
                                    speechRecognizer.stopListening()
                                    isListening = false
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }) {
                                Icon(
                                    Icons.Default.Mic, 
                                    contentDescription = "Mic", 
                                    tint = if (isListening) Color(0xFFEF4444) else Color.White
                                )
                            }
                        }
                    )
                    
                    // Send button
                    IconButton(
                        onClick = {
                            val userText = inputText
                            val userBitmap = selectedBitmap
                            val userPdf = selectedPdfUri
                            
                            if (userText.isNotBlank() || userBitmap != null || userPdf != null) {
                                messages.add(ChatMessage(userText, true, bitmap = userBitmap, pdfUri = userPdf))
                                inputText = ""
                                selectedBitmap = null
                                selectedPdfUri = null
                                isLoading = true
                                loadStatusText = "Connecting to Google AI Studio..."

                                if (userBitmap != null) {
                                    onImageUsed()
                                }
                                autoSaveActiveChat()
                                
                                scope.launch {
                                    try {
                                        val parts = mutableListOf<Part>()
                                        if (userText.isNotBlank()) parts.add(Part(text = userText))
                                        
                                        if (userBitmap != null) {
                                            val stream = ByteArrayOutputStream()
                                            userBitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                                            val base64Image = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                                            parts.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image)))
                                        }
                                        
                                        if (userPdf != null) {
                                            val inputStream = context.contentResolver.openInputStream(userPdf)
                                            val bytes = inputStream?.readBytes()
                                            if (bytes != null) {
                                                val base64Pdf = Base64.encodeToString(bytes, Base64.NO_WRAP)
                                                parts.add(Part(inlineData = InlineData(mimeType = "application/pdf", data = base64Pdf)))
                                            }
                                        }
                                        
                                        val sysInstructionText = """
                                            You are Lakshya AI 5.0 Ultra, the smartest AI Teacher.
                                            AI Personality: Be patient. Explain politely. Never skip steps. Always motivate students. Support Hindi, English, and Hinglish natively. You can also read handwriting from images and extract text from PDFs.
                                            Current Student Settings:
                                            - Target Exam/Class: $examMode
                                            - Target Learning Level: $difficultyMode (if Easy, explain using extremely simple analogies; if Medium, explain concepts with standard examples; if Advanced, explain using full math proofs, formal derivations and deep logic).
                                        """.trimIndent()
                                        
                                        val req = GenerateContentRequest(
                                            contents = listOf(Content(parts = parts, role = "user")),
                                            systemInstruction = Content(parts = listOf(Part(text = sysInstructionText)))
                                        )
                                        
                                        val botReply = queryGeminiWithRetry(req, "gemini-3.5-flash")
                                        messages.add(ChatMessage(botReply, false))
                                        autoSaveActiveChat()
                                    } catch (e: Exception) {
                                        val errorMsg = e.message ?: "An unknown error occurred."
                                        messages.add(ChatMessage("Error: $errorMsg", false))
                                        autoSaveActiveChat()
                                    } finally {
                                        isLoading = false
                                        loadStatusText = ""
                                    }
                                }
                            }
                        },
                        modifier = Modifier.testTag("send_button"),
                        enabled = !isLoading && (inputText.isNotBlank() || selectedBitmap != null || selectedPdfUri != null)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = SecondaryNeonCyan)
                    }
                }
            }
        }
    }
}

@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier.padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val transition = rememberInfiniteTransition(label = "dots_transition")
        for (i in 0 until 3) {
            val scale by transition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1.4f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, delayMillis = i * 150, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_scale_$i"
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .background(PrimaryNeonViolet, CircleShape)
            )
        }
    }
}

@Composable
fun ChatBubble(
    msg: ChatMessage, 
    onSpeakRequest: (String) -> Unit,
    isLastMessage: Boolean,
    onBookmarkToggle: (String) -> Unit,
    bookmarkedSet: Set<String>
) {
    val context = LocalContext.current
    val isBookmarked = bookmarkedSet.contains(msg.text)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!msg.isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(PrimaryNeonViolet, Color.Transparent)))
                    .border(1.dp, PrimaryNeonViolet, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("L", color = Color.White, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start) {
            // Main Glassmorphism Chat Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(16.dp).copy(
                    bottomEnd = if (msg.isUser) RoundedCornerShape(0.dp).topEnd else RoundedCornerShape(16.dp).topEnd,
                    bottomStart = if (!msg.isUser) RoundedCornerShape(0.dp).topStart else RoundedCornerShape(16.dp).topStart
                ),
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .glassCard(
                        bgColor = if (msg.isUser) Color(0x3B6C28C4) else Color(0x1A1B173E),
                        borderColor = if (msg.isUser) GlowingPurpleBorder else SoftCyanBorder
                    )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (msg.bitmap != null) {
                        Image(
                            bitmap = msg.bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, GlowingPurpleBorder, RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (msg.pdfUri != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassCard(shape = RoundedCornerShape(8.dp), bgColor = Color(0x1A000000))
                                .padding(8.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFEF4444))
                            Spacer(Modifier.width(8.dp))
                            Text("PDF Analyzed by AI", style = MaterialTheme.typography.bodySmall.copy(color = Color.White))
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (msg.text.isNotBlank()) {
                        StreamingText(
                            text = msg.text, 
                            isUser = msg.isUser, 
                            isLastMessage = isLastMessage
                        )
                    }
                }
            }
            
            // Smart actions row below bubbles
            Row(
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!msg.isUser) {
                    // Read Aloud Action
                    Row(
                        modifier = Modifier
                            .glassCard(shape = RoundedCornerShape(4.dp), bgColor = Color(0x0EFFFFFF))
                            .clickable { onSpeakRequest(msg.text) }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.VolumeUp, 
                            contentDescription = "Read Aloud", 
                            tint = SecondaryNeonCyan, 
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Speak", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFE2E8F0)))
                    }
                }

                // Copy Action
                Row(
                    modifier = Modifier
                        .glassCard(shape = RoundedCornerShape(4.dp), bgColor = Color(0x0EFFFFFF))
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Lakshya AI Notes", msg.text)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied to Clipboard!", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ContentCopy, 
                        contentDescription = "Copy text", 
                        tint = Color(0xFFCBD5E1), 
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Copy", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFE2E8F0)))
                }

                // Share Action
                Row(
                    modifier = Modifier
                        .glassCard(shape = RoundedCornerShape(4.dp), bgColor = Color(0x0EFFFFFF))
                        .clickable {
                            val intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, msg.text)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Study Solution"))
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Share, 
                        contentDescription = "Share", 
                        tint = Color(0xFFCBD5E1), 
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Share", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFE2E8F0)))
                }

                // Bookmark / Fav Toggle Action
                Row(
                    modifier = Modifier
                        .glassCard(shape = RoundedCornerShape(4.dp), bgColor = Color(0x0EFFFFFF))
                        .clickable { onBookmarkToggle(msg.text) }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, 
                        contentDescription = "Fav", 
                        tint = if (isBookmarked) Color(0xFFFFB020) else Color(0xFFCBD5E1), 
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (isBookmarked) "Saved" else "Fav", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFE2E8F0)))
                }
            }
        }
    }
}

@Composable
fun StreamingText(text: String, isUser: Boolean, isLastMessage: Boolean) {
    var visibleText by remember { mutableStateOf("") }
    LaunchedEffect(text) {
        if (!isUser && isLastMessage && visibleText.length < text.length) {
            val words = text.split(" ")
            val isLong = words.size > 150
            val delayMs = if (isLong) 4L else 14L
            var currentAccumulated = ""
            for (word in words) {
                currentAccumulated += if (currentAccumulated.isEmpty()) word else " $word"
                visibleText = currentAccumulated
                delay(delayMs)
            }
        } else {
            visibleText = text
        }
    }
    
    Text(
        text = visibleText,
        color = if (isUser) Color(0xFFF3F4F6) else Color(0xFFE2E8F0),
        style = MaterialTheme.typography.bodyMedium.copy(
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium
        )
    )
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExamModeTab(
    selectedExam: String,
    onExamSelected: (String) -> Unit,
    selectedDifficulty: String,
    onDifficultySelected: (String) -> Unit
) {
    val examsList = listOf(
        "Class 1", "Class 2", "Class 3", "Class 4", "Class 5", "Class 6", "Class 7", "Class 8", "Class 9", "Class 10", "Class 11", "Class 12",
        "Army GD", "Army Nursing Assistant", "NDA", "SSC GD", "CUET", "NEET", "JEE", "UP Board", "CBSE"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("AI Exam Setup Mode", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Selecting your exam configures Lakshya AI 5.0 to adjust question banks, difficulty ranges, formulas, and syllabus matching accordingly.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }

        item {
            Text("Select Target Exam / Class", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                examsList.forEach { exam ->
                    val isSelected = exam == selectedExam
                    ElevatedFilterChip(
                        selected = isSelected,
                        onClick = { onExamSelected(exam) },
                        label = { Text(exam) }
                    )
                }
            }
        }

        item {
            Text("Set Custom AI Teacher Mode", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Easy", "Medium", "Advanced").forEach { diff ->
                    val isSelected = diff == selectedDifficulty
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onDifficultySelected(diff) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(diff, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            Text(
                                when(diff) {
                                    "Easy" -> "Explain basics"
                                    "Medium" -> "Standard"
                                    else -> "Proof-focused"
                                }, 
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TipsAndUpdates, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(Modifier.width(8.dp))
                        Text("Smart Study Tip", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Switch to 'Easy' mode if you are studying a chapter for the first time. Revert back to 'Advanced' to solve JEE/NEET/Board level numerical derivations.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MockTestTab(
    examMode: String,
    onScoreAdded: (Int, Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isLoading by remember { mutableStateOf(false) }
    var mockQuestions = remember { mutableStateListOf<MockQuestion>() }
    var currentQuestionIdx by remember { mutableStateOf(0) }
    var selectedOptionIdx by remember { mutableStateOf<Int?>(null) }
    
    // Tracking answers
    var correctCount by remember { mutableStateOf(0) }
    var wrongCount by remember { mutableStateOf(0) }
    var isTestFinished by remember { mutableStateOf(false) }
    
    var testSubject by remember { mutableStateOf("General Knowledge") }
    var weakTopicAnalysis by remember { mutableStateOf("") }

    val startMockTest: () -> Unit = {
        isLoading = true
        isTestFinished = false
        currentQuestionIdx = 0
        correctCount = 0
        wrongCount = 0
        selectedOptionIdx = null
        weakTopicAnalysis = ""
        mockQuestions.clear()

        scope.launch {
            try {
                val sysPrompt = """
                    You are a professional mock test generator for $examMode.
                    Generate a Mock Test consisting of exactly 5 unique, balanced, and conceptual multiple choice questions.
                    The subject is: $testSubject.
                    Your response must be a strict JSON Array of objects with absolutely no markdown wrapping, no ```json formatting, nothing outside the JSON bracket.
                    Each object must have these exact string/integer properties:
                    - "question": (String)
                    - "options": (Array of 4 Strings)
                    - "correctIndex": (Integer 0 to 3)
                    - "solution": (String explanation)
                """.trimIndent()

                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = "Generate 5 Mock Test questions for subject: $testSubject")), role = "user")),
                    systemInstruction = Content(parts = listOf(Part(text = sysPrompt)))
                )

                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.service.generateContent(
                        "v1beta/models/gemini-3.5-flash:generateContent",
                        getAndValidateApiKey(context),
                        request
                    )
                }

                val jsonText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                val cleanJson = jsonText.trim().removeSurrounding("```json", "```").trim()
                val jsonArr = JSONArray(cleanJson)

                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    val qText = obj.getString("question")
                    val optsArr = obj.getJSONArray("options")
                    val opts = List(optsArr.length()) { optsArr.getString(it) }
                    val corrIdx = obj.getInt("correctIndex")
                    val solText = obj.getString("solution")
                    mockQuestions.add(MockQuestion(qText, opts, corrIdx, solText))
                }
            } catch (e: Exception) {
                // Fallback realistic questions in case of JSON error
                mockQuestions.add(MockQuestion("What is the speed of light in vacuum?", listOf("3 x 10^8 m/s", "2 x 10^8 m/s", "1.5 x 10^8 m/s", "3 x 10^6 m/s"), 0, "Light travels at approximately 3 x 10^8 m/s in a vacuum."))
                mockQuestions.add(MockQuestion("Which of the following is an inert gas?", listOf("Oxygen", "Argon", "Nitrogen", "Chlorine"), 1, "Argon is a noble/inert gas belonging to Group 18 of periodic table."))
                mockQuestions.add(MockQuestion("What is the chemical formula of common salt?", listOf("H2O", "NaOH", "NaCl", "HCl"), 2, "Common salt is Sodium Chloride (NaCl)."))
                mockQuestions.add(MockQuestion("Which planet is closest to the Sun?", listOf("Earth", "Mars", "Mercury", "Venus"), 2, "Mercury is the closest planet to the Sun."))
                mockQuestions.add(MockQuestion("What is the power house of the cell?", listOf("Ribosome", "Nucleus", "Lysosome", "Mitochondria"), 3, "Mitochondria is known as the powerhouse of the cell."))
            } finally {
                isLoading = false
            }
        }
    }

    val generateWeakTopicAnalysis: () -> Unit = {
        scope.launch {
            try {
                val promptText = "Generate a student Performance Report and Weak Topic analysis for an exam of $examMode in $testSubject. The student scored $correctCount out of 5 questions correct."
                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = promptText)))),
                    systemInstruction = Content(parts = listOf(Part(text = "Provide 3 concise suggestions for improvement. Support Hindi and English.")))
                )
                val resp = withContext(Dispatchers.IO) {
                    RetrofitClient.service.generateContent(
                        "v1beta/models/gemini-3.5-flash:generateContent",
                        getAndValidateApiKey(context),
                        request
                    )
                }
                weakTopicAnalysis = resp.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "Focus on weaker sub-topics and attempt weekly mocks."
            } catch (e: Exception) {
                weakTopicAnalysis = "Practice regularly, identify incorrect formulas, and revise textbook summaries."
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (mockQuestions.isEmpty() && !isLoading) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("1 Weekly Mock Test", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text("Take a difficulty-balanced interactive Mock Test with unique questions, solutions, auto evaluation, and smart performance analysis.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        
                        Text("Select Subject for Test:", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                        Spacer(Modifier.height(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("General Science", "Mathematics", "Social Studies", "General Knowledge", "English Grammar").forEach { subj ->
                                FilterChip(
                                    selected = testSubject == subj,
                                    onClick = { testSubject = subj },
                                    label = { Text(subj) }
                                )
                            }
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = startMockTest,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Start Weekly Mock Test")
                        }
                    }
                }
            }
        } else if (isLoading) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Generating Balanced Questions with AI...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else if (!isTestFinished) {
            val q = mockQuestions[currentQuestionIdx]
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Question ${currentQuestionIdx + 1} of 5", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Text("$testSubject - $examMode", style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(q.question, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
            }

            items(q.options.size) { index ->
                val option = q.options[index]
                val isSelected = selectedOptionIdx == index
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedOptionIdx = index },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isSelected, onClick = { selectedOptionIdx = index })
                        Spacer(Modifier.width(8.dp))
                        Text(option, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { mockQuestions.clear() }) {
                        Text("Exit Test")
                    }
                    Button(
                        onClick = {
                            if (selectedOptionIdx != null) {
                                if (selectedOptionIdx == q.correctIndex) {
                                    correctCount++
                                } else {
                                    wrongCount++
                                }
                                selectedOptionIdx = null
                                
                                if (currentQuestionIdx < 4) {
                                    currentQuestionIdx++
                                } else {
                                    isTestFinished = true
                                    onScoreAdded(correctCount, wrongCount)
                                    generateWeakTopicAnalysis()
                                }
                            } else {
                                Toast.makeText(context, "Please select an answer!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text(if (currentQuestionIdx < 4) "Next Question" else "Submit Test")
                    }
                }
            }
        } else {
            // Results & Evaluation
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Weekly Mock Result", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(8.dp))
                        Text("$correctCount / 5", style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Correct Answers", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { correctCount / 5f },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Weak Topic & Performance Analysis", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Spacer(Modifier.height(8.dp))
                        if (weakTopicAnalysis.isBlank()) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Text(weakTopicAnalysis, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            item {
                Text("Answer Key & Detailed Solutions", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            }

            items(mockQuestions.size) { index ->
                val mq = mockQuestions[index]
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Q${index+1}: ${mq.question}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Spacer(Modifier.height(4.dp))
                        Text("Correct Answer: ${mq.options[mq.correctIndex]}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Solution: ${mq.solution}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item {
                Button(
                    onClick = { mockQuestions.clear() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Back to Test Center")
                }
            }
        }
    }
}

@Composable
fun AnalyticsTab(correct: Int, wrong: Int, progress: Float) {
    val total = correct + wrong
    val accuracy = if (total > 0) (correct.toFloat() / total * 100).toInt() else 0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Your Accuracy", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Spacer(Modifier.height(4.dp))
                        Text("$accuracy%", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    Box(modifier = Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { accuracy / 100f },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 6.dp
                        )
                        Text("$accuracy%", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Correct Answers", style = MaterialTheme.typography.bodySmall)
                        Text("$correct", style = MaterialTheme.typography.titleLarge.copy(color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold))
                    }
                }
                Card(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Wrong Answers", style = MaterialTheme.typography.bodySmall)
                        Text("$wrong", style = MaterialTheme.typography.titleLarge.copy(color = Color(0xFFF44336), fontWeight = FontWeight.Bold))
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Overall Syllabus Progress", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(CircleShape)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("${(progress * 100).toInt()}% of Lakshya Syllabus Completed", style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Subject Strength Index", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(12.dp))
                    
                    // Canvas chart
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    ) {
                        val width = size.width
                        val height = size.height
                        
                        val subjects = listOf("Physics", "Chemistry", "Mathematics", "Biology")
                        val scores = listOf(0.85f, 0.65f, 0.90f, 0.75f)
                        val colors = listOf(Color(0xFF2196F3), Color(0xFFFF9800), Color(0xFF4CAF50), Color(0xFFE91E63))
                        
                        val barHeight = 20.dp.toPx()
                        val spacing = 14.dp.toPx()
                        
                        for (i in subjects.indices) {
                            val y = i * (barHeight + spacing) + 10.dp.toPx()
                            val barWidth = (width - 100.dp.toPx()) * scores[i]
                            
                            // Draw background bar
                            drawRect(
                                color = Color.LightGray.copy(alpha = 0.3f),
                                topLeft = Offset(100.dp.toPx(), y),
                                size = androidx.compose.ui.geometry.Size(width - 100.dp.toPx(), barHeight)
                            )
                            
                            // Draw score bar
                            drawRect(
                                color = colors[i],
                                topLeft = Offset(100.dp.toPx(), y),
                                size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                            )
                        }
                    }
                    
                    // Legend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(Color(0xFF2196F3)))
                            Spacer(Modifier.width(4.dp))
                            Text("Physics (85%)", style = MaterialTheme.typography.labelSmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(Color(0xFFFF9800)))
                            Spacer(Modifier.width(4.dp))
                            Text("Chem (65%)", style = MaterialTheme.typography.labelSmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(Color(0xFF4CAF50)))
                            Spacer(Modifier.width(4.dp))
                            Text("Math (90%)", style = MaterialTheme.typography.labelSmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(Color(0xFFE91E63)))
                            Spacer(Modifier.width(4.dp))
                            Text("Bio (75%)", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Weekly Learning Progress", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(12.dp))
                    
                    // Simple progress line canvas
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    ) {
                        val width = size.width
                        val height = size.height
                        
                        val points = listOf(
                            Offset(10.dp.toPx(), height * 0.8f),
                            Offset(width * 0.2f, height * 0.7f),
                            Offset(width * 0.4f, height * 0.5f),
                            Offset(width * 0.6f, height * 0.6f),
                            Offset(width * 0.8f, height * 0.3f),
                            Offset(width - 10.dp.toPx(), height * 0.1f)
                        )
                        
                        for (i in 0 until points.size - 1) {
                            drawLine(
                                color = Color(0xFF4CAF50),
                                start = points[i],
                                end = points[i + 1],
                                strokeWidth = 3.dp.toPx()
                            )
                        }
                        
                        points.forEach { point ->
                            drawCircle(
                                color = Color(0xFF4CAF50),
                                radius = 4.dp.toPx(),
                                center = point
                            )
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { day ->
                            Text(day, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AiLabsTab(
    voiceCount: Int,
    videoCount: Int,
    onVideoGenerated: () -> Unit,
    onTtsRequested: (String) -> Unit,
    isTtsSpeaking: Boolean,
    spokenText: String,
    onStopSpeech: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var videoTopic by remember { mutableStateOf("") }
    var isVideoLoading by remember { mutableStateOf(false) }
    var videoScript by remember { mutableStateOf("") }
    var isVideoPlaying by remember { mutableStateOf(false) }

    val handleGenerateVideo: () -> Unit = {
        if (videoTopic.isBlank()) {
            Toast.makeText(context, "Please enter a topic first!", Toast.LENGTH_SHORT).show()
        } else if (videoCount >= 5) {
            Toast.makeText(context, "Weekly Video generation limit reached (5/5)!", Toast.LENGTH_LONG).show()
        } else {
            isVideoLoading = true
            isVideoPlaying = false
            videoScript = ""
            
            scope.launch {
                try {
                    val sysPrompt = """
                        You are a premium educational video scriptwriter for students.
                        Given an educational topic, generate a concise study video layout/script containing:
                        - "Topic Name"
                        - "Animation/Visual Scenes Storyboard" (Break down into Scene 1, Scene 2, Scene 3)
                        - "Audio Voiceover Script" for each scene explaining the core concept in simple Hinglish (Hindi/English mix).
                        Always motivate and inspire the student.
                    """.trimIndent()

                    val req = GenerateContentRequest(
                        contents = listOf(Content(parts = listOf(Part(text = "Generate study video script on topic: $videoTopic")), role = "user")),
                        systemInstruction = Content(parts = listOf(Part(text = sysPrompt)))
                    )

                    val resp = withContext(Dispatchers.IO) {
                        RetrofitClient.service.generateContent(
                            "v1beta/models/gemini-3.5-flash:generateContent",
                            getAndValidateApiKey(context),
                            req
                        )
                    }

                    videoScript = resp.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "Failed to generate visual script."
                    onVideoGenerated()
                    isVideoPlaying = true
                } catch (e: Exception) {
                    videoScript = "Visual educational script generated for: $videoTopic.\n\nScene 1: Introduction with diagram of topic.\nScene 2: Core formulas derivation step-by-step.\nScene 3: Real life application explanation."
                    onVideoGenerated()
                    isVideoPlaying = true
                } finally {
                    isVideoLoading = false
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
        // Voice AI Lab Card
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Voice AI Lab", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Natural native voice reading for all classroom solutions.", style = MaterialTheme.typography.bodySmall)
                    
                    Spacer(Modifier.height(12.dp))
                    Text("Weekly Conversations Used: $voiceCount / 5", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { voiceCount / 5f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                    )

                    AnimatedVisibility(visible = isTtsSpeaking) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            Text("Now Speaking:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = if(spokenText.length > 80) spokenText.take(80) + "..." else spokenText,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { onTtsRequested(spokenText) }) {
                                    Icon(Icons.Default.Replay, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Replay")
                                }
                                TextButton(onClick = onStopSpeech, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                                    Icon(Icons.Default.Stop, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Pause / Stop")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Video AI Lab Card
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VideoCall, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Video AI Lab", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Generate conceptual study videos, voice scripts & full visual storyboards dynamically.", style = MaterialTheme.typography.bodySmall)
                    
                    Spacer(Modifier.height(12.dp))
                    Text("Weekly Video Projects Generated: $videoCount / 5", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { videoCount / 5f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                    )

                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = videoTopic,
                        onValueChange = { videoTopic = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("E.g., Quantum Physics Basics") },
                        label = { Text("Study Concept Topic") }
                    )

                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = handleGenerateVideo,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isVideoLoading
                    ) {
                        if (isVideoLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Default.Movie, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Render Concept Video Script")
                        }
                    }
                }
            }
        }

        if (isVideoPlaying && videoScript.isNotBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("AI Video Script / Storyboard", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onTertiaryContainer)
                            IconButton(onClick = { isVideoPlaying = false }) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(videoScript, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
        }
    }
}
