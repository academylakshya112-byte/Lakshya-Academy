package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.*
import com.example.api.R2SupabaseManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Simple presentation class for Authenticated User
data class AppUser(
    val email: String,
    val name: String,
    val role: String, // "STUDENT" or "ADMIN"
    val avatarEmoji: String = "🎓",
    val mobile: String = "",
    val photoUri: String = ""
)

// Active Test State Holder
data class ActiveTestProgress(
    val test: TestEntity,
    val questions: List<QuestionEntity>,
    var currentQuestionIndex: Int = 0,
    val selectedAnswers: MutableMap<Int, Int> = mutableStateMapOf(), // questionId -> selectedIndex (0..3)
    val reviewedQuestions: MutableSet<Int> = mutableSetOf(), // questionId set marked for review
    var secondsRemaining: Int,
    var isSubmitted: Boolean = false,
    val testScore: TestScoreEntity? = null
)

data class YouTubeMetadata(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val status: String, // "Live", "Upcoming", "Ended"
    val isLive: Boolean
)

fun extractYoutubeVideoId(input: String): String {
    val trimmed = input.trim()
    if (trimmed.length == 11 && !trimmed.contains("/") && !trimmed.contains("?") && !trimmed.contains(".") && !trimmed.contains(":")) {
        return trimmed
    }
    val pattern = "(?:youtube\\.com\\/(?:[^\\/]+\\/.+\\/|(?:v|e(?:mbed)?|live|shorts)\\/" +
            "|.*[?&]v=)|youtu\\.be\\/)([^\"&?\\/\\s]{11})"
    val matcher = java.util.regex.Pattern.compile(pattern).matcher(trimmed)
    if (matcher.find()) {
        return matcher.group(1) ?: trimmed
    }
    return trimmed
}

