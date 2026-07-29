package com.example.data

import android.content.Context
import android.net.Uri
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
import kotlinx.coroutines.withContext

class AcademyRepository(private val context: Context) {
    private val academyDao = AcademyDatabase.getDatabase(context).academyDao()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private fun getApi(): SupabaseApi {
        val creds = R2SupabaseManager.getCredentials(context)
        val rawUrl = if (creds.supabaseUrl.isNotBlank()) creds.cleanBaseUrl else "https://dummy.supabase.co"
        val baseUrl = if (rawUrl.endsWith("/")) rawUrl else "$rawUrl/"

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
        Log.i("AcademyRepository", "Converting object to JSON for Supabase: $jsonStr")
        return jsonStr.toRequestBody("application/json".toMediaTypeOrNull())
    }

    private fun syncWithSupabase(action: suspend (SupabaseApi) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                action(getApi())
            } catch (e: Exception) {
                Log.e("AcademyRepository", "Supabase sync failed: ${e.message}", e)
            }
        }
    }

    private suspend fun syncWithSupabaseAwait(action: suspend (SupabaseApi) -> Unit): Boolean {
        return try {
            action(getApi())
            true
        } catch (e: Exception) {
            Log.e("AcademyRepository", "Supabase sync (await) failed: ${e.message}", e)
            false
        }
    }

    // === Courses ===
    val allCourses: Flow<List<CourseEntity>> = academyDao.getAllCourses()

    suspend fun insertCourse(course: CourseEntity): Int {
        val id = academyDao.insertCourse(course).toInt()
        Log.i("AcademyRepository", "[PUBLIC URL] Course Image URL: ${course.imageUrl}")
        Log.i("AcademyRepository", "[ROOM SAVE] Course saved locally. ID: $id, Title: ${course.title}, Image URL: ${course.imageUrl}")
        Log.i("AcademyRepository", "[INSERT] New Course ID assigned in local DB: $id")
        
        val rb = toRequestBody(course.copy(id = id))
        Log.i("AcademyRepository", "[INSERT] Supabase INSERT Request Body for Course ID $id: ${JSONObject(moshi.adapter(CourseEntity::class.java).toJson(course.copy(id = id))).toString(2)}")
        
        val success = syncWithSupabaseAwait { 
            Log.d("AcademyRepository", "[INSERT] Sending POST request to Supabase courses table...")
            val resp = it.insertCourse(rb)
            Log.i("AcademyRepository", "[UPLOAD SUCCESS] [INSERT] Supabase Response (Course Created): $resp")
            
            // Verification
            try {
                val verified = it.getCourseById("eq.$id")
                if (verified.isNotEmpty()) {
                    val remoteCourse = verified[0]
                    Log.i("AcademyRepository", "[DATABASE UPDATE SUCCESS] Verified Course ID $id in Supabase. imageUrl: ${remoteCourse.imageUrl}")
                    if (remoteCourse.imageUrl.isBlank()) {
                        Log.e("AcademyRepository", "[DATABASE UPDATE FAILED] Verified Course ID $id but imageUrl is EMPTY in Supabase!")
                    }
                }
            } catch (e: Exception) {
                Log.e("AcademyRepository", "Verification failed after insert", e)
            }
        }
        
        if (success) {
            Log.i("AcademyRepository", "[INSERT] Course ID=$id successfully synchronized to Supabase.")
        } else {
            Log.e("AcademyRepository", "[INSERT] Failed to synchronize Course ID=$id to Supabase.")
        }
        return id
    }

    suspend fun deleteCourse(id: Int) {
        academyDao.deleteCourseById(id)
        syncWithSupabase { it.deleteCourseById("eq.$id") }
    }

    suspend fun updateCourse(course: CourseEntity): Boolean {
        Log.i("AcademyRepository", "[PUBLIC URL] Updating with Image URL: ${course.imageUrl}")
        Log.i("AcademyRepository", "[UPDATE] Updating course in local DB: ID=${course.id}, Title=${course.title}")
        Log.i("AcademyRepository", "[ROOM SAVE] Updating course locally. ID: ${course.id}, Title: ${course.title}, Image URL: ${course.imageUrl}")
        academyDao.updateCourse(course)
        
        Log.i("AcademyRepository", "[UPDATE] Synchronizing course update to Supabase Storage Database: ID=${course.id}")
        val rb = toRequestBody(course)
        val jsonStr = moshi.adapter(CourseEntity::class.java).toJson(course)
        Log.i("AcademyRepository", "[PATCH BODY] Exact JSON request body sent to Supabase for course ID=${course.id}: $jsonStr")
        
        val success = syncWithSupabaseAwait { 
            Log.i("AcademyRepository", "[COURSE UPDATE] [UPDATE] Sending PATCH request to Supabase for ID=${course.id}...")
            it.updateCourse("eq.${course.id}", rb)
            Log.i("AcademyRepository", "[UPLOAD SUCCESS] [UPDATE] Supabase update call completed successfully for ID=${course.id}")
            
            // Verification
            try {
                val verified = it.getCourseById("eq.${course.id}")
                if (verified.isNotEmpty()) {
                    val remoteCourse = verified[0]
                    Log.i("AcademyRepository", "[DATABASE UPDATE SUCCESS] Verified updated Course ID ${course.id} in Supabase. imageUrl: ${remoteCourse.imageUrl}")
                    if (remoteCourse.imageUrl.isBlank()) {
                        Log.e("AcademyRepository", "[DATABASE UPDATE FAILED] Verified updated Course ID ${course.id} but imageUrl is EMPTY in Supabase!")
                    }
                }
            } catch (e: Exception) {
                Log.e("AcademyRepository", "Verification failed after update", e)
            }
        }
        
        if (success) {
            Log.i("AcademyRepository", "[UPDATE] Course ID=${course.id} successfully updated on Supabase.")
        } else {
            Log.e("AcademyRepository", "[UPDATE] Failed to update Course ID=${course.id} on Supabase.")
        }
        
        return success
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

    suspend fun updateTest(test: TestEntity) {
        academyDao.updateTest(test)
        syncWithSupabase { it.updateTest("eq.${test.id}", toRequestBody(test)) }
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

    suspend fun updateBanner(banner: BannerEntity) {
        academyDao.insertBanner(banner)
        syncWithSupabase { it.updateBanner("eq.${banner.id}", toRequestBody(banner)) }
    }

    suspend fun deleteBanner(id: Int) {
        academyDao.deleteBannerById(id)
        syncWithSupabase { it.deleteBannerById("eq.$id") }
    }

    suspend fun uploadBannerImage(context: Context, uri: Uri): String {
        return R2SupabaseManager.uploadFile(context, uri, "videos")
    }

    suspend fun uploadLiveClassThumbnail(context: Context, uri: Uri): String {
        return R2SupabaseManager.uploadFile(context, uri, "videos")
    }

data class SupabaseLiveClassResult(
    val isSuccess: Boolean,
    val statusCode: Int = 0,
    val message: String = "",
    val code: String = "",
    val details: String = "",
    val hint: String = "",
    val rawBody: String = ""
) {
    fun toFormattedErrorMessage(): String {
        val parts = mutableListOf<String>()
        if (statusCode > 0) parts.add("HTTP Status: $statusCode")
        if (message.isNotBlank()) parts.add("Message: $message")
        if (code.isNotBlank()) parts.add("Code: $code")
        if (details.isNotBlank() && details != "null") parts.add("Details: $details")
        if (hint.isNotBlank() && hint != "null") parts.add("Hint: $hint")
        if (parts.isEmpty() && rawBody.isNotBlank()) parts.add("Raw: $rawBody")
        return parts.joinToString("\n• ")
    }
}

    // === Live Classes ===
    val allLiveClasses: Flow<List<LiveClassEntity>> = academyDao.getAllLiveClasses()

    suspend fun insertLiveClass(liveClass: LiveClassEntity): SupabaseLiveClassResult = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val creds = R2SupabaseManager.getCredentials(context)
        val rawUrl = if (creds.supabaseUrl.isNotBlank()) creds.cleanBaseUrl else "https://dummy.supabase.co"
        val baseUrl = if (rawUrl.endsWith("/")) rawUrl else "$rawUrl/"
        val endpointUrl = "${baseUrl}rest/v1/live_classes"

        val maskedKey = if (creds.supabaseAnonKey.length > 8)
            "${creds.supabaseAnonKey.take(4)}...${creds.supabaseAnonKey.takeLast(4)}"
        else "PRESENT"

        Log.d("AcademyRepository", "--------------------------------------------------")
        Log.d("AcademyRepository", "[SUPABASE INSERT REQUEST START]")
        Log.d("AcademyRepository", "Endpoint URL: $endpointUrl")
        Log.d("AcademyRepository", "Headers: apikey=$maskedKey, Authorization=Bearer $maskedKey, Content-Type=application/json, Prefer=return=representation")

        val ytId = liveClass.effectiveYoutubeId
        val ytUrl = if (liveClass.youtubeUrl.isNotBlank()) liveClass.youtubeUrl else "https://www.youtube.com/watch?v=$ytId"
        val thumbUrl = liveClass.effectiveThumbnailUrl

        // Candidate 1: Minimal Schema (Prompt Item 5: title, youtube_url, youtube_live_id, thumbnail_url, status)
        val payload1 = JSONObject().apply {
            put("title", liveClass.title.ifBlank { "Lakshya Live Class" })
            put("youtube_url", ytUrl)
            put("youtube_live_id", ytId)
            put("thumbnail_url", thumbUrl)
            put("status", liveClass.status.ifBlank { "Scheduled" })
        }

        // Candidate 2: Full PostgREST Schema
        val payload2 = JSONObject().apply {
            put("title", liveClass.title.ifBlank { "Lakshya Live Class" })
            put("description", liveClass.description.ifBlank { "Interactive Live Stream" })
            put("teacher_name", liveClass.teacherName.ifBlank { "Lakshya Academy" })
            put("subject", liveClass.subject.ifBlank { "Live Class" })
            put("chapter", liveClass.chapter.ifBlank { "Lakshya Classroom" })
            put("thumbnail_url", thumbUrl)
            put("youtube_live_id", ytId)
            put("youtube_url", ytUrl)
            put("status", liveClass.status.ifBlank { "Scheduled" })
            put("scheduled_date", liveClass.scheduledDate)
            put("scheduled_time", liveClass.scheduledTime)
        }

        // Candidate 3: Ultra Minimal Schema (title, youtube_url, youtube_live_id)
        val payload3 = JSONObject().apply {
            put("title", liveClass.title.ifBlank { "Lakshya Live Class" })
            put("youtube_url", ytUrl)
            put("youtube_live_id", ytId)
        }

        // Candidate 4: camelCase Schema (title, youtubeUrl, thumbnailUrl)
        val payload4 = JSONObject().apply {
            put("title", liveClass.title.ifBlank { "Lakshya Live Class" })
            put("youtubeUrl", ytUrl)
            put("thumbnailUrl", thumbUrl)
        }

        val candidates = listOf(
            "Minimal PostgREST Schema" to payload1,
            "Full PostgREST Schema" to payload2,
            "Ultra-Minimal Schema" to payload3,
            "camelCase Schema" to payload4
        )

        val api = getApi()
        var lastResult = SupabaseLiveClassResult(isSuccess = false, message = "Unknown error")

        for ((index, candidate) in candidates.withIndex()) {
            val (label, jsonObj) = candidate
            val jsonString = jsonObj.toString()
            Log.d("AcademyRepository", "[INSERT ATTEMPT ${index + 1}/4] $label")
            Log.d("AcademyRepository", "Request JSON:\n$jsonString")

            try {
                val reqBody = jsonString.toRequestBody("application/json".toMediaTypeOrNull())
                val response = api.insertLiveClass(reqBody)
                val responseCode = response.code()
                val responseBodyStr = response.body()?.string() ?: ""
                val errorBodyStr = response.errorBody()?.string() ?: ""

                Log.d("AcademyRepository", "Response Code: $responseCode")
                if (responseBodyStr.isNotBlank()) Log.d("AcademyRepository", "Response Body:\n$responseBodyStr")
                if (errorBodyStr.isNotBlank()) Log.d("AcademyRepository", "Error Body:\n$errorBodyStr")

                if (response.isSuccessful && (responseCode == 200 || responseCode == 201)) {
                    Log.d("AcademyRepository", "[INSERT RESULT: SUCCESS] Inserted successfully into public.live_classes")
                    
                    // Fetch latest live classes from Supabase to sync Room & UI
                    try {
                        val remoteList = api.getAllLiveClasses()
                        Log.d("AcademyRepository", "[FETCH RESULT] Retrieved ${remoteList.size} live classes from Supabase after insert.")
                        remoteList.forEach { academyDao.insertLiveClass(it) }
                    } catch (fetchErr: Exception) {
                        Log.e("AcademyRepository", "[FETCH RESULT ERROR] Could not refresh list after insert: ${fetchErr.message}", fetchErr)
                        academyDao.insertLiveClass(liveClass)
                    }

                    Log.d("AcademyRepository", "--------------------------------------------------")
                    return@withContext SupabaseLiveClassResult(
                        isSuccess = true,
                        statusCode = responseCode,
                        message = "Live class added successfully",
                        rawBody = responseBodyStr
                    )
                } else {
                    var msg = ""
                    var code = ""
                    var details = ""
                    var hint = ""
                    if (errorBodyStr.isNotBlank()) {
                        try {
                            val errJson = JSONObject(errorBodyStr)
                            msg = errJson.optString("message", "")
                            code = errJson.optString("code", "")
                            details = errJson.optString("details", "")
                            hint = errJson.optString("hint", "")
                        } catch (_: Exception) {
                            msg = errorBodyStr
                        }
                    }

                    Log.e("AcademyRepository", """
                        [INSERT ATTEMPT ${index + 1} FAILED]
                        HTTP Status Code: $responseCode
                        Message: $msg
                        Code: $code
                        Details: $details
                        Hint: $hint
                        Raw Error: $errorBodyStr
                    """.trimIndent())

                    lastResult = SupabaseLiveClassResult(
                        isSuccess = false,
                        statusCode = responseCode,
                        message = msg,
                        code = code,
                        details = details,
                        hint = hint,
                        rawBody = errorBodyStr
                    )

                    if (responseCode == 401 || responseCode == 403 || code == "42501") {
                        Log.e("AcademyRepository", "[RLS / AUTH ERROR DETECTED] RLS policy or Auth credential is blocking insert. Stopping further payload attempts.")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e("AcademyRepository", "[INSERT EXCEPTION] Attempt ${index + 1} exception: ${e.message}", e)
                lastResult = SupabaseLiveClassResult(
                    isSuccess = false,
                    statusCode = 0,
                    message = e.message ?: "Network Exception: ${e.javaClass.simpleName}"
                )
            }
        }

        Log.e("AcademyRepository", "[INSERT RESULT: FAILURE] All candidate inserts failed.")
        Log.d("AcademyRepository", "--------------------------------------------------")
        return@withContext lastResult
    }

    suspend fun updateLiveClass(liveClass: LiveClassEntity): SupabaseLiveClassResult = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val api = getApi()
        val ytId = liveClass.effectiveYoutubeId
        val ytUrl = if (liveClass.youtubeUrl.isNotBlank()) liveClass.youtubeUrl else "https://www.youtube.com/watch?v=$ytId"
        val thumbUrl = liveClass.effectiveThumbnailUrl

        val jsonObject = JSONObject().apply {
            put("id", liveClass.id)
            put("title", liveClass.title)
            put("description", liveClass.description)
            put("teacher_name", liveClass.teacherName)
            put("subject", liveClass.subject)
            put("chapter", liveClass.chapter)
            put("thumbnail_url", thumbUrl)
            put("youtube_live_id", ytId)
            put("youtube_url", ytUrl)
            put("status", liveClass.status)
            put("scheduled_date", liveClass.scheduledDate)
            put("scheduled_time", liveClass.scheduledTime)
        }

        val jsonString = jsonObject.toString()
        Log.d("AcademyRepository", "[UPDATE REQUEST] PATCH rest/v1/live_classes?id=eq.${liveClass.id}")
        Log.d("AcademyRepository", "Request JSON:\n$jsonString")

        try {
            academyDao.insertLiveClass(liveClass)
            val requestBody = jsonString.toRequestBody("application/json".toMediaTypeOrNull())
            val response = api.updateLiveClass("eq.${liveClass.id}", requestBody)
            val responseCode = response.code()
            val responseBodyStr = response.body()?.string() ?: ""
            val errorBodyStr = response.errorBody()?.string() ?: ""

            Log.d("AcademyRepository", "Update Response Code: $responseCode")
            if (responseBodyStr.isNotBlank()) Log.d("AcademyRepository", "Response Body:\n$responseBodyStr")

            if (response.isSuccessful) {
                Log.d("AcademyRepository", "[UPDATE RESULT: SUCCESS] Updated live class ID ${liveClass.id}")
                try {
                    val remoteList = api.getAllLiveClasses()
                    Log.d("AcademyRepository", "[FETCH RESULT] Retrieved ${remoteList.size} live classes after update.")
                    remoteList.forEach { academyDao.insertLiveClass(it) }
                } catch (e: Exception) {
                    Log.e("AcademyRepository", "Could not fetch list after update: ${e.message}")
                }
                SupabaseLiveClassResult(isSuccess = true, statusCode = responseCode, message = "Live class stream updated!")
            } else {
                var msg = ""
                var code = ""
                var details = ""
                var hint = ""
                if (errorBodyStr.isNotBlank()) {
                    try {
                        val errJson = JSONObject(errorBodyStr)
                        msg = errJson.optString("message", "")
                        code = errJson.optString("code", "")
                        details = errJson.optString("details", "")
                        hint = errJson.optString("hint", "")
                    } catch (_: Exception) {
                        msg = errorBodyStr
                    }
                }
                Log.e("AcademyRepository", "[UPDATE FAILED] HTTP $responseCode Code: $code Msg: $msg")
                SupabaseLiveClassResult(
                    isSuccess = false,
                    statusCode = responseCode,
                    message = msg,
                    code = code,
                    details = details,
                    hint = hint,
                    rawBody = errorBodyStr
                )
            }
        } catch (e: Exception) {
            Log.e("AcademyRepository", "[UPDATE EXCEPTION] ${e.message}", e)
            SupabaseLiveClassResult(isSuccess = false, statusCode = 0, message = e.message ?: "Exception: ${e.javaClass.simpleName}")
        }
    }

    suspend fun deleteLiveClass(id: Int): SupabaseLiveClassResult = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val api = getApi()
        Log.d("AcademyRepository", "[DELETE REQUEST] DELETE rest/v1/live_classes?id=eq.$id")
        try {
            academyDao.deleteLiveClassById(id)
            val response = api.deleteLiveClassById("eq.$id")
            val responseCode = response.code()
            Log.d("AcademyRepository", "Delete Response Code: $responseCode")

            if (response.isSuccessful) {
                Log.d("AcademyRepository", "[DELETE RESULT: SUCCESS] Deleted live class ID $id")
                try {
                    val remoteList = api.getAllLiveClasses()
                    Log.d("AcademyRepository", "[FETCH RESULT] Retrieved ${remoteList.size} remaining live classes.")
                    remoteList.forEach { academyDao.insertLiveClass(it) }
                } catch (e: Exception) {
                    Log.e("AcademyRepository", "Could not fetch list after delete: ${e.message}")
                }
                SupabaseLiveClassResult(isSuccess = true, statusCode = responseCode, message = "Live class deleted")
            } else {
                val errorBodyStr = response.errorBody()?.string() ?: ""
                var msg = ""
                var code = ""
                if (errorBodyStr.isNotBlank()) {
                    try {
                        val errJson = JSONObject(errorBodyStr)
                        msg = errJson.optString("message", "")
                        code = errJson.optString("code", "")
                    } catch (_: Exception) {
                        msg = errorBodyStr
                    }
                }
                Log.e("AcademyRepository", "[DELETE FAILED] HTTP $responseCode Code: $code Msg: $msg")
                SupabaseLiveClassResult(isSuccess = false, statusCode = responseCode, message = msg, code = code, rawBody = errorBodyStr)
            }
        } catch (e: Exception) {
            Log.e("AcademyRepository", "[DELETE EXCEPTION] ${e.message}", e)
            SupabaseLiveClassResult(isSuccess = false, statusCode = 0, message = e.message ?: "Exception: ${e.javaClass.simpleName}")
        }
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
        Log.i("AcademyRepository", "[BATCH SYNC] Starting full synchronization from remote Supabase...")
        val api = try {
            getApi()
        } catch (e: Exception) {
            Log.e("AcademyRepository", "[BATCH SYNC] Failed to construct Supabase API client", e)
            return
        }

        // 1. Sync Courses (Batches)
        try {
            Log.d("AcademyRepository", "[BATCH SYNC] Fetching all courses/batches from Supabase...")
            val remoteCourses = api.getAllCourses()
            Log.i("AcademyRepository", "[BATCH SYNC] Successfully downloaded ${remoteCourses.size} courses/batches from Supabase.")
            
            // Preserve local imageUrl if remote one is blank
            val localCourses = academyDao.getAllCoursesDirect()
            val coursesToInsert = remoteCourses.map { remoteCourse ->
                val localCourse = localCourses.find { it.id == remoteCourse.id }
                if (localCourse != null && remoteCourse.imageUrl.isBlank() && localCourse.imageUrl.isNotBlank()) {
                    remoteCourse.copy(imageUrl = localCourse.imageUrl)
                } else {
                    remoteCourse
                }
            }
            
            academyDao.deleteAllCourses()
            coursesToInsert.forEach { 
                academyDao.insertCourse(it)
            }
        } catch (e: Exception) {
            Log.e("AcademyRepository", "[BATCH SYNC] Error syncing courses/batches", e)
        }
        
        // 2. Sync Lessons
        try {
            Log.d("AcademyRepository", "[BATCH SYNC] Fetching all lessons from Supabase...")
            val remoteLessons = api.getAllLessons()
            Log.d("AcademyRepository", "[BATCH SYNC] Downloaded ${remoteLessons.size} lessons.")
            remoteLessons.forEach { academyDao.insertLesson(it) }
        } catch (e: Exception) {
            Log.e("AcademyRepository", "[BATCH SYNC] Error syncing lessons", e)
        }
        
        // 3. Sync Tests
        try {
            Log.d("AcademyRepository", "[BATCH SYNC] Fetching all tests from Supabase...")
            val remoteTests = api.getAllTests()
            Log.d("AcademyRepository", "[BATCH SYNC] Downloaded ${remoteTests.size} tests.")
            remoteTests.forEach { academyDao.insertTest(it) }
        } catch (e: Exception) {
            Log.e("AcademyRepository", "[BATCH SYNC] Error syncing tests", e)
        }
        
        // 4. Sync Questions
        try {
            Log.d("AcademyRepository", "[BATCH SYNC] Fetching all questions from Supabase...")
            val remoteQuestions = api.getAllQuestions()
            Log.d("AcademyRepository", "[BATCH SYNC] Downloaded ${remoteQuestions.size} questions.")
            remoteQuestions.forEach { academyDao.insertQuestion(it) }
        } catch (e: Exception) {
            Log.e("AcademyRepository", "[BATCH SYNC] Error syncing questions", e)
        }
        
        // 5. Sync Materials
        try {
            Log.d("AcademyRepository", "[BATCH SYNC] Fetching all materials from Supabase...")
            val remoteMaterials = api.getAllMaterials()
            Log.d("AcademyRepository", "[BATCH SYNC] Downloaded ${remoteMaterials.size} materials.")
            remoteMaterials.forEach { academyDao.insertMaterial(it) }
        } catch (e: Exception) {
            Log.e("AcademyRepository", "[BATCH SYNC] Error syncing materials", e)
        }
        
        // 6. Sync Live Classes
        try {
            Log.d("AcademyRepository", "[BATCH SYNC] Fetching all live classes from Supabase...")
            val remoteLiveClasses = api.getAllLiveClasses()
            Log.d("AcademyRepository", "[FETCH RESPONSE] [BATCH SYNC] Downloaded live classes from Supabase: $remoteLiveClasses")
            Log.d("AcademyRepository", "[LIVE CLASSES COUNT] Number of live classes returned: ${remoteLiveClasses.size}")
            remoteLiveClasses.forEach { academyDao.insertLiveClass(it) }
        } catch (e: Exception) {
            Log.e("AcademyRepository", "[BATCH SYNC] Error syncing live classes", e)
        }
        
        // 7. Sync Doubts
        try {
            Log.d("AcademyRepository", "[BATCH SYNC] Fetching all doubts from Supabase...")
            val remoteDoubts = api.getAllDoubts()
            Log.d("AcademyRepository", "[BATCH SYNC] Downloaded ${remoteDoubts.size} doubts.")
            remoteDoubts.forEach { academyDao.insertDoubt(it) }
        } catch (e: Exception) {
            Log.e("AcademyRepository", "[BATCH SYNC] Error syncing doubts", e)
        }
        
        // 8. Sync Chat Messages
        try {
            Log.d("AcademyRepository", "[BATCH SYNC] Fetching all chat messages from Supabase...")
            val remoteChatMessages = api.getAllChatMessages()
            Log.d("AcademyRepository", "[BATCH SYNC] Downloaded ${remoteChatMessages.size} chat messages.")
            remoteChatMessages.forEach { academyDao.insertChatMessage(it) }
        } catch (e: Exception) {
            Log.e("AcademyRepository", "[BATCH SYNC] Error syncing chat messages", e)
        }
        
        // 9. Sync Banners
        try {
            Log.d("AcademyRepository", "[BATCH SYNC] Fetching all banners from Supabase...")
            val remoteBanners = api.getAllBanners()
            Log.d("AcademyRepository", "[BATCH SYNC] Downloaded ${remoteBanners.size} banners.")
            academyDao.deleteAllBanners()
            remoteBanners.forEach { academyDao.insertBanner(it) }
        } catch (e: Exception) {
            Log.e("AcademyRepository", "[BATCH SYNC] Error syncing banners", e)
        }
        
        // 10. Sync Notifications
        try {
            Log.d("AcademyRepository", "[BATCH SYNC] Fetching all notifications from Supabase...")
            val remoteNotifications = api.getAllNotifications()
            Log.d("AcademyRepository", "[BATCH SYNC] Downloaded ${remoteNotifications.size} notifications.")
            remoteNotifications.forEach { academyDao.insertNotification(it) }
        } catch (e: Exception) {
            Log.e("AcademyRepository", "[BATCH SYNC] Error syncing notifications", e)
        }
        
        // 11. Sync Enrollments
        try {
            Log.d("AcademyRepository", "[BATCH SYNC] Fetching all enrollments from Supabase...")
            val remoteEnrollments = api.getAllEnrollments()
            Log.d("AcademyRepository", "[BATCH SYNC] Downloaded ${remoteEnrollments.size} enrollments.")
            remoteEnrollments.forEach { academyDao.insertEnrollment(it) }
        } catch (e: Exception) {
            Log.e("AcademyRepository", "[BATCH SYNC] Error syncing enrollments", e)
        }
        
        // 12. Sync Test Scores
        try {
            Log.d("AcademyRepository", "[BATCH SYNC] Fetching all test scores from Supabase...")
            val remoteScores = api.getAllScores()
            Log.d("AcademyRepository", "[BATCH SYNC] Downloaded ${remoteScores.size} test scores.")
            remoteScores.forEach { academyDao.insertScore(it) }
        } catch (e: Exception) {
            Log.e("AcademyRepository", "[BATCH SYNC] Error syncing test scores", e)
        }
        
        Log.i("AcademyRepository", "[BATCH SYNC] Batch synchronization process completed.")
    }

    suspend fun getRemoteLiveClassesDirect(): List<LiveClassEntity> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val api = getApi()
        try {
            val list = api.getAllLiveClasses()
            Log.d("AcademyRepository", "[FETCH RESPONSE] Direct fetch returned ${list.size} live classes from Supabase: $list")
            Log.d("AcademyRepository", "[LIVE CLASSES COUNT] Number of live classes returned: ${list.size}")
            list.forEach { academyDao.insertLiveClass(it) }
            list
        } catch (e: Exception) {
            Log.e("AcademyRepository", "[FETCH ERROR] getRemoteLiveClassesDirect failed: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getLatestAppUpdate(): AppUpdateEntity? {
        Log.d("AcademyRepository", "Checking latest app update from Supabase...")
        return try {
            val api = getApi()
            val updates = api.getAppUpdates()
            Log.d("AcademyRepository", "Supabase returned ${updates.size} app updates.")
            updates.firstOrNull()
        } catch (e: Exception) {
            Log.e("AcademyRepository", "Failed to retrieve latest app update: ${e.message}", e)
            null
        }
    }

    // === Folder Management System Repository Methods ===

    val allSyllabusFolders: Flow<List<SyllabusFolderEntity>> = academyDao.getAllSyllabusFolders()
    val allSyllabusFiles: Flow<List<SyllabusFileEntity>> = academyDao.getAllSyllabusFiles()

    val allPreviousPaperFolders: Flow<List<PreviousPaperFolderEntity>> = academyDao.getAllPreviousPaperFolders()
    val allPreviousPaperFiles: Flow<List<PreviousPaperFileEntity>> = academyDao.getAllPreviousPaperFiles()

    val allFreeBookFolders: Flow<List<FreeBookFolderEntity>> = academyDao.getAllFreeBookFolders()
    val allFreeBookFiles: Flow<List<FreeBookFileEntity>> = academyDao.getAllFreeBookFiles()

    private fun getOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    suspend fun syncFoldersAndFilesFromSupabase(module: FolderModule) = withContext(Dispatchers.IO) {
        val creds = R2SupabaseManager.getCredentials(context)
        if (creds.supabaseUrl.isBlank() || creds.supabaseAnonKey.isBlank()) return@withContext
        val baseUrl = creds.cleanBaseUrl.trimEnd('/')
        val client = getOkHttpClient()

        // Fetch Folders
        try {
            val req = okhttp3.Request.Builder()
                .url("$baseUrl/rest/v1/${module.folderTable}?select=*&order=id.desc")
                .addHeader("apikey", creds.supabaseAnonKey)
                .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
                .get()
                .build()

            val folderEntities = mutableListOf<Triple<Long, FolderItem, String>>()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: "[]"
                    val jsonArray = org.json.JSONArray(body)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val id = obj.optLong("id")
                        val parentId = if (obj.isNull("parent_id")) null else obj.optLong("parent_id")
                        val courseId = if (obj.isNull("course_id")) null else obj.optString("course_id")
                        val name = obj.optString("name")
                        val imageUrl = obj.optString("image_url")
                        val createdAt = obj.optString("created_at", System.currentTimeMillis().toString())

                        folderEntities.add(Triple(id, FolderItem(id, parentId, courseId, name, imageUrl, createdAt), courseId ?: ""))
                    }
                }
            }

            for ((id, folder, cId) in folderEntities) {
                when (module) {
                    FolderModule.SYLLABUS -> academyDao.insertSyllabusFolder(
                        SyllabusFolderEntity(id, folder.parentId, folder.courseId, folder.name, folder.imageUrl, folder.createdAt)
                    )
                    FolderModule.PREVIOUS_PAPERS -> academyDao.insertPreviousPaperFolder(
                        PreviousPaperFolderEntity(id, folder.parentId, folder.name, folder.imageUrl, folder.createdAt)
                    )
                    FolderModule.FREE_BOOKS -> academyDao.insertFreeBookFolder(
                        FreeBookFolderEntity(id, folder.parentId, folder.name, folder.imageUrl, folder.createdAt)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("AcademyRepository", "Error syncing folders for ${module.titleName}: ${e.message}")
        }

        // Fetch Files
        try {
            val req = okhttp3.Request.Builder()
                .url("$baseUrl/rest/v1/${module.fileTable}?select=*&order=id.desc")
                .addHeader("apikey", creds.supabaseAnonKey)
                .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
                .get()
                .build()

            val fileEntities = mutableListOf<FileItem>()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: "[]"
                    val jsonArray = org.json.JSONArray(body)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val id = obj.optLong("id")
                        val folderId = obj.optLong("folder_id")
                        val courseId = if (obj.isNull("course_id")) null else obj.optString("course_id")
                        val fileName = obj.optString("file_name")
                        val fileType = obj.optString("file_type", "pdf")
                        val storageUrl = obj.optString("storage_url")
                        val fileSize = obj.optString("file_size")
                        val createdAt = obj.optString("created_at", System.currentTimeMillis().toString())

                        fileEntities.add(FileItem(id, folderId, courseId, fileName, fileType, storageUrl, fileSize, createdAt))
                    }
                }
            }

            for (file in fileEntities) {
                when (module) {
                    FolderModule.SYLLABUS -> academyDao.insertSyllabusFile(
                        SyllabusFileEntity(file.id, file.folderId, file.courseId, file.fileName, file.fileType, file.storageUrl, file.fileSize, file.createdAt)
                    )
                    FolderModule.PREVIOUS_PAPERS -> academyDao.insertPreviousPaperFile(
                        PreviousPaperFileEntity(file.id, file.folderId, file.fileName, file.fileType, file.storageUrl, file.fileSize, file.createdAt)
                    )
                    FolderModule.FREE_BOOKS -> academyDao.insertFreeBookFile(
                        FreeBookFileEntity(file.id, file.folderId, file.fileName, file.fileType, file.storageUrl, file.fileSize, file.createdAt)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("AcademyRepository", "Error syncing files for ${module.titleName}: ${e.message}")
        }
    }

    suspend fun insertFolder(module: FolderModule, folder: FolderItem): Boolean = withContext(Dispatchers.IO) {
        var localId: Long = folder.id
        when (module) {
            FolderModule.SYLLABUS -> {
                localId = academyDao.insertSyllabusFolder(
                    SyllabusFolderEntity(folder.id, folder.parentId, folder.courseId, folder.name, folder.imageUrl, folder.createdAt)
                )
            }
            FolderModule.PREVIOUS_PAPERS -> {
                localId = academyDao.insertPreviousPaperFolder(
                    PreviousPaperFolderEntity(folder.id, folder.parentId, folder.name, folder.imageUrl, folder.createdAt)
                )
            }
            FolderModule.FREE_BOOKS -> {
                localId = academyDao.insertFreeBookFolder(
                    FreeBookFolderEntity(folder.id, folder.parentId, folder.name, folder.imageUrl, folder.createdAt)
                )
            }
        }

        val creds = R2SupabaseManager.getCredentials(context)
        if (creds.supabaseUrl.isBlank() || creds.supabaseAnonKey.isBlank()) return@withContext true
        val baseUrl = creds.cleanBaseUrl.trimEnd('/')

        val json = JSONObject().apply {
            if (folder.id > 0) put("id", folder.id)
            put("parent_id", folder.parentId ?: JSONObject.NULL)
            if (module == FolderModule.SYLLABUS && folder.courseId != null) put("course_id", folder.courseId)
            put("name", folder.name)
            put("image_url", folder.imageUrl)
            put("created_at", folder.createdAt)
        }

        try {
            val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val req = okhttp3.Request.Builder()
                .url("$baseUrl/rest/v1/${module.folderTable}")
                .addHeader("apikey", creds.supabaseAnonKey)
                .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
                .addHeader("Prefer", "return=representation")
                .post(body)
                .build()

            var remoteIdToUpdate: Long? = null
            getOkHttpClient().newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val respBody = resp.body?.string() ?: ""
                    val arr = org.json.JSONArray(respBody)
                    if (arr.length() > 0) {
                        val returnedObj = arr.getJSONObject(0)
                        val remoteId = returnedObj.optLong("id")
                        if (remoteId > 0 && remoteId != localId) {
                            remoteIdToUpdate = remoteId
                        }
                    }
                }
            }

            if (remoteIdToUpdate != null) {
                val remoteId = remoteIdToUpdate!!
                val remoteFolder = folder.copy(id = remoteId)
                when (module) {
                    FolderModule.SYLLABUS -> {
                        academyDao.deleteSyllabusFolderById(localId)
                        academyDao.insertSyllabusFolder(
                            SyllabusFolderEntity(remoteId, remoteFolder.parentId, remoteFolder.courseId, remoteFolder.name, remoteFolder.imageUrl, remoteFolder.createdAt)
                        )
                    }
                    FolderModule.PREVIOUS_PAPERS -> {
                        academyDao.deletePreviousPaperFolderById(localId)
                        academyDao.insertPreviousPaperFolder(
                            PreviousPaperFolderEntity(remoteId, remoteFolder.parentId, remoteFolder.name, remoteFolder.imageUrl, remoteFolder.createdAt)
                        )
                    }
                    FolderModule.FREE_BOOKS -> {
                        academyDao.deleteFreeBookFolderById(localId)
                        academyDao.insertFreeBookFolder(
                            FreeBookFolderEntity(remoteId, remoteFolder.parentId, remoteFolder.name, remoteFolder.imageUrl, remoteFolder.createdAt)
                        )
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e("AcademyRepository", "insertFolder Supabase error: ${e.message}")
            true
        }
    }

    suspend fun updateFolder(module: FolderModule, folder: FolderItem): Boolean = withContext(Dispatchers.IO) {
        when (module) {
            FolderModule.SYLLABUS -> academyDao.insertSyllabusFolder(
                SyllabusFolderEntity(folder.id, folder.parentId, folder.courseId, folder.name, folder.imageUrl, folder.createdAt)
            )
            FolderModule.PREVIOUS_PAPERS -> academyDao.insertPreviousPaperFolder(
                PreviousPaperFolderEntity(folder.id, folder.parentId, folder.name, folder.imageUrl, folder.createdAt)
            )
            FolderModule.FREE_BOOKS -> academyDao.insertFreeBookFolder(
                FreeBookFolderEntity(folder.id, folder.parentId, folder.name, folder.imageUrl, folder.createdAt)
            )
        }

        val creds = R2SupabaseManager.getCredentials(context)
        if (creds.supabaseUrl.isBlank()) return@withContext true
        val baseUrl = creds.cleanBaseUrl.trimEnd('/')

        val json = JSONObject().apply {
            put("parent_id", folder.parentId ?: JSONObject.NULL)
            put("name", folder.name)
            put("image_url", folder.imageUrl)
        }

        try {
            val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val req = okhttp3.Request.Builder()
                .url("$baseUrl/rest/v1/${module.folderTable}?id=eq.${folder.id}")
                .addHeader("apikey", creds.supabaseAnonKey)
                .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
                .patch(body)
                .build()

            var success = false
            getOkHttpClient().newCall(req).execute().use { success = it.isSuccessful }
            success
        } catch (e: Exception) {
            true
        }
    }

    suspend fun deleteFolder(module: FolderModule, folderId: Long): Boolean = withContext(Dispatchers.IO) {
        when (module) {
            FolderModule.SYLLABUS -> academyDao.deleteSyllabusFolderById(folderId)
            FolderModule.PREVIOUS_PAPERS -> academyDao.deletePreviousPaperFolderById(folderId)
            FolderModule.FREE_BOOKS -> academyDao.deleteFreeBookFolderById(folderId)
        }

        val creds = R2SupabaseManager.getCredentials(context)
        if (creds.supabaseUrl.isBlank()) return@withContext true
        val baseUrl = creds.cleanBaseUrl.trimEnd('/')

        try {
            val req = okhttp3.Request.Builder()
                .url("$baseUrl/rest/v1/${module.folderTable}?id=eq.$folderId")
                .addHeader("apikey", creds.supabaseAnonKey)
                .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
                .delete()
                .build()

            var success = false
            getOkHttpClient().newCall(req).execute().use { success = it.isSuccessful }
            success
        } catch (e: Exception) {
            true
        }
    }

    suspend fun insertFile(module: FolderModule, file: FileItem): Boolean = withContext(Dispatchers.IO) {
        var localId: Long = file.id
        when (module) {
            FolderModule.SYLLABUS -> {
                localId = academyDao.insertSyllabusFile(
                    SyllabusFileEntity(file.id, file.folderId, file.courseId, file.fileName, file.fileType, file.storageUrl, file.fileSize, file.createdAt)
                )
            }
            FolderModule.PREVIOUS_PAPERS -> {
                localId = academyDao.insertPreviousPaperFile(
                    PreviousPaperFileEntity(file.id, file.folderId, file.fileName, file.fileType, file.storageUrl, file.fileSize, file.createdAt)
                )
            }
            FolderModule.FREE_BOOKS -> {
                localId = academyDao.insertFreeBookFile(
                    FreeBookFileEntity(file.id, file.folderId, file.fileName, file.fileType, file.storageUrl, file.fileSize, file.createdAt)
                )
            }
        }

        val creds = R2SupabaseManager.getCredentials(context)
        if (creds.supabaseUrl.isBlank()) return@withContext true
        val baseUrl = creds.cleanBaseUrl.trimEnd('/')

        val json = JSONObject().apply {
            if (file.id > 0) put("id", file.id)
            put("folder_id", file.folderId)
            if (module == FolderModule.SYLLABUS && file.courseId != null) put("course_id", file.courseId)
            put("file_name", file.fileName)
            put("file_type", file.fileType)
            put("storage_url", file.storageUrl)
            put("file_size", file.fileSize)
            put("created_at", file.createdAt)
        }

        try {
            val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val req = okhttp3.Request.Builder()
                .url("$baseUrl/rest/v1/${module.fileTable}")
                .addHeader("apikey", creds.supabaseAnonKey)
                .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
                .addHeader("Prefer", "return=representation")
                .post(body)
                .build()

            var remoteIdToUpdate: Long? = null
            getOkHttpClient().newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val respBody = resp.body?.string() ?: ""
                    val arr = org.json.JSONArray(respBody)
                    if (arr.length() > 0) {
                        val returnedObj = arr.getJSONObject(0)
                        val remoteId = returnedObj.optLong("id")
                        if (remoteId > 0 && remoteId != localId) {
                            remoteIdToUpdate = remoteId
                        }
                    }
                }
            }

            if (remoteIdToUpdate != null) {
                val remoteId = remoteIdToUpdate!!
                val remoteFile = file.copy(id = remoteId)
                when (module) {
                    FolderModule.SYLLABUS -> {
                        academyDao.deleteSyllabusFileById(localId)
                        academyDao.insertSyllabusFile(
                            SyllabusFileEntity(remoteId, remoteFile.folderId, remoteFile.courseId, remoteFile.fileName, remoteFile.fileType, remoteFile.storageUrl, remoteFile.fileSize, remoteFile.createdAt)
                        )
                    }
                    FolderModule.PREVIOUS_PAPERS -> {
                        academyDao.deletePreviousPaperFileById(localId)
                        academyDao.insertPreviousPaperFile(
                            PreviousPaperFileEntity(remoteId, remoteFile.folderId, remoteFile.fileName, remoteFile.fileType, remoteFile.storageUrl, remoteFile.fileSize, remoteFile.createdAt)
                        )
                    }
                    FolderModule.FREE_BOOKS -> {
                        academyDao.deleteFreeBookFileById(localId)
                        academyDao.insertFreeBookFile(
                            FreeBookFileEntity(remoteId, remoteFile.folderId, remoteFile.fileName, remoteFile.fileType, remoteFile.storageUrl, remoteFile.fileSize, remoteFile.createdAt)
                        )
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e("AcademyRepository", "insertFile Supabase error: ${e.message}")
            true
        }
    }

    suspend fun updateFile(module: FolderModule, file: FileItem): Boolean = withContext(Dispatchers.IO) {
        when (module) {
            FolderModule.SYLLABUS -> academyDao.insertSyllabusFile(
                SyllabusFileEntity(file.id, file.folderId, file.courseId, file.fileName, file.fileType, file.storageUrl, file.fileSize, file.createdAt)
            )
            FolderModule.PREVIOUS_PAPERS -> academyDao.insertPreviousPaperFile(
                PreviousPaperFileEntity(file.id, file.folderId, file.fileName, file.fileType, file.storageUrl, file.fileSize, file.createdAt)
            )
            FolderModule.FREE_BOOKS -> academyDao.insertFreeBookFile(
                FreeBookFileEntity(file.id, file.folderId, file.fileName, file.fileType, file.storageUrl, file.fileSize, file.createdAt)
            )
        }

        val creds = R2SupabaseManager.getCredentials(context)
        if (creds.supabaseUrl.isBlank()) return@withContext true
        val baseUrl = creds.cleanBaseUrl.trimEnd('/')

        val json = JSONObject().apply {
            put("folder_id", file.folderId)
            put("file_name", file.fileName)
            put("storage_url", file.storageUrl)
            put("file_size", file.fileSize)
        }

        try {
            val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val req = okhttp3.Request.Builder()
                .url("$baseUrl/rest/v1/${module.fileTable}?id=eq.${file.id}")
                .addHeader("apikey", creds.supabaseAnonKey)
                .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
                .patch(body)
                .build()

            var success = false
            getOkHttpClient().newCall(req).execute().use { success = it.isSuccessful }
            success
        } catch (e: Exception) {
            true
        }
    }

    suspend fun deleteFile(module: FolderModule, fileId: Long): Boolean = withContext(Dispatchers.IO) {
        when (module) {
            FolderModule.SYLLABUS -> academyDao.deleteSyllabusFileById(fileId)
            FolderModule.PREVIOUS_PAPERS -> academyDao.deletePreviousPaperFileById(fileId)
            FolderModule.FREE_BOOKS -> academyDao.deleteFreeBookFileById(fileId)
        }

        val creds = R2SupabaseManager.getCredentials(context)
        if (creds.supabaseUrl.isBlank()) return@withContext true
        val baseUrl = creds.cleanBaseUrl.trimEnd('/')

        try {
            val req = okhttp3.Request.Builder()
                .url("$baseUrl/rest/v1/${module.fileTable}?id=eq.$fileId")
                .addHeader("apikey", creds.supabaseAnonKey)
                .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
                .delete()
                .build()

            var success = false
            getOkHttpClient().newCall(req).execute().use { success = it.isSuccessful }
            success
        } catch (e: Exception) {
            true
        }
    }
}
