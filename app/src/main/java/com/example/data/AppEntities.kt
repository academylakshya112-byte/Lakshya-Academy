package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val category: String, // e.g. "UPSC", "UP Police", "SSC", "NEET"
    val subject: String,
    val description: String,
    val isFree: Boolean,
    val price: Double,
    val totalLessons: Int,
    val imageUrl: String = ""
)

@Entity(tableName = "lessons")
data class LessonEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val courseId: Int,
    val chapterName: String,
    val title: String,
    val videoUrl: String,
    val folder: String = "General", // e.g. "All video", "PDF Notes"
    val pdfUrl: String,
    val pdfName: String,
    val pdfContent: String = "",
    val fileSize: String = "2.5 MB",
    val thumbnailUrl: String = "",
    val videoSourceType: String = "YOUTUBE",
    val youtubeVideoId: String = ""
)

@Entity(tableName = "enrollments")
data class EnrollmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userEmail: String,
    val courseId: Int,
    val completedLessonsCount: Int = 0,
    val isCompleted: Boolean = false,
    val purchaseDate: Long = System.currentTimeMillis()
)

@Entity(tableName = "tests")
data class TestEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val type: String, // "Mock Test", "Weekly Test", "Test Series"
    val durationMinutes: Int,
    val hasNegativeMarking: Boolean = true,
    val marksPerCorrect: Int = 2,
    val marksPerWrong: Float = -0.5f
)

@Entity(tableName = "questions")
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val testId: Int,
    val questionText: String,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val correctIndex: Int // 0 = A, 1 = B, 2 = C, 3 = D
)

@Entity(tableName = "test_scores")
data class TestScoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val testId: Int,
    val testTitle: String,
    val userEmail: String,
    val score: Float,
    val totalQuestions: Int,
    val correctAnswers: Int,
    val wrongAnswers: Int,
    val selectedAnswersJson: String = "{}", // Map of questionId -> selectedIndex
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "doubts")
data class DoubtEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userEmail: String,
    val userName: String,
    val subject: String,
    val questionText: String,
    val replyText: String = "",
    val answeredBy: String = ""
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val senderName: String,
    val senderEmail: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isAdminReply: Boolean = false
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "materials")
data class MaterialEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // "Book", "Syllabus", "Timetable", "Previous Year Paper", "Current Affairs"
    val title: String,
    val description: String = "",
    val fileSize: String = "1.5 MB",
    val uploadDate: Long = System.currentTimeMillis(),
    val fileContent: String = ""
)

// ... (previous content)
@Entity(tableName = "banners")
data class BannerEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val imageUrl: String = "",
    val linkUrl: String = "",
    val buttonText: String = "VIEW",
    val description: String = "",
    val isActive: Boolean = true
)

@Entity(tableName = "live_classes")
data class LiveClassEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val subject: String,
    val teacherName: String,
    val thumbnailUri: String = "",
    val isLive: Boolean = false,
    val scheduledTime: Long = 0,
    val recordingUri: String = ""
)

@Entity(tableName = "ai_animation_limits")
data class AiAnimationLimitEntity(
    @PrimaryKey val userEmail: String,
    val count: Int = 5,
    val weekOfYear: Int = -1,
    val year: Int = -1
)

@Entity(tableName = "ai_video_limits")
data class AiVideoLimitEntity(
    @PrimaryKey val userEmail: String,
    val count: Int = 0,
    val weekOfYear: Int = -1,
    val year: Int = -1
)
