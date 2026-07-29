package com.example.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class VideoViewDto(
    val id: Int = 0,
    @Json(name = "video_id") val videoId: String,
    @Json(name = "user_id") val userId: String,
    @Json(name = "viewed_at") val viewedAt: Long = System.currentTimeMillis()
)
