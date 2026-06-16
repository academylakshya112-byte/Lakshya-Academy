package com.example.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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
    var secondsRemaining: Int,
    var isSubmitted: Boolean = false,
    val testScore: TestScoreEntity? = null
)

class AcademyViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AcademyDatabase.getDatabase(application)
    private val repository = AcademyRepository(database.academyDao())
    private val prefs = application.getSharedPreferences("lakshya_app_prefs", android.content.Context.MODE_PRIVATE)

    // --- Authentication State ---
    var currentUser by mutableStateOf<AppUser?>(null)
        private set

    fun updateProfile(newName: String, newMobile: String, newPhotoUri: String, newEmail: String) {
        val user = currentUser ?: return
        
        // Remove old user record if email changed
        if (user.email != newEmail) {
            registeredUsers.remove(user.email)
        }
        
        val updatedUser = user.copy(name = newName, mobile = newMobile, photoUri = newPhotoUri, email = newEmail)
        currentUser = updatedUser
        registeredUsers[newEmail] = updatedUser
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

    // Simple user registry database simulation (in-memory persistent during app run)
    private val registeredUsers = mutableStateMapOf<String, AppUser>().apply {
        put("student@lakshya.com", AppUser("student@lakshya.com", "Anand Yadav", "STUDENT", "📚"))
        put("admin@lakshya.com", AppUser("admin@lakshya.com", "Director Sir (Ghazipur)", "ADMIN", "🎖️"))
        put("academylakshya112@gmail.com", AppUser("academylakshya112@gmail.com", "Director Sir (LAKSHYA)", "ADMIN", "🎖️"))
    }

    // --- Search & Filter States ---
    var searchQuery by mutableStateOf("")
    var selectedCategory by mutableStateOf("All")
    var darkThemeEnabled by mutableStateOf(false)
    
    // AI Processing Status
    var aiProcessingStatus by mutableStateOf<String?>(null)
        private set

    // --- State flows from Repository ---
    val allCourses: StateFlow<List<CourseEntity>> = repository.allCourses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBanners: StateFlow<List<BannerEntity>> = repository.allBanners
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
                        registeredUsers[email] = AppUser(email, name, role, avatar)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Load logged in active user session
        val savedEmail = prefs.getString("logged_in_user_email", null)
        val savedName = prefs.getString("logged_in_user_name", null)
        val savedRole = prefs.getString("logged_in_user_role", null)
        val savedAvatar = prefs.getString("logged_in_user_avatar", "🎓") ?: "🎓"
        if (savedEmail != null && savedName != null && savedRole != null) {
            currentUser = AppUser(savedEmail, savedName, savedRole, savedAvatar)
        }

        checkInternetStatus()
        // First startup seed and auto-fill lists
        viewModelScope.launch {
            seedDatabaseIfEmpty()
            observeMaterials()
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
                    imageUrl = "https://picsum.photos/seed/nda_shaurya/600/350"
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
                    imageUrl = "https://picsum.photos/seed/rrb_tejas/600/350"
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
                    pdfName = "Electrochemistry Formulas & Nernst Equation Practice Set"
                )
            )
            repository.insertLesson(
                LessonEntity(
                    courseId = c12Id,
                    chapterName = "Physics - Optics",
                    title = "Wave Optics - Huygen's Principle & Interference",
                    videoUrl = "https://www.w3schools.com/html/mov_bbb.mp4",
                    pdfUrl = "Class12_Physics_Optics.pdf",
                    pdfName = "Huygen's Principle Board Revision Guide"
                )
            )

            // 3. Seed test items
            val t1Id = repository.insertTest(
                TestEntity(
                    title = "UPSC GS Prelims Syllabus Test - Polity",
                    type = "Weekly Test",
                    durationMinutes = 5,
                    hasNegativeMarking = true,
                    marksPerCorrect = 2,
                    marksPerWrong = -0.66f
                )
            )
            val t2Id = repository.insertTest(
                TestEntity(
                    title = "UP Police Constable Full Mock Test 1",
                    type = "Mock Test",
                    durationMinutes = 10,
                    hasNegativeMarking = true,
                    marksPerCorrect = 2,
                    marksPerWrong = -0.5f
                )
            )

            // Seed questions for Test 1 (Polity)
            repository.insertQuestion(
                QuestionEntity(
                    testId = t1Id,
                    questionText = "Which Article of the Indian Constitution outlines the 'Basic Structure' doctrine implicitly?",
                    optionA = "Article 368",
                    optionB = "Article 13",
                    optionC = "Article 21",
                    optionD = "None of the above (it is a judicial innovated doctrine)",
                    correctIndex = 3
                )
            )
            repository.insertQuestion(
                QuestionEntity(
                    testId = t1Id,
                    questionText = "The Directive Principles of State Policy (DPSP) are borrowed from which country's Constitution?",
                    optionA = "Ireland",
                    optionB = "USA",
                    optionC = "USSR",
                    optionD = "Australia",
                    correctIndex = 0
                )
            )
            repository.insertQuestion(
                QuestionEntity(
                    testId = t1Id,
                    questionText = "In which landmark case did the Supreme Court establish the Basic Structure of the Constitution?",
                    optionA = "Golaknath vs State of Punjab",
                    optionB = "Kesavananda Bharati vs State of Kerala",
                    optionC = "Minerva Mills vs Union of India",
                    optionD = "Maneka Gandhi vs Union of India",
                    correctIndex = 1
                )
            )

            // Seed questions for Test 2 (UP Police)
            repository.insertQuestion(
                QuestionEntity(
                    testId = t2Id,
                    questionText = "Which city is known as the 'Ghazichand Kila / Gateway of Eastern UP' and has historical association with Lord Cornwallis' tomb?",
                    optionA = "Ghazipur",
                    optionB = "Varanasi",
                    optionC = "Ballia",
                    optionD = "Gorakhpur",
                    correctIndex = 0
                )
            )
            repository.insertQuestion(
                QuestionEntity(
                    testId = t2Id,
                    questionText = "Among these, who is the writer of Bihar's famous Hindi novel 'Maila Anchal'?",
                    optionA = "Premchand",
                    optionB = "Phanishwar Nath Renu",
                    optionC = "Ramdhari Singh Dinkar",
                    optionD = "Mahadevi Verma",
                    correctIndex = 1
                )
            )

            // --- SCHOOL CLASSES MOCK EXAMS (6th to 12th) ---
            // Seed a test for Class 6
            val c6TestId = repository.insertTest(
                TestEntity(
                    title = "Class 6 Math & Science Basic Booster Mock Test",
                    type = "Mock Test",
                    durationMinutes = 15,
                    hasNegativeMarking = false,
                    marksPerCorrect = 2,
                    marksPerWrong = 0.0f
                )
            )
            repository.insertQuestion(
                QuestionEntity(
                    testId = c6TestId,
                    questionText = "What is the smallest prime number? / सबसे छोटी अभाज्य संख्या कौन सी है?",
                    optionA = "1", optionB = "2", optionC = "3", optionD = "0", correctIndex = 1
                )
            )
            repository.insertQuestion(
                QuestionEntity(
                    testId = c6TestId,
                    questionText = "Which gas do we breath in to survive? / जीवित रहने के लिए हम कौन सी गैस सांस में लेते हैं?",
                    optionA = "Nitrogen / नाइट्रोजन", optionB = "Carbon dioxide / कार्बन डाइऑक्साइड", optionC = "Oxygen / ऑक्सीजन", optionD = "Hydrogen / हाइड्रोजन", correctIndex = 2
                )
            )

            // Seed a test for Class 7
            val c7TestId = repository.insertTest(
                TestEntity(
                    title = "Class 7 Social Science & Hindi Grammar Mock Test",
                    type = "Mock Test",
                    durationMinutes = 20,
                    hasNegativeMarking = false,
                    marksPerCorrect = 2,
                    marksPerWrong = 0.0f
                )
            )
            repository.insertQuestion(
                QuestionEntity(
                    testId = c7TestId,
                    questionText = "Who was the founder of Mughal Empire in India? / भारत में मुगल साम्राज्य का संस्थापक कौन था?",
                    optionA = "Akbar / अकबर", optionB = "Babur / बाबर", optionC = "Humayun / हुमायूँ", optionD = "Sher Shah Suri / शेर शाह सूरी", correctIndex = 1
                )
            )

            // Seed a test for Class 8
            val c8TestId = repository.insertTest(
                TestEntity(
                    title = "Class 8 Science (Force & Friction) Concepts Mock Exam",
                    type = "Mock Test",
                    durationMinutes = 15,
                    hasNegativeMarking = true,
                    marksPerCorrect = 4,
                    marksPerWrong = -1f
                )
            )
            repository.insertQuestion(
                QuestionEntity(
                    testId = c8TestId,
                    questionText = "Which force opposes relative motion between two surfaces? / दो सतहों के बीच सापेक्ष गति का विरोध कौन सा बल करता है?",
                    optionA = "Gravitational force / गुरुत्वाकर्षण बल", optionB = "Magnetic force / चुंबकीय बल", optionC = "Frictional force / घर्षण बल", optionD = "Electrostatic force / स्थिरवैद्युत बल", correctIndex = 2
                )
            )

            // Seed a test for Class 9
            val c9TestId = repository.insertTest(
                TestEntity(
                    title = "Class 9 Science laws of Motion Mock Test",
                    type = "Mock Test",
                    durationMinutes = 20,
                    hasNegativeMarking = true,
                    marksPerCorrect = 2,
                    marksPerWrong = -0.5f
                )
            )
            repository.insertQuestion(
                QuestionEntity(
                    testId = c9TestId,
                    questionText = "Who gave the three laws of motion? / गति के तीन नियम किसने दिए थे?",
                    optionA = "Galileo / गैलीलियो", optionB = "Newton / न्यूटन", optionC = "Einstein / आइंस्टीन", optionD = "Kepler / केपलर", correctIndex = 1
                )
            )

            // Seed a test for Class 11
            val c11TestId = repository.insertTest(
                TestEntity(
                    title = "Class 11 Science (Physics Kinematics) Revision Mock Test",
                    type = "Mock Test",
                    durationMinutes = 30,
                    hasNegativeMarking = true,
                    marksPerCorrect = 4,
                    marksPerWrong = -1.0f
                )
            )
            repository.insertQuestion(
                QuestionEntity(
                    testId = c11TestId,
                    questionText = "What is the acceleration due to gravity on Earth approximately? / पृथ्वी पर गुरुत्वाकर्षण के कारण त्वरण लगभग कितना होता है?",
                    optionA = "9.8 m/s²", optionB = "10 m/s", optionC = "9.8 cm/s²", optionD = "32 m/s²", correctIndex = 0
                )
            )

            // Seed a test for Class 12
            val c12TestId = repository.insertTest(
                TestEntity(
                    title = "Class 12 Boards Electrochemistry & Calculus Mock Test 1",
                    type = "Mock Test",
                    durationMinutes = 30,
                    hasNegativeMarking = true,
                    marksPerCorrect = 4,
                    marksPerWrong = -1.0f
                )
            )
            repository.insertQuestion(
                QuestionEntity(
                    testId = c12TestId,
                    questionText = "What is the derivative of sin(x) with respect to x? / x के सापेक्ष sin(x) का अवकलज (derivative) क्या है?",
                    optionA = "cos(x)", optionB = "-cos(x)", optionC = "sin(x)", optionD = "tan(x)", correctIndex = 0
                )
            )

            // === DYNAMIC SEEDING OF 10 MOCK TESTS WITH 50 BILINGUAL QUESTIONS EACH FOR CLASS 10 ===
            data class TempQuestion(
                val text: String,
                val a: String,
                val b: String,
                val c: String,
                val d: String,
                val correct: Int
            )

            val qList = listOf(
                TempQuestion(
                    "Which organelle is known as the powerhouse of the cell? / कोशिका का शक्तिगृह (पावरहाउस) किसे कहा जाता है?",
                    "Mitochondria / माइटोकॉन्ड्रिया", "Ribosome / राइबोसोम", "Nucleus / केंद्रक", "Golgi Body / गोल्गी काय", 0
                ),
                TempQuestion(
                    "What is the chemical formula of Water? / पानी का रासायनिक सूत्र क्या है?",
                    "CO2", "H2O", "O2", "NaCl", 1
                ),
                TempQuestion(
                    "Which gas is most abundant in Earth's atmosphere? / पृथ्वी के वायुमंडल में कौन सी गैस सबसे प्रचुर मात्रा में है?",
                    "Oxygen / ऑक्सीजन", "Hydrogen / हाइड्रोजन", "Nitrogen / नाइट्रोजन", "Carbon Dioxide / कार्बन डाइऑक्साइड", 2
                ),
                TempQuestion(
                    "What is the speed of light in a vacuum? / निर्वात में प्रकाश की गति क्या है?",
                    "3 * 10^8 m/s", "3 * 10^6 m/s", "150,000 m/s", "300,000 km/h", 0
                ),
                TempQuestion(
                    "What is the valency of Carbon? / कार्बन की संयोजकता कितनी होती है?",
                    "2", "3", "4", "6", 2
                ),
                TempQuestion(
                    "Which acid is naturally present in lemon? / नींबू में प्राकृतिक रूप से कौन सा अम्ल होता है?",
                    "Acetic Acid / एसिटिक अम्ल", "Citric Acid / सिट्रिक अम्ल", "Tartaric Acid / टार्टरिक अम्ल", "Lactic Acid / लैक्टिक अम्ल", 1
                ),
                TempQuestion(
                    "What is the pH value of pure water? / शुद्ध पानी का pH मान कितना होता है?",
                    "5", "7", "9", "12", 1
                ),
                TempQuestion(
                    "Which planet is known as the Red Planet? / किस ग्रह को लाल ग्रह के नाम से जाना जाता है?",
                    "Venus / शुक्र", "Mars / मंगल", "Jupiter / बृहस्पति", "Saturn / शनि", 1
                ),
                TempQuestion(
                    "Who formulated the universal law of gravity? / गुरुत्वाकर्षण के सार्वभौमिक नियम का प्रतिपादन किसने किया था?",
                    "Albert Einstein / अल्बर्ट आइंस्टीन", "Isaac Newton / आइजैक न्यूटन", "Galileo Galilei / गैलीलियो गैलीली", "Nikola Tesla / निकोला टेस्ला", 1
                ),
                TempQuestion(
                    "What is the SI unit of electric current? / विद्युत धारा का SI मात्रक क्या है?",
                    "Volt / वोल्ट", "Ampere / एम्पियर", "Ohm / ओम", "Watt / वाट", 1
                ),
                TempQuestion(
                    "Which animal is called the ship of the desert? / किस जानवर को रेगिस्तान का जहाज कहा जाता है?",
                    "Horse / घोड़ा", "Camel / ऊँट", "Elephant / हाथी", "Donkey / गधा", 1
                ),
                TempQuestion(
                    "\"Swaraj is my birthright\" Who raised this slogan? / \"स्वराज मेरा जन्मसिद्ध अधिकार है\" यह नारा किसने दिया था?",
                    "Lala Lajpat Rai / लाला लाजपत राय", "Bal Gangadhar Tilak / बाल गंगाधर तिलक", "Subhash Chandra Bose / सुभाष चंद्र बोस", "Mahatma Gandhi / महात्मा गांधी", 1
                ),
                TempQuestion(
                    "Who is recognized as the father of the Indian Constitution? / भारतीय संविधान के निर्माता या जनक किसे माना जाता है?",
                    "Dr. B.R. Ambedkar / डॉ. बी.आर. अम्बेडकर", "Mahatma Gandhi / महात्मा गांधी", "Jawaharlal Nehru / जवाहरलाल नेहरू", "Dr. Rajendra Prasad / डॉ. राजेंद्र प्रसाद", 0
                ),
                TempQuestion(
                    "What is the official capital of India? / भारत की आधिकारिक राजधानी क्या है?",
                    "Mumbai / मुंबई", "Kolkata / कोलकाता", "New Delhi / नई दिल्ली", "Chennai / चेन्नई", 2
                ),
                TempQuestion(
                    "Which is the largest and deepest ocean on Earth? / पृथ्वी का सबसे बड़ा और सबसे गहरा महासागर कौन सा है?",
                    "Atlantic Ocean / अटलांटिक महासागर", "Indian Ocean / हिंद महासागर", "Pacific Ocean / प्रशांत महासागर", "Arctic Ocean / आर्कटिक महासागर", 2
                ),
                TempQuestion(
                    "How many bones are there in adult human skeleton? / एक वयस्क मानव कंकाल में कितनी हड्डियाँ होती हैं?",
                    "150", "206", "300", "208", 1
                ),
                TempQuestion(
                    "Which vitamin is synthesized in skin using sunlight? / धूप की मदद से त्वचा में कौन सा विटामिन संश्लेषित होता है?",
                    "Vitamin A / विटामिन ए", "Vitamin B12 / विटामिन बी12", "Vitamin C / विटामिन सी", "Vitamin D / विटामिन डी", 3
                ),
                TempQuestion(
                    "Who served as the first Prime Minister of India? / भारत के प्रथम प्रधानमंत्री कौन थे?",
                    "Jawaharlal Nehru / जवाहरलाल नेहरू", "Sardar Patel / सरदार पटेल", "Lal Bahadur Shastri / लाल बहादुर शास्त्री", "Dr. Rajendra Prasad / डॉ. राजेंद्र प्रसाद", 0
                ),
                TempQuestion(
                    "Which is the highest mountain peak in the world? / विश्व की सबसे ऊंची पर्वत चोटी कौन सी है?",
                    "K2 / के2", "Mount Everest / माउंट एवरेस्ट", "Kanchenjunga / कंचनजंगा", "Lhotse / ल्होत्से", 1
                ),
                TempQuestion(
                    "Which Indian state is famously called 'Spices Garden of India'? / किस भारतीय राज्य को 'मसालों का बगीचा' कहा जाता है?",
                    "Karnataka / कर्नाटक", "Andhra Pradesh / आंध्र प्रदेश", "Tamil Nadu / तमिलनाडु", "Kerala / केरल", 3
                ),
                TempQuestion(
                    "What is the national animal of India? / भारत का राष्ट्रीय पशु कौन सा है?",
                    "Lion / शेर", "Bengal Tiger / बंगाल टाइगर", "Leopard / तेंदुआ", "Elephant / हाथी", 1
                ),
                TempQuestion(
                    "In which historic year did India attain independence? / भारत को किस ऐतिहासिक वर्ष में स्वतंत्रता प्राप्त हुई थी?",
                    "1935", "1942", "1947", "1950", 2
                ),
                TempQuestion(
                    "Who composed the national anthem 'Jana Gana Mana'? / राष्ट्रीय गान 'जन गण मन' की रचना किसने की थी?",
                    "Bankim Chandra Chattopadhyay / बंकिम चंद्र चट्टोपाध्याय", "Rabindranath Tagore / रवींद्रनाथ टैगोर", "Sarojini Naidu / सरोजिनी नायडू", "Mahatma Gandhi / महात्मा गांधी", 1
                ),
                TempQuestion(
                    "Which is the smallest Indian state by geographical area? / भौगोलिक क्षेत्रफल के आधार पर भारत का सबसे छोटा राज्य कौन सा है?",
                    "Sikkim / सिक्किम", "Goa / गोवा", "Tripura / त्रिपुरा", "Mizoram / मिजोरम", 1
                ),
                TempQuestion(
                    "What is the mathematical formula for area of a circle? / वृत्त के क्षेत्रफल का गणितीय सूत्र क्या है?",
                    "2*pi*r", "pi * r^2", "pi * r^3", "2*pi*r^2", 1
                ),
                TempQuestion(
                    "Who is revered as the 'Missile Man of India'? / किसे भारत के 'मिसाइल मैन' के रूप में जाना जाता है?",
                    "Dr. Homi Bhabha / डॉ. होमी भाभा", "Dr. A.P.J. Abdul Kalam / डॉ. ए.पी.जे. अब्दुल कलाम", "Dr. Vikram Sarabhai / डॉ. विक्रम साराभाई", "Satish Dhawan / सतीश धवन", 1
                ),
                TempQuestion(
                    "Which endocrine gland is commonly known as the master gland? / किस अंतःस्रावी ग्रंथि को 'मास्टर ग्रंथि' कहा जाता है?",
                    "Thyroid / थायराइड", "Pituitary / पीयूष ग्रंथि", "Adrenal / एड्रेनल", "Pancreas / अग्न्याशय", 1
                ),
                TempQuestion(
                    "Which non-metal is liquid at room temperature? / कौन सी अधातु कमरे के तापमान पर तरल होती है?",
                    "Phosphorus / फास्फोरस", "Carbon / कार्बन", "Helium / हीलियम", "Bromine / ब्रोमीन", 3
                ),
                TempQuestion(
                    "Which pigment is responsible for green color in leaves? / पत्तियों में हरे रंग के लिए कौन सा वर्णक जिम्मेदार होता है?",
                    "Chlorophyll / क्लोरोफिल", "Carotenoids / कैरोटीनॉयड", "Hemoglobin / हीमोग्लोबिन", "Melanin / मेलेनिन", 0
                ),
                TempQuestion(
                    "Who invented the first practical voice telephone? / पहले व्यावहारिक टेलीफोन का आविष्कार किसने किया था?",
                    "Thomas Edison / थॉमस एडिसन", "Alexander Graham Bell / अलेक्जेंडर ग्राहम बेल", "Guglielmo Marconi / गुग्लिएल्मो मार्कोनी", "Benjamin Franklin / बेंजामिन फ्रैंकलिन", 1
                ),
                TempQuestion(
                    "Which hot desert is known as the largest in the world? / किस गर्म मरुस्थल को विश्व का सबसे बड़ा मरुस्थल माना जाता है?",
                    "Gobi Desert / गोबी मरुस्थल", "Thar Desert / थार मरुस्थल", "Sahara Desert / सहारा मरुस्थल", "Kalahari Desert / कालाहारी मरुस्थल", 2
                ),
                TempQuestion(
                    "What is the approx value of Archimedes constant Pi (π)? / आर्किमिडीज नियतांक पाई (π) का अनुमानित मान क्या है?",
                    "2.14", "3.14159", "1.414", "1.732", 1
                ),
                TempQuestion(
                    "What is the total sum of interior angles in any triangle? / किसी भी त्रिभुज के आंतरिक कोणों का कुल योग कितना होता है?",
                    "90 degrees / 90 अंश", "180 degrees / 180 अंश", "360 degrees / 360 अंश", "270 degrees / 270 अंश", 1
                ),
                TempQuestion(
                    "Which star is structurally closest to Earth? / कौन सा तारा पृथ्वी के सबसे निकट स्थित है?",
                    "Sirius / सीरियस", "Alpha Centauri / अल्फा सेंटौरी", "Proxima Centauri / प्रोक्सिमा सेंटौरी", "The Sun / सूर्य", 3
                ),
                TempQuestion(
                    "Who was chosen as the first President of independent India? / स्वतंत्र भारत के प्रथम राष्ट्रपति के रूप में किसे चुना गया था?",
                    "Dr. Rajendra Prasad / डॉ. राजेंद्र प्रसाद", "Dr. S. Radhakrishnan / डॉ. एस. राधाकृष्णन", "Jawaharlal Nehru / जवाहरलाल नेहरू", "Sardar Patel / सरदार पटेल", 0
                ),
                TempQuestion(
                    "In which ancient city is the beautiful Taj Mahal monument located? / प्रसिद्ध ऐतिहासिक स्मारक ताजमहल किस शहर में स्थित है?",
                    "Delhi / दिल्ली", "Jaipur / जयपुर", "Agra / आगरा", "Lucknow / लखनऊ", 2
                ),
                TempQuestion(
                    "Which disease is caused directly by Vitamin C deficiency? / विटामिन सी की कमी (कमी) से कौन सा रोग होता है?",
                    "Rickets / सूखा रोग (रिकेट्स)", "Beriberi / बेरीबेरी", "Scurvy / स्कर्वी", "Night Blindness / रतौंधी", 2
                ),
                TempQuestion(
                    "What is the boiling point of pure water under standard conditions? / मानक परिस्थितियों में पानी का क्वथनांक सेल्सियस में कितना होता है?",
                    "50°C", "100°C", "80°C", "0°C", 1
                ),
                TempQuestion(
                    "Name the longest flowing river on our planet Earth. / हमारी पृथ्वी पर सबसे लंबी नदी का नाम क्या है?",
                    "Amazon River / अमेज़न नदी", "Nile River / नील नदी", "Ganga River / गंगा नदी", "Yangtze River / यांग्त्ज़ी नदी", 1
                ),
                TempQuestion(
                    "Which planet is designated as the largest in our solar system? / हमारे सौरमंडल का सबसे बड़ा ग्रह किसे नामित किया गया है?",
                    "Earth / पृथ्वी", "Jupiter / बृहस्पति", "Saturn / शनि", "Mars / मंगल", 1
                )
            )

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

        // Seed default banners if empty
        val bannerCheck = repository.allBanners.first()
