package com.example.service

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.example.data.AcademyDatabase
import com.example.data.FocusSessionEntity
import com.example.data.FocusSettingEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FocusTimerUiState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val totalSeconds: Int = 25 * 60,
    val remainingSeconds: Int = 25 * 60,
    val mode: String = "STANDARD", // "STANDARD", "POMODORO", "CUSTOM_POMODORO"
    val isPomodoroBreak: Boolean = false,
    val pomodoroCycleIndex: Int = 1,
    val pomodoroCyclesTotal: Int = 4,
    val isStrictMode: Boolean = false,
    val appsBlockedCount: Int = 0,
    val userEmail: String = "",
    val subject: String = "Mathematics",
    val chapter: String = "",
    val topic: String = "",
    val target: String = "",
    val notes: String = ""
)

object FocusManager {
    private const val TAG = "FocusManager"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _uiState = MutableStateFlow(FocusTimerUiState())
    val uiState = _uiState.asStateFlow()

    // Fast in-memory cache for FocusAccessibilityService and Watchdog
    @Volatile
    private var _isMasterBlockingEnabled: Boolean = false
    val isMasterBlockingEnabled: Boolean
        get() = _isMasterBlockingEnabled

    @Volatile
    private var isPomodoroBreakActive: Boolean = false

    val isBlockingActive: Boolean
        get() = (_isMasterBlockingEnabled || _uiState.value.isRunning) && !isPomodoroBreakActive

    @Volatile
    var blockedPackageSet: Set<String> = emptySet()
        private set

    @Volatile
    var blockedDomainSet: Set<String> = emptySet()
        private set

    @Volatile
    var allowedChannelSet: Set<String> = emptySet()
        private set

    @Volatile
    var blockShortsAndReels: Boolean = true
        private set

    @Volatile
    var isYouTubeStudyModeEnabled: Boolean = true

    @Volatile
    var blockYouTubeShorts: Boolean = true

    private var usageWatchdogJob: Job? = null
    private var lastOverlayTriggerTime: Long = 0L

    var onSessionCompletedListener: ((FocusSessionEntity) -> Unit)? = null

    fun setMasterBlockingEnabled(context: Context, enabled: Boolean) {
        _isMasterBlockingEnabled = enabled
        try {
            val prefs = context.getSharedPreferences("shadow_focus_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("master_blocking_enabled", enabled).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save master blocking state: ${e.message}")
        }

        if (enabled) {
            startUsageWatchdog(context.applicationContext)
        } else if (!_uiState.value.isRunning) {
            stopUsageWatchdog()
        }
    }

    fun setYouTubeStudyModeEnabled(context: Context, enabled: Boolean) {
        isYouTubeStudyModeEnabled = enabled
        try {
            val prefs = context.getSharedPreferences("shadow_focus_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("youtube_study_mode_enabled", enabled).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save youtube study mode: ${e.message}")
        }
    }

    fun setBlockYouTubeShorts(context: Context, enabled: Boolean) {
        blockYouTubeShorts = enabled
        try {
            val prefs = context.getSharedPreferences("shadow_focus_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("block_youtube_shorts", enabled).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save block youtube shorts: ${e.message}")
        }
    }

