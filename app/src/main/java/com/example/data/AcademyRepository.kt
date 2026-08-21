package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.api.R2SupabaseManager
import com.example.api.SupabaseApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
    val academyDao = AcademyDatabase.getDatabase(context).academyDao()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    fun getApi(): SupabaseApi {
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

    suspend fun deleteTest(id: Int): Boolean {
        // Try deleting questions associated with this test on Supabase first (best effort)
        try {
            syncWithSupabaseAwait { it.deleteQuestionsForTest("eq.$id") }
        } catch (e: Exception) {
            Log.e("AcademyRepository", "Error deleting remote questions for test $id: ${e.message}", e)
        }

        // Now delete the test itself from Supabase
        val isDeletedFromSupabase = syncWithSupabaseAwait { it.deleteTestById("eq.$id") }

        if (isDeletedFromSupabase) {
            // Delete questions and test locally
            try {
                academyDao.deleteQuestionsForTest(id)
            } catch (e: Exception) {
                Log.e("AcademyRepository", "Error deleting local questions for test $id: ${e.message}", e)
            }
            academyDao.deleteTestById(id)
            return true
        } else {
            return false
        }
    }

    suspend fun deleteAllTests() {
        academyDao.deleteAllTests()
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

    suspend fun sendGlobalNotification(title: String, message: String): Boolean {
        val timestamp = System.currentTimeMillis()
        val payload = mapOf(
            "title" to title.trim(),
            "message" to message.trim(),
            "timestamp" to timestamp
        )
        return try {
            val jsonStr = moshi.adapter(Map::class.java).toJson(payload)
            val body = jsonStr.toRequestBody("application/json".toMediaTypeOrNull())
            getApi().insertNotification(body)

            val localEntity = NotificationEntity(
                title = title.trim(),
                message = message.trim(),
                timestamp = timestamp
            )
            academyDao.insertNotification(localEntity)
            Log.i("AcademyRepository", "[ALERT SYSTEM] Notification Saved to Supabase: Title='${title.trim()}'")
            true
        } catch (e: Exception) {
            Log.e("AcademyRepository", "[ALERT SYSTEM] Errors sending notification: ${e.message}", e)
            try {
                val localEntity = NotificationEntity(
                    title = title.trim(),
                    message = message.trim(),
                    timestamp = timestamp
                )
                academyDao.insertNotification(localEntity)
            } catch (ignored: Exception) {}
            false
        }
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

    // === Study Websites ===
    val allStudyWebsites: Flow<List<StudyWebsiteEntity>> = academyDao.getAllStudyWebsites()

    // === Study Website Favorites (Pure Local SharedPreferences) ===
    private val favoriteIdsFlowMap = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.flow.MutableStateFlow<List<String>>>()

    private fun getFavoriteIdsFlowForUser(userEmail: String): kotlinx.coroutines.flow.MutableStateFlow<List<String>> {
        val email = userEmail.ifBlank { "guest" }
        return favoriteIdsFlowMap.getOrPut(email) {
            val sharedPrefs = context.getSharedPreferences("study_website_favorites_local", Context.MODE_PRIVATE)
            val csv = sharedPrefs.getString("favs_$email", "") ?: ""
            val list = if (csv.isBlank()) emptyList<String>() else csv.split(",")
            kotlinx.coroutines.flow.MutableStateFlow(list)
        }
    }

    fun getFavoriteStudyWebsites(userEmail: String): Flow<List<StudyWebsiteEntity>> {
        val email = userEmail.ifBlank { "guest" }
        val idsFlow = getFavoriteIdsFlowForUser(email)
        return combine(allStudyWebsites, idsFlow) { websites, favoriteIds ->
            val websiteMap = websites.associateBy { it.id }
            favoriteIds.mapNotNull { websiteMap[it] }
        }
    }

    fun getFavoriteWebsiteIds(userEmail: String): Flow<List<String>> {
        val email = userEmail.ifBlank { "guest" }
        return getFavoriteIdsFlowForUser(email)
    }

    suspend fun addStudyWebsiteFavorite(userEmail: String, websiteId: String) {
        val email = userEmail.ifBlank { "guest" }
        val sharedPrefs = context.getSharedPreferences("study_website_favorites_local", Context.MODE_PRIVATE)
        val csv = sharedPrefs.getString("favs_$email", "") ?: ""
        val list = if (csv.isBlank()) emptyList() else csv.split(",")
        val updatedList = (list.filter { it != websiteId } + websiteId)
        val newCsv = updatedList.joinToString(",")
        sharedPrefs.edit().putString("favs_$email", newCsv).apply()
        
        getFavoriteIdsFlowForUser(email).value = updatedList
    }

    suspend fun removeStudyWebsiteFavorite(userEmail: String, websiteId: String) {
        val email = userEmail.ifBlank { "guest" }
        val sharedPrefs = context.getSharedPreferences("study_website_favorites_local", Context.MODE_PRIVATE)
        val csv = sharedPrefs.getString("favs_$email", "") ?: ""
        val list = if (csv.isBlank()) emptyList() else csv.split(",")
        val updatedList = list.filter { it != websiteId }
        val newCsv = updatedList.joinToString(",")
        sharedPrefs.edit().putString("favs_$email", newCsv).apply()
        
        getFavoriteIdsFlowForUser(email).value = updatedList
    }

    suspend fun insertStudyWebsite(website: StudyWebsiteEntity) {
        academyDao.insertStudyWebsite(website)
        syncWithSupabase { it.insertStudyWebsite(toRequestBody(website)) }
    }

    suspend fun updateStudyWebsite(website: StudyWebsiteEntity) {
        academyDao.insertStudyWebsite(website)
        syncWithSupabase { it.updateStudyWebsite("eq.${website.id}", toRequestBody(website)) }
    }

    suspend fun deleteStudyWebsite(id: String) {
        academyDao.deleteStudyWebsiteById(id)
        syncWithSupabase { it.deleteStudyWebsiteById("eq.$id") }
    }

    suspend fun uploadStudyWebsiteBanner(context: Context, uri: Uri): String {
        return R2SupabaseManager.uploadFileToSupabaseStorage(context, uri, "study-websites")
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

        val dto = liveClass.toDto()
        val payload = JSONObject().apply {
            put("title", dto.title)
            put("subject", dto.subject)
            put("teacherName", dto.teacherName)
            put("thumbnailUri", dto.thumbnailUri)
            put("isLive", dto.isLive)
            put("scheduledTime", dto.scheduledTime)
            put("recordingUri", dto.recordingUri)
        }

        val api = getApi()
        var lastResult = SupabaseLiveClassResult(isSuccess = false, message = "Unknown error")
        val jsonString = payload.toString()
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
                    remoteList.forEach { academyDao.insertLiveClass(it.toEntity()) }
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
                    [INSERT FAILED]
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
            }
        } catch (e: Exception) {
            Log.e("AcademyRepository", "[INSERT EXCEPTION] exception: ${e.message}", e)
            lastResult = SupabaseLiveClassResult(
                isSuccess = false,
                statusCode = 0,
                message = e.message ?: "Network Exception: ${e.javaClass.simpleName}"
            )
        }

        Log.e("AcademyRepository", "[INSERT RESULT: FAILURE] Insert failed.")
        Log.d("AcademyRepository", "--------------------------------------------------")
        return@withContext lastResult
    }

    suspend fun updateLiveClass(liveClass: LiveClassEntity): SupabaseLiveClassResult = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val api = getApi()
        val dto = liveClass.toDto()

        val jsonObject = JSONObject().apply {
            put("id", dto.id)
            put("title", dto.title)
            put("subject", dto.subject)
            put("teacherName", dto.teacherName)
            put("thumbnailUri", dto.thumbnailUri)
            put("isLive", dto.isLive)
            put("scheduledTime", dto.scheduledTime)
            put("recordingUri", dto.recordingUri)
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
                    remoteList.forEach { academyDao.insertLiveClass(it.toEntity()) }
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
                    remoteList.forEach { academyDao.insertLiveClass(it.toEntity()) }
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
            academyDao.deleteAllTests()
            remoteTests.forEach { academyDao.insertTest(it) }
        } catch (e: Exception) {
            Log.e("AcademyRepository", "[BATCH SYNC] Error syncing tests", e)
        }
        
        // 4. Sync Questions
        try {
            Log.d("AcademyRepository", "[BATCH SYNC] Fetching all questions from Supabase...")
            val remoteQuestions = api.getAllQuestions()
            Log.d("AcademyRepository", "[BATCH SYNC] Downloaded ${remoteQuestions.size} questions.")
            academyDao.deleteAllQuestions()
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
            remoteLiveClasses.forEach { academyDao.insertLiveClass(it.toEntity()) }
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

        // 13. Sync Study Websites
        try {
            Log.d("AcademyRepository", "[BATCH SYNC] Fetching all study websites from Supabase...")
            val remoteWebsites = api.getAllStudyWebsites()
            Log.d("AcademyRepository", "[BATCH SYNC] Downloaded ${remoteWebsites.size} study websites.")
            academyDao.deleteAllStudyWebsites()
            remoteWebsites.forEach { academyDao.insertStudyWebsite(it) }
        } catch (e: Exception) {
            Log.e("AcademyRepository", "[BATCH SYNC] Error syncing study websites", e)
        }

        Log.i("AcademyRepository", "[BATCH SYNC] Batch synchronization process completed.")
    }

    suspend fun getRemoteLiveClassesDirect(): List<LiveClassEntity> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val api = getApi()
        try {
            val list = api.getAllLiveClasses()
            Log.d("AcademyRepository", "[FETCH RESPONSE] Direct fetch returned ${list.size} live classes from Supabase: $list")
            Log.d("AcademyRepository", "[LIVE CLASSES COUNT] Number of live classes returned: ${list.size}")
            val entities = list.map { it.toEntity() }
            entities.forEach { academyDao.insertLiveClass(it) }
            entities
        } catch (e: Exception) {
            Log.e("AcademyRepository", "[FETCH ERROR] getRemoteLiveClassesDirect failed: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getLatestAppUpdate(): AppUpdateEntity? {
        Log.d("AcademyRepository", "[UPDATE SYSTEM] Fetching latest app update from Supabase...")
        return try {
            val creds = R2SupabaseManager.getCredentials(context)
            val rawUrl = if (creds.supabaseUrl.isNotBlank()) creds.cleanBaseUrl else "https://dummy.supabase.co"
            val baseUrl = if (rawUrl.endsWith("/")) rawUrl else "$rawUrl/"
            val requestUrl = "${baseUrl}rest/v1/app_update?select=*"

            Log.d("AcademyRepository", "[UPDATE SYSTEM] Supabase Request URL: $requestUrl")
            Log.d("AcademyRepository", "[UPDATE SYSTEM] Using Supabase Anon Key prefix: ${creds.supabaseAnonKey.take(10)}...")

            val request = okhttp3.Request.Builder()
                .url(requestUrl)
                .addHeader("apikey", creds.supabaseAnonKey)
                .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
                .addHeader("Prefer", "return=representation")
                .get()
                .build()

            val response = getOkHttpClient().newCall(request).execute()
            val code = response.code
            val bodyString = response.body?.string() ?: ""

            Log.i("AcademyRepository", "[UPDATE SYSTEM] Supabase API Status Code: $code")
            Log.i("AcademyRepository", "[UPDATE SYSTEM] Supabase API Raw Response Body: $bodyString")

            if (!response.isSuccessful) {
                Log.e("AcademyRepository", "[UPDATE SYSTEM] Supabase request failed with HTTP $code: $bodyString")
                return null
            }

            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, AppUpdateEntity::class.java)
            val adapter = moshi.adapter<List<AppUpdateEntity>>(type)
            val updates = adapter.fromJson(bodyString)

            Log.i("AcademyRepository", "[UPDATE SYSTEM] JSON Parsing success -> Found ${updates?.size ?: 0} entries in app_update.")
            val latest = updates?.firstOrNull()
            if (latest != null) {
                Log.i("AcademyRepository", "[UPDATE SYSTEM] Parsed Entity -> ID: ${latest.id}, LatestVersion: '${latest.safeLatestVersion}', MinVersion: '${latest.safeMinimumVersion}', ForceUpdate: ${latest.isForceUpdate}, ApkUrl: '${latest.safeApkUrl}'")
            } else {
                Log.w("AcademyRepository", "[UPDATE SYSTEM] app_update table returned empty list []. No update records present.")
            }
            latest
        } catch (e: Exception) {
            Log.e("AcademyRepository", "[UPDATE SYSTEM] Exception while fetching app update: ${e.message}", e)
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

    private fun executeSupabaseRequest(request: okhttp3.Request, requestBodyString: String? = null): String {
        val client = getOkHttpClient()
        Log.i("SupabaseRequest", "--- SUPABASE REQUEST ---")
        Log.i("SupabaseRequest", "URL: ${request.url}")
        Log.i("SupabaseRequest", "Method: ${request.method}")
        if (requestBodyString != null) {
            Log.i("SupabaseRequest", "Request Body: $requestBodyString")
        }
        try {
            client.newCall(request).execute().use { resp ->
                val code = resp.code
                val responseBody = resp.body?.string() ?: ""
                Log.i("SupabaseRequest", "--- SUPABASE RESPONSE ---")
                Log.i("SupabaseRequest", "Status Code: $code")
                Log.i("SupabaseRequest", "Response Body: $responseBody")
                if (!resp.isSuccessful) {
                    throw Exception("Supabase request failed with code $code: $responseBody")
                }
                return responseBody
            }
        } catch (e: Exception) {
            Log.e("SupabaseRequest", "Exception in Supabase Request", e)
            throw e
        }
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
        val creds = R2SupabaseManager.getCredentials(context)
        if (creds.supabaseUrl.isBlank() || creds.supabaseAnonKey.isBlank()) {
            throw Exception("Supabase credentials are not configured!")
        }
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
            val jsonStr = json.toString()
            val body = jsonStr.toRequestBody("application/json".toMediaTypeOrNull())
            val req = okhttp3.Request.Builder()
                .url("$baseUrl/rest/v1/${module.folderTable}")
                .addHeader("apikey", creds.supabaseAnonKey)
                .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
                .addHeader("Prefer", "return=representation")
                .post(body)
                .build()

            val respBody = executeSupabaseRequest(req, jsonStr)
            val arr = org.json.JSONArray(respBody)
            var remoteId = folder.id
            if (arr.length() > 0) {
                val returnedObj = arr.getJSONObject(0)
                remoteId = returnedObj.optLong("id")
            }

            when (module) {
                FolderModule.SYLLABUS -> {
                    academyDao.insertSyllabusFolder(
                        SyllabusFolderEntity(remoteId, folder.parentId, folder.courseId, folder.name, folder.imageUrl, folder.createdAt)
                    )
                }
                FolderModule.PREVIOUS_PAPERS -> {
                    academyDao.insertPreviousPaperFolder(
                        PreviousPaperFolderEntity(remoteId, folder.parentId, folder.name, folder.imageUrl, folder.createdAt)
                    )
                }
                FolderModule.FREE_BOOKS -> {
                    academyDao.insertFreeBookFolder(
                        FreeBookFolderEntity(remoteId, folder.parentId, folder.name, folder.imageUrl, folder.createdAt)
                    )
                }
            }
            true
        } catch (e: Exception) {
            Log.e("AcademyRepository", "insertFolder error: ${e.message}", e)
            throw e
        }
    }

    suspend fun updateFolder(module: FolderModule, folder: FolderItem): Boolean = withContext(Dispatchers.IO) {
        val creds = R2SupabaseManager.getCredentials(context)
        if (creds.supabaseUrl.isBlank() || creds.supabaseAnonKey.isBlank()) {
            throw Exception("Supabase credentials are not configured!")
        }
        val baseUrl = creds.cleanBaseUrl.trimEnd('/')

        val json = JSONObject().apply {
            put("parent_id", folder.parentId ?: JSONObject.NULL)
            put("name", folder.name)
            put("image_url", folder.imageUrl)
        }

        try {
            val jsonStr = json.toString()
            val body = jsonStr.toRequestBody("application/json".toMediaTypeOrNull())
            val req = okhttp3.Request.Builder()
                .url("$baseUrl/rest/v1/${module.folderTable}?id=eq.${folder.id}")
                .addHeader("apikey", creds.supabaseAnonKey)
                .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
                .patch(body)
                .build()

            executeSupabaseRequest(req, jsonStr)

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
            true
        } catch (e: Exception) {
            Log.e("AcademyRepository", "updateFolder error: ${e.message}", e)
            throw e
        }
    }

    suspend fun deleteFolder(module: FolderModule, folderId: Long): Boolean = withContext(Dispatchers.IO) {
        val creds = R2SupabaseManager.getCredentials(context)
        if (creds.supabaseUrl.isBlank() || creds.supabaseAnonKey.isBlank()) {
            throw Exception("Supabase credentials are not configured!")
        }
        val baseUrl = creds.cleanBaseUrl.trimEnd('/')

        try {
            val req = okhttp3.Request.Builder()
                .url("$baseUrl/rest/v1/${module.folderTable}?id=eq.$folderId")
                .addHeader("apikey", creds.supabaseAnonKey)
                .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
                .delete()
                .build()

            executeSupabaseRequest(req, null)

            when (module) {
                FolderModule.SYLLABUS -> academyDao.deleteSyllabusFolderById(folderId)
                FolderModule.PREVIOUS_PAPERS -> academyDao.deletePreviousPaperFolderById(folderId)
                FolderModule.FREE_BOOKS -> academyDao.deleteFreeBookFolderById(folderId)
            }
            true
        } catch (e: Exception) {
            Log.e("AcademyRepository", "deleteFolder error: ${e.message}", e)
            throw e
        }
    }

    suspend fun insertFile(module: FolderModule, file: FileItem): Boolean = withContext(Dispatchers.IO) {
        val creds = R2SupabaseManager.getCredentials(context)
        if (creds.supabaseUrl.isBlank() || creds.supabaseAnonKey.isBlank()) {
            throw Exception("Supabase credentials are not configured!")
        }
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
            val jsonStr = json.toString()
            val body = jsonStr.toRequestBody("application/json".toMediaTypeOrNull())
            val req = okhttp3.Request.Builder()
                .url("$baseUrl/rest/v1/${module.fileTable}")
                .addHeader("apikey", creds.supabaseAnonKey)
                .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
                .addHeader("Prefer", "return=representation")
                .post(body)
                .build()

            val respBody = executeSupabaseRequest(req, jsonStr)
            val arr = org.json.JSONArray(respBody)
            var remoteId = file.id
            if (arr.length() > 0) {
                val returnedObj = arr.getJSONObject(0)
                remoteId = returnedObj.optLong("id")
            }

            when (module) {
                FolderModule.SYLLABUS -> {
                    academyDao.insertSyllabusFile(
                        SyllabusFileEntity(remoteId, file.folderId, file.courseId, file.fileName, file.fileType, file.storageUrl, file.fileSize, file.createdAt)
                    )
                }
                FolderModule.PREVIOUS_PAPERS -> {
                    academyDao.insertPreviousPaperFile(
                        PreviousPaperFileEntity(remoteId, file.folderId, file.fileName, file.fileType, file.storageUrl, file.fileSize, file.createdAt)
                    )
                }
                FolderModule.FREE_BOOKS -> {
                    academyDao.insertFreeBookFile(
                        FreeBookFileEntity(remoteId, file.folderId, file.fileName, file.fileType, file.storageUrl, file.fileSize, file.createdAt)
                    )
                }
            }
            true
        } catch (e: Exception) {
            Log.e("AcademyRepository", "insertFile error: ${e.message}", e)
            throw e
        }
    }

    suspend fun updateFile(module: FolderModule, file: FileItem): Boolean = withContext(Dispatchers.IO) {
        val creds = R2SupabaseManager.getCredentials(context)
        if (creds.supabaseUrl.isBlank() || creds.supabaseAnonKey.isBlank()) {
            throw Exception("Supabase credentials are not configured!")
        }
        val baseUrl = creds.cleanBaseUrl.trimEnd('/')

        val json = JSONObject().apply {
            put("folder_id", file.folderId)
            put("file_name", file.fileName)
            put("storage_url", file.storageUrl)
            put("file_size", file.fileSize)
        }

        try {
            val jsonStr = json.toString()
            val body = jsonStr.toRequestBody("application/json".toMediaTypeOrNull())
            val req = okhttp3.Request.Builder()
                .url("$baseUrl/rest/v1/${module.fileTable}?id=eq.${file.id}")
                .addHeader("apikey", creds.supabaseAnonKey)
                .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
                .patch(body)
                .build()

            executeSupabaseRequest(req, jsonStr)

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
            true
        } catch (e: Exception) {
            Log.e("AcademyRepository", "updateFile error: ${e.message}", e)
            throw e
        }
    }

    suspend fun deleteFile(module: FolderModule, fileId: Long): Boolean = withContext(Dispatchers.IO) {
        val creds = R2SupabaseManager.getCredentials(context)
        if (creds.supabaseUrl.isBlank() || creds.supabaseAnonKey.isBlank()) {
            throw Exception("Supabase credentials are not configured!")
        }
        val baseUrl = creds.cleanBaseUrl.trimEnd('/')

        try {
            val req = okhttp3.Request.Builder()
                .url("$baseUrl/rest/v1/${module.fileTable}?id=eq.$fileId")
                .addHeader("apikey", creds.supabaseAnonKey)
                .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
                .delete()
                .build()

            executeSupabaseRequest(req, null)

            when (module) {
                FolderModule.SYLLABUS -> academyDao.deleteSyllabusFileById(fileId)
                FolderModule.PREVIOUS_PAPERS -> academyDao.deletePreviousPaperFileById(fileId)
                FolderModule.FREE_BOOKS -> academyDao.deleteFreeBookFileById(fileId)
            }
            true
        } catch (e: Exception) {
            Log.e("AcademyRepository", "deleteFile error: ${e.message}", e)
            throw e
        }
    }

    // --- Supabase Authentication & Profile Management ---

    suspend fun sendOtp(phone: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = mapOf("phone" to phone)
            val response = getApi().sendOtp(body)
            response.isSuccessful
        } catch (e: Exception) {
            Log.e("AcademyRepository", "sendOtp error: ${e.message}", e)
            false
        }
    }

    suspend fun verifyOtp(phone: String, token: String): SupabaseSession = withContext(Dispatchers.IO) {
        val body = mapOf(
            "type" to "sms",
            "phone" to phone,
            "token" to token
        )
        getApi().verifyOtp(body)
    }

    suspend fun verifyGoogleIdToken(idToken: String): SupabaseSession = withContext(Dispatchers.IO) {
        val body = mapOf(
            "provider" to "google",
            "id_token" to idToken
        )
        getApi().verifyGoogleIdToken(body)
    }

    suspend fun getProfileById(id: String): ProfileEntity? = withContext(Dispatchers.IO) {
        try {
            val list = getApi().getProfileById("eq.$id")
            list.firstOrNull()
        } catch (e: Exception) {
            Log.e("AcademyRepository", "getProfileById error: ${e.message}", e)
            null
        }
    }

    suspend fun checkAnyProfilesExist(): Boolean = withContext(Dispatchers.IO) {
        try {
            val list = getApi().checkAnyProfilesExist()
            list.isNotEmpty()
        } catch (e: Exception) {
            Log.e("AcademyRepository", "checkAnyProfilesExist error: ${e.message}", e)
            false
        }
    }

    suspend fun insertProfile(profile: ProfileEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = getApi().insertProfile(profile)
            response.isSuccessful
        } catch (e: Exception) {
            Log.e("AcademyRepository", "insertProfile error: ${e.message}", e)
            false
        }
    }

    suspend fun updateProfile(id: String, updates: Map<String, String>): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = getApi().updateProfile("eq.$id", updates)
            response.isSuccessful
        } catch (e: Exception) {
            Log.e("AcademyRepository", "updateProfile error: ${e.message}", e)
            false
        }
    }

    suspend fun signUpWithEmail(email: String, password: String, metadata: Map<String, String>): retrofit2.Response<okhttp3.ResponseBody> = withContext(Dispatchers.IO) {
        val request = SignUpRequest(
            email = email,
            password = password,
            data = metadata
        )
        getApi().signUpWithEmail(request)
    }

    suspend fun signInWithPassword(email: String, password: String): retrofit2.Response<okhttp3.ResponseBody> = withContext(Dispatchers.IO) {
        val body = mapOf(
            "email" to email,
            "password" to password
        )
        getApi().signInWithPassword(body)
    }

    suspend fun recoverPassword(email: String): retrofit2.Response<okhttp3.ResponseBody> = withContext(Dispatchers.IO) {
        val body = mapOf(
            "email" to email
        )
        getApi().recoverPassword(body)
    }

    // === Community Popup ===
    fun getCommunityPopupLocal(): CommunityPopupEntity {
        val prefs = context.getSharedPreferences("community_popup_prefs", Context.MODE_PRIVATE)
        val defaultId = "00000000-0000-0000-0000-000000000001"
        return CommunityPopupEntity(
            id = prefs.getString("id", defaultId) ?: defaultId,
            title = prefs.getString("title", "SHADOWXRAHUL") ?: "SHADOWXRAHUL",
            description = prefs.getString("description", "Join our Official Community to receive the latest updates, study materials, notices, announcements, and important information.") ?: "Join our Official Community to receive the latest updates, study materials, notices, announcements, and important information.",
            imageUrl = prefs.getString("image_url", "") ?: "",
            whatsappUrl = prefs.getString("whatsapp_url", "") ?: "",
            telegramUrl = prefs.getString("telegram_url", "") ?: "",
            enabled = prefs.getBoolean("enabled", true)
        )
    }

    fun saveCommunityPopupLocal(entity: CommunityPopupEntity) {
        context.getSharedPreferences("community_popup_prefs", Context.MODE_PRIVATE).edit()
            .putString("id", entity.id)
            .putString("title", entity.title)
            .putString("description", entity.description)
            .putString("image_url", entity.imageUrl)
            .putString("whatsapp_url", entity.whatsappUrl)
            .putString("telegram_url", entity.telegramUrl)
            .putBoolean("enabled", entity.enabled)
            .apply()
    }

    suspend fun getCommunityPopupFromSupabase(): CommunityPopupEntity? = withContext(Dispatchers.IO) {
        try {
            val list = getApi().getCommunityPopup()
            if (list.isNotEmpty()) {
                val config = list.first()
                saveCommunityPopupLocal(config)
                config
            } else {
                getCommunityPopupLocal()
            }
        } catch (e: Exception) {
            Log.e("AcademyRepository", "Failed to fetch community_popup from Supabase: ${e.message}")
            getCommunityPopupLocal()
        }
    }

    suspend fun saveCommunityPopup(entity: CommunityPopupEntity): Boolean = withContext(Dispatchers.IO) {
        saveCommunityPopupLocal(entity)
        syncWithSupabaseAwait { api ->
            val existing = try { api.getCommunityPopup() } catch(e: Exception) { emptyList() }
            val targetId = if (existing.isNotEmpty()) existing.first().id else entity.id
            val finalEntity = entity.copy(id = targetId)
            val rb = toRequestBody(finalEntity)
            if (existing.isNotEmpty()) {
                api.updateCommunityPopup("eq.$targetId", rb)
            } else {
                api.insertCommunityPopup(rb)
            }
        }
    }
}

