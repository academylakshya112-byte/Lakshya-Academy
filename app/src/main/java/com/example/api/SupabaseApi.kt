package com.example.api

import com.example.data.*
import retrofit2.http.*
import okhttp3.RequestBody

interface SupabaseApi {
    @GET("rest/v1/courses?select=*&order=id.desc")
    suspend fun getAllCourses(): List<CourseEntity>
    @GET("rest/v1/courses?select=*")
    suspend fun getCourseById(@Query("id") id: String): List<CourseEntity>
    @POST("rest/v1/courses")
    suspend fun insertCourse(@Body body: RequestBody): List<CourseEntity>
    @DELETE("rest/v1/courses")
    suspend fun deleteCourseById(@Query("id") id: String)
    @PATCH("rest/v1/courses")
    suspend fun updateCourse(@Query("id") id: String, @Body body: RequestBody)

        @GET("rest/v1/lessons?select=*")
    suspend fun getAllLessons(): List<LessonEntity>
    @GET("rest/v1/lessons?select=*&order=id.asc")
    suspend fun getLessonsForCourse(@Query("courseId") courseId: String): List<LessonEntity>
    @POST("rest/v1/lessons")
    suspend fun insertLesson(@Body body: RequestBody)
    @DELETE("rest/v1/lessons")
    suspend fun deleteLessonById(@Query("id") id: String)

    @GET("rest/v1/enrollments?select=*")
    suspend fun getAllEnrollments(): List<EnrollmentEntity>
    @GET("rest/v1/enrollments?select=*")
    suspend fun getEnrollmentsForUser(@Query("userEmail") email: String): List<EnrollmentEntity>
    @POST("rest/v1/enrollments")
    suspend fun insertEnrollment(@Body body: RequestBody)
    @PATCH("rest/v1/enrollments")
    suspend fun updateEnrollment(@Query("id") id: String, @Body body: RequestBody)

    @GET("rest/v1/tests?select=*&order=id.desc")
    suspend fun getAllTests(): List<TestEntity>
    @GET("rest/v1/tests?select=*")
    suspend fun getTestByTitle(@Query("title") titleFilter: String): List<TestEntity>
    @POST("rest/v1/tests")
    suspend fun insertTest(@Body body: RequestBody): List<TestEntity>
    @PATCH("rest/v1/tests")
    suspend fun updateTest(@Query("id") id: String, @Body body: RequestBody)
    @DELETE("rest/v1/tests")
    suspend fun deleteTestById(@Query("id") id: String)
    @DELETE("rest/v1/tests")
    suspend fun deleteAllTests()

        @GET("rest/v1/questions?select=*")
    suspend fun getAllQuestions(): List<QuestionEntity>
    @GET("rest/v1/questions?select=*&order=id.asc")
    suspend fun getQuestionsForTest(@Query("testId") testId: String): List<QuestionEntity>
    @POST("rest/v1/questions")
    suspend fun insertQuestion(@Body body: RequestBody)
    @DELETE("rest/v1/questions")
    suspend fun deleteQuestionById(@Query("id") id: String)
    @DELETE("rest/v1/questions")
    suspend fun deleteQuestionsForTest(@Query("testId") testId: String)
    @DELETE("rest/v1/questions")
    suspend fun deleteAllQuestions()

    @GET("rest/v1/test_scores?select=*&order=timestamp.desc")
    suspend fun getAllScores(): List<TestScoreEntity>
    @GET("rest/v1/test_scores?select=*&order=timestamp.desc")
    suspend fun getScoresForUser(@Query("userEmail") email: String): List<TestScoreEntity>
    @POST("rest/v1/test_scores")
    suspend fun insertScore(@Body body: RequestBody)

    @GET("rest/v1/doubts?select=*&order=id.desc")
    suspend fun getAllDoubts(): List<DoubtEntity>
    @POST("rest/v1/doubts")
    suspend fun insertDoubt(@Body body: RequestBody)
    @PATCH("rest/v1/doubts")
    suspend fun updateDoubt(@Query("id") id: String, @Body body: RequestBody)

