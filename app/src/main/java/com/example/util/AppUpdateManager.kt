package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.example.data.AppUpdateEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class UpdateDownloadState {
    object Idle : UpdateDownloadState()
    object Checking : UpdateDownloadState()
    data class UpdateAvailable(val update: AppUpdateEntity, val isForce: Boolean) : UpdateDownloadState()
    object NoUpdate : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}

class AppUpdateManager(private val context: Context) {
    
    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()
    
    fun setIdle() {
        _downloadState.value = UpdateDownloadState.Idle
    }

    fun setUpdateAvailable(update: AppUpdateEntity, isForce: Boolean) {
        _downloadState.value = UpdateDownloadState.UpdateAvailable(update, isForce)
    }

    fun cleanup() {
        // Cleanup unnecessary files if any previously existed
    }
    
    fun isValidUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val trimmed = url.trim()
        return (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("tg://", ignoreCase = true) ||
                trimmed.startsWith("t.me/", ignoreCase = true))
    }

    fun openUpdateLink(update: AppUpdateEntity): Boolean {
        val targetUrl = update.safeApkUrl.trim()
        Log.d("AppUpdateManager", "[UPDATE SYSTEM] Opening update link: '$targetUrl'")
        
        if (!isValidUrl(targetUrl)) {
            Log.e("AppUpdateManager", "[UPDATE SYSTEM] Invalid or missing update URL: '$targetUrl'")
            Toast.makeText(context, "Update link is unavailable or invalid in database.", Toast.LENGTH_LONG).show()
            return false
        }

        return UpdateUrlUtils.openUpdateLink(context, targetUrl)
    }

    fun openExternalUrl(url: String) {
        UpdateUrlUtils.openUpdateLink(context, url)
    }
}

object UpdateUrlUtils {
    fun openUpdateLink(context: Context, rawUrl: String): Boolean {
        if (rawUrl.isBlank()) {
            Toast.makeText(context, "Update link is missing in database.", Toast.LENGTH_LONG).show()
            return false
        }

        var url = rawUrl.trim()
        if (!url.startsWith("http://", ignoreCase = true) && 
            !url.startsWith("https://", ignoreCase = true) && 
            !url.startsWith("tg://", ignoreCase = true)) {
            url = if (url.startsWith("t.me/", ignoreCase = true)) {
                "https://$url"
            } else {
                "https://$url"
            }
        }

        val uri = try {
            Uri.parse(url)
        } catch (e: Exception) {
            Toast.makeText(context, "Invalid update link URL format.", Toast.LENGTH_LONG).show()
            return false
        }

        // List of Telegram package names to try directly
        val telegramPackages = listOf(
            "org.telegram.messenger",
            "org.telegram.plus",
            "org.telegram.messenger.web",
            "org.thunderdog.challegram"
        )

        var openedInTelegramApp = false
        for (pkgName in telegramPackages) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(pkgName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                openedInTelegramApp = true
                Log.d("AppUpdateManager", "Successfully opened update link in Telegram app ($pkgName)")
                break
            } catch (e: Exception) {
                // Package not installed, continue loop
            }
        }

        if (!openedInTelegramApp) {
            // Fallback: Open in browser or default app handler
            try {
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                Log.d("AppUpdateManager", "Opened update link in external browser: $url")
                return true
            } catch (e: Exception) {
                Log.e("AppUpdateManager", "Failed to open update link in browser: ${e.message}", e)
                Toast.makeText(context, "Unable to open update link: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                return false
            }
        }

        return true
    }
}

