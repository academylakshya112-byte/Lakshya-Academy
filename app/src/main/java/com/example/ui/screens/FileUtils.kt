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

fun extractYouTubeVideoId(url: String): String? {
    val cleanUrl = url.trim()
    if (cleanUrl.isBlank()) return null
    
    // 1. Check if the input itself is a raw 11-char video ID
    if (cleanUrl.length == 11 && cleanUrl.matches(Regex("[a-zA-Z0-9_-]{11}"))) {
        return cleanUrl
    }
    
    // 2. Handle standard URL formats
    return try {
        val uri = Uri.parse(cleanUrl)
        val host = uri.host?.lowercase()
        
        when {
            host == null -> null
            host.contains("youtu.be") -> {
                uri.pathSegments.firstOrNull()
            }
            host.contains("youtube.com") -> {
                when {
                    uri.pathSegments.contains("watch") -> uri.getQueryParameter("v")
                    uri.pathSegments.contains("shorts") -> uri.pathSegments.getOrNull(uri.pathSegments.indexOf("shorts") + 1)
                    uri.pathSegments.contains("embed") -> uri.pathSegments.getOrNull(uri.pathSegments.indexOf("embed") + 1)
                    uri.pathSegments.contains("v") -> uri.pathSegments.getOrNull(uri.pathSegments.indexOf("v") + 1)
                    else -> uri.getQueryParameter("v")
                }
            }
            else -> {
                // Regex fallback for non-standard or malformed URLs
                val regex = "(?:youtube\\.com\\/(?:[^/]+\\/.+\\/|(?:v|e(?:mbed)?)\\/|.*[?&]v=)|youtu\\.be\\/)([^\"&?/\\s]{11})"
                val pattern = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.CASE_INSENSITIVE)
                val matcher = pattern.matcher(cleanUrl)
                if (matcher.find()) matcher.group(1) else null
            }
        }
    } catch (e: Exception) {
        null
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
