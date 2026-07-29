package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.Json

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @Json(name = "title") val title: String,
    @Json(name = "category") val category: String, // e.g. "UPSC", "UP Police", "SSC", "NEET"
    @Json(name = "subject") val subject: String,
    @Json(name = "description") val description: String,
    @Json(name = "isFree") val isFree: Boolean,
    @Json(name = "price") val price: Double,
    @Json(name = "totalLessons") val totalLessons: Int,
    @Json(name = "imageUrl") val imageUrl: String = ""
)

@Entity(tableName = "lessons")
data class LessonEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @Json(name = "course_id") val courseId: Int,
    @Json(name = "chapter_name") val chapterName: String,
    val title: String,
    @Json(name = "video_url") val videoUrl: String,
    val folder: String = "General", // e.g. "All video", "PDF Notes"
    @Json(name = "pdf_url") val pdfUrl: String,
    @Json(name = "pdf_name") val pdfName: String,
    @Json(name = "pdf_content") val pdfContent: String = "",
    @Json(name = "file_size") val fileSize: String = "2.5 MB",
    @Json(name = "thumbnail_url") val thumbnailUrl: String = "",
    @Json(name = "video_source_type") val videoSourceType: String = "YOUTUBE",
    @Json(name = "youtube_video_id") val youtubeVideoId: String = ""
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
    @Json(name = "imageUrl") val imageUrl: String = "",
    @Json(name = "button_url") val linkUrl: String = "",
    @Json(name = "button_text") val buttonText: String = "VIEW",
    val description: String = "",
    @Json(name = "is_active") val isActive: Boolean = true,
    @Json(name = "display_order") val displayOrder: Int = 0,
    @Json(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @Json(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "live_classes")
data class LiveClassEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String = "",
    val description: String = "",
    @Json(name = "teacher_name") val teacherName: String = "",
    val subject: String = "",
    val chapter: String = "",
    @Json(name = "thumbnail_url") val thumbnailUrl: String = "",
    @Json(name = "youtube_live_id") val youtubeLiveId: String = "",
    @Json(name = "youtube_url") val youtubeUrl: String = "",
    @Json(name = "thumbnail") val thumbnail: String = "",
    val status: String = "Scheduled", // "Scheduled", "Live", "Ended"
    @Json(name = "scheduled_date") val scheduledDate: String = "",
    @Json(name = "scheduled_time") val scheduledTime: String = "",
    @Json(name = "start_time") val startTime: String = "",
    @Json(name = "end_time") val endTime: String = "",
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null,
    
    // Backwards compatibility with legacy code or local UI references
    val thumbnailUri: String = "",
    val isLive: Boolean = false,
    val recordingUri: String = ""
) {
    val effectiveYoutubeId: String
        get() {
            if (youtubeLiveId.isNotBlank()) return parseYtId(youtubeLiveId)
            if (youtubeUrl.isNotBlank()) return parseYtId(youtubeUrl)
            if (recordingUri.isNotBlank()) return parseYtId(recordingUri)
            return ""
        }

    val effectiveThumbnailUrl: String
        get() {
            if (thumbnailUrl.isNotBlank()) return thumbnailUrl
            if (thumbnail.isNotBlank()) return thumbnail
            val ytId = effectiveYoutubeId
            if (ytId.isNotBlank()) return "https://img.youtube.com/vi/$ytId/hqdefault.jpg"
            return ""
        }
}

private fun parseYtId(input: String): String {
    val trimmed = input.trim()
    if (trimmed.length == 11 && !trimmed.contains("/") && !trimmed.contains("?") && !trimmed.contains(".") && !trimmed.contains(":")) {
        return trimmed
    }
    val pattern = "(?:youtube\\.com\\/(?:[^\\/]+\\/.+\\/|(?:v|e(?:mbed)?|live)\\/" +
            "|.*[?&]v=)|youtu\\.be\\/)([^\"&?\\/\\s]{11})"
    val matcher = java.util.regex.Pattern.compile(pattern).matcher(trimmed)
    if (matcher.find()) {
        return matcher.group(1) ?: trimmed
    }
    return trimmed
}

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

