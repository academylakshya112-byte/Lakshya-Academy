package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.*
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.StudyWebsiteEntity
import com.example.ui.viewmodel.AcademyViewModel
import com.example.api.R2SupabaseManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// === Persistent Download History Models & Helpers ===
data class DownloadRecord(
    val id: Long,
    val fileName: String,
    val url: String,
    val mimeType: String,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val status: String = "Downloading", // "Downloading", "Completed", "Failed", "Paused"
    val timestamp: Long = System.currentTimeMillis(),
    val localFilePath: String? = null,
    val errorMessage: String? = null
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("fileName", fileName)
            put("url", url)
            put("mimeType", mimeType)
            put("totalBytes", totalBytes)
            put("downloadedBytes", downloadedBytes)
            put("status", status)
            put("timestamp", timestamp)
            put("localFilePath", localFilePath ?: "")
            put("errorMessage", errorMessage ?: "")
        }
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): DownloadRecord {
            return DownloadRecord(
                id = obj.optLong("id", 0L),
                fileName = obj.optString("fileName", "download"),
                url = obj.optString("url", ""),
                mimeType = obj.optString("mimeType", "application/octet-stream"),
                totalBytes = obj.optLong("totalBytes", 0L),
                downloadedBytes = obj.optLong("downloadedBytes", 0L),
                status = obj.optString("status", "Downloading"),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                localFilePath = obj.optString("localFilePath").takeIf { !it.isNullOrBlank() },
                errorMessage = obj.optString("errorMessage").takeIf { !it.isNullOrBlank() }
            )
        }
    }
}

object DownloadHistoryManager {
    private const val PREFS_NAME = "study_website_download_history_v3"
    private const val KEY_HISTORY = "history_json_v3"

    fun getHistory(context: Context): List<DownloadRecord> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        val list = mutableListOf<DownloadRecord>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(DownloadRecord.fromJsonObject(obj))
            }
        } catch (e: Exception) {
            android.util.Log.e("DownloadHistoryManager", "Error reading history: ${e.message}")
        }
        return list
    }

    fun saveHistory(context: Context, list: List<DownloadRecord>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        list.forEach { jsonArray.put(it.toJsonObject()) }
        prefs.edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
    }

    fun addOrUpdate(context: Context, record: DownloadRecord) {
        val list = getHistory(context).toMutableList()
        val index = list.indexOfFirst {
            it.id == record.id || (it.url == record.url && it.url.isNotBlank() && (System.currentTimeMillis() - it.timestamp) < 5000)
        }
        if (index >= 0) {
            list[index] = record
        } else {
            list.add(0, record)
        }
        saveHistory(context, list)
    }

    fun delete(context: Context, id: Long, deleteFileFromStorage: Boolean) {
        val list = getHistory(context).toMutableList()
        val item = list.find { it.id == id }
        if (item != null) {
            if (deleteFileFromStorage) {
                try {
                    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? android.app.DownloadManager
                    dm?.remove(id)
                } catch (e: Exception) {
                    android.util.Log.e("DownloadHistoryManager", "Remove DM error: ${e.message}")
                }
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetFile = File(downloadsDir, item.fileName)
                if (targetFile.exists()) {
                    try { targetFile.delete() } catch (e: Exception) {}
                }
                if (!item.localFilePath.isNullOrBlank()) {
                    val path = Uri.parse(item.localFilePath).path ?: item.localFilePath
                    val f = File(path)
                    if (f.exists()) {
                        try { f.delete() } catch (e: Exception) {}
                    }
                }
            }
            list.removeAll { it.id == id }
            saveHistory(context, list)
        }
    }

    fun clearAll(context: Context, deleteFilesFromStorage: Boolean) {
        if (deleteFilesFromStorage) {
            val list = getHistory(context)
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? android.app.DownloadManager
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            list.forEach { item ->
                try { dm?.remove(item.id) } catch (e: Exception) {}
                val f = File(downloadsDir, item.fileName)
                if (f.exists()) { try { f.delete() } catch (e: Exception) {} }
            }
        }
        saveHistory(context, emptyList())
    }

    fun syncWithDownloadManager(context: Context): List<DownloadRecord> {
        val list = getHistory(context).toMutableList()
        if (list.isEmpty()) return emptyList()

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? android.app.DownloadManager
            ?: return list

        var updated = false
        for (i in list.indices) {
            val item = list[i]
            if (item.status == "Downloading" || item.status == "Paused") {
                val query = android.app.DownloadManager.Query().setFilterById(item.id)
                val cursor = try { dm.query(query) } catch (e: Exception) { null }
                if (cursor != null && cursor.moveToFirst()) {
                    val statusIdx = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS)
                    val bytesDownloadedIdx = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalBytesIdx = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val reasonIdx = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_REASON)
                    val localUriIdx = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_LOCAL_URI)

                    val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else -1
                    val downloaded = if (bytesDownloadedIdx >= 0) cursor.getLong(bytesDownloadedIdx) else item.downloadedBytes
                    val total = if (totalBytesIdx >= 0) cursor.getLong(totalBytesIdx) else item.totalBytes
                    val localUriStr = if (localUriIdx >= 0) cursor.getString(localUriIdx) else item.localFilePath

                    val statusStr = when (status) {
                        android.app.DownloadManager.STATUS_SUCCESSFUL -> "Completed"
                        android.app.DownloadManager.STATUS_FAILED -> "Failed"
                        android.app.DownloadManager.STATUS_PAUSED -> "Paused"
                        android.app.DownloadManager.STATUS_RUNNING -> "Downloading"
                        android.app.DownloadManager.STATUS_PENDING -> "Downloading"
                        else -> item.status
                    }

                    var errorReason: String? = null
                    if (status == android.app.DownloadManager.STATUS_FAILED && reasonIdx >= 0) {
                        val reason = cursor.getInt(reasonIdx)
                        errorReason = "Download error ($reason)"
                    }

                    val updatedItem = item.copy(
                        downloadedBytes = downloaded,
                        totalBytes = if (total > 0) total else item.totalBytes,
                        status = statusStr,
                        localFilePath = localUriStr ?: item.localFilePath,
                        errorMessage = errorReason ?: item.errorMessage
                    )
                    list[i] = updatedItem
                    updated = true
                    cursor.close()
                } else {
                    cursor?.close()
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val localFile = File(downloadsDir, item.fileName)
                    if (localFile.exists()) {
                        if (item.status != "Completed") {
                            list[i] = item.copy(status = "Completed", totalBytes = if (item.totalBytes <= 0) localFile.length() else item.totalBytes, downloadedBytes = localFile.length())
                            updated = true
                        }
                    } else if (item.status == "Downloading") {
                        list[i] = item.copy(status = "Failed", errorMessage = "Download cancelled or failed")
                        updated = true
                    }
                }
            }
        }
        if (updated) {
            saveHistory(context, list)
        }
        return list
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
        else -> "$bytes B"
    }
}

fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun getFileMimeType(fileName: String, rawMimeType: String?): String {
    if (!rawMimeType.isNullOrBlank() && rawMimeType != "application/octet-stream") {
        return rawMimeType
    }
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "pdf" -> "application/pdf"
        "doc", "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "ppt", "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "xls", "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "apk" -> "application/vnd.android.package-archive"
        "zip" -> "application/zip"
        "rar" -> "application/x-rar-compressed"
        "7z" -> "application/x-7z-compressed"
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "txt" -> "text/plain"
        "html" -> "text/html"
        else -> "application/octet-stream"
    }
}