    fun persistPreferences(context: Context) {
        try {
            val prefs = context.getSharedPreferences("shadow_focus_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("master_blocking_enabled", _isMasterBlockingEnabled)
                .putBoolean("youtube_study_mode_enabled", isYouTubeStudyModeEnabled)
                .putBoolean("block_youtube_shorts", blockYouTubeShorts)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist preferences: ${e.message}")
        }
    }

    fun initFromPrefs(context: Context) {
        try {
            val prefs = context.getSharedPreferences("shadow_focus_prefs", Context.MODE_PRIVATE)
            _isMasterBlockingEnabled = prefs.getBoolean("master_blocking_enabled", false)
            isYouTubeStudyModeEnabled = prefs.getBoolean("youtube_study_mode_enabled", true)
            blockYouTubeShorts = prefs.getBoolean("block_youtube_shorts", true)
            AdultWebFilterGuard.init(context)

            // Restore blocked packages and domains from preferences if cached
            val savedBlockedPkgs = prefs.getStringSet("cached_blocked_packages", null)
            if (savedBlockedPkgs != null) {
                blockedPackageSet = savedBlockedPkgs
            }
            val savedBlockedDomains = prefs.getStringSet("cached_blocked_domains", null)
            if (savedBlockedDomains != null) {
                blockedDomainSet = savedBlockedDomains
            }
            val savedAllowedChannels = prefs.getStringSet("cached_allowed_channels", null)
            if (savedAllowedChannels != null) {
                allowedChannelSet = savedAllowedChannels
            }

            // Check if a Focus Session was running before app was closed
            val isSessionActive = prefs.getBoolean("session_is_active", false)
            if (isSessionActive) {
                val targetEndEpoch = prefs.getLong("session_target_end_epoch", 0L)
                val totalSecs = prefs.getInt("session_total_seconds", 25 * 60)
                val mode = prefs.getString("session_mode", "STANDARD") ?: "STANDARD"
                val isStrict = prefs.getBoolean("session_is_strict", false)
                val userEmail = prefs.getString("session_user_email", "") ?: ""
                val subject = prefs.getString("session_subject", "Mathematics") ?: "Mathematics"
                val chapter = prefs.getString("session_chapter", "") ?: ""
                val topic = prefs.getString("session_topic", "") ?: ""
                val target = prefs.getString("session_target", "") ?: ""
                val notes = prefs.getString("session_notes", "") ?: ""
                val blockedAppsCount = prefs.getInt("session_blocked_apps_count", blockedPackageSet.size)
                val cycleIdx = prefs.getInt("session_pomodoro_cycle_index", 1)
                val cyclesTotal = prefs.getInt("session_pomodoro_cycles_total", 4)

                val now = System.currentTimeMillis()
                val remainingSecs = ((targetEndEpoch - now) / 1000L).toInt()

                if (remainingSecs > 0) {
                    _uiState.value = FocusTimerUiState(
                        isRunning = true,
                        isPaused = false,
                        totalSeconds = totalSecs,
                        remainingSeconds = remainingSecs,
                        mode = mode,
                        isPomodoroBreak = false,
                        pomodoroCycleIndex = cycleIdx,
                        pomodoroCyclesTotal = cyclesTotal,
                        isStrictMode = isStrict,
                        appsBlockedCount = blockedAppsCount,
                        userEmail = userEmail,
                        subject = subject,
                        chapter = chapter,
                        topic = topic,
                        target = target,
                        notes = notes
                    )
                    startUsageWatchdog(context.applicationContext)
                } else {
                    // Time elapsed while app was closed -> auto finalize
                    endFocusSession(context, completed = true)
                }
            } else if (_isMasterBlockingEnabled) {
                startUsageWatchdog(context.applicationContext)
            }
        } catch (e: Exception) {
            Log.e(TAG, "initFromPrefs error: ${e.message}")
        }
    }

    fun triggerBlockOverlay(
        context: Context,
        blockedTarget: String,
        reason: String,
        isYouTubeStudy: Boolean = false,
        isShorts: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        if (now - lastOverlayTriggerTime < 1200L) return
        lastOverlayTriggerTime = now

        try {
            val intent = Intent(context, com.example.ui.screens.BlockedAppOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("BLOCKED_PKG", blockedTarget)
                putExtra("BLOCKED_REASON", reason)
                putExtra("IS_YOUTUBE_STUDY", isYouTubeStudy)
                putExtra("IS_SHORTS", isShorts)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "triggerBlockOverlay failed: ${e.message}", e)
        }
    }

    fun startUsageWatchdog(context: Context) {
        if (usageWatchdogJob?.isActive == true) return
        usageWatchdogJob = scope.launch(Dispatchers.IO) {
            val appContext = context.applicationContext
            while (isActive && isBlockingActive) {
                try {
                    delay(1000L)
                    if (!isBlockingActive || blockedPackageSet.isEmpty()) continue

                    // Check top foreground package via UsageStats
                    val usm = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
                    if (usm != null) {
                        val endTime = System.currentTimeMillis()
                        val startTime = endTime - 3500L
                        val events = usm.queryEvents(startTime, endTime)
                        val event = android.app.usage.UsageEvents.Event()
                        var topPackage: String? = null

                        while (events.hasNextEvent()) {
                            events.getNextEvent(event)
                            if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED ||
                                event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) {
                                topPackage = event.packageName
                            }
                        }

                        if (topPackage != null && topPackage != appContext.packageName && blockedPackageSet.contains(topPackage)) {
                            withContext(Dispatchers.Main) {
                                triggerBlockOverlay(appContext, topPackage, "App is blocked by SHADOW X RAHUL Focus Protection")
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore watchdog iteration error
                }
            }
        }
    }

    fun stopUsageWatchdog() {
        usageWatchdogJob?.cancel()
        usageWatchdogJob = null
    }

    fun updateCache(
        blockedPackages: Set<String>,
        blockedDomains: Set<String>,
        allowedChannels: Set<String>,
        blockShorts: Boolean,
        context: Context? = null
    ) {
        blockedPackageSet = blockedPackages
        blockedDomainSet = blockedDomains
        allowedChannelSet = allowedChannels
        blockShortsAndReels = blockShorts

        if (context != null) {
            try {
                val prefs = context.getSharedPreferences("shadow_focus_prefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putStringSet("cached_blocked_packages", blockedPackages)
                    .putStringSet("cached_blocked_domains", blockedDomains)
                    .putStringSet("cached_allowed_channels", allowedChannels)
                    .putBoolean("block_youtube_shorts", blockShorts)
                    .apply()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cache focus settings to prefs: ${e.message}")
            }
        }
    }

    fun startFocusSession(
        context: Context,
        userEmail: String,
        durationMinutes: Int,
        mode: String = "STANDARD",
        isStrictMode: Boolean = false,
        pomodoroSettings: FocusSettingEntity? = null,
        blockedAppsCount: Int = blockedPackageSet.size,
        subject: String = "Mathematics",
        chapter: String = "",
        topic: String = "",
        target: String = "",
        notes: String = ""
    ) {
        val totalSecs = if (mode == "POMODORO") {
            (pomodoroSettings?.pomodoroStudyMinutes ?: 25) * 60
        } else {
            durationMinutes * 60
        }

        _uiState.value = FocusTimerUiState(
            isRunning = true,
            isPaused = false,
            totalSeconds = totalSecs,
            remainingSeconds = totalSecs,
            mode = mode,
            isPomodoroBreak = false,
            pomodoroCycleIndex = 1,
            pomodoroCyclesTotal = pomodoroSettings?.pomodoroCycles ?: 4,
            isStrictMode = isStrictMode,
            appsBlockedCount = blockedAppsCount,
            userEmail = userEmail,
            subject = subject,
            chapter = chapter,
            topic = topic,
            target = target,
            notes = notes
        )

        isPomodoroBreakActive = false
        startUsageWatchdog(context.applicationContext)

        // Persist active session state to SharedPreferences so it survives app close / kill
        try {
            val nowEpoch = System.currentTimeMillis()
            val targetEndEpoch = nowEpoch + (totalSecs * 1000L)
            val prefs = context.getSharedPreferences("shadow_focus_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("session_is_active", true)
                .putLong("session_start_epoch", nowEpoch)
                .putLong("session_target_end_epoch", targetEndEpoch)
                .putInt("session_total_seconds", totalSecs)
                .putString("session_mode", mode)
                .putBoolean("session_is_strict", isStrictMode)
                .putString("session_user_email", userEmail)
                .putString("session_subject", subject)
                .putString("session_chapter", chapter)
                .putString("session_topic", topic)
                .putString("session_target", target)
                .putString("session_notes", notes)
                .putInt("session_blocked_apps_count", blockedAppsCount)
                .putInt("session_pomodoro_cycle_index", 1)
                .putInt("session_pomodoro_cycles_total", pomodoroSettings?.pomodoroCycles ?: 4)
                .putStringSet("cached_blocked_packages", blockedPackageSet)
                .putStringSet("cached_blocked_domains", blockedDomainSet)
                .putStringSet("cached_allowed_channels", allowedChannelSet)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist active session to prefs: ${e.message}")
        }

        // Notification suppression if enabled
        if (pomodoroSettings?.blockNotifications == true) {
            enableDndIfPermitted(context)
        }

        // Start Foreground Service
        val serviceIntent = Intent(context, FocusTimerForegroundService::class.java).apply {
            action = FocusTimerForegroundService.ACTION_START
            putExtra("USER_EMAIL", userEmail)
            putExtra("DURATION_SECONDS", totalSecs)
            putExtra("MODE", mode)
            putExtra("STRICT_MODE", isStrictMode)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    fun pauseFocusSession(context: Context) {
        if (!_uiState.value.isRunning || _uiState.value.isStrictMode) return
        _uiState.value = _uiState.value.copy(isPaused = true)
        val intent = Intent(context, FocusTimerForegroundService::class.java).apply {
            action = FocusTimerForegroundService.ACTION_PAUSE
        }
        context.startService(intent)
    }

    fun resumeFocusSession(context: Context) {
        if (!_uiState.value.isRunning) return
        _uiState.value = _uiState.value.copy(isPaused = false)
        val intent = Intent(context, FocusTimerForegroundService::class.java).apply {
            action = FocusTimerForegroundService.ACTION_RESUME
        }
        context.startService(intent)
    }

    fun endFocusSession(context: Context, completed: Boolean = false, emergencyUnlock: Boolean = false) {
        val currentState = _uiState.value
        if (currentState.isStrictMode && !completed && !emergencyUnlock) {
            Log.w(TAG, "Cannot end session: Strict mode is active")
            return
        }

        // Clear active session from SharedPreferences
        try {
            val prefs = context.getSharedPreferences("shadow_focus_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("session_is_active", false)
                .remove("session_start_epoch")
                .remove("session_target_end_epoch")
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear active session in prefs: ${e.message}")
        }

        val sessionDurationMin = ((currentState.totalSeconds - currentState.remainingSeconds) / 60).coerceAtLeast(1)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val finalDuration = if (completed) (currentState.totalSeconds / 60) else sessionDurationMin
        val calculatedXp = (finalDuration * 1) + (if (completed) 10 else 0)

        val sessionEntity = FocusSessionEntity(
            userEmail = currentState.userEmail,
            startTime = System.currentTimeMillis() - (sessionDurationMin * 60 * 1000L),
            endTime = System.currentTimeMillis(),
            durationMinutes = finalDuration,
            sessionType = currentState.mode,
            appsBlockedCount = currentState.appsBlockedCount,
            completed = completed,
            dateStr = todayStr,
            pomodoroCyclesCompleted = if (currentState.mode == "POMODORO") currentState.pomodoroCycleIndex else 0,
            subject = currentState.subject,
            chapter = currentState.chapter,
            topic = currentState.topic,
            target = currentState.target,
            notes = currentState.notes,
            xpEarned = calculatedXp
        )

        // Save session locally to Room database
        scope.launch(Dispatchers.IO) {
            try {
                AcademyDatabase.getDatabase(context).academyDao().insertFocusSession(sessionEntity)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save focus session: ${e.message}")
            }
        }

        _uiState.value = FocusTimerUiState(
            isRunning = false,
            isPaused = false,
            totalSeconds = 25 * 60,
            remainingSeconds = 25 * 60
        )

        isPomodoroBreakActive = false
        if (!_isMasterBlockingEnabled) {
            stopUsageWatchdog()
        }

        // Stop Audio if running
        FocusAudioSynthesizer.stopSound()

        // Stop foreground service
        val intent = Intent(context, FocusTimerForegroundService::class.java).apply {
            action = FocusTimerForegroundService.ACTION_STOP
        }
        context.startService(intent)

        disableDndIfPermitted(context)

        if (completed) {
            onSessionCompletedListener?.invoke(sessionEntity)
        }
    }

    fun updateTimerTick(remainingSecs: Int, totalSecs: Int) {
        _uiState.value = _uiState.value.copy(
            remainingSeconds = remainingSecs,
            totalSeconds = totalSecs
        )
    }

    fun transitionPomodoroPhase(context: Context, toBreak: Boolean, durationSecs: Int, cycleIndex: Int) {
        _uiState.value = _uiState.value.copy(
            isPomodoroBreak = toBreak,
            totalSeconds = durationSecs,
            remainingSeconds = durationSecs,
            pomodoroCycleIndex = cycleIndex
        )
        // If in break, allow temporary relaxation of blocking
        isPomodoroBreakActive = toBreak
    }

    // === Permission Utilities ===

    fun hasUsageStatsPermission(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun hasAccessibilityPermission(context: Context): Boolean {
        val expectedServiceName = "${context.packageName}/${FocusAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(expectedServiceName) || enabledServices.contains(FocusAccessibilityService::class.java.simpleName)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }

    // Permission Intent Launchers
    fun openUsageStatsSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }
    }

    fun openOverlaySettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            }
        }
    }

    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open accessibility settings: ${e.message}")
        }
    }

    fun openNotificationSettings(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } else {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open notification settings: ${e.message}")
        }
    }

    fun requestBatteryOptimizationExemption(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }

    private fun enableDndIfPermitted(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not set DND: ${e.message}")
        }
    }

    private fun disableDndIfPermitted(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not restore DND: ${e.message}")
        }
    }
}
