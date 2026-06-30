package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AcademyDao {
    // === Courses ===
    @Query("SELECT * FROM courses ORDER BY id DESC")
    fun getAllCourses(): Flow<List<CourseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: CourseEntity): Long

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deleteCourseById(id: Int)

    @Update
    suspend fun updateCourse(course: CourseEntity)

    // === Lessons ===
    @Query("SELECT * FROM lessons WHERE courseId = :courseId ORDER BY id ASC")
    fun getLessonsForCourse(courseId: Int): Flow<List<LessonEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLesson(lesson: LessonEntity)

    @Query("DELETE FROM lessons WHERE id = :id")
    suspend fun deleteLessonById(id: Int)

    // === Enrollments ===
    @Query("SELECT * FROM enrollments")
    fun getAllEnrollments(): Flow<List<EnrollmentEntity>>

    @Query("SELECT * FROM enrollments WHERE userEmail = :email")
    fun getEnrollmentsForUser(email: String): Flow<List<EnrollmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEnrollment(enrollment: EnrollmentEntity)

    @Update
    suspend fun updateEnrollment(enrollment: EnrollmentEntity)

    // === Tests ===
    @Query("SELECT * FROM tests ORDER BY id DESC")
    fun getAllTests(): Flow<List<TestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTest(test: TestEntity): Long

    @Query("DELETE FROM tests WHERE id = :id")
    suspend fun deleteTestById(id: Int)

    @Query("DELETE FROM tests")
    suspend fun deleteAllTests()

    // === Questions ===
    @Query("SELECT * FROM questions WHERE testId = :testId ORDER BY id ASC")
    fun getQuestionsForTest(testId: Int): Flow<List<QuestionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestion(question: QuestionEntity)

    @Query("DELETE FROM questions WHERE id = :id")
    suspend fun deleteQuestionById(id: Int)

    @Query("DELETE FROM questions")
    suspend fun deleteAllQuestions()

    // === Test Scores ===
    @Query("SELECT * FROM test_scores ORDER BY timestamp DESC")
    fun getAllScores(): Flow<List<TestScoreEntity>>

    @Query("SELECT * FROM test_scores WHERE userEmail = :email ORDER BY timestamp DESC")
    fun getScoresForUser(email: String): Flow<List<TestScoreEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScore(score: TestScoreEntity)

    // === Doubts ===
    @Query("SELECT * FROM doubts ORDER BY id DESC")
    fun getAllDoubts(): Flow<List<DoubtEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoubt(doubt: DoubtEntity)

    @Update
    suspend fun updateDoubt(doubt: DoubtEntity)

    // === Chat Messages ===
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllChatMessages(): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: ChatMessageEntity)

    // === Notifications ===
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    // === Materials ===
    @Query("SELECT * FROM materials WHERE type = :type ORDER BY uploadDate DESC")
    fun getMaterialsByType(type: String): Flow<List<MaterialEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaterial(material: MaterialEntity)

    @Query("DELETE FROM materials WHERE id = :id")
    suspend fun deleteMaterialById(id: Int)

    // === Banners ===
    @Query("SELECT * FROM banners ORDER BY id DESC")
    fun getAllBanners(): Flow<List<BannerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBanner(banner: BannerEntity)

    @Query("DELETE FROM banners WHERE id = :id")
    suspend fun deleteBannerById(id: Int)

    // === Live Classes ===
    @Query("SELECT * FROM live_classes ORDER BY scheduledTime ASC")
    fun getAllLiveClasses(): Flow<List<LiveClassEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLiveClass(liveClass: LiveClassEntity)

    @Query("DELETE FROM live_classes WHERE id = :id")
    suspend fun deleteLiveClassById(id: Int)

    // === AI Animation Limits ===
    @Query("SELECT * FROM ai_animation_limits WHERE userEmail = :email")
    suspend fun getAnimationLimit(email: String): AiAnimationLimitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnimationLimit(limit: AiAnimationLimitEntity)

    // === AI Video Limits ===
    @Query("SELECT * FROM ai_video_limits WHERE userEmail = :email")
    suspend fun getVideoLimit(email: String): AiVideoLimitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideoLimit(limit: AiVideoLimitEntity)
}

@Database(
    entities = [
        CourseEntity::class,
        LessonEntity::class,
        EnrollmentEntity::class,
        TestEntity::class,
        QuestionEntity::class,
        TestScoreEntity::class,
        DoubtEntity::class,
        ChatMessageEntity::class,
        NotificationEntity::class,
        MaterialEntity::class,
        BannerEntity::class,
        LiveClassEntity::class,
        AiAnimationLimitEntity::class,
        AiVideoLimitEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AcademyDatabase : RoomDatabase() {
    abstract fun academyDao(): AcademyDao

    companion object {
        @Volatile
        private var INSTANCE: AcademyDatabase? = null

        fun getDatabase(context: Context): AcademyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AcademyDatabase::class.java,
                    "academy_database"
                )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                @Suppress("UpdateOfToValue")
                INSTANCE = instance
                instance
            }
        }
    }
}
