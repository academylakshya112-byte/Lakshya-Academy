package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.api.R2SupabaseManager
import com.example.data.AcademyDatabase
import com.example.data.AcademyRepository
import com.example.data.CourseEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import okhttp3.OkHttpClient
import okhttp3.Request

@RunWith(RobolectricTestRunner::class)
class TraceImageUrlTest9 {
    @Test
    fun dumpRawLayers() = runBlocking {
        ShadowLog.stream = System.out
        val context = ApplicationProvider.getApplicationContext<Application>()
        
        val supabaseUrl = "https://kugyjkowjtbbpyxsbiup.supabase.co"
        val anonKey = System.getenv("SUPABASE_ANON_KEY") ?: ""
        
        println("SETUP: URL='$supabaseUrl', KEY length=${anonKey.length}")
        
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("$supabaseUrl/rest/v1/courses?select=*&order=id.desc")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .build()
            
        println("LAYER 1: RAW JSON FROM GET /courses")
        val response = client.newCall(request).execute()
        val rawJson = response.body?.string() ?: ""
        println(rawJson)
        
        println("LAYER 2: CourseEntity immediately after Moshi deserialization")
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        val type = Types.newParameterizedType(List::class.java, CourseEntity::class.java)
        val adapter = moshi.adapter<List<CourseEntity>>(type)
        
        val remoteCourses = try {
            adapter.fromJson(rawJson) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
        
        for (course in remoteCourses) {
            if (course.title.contains("AIRFORCE") || course.title.contains("वायु")) {
                println("DESERIALIZED - ID: ${course.id} | Title: '${course.title}' | imageUrl: '${course.imageUrl}'")
            }
        }
        
        // Also write credentials so AcademyRepository can run
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
        
        println("LAYER 3 & 4: Before/after Room Insert and Query")
        repository.syncAllFromRemote()
        val db = AcademyDatabase.getDatabase(context)
        val coursesInDb = db.academyDao().getAllCoursesDirect()
        for (course in coursesInDb) {
            if (course.title.contains("AIRFORCE") || course.title.contains("वायु")) {
                println("ROOM DB - ID: ${course.id} | Title: '${course.title}' | imageUrl: '${course.imageUrl}'")
            }
        }
    }
}
