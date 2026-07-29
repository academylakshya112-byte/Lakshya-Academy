package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AcademyDatabase
import com.example.data.CourseEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TraceImageUrlRoomTest {
    private lateinit var db: AcademyDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(
            context, AcademyDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testRoomInsertAndRead() = runBlocking {
        val course = CourseEntity(
            title = "Test Course",
            category = "Test",
            subject = "Test Sub",
            description = "Test Desc",
            isFree = true,
            price = 0.0,
            totalLessons = 1,
            imageUrl = "https://example.com/image.jpg"
        )
        
        db.academyDao().insertCourse(course)
        
        val courses = db.academyDao().getAllCourses().first()
        val readCourse = courses.first()
        
        println("[ROOM TEST] original imageUrl: '${course.imageUrl}'")
        println("[ROOM TEST] read imageUrl: '${readCourse.imageUrl}'")
        assertEquals("https://example.com/image.jpg", readCourse.imageUrl)
    }
}