fun openDownloadedFile(context: Context, item: DownloadRecord) {
    try {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? android.app.DownloadManager
        var contentUri: Uri? = null

        if (item.id > 0) {
            contentUri = try { dm?.getUriForDownloadedFile(item.id) } catch (e: Exception) { null }
        }

        if (contentUri == null) {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val localFile = File(downloadsDir, item.fileName)
            if (localFile.exists()) {
                contentUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    localFile
                )
            } else if (!item.localFilePath.isNullOrBlank()) {
                val f = File(Uri.parse(item.localFilePath).path ?: item.localFilePath)
                if (f.exists()) {
                    contentUri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        f
                    )
                }
            }
        }

        if (contentUri != null) {
            val mimeType = getFileMimeType(item.fileName, item.mimeType)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "File not found on device", Toast.LENGTH_SHORT).show()
        }
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app available to open this file type", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to open file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun shareDownloadedFile(context: Context, item: DownloadRecord) {
    try {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? android.app.DownloadManager
        var contentUri: Uri? = try { dm?.getUriForDownloadedFile(item.id) } catch (e: Exception) { null }

        if (contentUri == null) {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val localFile = File(downloadsDir, item.fileName)
            if (localFile.exists()) {
                contentUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    localFile
                )
            }
        }

        if (contentUri != null) {
            val mimeType = getFileMimeType(item.fileName, item.mimeType)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share ${item.fileName}"))
        } else {
            Toast.makeText(context, "File not found to share", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to share file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun retryDownload(context: Context, item: DownloadRecord) {
    if (item.url.isBlank() || item.url.startsWith("blob:")) {
        Toast.makeText(context, "Cannot retry this download", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? android.app.DownloadManager
        val uri = Uri.parse(item.url)
        val buildReq = { usePublicDir: Boolean ->
            android.app.DownloadManager.Request(uri).apply {
                val mimeType = getFileMimeType(item.fileName, item.mimeType)
                setMimeType(mimeType)
                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                if (usePublicDir) {
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, item.fileName)
                } else {
                    setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, item.fileName)
                }
            }
        }
        val newId = try {
            dm?.enqueue(buildReq(true))
        } catch (ex: Exception) {
            dm?.enqueue(buildReq(false))
        } ?: System.currentTimeMillis()
        val newRecord = item.copy(
            id = newId,
            status = "Downloading",
            downloadedBytes = 0L,
            timestamp = System.currentTimeMillis(),
            errorMessage = null
        )
        DownloadHistoryManager.addOrUpdate(context, newRecord)
        Toast.makeText(context, "Retrying download for ${item.fileName}...", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Retry failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun enqueueWebDownload(
    context: Context,
    downloadUrl: String,
    userAgent: String?,
    contentDisposition: String?,
    mimetype: String?
) {
    if (downloadUrl.isBlank()) return

    // Prevent duplicate download entries within 3 seconds
    val history = DownloadHistoryManager.getHistory(context)
    val duplicate = history.find {
        it.url == downloadUrl && (System.currentTimeMillis() - it.timestamp) < 3000
    }
    if (duplicate != null) {
        Toast.makeText(context, "Download already in progress for ${duplicate.fileName}", Toast.LENGTH_SHORT).show()
        return
    }

    val isPdf = downloadUrl.endsWith(".pdf", ignoreCase = true) ||
            downloadUrl.contains(".pdf?", ignoreCase = true) ||
            downloadUrl.contains("/pdf", ignoreCase = true) ||
            (mimetype != null && mimetype.lowercase().contains("pdf")) ||
            (contentDisposition != null && contentDisposition.lowercase().contains(".pdf"))

    val rawFilename = android.webkit.URLUtil.guessFileName(downloadUrl, contentDisposition, mimetype)
    val filename = if (isPdf && !rawFilename.lowercase().endsWith(".pdf")) {
        "$rawFilename.pdf"
    } else {
        rawFilename
    }

    val cleanMimeType = getFileMimeType(filename, mimetype)

    try {
        val uri = Uri.parse(downloadUrl)
        val buildReq = { usePublicDir: Boolean ->
            android.app.DownloadManager.Request(uri).apply {
                setMimeType(cleanMimeType)
                val cookies = android.webkit.CookieManager.getInstance().getCookie(downloadUrl)
                if (!cookies.isNullOrEmpty()) {
                    addRequestHeader("cookie", cookies)
                }
                if (!userAgent.isNullOrEmpty()) {
                    addRequestHeader("User-Agent", userAgent)
                }
                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                if (usePublicDir) {
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                } else {
                    setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, filename)
                }
            }
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? android.app.DownloadManager
        val downloadId = try {
            dm?.enqueue(buildReq(true))
        } catch (ex: Exception) {
            dm?.enqueue(buildReq(false))
        } ?: System.currentTimeMillis()

        val record = DownloadRecord(
            id = downloadId,
            fileName = filename,
            url = downloadUrl,
            mimeType = cleanMimeType,
            totalBytes = 0L,
            downloadedBytes = 0L,
            status = "Downloading",
            timestamp = System.currentTimeMillis(),
            localFilePath = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filename).absolutePath
        )
        DownloadHistoryManager.addOrUpdate(context, record)
        Toast.makeText(context, "Downloading $filename...", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

fun getWebsiteSubtitle(website: StudyWebsiteEntity): String {
    val name = website.name.lowercase()
    val url = website.websiteUrl.lowercase()
    return when {
        name.contains("google") || url.contains("google") -> "Search anything, explore the world's information."
        name.contains("youtube") || url.contains("youtube") -> "Best platform to watch educational videos and tutorials."
        name.contains("wikipedia") || url.contains("wikipedia") -> "The free encyclopedia that you can edit and contribute."
        name.contains("khan") || url.contains("khanacademy") -> "Free world-class education for anyone, anywhere."
        name.contains("geeks") || url.contains("geeksforgeeks") -> "Computer science portal for geeks. Learn, practice, grow."
        name.contains("linkedin") || url.contains("linkedin") -> "Learn skills with online courses from industry experts."
        name.contains("ncert") || url.contains("ncert") -> "Official NCERT books and solutions for all classes."
        name.contains("physics wallah") || name.contains("pw") || url.contains("pw.live") -> "Interactive live classes, notes & test series."
        name.contains("unacademy") || url.contains("unacademy") -> "India's largest learning platform for exam prep."
        name.contains("byju") || url.contains("byjus") -> "Comprehensive learning programs and mock tests."
        name.contains("coursera") || url.contains("coursera") -> "Learn online and earn credentials from top universities."
        name.contains("udemy") || url.contains("udemy") -> "Explore thousands of courses on in-demand topics."
        name.contains("edx") || url.contains("edx.org") -> "Access free online courses from top institutions worldwide."
        url.isNotBlank() -> "Explore study resources, syllabus & online learning."
        else -> "Educational tools & study materials for students."
    }
}

@Composable
fun FuturisticAtmosphereBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "stars_anim")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient_glow"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Top purple/indigo cosmic aura
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF6B21A8).copy(alpha = 0.25f * pulseAlpha),
                    Color(0xFF3B0764).copy(alpha = 0.12f * pulseAlpha),
                    Color.Transparent
                ),
                center = Offset(w * 0.5f, h * 0.12f),
                radius = w * 0.7f
            )
        )

        // Bottom blue/cyan subtle aura
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF0369A1).copy(alpha = 0.15f * pulseAlpha),
                    Color.Transparent
                ),
                center = Offset(w * 0.5f, h * 0.9f),
                radius = w * 0.6f
            )
        )

        // Static crisp stars with subtle brightness
        val starPoints = listOf(
            Offset(w * 0.12f, h * 0.08f),
            Offset(w * 0.88f, h * 0.14f),
            Offset(w * 0.22f, h * 0.28f),
            Offset(w * 0.78f, h * 0.35f),
            Offset(w * 0.08f, h * 0.52f),
            Offset(w * 0.92f, h * 0.64f),
            Offset(w * 0.15f, h * 0.78f),
            Offset(w * 0.82f, h * 0.85f),
            Offset(w * 0.45f, h * 0.92f)
        )
        starPoints.forEachIndexed { idx, point ->
            val starAlpha = if (idx % 2 == 0) pulseAlpha else (1.05f - pulseAlpha)
            drawCircle(
                color = Color.White.copy(alpha = 0.45f * starAlpha),
                radius = if (idx % 3 == 0) 2.2f else 1.5f,
                center = point
            )
        }
    }
}

@Composable
fun ReferenceShadowXTopBranding(
    modifier: Modifier = Modifier,
    onMenuClick: (() -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "halo_transition")
    val haloPulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_pulse"
    )
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(3400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_offset"
    )
    val dotBlinkAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_blink_alpha"
    )
    val dotScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_scale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 16.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Top Circular Golden Logo Emblem with Crown & "SR"
        Box(
            modifier = Modifier
                .size(116.dp)
                .offset(y = floatOffset.dp),
            contentAlignment = Alignment.Center
        ) {
            // Ambient Warm Golden Aura Glow
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerOffset = Offset(size.width / 2f, size.height / 2f)
                val r = size.minDimension / 2f

                // Outer soft gold halo
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFB300).copy(alpha = 0.55f * haloPulse),
                            Color(0xFFFF8F00).copy(alpha = 0.22f * haloPulse),
                            Color.Transparent
                        ),
                        center = centerOffset,
                        radius = r
                    )
                )

                // Gold ring
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(
                            Color(0xFFFFD54F),
                            Color(0xFFFF8F00),
                            Color(0xFFFFE082),
                            Color(0xFFFF6F00),
                            Color(0xFFFFD54F)
                        )
                    ),
                    radius = r * 0.72f,
                    center = centerOffset,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Inner Emblem Disk
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF16141A), Color(0xFF070709))
                        )
                    )
                    .border(1.dp, Color(0xFFFFB300).copy(alpha = 0.75f), CircleShape)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Golden Royal Crown Icon
                    Text(
                        text = "👑",
                        fontSize = 12.sp,
                        lineHeight = 12.sp
                    )

                    // Intertwined SR Monogram
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "S",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-1).sp
                        )
                        Text(
                            text = "R",
                            color = Color(0xFFFFB300),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-1).sp
                        )
                    }

                    // SHADOW X RAHUL text
                    Text(
                        text = "SHADOW X RAHUL",
                        color = Color(0xFFFFE082),
                        fontSize = 5.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp,
                        maxLines = 1
                    )

                    // Flourish: — X —
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(top = 1.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(8.dp)
                                .height(0.8.dp)
                                .background(Color(0xFFFFB300))
                        )
                        Text(
                            text = " X ",
                            color = Color(0xFFFFB300),
                            fontSize = 5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .width(8.dp)
                                .height(0.8.dp)
                                .background(Color(0xFFFFB300))
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. "● SELECT ECOSYSTEM" Glass Badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0F1424).copy(alpha = 0.88f))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Blinking glowing green indicator dot
                Box(
                    modifier = Modifier.size(9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Outer pulsing halo
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .scale(dotScale)
                            .clip(CircleShape)
                            .background(Color(0xFF00E676).copy(alpha = 0.4f * dotBlinkAlpha))
                    )
                    // Inner bright neon dot
                    Box(
                        modifier = Modifier
                            .size(6.5.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E676).copy(alpha = dotBlinkAlpha))
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SELECT ECOSYSTEM",
                    color = Color(0xFFE2E8F0),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. "Choose Platform" Main Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Choose ",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = "Platform",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFFFF9F0A),
                letterSpacing = (-0.5).sp
            )
        }
    }
}