    @GET("rest/v1/chat_messages?select=*&order=timestamp.asc")
    suspend fun getAllChatMessages(): List<ChatMessageEntity>
    @POST("rest/v1/chat_messages")
    suspend fun insertChatMessage(@Body body: RequestBody)

    @GET("rest/v1/notifications?select=*&order=timestamp.desc")
    suspend fun getAllNotifications(): List<NotificationEntity>
    @POST("rest/v1/notifications")
    suspend fun insertNotification(@Body body: RequestBody)

        @GET("rest/v1/materials?select=*")
    suspend fun getAllMaterials(): List<MaterialEntity>
    @GET("rest/v1/materials?select=*&order=uploadDate.desc")
    suspend fun getMaterialsByType(@Query("type") type: String): List<MaterialEntity>
    @POST("rest/v1/materials")
    suspend fun insertMaterial(@Body body: RequestBody)
    @DELETE("rest/v1/materials")
    suspend fun deleteMaterialById(@Query("id") id: String)

    @GET("rest/v1/banners?select=*&order=id.desc")
    suspend fun getAllBanners(): List<BannerEntity>
    @POST("rest/v1/banners")
    suspend fun insertBanner(@Body body: RequestBody)
    @PATCH("rest/v1/banners")
    suspend fun updateBanner(@Query("id") id: String, @Body body: RequestBody)
    @DELETE("rest/v1/banners")
    suspend fun deleteBannerById(@Query("id") id: String)

    @GET("rest/v1/study_websites?select=*&order=id.desc")
    suspend fun getAllStudyWebsites(): List<StudyWebsiteEntity>
    @POST("rest/v1/study_websites")
    suspend fun insertStudyWebsite(@Body body: RequestBody): retrofit2.Response<okhttp3.ResponseBody>
    @Headers("Prefer: return=representation", "Content-Type: application/json")
    @PATCH("rest/v1/study_websites")
    suspend fun updateStudyWebsite(@Query("id") id: String, @Body body: RequestBody): retrofit2.Response<okhttp3.ResponseBody>
    @Headers("Prefer: return=representation")
    @DELETE("rest/v1/study_websites")
    suspend fun deleteStudyWebsiteById(@Query("id") id: String): retrofit2.Response<okhttp3.ResponseBody>

    @GET("rest/v1/live_classes?select=*&order=id.desc")
    suspend fun getAllLiveClasses(): List<SupabaseLiveClassDto>

    @Headers("Prefer: return=representation", "Content-Type: application/json")
    @POST("rest/v1/live_classes")
    suspend fun insertLiveClass(@Body body: RequestBody): retrofit2.Response<okhttp3.ResponseBody>

    @Headers("Prefer: return=representation", "Content-Type: application/json")
    @PATCH("rest/v1/live_classes")
    suspend fun updateLiveClass(@Query("id") id: String, @Body body: RequestBody): retrofit2.Response<okhttp3.ResponseBody>

    @Headers("Prefer: return=representation")
    @DELETE("rest/v1/live_classes")
    suspend fun deleteLiveClassById(@Query("id") id: String): retrofit2.Response<okhttp3.ResponseBody>

    @GET("rest/v1/ai_animation_limits?select=*")
    suspend fun getAnimationLimit(@Query("userEmail") email: String): List<AiAnimationLimitEntity>
    @POST("rest/v1/ai_animation_limits")
    suspend fun insertAnimationLimit(@Body body: RequestBody)

    @GET("rest/v1/ai_video_limits?select=*")
    suspend fun getVideoLimit(@Query("userEmail") email: String): List<AiVideoLimitEntity>
    @POST("rest/v1/ai_video_limits")
    suspend fun insertVideoLimit(@Body body: RequestBody)

    @GET("rest/v1/video_views?select=*")
    suspend fun getVideoViewsForVideo(@Query("video_id") videoId: String): List<VideoViewDto>

