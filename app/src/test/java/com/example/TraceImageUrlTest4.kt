package com.example

import com.example.data.CourseEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class TraceImageUrlTest4 {
    @Test
    fun testTraceMoshiSerialization() {
        val course = CourseEntity(
            id = 7,
            title = "AIRFORCE",
            category = "Target Batch",
            subject = "Reasoning",
            description = "LMS Course",
            isFree = false,
            price = 0.0,
            totalLessons = 1,
            imageUrl = "https://example.com/image.jpg"
        )

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        val adapter = moshi.adapter(CourseEntity::class.java)
        
        val json = adapter.toJson(course)
        
        println("[STEP 3: Retrofit/Moshi Serialization]")
        println("JSON value: '$json'")
    }
}