// === Folder Management System Entities ===

enum class FolderModule(val folderTable: String, val fileTable: String, val titleName: String) {
    SYLLABUS("syllabus_folders", "syllabus_files", "Course Syllabus"),
    PREVIOUS_PAPERS("previous_paper_folders", "previous_paper_files", "Previous Papers"),
    FREE_BOOKS("free_book_folders", "free_book_files", "Free Books")
}

data class FolderItem(
    val id: Long = 0,
    val parentId: Long? = null,
    val courseId: String? = null,
    val name: String,
    val imageUrl: String = "",
    val createdAt: String = System.currentTimeMillis().toString()
)

data class FileItem(
    val id: Long = 0,
    val folderId: Long,
    val courseId: String? = null,
    val fileName: String,
    val fileType: String, // "pdf", "image"
    val storageUrl: String,
    val fileSize: String = "",
    val createdAt: String = System.currentTimeMillis().toString()
)

// Course Syllabus
@Entity(tableName = "syllabus_folders")
data class SyllabusFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Json(name = "parent_id") val parent_id: Long? = null,
    @Json(name = "course_id") val course_id: String? = null,
    @Json(name = "name") val name: String,
    @Json(name = "image_url") val image_url: String = "",
    @Json(name = "created_at") val created_at: String = System.currentTimeMillis().toString()
) {
    fun toFolderItem() = FolderItem(id, parent_id, course_id, name, image_url, created_at)
}

@Entity(tableName = "syllabus_files")
data class SyllabusFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Json(name = "folder_id") val folder_id: Long,
    @Json(name = "course_id") val course_id: String? = null,
    @Json(name = "file_name") val file_name: String,
    @Json(name = "file_type") val file_type: String,
    @Json(name = "storage_url") val storage_url: String,
    @Json(name = "file_size") val file_size: String = "",
    @Json(name = "created_at") val created_at: String = System.currentTimeMillis().toString()
) {
    fun toFileItem() = FileItem(id, folder_id, course_id, file_name, file_type, storage_url, file_size, created_at)
}

// Previous Papers
@Entity(tableName = "previous_paper_folders")
data class PreviousPaperFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Json(name = "parent_id") val parent_id: Long? = null,
    @Json(name = "name") val name: String,
    @Json(name = "image_url") val image_url: String = "",
    @Json(name = "created_at") val created_at: String = System.currentTimeMillis().toString()
) {
    fun toFolderItem() = FolderItem(id, parent_id, null, name, image_url, created_at)
}

@Entity(tableName = "previous_paper_files")
data class PreviousPaperFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Json(name = "folder_id") val folder_id: Long,
    @Json(name = "file_name") val file_name: String,
    @Json(name = "file_type") val file_type: String,
    @Json(name = "storage_url") val storage_url: String,
    @Json(name = "file_size") val file_size: String = "",
    @Json(name = "created_at") val created_at: String = System.currentTimeMillis().toString()
) {
    fun toFileItem() = FileItem(id, folder_id, null, file_name, file_type, storage_url, file_size, created_at)
}

// Free Books
@Entity(tableName = "free_book_folders")
data class FreeBookFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Json(name = "parent_id") val parent_id: Long? = null,
    @Json(name = "name") val name: String,
    @Json(name = "image_url") val image_url: String = "",
    @Json(name = "created_at") val created_at: String = System.currentTimeMillis().toString()
) {
    fun toFolderItem() = FolderItem(id, parent_id, null, name, image_url, created_at)
}

@Entity(tableName = "free_book_files")
data class FreeBookFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Json(name = "folder_id") val folder_id: Long,
    @Json(name = "file_name") val file_name: String,
    @Json(name = "file_type") val file_type: String,
    @Json(name = "storage_url") val storage_url: String,
    @Json(name = "file_size") val file_size: String = "",
    @Json(name = "created_at") val created_at: String = System.currentTimeMillis().toString()
) {
    fun toFileItem() = FileItem(id, folder_id, null, file_name, file_type, storage_url, file_size, created_at)
}