suspend fun fetchYouTubeMetadata(urlOrId: String): YouTubeMetadata = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val videoId = extractYoutubeVideoId(urlOrId)
    var fetchedTitle = "Lakshya Live Class"
    var thumbnailUrl = if (videoId.isNotBlank()) "https://img.youtube.com/vi/$videoId/hqdefault.jpg" else ""
    var status = "Live"
    var isLive = true

    if (videoId.isNotBlank()) {
        val apiKey = BuildConfig.YOUTUBE_API_KEY
        var usedApi = false
        if (apiKey.isNotBlank() && apiKey != "YOUR_YOUTUBE_API_KEY" && !apiKey.startsWith("YOUR_")) {
            try {
                val url = "https://www.googleapis.com/youtube/v3/videos?part=snippet,liveStreamingDetails&id=$videoId&key=$apiKey"
                val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    requestMethod = "GET"
                }
                if (conn.responseCode == 200) {
                    val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = org.json.JSONObject(jsonStr)
                    val items = json.optJSONArray("items")
                    if (items != null && items.length() > 0) {
                        val item = items.getJSONObject(0)
                        val snippet = item.optJSONObject("snippet")
                        if (snippet != null) {
                            if (snippet.has("title")) {
                                fetchedTitle = snippet.getString("title")
                            }
                            val thumbnails = snippet.optJSONObject("thumbnails")
                            if (thumbnails != null) {
                                val sizes = listOf("maxres", "standard", "high", "medium", "default")
                                for (size in sizes) {
                                    val thumbObj = thumbnails.optJSONObject(size)
                                    if (thumbObj != null && thumbObj.has("url")) {
                                        thumbnailUrl = thumbObj.getString("url")
                                        break
                                    }
                                }
                            }
                            val liveBroadcastContent = snippet.optString("liveBroadcastContent", "none")
                            when (liveBroadcastContent) {
                                "live" -> {
                                    status = "Live"
                                    isLive = true
                                }
                                "upcoming" -> {
                                    status = "Upcoming"
                                    isLive = false
                                }
                                "none" -> {
                                    status = "Ended"
                                    isLive = false
                                }
                                else -> {
                                    status = "Live"
                                    isLive = true
                                }
                            }
                            usedApi = true
                        }
                    }
                } else {
                    Log.e("AcademyViewModel", "YouTube API returned response code: ${conn.responseCode}")
                }
            } catch (e: Exception) {
                Log.e("AcademyViewModel", "YouTube API call failed, falling back to oEmbed/Scraping", e)
            }
        }

        if (!usedApi) {
            // 1. Fetch title and thumbnail via YouTube oEmbed
            try {
                val oembedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
                val conn = (java.net.URL(oembedUrl).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 4000
                    readTimeout = 4000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }
                if (conn.responseCode == 200) {
                    val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = org.json.JSONObject(jsonStr)
                    if (json.has("title") && json.getString("title").isNotBlank()) {
                        fetchedTitle = json.getString("title")
                    }
                    if (json.has("thumbnail_url") && json.getString("thumbnail_url").isNotBlank()) {
                        thumbnailUrl = json.getString("thumbnail_url")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Scan watch HTML for live / upcoming / ended status
            try {
                val watchUrl = "https://www.youtube.com/watch?v=$videoId"
                val conn = (java.net.URL(watchUrl).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 4000
                    readTimeout = 4000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                }
                if (conn.responseCode == 200) {
                    val html = conn.inputStream.bufferedReader().use { reader ->
                        val sb = StringBuilder()
                        var line: String?
                        var bytesRead = 0
                        while (reader.readLine().also { line = it } != null && bytesRead < 250000) {
                            sb.append(line)
                            bytesRead += line?.length ?: 0
                        }
                        sb.toString()
                    }
                    if (html.contains("\"isLive\":true") || html.contains("\"isLiveNow\":true") || html.contains("\"style\":\"LIVE\"") || html.contains("isLiveStream\":true") || html.contains("LIVE_NOW")) {
                        status = "Live"
                        isLive = true
                    } else if (html.contains("\"isUpcoming\":true") || html.contains("upcomingEventData") || html.contains("\"UPCOMING\"") || html.contains("scheduledStartTime")) {
                        status = "Upcoming"
                        isLive = false
                    } else if (html.contains("\"isLiveContent\":true")) {
                        status = "Ended"
                        isLive = false
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    YouTubeMetadata(
        videoId = videoId,
        title = fetchedTitle,
        thumbnailUrl = thumbnailUrl,
        status = status,
        isLive = isLive
    )
}

class AcademyViewModel(application: Application) : AndroidViewModel(application) {

    val appUpdateManager = com.example.util.AppUpdateManager(application.applicationContext)
    val repository = AcademyRepository(application.applicationContext)
    private val prefs = application.getSharedPreferences("lakshya_app_prefs", android.content.Context.MODE_PRIVATE)

    // --- Authentication State ---
    var currentUser by mutableStateOf<AppUser?>(null)
        private set

    private fun copyUriToInternalStorage(uriStr: String): String {
        if (uriStr.isEmpty() || !uriStr.startsWith("content://")) {
            return uriStr
        }
        return try {
            val context = getApplication<Application>()
            val uri = android.net.Uri.parse(uriStr)
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val file = java.io.File(context.filesDir, "profile_photo.jpg")
                val outputStream = java.io.FileOutputStream(file)
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                outputStream.close()
                inputStream.close()
                android.net.Uri.fromFile(file).toString()
            } else {
                uriStr
            }
        } catch (e: Exception) {
            e.printStackTrace()
            uriStr
        }
    }

    fun updateProfile(newName: String, newMobile: String, newPhotoUri: String, newEmail: String) {
        val user = currentUser ?: return
        
        // Remove old user record if email changed
        if (user.email != newEmail) {
            registeredUsers.remove(user.email)
            try {
                prefs.edit().remove("reg_${user.email}").apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Copy the chosen content uri to internal storage to avoid permission loss on app death
        val localPhotoUri = copyUriToInternalStorage(newPhotoUri)
        
        val updatedUser = user.copy(name = newName, mobile = newMobile, photoUri = localPhotoUri, email = newEmail)
        currentUser = updatedUser
        registeredUsers[newEmail] = updatedUser

        try {
            prefs.edit()
                .putString("reg_$newEmail", "${updatedUser.name}|${updatedUser.role}|${updatedUser.avatarEmoji}|${updatedUser.mobile}|${updatedUser.photoUri}")
                .putString("logged_in_user_email", updatedUser.email)
                .putString("logged_in_user_name", updatedUser.name)
                .putString("logged_in_user_role", updatedUser.role)
                .putString("logged_in_user_avatar", updatedUser.avatarEmoji)
                .putString("logged_in_user_mobile", updatedUser.mobile)
                .putString("logged_in_user_photo_uri", updatedUser.photoUri)
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    var authError by mutableStateOf<String?>(null)
        private set

    var isInternetConnectionAvailable by mutableStateOf(true)
        private set

    fun checkInternetStatus(): Boolean {
        return try {
            val cm = getApplication<Application>().getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val activeNetwork = cm.activeNetwork
            if (activeNetwork != null) {
                val nc = cm.getNetworkCapabilities(activeNetwork)
                val hasInternet = nc != null && (
                    nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                    nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
                )
                isInternetConnectionAvailable = hasInternet
                hasInternet
            } else {
                isInternetConnectionAvailable = false
                false
            }
        } catch (e: Exception) {
            isInternetConnectionAvailable = true
            true
        }
    }

    // --- AI Limits ---
    suspend fun checkAndUseVideoLimit(): Boolean {
        val email = currentUser?.email ?: return false
        val cal = java.util.Calendar.getInstance()
        val currentWeek = cal.get(java.util.Calendar.WEEK_OF_YEAR)
        val currentYear = cal.get(java.util.Calendar.YEAR)

        val limit = repository.getVideoLimit(email)
        return if (limit == null || limit.weekOfYear != currentWeek || limit.year != currentYear) {
            // New week or new user
            repository.insertVideoLimit(AiVideoLimitEntity(email, 1, currentWeek, currentYear))
            true
        } else {
            if (limit.count < 3) {
                repository.insertVideoLimit(limit.copy(count = limit.count + 1))
                true
            } else {
                false
            }
        }
    }

    suspend fun getRemainingVideoCount(): Int {
        val email = currentUser?.email ?: return 0
        val cal = java.util.Calendar.getInstance()
        val currentWeek = cal.get(java.util.Calendar.WEEK_OF_YEAR)
        val currentYear = cal.get(java.util.Calendar.YEAR)

        val limit = repository.getVideoLimit(email)
        return if (limit == null || limit.weekOfYear != currentWeek || limit.year != currentYear) {
            3
        } else {
            (3 - limit.count).coerceAtLeast(0)
        }
    }

    // Simple user registry database simulation (in-memory persistent during app run)
    private val registeredUsers = mutableStateMapOf<String, AppUser>().apply {
        put("academylakshya112@gmail.com", AppUser("academylakshya112@gmail.com", "Director Sir (LAKSHYA)", "ADMIN", "🎖️"))
    }

    // --- Search & Filter States ---
    var searchQuery by mutableStateOf("")
    var selectedCategory by mutableStateOf("All")
    var darkThemeEnabled by mutableStateOf(false)
    
    // AI Processing Status
    var aiProcessingStatus by mutableStateOf<String?>(null)
        private set

    // --- AI Animation Limits State ---
    var animationLimit by mutableStateOf<AiAnimationLimitEntity?>(null)
        private set

    fun loadOrInitAnimationLimit() {
        val userEmail = currentUser?.email ?: return
        viewModelScope.launch {
            val calendar = java.util.Calendar.getInstance()
            val currentWeek = calendar.get(java.util.Calendar.WEEK_OF_YEAR)
            val currentYear = calendar.get(java.util.Calendar.YEAR)
            
            var limit = repository.getAnimationLimit(userEmail)
            if (limit == null || limit.weekOfYear != currentWeek || limit.year != currentYear) {
                limit = AiAnimationLimitEntity(userEmail, 5, currentWeek, currentYear)
                repository.insertAnimationLimit(limit)
            }
            animationLimit = limit
        }
    }

    fun decrementAnimationLimit() {
        val current = animationLimit ?: return
        if (current.count > 0) {
            viewModelScope.launch {
                val updated = current.copy(count = current.count - 1)
                repository.insertAnimationLimit(updated)
                animationLimit = updated
            }
        }
    }

    // --- State flows from Repository ---
    val allCourses: StateFlow<List<CourseEntity>> = repository.allCourses
        .onEach { list ->
            list.forEach { 
                android.util.Log.d("AcademyViewModel", "[VIEWMODEL] Observed Course - ID: ${it.id}, Title: ${it.title}, Image URL: ${it.imageUrl}")
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBanners: StateFlow<List<BannerEntity>> = repository.allBanners
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allStudyWebsites: StateFlow<List<StudyWebsiteEntity>> = repository.allStudyWebsites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allLiveClasses: StateFlow<List<LiveClassEntity>> = repository.allLiveClasses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allEnrollments: StateFlow<List<EnrollmentEntity>> = repository.allEnrollments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTests: StateFlow<List<TestEntity>> = repository.allTests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDoubts: StateFlow<List<DoubtEntity>> = repository.allDoubts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allChatMessages: StateFlow<List<ChatMessageEntity>> = repository.allChatMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allNotifications: StateFlow<List<NotificationEntity>> = repository.allNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allScores: StateFlow<List<TestScoreEntity>> = repository.allScores
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Active states updated by navigation selection ---
    var selectedCourseForDetail by mutableStateOf<CourseEntity?>(null)
    var currentLessonList = MutableStateFlow<List<LessonEntity>>(emptyList())
    var currentTestQuestionList = MutableStateFlow<List<QuestionEntity>>(emptyList())
    
    // --- Materials Lists (cached to flow on selection) ---
    private val _booksList = MutableStateFlow<List<MaterialEntity>>(emptyList())
    val booksList: StateFlow<List<MaterialEntity>> = _booksList.asStateFlow()

    private val _syllabusList = MutableStateFlow<List<MaterialEntity>>(emptyList())
    val syllabusList: StateFlow<List<MaterialEntity>> = _syllabusList.asStateFlow()

    private val _timetableList = MutableStateFlow<List<MaterialEntity>>(emptyList())
    val timetableList: StateFlow<List<MaterialEntity>> = _timetableList.asStateFlow()

    private val _pypList = MutableStateFlow<List<MaterialEntity>>(emptyList())
    val pypList: StateFlow<List<MaterialEntity>> = _pypList.asStateFlow()

    private val _currentAffairsList = MutableStateFlow<List<MaterialEntity>>(emptyList())
    val currentAffairsList: StateFlow<List<MaterialEntity>> = _currentAffairsList.asStateFlow()

    // --- Active Test Engine ---
    var activeTestProgress by mutableStateOf<ActiveTestProgress?>(null)
        private set

    // --- Live Class Mock Engine ---
    var liveClassRoomActive by mutableStateOf(false)
    var liveViewerCount by mutableIntStateOf(142)
    var liveChatMessageList = mutableStateMapOf<Long, Pair<String, String>>() // Timestamp -> (Name, Message)

    // Dynamic Live Stream status managed by Administrator
    var activeLiveStreamTitle by mutableStateOf("UPSC/Polity Interactive stream")
    var activeLiveStreamTeacher by mutableStateOf("Ghazipur Faculty")
    var activeLiveIsScheduled by mutableStateOf(true)

    init {
        // Start Weekly Mock Test Generator Scheduler (Disabled as requested to permanently remove mock tests)
        /*
        try {
            com.example.service.WeeklyMockTestGenerator.startScheduler(application, repository)
        } catch (e: Exception) {
            android.util.Log.e("AcademyViewModel", "Error starting WeeklyMockTestGenerator: ${e.message}")
        }
        */

        viewModelScope.launch {
            try {
                repository.deleteAllTests()
                repository.deleteAllQuestions()
            } catch (e: Exception) {
                android.util.Log.e("AcademyViewModel", "Error clearing tests/questions on init: ${e.message}")
            }
            repository.syncAllFromRemote()
            checkForUpdates()
        }
        // Load dynamically registered users
        try {
            prefs.all.forEach { (key, value) ->
                if (key.startsWith("reg_") && value is String) {
                    val email = key.substring(4)
                    val parts = value.split("|")
                    if (parts.size >= 2) {
                        val name = parts[0]
                        val role = parts[1]
                        val avatar = if (parts.size >= 3) parts[2] else "🎓"
                        val mobile = if (parts.size >= 4) parts[3] else ""
                        val photoUri = if (parts.size >= 5) parts[4] else ""
                        registeredUsers[email] = AppUser(email, name, role, avatar, mobile, photoUri)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AcademyViewModel", "Error loading users: ${e.message}")
        }

        // Load logged in active user session
        try {
            val savedEmail = prefs.getString("logged_in_user_email", null)
            val savedName = prefs.getString("logged_in_user_name", null)
            val savedRole = prefs.getString("logged_in_user_role", null)
            val savedAvatar = prefs.getString("logged_in_user_avatar", "🎓") ?: "🎓"
            val savedMobile = prefs.getString("logged_in_user_mobile", "") ?: ""
            val savedPhotoUri = prefs.getString("logged_in_user_photo_uri", "") ?: ""
            if (savedEmail != null && savedName != null && savedRole != null) {
                currentUser = AppUser(savedEmail, savedName, savedRole, savedAvatar, savedMobile, savedPhotoUri)
            }
        } catch (e: Exception) {
            android.util.Log.e("AcademyViewModel", "Error loading session: ${e.message}")
        }

        try {
            checkInternetStatus()
        } catch (e: Exception) {
            isInternetConnectionAvailable = true
        }

        // First startup seed and auto-fill lists
        viewModelScope.launch {
            try {
                observeMaterials()
                try {
                    repository.deleteAllTests()
                    repository.deleteAllQuestions()
                } catch (e: Exception) {
                    android.util.Log.e("AcademyViewModel", "Error clearing tests in startup: ${e.message}")
                }
                // Fetch latest data from backend directly
                repository.syncAllFromRemote()
            } catch (e: Exception) {
                android.util.Log.e("AcademyViewModel", "Error in startup tasks: ${e.message}")
            }
        }
    }

    fun syncFromRemote() {
        viewModelScope.launch {
            try {
                repository.syncAllFromRemote()
                fetchCommunityPopup()
            } catch (e: Exception) {
                android.util.Log.e("AcademyViewModel", "Manual sync failed", e)
            }
        }
    }

    private suspend fun ensureClass9TestVideoSeeded() {
        try {
            val courses = repository.allCourses.first()
            val class9Course = courses.find { it.category == "Class 9" }
            if (class9Course != null) {
                val lessons = repository.getLessonsForCourse(class9Course.id).first()
                val hasTestVideo = lessons.any { it.videoUrl == "https://youtu.be/EWAI1fi3k7Y" }
                if (!hasTestVideo) {
                    repository.insertLesson(
                        LessonEntity(
                            courseId = class9Course.id,
                            chapterName = "Physics - Motion",
                            title = "Chapter 8: Real Physics Demo Lecture (YouTube Test)",
                            videoUrl = "https://youtu.be/EWAI1fi3k7Y",
                            pdfUrl = "Class9_Motion_Demo_Notes.pdf",
                            pdfName = "Laws of Motion Notes Compilation",
                            folder = "All video"
                        )
                    )
                    // Update total lessons count
                    repository.updateCourse(class9Course.copy(totalLessons = lessons.size + 1))
                    android.util.Log.d("VideoSystem", "Successfully seeded YouTube test video into Class 9th course")
                } else {
                    android.util.Log.d("VideoSystem", "YouTube test video already seeded in Class 9th course")
                }
            } else {
                android.util.Log.e("VideoSystem", "Could not find Class 9 course to seed YouTube video")
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoSystem", "Error in ensureClass9TestVideoSeeded: ${e.message}")
        }
    }

    private fun observeMaterials() {
        viewModelScope.launch {
            repository.getMaterialsByType("Book").collect { _booksList.value = it }
        }
        viewModelScope.launch {
            repository.getMaterialsByType("Syllabus").collect { _syllabusList.value = it }
        }
        viewModelScope.launch {
            repository.getMaterialsByType("Timetable").collect { _timetableList.value = it }
        }
        viewModelScope.launch {
            repository.getMaterialsByType("Previous Year Paper").collect { _pypList.value = it }
        }
        viewModelScope.launch {
            repository.getMaterialsByType("Current Affairs").collect { _currentAffairsList.value = it }
        }
    }

    // Seed realistic Lakshya Academy Ghazipur mock data
    private suspend fun seedDatabaseIfEmpty() {
        // Collect first value of courses; if empty, seed!
        val courseCheck = repository.allCourses.first()
        if (courseCheck.isEmpty()) {
            // 1. Seed courses
            val cNDAId = repository.insertCourse(
                CourseEntity(
                    title = "NDA 1/2026 (शौर्य बैच)",
                    category = "NDA",
                    subject = "Maths & GAT Foundation",
                    description = "Features: Live Classes, Doubt Group, Class Notes PDF, Mock Test, Experienced Teachers. Validity till 12 Oct 2026.",
                    isFree = false,
                    price = 1499.00,
                    totalLessons = 14,
                    imageUrl = "https://picsum.photos/seed/nda_shaurya/600/350",
                )
            )

            val cTejasId = repository.insertCourse(
                CourseEntity(
                    title = "RRB ALP 2025 CBT 2 - तेज़स बैच 2.0",
                    category = "Railway",
                    subject = "Electrician / Wireman Spec",
                    description = "Features: Live & Recorded Lectures, Topic Tests, Specialized Study Workbooks. Highly Experienced Teachers.",
                    isFree = false,
                    price = 639.00,
                    totalLessons = 8,
                    imageUrl = "https://picsum.photos/seed/rrb_tejas/600/350",
                )
            )

            val cFreeNDAId = repository.insertCourse(
                CourseEntity(
                    title = "NDA Foundation Course (Free Batch)",
                    category = "NDA",
                    subject = "Mathematics Basics",
                    description = "Features: Free Concept Classes, Practice PDF Worksheets, Live Support Chats. Start your defence career with Lakshya.",
                    isFree = true,
                    price = 0.0,
                    totalLessons = 6,
                    imageUrl = "https://picsum.photos/seed/nda_free/600/350"
                )
            )

            val c1Id = repository.insertCourse(
                CourseEntity(
                    title = "UPSC IAS Prelims 2026 Batch",
                    category = "UPSC",
                    subject = "General Studies & CSAT",
                    description = "Premium comprehensive course prepared by top specialists for Civil Services Examination. Outlines critical chapters dynamically.",
                    isFree = false,
                    price = 4999.00,
                    totalLessons = 3,
                    imageUrl = "https://picsum.photos/seed/upsc/300/200"
                )
            )

            val c2Id = repository.insertCourse(
                CourseEntity(
                    title = "UP Police Constable targets (Free)",
                    category = "UP Police",
                    subject = "Hindi & General Studies",
                    description = "Special batch for UP Police Constables Exam 2026. Absolutely free, including video tutorials, Syllabus worksheets and PDF notes.",
                    isFree = true,
                    price = 0.0,
                    totalLessons = 2,
                    imageUrl = "https://picsum.photos/seed/police/300/200"
                )
            )

            val c3Id = repository.insertCourse(
                CourseEntity(
                    title = "SSC CGL Math & Reasoning Batch",
                    category = "SSC",
                    subject = "Quantitative Aptitude",
                    description = "Master short tricks and fast calculation strategies for Tier 1 and Tier 2 examination. Designed for high efficiency.",
                    isFree = false,
                    price = 1499.0,
                    totalLessons = 2,
                    imageUrl = "https://picsum.photos/seed/ssc/300/200"
                )
            )

            // Seed courses for school classes (Class 6th to 12th)
            val c6Id = repository.insertCourse(
                CourseEntity(
                    title = "कक्षा 6 विज्ञान और गणित - Class 6 Foundation",
                    category = "Class 6",
                    subject = "Science & Mathematics (NCERT)",
                    description = "Class 6 NCERT basic foundation concepts explained in simple Hindi and English. Includes complete notes.",
                    isFree = true,
                    price = 0.0,
                    totalLessons = 2,
                    imageUrl = "https://picsum.photos/seed/class6/300/200"
                )
            )

            val c7Id = repository.insertCourse(
                CourseEntity(
                    title = "कक्षा 7 सामाजिक विज्ञान - Class 7 Pro",
                    category = "Class 7",
                    subject = "Social Science & Grammar",
                    description = "High-quality academic lessons covering History, Geography, Civics, and English Grammar according to newest syllabus.",
                    isFree = true,
                    price = 0.0,
                    totalLessons = 1,
                    imageUrl = "https://picsum.photos/seed/class7/300/200"
                )
            )

            val c8Id = repository.insertCourse(
                CourseEntity(
                    title = "कक्षा 8 गणित विशेष (Algebra & Geometry)",
                    category = "Class 8",
                    subject = "Mathematics Spec",
                    description = "Detailed class video lectures and workbooks for Class 8 Math. Build ultra-strong quantitative foundations early.",
                    isFree = true,
                    price = 0.0,
                    totalLessons = 1,
                    imageUrl = "https://picsum.photos/seed/class8/300/200"
                )
            )

            val c9Id = repository.insertCourse(
                CourseEntity(
                    title = "कक्षा 9 CBSE & Science Super Batch",
                    category = "Class 9",
                    subject = "Physics, Chem, Biology",
                    description = "Full concepts coverage of Class 9 Science. Step-by-step numerical solving tips and theory worksheets.",
                    isFree = true,
                    price = 0.0,
                    totalLessons = 1,
                    imageUrl = "https://picsum.photos/seed/class9/300/200"
                )
            )

            val c10Id = repository.insertCourse(
                CourseEntity(
                    title = "कक्षा 10 बोर्ड परीक्षा 2026 - Target 95%+",
                    category = "Class 10",
                    subject = "All Compulsory Subjects",
                    description = "Special target board exam revision course with PYQs (Previous Year Questions) and full doubt support. Hindi & English medium mixed.",
                    isFree = false,
                    price = 499.0,
                    totalLessons = 2,
                    imageUrl = "https://picsum.photos/seed/class10/300/200"
                )
            )

            val c11Id = repository.insertCourse(
                CourseEntity(
                    title = "कक्षा 11 JEE & NEET Foundation Course",
                    category = "Class 11",
                    subject = "Physics, Chemistry, Maths",
                    description = "Premium academic sessions targeting competitive exams fundamentals alongside state board exams topics.",
                    isFree = false,
                    price = 999.0,
                    totalLessons = 1,
                    imageUrl = "https://picsum.photos/seed/class11/300/200"
                )
            )

            val c12Id = repository.insertCourse(
                CourseEntity(
                    title = "कक्षा 12 बोर्ड परीक्षा और CUET 2026 विशेष",
                    category = "Class 12",
                    subject = "Physics, Organic Chemistry & PCM",
                    description = "Guaranteed high results targeted lecture batch. Includes comprehensive printable revision sheet notes and sample mocks.",
                    isFree = false,
                    price = 1199.0,
                    totalLessons = 2,
                    imageUrl = "https://picsum.photos/seed/class12/300/200"
                )
            )

            // 2. Seed lessons for Course 1
            // High fidelity structured NDA Shaurya batch lessons
            repository.insertLesson(
                LessonEntity(
                    courseId = cNDAId,
                    chapterName = "Maths (शौर्य बैच)|Worksheet",
                    title = "Maths By Vishal Sir || Trigonometry (त्रिकोणमिति)",
                    videoUrl = "https://www.w3schools.com/html/mov_bbb.mp4",
                    pdfUrl = "Maths_Trig_Worksheet_01.pdf",
                    pdfName = "Maths By Vishal Sir || Trigonometry (त्रिकोणमिति) - PDF - I",
                    fileSize = "2.4 MB",
                    thumbnailUrl = "https://images.unsplash.com/photo-1635070041078-e363dbe005cb?w=150&q=80"
                )
            )
            repository.insertLesson(
                LessonEntity(
                    courseId = cNDAId,
                    chapterName = "Maths (शौर्य बैच)|Worksheet",
                    title = "Maths By Vishal Sir || Trigonometry (त्रिकोणमिति) #2",
                    videoUrl = "https://www.w3schools.com/html/movie.mp4",
                    pdfUrl = "Maths_Trig_Worksheet_02.pdf",
                    pdfName = "Maths By Vishal Sir || Trigonometry (त्रिकोणमिति) - PDF - II",
                    fileSize = "1.8 MB",
                    thumbnailUrl = "" // Trigger fallback
                )
            )
            repository.insertLesson(
                LessonEntity(
                    courseId = cNDAId,
                    chapterName = "Maths (शौर्य बैच)|Worksheet",
                    title = "Maths By Vishal Sir || Trigonometry (त्रिकोणमिति) #3",
                    videoUrl = "https://www.w3schools.com/html/mov_bbb.mp4",
                    pdfUrl = "Maths_Trig_Worksheet_03.pdf",
                    pdfName = "Maths By Vishal Sir || Trigonometry (त्रिकोणमिति) - PDF - III",
                    fileSize = "3.2 MB",
                    thumbnailUrl = ""
                )
            )
            repository.insertLesson(
                LessonEntity(
                    courseId = cNDAId,
                    chapterName = "Maths (शौर्य बैच)|Worksheet",
                    title = "Maths By Vishal Sir || Trigonometry (त्रिकोणमिति) #4",
                    videoUrl = "https://www.w3schools.com/html/movie.mp4",
                    pdfUrl = "Maths_Trig_Worksheet_04.pdf",
                    pdfName = "Maths By Vishal Sir || Trigonometry (त्रिकोणमिति) - PDF - IV",
                    fileSize = "1.9 MB",
                    thumbnailUrl = ""
                )
            )
            repository.insertLesson(
                LessonEntity(
                    courseId = cNDAId,
                    chapterName = "Maths (शौर्य बैच)|The Point",
                    title = "Worksheet on Coordinates and Point Distances",
                    videoUrl = "https://www.w3schools.com/html/movie.mp4",
                    pdfUrl = "The_Point_Worksheet.pdf",
                    pdfName = "Point Distance Formulas Study-material",
                    fileSize = "1.5 MB",
                    thumbnailUrl = ""
                )
            )
            repository.insertLesson(
                LessonEntity(
                    courseId = cNDAId,
                    chapterName = "Maths (शौर्य बैच)|Straight Line",
                    title = "Straight Line Class 1: Slopes & General Equation",
                    videoUrl = "https://www.w3schools.com/html/mov_bbb.mp4",
                    pdfUrl = "StraightLine_Notes.pdf",
                    pdfName = "Complete concepts of Straight Lines (Slope/Intercept)",
                    fileSize = "2.1 MB",
                    thumbnailUrl = ""
                )
            )
            repository.insertLesson(
                LessonEntity(
                    courseId = cNDAId,
                    chapterName = "Geography (शौर्य बैच)|Worksheet",
                    title = "Indian Physical Geography: River basins & Drainage systems",
                    videoUrl = "https://www.w3schools.com/html/movie.mp4",
                    pdfUrl = "River_Basins_Notes.pdf",
                    pdfName = "Major Rivers of India Detailed Map Guide Booklet",
                    fileSize = "4.2 MB",
                    thumbnailUrl = ""
                )
            )
            repository.insertLesson(
                LessonEntity(
                    courseId = cNDAId,
                    chapterName = "Current Affairs (आदर्श सर)|Worksheet",
                    title = "Daily current affairs discussion & General GK Digest",
                    videoUrl = "https://www.w3schools.com/html/mov_bbb.mp4",
                    pdfUrl = "Daily_Current_Affairs_June2026.pdf",
                    pdfName = "General Current Affairs Daily Booster PDF",
                    fileSize = "1.1 MB",
                    thumbnailUrl = ""
                )
            )
            repository.insertLesson(
                LessonEntity(
                    courseId = cNDAId,
                    chapterName = "Polity (शौर्य बैच)|Worksheet",
                    title = "Indian Parliament & Legislative structures lecture series",
                    videoUrl = "https://www.w3schools.com/html/movie.mp4",
                    pdfUrl = "Parliament_Polity_Notes.pdf",
                    pdfName = "Lok Sabha, Rajya Sabha & President powers list",
                    fileSize = "3.2 MB",
                    thumbnailUrl = ""
                )
            )
            repository.insertLesson(
                LessonEntity(
                    courseId = cNDAId,
                    chapterName = "Physics (शौर्य बैच)|Worksheet",
                    title = "Mechanics Part 1: Newton's Laws of Motion",
                    videoUrl = "https://www.w3schools.com/html/mov_bbb.mp4",
                    pdfUrl = "Newton_Laws_Physics.pdf",
                    pdfName = "Kinematics & Newton's laws of motion core notes",
                    fileSize = "2.5 MB",
                    thumbnailUrl = ""
                )
            )

            // Lessons for Tejas Batch RRB ALP
            repository.insertLesson(
                LessonEntity(
                    courseId = cTejasId,
                    chapterName = "Basic Electricity|Worksheet",
                    title = "Electrician Trade Theory - Coulomb's Law & Charge basic",
                    videoUrl = "https://www.w3schools.com/html/movie.mp4",
                    pdfUrl = "Tejas_CBT2_Trade_Theory_01.pdf",
                    pdfName = "Electrician Trade Theory - PDF - I",
                    fileSize = "2.2 MB",
                    thumbnailUrl = ""
                )
            )
            repository.insertLesson(
                LessonEntity(
                    courseId = cTejasId,
                    chapterName = "Basic Electricity|Ohm's Law",
                    title = "Ohm's Law and Series-Parallel resistance networks",
                    videoUrl = "https://www.w3schools.com/html/mov_bbb.mp4",
                    pdfUrl = "Ohms_Law_Circuits.pdf",
                    pdfName = "Ohm's Law & Circuit solving tricks sheet",
                    fileSize = "1.9 MB",
                    thumbnailUrl = ""
                )
            )
            repository.insertLesson(
                LessonEntity(
                    courseId = cTejasId,
                    chapterName = "Instruments Theory|Worksheet",
                    title = "Ammeter, Voltmeter & Galvanometer range extensions",
                    videoUrl = "https://www.w3schools.com/html/movie.mp4",
                    pdfUrl = "Instruments_Tricks.pdf",
                    pdfName = "Measuring Instruments core details summary",
                    fileSize = "3.1 MB",
                    thumbnailUrl = ""
                )
            )

            // Let's seed courses for the free model too
            repository.insertLesson(
                LessonEntity(
                    courseId = cFreeNDAId,
                    chapterName = "Maths (शौर्य बैच)|Worksheet",
                    title = "Maths By Vishal Sir || Trigonometry (त्रिकोणमिति) - Free version",
                    videoUrl = "https://www.w3schools.com/html/mov_bbb.mp4",
                    pdfUrl = "Trig_Free_Notes.pdf",
                    pdfName = "Free segment Trigonometry basics study sheets",
                    fileSize = "2.5 MB",
                    thumbnailUrl = ""
                )
            )

            repository.insertLesson(
                LessonEntity(
                    courseId = c1Id,
                    chapterName = "Indian Polity",
                    title = "Polity Chapter-1: Basic Structure & Preamble",
                    videoUrl = "https://www.w3schools.com/html/mov_bbb.mp4",
                    pdfUrl = "Polity_Chapter1_Class_Notes.pdf",
                    pdfName = "Polity Chapter 1 Notes (M. Laxmikanth Supplement)"
                )
            )
            repository.insertLesson(
                LessonEntity(
                    courseId = c1Id,
                    chapterName = "Indian Polity",
                    title = "Polity Chapter-2: Fundamental Rights Articles 12-35",
                    videoUrl = "https://www.w3schools.com/html/movie.mp4",
                    pdfUrl = "Fundamental_Rights_Deep_Study.pdf",
                    pdfName = "Fundamental Rights Article Deep-dive Worksheet"
                )
            )
            repository.insertLesson(
                LessonEntity(
                    courseId = c1Id,
                    chapterName = "Ancient History",
                    title = "Indus Valley Civilization & Vedic Period",
                    videoUrl = "https://www.w3schools.com/html/mov_bbb.mp4",
                    pdfUrl = "Ancient_History_IVC_Notes.pdf",
                    pdfName = "Harappan Civilization Sites & Archeological Finds"
                )
            )

            // 2b. Seed lessons for UP Police (Course 2)
            repository.insertLesson(
                LessonEntity(
                    courseId = c2Id,
                    chapterName = "Ras sand Alankar",
                    title = "Hindi Grammar: Ras, Chhand and Alankar Rules",
                    videoUrl = "https://www.w3schools.com/html/movie.mp4",
                    pdfUrl = "Hindi_Alankar_Tricks.pdf",
                    pdfName = "UP Police Alankar and Sandhi Short Tricks PDF"
                )
            )
            repository.insertLesson(
                LessonEntity(
                    courseId = c2Id,
                    chapterName = "General Knowledge",
                    title = "Uttar Pradesh General Knowledge & Ghazipur History",
                    videoUrl = "https://www.w3schools.com/html/mov_bbb.mp4",
                    pdfUrl = "UP_GK_Special_Notes.pdf",
                    pdfName = "Uttar Pradesh GK special capsule 2026"
                )
            )

            // 2c. Seed lessons for SSC CGL (Course 3)
            repository.insertLesson(
                LessonEntity(
                    courseId = c3Id,
                    chapterName = "Ratio & Proportions",
                    title = "Short tricks: Ratio and Mixtures questions",
                    videoUrl = "https://www.w3schools.com/html/mov_bbb.mp4",
                    pdfUrl = "SSC_Math_Ratios.pdf",
                    pdfName = "SSC CGL Math Tricks Worksheet - Mixtures and Allegations"
                )
            )
            repository.insertLesson(
                LessonEntity(
                    courseId = c3Id,
                    chapterName = "Time & Work",
                    title = "Time, Speed & Distance Formula shortcuts",
                    videoUrl = "https://www.w3schools.com/html/movie.mp4",
                    pdfUrl = "SSC_Math_TimeDistance.pdf",
                    pdfName = "Time, Speed and Distance Formula Shortcuts Sheet"
                )
            )

            // Seed lessons for class 6th to 12th
            repository.insertLesson(
                LessonEntity(
                    courseId = c6Id,
                    chapterName = "Science - Food",
                    title = "Chapter 1: Food- Where does it come from?",
                    videoUrl = "https://www.w3schools.com/html/movie.mp4",
                    pdfUrl = "Class6_Science_Ch1.pdf",
                    pdfName = "Class 6 Science Chapter 1 Notes"
                )
            )
            repository.insertLesson(
                LessonEntity(
                    courseId = c6Id,
                    chapterName = "Maths - Numbers",
                    title = "Chapter 1: Knowing Our Numbers Concepts",
                    videoUrl = "https://www.w3schools.com/html/mov_bbb.mp4",
                    pdfUrl = "Class6_Maths_Ch1.pdf",
                    pdfName = "Class 6 mathematics Numbers Practice Sheet"
                )
            )

            repository.insertLesson(
                LessonEntity(
                    courseId = c7Id,
                    chapterName = "History - Delhi Sultans",
                    title = "Chapter 3: The Delhi Sultans Summary",
                    videoUrl = "https://www.w3schools.com/html/mov_bbb.mp4",
                    pdfUrl = "Class7_History_Ch3.pdf",
                    pdfName = "The Delhi Sultans Revision Notes"
                )
            )

            repository.insertLesson(
                LessonEntity(
                    courseId = c8Id,
                    chapterName = "Maths - Rational Numbers",
                    title = "Chapter 1: Rational Numbers Properties",
                    videoUrl = "https://www.w3schools.com/html/movie.mp4",
                    pdfUrl = "Class8_Maths_Ch1.pdf",
                    pdfName = "Rational Numbers Properties & Questions"
                )
            )

            repository.insertLesson(
                LessonEntity(
                    courseId = c9Id,
                    chapterName = "Physics - Motion",
                    title = "Chapter 8: Laws of Motion & Graphs",
                    videoUrl = "https://www.w3schools.com/html/mov_bbb.mp4",
                    pdfUrl = "Class9_Physics_Motion.pdf",
                    pdfName = "Equations of Motion Graphical Derivations"
                )
            )

            repository.insertLesson(
                LessonEntity(
                    courseId = c10Id,
                    chapterName = "Science - Electricity",
                    title = "Chapter 12: Electric Current & Ohm's Law",
                    videoUrl = "https://www.w3schools.com/html/movie.mp4",
                    pdfUrl = "Class10_Science_Electricity.pdf",
                    pdfName = "Ohm's Law & Resistance Numericals Notes"
                )
            )
            repository.insertLesson(
                LessonEntity(
                    courseId = c10Id,
                    chapterName = "Maths - Quadratic Equations",
                    title = "Chapter 4: Solving Quadratic Equations Tricks",
                    videoUrl = "https://www.w3schools.com/html/mov_bbb.mp4",
                    pdfUrl = "Class10_Maths_Quadratic.pdf",
                    pdfName = "Quadratic Equations Board Level Solved Questions"
                )
            )

            repository.insertLesson(
                LessonEntity(
                    courseId = c11Id,
                    chapterName = "Physics - Kinematics",
                    title = "Calculus in Kinematics: Derivatives & Integrals",
                    videoUrl = "https://www.w3schools.com/html/movie.mp4",
                    pdfUrl = "Class11_Physics_Kinematics.pdf",
                    pdfName = "Kinematics Calculus Supplement Core Notes"
                )
            )

            repository.insertLesson(
                LessonEntity(
                    courseId = c12Id,
                    chapterName = "Chemistry - Electrochemistry",
                    title = "Nernst Equation & Cell Potential Calculations",
                    videoUrl = "https://www.w3schools.com/html/movie.mp4",
                    pdfUrl = "Class12_Chemistry_Electrochemistry.pdf",
                    pdfName = "Nernst Equation Practice Problems"
                )
            )

            // 3. Seed test items (Cleared and re-seeded for Class 1 to 12 as requested)
            repository.deleteAllTests()
            repository.deleteAllQuestions()

            for (clsNum in 1..12) {
                val subjects = when {
                    clsNum <= 5 -> listOf("Mathematics / गणित", "EVS / पर्यावरण अध्ययन", "Hindi & English / हिंदी और अंग्रेजी")
                    clsNum <= 10 -> listOf("Mathematics / गणित", "Science / विज्ञान", "Social Science / सामाजिक विज्ञान", "Hindi & English / हिंदी और अंग्रेजी")
                    else -> listOf("Physics / भौतिक विज्ञान", "Chemistry / रसायन विज्ञान", "Biology / जीव विज्ञान", "Mathematics / गणित")
                }

                for (subjName in subjects) {
                    val tId = repository.insertTest(
                        TestEntity(
                            title = "Class $clsNum Mock Test - $subjName",
                            type = "Mock Test",
                            durationMinutes = 60,
                            hasNegativeMarking = true,
                            marksPerCorrect = 4,
                            marksPerWrong = -1.0f
                        )
                    )

                    // Generate exactly 50 questions for this class and subject
                    for (qIndex in 1..50) {
                        val questionText: String
                        val optA: String
                        val optB: String
                        val optC: String
                        val optD: String
                        val correctIdx: Int = (qIndex % 4) // deterministic but varies

                        when {
                            subjName.contains("Math") -> {
                                val num1 = 10 + (qIndex * 7) % 90
                                val num2 = 5 + (qIndex * 13) % 40
                                val sumVal = num1 + num2
                                val diffVal = num1 - num2
                                when (qIndex % 4) {
                                    0 -> {
                                        questionText = "Class $clsNum Maths: Simple calculation. What is $num1 + $num2? / कक्षा $clsNum गणित: सरल गणना। $num1 + $num2 का मान क्या है?"
                                        optA = if (correctIdx == 0) "$sumVal" else "${sumVal + 5}"
                                        optB = if (correctIdx == 1) "$sumVal" else "${sumVal - 3}"
                                        optC = if (correctIdx == 2) "$sumVal" else "${sumVal + 12}"
                                        optD = if (correctIdx == 3) "$sumVal" else "${sumVal - 1}"
                                    }
                                    1 -> {
                                        questionText = "Class $clsNum Maths: Subtraction. What is $num1 - $num2? / कक्षा $clsNum गणित: घटाव। $num1 - $num2 का मान क्या है?"
                                        optA = if (correctIdx == 0) "$diffVal" else "${diffVal + 4}"
                                        optB = if (correctIdx == 1) "$diffVal" else "${diffVal - 6}"
                                        optC = if (correctIdx == 2) "$diffVal" else "${diffVal + 15}"
                                        optD = if (correctIdx == 3) "$diffVal" else "${diffVal - 1}"
                                    }
                                    2 -> {
                                        val shortNum1 = 2 + (qIndex % 10)
                                        val shortNum2 = 3 + (qIndex % 8)
                                        val multVal = shortNum1 * shortNum2
                                        questionText = "Class $clsNum Maths: Multiplication. What is $shortNum1 x $shortNum2? / कक्षा $clsNum गणित: गुणा। $shortNum1 x $shortNum2 का मान क्या है?"
                                        optA = if (correctIdx == 0) "$multVal" else "${multVal + 6}"
                                        optB = if (correctIdx == 1) "$multVal" else "${multVal - 2}"
                                        optC = if (correctIdx == 2) "$multVal" else "${multVal + 10}"
                                        optD = if (correctIdx == 3) "$multVal" else "${multVal - 4}"
                                    }
                                    else -> {
                                        val sqNum = 2 + (qIndex % 12)
                                        val sqVal = sqNum * sqNum
                                        questionText = "Class $clsNum Maths: What is the square of $sqNum? / कक्षा $clsNum गणित: $sqNum का वर्ग क्या है?"
                                        optA = if (correctIdx == 0) "$sqVal" else "${sqVal + 9}"
                                        optB = if (correctIdx == 1) "$sqVal" else "${sqVal - 8}"
                                        optC = if (correctIdx == 2) "$sqVal" else "${sqVal + 20}"
                                        optD = if (correctIdx == 3) "$sqVal" else "${sqVal - 2}"
                                    }
                                }
                            }
                            subjName.contains("Science") || subjName.contains("Physics") || subjName.contains("Chemistry") || subjName.contains("Biology") || subjName.contains("EVS") -> {
                                val topics = listOf(
                                    Triple("Powerhouse of the cell / कोशिका का बिजलीघर", "Mitochondria / माइटोकॉन्ड्रिया", "Ribosome / राइबोसोम"),
                                    Triple("Boiling point of pure water / शुद्ध जल का क्वथनांक", "100°C", "0°C"),
                                    Triple("Simplest ketone / सबसे सरल कीटोन", "Propanone / प्रोपेनोन", "Ethanol / इथेनॉल"),
                                    Triple("Most electronegative element / सबसे अधिक विद्युत ऋणात्मक तत्व", "Fluorine / फ्लोरीन", "Chlorine / क्लोरीन"),
                                    Triple("Double helix model of DNA discovered by / डीएनए के द्विकुंडलित मॉडल की खोज की थी", "Watson and Crick / वाटसन और क्रिक", "Mendel / मेंडल"),
                                    Triple("SI Unit of Resistance / प्रतिरोध का SI मात्रक", "Ohm / ओम", "Volt / वोल्ट"),
                                    Triple("What gas do plants absorb during photosynthesis? / प्रकाश संश्लेषण के दौरान पौधे कौन सी गैस अवशोषित करते हैं?", "Carbon Dioxide / कार्बन डाइऑक्साइड", "Oxygen / ऑक्सीजन"),
                                    Triple("Which planet is known as Red Planet? / किस ग्रह को लाल ग्रह के रूप में जाना जाता है?", "Mars / मंगल", "Jupiter / बृहस्पति"),
                                    Triple("The primary source of energy on Earth is / पृथ्वी पर ऊर्जा का प्राथमिक स्रोत है", "The Sun / सूर्य", "Wind / पवन"),
                                    Triple("Which non-metal is in liquid state / कौन सी अधातु द्रव अवस्था में होती है", "Bromine / ब्रोमीन", "Chlorine / क्लोरीन")
                                )
                                val selected = topics[qIndex % topics.size]
                                questionText = "Class $clsNum Science ($subjName): Identify '${selected.first}'? / कक्षा $clsNum विज्ञान ($subjName): '${selected.first}' की पहचान करें?"
                                optA = if (correctIdx == 0) selected.second else selected.third
                                optB = if (correctIdx == 1) selected.second else "None of these / इनमें से कोई नहीं"
                                optC = if (correctIdx == 2) selected.second else "Both / दोनों"
                                optD = if (correctIdx == 3) selected.second else "All of the above / उपरोक्त सभी"
                            }
                            subjName.contains("Social") || subjName.contains("SST") || subjName.contains("इतिहास") || subjName.contains("भूगोल") -> {
                                val topics = listOf(
                                    Triple("Battle of Plassey took place in year / प्लासी का युद्ध किस वर्ष हुआ था?", "1757", "1857"),
                                    Triple("Primary organ of Indian Constitution implementation / भारतीय संविधान को लागू करने वाला प्राथमिक अंग है", "Parliament / संसद", "Supreme Court / सर्वोच्च न्यायालय"),
                                    Triple("Largest ocean on Earth / पृथ्वी का सबसे बड़ा महासागर है", "Pacific Ocean / प्रशांत महासागर", "Atlantic Ocean / अटलांटिक महासागर"),
                                    Triple("In which year did India celebrate its first Republic Day? / भारत ने अपना पहला गणतंत्र दिवस किस वर्ष मनाया था?", "1950", "1947"),
                                    Triple("Which is the longest river flowing in India? / भारत में बहने वाली सबसे लंबी नदी कौन सी है?", "Ganga / गंगा", "Yamuna / यमुना"),
                                    Triple("Who is known as Father of Indian Constitution? / भारतीय संविधान के जनक के रूप में किसे जाना जाता है?", "Dr. B.R. Ambedkar / डॉ. बी.आर. अंबेडकर", "Mahatma Gandhi / महात्मा गांधी"),
                                    Triple("Capital of India before New Delhi / नई दिल्ली से पहले भारत की राजधानी कौन सी थी?", "Calcutta (Kolkata) / कलकत्ता", "Bombay (Mumbai) / मुंबई"),
                                    Triple("Indian State with longest coastline / सबसे लंबी तटरेखा वाला भारतीय राज्य कौन सा है?", "Gujarat / गुजरात", "Maharashtra / महाराष्ट्र")
                                )
                                val selected = topics[qIndex % topics.size]
                                questionText = "Class $clsNum SST: ${selected.first}"
                                optA = if (correctIdx == 0) selected.second else selected.third
                                optB = if (correctIdx == 1) selected.second else "Mughal Empire / मुगल साम्राज्य"
                                optC = if (correctIdx == 2) selected.second else "Indus River / सिंधु नदी"
                                optD = if (correctIdx == 3) selected.second else "None of the above / इनमें से कोई नहीं"
                            }
                            else -> {
                                val topics = listOf(
                                    Triple("Opposite of 'Hot' / 'गर्म' का विलोम शब्द है", "Cold / ठंडा", "Warm / गुनगुना"),
                                    Triple("Abstract noun of 'Beautiful' is / 'Beautiful' का भाववाचक संज्ञा शब्द है", "Beauty / सुंदरता", "Beautify / सुंदर बनाना"),
                                    Triple("Identify the correct spelling / सही वर्तनी की पहचान करें", "Receive / प्राप्त करना", "Recieve / प्राप्त करना"),
                                    Triple("Plural form of 'Child' is / 'Child' का बहुवचन रूप है", "Children / बच्चे", "Childs / बच्चे"),
                                    Triple("Opposite of 'Happy' / 'खुश' का विलोम शब्द है", "Sad / दुखी", "Glad / प्रसन्न"),
                                    Triple("Identify correct grammar: 'She ____ a letter yesterday' / 'She ____ _ yesterday' का सही रूप", "wrote / लिखा", "writes / लिखती है")
                                )
                                val selected = topics[qIndex % topics.size]
                                questionText = "Class $clsNum Language: What is the correct answer for '${selected.first}'? / कक्षा $clsNum भाषा: '${selected.first}' का सही उत्तर क्या है?"
                                optA = if (correctIdx == 0) selected.second else selected.third
                                optB = if (correctIdx == 1) selected.second else "Incorrect / अशुद्ध"
                                optC = if (correctIdx == 2) selected.second else "Noun / संज्ञा"
                                optD = if (correctIdx == 3) selected.second else "None of the above / इनमें से कोई नहीं"
                            }
                        }

                        repository.insertQuestion(
                            QuestionEntity(
                                testId = tId,
                                questionText = questionText,
                                optionA = optA,
                                optionB = optB,
                                optionC = optC,
                                optionD = optD,
                                correctIndex = correctIdx
                            )
                        )
                    }
                }
            }

            // 4. Seed Support Materials
            repository.insertMaterial(
                MaterialEntity(
                    type = "Book",
                    title = "Objective Indian Polity (Full Syllabus M3 Version)",
                    description = "Laxmikanth complete Indian Polity book with simplified explanations.",
                    fileSize = "14.5 MB"
                )
            )
            repository.insertMaterial(
                MaterialEntity(
                    type = "Syllabus",
                    title = "UPSC 2026 Prelims civil services official syllabus",
                    description = "Detailed syllabus PDF for Civil Services GS and CSAT.",
                    fileSize = "2.1 MB"
                )
            )
            repository.insertMaterial(
                MaterialEntity(
                    type = "Timetable",
                    title = "Lakshya Academy June 2026 Live online batches",
                    description = "Schedule for IAS Daily interactive, mock discussion and doubts sessions.",
                    fileSize = "0.8 MB"
                )
            )
            repository.insertMaterial(
                MaterialEntity(
                    type = "Previous Year Paper",
                    title = "UPSC GS Prelims 2025 Paper solved answers key",
                    description = "Fully annotated UPSC paper 1 answers keys with brief explanation notes.",
                    fileSize = "4.2 MB"
                )
            )
            repository.insertMaterial(
                MaterialEntity(
                    type = "Current Affairs",
                    title = "Daily GK capsule 12 June 2026 (Editorials Special)",
                    description = "Compiled editorial briefs for Ghazipur online exam pupils.",
                    fileSize = "1.2 MB"
                )
            )

            // 5. Seed Support doubts
            repository.insertDoubt(
                DoubtEntity(
                    userEmail = "anand@yadav.com",
                    userName = "Anand Yadav",
                    subject = "Polity Query",
                    questionText = "Can fundamental rights be suspended during a National Emergency? If yes, which articles are exceptions?",
                    replyText = "Yes Anand, during National Emergency under Article 352, standard rights are suspended, EXCEPT Article 20 and Article 21, which guarantee protection in respect of conviction for offenses and protection of life/liberty respectively. This is per 44th Amendment Act.",
                    answeredBy = "Chief Faculty (Polity)"
                )
            )
            
            // 6. Seed Push Notifications
            repository.insertNotification(
                NotificationEntity(
                    title = "Welcome to Lakshya Academy!",
                    message = "Your gateway to competitive online coaching in Ghazipur. Learn from the best teachers, participate in weekly Live Tests series, and track your daily progress."
                )
            )

            // 7. Seed support chat starter messages
            repository.insertChatMessage(
                ChatMessageEntity(
                    senderName = "Academy Helper Bot",
                    senderEmail = "support@lakshya.academy",
                    text = "Welcome to Lakshya Academy Chat Support. Ask any question regarding exam guides, fees, online tests or offline batches. We are here to help you!",
                    isAdminReply = true
                )
            )
        }

        // Clean up any existing demo/seeded banners from local and remote DB
        viewModelScope.launch {
            try {
                val currentBanners = repository.allBanners.first()
                currentBanners.forEach { banner ->
                    if (banner.imageUrl.contains("android.resource://") || 
                        banner.title.contains("Join Our Official Telegram") || 
                        banner.title.contains("Subscribe to Our YouTube") || 
                        banner.title.contains("Join Our Official WhatsApp") || 
                        banner.title.contains("Follow Our Instagram")) {
                        repository.deleteBanner(banner.id)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AcademyViewModel", "Failed to clear demo banners", e)
            }
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            try {
                android.util.Log.d("AcademyViewModel", "[UPDATE SYSTEM] Checking for updates from Supabase...")
                val latestUpdate = repository.getLatestAppUpdate()
                if (latestUpdate == null) {
                    android.util.Log.d("AcademyViewModel", "[UPDATE SYSTEM] No update details found in database.")
                    appUpdateManager.setIdle()
                    return@launch
                }
                
                val context = getApplication<Application>()
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val installedVersion = pInfo.versionName ?: "1.0"
                
                android.util.Log.d("AcademyViewModel", "[UPDATE SYSTEM] Installed Version: $installedVersion, Latest: ${latestUpdate.latestVersion}, Minimum: ${latestUpdate.minimumVersion}")
                
                val hasNewer = compareVersions(latestUpdate.latestVersion, installedVersion) > 0
                val isBelowMin = compareVersions(installedVersion, latestUpdate.minimumVersion) < 0
                val isForce = latestUpdate.forceUpdate || isBelowMin
                
                if (hasNewer) {
                    android.util.Log.i("AcademyViewModel", "[UPDATE SYSTEM] New version available! Force Update: $isForce")
                    appUpdateManager.setUpdateAvailable(latestUpdate, isForce)
                } else {
                    android.util.Log.d("AcademyViewModel", "[UPDATE SYSTEM] Application is up-to-date.")
                    appUpdateManager.setIdle()
                }
            } catch (e: Exception) {
                android.util.Log.e("AcademyViewModel", "[UPDATE SYSTEM] Error checking for updates: ${e.message}", e)
            }
        }
    }
    
    private fun compareVersions(v1: String, v2: String): Int {
        val s1 = v1.split(".").mapNotNull { it.toIntOrNull() }
        val s2 = v2.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(s1.size, s2.size)
        for (i in 0 until maxLen) {
            val val1 = if (i < s1.size) s1[i] else 0
            val val2 = if (i < s2.size) s2[i] else 0
            if (val1 != val2) {
                return val1.compareTo(val2)
            }
        }
        return 0
    }

    // --- Authentication Actions ---
    fun login(email: String, name: String, role: String, isSignUp: Boolean = false) {
        viewModelScope.launch {
            authError = null
            
            // 1. Internet connection check
            if (!checkInternetStatus()) {
                authError = "No Internet Connection detected! (सक्रिय इंटरनेट कनेक्शन नहीं मिला)। Lakshya App requires an active internet connection to login (लॉगिन करने के लिए इंटरनेट आवश्यक है)।"
                return@launch
            }
            
            if (email.isBlank() || name.isBlank()) {
                authError = "Email and Name fields cannot be blank."
                return@launch
            }
            val formattedEmail = email.trim().lowercase()
            
            // Restrict ADMIN workspace login to authorized emails only
            if (role == "ADMIN" && formattedEmail != "academylakshya112@gmail.com") {
                authError = "Access Denied: '$email' is not registered as an authorized Academy Admin. Only genuine Lakshmi/Lakshya Academy Directors can access the Admin Desk."
                return@launch
            }
            
            // 2. Checking if the user already exists
            val exists = registeredUsers.containsKey(formattedEmail)
            if (!isSignUp && !exists) {
                authError = "Account Not Found! (अकाउंट नहीं मिला!) Naye users pehle neeche 'Sign Up' link pe click karke apna Account banayein (New students must click 'Sign Up' to register first)."
                return@launch
            }
            
            // In a real application or this sandbox, we register the user dynamically if they didn't exist during explicit sign-up
            val userProfile = registeredUsers[formattedEmail] ?: if (isSignUp) {
                AppUser(
                    email = formattedEmail,
                    name = name.trim(),
                    role = role,
                    avatarEmoji = if (role == "ADMIN") "🎖️" else "🎓"
                )
            } else {
                null
            }
            
            if (userProfile == null) {
                authError = "Error authenticating account. Please register first."
                return@launch
            }
            
            // Update role if selected role differs
            val finalProfile = if (userProfile.role != role) userProfile.copy(role = role) else userProfile
            registeredUsers[formattedEmail] = finalProfile
            
            // Save self-registration dynamically so they persist across app sessions
            try {
                prefs.edit().putString("reg_$formattedEmail", "${finalProfile.name}|${finalProfile.role}|${finalProfile.avatarEmoji}|${finalProfile.mobile}|${finalProfile.photoUri}").apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            currentUser = finalProfile
            
            if (finalProfile.role == "ADMIN") {
                android.util.Log.i("AcademyViewModel", "[BATCH SYNC] Admin logged in successfully! Automatically triggering sync from remote Supabase...")
                viewModelScope.launch {
                    try {
                        repository.syncAllFromRemote()
                    } catch (e: Exception) {
                        android.util.Log.e("AcademyViewModel", "[BATCH SYNC] Automatic sync after Admin login failed", e)
                    }
                }
            }
            
            // Save user login session details
            try {
                prefs.edit()
                    .putString("logged_in_user_email", finalProfile.email)
                    .putString("logged_in_user_name", finalProfile.name)
                    .putString("logged_in_user_role", finalProfile.role)
                    .putString("logged_in_user_avatar", finalProfile.avatarEmoji)
                    .putString("logged_in_user_mobile", finalProfile.mobile)
                    .putString("logged_in_user_photo_uri", finalProfile.photoUri)
                    .apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- Supabase Production-Ready Authentication System ---
    var isAuthLoading by mutableStateOf(false)
    var authSuccessMessage by mutableStateOf<String?>(null)

    suspend fun uploadProfilePhoto(context: Context, imageUri: Uri): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            com.example.api.R2SupabaseManager.uploadFileToSupabaseStorage(
                context = context,
                fileUri = imageUri,
                bucketName = "avatars",
                folderPath = "profiles"
            )
        } catch (e: Exception) {
            Log.e("AcademyViewModel", "Profile photo upload to avatars bucket failed: ${e.message}, trying materials bucket...", e)
            com.example.api.R2SupabaseManager.uploadFileToSupabaseStorage(
                context = context,
                fileUri = imageUri,
                bucketName = "materials",
                folderPath = "profile_photos"
            )
        }
    }

    fun signUpWithEmailAndPassword(email: String, password: String, confirmPassword: String, name: String, photoUrl: String = "") {
        viewModelScope.launch {
            authError = null
            authSuccessMessage = null
            isAuthLoading = true

            if (!checkInternetStatus()) {
                authError = "No Internet Connection! Please connect to the internet."
                isAuthLoading = false
                return@launch
            }

            if (name.isBlank()) {
                authError = "Name field cannot be blank."
                isAuthLoading = false
                return@launch
            }

            if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                authError = "Please enter a valid email address."
                isAuthLoading = false
                return@launch
            }

            if (password.length < 8) {
                authError = "Password must be at least 8 characters long."
                isAuthLoading = false
                return@launch
            }

            if (password != confirmPassword) {
                authError = "Password and Confirm Password must match."
                isAuthLoading = false
                return@launch
            }

            try {
                val metadata = mapOf("full_name" to name)
                val response = repository.signUpWithEmail(email, password, metadata)
                val moshiInstance = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

                if (response.isSuccessful) {
                    val json = response.body()?.string() ?: ""
                    val signupResponse = try {
                        moshiInstance.adapter(SupabaseSignupResponse::class.java).fromJson(json)
                    } catch (e: Exception) {
                        null
                    }

                    val uuid = signupResponse?.id ?: signupResponse?.user?.id
                    if (uuid != null) {
                        // Successfully signed up! Now automatically create a profile in the profiles table.
                        val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.getDefault()).format(java.util.Date())
                        
                        // We do not make anyone admin automatically (per instructions: "Do NOT make anyone admin automatically. Do NOT use first-login-is-admin logic.")
                        val profile = ProfileEntity(
                            id = uuid,
                            name = name,
                            email = email,
                            phone = "",
                            photoUrl = photoUrl,
                            role = "student",
                            createdAt = now,
                            lastLogin = now,
                            status = "active"
                        )

                        val inserted = repository.insertProfile(profile)
                        if (!inserted) {
                            Log.e("AcademyViewModel", "Failed to create user profile in profiles table.")
                        }

                        // Map to local representation and login
                        val appUser = AppUser(
                            email = email,
                            name = name,
                            role = "STUDENT",
                            avatarEmoji = "🎓",
                            mobile = "",
                            photoUri = photoUrl
                        )

                        // Save session in SharedPreferences
                        prefs.edit()
                            .putString("logged_in_user_email", appUser.email)
                            .putString("logged_in_user_name", appUser.name)
                            .putString("logged_in_user_role", appUser.role)
                            .putString("logged_in_user_avatar", appUser.avatarEmoji)
                            .putString("logged_in_user_mobile", appUser.mobile)
                            .putString("logged_in_user_photo_uri", appUser.photoUri)
                            .putString("logged_in_user_uuid", uuid)
                            .apply()

                        currentUser = appUser
                        authSuccessMessage = "Account created successfully!"
                    } else {
                        // Signup successful, but needs confirmation or returned unexpected structure
                        authSuccessMessage = "Signup successful! Please check your email for a confirmation link."
                    }
                } else {
                    val errJson = response.errorBody()?.string() ?: ""
                    val errObj = try {
                        moshiInstance.adapter(SupabaseAuthError::class.java).fromJson(errJson)
                    } catch (e: Exception) {
                        null
                    }
                    val errMsg = errObj?.errorDescription ?: errObj?.msg ?: errObj?.message ?: ""
                    
                    if (errMsg.contains("already registered", ignoreCase = true) || errMsg.contains("already exists", ignoreCase = true)) {
                        authError = "Email already exists. This email is already registered."
                    } else if (errMsg.contains("weak", ignoreCase = true)) {
                        authError = "Weak password. Password should be stronger."
                    } else if (response.code() >= 500) {
                        authError = "Server unavailable. Please try again later."
                    } else {
                        authError = errMsg.ifBlank { "Signup failed: ${response.message()}" }
                    }
                }
            } catch (e: Exception) {
                Log.e("AcademyViewModel", "signUpWithEmailAndPassword error", e)
                authError = "An unknown error occurred during signup: ${e.localizedMessage ?: e.message}"
            } finally {
                isAuthLoading = false
            }
        }
    }

    fun signInWithEmailAndPassword(email: String, password: String) {
        viewModelScope.launch {
            authError = null
            authSuccessMessage = null
            isAuthLoading = true

            if (!checkInternetStatus()) {
                authError = "No Internet Connection! Please connect to the internet."
                isAuthLoading = false
                return@launch
            }

            if (email.isBlank() || password.isBlank()) {
                authError = "Email and Password cannot be blank."
                isAuthLoading = false
                return@launch
            }

            try {
                val response = repository.signInWithPassword(email, password)
                val moshiInstance = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

                if (response.isSuccessful) {
                    val json = response.body()?.string() ?: ""
                    val session = try {
                        moshiInstance.adapter(SupabaseSession::class.java).fromJson(json)
                    } catch (e: Exception) {
                        null
                    }

                    val uuid = session?.user?.id
                    if (uuid != null) {
                        // Fetch profile from profiles table to check their role
                        var profile = repository.getProfileById(uuid)

                        if (profile == null) {
                            // If profile doesn't exist for some reason, create it automatically as student
                            val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.getDefault()).format(java.util.Date())
                            profile = ProfileEntity(
                                id = uuid,
                                name = session.user.userMetadata?.get("full_name") as? String ?: email.substringBefore("@"),
                                email = email,
                                phone = "",
                                photoUrl = "",
                                role = "student",
                                createdAt = now,
                                lastLogin = now,
                                status = "active"
                            )
                            repository.insertProfile(profile)
                        } else {
                            // Update last login
                            val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.getDefault()).format(java.util.Date())
                            repository.updateProfile(uuid, mapOf("last_login" to now))
                        }

                        // Check blocked status
                        if (profile.status == "blocked") {
                            authError = "Your account has been blocked by the Administrator."
                            isAuthLoading = false
                            return@launch
                        }

                        // Read role and open Admin Dashboard or Student Dashboard depending only on the database role
                        val dbRole = profile.role.lowercase()
                        val finalRole = if (dbRole == "admin") "ADMIN" else "STUDENT"

                        val appUser = AppUser(
                            email = email,
                            name = profile.name,
                            role = finalRole,
                            avatarEmoji = if (finalRole == "ADMIN") "🎖️" else "🎓",
                            mobile = profile.phone ?: "",
                            photoUri = profile.photoUrl ?: ""
                        )

                        // Save session in SharedPreferences
                        prefs.edit()
                            .putString("logged_in_user_email", appUser.email)
                            .putString("logged_in_user_name", appUser.name)
                            .putString("logged_in_user_role", appUser.role)
                            .putString("logged_in_user_avatar", appUser.avatarEmoji)
                            .putString("logged_in_user_mobile", appUser.mobile)
                            .putString("logged_in_user_photo_uri", appUser.photoUri)
                            .putString("logged_in_user_uuid", uuid)
                            .apply()

                        currentUser = appUser
                        authSuccessMessage = "Successfully Authenticated!"

                        if (finalRole == "ADMIN") {
                            Log.i("AcademyViewModel", "Admin logged in! Syncing remote database...")
                            repository.syncAllFromRemote()
                        }
                    } else {
                        authError = "Invalid Email or Password"
                    }
                } else {
                    val errJson = response.errorBody()?.string() ?: ""
                    val errObj = try {
                        moshiInstance.adapter(SupabaseAuthError::class.java).fromJson(errJson)
                    } catch (e: Exception) {
                        null
                    }
                    val errMsg = errObj?.errorDescription ?: errObj?.msg ?: errObj?.message ?: ""

                    if (errMsg.contains("invalid", ignoreCase = true) || errMsg.contains("credentials", ignoreCase = true)) {
                        authError = "Invalid Email or Password"
                    } else if (response.code() >= 500) {
                        authError = "Server unavailable. Please try again later."
                    } else {
                        authError = errMsg.ifBlank { "Invalid Email or Password" }
                    }
                }
            } catch (e: Exception) {
                Log.e("AcademyViewModel", "signInWithEmailAndPassword error", e)
                authError = "Invalid Email or Password"
            } finally {
                isAuthLoading = false
            }
        }
    }

    fun forgotPassword(email: String) {
        viewModelScope.launch {
            authError = null
            authSuccessMessage = null
            isAuthLoading = true

            if (!checkInternetStatus()) {
                authError = "No Internet Connection! Please connect to the internet."
                isAuthLoading = false
                return@launch
            }

            if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                authError = "Please enter a valid email address."
                isAuthLoading = false
                return@launch
            }

            try {
                val response = repository.recoverPassword(email)
                if (response.isSuccessful) {
                    authSuccessMessage = "Password reset instructions sent successfully! Please check your email inbox."
                } else {
                    val errJson = response.errorBody()?.string() ?: ""
                    val moshiInstance = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                    val errObj = try {
                        moshiInstance.adapter(SupabaseAuthError::class.java).fromJson(errJson)
                    } catch (e: Exception) {
                        null
                    }
                    val errMsg = errObj?.errorDescription ?: errObj?.msg ?: errObj?.message ?: ""
                    if (response.code() >= 500) {
                        authError = "Server unavailable. Please try again later."
                    } else {
                        authError = errMsg.ifBlank { "Failed to send reset instructions: ${response.message()}" }
                    }
                }
            } catch (e: Exception) {
                Log.e("AcademyViewModel", "forgotPassword error", e)
                authError = "An unknown error occurred: ${e.localizedMessage ?: e.message}"
            } finally {
                isAuthLoading = false
            }
        }
    }

    fun logout() {
        currentUser = null
        selectedCourseForDetail = null
        activeTestProgress = null
        liveClassRoomActive = false
        try {
            prefs.edit()
                .remove("logged_in_user_email")
                .remove("logged_in_user_name")
                .remove("logged_in_user_role")
                .remove("logged_in_user_avatar")
                .remove("logged_in_user_mobile")
                .remove("logged_in_user_photo_uri")
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteAccount() {
        val email = currentUser?.email
        currentUser = null
        selectedCourseForDetail = null
        activeTestProgress = null
        liveClassRoomActive = false
        if (email != null) {
            registeredUsers.remove(email)
            try {
                prefs.edit()
                    .remove("reg_$email")
                    .remove("logged_in_user_email")
                    .remove("logged_in_user_name")
                    .remove("logged_in_user_role")
                    .remove("logged_in_user_avatar")
                    .remove("logged_in_user_mobile")
                    .remove("logged_in_user_photo_uri")
                    .apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- Course Actions ---
    fun selectCourse(course: CourseEntity) {
        selectedCourseForDetail = course
        viewModelScope.launch {
            repository.getLessonsForCourse(course.id).collect {
                currentLessonList.value = it
            }
        }
    }

    // Enroll in FREE or PAID courses (Simulation)
    fun enrollInCourse(courseId: Int) {
        val user = currentUser ?: return
        viewModelScope.launch {
            val enrollment = EnrollmentEntity(
                userEmail = user.email,
                courseId = courseId,
                completedLessonsCount = 0,
                isCompleted = false
            )
            repository.insertEnrollment(enrollment)
        }
    }

    // Complete lesson and increase progress
    fun toggleLessonCompletedState(courseId: Int, clickedLessonId: Int, currentlyCompleted: Int, total: Int) {
        val user = currentUser ?: return
        viewModelScope.launch {
            // Find current enrollment
            val enrollments = repository.getEnrollmentsForUser(user.email).first()
            val existing = enrollments.find { it.courseId == courseId } ?: return@launch
            
            val newCompletedCount = (existing.completedLessonsCount + 1).coerceAtMost(total)
            val updated = existing.copy(
                completedLessonsCount = newCompletedCount,
                isCompleted = newCompletedCount == total
            )
            repository.updateEnrollment(updated)
            
            // Log a funny local chat message or feedback
            if (updated.isCompleted) {
                repository.insertNotification(
                    NotificationEntity(
                        title = "Course Completed! 🎉",
                        message = "Congratulations! You have completed all lessons of the course. Claim your graduation certificate inside your Profile section."
                    )
                )
            }
        }
    }

    // --- Test System Action & Engines ---
    fun startTest(test: TestEntity) {
        viewModelScope.launch {
            val user = currentUser ?: return@launch
            val scoreEntity = repository.allScores.first().find { it.userEmail == user.email && it.testId == test.id }
            val questions = repository.getQuestionsForTest(test.id).first()
            if (questions.isNotEmpty()) {
                if (scoreEntity != null) {
                    val type = Types.newParameterizedType(Map::class.java, Integer::class.java, Integer::class.java)
                    val adapter: JsonAdapter<Map<Int, Int>> = Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build().adapter(type)
                    val map = try { adapter.fromJson(scoreEntity.selectedAnswersJson) ?: emptyMap() } catch (e: Exception) { emptyMap() }
                    
                    activeTestProgress = ActiveTestProgress(
                        test = test,
                        questions = questions,
                        secondsRemaining = 0,
                        isSubmitted = true,
                        testScore = scoreEntity
                    ).apply {
                        selectedAnswers.putAll(map)
                    }
                } else {
                    activeTestProgress = ActiveTestProgress(
                        test = test,
                        questions = questions,
                        secondsRemaining = test.durationMinutes * 60
                    )
                    startTestTimer()
                }
            }
        }
    }

    private fun startTestTimer() {
        viewModelScope.launch {
            while (activeTestProgress?.isSubmitted == false && (activeTestProgress?.secondsRemaining ?: 0) > 0) {
                delay(1000)
                activeTestProgress?.let {
                    if (it.secondsRemaining > 0 && !it.isSubmitted) {
                        activeTestProgress = it.copy(secondsRemaining = it.secondsRemaining - 1)
                    }
                }
            }
            // Auto submit if timer runs out and not submitted
            activeTestProgress?.let {
                if (!it.isSubmitted && it.secondsRemaining <= 0) {
                    submitActiveTest()
                }
            }
        }
    }

    fun selectTestAnswer(questionId: Int, index: Int) {
        activeTestProgress?.selectedAnswers?.put(questionId, index)
    }

    fun toggleReviewQuestion(questionId: Int) {
        val progress = activeTestProgress ?: return
        if (progress.reviewedQuestions.contains(questionId)) {
            progress.reviewedQuestions.remove(questionId)
        } else {
            progress.reviewedQuestions.add(questionId)
        }
        activeTestProgress = progress.copy()
    }

    fun clearTestAnswer(questionId: Int) {
        val progress = activeTestProgress ?: return
        progress.selectedAnswers.remove(questionId)
        activeTestProgress = progress.copy()
    }

    fun submitActiveTest() {
        val user = currentUser ?: return
        val currentProgress = activeTestProgress ?: return
        
        viewModelScope.launch {
            var correctCount = 0
            var wrongCount = 0
            val answersMap = mutableMapOf<Int, Int>()
            
            currentProgress.questions.forEach { question ->
                val selectedIndex = currentProgress.selectedAnswers[question.id]
                if (selectedIndex != null) {
                    answersMap[question.id] = selectedIndex
                    if (selectedIndex == question.correctIndex) {
                        correctCount++
                    } else {
                        wrongCount++
                    }
                }
            }
            
            // Calculate marks
            val baseMark = currentProgress.test.marksPerCorrect
            val penalty = if (currentProgress.test.hasNegativeMarking) currentProgress.test.marksPerWrong else 0f
            
            val rawScore = (correctCount * baseMark) + (wrongCount * penalty)
            val finalScore = String.format("%.2f", rawScore).toFloat()

            val scoreEntity = TestScoreEntity(
                testId = currentProgress.test.id,
                testTitle = currentProgress.test.title,
                userEmail = user.email,
                score = finalScore,
                totalQuestions = currentProgress.questions.size,
                correctAnswers = correctCount,
                wrongAnswers = wrongCount,
                selectedAnswersJson = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                    .adapter<Map<Int, Int>>(Types.newParameterizedType(Map::class.java, Integer::class.java, Integer::class.java))
                    .toJson(answersMap)
            )
            
            repository.insertScore(scoreEntity)
            
            activeTestProgress = currentProgress.copy(
                isSubmitted = true,
                testScore = scoreEntity
            )
            
            // Insert notification
            repository.insertNotification(
                NotificationEntity(
                    title = "Test Submitted! 📝",
                    message = "You scored $finalScore marks in '${currentProgress.test.title}'. View report details inside Test Series tab."
                )
            )
        }
    }

    fun exitTest() {
        activeTestProgress = null
    }

    fun updateTestQuestionIndex(index: Int) {
        activeTestProgress = activeTestProgress?.copy(currentQuestionIndex = index)
    }

    fun getVideoQuotaRemaining(): Int {
        val user = currentUser ?: return 0
        val prefs = getApplication<android.app.Application>().getSharedPreferences("lakshya_video_quota", android.content.Context.MODE_PRIVATE)
        val currentWeek = java.util.Calendar.getInstance().get(java.util.Calendar.WEEK_OF_YEAR)
        val key = "vid_quota_${user.email}_${currentWeek}"
        val used = prefs.getInt(key, 0)
        return maxOf(0, 5 - used)
    }

    fun incrementVideoQuota(): Boolean {
        val user = currentUser ?: return false
        val prefs = getApplication<android.app.Application>().getSharedPreferences("lakshya_video_quota", android.content.Context.MODE_PRIVATE)
        val currentWeek = java.util.Calendar.getInstance().get(java.util.Calendar.WEEK_OF_YEAR)
        val key = "vid_quota_${user.email}_${currentWeek}"
        val current = prefs.getInt(key, 0)
        if (current >= 5) return false
        prefs.edit().putInt(key, current + 1).apply()
        return true
    }

    // --- Support & Chat Section ---
    fun sendSupportChatMessage(text: String, isUserAdmin: Boolean = false) {
        val user = currentUser ?: return
        if (text.isBlank()) return
        
        viewModelScope.launch {
            val message = ChatMessageEntity(
                senderName = user.name,
                senderEmail = user.email,
                text = text.trim(),
                isAdminReply = isUserAdmin
            )
            repository.insertChatMessage(message)
            
            // If sender is a regular student, trigger an automated educational response after short delay to make it highly immersive!
            if (!isUserAdmin) {
                delay(1200)
                val responseTemplate = listOf(
                    "Thank you for contacting Lakshya Academy. Lakshya teachers at Ghazipur are actively processing your study inquiry. 📚 We will mail you special updates or reply shortly!",
                    "Your question is received! 😊 Note that our upcoming live batch starts next week. Let us know if you need enrollment guidelines.",
                    "Hello! You can purchase books, notes, and pyps syllabus inside the respective library drawers. Keep up the high effort study! 🎯"
                ).random()
                
                repository.insertChatMessage(
                    ChatMessageEntity(
                        senderName = "Ghazipur Office Robot",
                        senderEmail = "support@lakshya.academy",
                        text = responseTemplate,
                        isAdminReply = true
                    )
                )
            }
        }
    }

    // --- Doubts Thread ---
    fun postDoubt(subject: String, question: String) {
        val user = currentUser ?: return
        if (subject.isBlank() || question.isBlank()) return
        
        viewModelScope.launch {
            val doubt = DoubtEntity(
                userEmail = user.email,
                userName = user.name,
                subject = subject.trim(),
                questionText = question.trim()
            )
            repository.insertDoubt(doubt)
        }
    }

    fun submitDoubtReply(doubt: DoubtEntity, reply: String) {
        val admin = currentUser ?: return
        if (admin.role != "ADMIN" || reply.isBlank()) return
        
        viewModelScope.launch {
            val updated = doubt.copy(
                replyText = reply.trim(),
                answeredBy = admin.name
            )
            repository.updateDoubt(updated)
        }
    }

    // --- Live Class Actions ---
    fun joinLiveClass() {
        liveClassRoomActive = true
        liveViewerCount = 0
        liveChatMessageList.clear()
    }

    suspend fun getLiveClassFromSupabase(id: Int): LiveClassEntity? {
        return try {
            val list = repository.getRemoteLiveClassesDirect()
            list.find { it.id == id }
        } catch (e: Exception) {
            android.util.Log.e("AcademyViewModel", "Failed to get live class from Supabase", e)
            null
        }
    }

    fun sendLiveMessage(txt: String) {
        // Live chat is not implemented per instructions
    }

    fun leaveLiveClass() {
        liveClassRoomActive = false
    }

    // --- ADMIN MODULE ACTIONS ---
    fun adminAddNewCourse(title: String, category: String, subject: String, desc: String, isFree: Boolean, price: Double, imageUrl: String = "") {
        if (currentUser?.role != "ADMIN") return
        if (title.isBlank() || subject.isBlank() || desc.isBlank()) return
        viewModelScope.launch {
            var finalImageUrl = imageUrl.trim()
            if (finalImageUrl.startsWith("content://") || finalImageUrl.startsWith("file://")) {
                try {
                    val context = getApplication<Application>()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Uploading batch cover image to Supabase Storage...", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    val uploadedUrl = uploadBatchImageWithRetry(context, android.net.Uri.parse(finalImageUrl))
                    android.util.Log.i("AcademyViewModel", "[UPLOAD SUCCESS] Batch cover image uploaded to Supabase Storage.")
                    finalImageUrl = uploadedUrl
                    android.util.Log.i("AcademyViewModel", "[PUBLIC URL] Generated Public Supabase Storage URL: $finalImageUrl")
                    android.util.Log.i("AcademyViewModel", "[COVER IMAGE] Generated Public Supabase Storage URL: $finalImageUrl")
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Batch cover image uploaded successfully to Supabase!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AcademyViewModel", "Failed to upload batch cover image to Supabase, falling back to local uri", e)
                    val context = getApplication<Application>()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Failed to upload image: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }

            android.util.Log.i("AcademyViewModel", "[DATABASE] Attempting to INSERT new course with Image URL: $finalImageUrl")
            val newId = repository.insertCourse(
                CourseEntity(
                    title = title.trim(),
                    category = category,
                    subject = subject,
                    description = desc.trim(),
                    isFree = isFree,
                    price = if (isFree) 0.0 else price,
                    totalLessons = 0,
                    imageUrl = finalImageUrl,
                )
            )
            
            if (newId > 0) {
                android.util.Log.i("AcademyViewModel", "[DATABASE] New course successfully inserted (Local ID: $newId).")
                repository.insertNotification(
                    NotificationEntity(
                        title = "New Course Added! 🎓",
                        message = "Lakshya Academy just launched '${title}' online batch! Enroll now."
                    )
                )
                // Reload batches to display new cover image
                repository.syncAllFromRemote()
            } else {
                android.util.Log.e("AcademyViewModel", "[DATABASE] Failed to insert new course locally.")
            }
        }
    }

    fun adminDeleteCourse(id: Int) {
        if (currentUser?.role != "ADMIN") return
        viewModelScope.launch {
            repository.deleteCourse(id)
        }
    }

    fun adminUpdateCourse(id: Int, title: String, description: String, price: Double, isFree: Boolean, imageUrl: String) {
        if (currentUser?.role != "ADMIN") return
        viewModelScope.launch {
            var finalImageUrl = imageUrl.trim()
            if (finalImageUrl.startsWith("content://") || finalImageUrl.startsWith("file://")) {
                try {
                    val context = getApplication<Application>()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Uploading updated batch cover image to Supabase Storage...", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    val uploadedUrl = uploadBatchImageWithRetry(context, android.net.Uri.parse(finalImageUrl))
                    android.util.Log.i("AcademyViewModel", "[UPLOAD SUCCESS] Batch cover image updated successfully on Supabase Storage.")
                    finalImageUrl = uploadedUrl
                    android.util.Log.i("AcademyViewModel", "[PUBLIC URL] Generated Public Supabase Storage URL: $finalImageUrl")
                    android.util.Log.i("AcademyViewModel", "[COVER IMAGE] Generated Public Supabase Storage URL: $finalImageUrl")
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Batch cover image updated successfully on Supabase!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AcademyViewModel", "Failed to upload batch cover image to Supabase, falling back to local uri", e)
                    val context = getApplication<Application>()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Failed to upload image: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }

            val course = allCourses.value.find { it.id == id }
            if (course != null) {
                android.util.Log.i("AcademyViewModel", "[DATABASE] Attempting to UPDATE course ID: $id with Image URL: $finalImageUrl")
                val success = repository.updateCourse(course.copy(
                    title = title.trim(),
                    description = description.trim(),
                    price = price,
                    isFree = isFree,
                    imageUrl = finalImageUrl
                ))
                
                if (success) {
                    android.util.Log.i("AcademyViewModel", "[DATABASE] Course ID: $id successfully updated and synced to Supabase.")
                    // Force reload to ensure the new cover image is displayed immediately
                    repository.syncAllFromRemote()
                } else {
                    android.util.Log.e("AcademyViewModel", "[DATABASE] Failed to sync course update to Supabase for ID: $id")
                }
            }
        }
    }

    fun updateCourseThumbnail(courseId: Int, newThumbnailUrl: String) {
        if (currentUser?.role != "ADMIN") return
        viewModelScope.launch {
            val course = allCourses.value.find { it.id == courseId }
            if (course != null) {
                repository.updateCourse(course.copy(imageUrl = newThumbnailUrl.trim()))
            }
        }
    }

    fun adminAddLessonToCourse(
        courseId: Int,
        chapter: String,
        folder: String = "All video",
        title: String,
        videoLink: String,
        pdfLink: String,
        pdfName: String,
        pdfContent: String = "",
        fileSize: String = "2.5 MB",
        thumbnailUrl: String = ""
    ) {
        if (currentUser?.role != "ADMIN") return
        if (chapter.isBlank() || title.isBlank()) return
        viewModelScope.launch {
            val finalVideoUrl = if (videoLink.isBlank()) "https://www.w3schools.com/html/mov_bbb.mp4" else videoLink.trim()
            val finalSourceType = com.example.ui.screens.detectVideoSourceType(finalVideoUrl)
            
            repository.insertLesson(
                LessonEntity(
                    courseId = courseId,
                    chapterName = chapter.trim(),
                    folder = folder.trim(),
                    title = title.trim(),
                    videoUrl = finalVideoUrl,
                    pdfUrl = if (pdfLink.isBlank()) "Class_Handout.pdf" else pdfLink.trim(),
                    pdfName = if (pdfName.isBlank()) "Study notes compilation" else pdfName.trim(),
                    pdfContent = pdfContent.trim(),
                    fileSize = if (fileSize.isBlank()) "2.5 MB" else fileSize.trim(),
                    thumbnailUrl = thumbnailUrl.trim(),
                    videoSourceType = finalSourceType
                )
            )
            val currentCourse = allCourses.value.find { it.id == courseId }
            if (currentCourse != null) {
                repository.updateCourse(currentCourse.copy(totalLessons = currentCourse.totalLessons + 1))
            }
        }
    }

    fun adminDeleteLesson(lessonId: Int, courseId: Int) {
        if (currentUser?.role != "ADMIN") return
        viewModelScope.launch {
            repository.deleteLesson(lessonId)
            val currentCourse = allCourses.value.find { it.id == courseId }
            if (currentCourse != null) {
                repository.updateCourse(currentCourse.copy(totalLessons = (currentCourse.totalLessons - 1).coerceAtLeast(0)))
            }
        }
    }

    // AI Mock Test Generator removed as requested (replaced by new Module 3).

    fun forceReSeedTestsIfNeeded() {
        return
        viewModelScope.launch {
            val checkFlag = prefs.getBoolean("has_cleaned_and_seeded_tests_v6", false)
            if (!checkFlag) {
                repository.deleteAllTests()
                repository.deleteAllQuestions()

                for (clsNum in 1..12) {
                    val subjects = when {
                        clsNum <= 5 -> listOf("Mathematics / गणित", "EVS / पर्यावरण अध्ययन", "Hindi & English / हिंदी और अंग्रेजी")
                        clsNum <= 10 -> listOf("Mathematics / गणित", "Science / विज्ञान", "Social Science / सामाजिक विज्ञान", "Hindi & English / हिंदी और अंग्रेजी")
                        else -> listOf("Physics / भौतिक विज्ञान", "Chemistry / रसायन विज्ञान", "Biology / जीव विज्ञान", "Mathematics / गणित")
                    }

                    for (subjName in subjects) {
                        val tId = repository.insertTest(
                            TestEntity(
                                title = "Class $clsNum Mock Test - $subjName",
                                type = "Mock Test",
                                durationMinutes = 60,
                                hasNegativeMarking = true,
                                marksPerCorrect = 4,
                                marksPerWrong = -1.0f
                            )
                        )

                        // Generate exactly 50 questions for this class and subject
                        for (qIndex in 1..50) {
                            var questionText = ""
                            var optA = ""
                            var optB = ""
                            var optC = ""
                            var optD = ""
                            val correctIdx = (qIndex % 4)

                            when {
                                subjName.contains("Math") -> {
                                    val num1 = 10 + (qIndex * 7) % 90
                                    val num2 = 5 + (qIndex * 13) % 40
                                    val sumVal = num1 + num2
                                    val diffVal = num1 - num2
                                    when (qIndex % 4) {
                                        0 -> {
                                            questionText = "Class $clsNum Maths: Simple calculation. What is $num1 + $num2? / कक्षा $clsNum गणित: सरल गणना। $num1 + $num2 का मान क्या है?"
                                            optA = if (correctIdx == 0) "$sumVal" else "${sumVal + 5}"
                                            optB = if (correctIdx == 1) "$sumVal" else "${sumVal - 3}"
                                            optC = if (correctIdx == 2) "$sumVal" else "${sumVal + 12}"
                                            optD = if (correctIdx == 3) "$sumVal" else "${sumVal - 1}"
                                        }
                                        1 -> {
                                            questionText = "Class $clsNum Maths: Subtraction. What is $num1 - $num2? / कक्षा $clsNum गणित: घटाव। $num1 - $num2 का मान क्या है?"
                                            optA = if (correctIdx == 0) "$diffVal" else "${diffVal + 4}"
                                            optB = if (correctIdx == 1) "$diffVal" else "${diffVal - 6}"
                                            optC = if (correctIdx == 2) "$diffVal" else "${diffVal + 15}"
                                            optD = if (correctIdx == 3) "$diffVal" else "${diffVal - 1}"
                                        }
                                        2 -> {
                                            val shortNum1 = 2 + (qIndex % 10)
                                            val shortNum2 = 3 + (qIndex % 8)
                                            val multVal = shortNum1 * shortNum2
                                            questionText = "Class $clsNum Maths: Multiplication. What is $shortNum1 x $shortNum2? / कक्षा $clsNum गणित: गुणा। $shortNum1 x $shortNum2 का मान क्या है?"
                                            optA = if (correctIdx == 0) "$multVal" else "${multVal + 6}"
                                            optB = if (correctIdx == 1) "$multVal" else "${multVal - 2}"
                                            optC = if (correctIdx == 2) "$multVal" else "${multVal + 10}"
                                            optD = if (correctIdx == 3) "$multVal" else "${multVal - 4}"
                                        }
                                        else -> {
                                            val sqNum = 2 + (qIndex % 12)
                                            val sqVal = sqNum * sqNum
                                            questionText = "Class $clsNum Maths: What is the square of $sqNum? / कक्षा $clsNum गणित: $sqNum का वर्ग क्या है?"
                                            optA = if (correctIdx == 0) "$sqVal" else "${sqVal + 9}"
                                            optB = if (correctIdx == 1) "$sqVal" else "${sqVal - 8}"
                                            optC = if (correctIdx == 2) "$sqVal" else "${sqVal + 20}"
                                            optD = if (correctIdx == 3) "$sqVal" else "${sqVal - 2}"
                                        }
                                    }
                                }
                                subjName.contains("Science") || subjName.contains("Physics") || subjName.contains("Chemistry") || subjName.contains("Biology") || subjName.contains("EVS") -> {
                                    val topics = listOf(
                                        Triple("Powerhouse of the cell / कोशिका का बिजलीघर", "Mitochondria / माइटोकॉन्ड्रिया", "Ribosome / राइबोसोम"),
                                        Triple("Boiling point of pure water / शुद्ध जल का क्वथनांक", "100°C / १००°C", "0°C / ०°C"),
                                        Triple("Simplest ketone / सबसे सरल कीटोन", "Propanone / प्रोपेनोन", "Ethanol / इथेनॉल"),
                                        Triple("Most electronegative element / सबसे अधिक विद्युत ऋणात्मक तत्व", "Fluorine / फ्लोरीन", "Chlorine / क्लोरीन"),
                                        Triple("Double helix model of DNA discovered by / डीएनए के द्विकुंडलित मॉडल की खोज की थी", "Watson and Crick / वाटसन और क्रिक", "Mendel / मेंडल"),
                                        Triple("SI Unit of Resistance / प्रतिरोध का SI मात्रक", "Ohm / ओम", "Volt / वोल्ट"),
                                        Triple("What gas do plants absorb during photosynthesis? / प्रकाश संश्लेषण के दौरान पौधे कौन सी गैस अवशोषित करते हैं?", "Carbon Dioxide / कार्बन डाइऑक्साइड", "Oxygen / ऑक्सीजन"),
                                        Triple("Which planet is known as Red Planet? / किस ग्रह को लाल ग्रह के रूप में जाना जाता है?", "Mars / मंगल", "Jupiter / बृहस्पति"),
                                        Triple("The primary source of energy on Earth is / पृथ्वी पर ऊर्जा का प्राथमिक स्रोत है", "The Sun / सूर्य", "Wind / पवन"),
                                        Triple("Which non-metal is in liquid state / कौन सी अधातु द्रव अवस्था में होती है", "Bromine / ब्रोमीन", "Chlorine / क्लोरीन")
                                    )
                                    val selected = topics[qIndex % topics.size]
                                    questionText = "Class $clsNum Science ($subjName): Identify '${selected.first}'? / कक्षा $clsNum विज्ञान ($subjName): '${selected.first}' की पहचान करें?"
                                    optA = if (correctIdx == 0) selected.second else selected.third
                                    optB = if (correctIdx == 1) selected.second else "None of these / इनमें से कोई नहीं"
                                    optC = if (correctIdx == 2) selected.second else "Both / दोनों"
                                    optD = if (correctIdx == 3) selected.second else "All of the above / उपरोक्त सभी"
                                }
                                subjName.contains("Social") || subjName.contains("SST") || subjName.contains("इतिहास") || subjName.contains("भूगोल") -> {
                                    val topics = listOf(
                                        Triple("Battle of Plassey took place in year / प्लासी का युद्ध किस वर्ष हुआ था?", "1757", "1857"),
                                        Triple("Primary organ of Indian Constitution implementation / भारतीय संविधान को लागू करने वाला प्राथमिक अंग है", "Parliament / संसद", "Supreme Court / सर्वोच्च न्यायालय"),
                                        Triple("Largest ocean on Earth / पृथ्वी का सबसे बड़ा महासागर है", "Pacific Ocean / प्रशांत महासागर", "Atlantic Ocean / अटलांटिक महासागर"),
                                        Triple("In which year did India celebrate its first Republic Day? / भारत ने अपना पहला गणतंत्र दिवस किस वर्ष मनाया था?", "1950 / १९५०", "1947 / १९४७"),
                                        Triple("Which is the longest river flowing in India? / भारत में बहने वाली सबसे लंबी नदी कौन सी है?", "Ganga / गंगा", "Yamuna / यमुना"),
                                        Triple("Who is known as Father of Indian Constitution? / भारतीय संविधान के जनक के रूप में किसे जाना जाता है?", "Dr. B.R. Ambedkar / डॉ. बी.आर. अंबेडकर", "Mahatma Gandhi / महात्मा गांधी"),
                                        Triple("Capital of India before New Delhi / नई दिल्ली से पहले भारत की राजधानी कौन सी थी?", "Calcutta (Kolkata) / कलकत्ता", "Bombay (Mumbai) / मुंबई"),
                                        Triple("Indian State with longest coastline / सबसे लंबी तटरेखा वाला भारतीय राज्य कौन सा है?", "Gujarat / गुजरात", "Maharashtra / महाराष्ट्र")
                                    )
                                    val selected = topics[qIndex % topics.size]
                                    questionText = "Class $clsNum SST: ${selected.first}"
                                    optA = if (correctIdx == 0) selected.second else selected.third
                                    optB = if (correctIdx == 1) selected.second else "Mughal Empire / मुगल साम्राज्य"
                                    optC = if (correctIdx == 2) selected.second else "Indus River / सिंधु नदी"
                                    optD = if (correctIdx == 3) selected.second else "None of the above / इनमें से कोई नहीं"
                                }
                                else -> {
                                    val topics = listOf(
                                        Triple("Opposite of 'Hot' / 'गर्म' का विलोम शब्द है", "Cold / ठंडा", "Warm / गुनगुना"),
                                        Triple("Abstract noun of 'Beautiful' is / 'Beautiful' का भाववाचक संज्ञा शब्द है", "Beauty / सुंदरता", "Beautify / सुंदर बनाना"),
                                        Triple("Identify the correct spelling / सही वर्तनी की पहचान करें", "Receive / प्राप्त करना", "Recieve / प्राप्त करना"),
                                        Triple("Plural form of 'Child' is / 'Child' का बहुवचन रूप है", "Children / बच्चे", "Childs / बच्चे"),
                                        Triple("Opposite of 'Happy' / 'खुश' का विलोम शब्द है", "Sad / दुखी", "Glad / प्रसन्न"),
                                        Triple("Identify correct grammar: 'She ____ a letter yesterday' / 'She ____ _ yesterday' का सही रूप", "wrote / लिखा", "writes / लिखती है")
                                    )
                                    val selected = topics[qIndex % topics.size]
                                    questionText = "Class $clsNum Language: What is the correct answer for '${selected.first}'? / कक्षा $clsNum भाषा: '${selected.first}' का सही उत्तर क्या है?"
                                    optA = if (correctIdx == 0) selected.second else selected.third
                                    optB = if (correctIdx == 1) selected.second else "Incorrect / अशुद्ध"
                                    optC = if (correctIdx == 2) selected.second else "Noun / संज्ञा"
                                    optD = if (correctIdx == 3) selected.second else "None of the above / इनमें से कोई नहीं"
                                }
                            }

                            repository.insertQuestion(
                                QuestionEntity(
                                    testId = tId,
                                    questionText = questionText,
                                    optionA = optA,
                                    optionB = optB,
                                    optionC = optC,
                                    optionD = optD,
                                    correctIndex = correctIdx
                                )
                            )
                        }
                    }
                }
                prefs.edit().putBoolean("has_cleaned_and_seeded_tests_v6", true).apply()
            }
        }
    }

    fun generateWeeklyMockTests() {
        return
        viewModelScope.launch {
            val user = currentUser ?: return@launch
            val existingTests = repository.allTests.first()
            val classesToGenerate = (1..12).map { "Class $it" }
            val currentWeek = java.util.Calendar.getInstance().get(java.util.Calendar.WEEK_OF_YEAR)
            
            for (cls in classesToGenerate) {
                val title = "$cls Weekly Mock Test (Week $currentWeek)"
                if (existingTests.none { it.title == title }) {
                    val tId = repository.insertTest(
                        TestEntity(
                            title = title,
                            type = "Weekly Auto Test",
                            durationMinutes = 60,
                            hasNegativeMarking = true,
                            marksPerCorrect = 4,
                            marksPerWrong = -1f
                        )
                    )
                    
                    val subjects = listOf("Science/विज्ञान", "History/इतिहास", "English/अंग्रेजी", "GK/सामान्य ज्ञान")
                    for (i in 1..50) {
                        val isMath = (1..10).random() > 6
                        val questionText: String
                        val optA: String
                        val optB: String
                        val optC: String
                        val optD: String
                        val correctIdx = (0..3).random()

                        if (isMath) {
                            val a = (11..99).random()
                            val b = (15..95).random()
                            val mathType = (0..2).random()
                            when (mathType) {
                                0 -> {
                                    val ans = a + b
                                    questionText = "Class $cls Maths: What is $a + $b? / $cls गणित: $a और $b का योग क्या है?"
                                    optA = if (correctIdx == 0) "$ans" else "${ans + (1..10).random()}"
                                    optB = if (correctIdx == 1) "$ans" else "${ans - (1..10).random()}"
                                    optC = if (correctIdx == 2) "$ans" else "${ans + 12}"
                                    optD = if (correctIdx == 3) "$ans" else "${ans - 5}"
                                }
                                1 -> {
                                    val ans = a - b
                                    questionText = "Class $cls Maths: What is $a - $b? / $cls गणित: $a - $b का मान क्या है?"
                                    optA = if (correctIdx == 0) "$ans" else "${ans + 3}"
                                    optB = if (correctIdx == 1) "$ans" else "${ans - 4}"
                                    optC = if (correctIdx == 2) "$ans" else "${ans + 10}"
                                    optD = if (correctIdx == 3) "$ans" else "${ans - 1}"
                                }
                                else -> {
                                    val xVal = (2..9).random()
                                    val yVal = (3..9).random()
                                    val ans = xVal * yVal
                                    questionText = "Class $cls Maths: What is $xVal x $yVal? / $cls गणित: $xVal x $yVal का गुणात्मक मान क्या है?"
                                    optA = if (correctIdx == 0) "$ans" else "${ans + 6}"
                                    optB = if (correctIdx == 1) "$ans" else "${ans - 2}"
                                    optC = if (correctIdx == 2) "$ans" else "${ans + 8}"
                                    optD = if (correctIdx == 3) "$ans" else "${ans - 1}"
                                }
                            }
                        } else {
                            val subjectEnum = subjects.random()
                            val topics = when {
                                subjectEnum.contains("Science") || subjectEnum.contains("विज्ञान") -> listOf(
                                    Triple("What is the chemical formula of Water? / पानी का रासायनिक सूत्र क्या है?", "H2O / एच२ओ", "CO2 / CO2"),
                                    Triple("Which gas do we inhale to breathe? / हम सांस लेने के लिए कौन सी गैस ग्रहण करते हैं?", "Oxygen / ऑक्सीजन", "Nitrogen / नाइट्रोजन"),
                                    Triple("What is the primary source of energy on Earth? / पृथ्वी पर ऊर्जा का प्राथमिक स्रोत क्या है?", "Sunlight / सूर्य का प्रकाश", "Coal / कोयला"),
                                    Triple("Which organ pumps blood in human body? / मानव शरीर में रक्त को कौन सा अंग पंप करता है?", "Heart / हृदय", "Lungs / फेफड़े"),
                                    Triple("What is the boiling point of pure water? / शुद्ध पानी का क्वथनांक क्या होता है?", "100°C / १००°C", "0°C / ०°C")
                                )
                                subjectEnum.contains("History") || subjectEnum.contains("SST") || subjectEnum.contains("इतिहास") -> listOf(
                                    Triple("When did India become independent? / भारत कब स्वतंत्र हुआ था?", "1947 / १९४७", "1950 / १९५०"),
                                    Triple("Who is known as the Father of the Nation of India? / भारत के राष्ट्रपिता के रूप में किसे जाना जाता है?", "Mahatma Gandhi / महात्मा गांधी", "Jawaharlal Nehru / जवाहरलाल नेहरू"),
                                    Triple("Where is the famous Taj Mahal located? / प्रसिद्ध ताजमहल कहाँ स्थित है?", "Agra / आगरा", "Delhi / दिल्ली"),
                                    Triple("Which is the largest country by land area? / क्षेत्रफल के हिसाब से सबसे बड़ा देश कौन सा है?", "Russia / रूस", "Canada / कनाडा"),
                                    Triple("Who designed the Indian National Flag? / भारतीय राष्ट्रीय ध्वज का डिजाइन किसने किया था?", "Pingali Venkayya / पिंगली वेंकैया", "Rabindranath Tagore / रवींद्रनाथ टैगोर")
                                )
                                else -> listOf(
                                    Triple("Identify the opposite of word 'Heavy'. / 'Heavy' (भारी) शब्द का विलोम क्या है?", "Light / हल्का", "Hard / कठिन"),
                                    Triple("Identify proper noun in: 'Ramu is a boy'. / 'Ramu is a boy' में व्यक्तिवाचक संज्ञा शब्द क्या है?", "Ramu / रामू", "boy / लड़का"),
                                    Triple("Choose the correct spelling / सही वर्तनी का चयन करें:", "Approve / स्वीकृत", "Aprove / स्वीकृत"),
                                    Triple("What is the plural of 'Tooth'? / 'Tooth' का बहुवचन क्या है?", "Teeth / दांत", "Tooths / टूथ्स"),
                                    Triple("Opposite of 'Rich' is / 'Rich' का विलोम शब्द है:", "Poor / गरीब", "Wealthy / अमीर")
                                )
                            }
                            val indexCalc = (i + cls.hashCode()) % topics.size
                            val selectedIdx = if (indexCalc < 0) indexCalc + topics.size else indexCalc
                            val selected = topics[selectedIdx]
                            questionText = "Class $cls $subjectEnum: ${selected.first}"
                            optA = if (correctIdx == 0) selected.second else selected.third
                            optB = if (correctIdx == 1) selected.second else "None of these / इनमें से कोई नहीं"
                            optC = if (correctIdx == 2) selected.second else "All of the above / उपरोक्त सभी"
                            optD = if (correctIdx == 3) selected.second else "Both / दोनों"
                        }

                        repository.insertQuestion(
                            QuestionEntity(
                                testId = tId,
                                questionText = questionText,
                                optionA = optA,
                                optionB = optB,
                                optionC = optC,
                                optionD = optD,
                                correctIndex = correctIdx
                            )
                        )
                    }
                }
            }
        }
    }

    fun adminCreateNewTest(title: String, type: String, duration: Int, negativeMarking: Boolean, correctMark: Int, penaltyMark: Float) {
        if (currentUser?.role != "ADMIN") return
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.insertTest(
                TestEntity(
                    title = title.trim(),
                    type = type,
                    durationMinutes = duration,
                    hasNegativeMarking = negativeMarking,
                    marksPerCorrect = correctMark,
                    marksPerWrong = penaltyMark
                )
            )
            repository.insertNotification(
                NotificationEntity(
                    title = "New Test Live! 📝",
                    message = "A new '${type}' titled '${title}' is live under test panels. Gauge your performance!"
                )
            )
        }
    }

    fun adminAddQuestionToTest(testId: Int, quest: String, a: String, b: String, c: String, d: String, correct: Int) {
        if (currentUser?.role != "ADMIN") return
        if (quest.isBlank() || a.isBlank() || b.isBlank()) return
        viewModelScope.launch {
            repository.insertQuestion(
                QuestionEntity(
                    testId = testId,
                    questionText = quest.trim(),
                    optionA = a.trim(),
                    optionB = b.trim(),
                    optionC = c.trim(),
                    optionD = d.trim(),
                    correctIndex = correct
                )
            )
        }
    }

    fun adminDeleteTest(id: Int, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (currentUser?.role != "ADMIN") {
            onError("Permission denied")
            return
        }
        viewModelScope.launch {
            val success = repository.deleteTest(id)
            if (success) {
                try {
                    repository.syncAllFromRemote()
                } catch (e: Exception) {
                    android.util.Log.e("AcademyViewModel", "Failed to sync tests after delete: ${e.message}", e)
                }
                onSuccess()
            } else {
                onError("Failed to delete Mock Test from Supabase.")
            }
        }
    }

    fun adminUpdateTest(test: TestEntity) {
        if (currentUser?.role != "ADMIN") return
        viewModelScope.launch {
            repository.updateTest(test)
        }
    }

    fun adminUploadMaterial(type: String, title: String, desc: String, size: String, content: String = "") {
        if (currentUser?.role != "ADMIN") return
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.insertMaterial(
                MaterialEntity(
                    type = type,
                    title = title.trim(),
                    description = desc.trim(),
                    fileSize = if (size.isBlank()) "1.5 MB" else size.trim(),
                    fileContent = content.trim()
                )
            )
            repository.insertNotification(
                NotificationEntity(
                    title = "New Study Material Uploaded! 📚",
                    message = "New ${type} document labeled '${title}' has been uploaded. Download it now!"
                )
            )
        }
    }

    fun adminDeleteMaterial(id: Int) {
        if (currentUser?.role != "ADMIN") return
        viewModelScope.launch {
            repository.deleteMaterial(id)
        }
    }

    fun adminSendPushNotification(title: String, body: String) {
        if (currentUser?.role != "ADMIN") return
        if (title.isBlank() || body.isBlank()) return
        viewModelScope.launch {
            repository.insertNotification(
                NotificationEntity(
                    title = title.trim(),
                    message = body.trim()
                )
            )
        }
    }

    fun adminScheduleLiveClass(title: String, teacher: String) {
        if (currentUser?.role != "ADMIN") return
        if (title.isBlank() || teacher.isBlank()) return
        activeLiveStreamTitle = title.trim()
        activeLiveStreamTeacher = teacher.trim()
        activeLiveIsScheduled = true
        viewModelScope.launch {
            repository.insertNotification(
                NotificationEntity(
                    title = "🔴 Class Live scheduled!",
                    message = "'${title.trim()}' starts now by ${teacher.trim()} Sir! Click to join dynamic discussion."
                )
            )
        }
    }

    fun adminAddBanner(
        title: String,
        imageUrl: String,
        linkUrl: String,
        buttonText: String,
        description: String = "",
        isActive: Boolean = true,
        displayOrder: Int = 0
    ) {
        if (currentUser?.role != "ADMIN") return
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.insertBanner(
                BannerEntity(
                    title = title.trim(),
                    imageUrl = imageUrl.trim(),
                    linkUrl = linkUrl.trim(),
                    buttonText = if (buttonText.isBlank()) "VIEW" else buttonText.trim(),
                    description = description.trim(),
                    isActive = isActive,
                    displayOrder = displayOrder,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun adminUpdateBanner(
        id: Int,
        title: String,
        imageUrl: String,
        linkUrl: String,
        buttonText: String,
        description: String = "",
        isActive: Boolean = true,
        displayOrder: Int = 0
    ) {
        if (currentUser?.role != "ADMIN") return
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.updateBanner(
                BannerEntity(
                    id = id,
                    title = title.trim(),
                    imageUrl = imageUrl.trim(),
                    linkUrl = linkUrl.trim(),
                    buttonText = if (buttonText.isBlank()) "VIEW" else buttonText.trim(),
                    description = description.trim(),
                    isActive = isActive,
                    displayOrder = displayOrder,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun uploadBannerImage(context: Context, uri: Uri): String {
        return repository.uploadBannerImage(context, uri)
    }

    suspend fun uploadLiveClassThumbnail(context: Context, uri: Uri): String {
        return repository.uploadLiveClassThumbnail(context, uri) // Reusing the same generic R2/Supabase upload logic with correct bucket
    }

    suspend fun uploadStudyWebsiteBanner(context: Context, uri: Uri): String {
        return repository.uploadStudyWebsiteBanner(context, uri)
    }

    suspend fun adminAddStudyWebsite(name: String, imageUrl: String, websiteUrl: String) {
        if (currentUser?.role != "ADMIN") throw Exception("Access denied: Not an Admin")
        val website = StudyWebsiteEntity(
            name = name,
            imageUrl = imageUrl,
            websiteUrl = websiteUrl,
            createdAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        )
        repository.insertStudyWebsite(website)
        repository.syncAllFromRemote()
    }

    suspend fun adminUpdateStudyWebsite(id: String, name: String, imageUrl: String, websiteUrl: String) {
        if (currentUser?.role != "ADMIN") throw Exception("Access denied: Not an Admin")
        val website = StudyWebsiteEntity(
            id = id,
            name = name,
            imageUrl = imageUrl,
            websiteUrl = websiteUrl,
            createdAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        )
        repository.updateStudyWebsite(website)
        repository.syncAllFromRemote()
    }

    suspend fun adminDeleteStudyWebsite(id: String, imageUrl: String) {
        if (currentUser?.role != "ADMIN") throw Exception("Access denied: Not an Admin")
        repository.deleteStudyWebsite(id)
        if (imageUrl.isNotBlank()) {
            try {
                R2SupabaseManager.deleteFileFromSupabaseStorage(getApplication(), imageUrl, "study-websites")
            } catch (e: Exception) {
                android.util.Log.e("AcademyViewModel", "Failed to delete file from storage: ${e.message}")
            }
        }
        repository.syncAllFromRemote()
    }

    fun adminDeleteBanner(id: Int) {
        if (currentUser?.role != "ADMIN") return
        viewModelScope.launch {
            repository.deleteBanner(id)
        }
    }

    // === Live Classes Admin and Student Operations ===
    fun createLiveClassFromUrl(
        youtubeUrl: String,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        if (currentUser?.role != "ADMIN") return
        viewModelScope.launch {
            try {
                val metadata = fetchYouTubeMetadata(youtubeUrl)
                if (metadata.videoId.isBlank()) {
                    onComplete(false, "Invalid YouTube URL or Video ID")
                    return@launch
                }
                val currentDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                val currentTime = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())

                val liveClass = LiveClassEntity(
                    title = metadata.title.ifBlank { "Lakshya Live Class" },
                    description = "Interactive Live Stream on YouTube",
                    subject = "Live Class",
                    chapter = "Lakshya Smart Classroom",
                    teacherName = "Lakshya Academy",
                    thumbnailUrl = metadata.thumbnailUrl,
                    youtubeLiveId = metadata.videoId,
                    youtubeUrl = "https://www.youtube.com/watch?v=${metadata.videoId}",
                    status = metadata.status,
                    scheduledDate = currentDate,
                    scheduledTime = currentTime,
                    isLive = metadata.isLive
                )
                val result = repository.insertLiveClass(liveClass)
                
                if (result.isSuccess) {
                    try {
                        repository.getRemoteLiveClassesDirect()
                    } catch (_: Exception) {}
                    refreshLiveClassesStatus()

                    if (metadata.isLive) {
                        try {
                            repository.insertNotification(
                                NotificationEntity(
                                    title = "🔴 Live Class Started",
                                    message = "${metadata.title}\nTap to Join Now!"
                                )
                            )
                        } catch (_: Exception) {}
                    }
                    onComplete(true, "Live class added successfully")
                } else {
                    val errMsg = result.toFormattedErrorMessage()
                    Log.e("AcademyViewModel", "createLiveClassFromUrl insert failed:\n$errMsg")
                    onComplete(false, errMsg)
                }
            } catch (e: Exception) {
                Log.e("AcademyViewModel", "createLiveClassFromUrl error: ${e.message}", e)
                onComplete(false, "Failed to schedule live class: ${e.message}")
            }
        }
    }

    fun updateLiveClassFromUrl(
        id: Int,
        youtubeUrl: String,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        if (currentUser?.role != "ADMIN") return
        viewModelScope.launch {
            try {
                val metadata = fetchYouTubeMetadata(youtubeUrl)
                if (metadata.videoId.isBlank()) {
                    onComplete(false, "Invalid YouTube URL or Video ID")
                    return@launch
                }
                val currentDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                val currentTime = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())

                val liveClass = LiveClassEntity(
                    id = id,
                    title = metadata.title.ifBlank { "Lakshya Live Class" },
                    description = "Interactive Live Stream on YouTube",
                    subject = "Live Class",
                    chapter = "Lakshya Smart Classroom",
                    teacherName = "Lakshya Academy",
                    thumbnailUrl = metadata.thumbnailUrl,
                    youtubeLiveId = metadata.videoId,
                    youtubeUrl = "https://www.youtube.com/watch?v=${metadata.videoId}",
                    status = metadata.status,
                    scheduledDate = currentDate,
                    scheduledTime = currentTime,
                    isLive = metadata.isLive
                )
                val result = repository.updateLiveClass(liveClass)
                if (result.isSuccess) {
                    try {
                        repository.getRemoteLiveClassesDirect()
                    } catch (_: Exception) {}
                    refreshLiveClassesStatus()
                    onComplete(true, "Live class stream updated!")
                } else {
                    val errMsg = result.toFormattedErrorMessage()
                    Log.e("AcademyViewModel", "updateLiveClassFromUrl update failed:\n$errMsg")
                    onComplete(false, errMsg)
                }
            } catch (e: Exception) {
                Log.e("AcademyViewModel", "updateLiveClassFromUrl error: ${e.message}", e)
                onComplete(false, "Error: ${e.message}")
            }
        }
    }

    fun refreshLiveClassesStatus() {
        viewModelScope.launch {
            try {
                repository.getRemoteLiveClassesDirect()
            } catch (e: Exception) {
                Log.e("AcademyViewModel", "refreshLiveClassesStatus fetch failed: ${e.message}", e)
            }
            val currentClasses = allLiveClasses.value
            currentClasses.forEach { item ->
                if (item.effectiveYoutubeId.isNotBlank()) {
                    try {
                        val metadata = fetchYouTubeMetadata(item.effectiveYoutubeId)
                        if (metadata.status != item.status || (metadata.title.isNotBlank() && metadata.title != "Lakshya Live Class" && metadata.title != item.title)) {
                            val updated = item.copy(
                                title = if (metadata.title.isNotBlank() && metadata.title != "Lakshya Live Class") metadata.title else item.title,
                                thumbnailUrl = if (metadata.thumbnailUrl.isNotBlank()) metadata.thumbnailUrl else item.thumbnailUrl,
                                status = metadata.status,
                                isLive = metadata.isLive
                            )
                            repository.updateLiveClass(updated)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun createLiveClass(
        title: String,
        description: String,
        subject: String,
        chapter: String,
        teacherName: String,
        thumbnailUrl: String,
        youtubeLiveId: String,
        scheduledDate: String,
        scheduledTime: String,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        if (currentUser?.role != "ADMIN") return
        viewModelScope.launch {
            val liveClass = LiveClassEntity(
                title = title.trim(),
                description = description.trim(),
                subject = subject.trim(),
                chapter = chapter.trim(),
                teacherName = teacherName.trim(),
                thumbnailUrl = thumbnailUrl.trim(),
                youtubeLiveId = youtubeLiveId.trim(),
                status = "Scheduled",
                scheduledDate = scheduledDate.trim(),
                scheduledTime = scheduledTime.trim(),
                isLive = false
            )
            val result = repository.insertLiveClass(liveClass)
            if (result.isSuccess) {
                try {
                    repository.getRemoteLiveClassesDirect()
                } catch (_: Exception) {}
                refreshLiveClassesStatus()
                onComplete(true, "Live class added successfully")
            } else {
                onComplete(false, result.toFormattedErrorMessage())
            }
        }
    }

    fun updateLiveClassDetails(
        id: Int,
        title: String,
        description: String,
        subject: String,
        chapter: String,
        teacherName: String,
        thumbnailUrl: String,
        youtubeLiveId: String,
        scheduledDate: String,
        scheduledTime: String,
        status: String,
        recordingUri: String = "",
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        if (currentUser?.role != "ADMIN") return
        viewModelScope.launch {
            val liveClass = LiveClassEntity(
                id = id,
                title = title.trim(),
                description = description.trim(),
                subject = subject.trim(),
                chapter = chapter.trim(),
                teacherName = teacherName.trim(),
                thumbnailUrl = thumbnailUrl.trim(),
                youtubeLiveId = youtubeLiveId.trim(),
                status = status.trim(),
                scheduledDate = scheduledDate.trim(),
                scheduledTime = scheduledTime.trim(),
                isLive = status.trim().equals("Live", ignoreCase = true),
                recordingUri = recordingUri.trim()
            )
            val result = repository.updateLiveClass(liveClass)
            if (result.isSuccess) {
                try {
                    repository.getRemoteLiveClassesDirect()
                } catch (_: Exception) {}
                refreshLiveClassesStatus()
                onComplete(true, "Live class stream updated!")
            } else {
                onComplete(false, result.toFormattedErrorMessage())
            }
        }
    }

    fun startLiveClass(liveClass: LiveClassEntity) {
        if (currentUser?.role != "ADMIN") return
        viewModelScope.launch {
            val updated = liveClass.copy(
                status = "Live",
                isLive = true
            )
            repository.updateLiveClass(updated)
            
            // Send real-time notification to all students
            repository.insertNotification(
                NotificationEntity(
                    title = "🔴 Live Class Started",
                    message = "Teacher: ${liveClass.teacherName}\nSubject: ${liveClass.subject}\nTap to Join"
                )
            )
        }
    }

    fun endLiveClass(liveClass: LiveClassEntity, recordingUri: String = "") {
        if (currentUser?.role != "ADMIN") return
        viewModelScope.launch {
            val updated = liveClass.copy(
                status = "Ended",
                isLive = false,
                recordingUri = recordingUri.trim().ifBlank { "https://www.youtube.com/watch?v=${liveClass.youtubeLiveId}" }
            )
            repository.updateLiveClass(updated)
        }
    }

    fun deleteLiveClass(id: Int) {
        if (currentUser?.role != "ADMIN") return
        viewModelScope.launch {
            val result = repository.deleteLiveClass(id)
            if (result.isSuccess) {
                try {
                    repository.getRemoteLiveClassesDirect()
                } catch (_: Exception) {}
                refreshLiveClassesStatus()
            }
        }
    }

    private suspend fun uploadBatchImage(context: Context, fileUri: android.net.Uri): String {
        android.util.Log.d("AcademyViewModel", "[COVER IMAGE] Starting batch cover image upload. Local URI: $fileUri")
        
        val activeProvider = com.example.service.MediaStorageServiceFactory.getService(context).getStorageType()
        
        // 1. Upload to the active configured storage provider
        val uploadedCustomUrl = kotlin.coroutines.suspendCoroutine<String> { continuation ->
            if (activeProvider == "BACKBLAZE_B2") {
                com.example.api.BackblazeB2Manager.uploadFileOnlyToBackblazeB2(
                    context = context,
                    creds = com.example.api.BackblazeB2Manager.getCredentials(context),
                    fileUri = fileUri,
                    onProgress = {},
                    onSuccess = { url -> continuation.resume(url) },
                    onError = { err -> continuation.resumeWithException(Exception(err)) }
                )
            } else if (activeProvider == "CLOUDFLARE_R2") {
                com.example.api.R2SupabaseManager.uploadFileOnlyToCloudflareR2(
                    context = context,
                    creds = com.example.api.R2SupabaseManager.getCredentials(context),
                    fileUri = fileUri,
                    onProgress = {},
                    onSuccess = { url -> continuation.resume(url) },
                    onError = { err -> continuation.resumeWithException(Exception(err)) }
                )
            } else {
                // Supabase is default
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        val url = com.example.api.R2SupabaseManager.uploadFileToSupabaseStorage(
                            context = context,
                            fileUri = fileUri,
                            bucketName = "videos"
                        )
                        continuation.resume(url)
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                }
            }
        }
        
        // 2. Obtain the final public HTTP URL by resolving the custom scheme
        val publicUrl = com.example.service.MediaStorageServiceFactory.getService(context).resolveMediaUrl(uploadedCustomUrl)
        
        android.util.Log.i("AcademyViewModel", "[COVER IMAGE] Batch cover image successfully uploaded to active storage. Generated Public URL: $publicUrl")
        return publicUrl
    }

    private suspend fun uploadBatchImageWithRetry(context: Context, fileUri: android.net.Uri, maxRetries: Int = 3): String {
        var lastException: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                return uploadBatchImage(context, fileUri)
            } catch (e: Exception) {
                lastException = e
                android.util.Log.w("AcademyViewModel", "[COVER IMAGE] Upload attempt $attempt failed, retrying...", e)
                if (attempt < maxRetries) {
                    kotlinx.coroutines.delay(1000L * attempt)
                }
            }
        }
        throw lastException ?: Exception("Upload failed after $maxRetries attempts")
    }

    private suspend fun uploadModuleFileWithRetry(context: Context, fileUri: android.net.Uri, module: FolderModule, maxRetries: Int = 3): String {
        var lastException: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                return uploadModuleFile(context, fileUri, module)
            } catch (e: Exception) {
                lastException = e
                android.util.Log.w("AcademyViewModel", "[MODULE FILE] Upload attempt $attempt failed, retrying...", e)
                if (attempt < maxRetries) {
                    kotlinx.coroutines.delay(1000L * attempt)
                }
            }
        }
        throw lastException ?: Exception("Module file upload failed after $maxRetries attempts")
    }

    private suspend fun uploadModuleFile(context: Context, fileUri: android.net.Uri, module: FolderModule): String {
        android.util.Log.d("AcademyViewModel", "[MODULE FILE] Starting upload for ${module.titleName}. Local URI: $fileUri")
        
        val folderPath = when (module) {
            FolderModule.SYLLABUS -> "course-syllabus"
            FolderModule.PREVIOUS_PAPERS -> "previous-papers"
            FolderModule.FREE_BOOKS -> "free-books"
        }
        
        val publicUrl = com.example.api.R2SupabaseManager.uploadFileToSupabaseStorage(
            context = context,
            fileUri = fileUri,
            bucketName = "materials",
            folderPath = folderPath
        )
        
        android.util.Log.i("AcademyViewModel", "[MODULE FILE] Upload successfully completed. Generated Public URL: $publicUrl")
        return publicUrl
    }

    // === Folder Management System State & Functions ===

    val syllabusFolders: StateFlow<List<FolderItem>> = repository.allSyllabusFolders
        .map { list -> list.map { it.toFolderItem() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syllabusFiles: StateFlow<List<FileItem>> = repository.allSyllabusFiles
        .map { list -> list.map { it.toFileItem() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val previousPaperFolders: StateFlow<List<FolderItem>> = repository.allPreviousPaperFolders
        .map { list -> list.map { it.toFolderItem() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val previousPaperFiles: StateFlow<List<FileItem>> = repository.allPreviousPaperFiles
        .map { list -> list.map { it.toFileItem() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val freeBookFolders: StateFlow<List<FolderItem>> = repository.allFreeBookFolders
        .map { list -> list.map { it.toFolderItem() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val freeBookFiles: StateFlow<List<FileItem>> = repository.allFreeBookFiles
        .map { list -> list.map { it.toFileItem() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun refreshFolderModule(module: FolderModule) {
        viewModelScope.launch {
            try {
                repository.syncFoldersAndFilesFromSupabase(module)
            } catch (e: Exception) {
                android.util.Log.e("AcademyViewModel", "Failed to refresh ${module.titleName}: ${e.message}")
            }
        }
    }

    fun createFolder(
        module: FolderModule,
        parentId: Long?,
        name: String,
        imageUri: Uri?,
        context: Context,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            var imageUrl = ""
            try {
                if (imageUri != null) {
                    imageUrl = uploadModuleFileWithRetry(context, imageUri, module)
                }
                val newItem = FolderItem(
                    parentId = parentId,
                    name = name.trim(),
                    imageUrl = imageUrl
                )
                val success = repository.insertFolder(module, newItem)
                if (success) {
                    refreshFolderModule(module)
                }
                onComplete(success)
            } catch (e: Exception) {
                android.util.Log.e("AcademyViewModel", "createFolder error: ${e.message}", e)
                if (imageUrl.isNotBlank()) {
                    try {
                        com.example.api.R2SupabaseManager.deleteFileFromSupabaseStorage(context, imageUrl)
                    } catch (rollbackEx: Exception) {
                        android.util.Log.e("AcademyViewModel", "Rollback cover image failed", rollbackEx)
                    }
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Error creating folder: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
                onComplete(false)
            }
        }
    }

    fun updateFolderCoverImage(
        module: FolderModule,
        folder: FolderItem,
        imageUri: Uri,
        context: Context,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            var imageUrl = ""
            try {
                imageUrl = uploadModuleFileWithRetry(context, imageUri, module)
                val updated = folder.copy(imageUrl = imageUrl)
                val success = repository.updateFolder(module, updated)
                if (success) {
                    refreshFolderModule(module)
                }
                onComplete(success)
            } catch (e: Exception) {
                android.util.Log.e("AcademyViewModel", "updateFolderCoverImage error: ${e.message}", e)
                if (imageUrl.isNotBlank()) {
                    try {
                        com.example.api.R2SupabaseManager.deleteFileFromSupabaseStorage(context, imageUrl)
                    } catch (rollbackEx: Exception) {
                        android.util.Log.e("AcademyViewModel", "Rollback cover image failed", rollbackEx)
                    }
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Error updating cover: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
                onComplete(false)
            }
        }
    }

    fun renameFolder(
        module: FolderModule,
        folder: FolderItem,
        newName: String,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val updated = folder.copy(name = newName.trim())
                val success = repository.updateFolder(module, updated)
                if (success) {
                    refreshFolderModule(module)
                }
                onComplete(success)
            } catch (e: Exception) {
                android.util.Log.e("AcademyViewModel", "renameFolder error", e)
                onComplete(false)
            }
        }
    }

    fun moveFolder(
        module: FolderModule,
        folder: FolderItem,
        newParentId: Long?,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val updated = folder.copy(parentId = newParentId)
                val success = repository.updateFolder(module, updated)
                if (success) {
                    refreshFolderModule(module)
                }
                onComplete(success)
            } catch (e: Exception) {
                android.util.Log.e("AcademyViewModel", "moveFolder error", e)
                onComplete(false)
            }
        }
    }

    fun deleteFolder(
        module: FolderModule,
        folderId: Long,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val success = repository.deleteFolder(module, folderId)
                if (success) {
                    refreshFolderModule(module)
                }
                onComplete(success)
            } catch (e: Exception) {
                android.util.Log.e("AcademyViewModel", "deleteFolder error", e)
                onComplete(false)
            }
        }
    }

    fun uploadFilesToFolder(
        module: FolderModule,
        folderId: Long,
        fileUris: List<Uri>,
        context: Context,
        onProgress: (currentIndex: Int, totalCount: Int, fileProgress: Float) -> Unit,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val total = fileUris.size
                var overallSuccess = true

                for ((index, uri) in fileUris.withIndex()) {
                    onProgress(index + 1, total, 0.1f)
                    val mimeType = context.contentResolver.getType(uri) ?: ""
                    val fileType = if (mimeType.contains("image", ignoreCase = true)) "image" else "pdf"
                    
                    val fileName = try {
                        var name = "File_${System.currentTimeMillis()}"
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIdx != -1 && cursor.moveToFirst()) {
                                name = cursor.getString(nameIdx)
                            }
                        }
                        name
                    } catch (e: Exception) {
                        "File_${System.currentTimeMillis()}.${if (fileType == "image") "jpg" else "pdf"}"
                    }

                    val fileSize = try {
                        var sizeBytes = 0L
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                            if (sizeIdx != -1 && cursor.moveToFirst()) {
                                sizeBytes = cursor.getLong(sizeIdx)
                            }
                        }
                        if (sizeBytes > 0) String.format("%.1f MB", sizeBytes / (1024f * 1024f)) else ""
                    } catch (e: Exception) {
                        ""
                    }

                    onProgress(index + 1, total, 0.4f)
                    val uploadedUrl = try {
                        uploadModuleFileWithRetry(context, uri, module)
                    } catch (e: Exception) {
                        overallSuccess = false
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Storage upload failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        }
                        ""
                    }

                    onProgress(index + 1, total, 0.8f)
                    if (uploadedUrl.isNotBlank()) {
                        val fileItem = FileItem(
                            folderId = folderId,
                            fileName = fileName,
                            fileType = fileType,
                            storageUrl = uploadedUrl,
                            fileSize = fileSize
                        )
                        try {
                            val success = repository.insertFile(module, fileItem)
                            if (success) {
                                refreshFolderModule(module)
                            } else {
                                overallSuccess = false
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AcademyViewModel", "Database insert failed for $fileName, rolling back storage upload", e)
                            overallSuccess = false
                            try {
                                com.example.api.R2SupabaseManager.deleteFileFromSupabaseStorage(context, uploadedUrl)
                            } catch (rollbackEx: Exception) {
                                android.util.Log.e("AcademyViewModel", "Rollback failed", rollbackEx)
                            }
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                android.widget.Toast.makeText(context, "DB Insert failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    onProgress(index + 1, total, 1.0f)
                }

                onComplete(overallSuccess)
            } catch (e: Exception) {
                android.util.Log.e("AcademyViewModel", "uploadFilesToFolder error", e)
                onComplete(false)
            }
        }
    }

    fun renameFile(
        module: FolderModule,
        file: FileItem,
        newName: String,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val updated = file.copy(fileName = newName.trim())
                val success = repository.updateFile(module, updated)
                if (success) {
                    refreshFolderModule(module)
                }
                onComplete(success)
            } catch (e: Exception) {
                android.util.Log.e("AcademyViewModel", "renameFile error", e)
                onComplete(false)
            }
        }
    }

    fun moveFile(
        module: FolderModule,
        file: FileItem,
        newFolderId: Long,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val updated = file.copy(folderId = newFolderId)
                val success = repository.updateFile(module, updated)
                if (success) {
                    refreshFolderModule(module)
                }
                onComplete(success)
            } catch (e: Exception) {
                android.util.Log.e("AcademyViewModel", "moveFile error", e)
                onComplete(false)
            }
        }
    }

    fun replaceFile(
        module: FolderModule,
        file: FileItem,
        newUri: Uri,
        context: Context,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            var uploadedUrl = ""
            try {
                uploadedUrl = uploadModuleFileWithRetry(context, newUri, module)
                val updated = file.copy(storageUrl = uploadedUrl)
                val success = repository.updateFile(module, updated)
                if (success) {
                    refreshFolderModule(module)
                }
                onComplete(success)
            } catch (e: Exception) {
                android.util.Log.e("AcademyViewModel", "replaceFile error", e)
                if (uploadedUrl.isNotBlank()) {
                    try {
                        com.example.api.R2SupabaseManager.deleteFileFromSupabaseStorage(context, uploadedUrl)
                    } catch (rollbackEx: Exception) {
                        android.util.Log.e("AcademyViewModel", "Rollback failed", rollbackEx)
                    }
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Replace file failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
                onComplete(false)
            }
        }
    }

    fun deleteFile(
        module: FolderModule,
        fileId: Long,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val success = repository.deleteFile(module, fileId)
                if (success) {
                    refreshFolderModule(module)
                }
                onComplete(success)
            } catch (e: Exception) {
                android.util.Log.e("AcademyViewModel", "deleteFile error", e)
                onComplete(false)
            }
        }
    }

    // === Community Popup State & Functions ===
    var communityPopupConfig by mutableStateOf<CommunityPopupEntity>(
        repository.getCommunityPopupLocal()
    )
        private set

    fun fetchCommunityPopup() {
        viewModelScope.launch {
            val popup = repository.getCommunityPopupFromSupabase()
            if (popup != null) {
                communityPopupConfig = popup
            }
        }
    }

    suspend fun updateCommunityPopupConfig(config: CommunityPopupEntity): Boolean {
        communityPopupConfig = config
        return repository.saveCommunityPopup(config)
    }
}
