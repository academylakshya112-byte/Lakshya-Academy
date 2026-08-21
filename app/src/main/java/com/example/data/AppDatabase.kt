package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AcademyDao {
    // === Courses ===
    @Query("SELECT * FROM courses ORDER BY id DESC")
    fun getAllCourses(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses ORDER BY id DESC")
    suspend fun getAllCoursesDirect(): List<CourseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: CourseEntity): Long

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deleteCourseById(id: Int)

    @Query("DELETE FROM courses")
    suspend fun deleteAllCourses()

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

    @Update
    suspend fun updateTest(test: TestEntity)

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

    @Query("DELETE FROM questions WHERE testId = :testId")
    suspend fun deleteQuestionsForTest(testId: Int)

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

    @Query("DELETE FROM banners")
    suspend fun deleteAllBanners()

    // === Live Classes ===
    @Query("SELECT * FROM live_classes ORDER BY id DESC")
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

    // === Syllabus Folders & Files ===
    @Query("SELECT * FROM syllabus_folders ORDER BY id DESC")
    fun getAllSyllabusFolders(): Flow<List<SyllabusFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyllabusFolder(folder: SyllabusFolderEntity): Long

    @Query("DELETE FROM syllabus_folders WHERE id = :id")
    suspend fun deleteSyllabusFolderById(id: Long)

    @Query("SELECT * FROM syllabus_files ORDER BY id DESC")
    fun getAllSyllabusFiles(): Flow<List<SyllabusFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyllabusFile(file: SyllabusFileEntity): Long

    @Query("DELETE FROM syllabus_files WHERE id = :id")
    suspend fun deleteSyllabusFileById(id: Long)

    // === Previous Paper Folders & Files ===
    @Query("SELECT * FROM previous_paper_folders ORDER BY id DESC")
    fun getAllPreviousPaperFolders(): Flow<List<PreviousPaperFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreviousPaperFolder(folder: PreviousPaperFolderEntity): Long

    @Query("DELETE FROM previous_paper_folders WHERE id = :id")
    suspend fun deletePreviousPaperFolderById(id: Long)

    @Query("SELECT * FROM previous_paper_files ORDER BY id DESC")
    fun getAllPreviousPaperFiles(): Flow<List<PreviousPaperFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreviousPaperFile(file: PreviousPaperFileEntity): Long

    @Query("DELETE FROM previous_paper_files WHERE id = :id")
    suspend fun deletePreviousPaperFileById(id: Long)

    // === Free Book Folders & Files ===
    @Query("SELECT * FROM free_book_folders ORDER BY id DESC")
    fun getAllFreeBookFolders(): Flow<List<FreeBookFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFreeBookFolder(folder: FreeBookFolderEntity): Long

    @Query("DELETE FROM free_book_folders WHERE id = :id")
    suspend fun deleteFreeBookFolderById(id: Long)

    @Query("SELECT * FROM free_book_files ORDER BY id DESC")
    fun getAllFreeBookFiles(): Flow<List<FreeBookFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFreeBookFile(file: FreeBookFileEntity): Long

    @Query("DELETE FROM free_book_files WHERE id = :id")
    suspend fun deleteFreeBookFileById(id: Long)

    // === Study Websites ===
    @Query("SELECT * FROM study_websites ORDER BY id DESC")
    fun getAllStudyWebsites(): Flow<List<StudyWebsiteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudyWebsite(website: StudyWebsiteEntity)

    @Query("DELETE FROM study_websites WHERE id = :id")
    suspend fun deleteStudyWebsiteById(id: String)

    @Query("DELETE FROM study_websites")
    suspend fun deleteAllStudyWebsites()

    // === Study Website Favorites ===
    @Query("""
        SELECT w.* FROM study_websites w 
        INNER JOIN study_website_favorites f ON w.id = f.websiteId 
        WHERE f.userEmail = :userEmail 
        ORDER BY f.favoritedAt DESC
    """)
    fun getFavoriteStudyWebsites(userEmail: String): Flow<List<StudyWebsiteEntity>>

    @Query("SELECT websiteId FROM study_website_favorites WHERE userEmail = :userEmail")
    fun getFavoriteWebsiteIds(userEmail: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudyWebsiteFavorite(favorite: StudyWebsiteFavoriteEntity)

    @Query("DELETE FROM study_website_favorites WHERE userEmail = :userEmail AND websiteId = :websiteId")
    suspend fun deleteStudyWebsiteFavorite(userEmail: String, websiteId: String)
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
        AiVideoLimitEntity::class,
        SyllabusFolderEntity::class,
        SyllabusFileEntity::class,
        PreviousPaperFolderEntity::class,
        PreviousPaperFileEntity::class,
        FreeBookFolderEntity::class,
        FreeBookFileEntity::class,
        StudyWebsiteEntity::class,
        StudyWebsiteFavoriteEntity::class
    ],
    version = 21,
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
