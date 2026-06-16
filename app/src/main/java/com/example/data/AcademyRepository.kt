package com.example.data

import kotlinx.coroutines.flow.Flow

class AcademyRepository(private val academyDao: AcademyDao) {

    // === Courses ===
    val allCourses: Flow<List<CourseEntity>> = academyDao.getAllCourses()

    suspend fun insertCourse(course: CourseEntity): Int {
        return academyDao.insertCourse(course).toInt()
    }

    suspend fun deleteCourse(id: Int) {
        academyDao.deleteCourseById(id)
    }

    suspend fun updateCourse(course: CourseEntity) {
        academyDao.updateCourse(course)
    }

    // === Lessons ===
    fun getLessonsForCourse(courseId: Int): Flow<List<LessonEntity>> {
        return academyDao.getLessonsForCourse(courseId)
    }

    suspend fun insertLesson(lesson: LessonEntity) {
        academyDao.insertLesson(lesson)
    }

    suspend fun deleteLesson(id: Int) {
        academyDao.deleteLessonById(id)
    }

    // === Enrollments ===
    val allEnrollments: Flow<List<EnrollmentEntity>> = academyDao.getAllEnrollments()

    fun getEnrollmentsForUser(email: String): Flow<List<EnrollmentEntity>> {
        return academyDao.getEnrollmentsForUser(email)
    }

    suspend fun insertEnrollment(enrollment: EnrollmentEntity) {
        academyDao.insertEnrollment(enrollment)
    }

    suspend fun updateEnrollment(enrollment: EnrollmentEntity) {
        academyDao.updateEnrollment(enrollment)
    }

    // === Tests ===
    val allTests: Flow<List<TestEntity>> = academyDao.getAllTests()

    suspend fun insertTest(test: TestEntity): Int {
        return academyDao.insertTest(test).toInt()
    }

    suspend fun deleteTest(id: Int) {
        academyDao.deleteTestById(id)
    }

    // === Questions ===
    fun getQuestionsForTest(testId: Int): Flow<List<QuestionEntity>> {
        return academyDao.getQuestionsForTest(testId)
    }

    suspend fun insertQuestion(question: QuestionEntity) {
        academyDao.insertQuestion(question)
    }

    suspend fun deleteQuestion(id: Int) {
        academyDao.deleteQuestionById(id)
    }

    // === Test Scores ===
    val allScores: Flow<List<TestScoreEntity>> = academyDao.getAllScores()

    fun getScoresForUser(email: String): Flow<List<TestScoreEntity>> {
        return academyDao.getScoresForUser(email)
    }

    suspend fun insertScore(score: TestScoreEntity) {
        academyDao.insertScore(score)
    }

    // === Doubts ===
    val allDoubts: Flow<List<DoubtEntity>> = academyDao.getAllDoubts()

    suspend fun insertDoubt(doubt: DoubtEntity) {
        academyDao.insertDoubt(doubt)
    }

    suspend fun updateDoubt(doubt: DoubtEntity) {
        academyDao.updateDoubt(doubt)
    }

    // === Chat Messages ===
    val allChatMessages: Flow<List<ChatMessageEntity>> = academyDao.getAllChatMessages()

    suspend fun insertChatMessage(message: ChatMessageEntity) {
        academyDao.insertChatMessage(message)
    }

    // === Notifications ===
    val allNotifications: Flow<List<NotificationEntity>> = academyDao.getAllNotifications()

    suspend fun insertNotification(notification: NotificationEntity) {
        academyDao.insertNotification(notification)
    }

    // === Materials ===
    fun getMaterialsByType(type: String): Flow<List<MaterialEntity>> {
        return academyDao.getMaterialsByType(type)
    }

    suspend fun insertMaterial(material: MaterialEntity) {
        academyDao.insertMaterial(material)
    }

    suspend fun deleteMaterial(id: Int) {
        academyDao.deleteMaterialById(id)
    }

    // === Banners ===
    val allBanners: Flow<List<BannerEntity>> = academyDao.getAllBanners()

    suspend fun insertBanner(banner: BannerEntity) {
        academyDao.insertBanner(banner)
    }

    suspend fun deleteBanner(id: Int) {
        academyDao.deleteBannerById(id)
    }

    // === Live Classes ===
    val allLiveClasses: Flow<List<LiveClassEntity>> = academyDao.getAllLiveClasses()

    suspend fun insertLiveClass(liveClass: LiveClassEntity) {
        academyDao.insertLiveClass(liveClass)
    }

    suspend fun deleteLiveClass(id: Int) {
        academyDao.deleteLiveClassById(id)
    }
}
