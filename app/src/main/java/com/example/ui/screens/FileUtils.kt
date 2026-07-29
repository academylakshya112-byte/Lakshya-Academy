package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

fun getFileNameFromUri(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor.use {
            if (it != null && it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = it.getString(index)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "file"
}

fun getFileSizeFromUri(context: Context, uri: Uri): String {
    var result: Long = 0
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor.use {
            if (it != null && it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.SIZE)
                if (index != -1) {
                    result = it.getLong(index)
                }
            }
        }
    }
    if (result <= 0) return "Unknown Size"
    val kb = result / 1024.0
    val mb = kb / 1024.0
    return if (mb > 1.0) String.format("%.2f MB", mb) else String.format("%.2f KB", kb)
}

fun sanitizeVideoUrl(url: String): String {
    val trimmed = url.trim()
    
    // Handle Google Drive Links
    if (trimmed.contains("drive.google.com")) {
        val fileId = if (trimmed.contains("id=")) {
            trimmed.substringAfter("id=").substringBefore("&")
        } else if (trimmed.contains("/d/")) {
            trimmed.substringAfter("/d/").substringBefore("/")
        } else {
            null
        }
        
        if (fileId != null) {
            // Direct download link often works for ExoPlayer if the file is public
            return "https://drive.google.com/uc?export=download&id=$fileId"
        }
    }
    
    return trimmed
}

fun detectVideoSourceType(url: String): String {
    val trimmed = url.trim()
    if (trimmed.startsWith("content://") || trimmed.startsWith("file://")) {
        return "LOCAL"
    }
    
    val lower = trimmed.lowercase()
    if (lower.contains("youtube.com") || lower.contains("youtu.be")) {
        return "YOUTUBE"
    }
    
    if (lower.contains("drive.google.com") || lower.contains("docs.google.com")) {
        return "GOOGLE_DRIVE"
    }
    
    if (lower.contains("supabase")) {
        return "SUPABASE"
    }
    
    if (lower.endsWith(".mp4") || lower.contains(".mp4?") || lower.contains("/mp4") || lower.contains("mov_bbb") || lower.contains("movie.mp4") || lower.startsWith("http")) {
        return "MP4"
    }
    
    return "LOCAL"
}

fun isYouTubeUrl(url: String): Boolean {
    val lower = url.trim().lowercase()
    return lower.contains("youtube.com") || lower.contains("youtu.be")
}

fun extractYouTubeVideoId(url: String): String? {
    val cleanUrl = url.trim()
    if (cleanUrl.isBlank()) return null
    
    return try {
        when {
            cleanUrl.contains("watch?v=") -> {
                cleanUrl.substringAfter("watch?v=").substringBefore("&").substringBefore("?")
            }
            cleanUrl.contains("youtu.be/") -> {
                cleanUrl.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
            }
            cleanUrl.contains("youtube.com/embed/") -> {
                cleanUrl.substringAfter("youtube.com/embed/").substringBefore("?").substringBefore("&")
            }
            cleanUrl.contains("youtube.com/live/") -> {
                cleanUrl.substringAfter("youtube.com/live/").substringBefore("?").substringBefore("&")
            }
            cleanUrl.contains("youtube.com/shorts/") -> {
                cleanUrl.substringAfter("youtube.com/shorts/").substringBefore("?").substringBefore("&")
            }
            cleanUrl.contains("youtube.com/v/") -> {
                cleanUrl.substringAfter("youtube.com/v/").substringBefore("?").substringBefore("&")
            }
            cleanUrl.contains("watch?") && cleanUrl.contains("v=") -> {
                cleanUrl.substringAfter("v=").substringBefore("&").substringBefore("?")
            }
            else -> {
                if (cleanUrl.length == 11 && !cleanUrl.contains("/") && !cleanUrl.contains(".") && !cleanUrl.contains("?")) {
                    cleanUrl
                } else {
                    null
                }
            }
        }
    } catch (e: Exception) {
        null
    }
}

fun extractYouTubePlaylistId(url: String): String? {
    val cleanUrl = url.trim()
    if (cleanUrl.isBlank()) return null
    return try {
        when {
            cleanUrl.contains("list=") -> {
                cleanUrl.substringAfter("list=").substringBefore("&").substringBefore("?")
            }
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}

fun getYouTubeEmbedUrl(url: String): String {
    val cleanUrl = url.trim()
    val videoId = extractYouTubeVideoId(cleanUrl)
    val playlistId = extractYouTubePlaylistId(cleanUrl)

    return when {
        videoId != null && playlistId != null -> {
            "https://www.youtube.com/embed/$videoId?list=$playlistId&autoplay=1&rel=0&modestbranding=1&playsinline=1"
        }
        playlistId != null -> {
            "https://www.youtube.com/embed/videoseries?list=$playlistId&autoplay=1&rel=0&modestbranding=1&playsinline=1"
        }
        videoId != null -> {
            "https://www.youtube.com/embed/$videoId?autoplay=1&rel=0&modestbranding=1&playsinline=1"
        }
        cleanUrl.lowercase().contains("channel/") && cleanUrl.lowercase().contains("/live") -> {
            val channelId = cleanUrl.substringAfter("channel/").substringBefore("/")
            "https://www.youtube.com/embed/live_stream?channel=$channelId&autoplay=1"
        }
        cleanUrl.startsWith("http") -> cleanUrl
        else -> "https://www.youtube.com/embed/$cleanUrl?autoplay=1&rel=0&modestbranding=1&playsinline=1"
    }
}

fun getYouTubeThumbnailUrl(url: String): String {
    val cleanUrl = url.trim()
    val videoId = extractYouTubeVideoId(cleanUrl)
    return if (videoId != null && videoId.isNotEmpty()) {
        "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
    } else {
        "https://img.youtube.com/vi/hqdefault.jpg"
    }
}

fun convertDriveUrl(url: String): String {
    val trimmed = url.trim()
    if (!trimmed.contains("drive.google.com") && !trimmed.contains("docs.google.com")) return trimmed
    
    val fileId = if (trimmed.contains("id=")) {
        trimmed.substringAfter("id=").substringBefore("&")
    } else if (trimmed.contains("/d/")) {
        trimmed.substringAfter("/d/").substringBefore("/")
    } else {
        null
    }
    
    return if (fileId != null) {
        "https://drive.google.com/uc?export=download&id=$fileId"
    } else {
        trimmed
    }
}
