package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AppUpdateEntity(
    @Json(name = "id") val id: Long? = null,
    @Json(name = "latest_version") val latestVersion: String? = "1.0.0",
    @Json(name = "minimum_version") val minimumVersion: String? = "1.0.0",
    @Json(name = "force_update") val forceUpdate: Boolean? = false,
    @Json(name = "release_notes") val releaseNotes: String? = "",
    @Json(name = "apk_url") val apkUrl: String? = "",
    @Json(name = "updated_at") val updatedAt: String? = null
) {
    val safeLatestVersion: String get() = latestVersion?.takeIf { it.isNotBlank() } ?: "1.0.0"
    val safeMinimumVersion: String get() = minimumVersion?.takeIf { it.isNotBlank() } ?: "1.0.0"
    val isForceUpdate: Boolean get() = forceUpdate ?: false
    val safeReleaseNotes: String get() = releaseNotes ?: ""
    val safeApkUrl: String get() = apkUrl ?: ""
}