if (!bannerCheck.any { it.title.contains("Facebook") }) {
             repository.insertBanner(
                BannerEntity(
                    title = "Join Our Facebook Community! 👥",
                    imageUrl = "https://images.unsplash.com/photo-1543269865-cbf427effbad?w=600&auto=format&fit=crop&q=60",
                    linkUrl = "https://www.facebook.com/share/1Ld9zB8Khi/",
                    buttonText = "FOLLOW PAGE",
                    description = "Stay updated with announcements, class schedules and community discussions on our official Facebook Page."
                )
            )
        }
        if (!bannerCheck.any { it.title.contains("Lakshya Batch") }) {
             repository.insertBanner(
                BannerEntity(
                    title = "लक्ष्य बैच (Lakshya Batch) 2024-25 - YouTube पर पहली बार FREE!",
                    imageUrl = "android.resource://com.aistudio.lakshya_academy.gzkvpm/drawable/lakshya_batch_banner_1781437391844",
                    linkUrl = "COURSES",
                    buttonText = "Watch Now",
                    description = "Features: Live classes, notes, test series, 100% preparation by Pankaj sir, Kamlesh sir, Dushyant sir."
                )
            )
        }
        if (bannerCheck.isEmpty()) {
            repository.insertBanner(
                BannerEntity(
                    title = "Follow Our Instagram for Daily GK Reels! 📲",
                    imageUrl = "https://images.unsplash.com/photo-1611262588024-d12430b98920?w=600&auto=format&fit=crop&q=60",
                    linkUrl = "https://www.instagram.com/lakshya_academy_sirgitha_gzpr?igsh=MXU5eHVicWRhNmgwag==",
                    buttonText = "FOLLOW US",
                    description = "Get short tricks, current affairs quiz and exam notification reels directly on Instagram!"
                )
            )
            repository.insertBanner(
                BannerEntity(
                    title = "Join Official Telegram Study Channel! 💎",
                    imageUrl = "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=600&auto=format&fit=crop&q=60",
                    linkUrl = "https://t.me/lakshya_academy",
                    buttonText = "JOIN NOW",
                    description = "Download free PDFs, class notes, schedules & interactive discussion worksheets instantly."
                )
            )
                        repository.insertBanner(
                BannerEntity(
                    title = "लक्ष्य बैच (Lakshya Batch) 2024-25 - YouTube पर पहली बार FREE!",
                    imageUrl = "android.resource://com.aistudio.lakshya_academy.gzkvpm/drawable/lakshya_batch_banner_1781437391844",
                    linkUrl = "COURSES",
                    buttonText = "Watch Now",
                    description = "Features: Live classes, notes, test series, 100% preparation by Pankaj sir, Kamlesh sir, Dushyant sir."
                )
            )
            repository.insertBanner(
                BannerEntity(
                    title = "Lakshya All-Subject Special Batch Starting! 🔴",
                    imageUrl = "https://images.unsplash.com/photo-1434030216411-0b793f4b4173?w=600&auto=format&fit=crop&q=60",
                    linkUrl = "COURSES",
                    buttonText = "ENROLL",
                    description = "A new high-yield mock-test batch containing All-Subject lessons starts this Monday. Secure your rank now!"
                )
            )
        }
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
            if (role == "ADMIN" && formattedEmail != "admin@lakshya.com" && formattedEmail != "academylakshya112@gmail.com") {
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
                prefs.edit().putString("reg_$formattedEmail", "${finalProfile.name}|${finalProfile.role}|${finalProfile.avatarEmoji}").apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            currentUser = finalProfile
            
            // Save user login session details
            try {
                prefs.edit()
                    .putString("logged_in_user_email", finalProfile.email)
                    .putString("logged_in_user_name", finalProfile.name)
                    .putString("logged_in_user_role", finalProfile.role)
                    .putString("logged_in_user_avatar", finalProfile.avatarEmoji)
                    .apply()
            } catch (e: Exception) {
                e.printStackTrace()
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

    // --- Live Class Mock Simulation ---
    fun joinLiveClass() {
        liveClassRoomActive = true
        liveViewerCount = (120..180).random()
        liveChatMessageList.clear()
        
        // Populate chat with realistic stream of exam chats
        viewModelScope.launch {
            val names = listOf("Rahul Gaur", "Prachi Singh", "Deepak Bind", "Shivani Yadav", "Amit Pal", "Alok Patel")
            val chats = listOf(
                "Good evening sir! Very excited for Polity today.",
                "Sir, in kesavananda case, what was the judges ratio?",
                "Ghazipur branch notes are premium. Clear audio!",
                "Amazing session! Thank you sir. Please increase weekly PDF lectures.",
                "Is UP police test series live inside app dashboard?",
                "Yes Rahul, we can click Test Series to take the paper now."
            )
            
            for (i in 0 until 5) {
                if (!liveClassRoomActive) break
                delay(800)
                liveChatMessageList[System.nanoTime()] = Pair(names.random(), chats.random())
            }
        }
    }

    fun sendLiveMessage(txt: String) {
        val user = currentUser ?: return
        if (txt.isBlank()) return
        liveChatMessageList[System.nanoTime()] = Pair(user.name, txt.trim())
    }

    fun leaveLiveClass() {
        liveClassRoomActive = false
    }

    // --- ADMIN MODULE ACTIONS ---
    fun adminAddNewCourse(title: String, category: String, subject: String, desc: String, isFree: Boolean, price: Double, imageUrl: String = "") {
        if (currentUser?.role != "ADMIN") return
        if (title.isBlank() || subject.isBlank() || desc.isBlank()) return
        viewModelScope.launch {
            repository.insertCourse(
                CourseEntity(
                    title = title.trim(),
                    category = category,
                    subject = subject,
                    description = desc.trim(),
                    isFree = isFree,
                    price = if (isFree) 0.0 else price,
                    totalLessons = 0,
                    imageUrl = imageUrl.trim()
                )
            )
            repository.insertNotification(
                NotificationEntity(
                    title = "New Course Added! 🎓",
                    message = "Lakshya Academy just launched '${title}' online batch! Enroll now."
                )
            )
        }
    }

    fun adminDeleteCourse(id: Int) {
        if (currentUser?.role != "ADMIN") return
        viewModelScope.launch {
            repository.deleteCourse(id)
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
            repository.insertLesson(
                LessonEntity(
                    courseId = courseId,
                    chapterName = chapter.trim(),
                    folder = folder.trim(),
                    title = title.trim(),
                    videoUrl = if (videoLink.isBlank()) "https://www.w3schools.com/html/mov_bbb.mp4" else videoLink.trim(),
                    pdfUrl = if (pdfLink.isBlank()) "Class_Handout.pdf" else pdfLink.trim(),
                    pdfName = if (pdfName.isBlank()) "Study notes compilation" else pdfName.trim(),
                    pdfContent = pdfContent.trim(),
                    fileSize = if (fileSize.isBlank()) "2.5 MB" else fileSize.trim(),
                    thumbnailUrl = thumbnailUrl.trim()
                )
            )
            val currentCourse = allCourses.value.find { it.id == courseId }
            if (currentCourse != null) {
                repository.updateCourse(currentCourse.copy(totalLessons = currentCourse.totalLessons + 1))
            }
        }
    }

    // AI Mock Test Generator removed as requested (replaced by new Module 3).

    fun generateWeeklyMockTests() {
        viewModelScope.launch {
            val user = currentUser ?: return@launch
            val existingTests = repository.allTests.first()
            val classesToGenerate = listOf("Class 5", "Class 6", "Class 7", "Class 8", "Class 9", "Class 10", "Class 11", "Class 12")
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
                        val qTxt = if (isMath) {
                            val a = (11..99).random()
                            val b = (11..99).random()
                            "What is $a + $b ? / $a और $b का योग क्या है?"
                        } else {
                            val subject = subjects.random()
                            "$cls - $subject Question $i? Choose correct missing fact. / $cls - $subject का विश्लेषणात्मक प्रश्न $i?"
                        }
                        
                        repository.insertQuestion(
                            QuestionEntity(
                                testId = tId,
                                questionText = qTxt,
                                optionA = if (isMath) "${(22..198).random()}" else "Statement A is correct",
                                optionB = if (isMath) "${(22..198).random()}" else "Statement B is correct",
                                optionC = if (isMath) "${(22..198).random()}" else "Both A and B are wrong",
                                optionD = if (isMath) "${(22..198).random()}" else "None of the above",
                                correctIndex = (0..3).random()
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

    fun adminDeleteTest(id: Int) {
        if (currentUser?.role != "ADMIN") return
        viewModelScope.launch {
            repository.deleteTest(id)
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

    fun adminAddBanner(title: String, imageUrl: String, linkUrl: String, buttonText: String, description: String = "") {
        if (currentUser?.role != "ADMIN") return
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.insertBanner(
                BannerEntity(
                    title = title.trim(),
                    imageUrl = imageUrl.trim(),
                    linkUrl = linkUrl.trim(),
                    buttonText = if (buttonText.isBlank()) "VIEW" else buttonText.trim(),
                    description = description.trim()
                )
            )
        }
    }

    fun adminDeleteBanner(id: Int) {
        if (currentUser?.role != "ADMIN") return
        viewModelScope.launch {
            repository.deleteBanner(id)
        }
    }
}
