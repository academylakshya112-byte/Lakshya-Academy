package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.api.R2SupabaseManager
import com.example.api.SupabaseVideo
import com.example.data.AcademyDatabase
import com.example.data.AcademyRepository
import com.example.data.CourseEntity
import com.example.ui.viewmodel.AcademyViewModel
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

@RunWith(RobolectricTestRunner::class)
class TraceImageUrlTest10 {
    @Test
    fun runCompleteLayerTrace() = runBlocking {
        ShadowLog.stream = System.out
        val context = ApplicationProvider.getApplicationContext<Application>()
        
        val supabaseUrl = "https://kugyjkowjtbbpyxsbiup.supabase.co"
        val anonKey = System.getenv("SUPABASE_ANON_KEY") ?: ""
        
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("$supabaseUrl/rest/v1/courses?select=*&order=id.desc")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .build()
            
        // 1. Raw JSON returned by GET /courses
        println("\n=== LAYER 1: Raw JSON returned by GET /courses ===")
        val response = client.newCall(request).execute()
        val rawJson = response.body?.string() ?: ""
        
        val jsonArray = JSONArray(rawJson)
        var rawAirforceFound = false
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val title = obj.optString("title", "")
            if (title.contains("AIRFORCE") || title.contains("वायु")) {
                println("Batch Title: $title")
                println("Course ID: ${obj.optInt("id", -1)}")
                println("imageUrl: '${obj.optString("imageUrl", "")}'")
                rawAirforceFound = true
            }
        }
        if (!rawAirforceFound) {
            println("AIRFORCE Batch not found in Raw JSON!")
        }
        
        // 2. CourseEntity immediately after Moshi deserialization
        println("\n=== LAYER 2: CourseEntity immediately after Moshi deserialization ===")
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        val type = Types.newParameterizedType(List::class.java, CourseEntity::class.java)
        val adapter = moshi.adapter<List<CourseEntity>>(type)
        val remoteCourses = adapter.fromJson(rawJson) ?: emptyList()
        
        var deserializedAirforce: CourseEntity? = null
        for (course in remoteCourses) {
            if (course.title.contains("AIRFORCE") || course.title.contains("वायु")) {
                println("Batch Title: ${course.title}")
                println("Course ID: ${course.id}")
                println("imageUrl: '${course.imageUrl}'")
                deserializedAirforce = course
            }
        }
        
        // Setup credentials for the remaining parts
        val creds = R2SupabaseManager.Credentials(
            supabaseUrl = supabaseUrl,
            supabaseAnonKey = anonKey,
            supabaseTable = "videos",
            r2AccountId = "",
            r2BucketName = "",
            r2AccessKeyId = "",
            r2SecretAccessKey = "",
            r2PublicUrl = ""
        )
        R2SupabaseManager.saveCredentials(context, creds)
        
        val repository = AcademyRepository(context)
        val db = AcademyDatabase.getDatabase(context)
        
        // 3. AcademyRepository before Room insert
        println("\n=== LAYER 3: AcademyRepository before Room insert ===")
        val localCourses = db.academyDao().getAllCoursesDirect()
        val coursesToInsert = remoteCourses.map { remoteCourse ->
            val localCourse = localCourses.find { it.id == remoteCourse.id }
            if (localCourse != null && remoteCourse.imageUrl.isBlank() && localCourse.imageUrl.isNotBlank()) {
                remoteCourse.copy(imageUrl = localCourse.imageUrl)
            } else {
                remoteCourse
            }
        }
        for (course in coursesToInsert) {
            if (course.title.contains("AIRFORCE") || course.title.contains("वायु")) {
                println("Batch Title: ${course.title}")
                println("Course ID: ${course.id}")
                println("imageUrl: '${course.imageUrl}'")
            }
        }
        
        // 4. Room database after insert
        println("\n=== LAYER 4: Room database after insert ===")
        repository.syncAllFromRemote()
        val coursesInDb = db.academyDao().getAllCoursesDirect()
        var dbAirforce: CourseEntity? = null
        for (course in coursesInDb) {
            if (course.title.contains("AIRFORCE") || course.title.contains("वायु")) {
                println("Batch Title: ${course.title}")
                println("Course ID: ${course.id}")
                println("imageUrl: '${course.imageUrl}'")
                dbAirforce = course
            }
        }
        
        // 5. AcademyViewModel.allCourses
        println("\n=== LAYER 5: AcademyViewModel.allCourses ===")
        val viewModel = AcademyViewModel(context)
        // Let the state flow emit
        val vmCourses = viewModel.allCourses.first()
        var vmAirforce: CourseEntity? = null
        for (course in vmCourses) {
            if (course.title.contains("AIRFORCE") || course.title.contains("वायु")) {
                println("Batch Title: ${course.title}")
                println("Course ID: ${course.id}")
                println("imageUrl: '${course.imageUrl}'")
                vmAirforce = course
            }
        }
        
        // 6. StudentHomeDashboard (course.imageUrl)
        println("\n=== LAYER 6: StudentHomeDashboard (course.imageUrl) ===")
        // Simulating the combinedCourses mapping in StudentHomeDashboard
        val supabaseVideos = mutableListOf<SupabaseVideo>() // empty list or fetch from manager
        val r2Batches = emptyList<String>()
        val combinedCourses = mutableListOf<CourseEntity>()
        for (course in vmCourses) {
            var updatedCourse = course
            if (course.imageUrl.isBlank()) {
                val sampleVideo = supabaseVideos.firstOrNull { 
                    it.classText.trim().equals(course.title.trim(), ignoreCase = true) && 
                    !it.thumbnailUrl.isNullOrBlank() && 
                    it.thumbnailUrl != "null" 
                }
                if (sampleVideo != null) {
                    val thumbnail = sampleVideo.thumbnailUrl
                    updatedCourse = course.copy(imageUrl = thumbnail)
                }
            }
            combinedCourses.add(updatedCourse)
        }
        
        var dashboardAirforce: CourseEntity? = null
        for (course in combinedCourses) {
            if (course.title.contains("AIRFORCE") || course.title.contains("वायु")) {
                println("Batch Title: ${course.title}")
                println("Course ID: ${course.id}")
                println("imageUrl: '${course.imageUrl}'")
                dashboardAirforce = course
            }
        }
        
        // 7. CourseCard (course.imageUrl)
        println("\n=== LAYER 7: CourseCard (course.imageUrl) ===")
        if (dashboardAirforce != null) {
            val resolvedUrl = if (dashboardAirforce.imageUrl.isNotBlank()) {
                com.example.service.MediaStorageServiceFactory.getService(context).resolveMediaUrl(dashboardAirforce.imageUrl)
            } else ""
            println("Batch Title: ${dashboardAirforce.title}")
            println("Course ID: ${dashboardAirforce.id}")
            println("imageUrl (resolved): '$resolvedUrl'")
        } else {
            println("AIRFORCE Batch not found in combinedCourses!")
        }
        println("\n=== END OF TRACE ===\n")
    }
}
