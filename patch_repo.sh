cat << 'INNER_EOF' > app/src/main/java/com/example/data/AcademyRepository.kt
package com.example.data

import android.content.Context
import android.util.Log
import com.example.api.R2SupabaseManager
import com.example.api.SupabaseApi
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AcademyRepository(private val context: Context) {
    private val academyDao = AcademyDatabase.getDatabase(context).academyDao()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private fun getApi(): SupabaseApi {
        val creds = R2SupabaseManager.getCredentials(context)
        val baseUrl = if (creds.supabaseUrl.isNotBlank()) {
            if (creds.supabaseUrl.endsWith("/")) creds.supabaseUrl else "${creds.supabaseUrl}/"
        } else {
            "https://dummy.supabase.co/"
        }

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("apikey", creds.supabaseAnonKey)
                    .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
                    .addHeader("Prefer", "return=representation")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SupabaseApi::class.java)
    }

    private inline fun <reified T> toRequestBody(obj: T): RequestBody {
        val adapter = moshi.adapter(T::class.java)
        val jsonStr = adapter.toJson(obj)
        val jsonObject = JSONObject(jsonStr)
        // Keep ID for sync
        return jsonObject.toString().toRequestBody("application/json".toMediaTypeOrNull())
    }

    private fun syncWithSupabase(action: suspend (SupabaseApi) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                action(getApi())
            } catch (e: Exception) {
                Log.e("AcademyRepository", "Supabase sync failed", e)
            }
        }
    }

    // === Courses ===
    val allCourses: Flow<List<CourseEntity>> = academyDao.getAllCourses()

    suspend fun insertCourse(course: CourseEntity): Int {
        val id = academyDao.insertCourse(course).toInt()
        syncWithSupabase { it.insertCourse(toRequestBody(course.copy(id = id))) }
        return id
    }

    suspend fun deleteCourse(id: Int) {
        academyDao.deleteCourseById(id)
        syncWithSupabase { it.deleteCourseById("eq.$id") }
    }

    suspend fun updateCourse(course: CourseEntity) {
        academyDao.updateCourse(course)
        syncWithSupabase { it.updateCourse("eq.${course.id}", toRequestBody(course)) }
    }

    // === Lessons ===
    fun getLessonsForCourse(courseId: Int): Flow<List<LessonEntity>> = academyDao.getLessonsForCourse(courseId)

    suspend fun insertLesson(lesson: LessonEntity) {
        val resolvedSourceType = com.example.ui.screens.detectVideoSourceType(lesson.videoUrl)
        var finalLesson = lesson.copy(videoSourceType = resolvedSourceType)
        if (resolvedSourceType == "YOUTUBE") {
            val extractedId = com.example.ui.screens.extractYouTubeVideoId(lesson.videoUrl) ?: ""
            val generatedThumbnail = if (extractedId.isNotEmpty()) "https://img.youtube.com/vi/$extractedId/hqdefault.jpg" else lesson.thumbnailUrl
            finalLesson = finalLesson.copy(
                youtubeVideoId = extractedId,
                thumbnailUrl = if (lesson.thumbnailUrl.isBlank() || lesson.thumbnailUrl.startsWith("http://") || lesson.thumbnailUrl.startsWith("https://picsum") || lesson.thumbnailUrl == "") generatedThumbnail else lesson.thumbnailUrl
            )
        }
        academyDao.insertLesson(finalLesson)
        syncWithSupabase { it.insertLesson(toRequestBody(finalLesson)) }
    }

    suspend fun deleteLesson(id: Int) {
        academyDao.deleteLessonById(id)
        syncWithSupabase { it.deleteLessonById("eq.$id") }
    }

    // === Enrollments ===
    val allEnrollments: Flow<List<EnrollmentEntity>> = academyDao.getAllEnrollments()

    fun getEnrollmentsForUser(email: String): Flow<List<EnrollmentEntity>> = academyDao.getEnrollmentsForUser(email)

    suspend fun insertEnrollment(enrollment: EnrollmentEntity) {
        academyDao.insertEnrollment(enrollment)
        syncWithSupabase { it.insertEnrollment(toRequestBody(enrollment)) }
    }

    suspend fun updateEnrollment(enrollment: EnrollmentEntity) {
        academyDao.updateEnrollment(enrollment)
        syncWithSupabase { it.updateEnrollment("eq.${enrollment.id}", toRequestBody(enrollment)) }
    }

    // === Tests ===
    val allTests: Flow<List<TestEntity>> = academyDao.getAllTests()

    suspend fun insertTest(test: TestEntity): Int {
        val id = academyDao.insertTest(test).toInt()
        syncWithSupabase { it.insertTest(toRequestBody(test.copy(id = id))) }
        return id
    }

    suspend fun deleteTest(id: Int) {
        academyDao.deleteTestById(id)
        syncWithSupabase { it.deleteTestById("eq.$id") }
    }

    suspend fun deleteAllTests() {
        academyDao.deleteAllTests()
        syncWithSupabase { it.deleteAllTests() }
    }

    // === Questions ===
    fun getQuestionsForTest(testId: Int): Flow<List<QuestionEntity>> = academyDao.getQuestionsForTest(testId)

    suspend fun insertQuestion(question: QuestionEntity) {
        academyDao.insertQuestion(question)
        syncWithSupabase { it.insertQuestion(toRequestBody(question)) }
    }

    suspend fun deleteQuestion(id: Int) {
        academyDao.deleteQuestionById(id)
        syncWithSupabase { it.deleteQuestionById("eq.$id") }
    }

    suspend fun deleteAllQuestions() {
        academyDao.deleteAllQuestions()
        syncWithSupabase { it.deleteAllQuestions() }
    }

    // === Test Scores ===
    val allScores: Flow<List<TestScoreEntity>> = academyDao.getAllScores()

    fun getScoresForUser(email: String): Flow<List<TestScoreEntity>> = academyDao.getScoresForUser(email)

    suspend fun insertScore(score: TestScoreEntity) {
        academyDao.insertScore(score)
        syncWithSupabase { it.insertScore(toRequestBody(score)) }
    }

    // === Doubts ===
    val allDoubts: Flow<List<DoubtEntity>> = academyDao.getAllDoubts()

    suspend fun insertDoubt(doubt: DoubtEntity) {
        academyDao.insertDoubt(doubt)
        syncWithSupabase { it.insertDoubt(toRequestBody(doubt)) }
    }

    suspend fun updateDoubt(doubt: DoubtEntity) {
        academyDao.updateDoubt(doubt)
        syncWithSupabase { it.updateDoubt("eq.${doubt.id}", toRequestBody(doubt)) }
    }

    // === Chat Messages ===
    val allChatMessages: Flow<List<ChatMessageEntity>> = academyDao.getAllChatMessages()

    suspend fun insertChatMessage(message: ChatMessageEntity) {
        academyDao.insertChatMessage(message)
        syncWithSupabase { it.insertChatMessage(toRequestBody(message)) }
    }

    // === Notifications ===
    val allNotifications: Flow<List<NotificationEntity>> = academyDao.getAllNotifications()

    suspend fun insertNotification(notification: NotificationEntity) {
        academyDao.insertNotification(notification)
        syncWithSupabase { it.insertNotification(toRequestBody(notification)) }
    }

    // === Materials ===
    fun getMaterialsByType(type: String): Flow<List<MaterialEntity>> = academyDao.getMaterialsByType(type)

    suspend fun insertMaterial(material: MaterialEntity) {
        academyDao.insertMaterial(material)
        syncWithSupabase { it.insertMaterial(toRequestBody(material)) }
    }

    suspend fun deleteMaterial(id: Int) {
        academyDao.deleteMaterialById(id)
        syncWithSupabase { it.deleteMaterialById("eq.$id") }
    }

    // === Banners ===
    val allBanners: Flow<List<BannerEntity>> = academyDao.getAllBanners()

    suspend fun insertBanner(banner: BannerEntity) {
        academyDao.insertBanner(banner)
        syncWithSupabase { it.insertBanner(toRequestBody(banner)) }
    }

    suspend fun deleteBanner(id: Int) {
        academyDao.deleteBannerById(id)
        syncWithSupabase { it.deleteBannerById("eq.$id") }
    }

    // === Live Classes ===
    val allLiveClasses: Flow<List<LiveClassEntity>> = academyDao.getAllLiveClasses()

    suspend fun insertLiveClass(liveClass: LiveClassEntity) {
        academyDao.insertLiveClass(liveClass)
        syncWithSupabase { it.insertLiveClass(toRequestBody(liveClass)) }
    }

    suspend fun deleteLiveClass(id: Int) {
        academyDao.deleteLiveClassById(id)
        syncWithSupabase { it.deleteLiveClassById("eq.$id") }
    }

    // === AI Animation Limits ===
    suspend fun getAnimationLimit(email: String): AiAnimationLimitEntity? {
        return academyDao.getAnimationLimit(email)
    }

    suspend fun insertAnimationLimit(limit: AiAnimationLimitEntity) {
        academyDao.insertAnimationLimit(limit)
        syncWithSupabase { it.insertAnimationLimit(toRequestBody(limit)) }
    }

    // === AI Video Limits ===
    suspend fun getVideoLimit(email: String): AiVideoLimitEntity? {
        return academyDao.getVideoLimit(email)
    }

    suspend fun insertVideoLimit(limit: AiVideoLimitEntity) {
        academyDao.insertVideoLimit(limit)
        syncWithSupabase { it.insertVideoLimit(toRequestBody(limit)) }
    }
    
    // Remote fetch to local DB sync
    suspend fun syncAllFromRemote() {
        try {
            val api = getApi()
            
            // Sync Courses
            api.getAllCourses().forEach { academyDao.insertCourse(it) }
            
            // Sync Lessons
            api.getAllLessons().forEach { academyDao.insertLesson(it) }
            
            // Sync Tests
            api.getAllTests().forEach { academyDao.insertTest(it) }
            
            // Sync Questions
            api.getAllQuestions().forEach { academyDao.insertQuestion(it) }
            
            // Sync Materials
            api.getAllMaterials().forEach { academyDao.insertMaterial(it) }
            
            // Sync Live Classes
            api.getAllLiveClasses().forEach { academyDao.insertLiveClass(it) }
            
            // Sync Doubts
            api.getAllDoubts().forEach { academyDao.insertDoubt(it) }
            
            // Sync Chat Messages
            api.getAllChatMessages().forEach { academyDao.insertChatMessage(it) }
            
            // Sync Banners
            api.getAllBanners().forEach { academyDao.insertBanner(it) }
            
            // Sync Notifications
            api.getAllNotifications().forEach { academyDao.insertNotification(it) }
            
            // Sync Enrollments
            api.getAllEnrollments().forEach { academyDao.insertEnrollment(it) }
            
            // Sync Test Scores
            api.getAllScores().forEach { academyDao.insertScore(it) }
            
        } catch (e: Exception) {
            Log.e("AcademyRepository", "Inbound sync failed", e)
        }
    }
}
INNER_EOF
