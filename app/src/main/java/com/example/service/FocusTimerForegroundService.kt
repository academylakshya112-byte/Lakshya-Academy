package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.*

class FocusTimerForegroundService : Service() {

    companion object {
        private const val TAG = "FocusTimerService"
        const val CHANNEL_ID = "shadow_focus_timer_channel"
        const val NOTIFICATION_ID = 9991

        const val ACTION_START = "com.example.focus.ACTION_START"
        const val ACTION_PAUSE = "com.example.focus.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.focus.ACTION_RESUME"
        const val ACTION_STOP = "com.example.focus.ACTION_STOP"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var timerJob: Job? = null
    private var remainingSeconds = 25 * 60
    private var totalSeconds = 25 * 60
    private var isPaused = false
    private var mode = "STANDARD"
    private var isStrictMode = false
    private var userEmail = ""
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // System restarted service after app was closed/killed
            restoreSessionFromPrefs()
            return START_STICKY
        }

        when (intent.action) {
            ACTION_START -> {
                remainingSeconds = intent.getIntExtra("DURATION_SECONDS", 25 * 60)
                totalSeconds = remainingSeconds
                mode = intent.getStringExtra("MODE") ?: "STANDARD"
                isStrictMode = intent.getBooleanExtra("STRICT_MODE", false)
                userEmail = intent.getStringExtra("USER_EMAIL") ?: ""
                isPaused = false

                acquireWakeLock()
                startForegroundWithNotification()
                startTimer()
            }
            ACTION_PAUSE -> {
                if (!isStrictMode) {
                    isPaused = true
                    updateNotification()
                }
            }
            ACTION_RESUME -> {
                isPaused = false
                updateNotification()
            }
            ACTION_STOP -> {
                stopTimer()
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                restoreSessionFromPrefs()
            }
        }
        return START_STICKY
    }

    private fun restoreSessionFromPrefs() {
        try {
            val prefs = getSharedPreferences("shadow_focus_prefs", Context.MODE_PRIVATE)
            val isActive = prefs.getBoolean("session_is_active", false)
            if (isActive) {
                val targetEndEpoch = prefs.getLong("session_target_end_epoch", 0L)
                totalSeconds = prefs.getInt("session_total_seconds", 25 * 60)
                mode = prefs.getString("session_mode", "STANDARD") ?: "STANDARD"
                isStrictMode = prefs.getBoolean("session_is_strict", false)
                userEmail = prefs.getString("session_user_email", "") ?: ""

                val now = System.currentTimeMillis()
                val diffSecs = ((targetEndEpoch - now) / 1000L).toInt()

                if (diffSecs > 0) {
                    remainingSeconds = diffSecs
                    isPaused = false
                    acquireWakeLock()
                    startForegroundWithNotification()
                    startTimer()
                    FocusManager.startUsageWatchdog(applicationContext)
                } else {
                    // Session finished while app was closed
                    FocusManager.endFocusSession(applicationContext, completed = true)
                    showSessionCompleteNotification()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring session from prefs: ${e.message}")
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "App task swiped away / closed from recents. Focus service persisting!")

        // If focus session is active, keep the service alive in background!
        val prefs = getSharedPreferences("shadow_focus_prefs", Context.MODE_PRIVATE)
        val isActive = prefs.getBoolean("session_is_active", false)
        if (isActive && remainingSeconds > 0) {
            val restartServiceIntent = Intent(applicationContext, FocusTimerForegroundService::class.java).apply {
                setPackage(packageName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restartServiceIntent)
            } else {
                startService(restartServiceIntent)
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive && remainingSeconds > 0) {
                delay(1000L)
                if (!isPaused) {
                    remainingSeconds--
                    FocusManager.updateTimerTick(remainingSeconds, totalSeconds)
                    if (remainingSeconds % 5 == 0 || remainingSeconds <= 10) {
                        updateNotification()
                    }
                }
            }

            if (remainingSeconds <= 0) {
                withContext(Dispatchers.Main) {
                    FocusManager.endFocusSession(applicationContext, completed = true)
                }
                showSessionCompleteNotification()
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null || wakeLock?.isHeld == false) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "ShadowFocus::TimerWakeLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire(6 * 60 * 60 * 1000L) // 6 hours safety max
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release wake lock: ${e.message}")
        }
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val mins = remainingSeconds / 60
        val secs = remainingSeconds % 60
        val timeFormatted = String.format("%02d:%02d", mins, secs)

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isPaused) "Focus Session Paused ⏸️" else "Focus Study Session Active 🎯 ($timeFormatted)"
        val content = if (isStrictMode) "Strict Mode Active • Stay disciplined!" else "Blocking distracting apps & websites across device"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(totalSeconds, totalSeconds - remainingSeconds, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showSessionCompleteNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Focus Session Complete! 🎯")
            .setContentText("Great job! Your study session is completed successfully.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Focus Study Timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live focus countdown timer and status"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopTimer()
        releaseWakeLock()
        super.onDestroy()
    }
}
