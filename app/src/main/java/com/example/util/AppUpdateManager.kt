package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.AppUpdateEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

sealed class UpdateDownloadState {
    object Idle : UpdateDownloadState()
    object Checking : UpdateDownloadState()
    data class UpdateAvailable(val update: AppUpdateEntity, val isForce: Boolean) : UpdateDownloadState()
    object NoUpdate : UpdateDownloadState()
    
    data class Downloading(
        val percentage: Int,
        val speedKbps: Double, // KB/s
        val remainingSeconds: Long,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val isResumed: Boolean = false
    ) : UpdateDownloadState()
    
    data class ReadyToInstall(val apkFile: File) : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}

class AppUpdateManager(private val context: Context) {
    
    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()
    
    private var downloadJob: Job? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
        
    private val apkFile = File(context.getExternalFilesDir("updates"), "lakshya_academy_update.apk")
    
    init {
        cleanup()
    }
    
    fun setIdle() {
        _downloadState.value = UpdateDownloadState.Idle
    }

    fun setUpdateAvailable(update: AppUpdateEntity, isForce: Boolean) {
        _downloadState.value = UpdateDownloadState.UpdateAvailable(update, isForce)
    }

    fun cleanup() {
        try {
            if (apkFile.exists()) {
                apkFile.delete()
                Log.d("AppUpdateManager", "[UPDATE SYSTEM] Cleaned up old downloaded update file.")
            }
        } catch (e: Exception) {
            Log.e("AppUpdateManager", "[UPDATE SYSTEM] Error cleaning up update file", e)
        }
    }
    
    fun startDownload(update: AppUpdateEntity) {
        if (update.apkUrl.isBlank()) {
            _downloadState.value = UpdateDownloadState.Error("Invalid APK URL")
            return
        }
        
        downloadJob?.cancel()
        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            var input: InputStream? = null
            var out: FileOutputStream? = null
            try {
                Log.d("AppUpdateManager", "[UPDATE SYSTEM] Checking if APK URL exists: ${update.apkUrl}")
                
                // Get current length of partially downloaded file for resume
                val existingLength = if (apkFile.exists()) apkFile.length() else 0L
                val requestBuilder = Request.Builder().url(update.apkUrl)
                
                var isResuming = false
                if (existingLength > 0L) {
                    requestBuilder.header("Range", "bytes=$existingLength-")
                    isResuming = true
                    Log.d("AppUpdateManager", "[UPDATE SYSTEM] Attempting to resume from byte: $existingLength")
                }
                
                val response = client.newCall(requestBuilder.build()).execute()
                
                if (!response.isSuccessful) {
                    Log.e("AppUpdateManager", "[UPDATE SYSTEM] Server returned unsuccessful code: ${response.code}")
                    if (isResuming) {
                        Log.w("AppUpdateManager", "[UPDATE SYSTEM] Range request failed. Retrying from 0.")
                        apkFile.delete()
                        // Restart download from 0
                        startDownload(update)
                    } else {
                        _downloadState.value = UpdateDownloadState.Error("Server error: ${response.code}")
                    }
                    return@launch
                }
                
                val body = response.body
                if (body == null) {
                    _downloadState.value = UpdateDownloadState.Error("Empty server response body")
                    return@launch
                }
                
                val responseCode = response.code
                val isRangeSupported = responseCode == 206
                
                val finalLength = if (isRangeSupported) {
                    existingLength + body.contentLength()
                } else {
                    body.contentLength()
                }
                
                if (finalLength <= 0) {
                    _downloadState.value = UpdateDownloadState.Error("Invalid content length from server")
                    return@launch
                }
                
                val append = isRangeSupported && existingLength > 0
                if (!append && apkFile.exists()) {
                    apkFile.delete()
                }
                
                if (apkFile.parentFile?.exists() == false) {
                    apkFile.parentFile?.mkdirs()
                }
                
                input = body.byteStream()
                out = FileOutputStream(apkFile, append)
                
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalDownloaded = if (append) existingLength else 0L
                
                val startTime = System.currentTimeMillis()
                var lastProgressUpdateTime = System.currentTimeMillis()
                
                while (isActive) {
                    bytesRead = input.read(buffer)
                    if (bytesRead == -1) break
                    
                    out.write(buffer, 0, bytesRead)
                    totalDownloaded += bytesRead
                    
                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdateTime >= 150L || totalDownloaded == finalLength) {
                        val durationMs = (now - startTime).coerceAtLeast(1)
                        val totalDurationSec = durationMs / 1000.0
                        
                        val currentSessionBytes = if (append) totalDownloaded - existingLength else totalDownloaded
                        val speedKbps = if (totalDurationSec > 0) {
                            (currentSessionBytes / 1024.0) / totalDurationSec
                        } else {
                            0.0
                        }
                        
                        val percentage = ((totalDownloaded * 100) / finalLength).toInt().coerceIn(0, 100)
                        
                        val remainingBytes = (finalLength - totalDownloaded).coerceAtLeast(0L)
                        val remainingSec = if (speedKbps > 0) {
                            (remainingBytes / 1024.0 / speedKbps).toLong().coerceAtLeast(0L)
                        } else {
                            999L
                        }
                        
                        _downloadState.value = UpdateDownloadState.Downloading(
                            percentage = percentage,
                            speedKbps = speedKbps,
                            remainingSeconds = remainingSec,
                            downloadedBytes = totalDownloaded,
                            totalBytes = finalLength,
                            isResumed = append
                        )
                        lastProgressUpdateTime = now
                    }
                }
                
                if (isActive) {
                    Log.i("AppUpdateManager", "[UPDATE SYSTEM] Download completed: ${apkFile.absolutePath}")
                    _downloadState.value = UpdateDownloadState.ReadyToInstall(apkFile)
                } else {
                    Log.i("AppUpdateManager", "[UPDATE SYSTEM] Download cancelled.")
                }
                
            } catch (e: Exception) {
                Log.e("AppUpdateManager", "[UPDATE SYSTEM] Download failed: ${e.message}", e)
                _downloadState.value = UpdateDownloadState.Error("Network failure or connection slow. Please retry.")
            } finally {
                try { input?.close() } catch (e: Exception) {}
                try { out?.close() } catch (e: Exception) {}
            }
        }
    }
    
    fun cancelDownload() {
        Log.i("AppUpdateManager", "[UPDATE SYSTEM] Cancelling update download.")
        downloadJob?.cancel()
        _downloadState.value = UpdateDownloadState.Idle
    }
    
    fun installApk(file: File = apkFile) {
        if (!file.exists()) {
            _downloadState.value = UpdateDownloadState.Error("APK file not found for installation")
            return
        }
        
        try {
            Log.i("AppUpdateManager", "[UPDATE SYSTEM] Launching installer for ${file.absolutePath}")
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                val uri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                setDataAndType(uri, "application/vnd.android.package-archive")
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Log.w("AppUpdateManager", "[UPDATE SYSTEM] Request install unknown packages permission needed.")
                    val settingsIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }
            
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("AppUpdateManager", "[UPDATE SYSTEM] Error launching installer: ${e.message}", e)
            _downloadState.value = UpdateDownloadState.Error("Failed to launch Package Installer: ${e.message}")
        }
    }
}
