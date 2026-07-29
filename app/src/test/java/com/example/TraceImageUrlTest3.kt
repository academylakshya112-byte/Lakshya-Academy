package com.example

import com.example.data.CourseEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class TraceImageUrlTest3 {
    @Test
    fun testTraceMoshiDeserialization() {
        val json = """
            {
                "id": 7,
                "title": "AIRFORCE",
                "category": "Target Batch",
                "subject": "Reasoning",
                "description": "LMS Course",
                "isFree": false,
                "price": 0,
                "totalLessons": 1,
                "imageUrl": "https://example.com/image.jpg"
            }
        """.trimIndent()

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        val adapter = moshi.adapter(CourseEntity::class.java)
        
        val course = adapter.fromJson(json)
        
        println("[STEP 2: Retrofit/Moshi Deserialization]")
        println("imageUrl value: '${course?.imageUrl}'")
    }
}
