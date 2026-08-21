package com.example.util

import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.api.SupabaseApi
import com.example.data.AcademyDao
import com.example.data.NotificationEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AlertSystemManager(
    private val context: Context,
    private val api: SupabaseApi,
    private val academyDao: AcademyDao
) {
    private val prefs = context.getSharedPreferences("alert_system_prefs", Context.MODE_PRIVATE)

    private val _activePopupNotification = MutableStateFlow<NotificationEntity?>(null)
    val activePopupNotification: StateFlow<NotificationEntity?> = _activePopupNotification.asStateFlow()

    private var pollJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun getStoredNotificationId(): Int {
        return prefs.getInt("last_shown_notification_id", 0)
    }

    fun saveStoredNotificationId(id: Int) {
        prefs.edit().putInt("last_shown_notification_id", id).apply()
        Log.d("AlertSystem", "[ALERT SYSTEM] Notification Saved: Stored ID set to $id")
    }

    fun dismissPopup() {
        _activePopupNotification.value = null
    }

    fun startPolling() {
        stopPolling()
        Log.d("AlertSystem", "[ALERT SYSTEM] Checking Notifications - Starting 30s timer")
        pollJob = scope.launch {
            while (isActive) {
                checkNotifications()
                delay(30000L)
            }
        }
    }

    fun stopPolling() {
        if (pollJob != null) {
            Log.d("AlertSystem", "[ALERT SYSTEM] Stopping notification polling (App in background)")
            pollJob?.cancel()
            pollJob = null
        }
    }

    suspend fun checkNotifications() {
        Log.i("AlertSystem", "[ALERT SYSTEM] Checking Notifications")
        try {
            val remoteNotifications = try {
                api.getAllNotifications()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("AlertSystem", "[ALERT SYSTEM] Errors fetching notifications from Supabase: ${e.message}", e)
                emptyList()
            }

            Log.i("AlertSystem", "[ALERT SYSTEM] Supabase Response: count=${remoteNotifications.size}")

            if (remoteNotifications.isEmpty()) {
                Log.i("AlertSystem", "[ALERT SYSTEM] Supabase Response: Empty table")
                return
            }

            // Cache to local Room database for offline history
            remoteNotifications.forEach { notif ->
                try {
                    academyDao.insertNotification(notif)
                } catch (e: Exception) {
                    // Ignore room constraint duplicate errors
                }
            }

            val latestNotif = remoteNotifications.maxByOrNull { it.id } ?: return
            val latestId = latestNotif.id
            val storedId = getStoredNotificationId()

            Log.i("AlertSystem", "[ALERT SYSTEM] Latest Notification ID: $latestId")
            Log.i("AlertSystem", "[ALERT SYSTEM] Stored Notification ID: $storedId")

            if (latestId > storedId) {
                Log.i("AlertSystem", "[ALERT SYSTEM] Popup Decision: SHOWING POPUP for ID $latestId (latest $latestId > stored $storedId)")
                
                // Store the new ID locally
                saveStoredNotificationId(latestId)

                // Show popup
                _activePopupNotification.value = latestNotif

                // Play sound and vibration
                playAlertSoundAndVibrate()
            } else {
                Log.i("AlertSystem", "[ALERT SYSTEM] Popup Decision: DO NOTHING (latest $latestId <= stored $storedId)")
            }

        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("AlertSystem", "[ALERT SYSTEM] Errors: ${e.message}", e)
        }
    }

    private fun playAlertSoundAndVibrate() {
        try {
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, notificationUri)
            ringtone?.play()
        } catch (e: Exception) {
            Log.e("AlertSystem", "[ALERT SYSTEM] Errors playing alert sound: ${e.message}")
        }

        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(400)
                }
            }
        } catch (e: Exception) {
            Log.e("AlertSystem", "[ALERT SYSTEM] Errors vibrating device: ${e.message}")
        }
    }
}
