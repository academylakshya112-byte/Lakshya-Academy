package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AppUpdateEntity(
    @Json(name = "latest_version") val latestVersion: String,
    @Json(name = "minimum_version") val minimumVersion: String,
    @Json(name = "force_update") val forceUpdate: Boolean,
    @Json(name = "release_notes") val releaseNotes: String = "",
    @Json(name = "apk_url") val apkUrl: String = "",
    @Json(name = "updated_at") val updatedAt: String = ""
)