    @GET("rest/v1/video_views?select=*")
    suspend fun checkVideoView(
        @Query("video_id") videoId: String,
        @Query("user_id") userId: String
    ): List<VideoViewDto>

    @POST("rest/v1/video_views")
    suspend fun insertVideoView(@Body body: RequestBody): List<VideoViewDto>

    @GET("rest/v1/app_update?select=*&order=id.desc")
    suspend fun getAppUpdates(): List<AppUpdateEntity>

    // --- Supabase GoTrue Auth Endpoints ---
    @POST("auth/v1/otp")
    suspend fun sendOtp(@Body body: Map<String, String>): retrofit2.Response<Unit>

    @POST("auth/v1/verify")
    suspend fun verifyOtp(@Body body: Map<String, String>): SupabaseSession

    @POST("auth/v1/token?grant_type=id_token")
    suspend fun verifyGoogleIdToken(@Body body: Map<String, String>): SupabaseSession

    @POST("auth/v1/signup")
    suspend fun signUpWithEmail(@Body body: SignUpRequest): retrofit2.Response<okhttp3.ResponseBody>

    @POST("auth/v1/token?grant_type=password")
    suspend fun signInWithPassword(@Body body: Map<String, String>): retrofit2.Response<okhttp3.ResponseBody>

    @POST("auth/v1/recover")
    suspend fun recoverPassword(@Body body: Map<String, String>): retrofit2.Response<okhttp3.ResponseBody>

    // --- Supabase Profiles Table Endpoints ---
    @GET("rest/v1/profiles?select=*")
    suspend fun getProfileById(@Query("id") idFilter: String): List<com.example.data.ProfileEntity>

    @GET("rest/v1/profiles?select=id")
    suspend fun checkAnyProfilesExist(): List<Map<String, Any>>

    @POST("rest/v1/profiles")
    suspend fun insertProfile(@Body profile: com.example.data.ProfileEntity): retrofit2.Response<Unit>

    @PATCH("rest/v1/profiles")
    suspend fun updateProfile(
        @Query("id") idFilter: String,
        @Body updates: Map<String, String>
    ): retrofit2.Response<Unit>

    // --- Community Popup Endpoints ---
    @GET("rest/v1/community_popup?select=*&limit=1")
    suspend fun getCommunityPopup(): List<com.example.data.CommunityPopupEntity>

    @Headers("Prefer: return=representation", "Content-Type: application/json")
    @POST("rest/v1/community_popup")
    suspend fun insertCommunityPopup(@Body body: RequestBody): retrofit2.Response<okhttp3.ResponseBody>

    @Headers("Prefer: return=representation", "Content-Type: application/json")
    @PATCH("rest/v1/community_popup")
    suspend fun updateCommunityPopup(@Query("id") idFilter: String, @Body body: RequestBody): retrofit2.Response<okhttp3.ResponseBody>

    // --- Study Progress Endpoints ---
    @GET("rest/v1/study_progress?select=*")
    suspend fun getStudyProgressForUser(
        @Query("user_email") userEmailFilter: String
    ): List<com.example.data.StudyProgressDto>

    @GET("rest/v1/study_progress?select=*")
    suspend fun getStudyProgressForUserAndDate(
        @Query("user_email") userEmailFilter: String,
        @Query("date") dateFilter: String
    ): List<com.example.data.StudyProgressDto>

    @Headers("Prefer: return=representation", "Content-Type: application/json")
    @POST("rest/v1/study_progress")
    suspend fun insertStudyProgress(@Body body: RequestBody): retrofit2.Response<okhttp3.ResponseBody>

    @Headers("Prefer: return=representation", "Content-Type: application/json")
    @PATCH("rest/v1/study_progress")
    suspend fun updateStudyProgress(
        @Query("user_email") userEmailFilter: String,
        @Query("date") dateFilter: String,
        @Body body: RequestBody
    ): retrofit2.Response<okhttp3.ResponseBody>
}