@Composable
fun FuturisticSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onFilterClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF0D1222).copy(alpha = 0.9f))
            .border(
                1.dp,
                if (query.isNotBlank()) Color(0xFFFF9F0A).copy(alpha = 0.8f) else Color(0xFF1E2840),
                RoundedCornerShape(26.dp)
            )
            .padding(horizontal = 16.dp, vertical = 3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = if (query.isNotBlank()) Color(0xFFFF9F0A) else Color(0xFF64748B),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))

            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .testTag("search_websites_input"),
                placeholder = {
                    Text(
                        text = "Search for a platform or feature...",
                        color = Color(0xFF64748B),
                        fontSize = 14.sp
                    )
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            if (query.isNotBlank()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear search",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FuturisticFilterTabs(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    favCount: Int
) {
    val tab0Source = remember { MutableInteractionSource() }
    val isTab0Pressed by tab0Source.collectIsPressedAsState()
    val tab0Scale by animateFloatAsState(
        targetValue = if (isTab0Pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tab0_scale"
    )

    val tab1Source = remember { MutableInteractionSource() }
    val isTab1Pressed by tab1Source.collectIsPressedAsState()
    val tab1Scale by animateFloatAsState(
        targetValue = if (isTab1Pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tab1_scale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Tab 0: All Apps
        val isAllSelected = selectedTab == 0
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = tab0Scale
                    scaleY = tab0Scale
                }
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (isAllSelected) {
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF2E1065).copy(alpha = 0.9f),
                                Color(0xFF1E1B4B).copy(alpha = 0.95f)
                            )
                        )
                    } else {
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF0F1424).copy(alpha = 0.7f),
                                Color(0xFF0F1424).copy(alpha = 0.7f)
                            )
                        )
                    }
                )
                .border(
                    if (isAllSelected) 1.5.dp else 1.dp,
                    if (isAllSelected) Color(0xFFFF9F0A) else Color(0xFF1E2840),
                    RoundedCornerShape(20.dp)
                )
                .clickable(
                    interactionSource = tab0Source,
                    indication = null,
                    onClick = { onTabSelected(0) }
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GridView,
                    contentDescription = null,
                    tint = if (isAllSelected) Color(0xFFFF9F0A) else Color(0xFF818CF8),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "All Apps",
                    color = if (isAllSelected) Color.White else Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }

        // Tab 1: Favourites
        val isFavSelected = selectedTab == 1
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = tab1Scale
                    scaleY = tab1Scale
                }
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (isFavSelected) {
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF3B1578).copy(alpha = 0.9f),
                                Color(0xFF24105A).copy(alpha = 0.95f)
                            )
                        )
                    } else {
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF0F1424).copy(alpha = 0.7f),
                                Color(0xFF0F1424).copy(alpha = 0.7f)
                            )
                        )
                    }
                )
                .border(
                    if (isFavSelected) 1.5.dp else 1.dp,
                    if (isFavSelected) Color(0xFFF43F5E) else Color(0xFF1E2840),
                    RoundedCornerShape(20.dp)
                )
                .clickable(
                    interactionSource = tab1Source,
                    indication = null,
                    onClick = { onTabSelected(1) }
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = if (isFavSelected) Color(0xFFFF2A6D) else Color(0xFFF43F5E),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (favCount > 0) "Favourites ($favCount)" else "Favourites",
                    color = if (isFavSelected) Color.White else Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    fontWeight = if (isFavSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun FuturisticWebsiteCard(
    website: StudyWebsiteEntity,
    index: Int,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenWebsite: () -> Unit
) {
    val context = LocalContext.current
    val neonAccents = remember {
        listOf(
            Color(0xFF8B5CF6), // Purple (Physics Wallah style)
            Color(0xFFF59E0B), // Orange / Amber (Next Toppers style)
            Color(0xFFEF4444), // Red / Crimson (Mission Jeet style)
            Color(0xFF0EA5E9), // Cyan / Sky Blue (Unacademy style)
            Color(0xFF3B82F6), // Deep Blue (Unacademy Offline style)
            Color(0xFFA855F7), // Violet / Purple (Vibrant Academy style)
            Color(0xFF10B981), // Emerald Green
            Color(0xFFEC4899)  // Pink / Rose
        )
    }
    val accentColor = neonAccents[index % neonAccents.size]

    // Staggered appearance animation on scroll / load
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(6) * 35L))
        isVisible = true
    }
    val entryAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "entry_alpha"
    )
    val entryTranslationY by animateFloatAsState(
        targetValue = if (isVisible) 0f else 32f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "entry_translation"
    )

    // Tap & Press Elastic Feedback
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "card_scale"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.32f else 0.12f,
        animationSpec = tween(durationMillis = 180),
        label = "glow_alpha"
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 0.40f,
        animationSpec = tween(durationMillis = 180),
        label = "border_alpha"
    )

    // Arrow Button Interaction
    val arrowSource = remember { MutableInteractionSource() }
    val isArrowPressed by arrowSource.collectIsPressedAsState()
    val arrowScale by animateFloatAsState(
        targetValue = if (isArrowPressed) 0.82f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "arrow_scale"
    )
    val arrowRotation by animateFloatAsState(
        targetValue = if (isArrowPressed) -15f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "arrow_rotation"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = entryAlpha
                translationY = entryTranslationY
                scaleX = cardScale
                scaleY = cardScale
            }
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF0A0E1A).copy(alpha = 0.96f))
            .border(
                width = if (isPressed) 1.5.dp else 1.dp,
                color = if (isPressed) accentColor.copy(alpha = borderAlpha) else Color(0xFF1B233A),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onOpenWebsite
            )
            .testTag("website_card_${website.id}")
    ) {
        // Ambient glow in card background with smooth dynamic pulse on tap
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = glowAlpha),
                        Color.Transparent
                    ),
                    center = Offset(36f, size.height * 0.5f),
                    radius = size.height * 1.4f
                )
            )
        }

        // Left Vertical Colored Accent Strip
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 0.dp)
                .width(4.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                .background(accentColor)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(4.dp))

            // Website Logo / Icon Circular Container
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF12182B))
                    .border(
                        1.5.dp,
                        if (isPressed) accentColor else accentColor.copy(alpha = 0.5f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (website.imageUrl.isNotBlank()) {
                    val resolvedUrl = com.example.service.MediaStorageServiceFactory.getService(context)
                        .resolveMediaUrl(website.imageUrl)
                    AsyncImage(
                        model = resolvedUrl,
                        contentDescription = "${website.name} Logo",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Website Info Column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp)
            ) {
                Text(
                    text = website.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = getWebsiteSubtitle(website),
                    color = Color(0xFF8E9BB0),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )
            }

            // Right Actions: Heart + Circular Glowing Arrow Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AnimatedHeartButton(
                    isFavorite = isFavorite,
                    onToggle = onToggleFavorite,
                    modifier = Modifier.size(36.dp)
                )

                // Circular Glowing Arrow Action Button with smooth tap animation
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .graphicsLayer {
                            scaleX = arrowScale
                            scaleY = arrowScale
                            rotationZ = arrowRotation
                        }
                        .clip(CircleShape)
                        .background(
                            if (isArrowPressed) accentColor.copy(alpha = 0.38f)
                            else accentColor.copy(alpha = 0.18f)
                        )
                        .border(1.5.dp, accentColor.copy(alpha = 0.85f), CircleShape)
                        .clickable(
                            interactionSource = arrowSource,
                            indication = null,
                            onClick = onOpenWebsite
                        )
                        .testTag("study_now_button_${website.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Open Platform",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FuturisticBottomNavigationBar(
    selectedRoute: String,
    onTabSelect: (String) -> Unit,
    onOpenDownloads: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF070913).copy(alpha = 0.96f),
        border = BorderStroke(1.dp, Color(0xFF181D33))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Home
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onTabSelect("home") }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Home",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text("Home", color = Color(0xFF94A3B8), fontSize = 10.sp)
            }

            // 2. My Progress
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onTabSelect("DASHBOARD") }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.TrendingUp,
                    contentDescription = "My Progress",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text("My Progress", color = Color(0xFF94A3B8), fontSize = 10.sp)
            }

            // 3. Study Websites (Selected Active Glow)
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF261245).copy(alpha = 0.7f))
                    .border(1.dp, Color(0xFFA855F7).copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = "Study Websites",
                    tint = Color(0xFFE879F9),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text("Study Websites", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            // 4. Downloads
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpenDownloads() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = "Downloads",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text("Downloads", color = Color(0xFF94A3B8), fontSize = 10.sp)
            }

            // 5. Profile
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onTabSelect("profile") }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.PersonOutline,
                    contentDescription = "Profile",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text("Profile", color = Color(0xFF94A3B8), fontSize = 10.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentStudyWebsitesScreen(
    viewModel: AcademyViewModel,
    onOpenWebsite: (String) -> Unit,
    onBack: () -> Unit,
    onNavigateTab: ((String) -> Unit)? = null
) {
    com.example.util.TrackStudyModule(com.example.util.StudyTracker.MODULE_STUDY_WEBSITES)

    val websites by viewModel.allStudyWebsites.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val userEmail = viewModel.currentUser?.email ?: "guest"
    val favoriteIds by viewModel.getFavoriteWebsiteIds(userEmail).collectAsStateWithLifecycle(initialValue = emptyList())
    val favoriteWebsites by viewModel.getFavoriteStudyWebsites(userEmail).collectAsStateWithLifecycle(initialValue = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: All Websites, 1: Favourites
    var showDownloadsDialog by remember { mutableStateOf(false) }

    val baseList = if (selectedTab == 1) favoriteWebsites else websites
    val displayWebsites = remember(baseList, searchQuery) {
        if (searchQuery.isBlank()) {
            baseList
        } else {
            val q = searchQuery.trim().lowercase()
            baseList.filter { it.name.lowercase().contains(q) || it.websiteUrl.lowercase().contains(q) }
        }
    }

    if (showDownloadsDialog) {
        DownloadsScreenOverlay(onBack = { showDownloadsDialog = false })
    }

    Scaffold(
        containerColor = Color(0xFF07080E)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF07080E))
                .padding(innerPadding)
        ) {
            // Cosmic atmospheric particles and ambient glowing nebula
            FuturisticAtmosphereBackground()

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // 1. Top Branding: Golden SHADOW X RAHUL Emblem + SELECT ECOSYSTEM Badge + Choose Platform
                item(key = "top_branding") {
                    ReferenceShadowXTopBranding(
                        onMenuClick = onBack
                    )
                }

                // 2. Search Bar: "Search for a platform or feature..."
                item(key = "search_bar") {
                    FuturisticSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onFilterClick = {
                            selectedTab = if (selectedTab == 0) 1 else 0
                        }
                    )
                }

                // 3. Filter Tabs: All Websites / Favourites
                item(key = "filter_tabs") {
                    FuturisticFilterTabs(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        favCount = favoriteWebsites.size
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // 5. Empty State or Website List
                if (displayWebsites.isEmpty()) {
                    item(key = "empty_state") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp, horizontal = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF14192D))
                                        .border(1.dp, Color(0xFF262E52), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (selectedTab == 1) Icons.Default.FavoriteBorder else Icons.Default.SearchOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                        tint = if (selectedTab == 1) Color(0xFFF43F5E) else Color(0xFF818CF8)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = when {
                                        searchQuery.isNotBlank() -> "No websites match \"$searchQuery\""
                                        selectedTab == 1 -> "No favourite websites yet"
                                        else -> "No study websites available"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (selectedTab == 1) {
                                        "Tap the heart icon ♡ on any website card to add it to your favourites!"
                                    } else {
                                        "Check back soon or explore other learning materials."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF94A3B8),
                                    textAlign = TextAlign.Center
                                )
                                if (searchQuery.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Button(
                                        onClick = { searchQuery = "" },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B1578))
                                    ) {
                                        Text("Clear Search", color = Color.White, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    itemsIndexed(
                        items = displayWebsites,
                        key = { _, item -> item.id }
                    ) { index, website ->
                        val isFav = favoriteIds.contains(website.id)
                        Box(
                            modifier = Modifier
                                .animateItem()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            FuturisticWebsiteCard(
                                website = website,
                                index = index,
                                isFavorite = isFav,
                                onToggleFavorite = {
                                    viewModel.toggleStudyWebsiteFavorite(userEmail, website.id, !isFav)
                                },
                                onOpenWebsite = {
                                    onOpenWebsite(website.websiteUrl)
                                }
                            )
                        }
                    }
                }

                // 6. Floating "New websites added regularly!" Badge
                item(key = "footer_badge") {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF140F2D).copy(alpha = 0.9f))
                                .border(1.dp, Color(0xFF7C3AED), RoundedCornerShape(20.dp))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "⚡",
                                    fontSize = 12.sp,
                                    color = Color(0xFFFFD166)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "New websites added regularly!",
                                    color = Color(0xFFDDD6FE),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun openInExternalBrowser(context: Context, rawUrl: String) {
    if (rawUrl.isBlank()) return
    val formattedUrl = if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
        "https://$rawUrl"
    } else {
        rawUrl
    }
    val uri = Uri.parse(formattedUrl)
    val defaultIntent = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(defaultIntent)
    } catch (ex: Exception) {
        Toast.makeText(context, "Unable to open browser", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun StudentStudyWebsitesSection(
    viewModel: AcademyViewModel,
    onOpenWebsite: (String) -> Unit
) {
    val websites by viewModel.allStudyWebsites.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val userEmail = viewModel.currentUser?.email ?: "guest"
    val favoriteIds by viewModel.getFavoriteWebsiteIds(userEmail).collectAsStateWithLifecycle(initialValue = emptyList())

    if (websites.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            SectionHeader(title = "Study Apps (अध्ययन ऐप्स)")
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                websites.forEach { website ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("website_card_${website.id}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Banner Image
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .background(Color.LightGray)
                            ) {
                                if (website.imageUrl.isNotBlank()) {
                                    val resolvedUrl = com.example.service.MediaStorageServiceFactory.getService(context).resolveMediaUrl(website.imageUrl)
                                    AsyncImage(
                                        model = resolvedUrl,
                                        contentDescription = "${website.name} Banner",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Language,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = Color.Gray
                                        )
                                    }
                                }

                                // Animated heart overlay in Top End
                                val isFav = favoriteIds.contains(website.id)
                                AnimatedHeartButton(
                                    isFavorite = isFav,
                                    onToggle = {
                                        viewModel.toggleStudyWebsiteFavorite(userEmail, website.id, !isFav)
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(12.dp)
                                        .background(Color.White.copy(alpha = 0.85f), CircleShape)
                                        .size(36.dp)
                                )
                            }

                            // Website Info & Study Button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1.2f)) {
                                    Text(
                                        text = website.name,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = { onOpenWebsite(website.websiteUrl) },
                                    modifier = Modifier
                                        .weight(0.8f)
                                        .testTag("study_now_button_${website.id}"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text(
                                        text = "Study",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminStudyWebsiteScreen(
    viewModel: AcademyViewModel
) {
    val websites by viewModel.allStudyWebsites.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedWebsiteId by remember { mutableStateOf<String?>(null) }
    var websiteName by remember { mutableStateOf("") }
    var websiteUrl by remember { mutableStateOf("") }
    var websiteImageUrl by remember { mutableStateOf("") }
    var localImageUri by remember { mutableStateOf<Uri?>(null) }
    var isUploadingImage by remember { mutableStateOf(false) }
    var fullErrorMessage by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            localImageUri = uri
            fullErrorMessage = null
            // Do NOT overwrite websiteImageUrl with uri.toString() to prevent displaying content:// URI as text
        }
    }

    if (fullErrorMessage != null) {
        AlertDialog(
            onDismissRequest = { fullErrorMessage = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Storage / Save Error", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = fullErrorMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { fullErrorMessage = null }) {
                    Text("Dismiss")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text(
                text = "Manage Study Apps",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Website Name Input
            OutlinedTextField(
                value = websiteName,
                onValueChange = { websiteName = it },
                label = { Text("Website Name") },
                modifier = Modifier.fillMaxWidth()
            )

            // Website Logo Row with Upload Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = if (localImageUri != null) "" else websiteImageUrl,
                    onValueChange = { input ->
                        websiteImageUrl = input
                        if (input.isNotBlank()) {
                            localImageUri = null
                        }
                    },
                    label = { Text("Website Logo URL") },
                    placeholder = {
                        if (localImageUri != null) {
                            Text("Logo selected from device")
                        } else {
                            Text("https://example.com/logo.png")
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (isUploadingImage) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                } else {
                    Button(onClick = { launcher.launch("image/*") }) {
                        Text(if (localImageUri != null) "Change\nLogo" else "Select\nLogo", textAlign = TextAlign.Center, fontSize = 11.sp)
                    }
                }
            }

            // Circular Logo Preview Container (Shows exact live appearance in Full Circle)
            val previewModel: Any? = when {
                localImageUri != null -> localImageUri
                websiteImageUrl.isNotBlank() && !websiteImageUrl.startsWith("content://") && !websiteImageUrl.startsWith("file://") -> {
                    com.example.service.MediaStorageServiceFactory.getService(context).resolveMediaUrl(websiteImageUrl)
                }
                else -> null
            }

            if (previewModel != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Circular Logo Preview (गोल लोगो प्रिव्यू):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Full Circle • App Logo Fit",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Circular Logo Container Preview
                            Box(
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF12182B))
                                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = previewModel,
                                    contentDescription = "Selected Website Logo Preview",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                    alignment = Alignment.Center
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (websiteName.isNotBlank()) websiteName else "Website Title Preview",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (websiteUrl.isNotBlank()) websiteUrl else "https://example.com",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "✓ Pura logo gole (circle) me perfectly baith kar dikhega",
                                    fontSize = 10.sp,
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Website URL Input
            OutlinedTextField(
                value = websiteUrl,
                onValueChange = { websiteUrl = it },
                label = { Text("Website URL") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            // Inline Error Banner if present
            if (fullErrorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Error saving website:",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SelectionContainer {
                            Text(
                                text = fullErrorMessage ?: "",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Save Buttons / Cancel Buttons
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                    if (selectedWebsiteId != null) {
                        TextButton(onClick = {
                            selectedWebsiteId = null
                            websiteName = ""
                            websiteUrl = ""
                            websiteImageUrl = ""
                            localImageUri = null
                            fullErrorMessage = null
                        }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Button(
                        enabled = !isUploadingImage,
                        onClick = {
                            if (websiteName.isNotBlank() && websiteUrl.isNotBlank()) {
                                scope.launch {
                                    isUploadingImage = true
                                    fullErrorMessage = null
                                    try {
                                        var finalImageUrl = websiteImageUrl
                                        val selectedUri = localImageUri
                                        if (selectedUri != null) {
                                            // Step 1: Upload the selected banner image
                                            val publicUrl = viewModel.uploadStudyWebsiteBanner(context, selectedUri)
                                            
                                            // Step 2: Delete old image if editing and replacing image
                                            val targetId = selectedWebsiteId
                                            if (targetId != null) {
                                                val existingWebsite = websites.find { it.id == targetId }
                                                if (existingWebsite != null && existingWebsite.imageUrl.isNotBlank() && existingWebsite.imageUrl != publicUrl) {
                                                    try {
                                                        R2SupabaseManager.deleteFileFromSupabaseStorage(context, existingWebsite.imageUrl, "study-websites")
                                                    } catch (ex: Exception) {
                                                        android.util.Log.e("AdminStudyWebsiteScreen", "Failed to delete old image", ex)
                                                    }
                                                }
                                            }
                                            
                                            finalImageUrl = publicUrl
                                            websiteImageUrl = publicUrl
                                        }

                                        if (finalImageUrl.isBlank()) {
                                            throw Exception("Banner image is required! Please select an image or enter a valid Image URL.")
                                        }

                                        val targetId = selectedWebsiteId
                                        if (targetId != null) {
                                            viewModel.adminUpdateStudyWebsite(
                                                id = targetId,
                                                name = websiteName,
                                                imageUrl = finalImageUrl,
                                                websiteUrl = websiteUrl
                                            )
                                            Toast.makeText(context, "Website updated successfully!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            viewModel.adminAddStudyWebsite(
                                                name = websiteName,
                                                imageUrl = finalImageUrl,
                                                websiteUrl = websiteUrl
                                            )
                                            Toast.makeText(context, "Website added successfully!", Toast.LENGTH_SHORT).show()
                                        }

                                        selectedWebsiteId = null
                                        websiteName = ""
                                        websiteUrl = ""
                                        websiteImageUrl = ""
                                        localImageUri = null
                                        fullErrorMessage = null
                                    } catch (e: Exception) {
                                        val fullErr = e.message ?: e.toString()
                                        fullErrorMessage = fullErr
                                        android.util.Log.e("AdminStudyWebsiteScreen", "Operation failed: $fullErr", e)
                                    } finally {
                                        isUploadingImage = false
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Name and Website URL are required!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        if (isUploadingImage) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Saving...")
                        } else {
                            Text(if (selectedWebsiteId != null) "Update Website" else "Save Website")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Current Study Apps (${websites.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (websites.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No Study Apps configured yet.", color = Color.Gray, fontWeight = FontWeight.Medium)
                    }
                }
            }
        } else {
            items(websites) { website ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Circular Logo Container for Admin List Item
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF12182B))
                                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (website.imageUrl.isNotBlank()) {
                                val resolvedImg = com.example.service.MediaStorageServiceFactory.getService(context).resolveMediaUrl(website.imageUrl)
                                AsyncImage(
                                    model = resolvedImg,
                                    contentDescription = "${website.name} Logo",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                    alignment = Alignment.Center
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = website.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                text = website.websiteUrl,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(onClick = {
                            selectedWebsiteId = website.id
                            websiteName = website.name
                            websiteUrl = website.websiteUrl
                            websiteImageUrl = website.imageUrl
                            localImageUri = null
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Website", tint = MaterialTheme.colorScheme.primary)
                        }

                        IconButton(onClick = {
                            scope.launch {
                                try {
                                    viewModel.adminDeleteStudyWebsite(website.id, website.imageUrl)
                                    Toast.makeText(context, "Website deleted successfully!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Delete failed: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Website", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

class WebTab(
    val id: String = java.util.UUID.randomUUID().toString(),
    initialUrl: String,
    initialTitle: String = "New Tab"
) {
    var url by mutableStateOf(initialUrl)
    var title by mutableStateOf(initialTitle)
    var favicon by mutableStateOf<Bitmap?>(null)
    var webView by mutableStateOf<WebView?>(null)
    var isLoading by mutableStateOf(true)
    var progress by mutableStateOf(0)
    var hasError by mutableStateOf(false)
    var errorMessage by mutableStateOf("Please check your internet connection or the URL provided by the Academy.")
}

data class WebTabClosedInfo(val url: String, val title: String)

private fun extractHost(urlString: String?): String? {
    if (urlString.isNullOrBlank()) return null
    return try {
        val uri = Uri.parse(urlString)
        uri.host?.lowercase(Locale.ROOT)?.trim()
    } catch (_: Exception) {
        null
    }
}

private fun getRootDomain(host: String?): String {
    if (host.isNullOrBlank()) return ""
    val cleanHost = host.lowercase(Locale.ROOT).trim()
    val parts = cleanHost.split(".")
    return if (parts.size >= 2) {
        val secondLast = parts[parts.size - 2]
        if (parts.size >= 3 && secondLast in listOf("co", "com", "org", "edu", "gov", "net", "ac", "res", "gen", "mil", "nic")) {
            parts.takeLast(3).joinToString(".")
        } else {
            parts.takeLast(2).joinToString(".")
        }
    } else {
        cleanHost
    }
}

private fun isInternalStudyUrl(targetUrl: String?, baseInitialUrl: String, currentTabUrl: String?): Boolean {
    if (targetUrl.isNullOrBlank()) return true

    if (targetUrl.startsWith("blob:", ignoreCase = true) ||
        targetUrl.startsWith("data:", ignoreCase = true) ||
        targetUrl.startsWith("about:", ignoreCase = true) ||
        targetUrl.startsWith("javascript:", ignoreCase = true)) {
        return true
    }

    val targetHost = extractHost(targetUrl) ?: return false
    val initialHost = extractHost(baseInitialUrl)
    val currentHost = extractHost(currentTabUrl)

    if (initialHost != null && (targetHost == initialHost || targetHost.endsWith(".$initialHost"))) {
        return true
    }
    if (currentHost != null && (targetHost == currentHost || targetHost.endsWith(".$currentHost"))) {
        return true
    }

    val targetRoot = getRootDomain(targetHost)
    val initialRoot = getRootDomain(initialHost)
    val currentRoot = getRootDomain(currentHost)

    if (initialRoot.isNotBlank() && targetRoot == initialRoot) {
        return true
    }
    if (currentRoot.isNotBlank() && targetRoot == currentRoot) {
        return true
    }

    return false
}

private fun openInDefaultBrowser(context: Context, urlString: String) {
    try {
        Log.d("WEBVIEW LINK", "[WEBVIEW LINK] Opening External Browser: $urlString")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlString)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.e("WEBVIEW LINK", "[WEBVIEW LINK] Browser Intent Failed: ActivityNotFoundException - ${e.message}", e)
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, "No web browser found to open this link.", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Log.e("WEBVIEW LINK", "[WEBVIEW LINK] Browser Intent Failed: ${e.message}", e)
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, "Failed to open link: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun StudyWebsiteWebViewScreen(
    url: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("study_website_prefs", Context.MODE_PRIVATE) }
    var isDesktopSite by remember { mutableStateOf(prefs.getBoolean("desktop_site_enabled", false)) }
    var defaultUserAgent by remember { mutableStateOf("") }
    val desktopUserAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    val formattedInitialUrl = remember(url) {
        if (url.isBlank()) "https://google.com"
        else if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("about:")) url
        else "https://$url"
    }
    val initialTab = remember(formattedInitialUrl) { WebTab(initialUrl = formattedInitialUrl, initialTitle = "Study App") }
    val tabs = remember { mutableStateListOf(initialTab) }
    var activeTabId by remember { mutableStateOf(initialTab.id) }
    val lastClosedTabs = remember { mutableStateListOf<WebTabClosedInfo>() }
    var isTabManagerOpen by remember { mutableStateOf(false) }
    var isDownloadsOpen by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showTabManagerMenu by remember { mutableStateOf(false) }

    var customView by remember { mutableStateOf<android.view.View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val uris = if (data?.clipData != null) {
                val count = data.clipData!!.itemCount
                (0 until count).map { data.clipData!!.getItemAt(it).uri }.toTypedArray()
            } else if (data?.data != null) {
                arrayOf(data.data!!)
            } else {
                null
            }
            filePathCallback?.onReceiveValue(uris)
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    val activeTab = tabs.find { it.id == activeTabId } ?: tabs.firstOrNull() ?: initialTab

    // Create tab webview helper
    fun createWebViewForTab(ctx: Context, tab: WebTab): WebView {
        return WebView(ctx).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            tab.webView = this

            setBackgroundColor(android.graphics.Color.WHITE)
            setLayerType(android.view.View.LAYER_TYPE_NONE, null)
            setInitialScale(0)

            isVerticalScrollBarEnabled = true
            isHorizontalScrollBarEnabled = true
            isScrollbarFadingEnabled = true
            isNestedScrollingEnabled = true
            overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS

            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(true)
                useWideViewPort = true
                loadWithOverviewMode = isDesktopSite
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = true
                allowContentAccess = true
                setGeolocationEnabled(true)
                cacheMode = WebSettings.LOAD_DEFAULT
                textZoom = 100

                if (isDesktopSite) {
                    userAgentString = desktopUserAgent
                } else {
                    userAgentString = null
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }

                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = true
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = true
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                WebView.setWebContentsDebuggingEnabled(true)
            }

            addJavascriptInterface(object {
                @android.webkit.JavascriptInterface
                fun processBlob(base64Data: String, fileName: String, mimeType: String) {
                    try {
                        val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        val cleanName = if (fileName.isNotBlank()) fileName else "download_${System.currentTimeMillis()}"
                        val targetFile = File(downloadsDir, cleanName)
                        targetFile.writeBytes(bytes)

                        val record = DownloadRecord(
                            id = System.currentTimeMillis(),
                            fileName = cleanName,
                            url = "blob:",
                            mimeType = if (mimeType.isNotBlank()) mimeType else getFileMimeType(cleanName, null),
                            totalBytes = bytes.size.toLong(),
                            downloadedBytes = bytes.size.toLong(),
                            status = "Completed",
                            timestamp = System.currentTimeMillis(),
                            localFilePath = targetFile.absolutePath
                        )
                        DownloadHistoryManager.addOrUpdate(ctx, record)
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(ctx, "Downloaded $cleanName", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(ctx, "Blob download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }, "AndroidBlobBridge")

            setDownloadListener { downloadUrl, userAgent, contentDisposition, mimetype, _ ->
                if (downloadUrl.startsWith("blob:")) {
                    val rawFilename = android.webkit.URLUtil.guessFileName(downloadUrl, contentDisposition, mimetype)
                    val js = """
                        (function() {
                            var xhr = new XMLHttpRequest();
                            xhr.open('GET', '$downloadUrl', true);
                            xhr.responseType = 'blob';
                            xhr.onload = function(e) {
                                if (this.status == 200) {
                                    var blob = this.response;
                                    var reader = new FileReader();
                                    reader.readAsDataURL(blob);
                                    reader.onloadend = function() {
                                        var base64data = reader.result.split(',')[1];
                                        if (window.AndroidBlobBridge) {
                                            window.AndroidBlobBridge.processBlob(base64data, '$rawFilename', '$mimetype');
                                        }
                                    }
                                }
                            };
                            xhr.send();
                        })();
                    """.trimIndent()
                    evaluateJavascript(js, null)
                    Toast.makeText(ctx, "Downloading blob file...", Toast.LENGTH_SHORT).show()
                } else {
                    enqueueWebDownload(
                        context = ctx,
                        downloadUrl = downloadUrl,
                        userAgent = userAgent,
                        contentDisposition = contentDisposition,
                        mimetype = mimetype
                    )
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, pageUrl, favicon)
                    tab.isLoading = true
                    if (pageUrl != null) tab.url = pageUrl
                    if (favicon != null) tab.favicon = favicon
                }

                override fun onPageFinished(view: WebView?, pageUrl: String?) {
                    super.onPageFinished(view, pageUrl)
                    tab.isLoading = false
                    if (pageUrl != null) tab.url = pageUrl
                    if (view?.title?.isNotBlank() == true) {
                        tab.title = view.title!!
                    }
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        val errorCode = error?.errorCode ?: 0
                        if (errorCode == ERROR_HOST_LOOKUP || errorCode == ERROR_CONNECT || errorCode == ERROR_TIMEOUT || errorCode == ERROR_FAILED_SSL_HANDSHAKE) {
                            tab.hasError = true
                            tab.errorMessage = "Unable to connect (${error?.errorCode}): ${error?.description}"
                        }
                    }
                }

                override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                    super.onReceivedHttpError(view, request, errorResponse)
                }

                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    view?.destroy()
                    tab.webView = null
                    tab.hasError = true
                    tab.errorMessage = "WebView process stopped unexpectedly. Please tap Retry to reload."
                    return true
                }

                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                    handler?.proceed()
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val currentUrl = request?.url?.toString() ?: return false
                    return handleUrlNavigation(currentUrl, view)
                }

                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    val currentUrl = url ?: return false
                    return handleUrlNavigation(currentUrl, view)
                }

                private fun handleUrlNavigation(currentUrl: String, view: WebView?): Boolean {
                    val isDownloadable = currentUrl.endsWith(".pdf", ignoreCase = true) ||
                            currentUrl.contains(".pdf?", ignoreCase = true) ||
                            currentUrl.contains("/pdf", ignoreCase = true) ||
                            currentUrl.endsWith(".docx", ignoreCase = true) ||
                            currentUrl.endsWith(".xlsx", ignoreCase = true) ||
                            currentUrl.endsWith(".pptx", ignoreCase = true) ||
                            currentUrl.endsWith(".zip", ignoreCase = true) ||
                            currentUrl.endsWith(".apk", ignoreCase = true) ||
                            currentUrl.endsWith(".mp4", ignoreCase = true) ||
                            currentUrl.endsWith(".mp3", ignoreCase = true)

                    if (isDownloadable && (currentUrl.startsWith("http://", ignoreCase = true) || currentUrl.startsWith("https://", ignoreCase = true))) {
                        enqueueWebDownload(
                            context = ctx,
                            downloadUrl = currentUrl,
                            userAgent = view?.settings?.userAgentString,
                            contentDisposition = null,
                            mimetype = null
                        )
                        return true
                    }

                    if (currentUrl.startsWith("blob:", ignoreCase = true) ||
                        currentUrl.startsWith("data:", ignoreCase = true) ||
                        currentUrl.startsWith("about:", ignoreCase = true) ||
                        currentUrl.startsWith("javascript:", ignoreCase = true)) {
                        return false
                    }

                    if (!currentUrl.startsWith("http://", ignoreCase = true) && !currentUrl.startsWith("https://", ignoreCase = true)) {
                        Log.d("WEBVIEW LINK", "[WEBVIEW LINK] External URL (Custom Scheme): $currentUrl")
                        openInDefaultBrowser(ctx, currentUrl)
                        return true
                    }

                    val isInternal = isInternalStudyUrl(
                        targetUrl = currentUrl,
                        baseInitialUrl = formattedInitialUrl,
                        currentTabUrl = tab.url
                    )

                    if (isInternal) {
                        Log.d("WEBVIEW LINK", "[WEBVIEW LINK] Internal URL: $currentUrl")
                        return false
                    } else {
                        Log.d("WEBVIEW LINK", "[WEBVIEW LINK] External URL: $currentUrl")
                        openInDefaultBrowser(ctx, currentUrl)
                        return true
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    tab.progress = newProgress
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    if (!title.isNullOrBlank()) {
                        tab.title = title
                    }
                }

                override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                    super.onReceivedIcon(view, icon)
                    if (icon != null) {
                        tab.favicon = icon
                    }
                }

                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: android.os.Message?
                ): Boolean {
                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                    if (transport != null && view != null) {
                        val tempWebView = WebView(view.context)
                        tempWebView.webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(v: WebView?, req: WebResourceRequest?): Boolean {
                                val urlStr = req?.url?.toString()
                                if (urlStr != null) {
                                    handleNewWindowUrl(urlStr, view)
                                }
                                return true
                            }

                            @Suppress("DEPRECATION")
                            override fun shouldOverrideUrlLoading(v: WebView?, u: String?): Boolean {
                                val urlStr = u
                                if (urlStr != null) {
                                    handleNewWindowUrl(urlStr, view)
                                }
                                return true
                            }

                            private fun handleNewWindowUrl(urlStr: String, mainWebView: WebView) {
                                try {
                                    val isDownloadable = urlStr.endsWith(".pdf", ignoreCase = true) ||
                                            urlStr.contains(".pdf?", ignoreCase = true) ||
                                            urlStr.contains("/pdf", ignoreCase = true) ||
                                            urlStr.endsWith(".docx", ignoreCase = true) ||
                                            urlStr.endsWith(".xlsx", ignoreCase = true) ||
                                            urlStr.endsWith(".pptx", ignoreCase = true) ||
                                            urlStr.endsWith(".zip", ignoreCase = true) ||
                                            urlStr.endsWith(".apk", ignoreCase = true) ||
                                            urlStr.endsWith(".mp4", ignoreCase = true) ||
                                            urlStr.endsWith(".mp3", ignoreCase = true)

                                    if (isDownloadable && (urlStr.startsWith("http://", ignoreCase = true) || urlStr.startsWith("https://", ignoreCase = true))) {
                                        enqueueWebDownload(
                                            context = ctx,
                                            downloadUrl = urlStr,
                                            userAgent = mainWebView.settings.userAgentString,
                                            contentDisposition = null,
                                            mimetype = null
                                        )
                                        return
                                    }

                                    if (urlStr.startsWith("blob:", ignoreCase = true) ||
                                        urlStr.startsWith("data:", ignoreCase = true) ||
                                        urlStr.startsWith("about:", ignoreCase = true) ||
                                        urlStr.startsWith("javascript:", ignoreCase = true)) {
                                        mainWebView.loadUrl(urlStr)
                                        return
                                    }

                                    if (!urlStr.startsWith("http://", ignoreCase = true) && !urlStr.startsWith("https://", ignoreCase = true)) {
                                        Log.d("WEBVIEW LINK", "[WEBVIEW LINK] External URL (Custom Scheme): $urlStr")
                                        openInDefaultBrowser(ctx, urlStr)
                                        return
                                    }

                                    val isInternal = isInternalStudyUrl(
                                        targetUrl = urlStr,
                                        baseInitialUrl = formattedInitialUrl,
                                        currentTabUrl = tab.url
                                    )

                                    if (isInternal) {
                                        Log.d("WEBVIEW LINK", "[WEBVIEW LINK] Internal URL: $urlStr")
                                        mainWebView.loadUrl(urlStr)
                                    } else {
                                        Log.d("WEBVIEW LINK", "[WEBVIEW LINK] External URL: $urlStr")
                                        openInDefaultBrowser(ctx, urlStr)
                                    }
                                } catch (e: Exception) {
                                    Log.e("WEBVIEW LINK", "[WEBVIEW LINK] Error handling new window URL: ${e.message}", e)
                                } finally {
                                    tempWebView.destroy()
                                }
                            }
                        }
                        transport.webView = tempWebView
                        resultMsg.sendToTarget()
                    }
                    return true
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    cb: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = cb
                    try {
                        val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }
                        filePickerLauncher.launch(intent)
                    } catch (e: Exception) {
                        filePathCallback = null
                        return false
                    }
                    return true
                }

                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.grant(request.resources)
                }

                override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                    callback?.invoke(origin, true, false)
                }

                override fun onShowCustomView(view: android.view.View?, callback: CustomViewCallback?) {
                    super.onShowCustomView(view, callback)
                    customView = view
                    customViewCallback = callback
                }

                override fun onHideCustomView() {
                    super.onHideCustomView()
                    customView = null
                    customViewCallback?.onCustomViewHidden()
                    customViewCallback = null
                }

                override fun getDefaultVideoPoster(): Bitmap? {
                    return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                }
            }

            loadUrl(tab.url)
        }
    }

    fun createNewTab(targetUrl: String = url) {
        val newTab = WebTab(initialUrl = targetUrl, initialTitle = "New Tab")
        tabs.add(newTab)
        activeTabId = newTab.id
        isTabManagerOpen = false
    }

    fun closeTab(tabToClose: WebTab) {
        lastClosedTabs.add(WebTabClosedInfo(tabToClose.url, tabToClose.title))
        tabToClose.webView?.destroy()
        tabToClose.webView = null
        tabs.remove(tabToClose)

        if (tabs.isEmpty()) {
            val defaultTab = WebTab(initialUrl = url, initialTitle = "Study App")
            tabs.add(defaultTab)
            activeTabId = defaultTab.id
        } else if (activeTabId == tabToClose.id) {
            activeTabId = tabs.last().id
        }
    }

    fun closeAllTabs() {
        tabs.forEach {
            lastClosedTabs.add(WebTabClosedInfo(it.url, it.title))
            it.webView?.destroy()
            it.webView = null
        }
        tabs.clear()
        val defaultTab = WebTab(initialUrl = url, initialTitle = "Study App")
        tabs.add(defaultTab)
        activeTabId = defaultTab.id
        isTabManagerOpen = false
    }

    fun restoreLastClosedTab() {
        if (lastClosedTabs.isNotEmpty()) {
            val lastClosed = lastClosedTabs.removeAt(lastClosedTabs.size - 1)
            val restoredTab = WebTab(initialUrl = lastClosed.url, initialTitle = lastClosed.title)
            tabs.add(restoredTab)
            activeTabId = restoredTab.id
            isTabManagerOpen = false
        }
    }

    // Android Hardware Back Button Handling
    androidx.activity.compose.BackHandler(enabled = true) {
        if (customView != null) {
            customViewCallback?.onCustomViewHidden()
            customView = null
        } else if (isDownloadsOpen) {
            isDownloadsOpen = false
        } else if (isTabManagerOpen) {
            isTabManagerOpen = false
        } else {
            val currentWebView = activeTab.webView
            if (currentWebView?.canGoBack() == true) {
                currentWebView.goBack()
            } else if (tabs.size > 1) {
                closeTab(activeTab)
            } else {
                onBack()
            }
        }
    }

    // Immersive Mode
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        if (window != null) {
            androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (window != null) {
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                    show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Fullscreen Video Custom View Overlay
        if (customView != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .zIndex(100f)
            ) {
                AndroidView(
                    factory = { ctx ->
                        (customView?.parent as? android.view.ViewGroup)?.removeView(customView)
                        customView!!
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (isTabManagerOpen) {
            // Chrome Tab Manager Overlay View
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF202124))
                    .statusBarsPadding()
                    .zIndex(50f)
            ) {
                // Tab Manager Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${tabs.size} open tabs",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(onClick = { createNewTab() }) {
                        Icon(Icons.Default.Add, contentDescription = "New Tab", tint = Color.White)
                    }

                    Box {
                        IconButton(onClick = { showTabManagerMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Tab Options", tint = Color.White)
                        }

                        DropdownMenu(
                            expanded = showTabManagerMenu,
                            onDismissRequest = { showTabManagerMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("New Tab") },
                                onClick = {
                                    showTabManagerMenu = false
                                    createNewTab()
                                }
                            )
                            if (lastClosedTabs.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Restore Last Closed Tab") },
                                    onClick = {
                                        showTabManagerMenu = false
                                        restoreLastClosedTab()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Close All Tabs") },
                                onClick = {
                                    showTabManagerMenu = false
                                    closeAllTabs()
                                }
                            )
                        }
                    }

                    IconButton(onClick = { isTabManagerOpen = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close Tab Manager", tint = Color.White)
                    }
                }

                Divider(color = Color.DarkGray)

                // Tab Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .weight(1f)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(tabs, key = { it.id }) { tab ->
                        val isSelected = tab.id == activeTab.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clickable {
                                    activeTabId = tab.id
                                    isTabManagerOpen = false
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFF35363A) else Color(0xFF2B2C2F)
                            ),
                            border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Card Header
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF1F2023))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (tab.favicon != null) {
                                        Image(
                                            bitmap = tab.favicon!!.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Language,
                                            contentDescription = null,
                                            tint = Color.Gray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    Text(
                                        text = tab.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )

                                    IconButton(
                                        onClick = { closeTab(tab) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close Tab",
                                            tint = Color.LightGray,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                // Card Content Preview Area
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = try { Uri.parse(tab.url).host ?: tab.url } catch (e: Exception) { tab.url },
                                            fontSize = 11.sp,
                                            color = Color.LightGray,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = tab.title,
                                            fontSize = 12.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Tab Manager Bottom Action Bar
                Surface(
                    color = Color(0xFF292A2D),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (lastClosedTabs.isNotEmpty()) {
                            TextButton(onClick = { restoreLastClosedTab() }) {
                                Icon(Icons.Default.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Restore Closed Tab", color = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        Button(
                            onClick = { createNewTab() },
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New Tab")
                        }
                    }
                }
            }
        } else {
            // Standard Web View Container with Chrome Controls Header
            Column(modifier = Modifier.fillMaxSize()) {
                // Chrome Top Bar
                Surface(
                    color = Color(0xFF202124),
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .zIndex(10f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Back Navigation Button (Hidden as requested)

                        Spacer(modifier = Modifier.weight(1f))

                        // Chrome Tab Counter Icon Button (Hidden as requested)

                        // Options Menu
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.White)
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("New Tab") },
                                    onClick = {
                                        showMenu = false
                                        createNewTab()
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = "Downloads",
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text("Downloads")
                                        }
                                    },
                                    onClick = {
                                        showMenu = false
                                        isDownloadsOpen = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Checkbox(
                                                checked = isDesktopSite,
                                                onCheckedChange = null
                                            )
                                            Text("Desktop Site")
                                        }
                                    },
                                    onClick = {
                                        val newState = !isDesktopSite
                                        isDesktopSite = newState
                                        prefs.edit().putBoolean("desktop_site_enabled", newState).apply()
                                        showMenu = false

                                        activeTab.webView?.let { webView ->
                                            webView.settings.apply {
                                                useWideViewPort = true
                                                loadWithOverviewMode = newState
                                                javaScriptEnabled = true
                                                if (newState) {
                                                    userAgentString = desktopUserAgent
                                                } else {
                                                    userAgentString = if (defaultUserAgent.isNotBlank()) defaultUserAgent else null
                                                }
                                            }
                                            activeTab.hasError = false
                                            webView.reload()
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Refresh") },
                                    onClick = {
                                        showMenu = false
                                        activeTab.hasError = false
                                        activeTab.webView?.reload()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Close Tab") },
                                    onClick = {
                                        showMenu = false
                                        closeTab(activeTab)
                                    }
                                )
                                if (lastClosedTabs.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Restore Last Closed Tab") },
                                        onClick = {
                                            showMenu = false
                                            restoreLastClosedTab()
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Close All Tabs") },
                                    onClick = {
                                        showMenu = false
                                        closeAllTabs()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Exit") },
                                    onClick = {
                                        showMenu = false
                                        onBack()
                                    }
                                )
                            }
                        }
                    }

                    // Progress Bar
                    if (activeTab.isLoading && activeTab.progress < 100) {
                        LinearProgressIndicator(
                            progress = { activeTab.progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // WebView Container / Error Screen
                Box(modifier = Modifier.weight(1f)) {
                    if (activeTab.hasError) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = "Offline",
                                modifier = Modifier.size(72.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Unable to load page",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = activeTab.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { closeTab(activeTab) }
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Close Tab")
                                }
                                Button(
                                    onClick = {
                                        activeTab.hasError = false
                                        activeTab.errorMessage = "Please check your internet connection or the URL provided by the Academy."
                                        activeTab.webView?.loadUrl(activeTab.url)
                                    }
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Retry")
                                }
                            }
                        }
                    } else {
                        key(activeTab.id) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    val wv = activeTab.webView ?: createWebViewForTab(ctx, activeTab)
                                    (wv.parent as? android.view.ViewGroup)?.removeView(wv)
                                    wv.layoutParams = android.view.ViewGroup.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    wv
                                },
                                update = { webView ->
                                    webView.layoutParams = android.view.ViewGroup.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        if (isDownloadsOpen) {
            DownloadsScreenOverlay(
                onBack = { isDownloadsOpen = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreenOverlay(onBack: () -> Unit) {
    val context = LocalContext.current
    var historyList by remember { mutableStateOf(DownloadHistoryManager.getHistory(context)) }
    var itemToDelete by remember { mutableStateOf<DownloadRecord?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var deleteFileFromStorageChoice by remember { mutableStateOf(false) }

    // Live sync loop every 1 second
    LaunchedEffect(Unit) {
        while (true) {
            historyList = DownloadHistoryManager.syncWithDownloadManager(context)
            delay(1000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .statusBarsPadding()
            .zIndex(200f)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Top Bar
            Surface(
                color = Color(0xFF1F1F1F),
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "Downloads",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )

                    if (historyList.isNotEmpty()) {
                        IconButton(onClick = { showClearAllDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear History", tint = Color.White)
                        }
                    }
                }
            }

            if (historyList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DownloadDone,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No downloads yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Files downloaded from study apps will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(historyList, key = { it.id }) { item ->
                        DownloadItemCard(
                            item = item,
                            onOpen = { openDownloadedFile(context, item) },
                            onShare = { shareDownloadedFile(context, item) },
                            onRetry = {
                                retryDownload(context, item)
                                historyList = DownloadHistoryManager.getHistory(context)
                            },
                            onDelete = { itemToDelete = item }
                        )
                    }
                }
            }
        }
    }

    // Delete Single Item Dialog
    if (itemToDelete != null) {
        val targetItem = itemToDelete!!
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete Download") },
            text = {
                Column {
                    Text("Remove '${targetItem.fileName}' from download history?")
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { deleteFileFromStorageChoice = !deleteFileFromStorageChoice }
                    ) {
                        Checkbox(
                            checked = deleteFileFromStorageChoice,
                            onCheckedChange = { deleteFileFromStorageChoice = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Also delete file from device storage")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        DownloadHistoryManager.delete(context, targetItem.id, deleteFileFromStorageChoice)
                        historyList = DownloadHistoryManager.getHistory(context)
                        itemToDelete = null
                        deleteFileFromStorageChoice = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear All Dialog
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear All Downloads") },
            text = {
                Column {
                    Text("Are you sure you want to clear all download history?")
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { deleteFileFromStorageChoice = !deleteFileFromStorageChoice }
                    ) {
                        Checkbox(
                            checked = deleteFileFromStorageChoice,
                            onCheckedChange = { deleteFileFromStorageChoice = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Also delete files from device storage")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        DownloadHistoryManager.clearAll(context, deleteFileFromStorageChoice)
                        historyList = emptyList()
                        showClearAllDialog = false
                        deleteFileFromStorageChoice = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DownloadItemCard(
    item: DownloadRecord,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.status == "Completed") { onOpen() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF232429)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // File Type Icon Box
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                item.fileName.endsWith(".pdf", ignoreCase = true) -> Color(0xFFE53935).copy(alpha = 0.2f)
                                item.fileName.endsWith(".apk", ignoreCase = true) -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                                item.mimeType.contains("video") || item.fileName.endsWith(".mp4", ignoreCase = true) -> Color(0xFF9C27B0).copy(alpha = 0.2f)
                                item.mimeType.contains("image") -> Color(0xFF2196F3).copy(alpha = 0.2f)
                                item.mimeType.contains("zip") || item.fileName.endsWith(".zip", ignoreCase = true) -> Color(0xFFFF9800).copy(alpha = 0.2f)
                                else -> Color(0xFF607D8B).copy(alpha = 0.2f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            item.fileName.endsWith(".pdf", ignoreCase = true) -> Icons.Default.PictureAsPdf
                            item.fileName.endsWith(".apk", ignoreCase = true) -> Icons.Default.Android
                            item.mimeType.contains("video") || item.fileName.endsWith(".mp4", ignoreCase = true) -> Icons.Default.VideoFile
                            item.mimeType.contains("image") -> Icons.Default.Image
                            item.mimeType.contains("zip") || item.fileName.endsWith(".zip", ignoreCase = true) -> Icons.Default.FolderZip
                            else -> Icons.Default.InsertDriveFile
                        },
                        contentDescription = null,
                        tint = when {
                            item.fileName.endsWith(".pdf", ignoreCase = true) -> Color(0xFFEF5350)
                            item.fileName.endsWith(".apk", ignoreCase = true) -> Color(0xFF66BB6A)
                            item.mimeType.contains("video") || item.fileName.endsWith(".mp4", ignoreCase = true) -> Color(0xFFAB47BC)
                            item.mimeType.contains("image") -> Color(0xFF42A5F5)
                            item.mimeType.contains("zip") || item.fileName.endsWith(".zip", ignoreCase = true) -> Color(0xFFFFA726)
                            else -> Color(0xFF78909C)
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    val statusColor = when (item.status) {
                        "Completed" -> Color(0xFF4CAF50)
                        "Downloading" -> Color(0xFF2196F3)
                        "Failed" -> Color(0xFFF44336)
                        "Paused" -> Color(0xFFFF9800)
                        else -> Color.Gray
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = statusColor.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = item.status,
                                fontSize = 11.sp,
                                color = statusColor,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        val sizeStr = if (item.totalBytes > 0) formatFileSize(item.totalBytes) else if (item.downloadedBytes > 0) formatFileSize(item.downloadedBytes) else ""
                        if (sizeStr.isNotBlank()) {
                            Text(
                                text = sizeStr,
                                fontSize = 11.sp,
                                color = Color.LightGray
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "•", fontSize = 11.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        Text(
                            text = formatTimestamp(item.timestamp),
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray, modifier = Modifier.size(20.dp))
                }
            }

            // Progress Bar for Downloading status
            if (item.status == "Downloading") {
                Spacer(modifier = Modifier.height(10.dp))
                val progress = if (item.totalBytes > 0) item.downloadedBytes.toFloat() / item.totalBytes.toFloat() else null
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFF2196F3),
                        trackColor = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val percent = (progress * 100).toInt()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$percent%",
                            fontSize = 11.sp,
                            color = Color(0xFF2196F3)
                        )
                        Text(
                            text = "${formatFileSize(item.downloadedBytes)} / ${formatFileSize(item.totalBytes)}",
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                    }
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFF2196F3),
                        trackColor = Color.DarkGray
                    )
                }
            }

            // Error message if Failed
            if (item.status == "Failed" && !item.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.errorMessage,
                    fontSize = 11.sp,
                    color = Color(0xFFEF5350)
                )
            }

            // Action Buttons
            if (item.status == "Completed" || item.status == "Failed") {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.status == "Completed") {
                        OutlinedButton(
                            onClick = onShare,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share", fontSize = 12.sp, color = Color.White)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = onOpen,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open", fontSize = 12.sp)
                        }
                    } else if (item.status == "Failed") {
                        Button(
                            onClick = onRetry,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedHeartButton(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isTapped by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isTapped) 1.45f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        finishedListener = {
            isTapped = false
        },
        label = "heartScale"
    )
    val heartColor by animateColorAsState(
        targetValue = if (isFavorite) Color(0xFFFF2A6D) else Color(0xFF64748B),
        animationSpec = tween(durationMillis = 200),
        label = "heart_color"
    )

    IconButton(
        onClick = {
            isTapped = true
            onToggle()
        },
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .minimumInteractiveComponentSize()
            .testTag("heart_button")
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = "Toggle Favourite",
            tint = heartColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

