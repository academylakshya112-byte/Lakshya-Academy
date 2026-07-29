package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.api.R2SupabaseManager
import com.example.api.SupabaseVideo
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

@RunWith(RobolectricTestRunner::class)
class TraceImageUrlTest13 {
    @Test
    fun fetchAllSupabaseVideos() = runBlocking {
        ShadowLog.stream = System.out
        val context = ApplicationProvider.getApplicationContext<Application>()
        
        val supabaseUrl = "https://kugyjkowjtbbpyxsbiup.supabase.co"
        val anonKey = System.getenv("SUPABASE_ANON_KEY") ?: ""
        
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("$supabaseUrl/rest/v1/videos?select=*")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .build()
            
        println("=== REMOTE VIDEOS (REST /videos) ===")
        val response = client.newCall(request).execute()
        val rawJson = response.body?.string() ?: ""
        
        val jsonArray = JSONArray(rawJson)
        println("Total Remote Videos found: ${jsonArray.length()}")
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val id = obj.optInt("id", -1)
            val classText = obj.optString("classText", "")
            val subject = obj.optString("subject", "")
            val thumbnailUrl = obj.optString("thumbnailUrl", "")
            val videoUrl = obj.optString("videoUrl", "")
            println("Video ID: $id | classText: '$classText' | subject: '$subject' | thumbnailUrl: '$thumbnailUrl' | videoUrl: '$videoUrl'")
        }
        println("=== END OF VIDEOS ===")
    }
}
