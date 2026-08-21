package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.data.AcademyRepository
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Composable helper to automatically track active study time for a module when screen is visible.
 */
@Composable
fun TrackStudyModule(moduleName: String) {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(moduleName, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    StudyTracker.onModuleStarted(context, moduleName)
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    StudyTracker.onModuleStopped(context, moduleName)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        StudyTracker.onModuleStarted(context, moduleName)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            StudyTracker.onModuleStopped(context, moduleName)
        }
    }
}

object StudyTracker {
    private const val TAG = "StudyTracker"
    private const val PREFS_NAME = "lakshya_study_tracker"
    private const val KEY_GOAL_MINUTES = "study_goal_minutes"
    private const val DEFAULT_GOAL_MINUTES = 45

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // 10 Official Study Modules
    const val MODULE_LIVE_CLASSES = "Live Classes"
    const val MODULE_COURSE_SYLLABUS = "Course Syllabus"
    const val MODULE_AI_COACH = "Lakshya AI Coach"
    const val MODULE_CURRENT_AFFAIRS = "Current Affairs"
    const val MODULE_TEST_SERIES = "Test Series"
    const val MODULE_PREVIOUS_PAPERS = "Previous Papers"
    const val MODULE_EXAM_ALERTS = "Exam Alerts"
    const val MODULE_FREE_BOOKS = "Free Books"
    const val MODULE_TIME_TABLE = "Time Table"
    const val MODULE_STUDY_WEBSITES = "Study Websites"

    // Active timing state
    private val activeModuleStartTimes = ConcurrentHashMap<String, Long>()
    private val activeModuleTimerJobs = ConcurrentHashMap<String, Job>()
    private val trackerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var currentActiveUserEmail: String = ""

    fun setCurrentUserEmail(email: String) {
        if (email.isNotBlank()) {
            currentActiveUserEmail = email
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Start timing automatically when the user opens a study module.
     */
    @Synchronized
    fun onModuleStarted(context: Context, moduleName: String) {
        if (activeModuleStartTimes.containsKey(moduleName)) {
            return
        }
        val startTime = System.currentTimeMillis()
        activeModuleStartTimes[moduleName] = startTime
        Log.d(TAG, "[STUDY TRACKER] Module Started: $moduleName")

        // Active ticker job: increment active seconds every second
        val job = trackerScope.launch {
            while (isActive) {
                delay(1000L)
                if (activeModuleStartTimes.containsKey(moduleName)) {
                    addStudyTimeForModule(context, moduleName, 1)
                } else {
                    break
                }
            }
        }
        activeModuleTimerJobs[moduleName] = job
    }

    /**
     * Pause / stop timing automatically when user exits module or app is backgrounded.
     */
    @Synchronized
    fun onModuleStopped(context: Context, moduleName: String) {
        val startTime = activeModuleStartTimes.remove(moduleName)
        val job = activeModuleTimerJobs.remove(moduleName)
        job?.cancel()

        if (startTime != null) {
            val durationSec = ((System.currentTimeMillis() - startTime) / 1000).toInt()
            Log.d(TAG, "[STUDY TRACKER] Module Stopped: $moduleName, duration = ${durationSec}s")

            val todayStr = dateFormat.format(Date())
            val todayTotalSec = getTodayStudySeconds(context)
            Log.d(TAG, "[STUDY TRACKER] Time Saved: ${todayTotalSec}s for date $todayStr")

            syncWithSupabaseAsync(context)
        }
    }

    /**
     * Pause all active module timers (e.g. app backgrounded or screen off).
     */
    fun pauseAll(context: Context) {
        val activeModules = activeModuleStartTimes.keys.toList()
        for (module in activeModules) {
            onModuleStopped(context, module)
        }
    }

    /**
     * Save study time for a specific module locally for today's date.
     */
    fun addStudyTimeForModule(context: Context, moduleName: String, seconds: Int) {
        if (seconds <= 0) return
        val prefs = getPrefs(context)
        val todayStr = dateFormat.format(Date())

        val keyTotal = "study_sec_$todayStr"
        val keyModule = "mod_sec_${moduleKey(moduleName)}_$todayStr"

        val currentTotal = prefs.getInt(keyTotal, 0)
        val currentModule = prefs.getInt(keyModule, 0)

        prefs.edit()
            .putInt(keyTotal, currentTotal + seconds)
            .putInt(keyModule, currentModule + seconds)
            .apply()
    }

    fun addStudyTime(context: Context, seconds: Int) {
        addStudyTimeForModule(context, MODULE_COURSE_SYLLABUS, seconds)
    }

    private fun moduleKey(moduleName: String): String {
        return when (moduleName) {
            MODULE_LIVE_CLASSES -> "live_classes"
            MODULE_COURSE_SYLLABUS -> "course_syllabus"
            MODULE_AI_COACH -> "ai_coach"
            MODULE_CURRENT_AFFAIRS -> "current_affairs"
            MODULE_TEST_SERIES -> "test_series"
            MODULE_PREVIOUS_PAPERS -> "previous_papers"
            MODULE_EXAM_ALERTS -> "exam_alerts"
            MODULE_FREE_BOOKS -> "free_books"
            MODULE_TIME_TABLE -> "time_table"
            MODULE_STUDY_WEBSITES -> "study_websites"
            else -> moduleName.lowercase(Locale.ROOT).replace(" ", "_")
        }
    }

    fun getModuleStudySeconds(context: Context, moduleName: String, dateStr: String = dateFormat.format(Date())): Int {
        val keyModule = "mod_sec_${moduleKey(moduleName)}_$dateStr"
        return getPrefs(context).getInt(keyModule, 0)
    }

    fun getStudySecondsForDate(context: Context, dateStr: String): Int {
        return getPrefs(context).getInt("study_sec_$dateStr", 0)
    }

    fun getTodayStudySeconds(context: Context): Int {
        val todayStr = dateFormat.format(Date())
        return getStudySecondsForDate(context, todayStr)
    }

    fun getDailyStudyGoalMinutes(context: Context): Int {
        return getPrefs(context).getInt(KEY_GOAL_MINUTES, DEFAULT_GOAL_MINUTES)
    }

    fun setDailyStudyGoalMinutes(context: Context, minutes: Int) {
        val finalMinutes = minutes.coerceAtLeast(5)
        getPrefs(context).edit().putInt(KEY_GOAL_MINUTES, finalMinutes).apply()
        syncWithSupabaseAsync(context)
    }

    fun getTotalStudyMinutesAllTime(context: Context): Int {
        val prefs = getPrefs(context)
        var totalSec = 0
        for ((key, value) in prefs.all) {
            if (key.startsWith("study_sec_") && value is Int) {
                totalSec += value
            }
        }
        return totalSec / 60
    }

    fun getLast7DaysStudyMinutes(context: Context): List<Pair<String, Int>> {
        val result = mutableListOf<Pair<String, Int>>()
        val cal = Calendar.getInstance()
        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        cal.add(Calendar.DAY_OF_YEAR, -6)
        for (i in 0..6) {
            val dateStr = dateFormat.format(cal.time)
            val label = dayFormat.format(cal.time)
            val seconds = getStudySecondsForDate(context, dateStr)
            result.add(Pair(label, seconds / 60))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return result
    }

    fun getWeeklyStudyMinutes(context: Context): Int {
        var totalSeconds = 0
        val cal = Calendar.getInstance()
        for (i in 0..6) {
            val dateStr = dateFormat.format(cal.time)
            totalSeconds += getStudySecondsForDate(context, dateStr)
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return totalSeconds / 60
    }

    fun getMonthlyStudyMinutes(context: Context): Int {
        var totalSeconds = 0
        val cal = Calendar.getInstance()
        for (i in 0..29) {
            val dateStr = dateFormat.format(cal.time)
            totalSeconds += getStudySecondsForDate(context, dateStr)
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return totalSeconds / 60
    }

    fun getStudyStreak(context: Context): Int {
        val cal = Calendar.getInstance()
        val todayStr = dateFormat.format(cal.time)
        val todaySec = getStudySecondsForDate(context, todayStr)

        var streak = 0
        if (todaySec > 0) {
            streak = 1
            cal.add(Calendar.DAY_OF_YEAR, -1)
            while (true) {
                val dateStr = dateFormat.format(cal.time)
                val sec = getStudySecondsForDate(context, dateStr)
                if (sec > 0) {
                    streak++
                    cal.add(Calendar.DAY_OF_YEAR, -1)
                } else {
                    break
                }
            }
        } else {
            cal.add(Calendar.DAY_OF_YEAR, -1)
            val yesterdayStr = dateFormat.format(cal.time)
            val yesterdaySec = getStudySecondsForDate(context, yesterdayStr)
            if (yesterdaySec > 0) {
                streak = 1
                cal.add(Calendar.DAY_OF_YEAR, -1)
                while (true) {
                    val dateStr = dateFormat.format(cal.time)
                    val sec = getStudySecondsForDate(context, dateStr)
                    if (sec > 0) {
                        streak++
                        cal.add(Calendar.DAY_OF_YEAR, -1)
                    } else {
                        break
                    }
                }
            } else {
                streak = 0
            }
        }
        return streak
    }

    // --- SUPABASE SYNC IMPLEMENTATION ---

    fun syncWithSupabaseAsync(context: Context) {
        trackerScope.launch {
            try {
                val repository = AcademyRepository(context.applicationContext)
                val api = repository.getApi()

                val prefs = context.getSharedPreferences("lakshya_app_prefs", Context.MODE_PRIVATE)
                val userEmail = currentActiveUserEmail.ifBlank {
                    prefs.getString("logged_in_email", "") ?: ""
                }

                if (userEmail.isBlank()) {
                    Log.d(TAG, "[STUDY TRACKER] User email is empty, skipping Supabase sync")
                    return@launch
                }

                val todayStr = dateFormat.format(Date())
                val todayTotalSec = getTodayStudySeconds(context)
                val goalMin = getDailyStudyGoalMinutes(context)

                val payload = mutableMapOf<String, Any>(
                    "user_email" to userEmail,
                    "date" to todayStr,
                    "total_seconds" to todayTotalSec,
                    "daily_goal_minutes" to goalMin,
                    "live_classes_seconds" to getModuleStudySeconds(context, MODULE_LIVE_CLASSES, todayStr),
                    "course_syllabus_seconds" to getModuleStudySeconds(context, MODULE_COURSE_SYLLABUS, todayStr),
                    "ai_coach_seconds" to getModuleStudySeconds(context, MODULE_AI_COACH, todayStr),
                    "current_affairs_seconds" to getModuleStudySeconds(context, MODULE_CURRENT_AFFAIRS, todayStr),
                    "test_series_seconds" to getModuleStudySeconds(context, MODULE_TEST_SERIES, todayStr),
                    "previous_papers_seconds" to getModuleStudySeconds(context, MODULE_PREVIOUS_PAPERS, todayStr),
                    "exam_alerts_seconds" to getModuleStudySeconds(context, MODULE_EXAM_ALERTS, todayStr),
                    "free_books_seconds" to getModuleStudySeconds(context, MODULE_FREE_BOOKS, todayStr),
                    "time_table_seconds" to getModuleStudySeconds(context, MODULE_TIME_TABLE, todayStr),
                    "study_websites_seconds" to getModuleStudySeconds(context, MODULE_STUDY_WEBSITES, todayStr),
                    "updated_at" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
                )

                val moshi = com.squareup.moshi.Moshi.Builder().build()
                val jsonStr = moshi.adapter(Map::class.java).toJson(payload)
                val body = jsonStr.toRequestBody("application/json".toMediaTypeOrNull())

                val existing = try {
                    api.getStudyProgressForUserAndDate("eq.$userEmail", "eq.$todayStr")
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    emptyList()
                }

                if (existing.isNotEmpty()) {
                    val resp = api.updateStudyProgress("eq.$userEmail", "eq.$todayStr", body)
                    if (resp.isSuccessful) {
                        Log.i(TAG, "[STUDY TRACKER] Supabase Sync: Success for date $todayStr (User: $userEmail)")
                    } else {
                        Log.w(TAG, "[STUDY TRACKER] Supabase update HTTP ${resp.code()} - ${resp.errorBody()?.string()}")
                    }
                } else {
                    val resp = api.insertStudyProgress(body)
                    if (resp.isSuccessful) {
                        Log.i(TAG, "[STUDY TRACKER] Supabase Sync: Success for date $todayStr (User: $userEmail)")
                    } else {
                        Log.w(TAG, "[STUDY TRACKER] Supabase insert HTTP ${resp.code()} - ${resp.errorBody()?.string()}")
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "[STUDY TRACKER] Sync failed: ${e.message}")
            }
        }
    }

    fun fetchAndMergeFromSupabase(context: Context, userEmail: String) {
        if (userEmail.isBlank()) return
        setCurrentUserEmail(userEmail)
        trackerScope.launch {
            try {
                val repository = AcademyRepository(context.applicationContext)
                val api = repository.getApi()
                val remoteRecords = api.getStudyProgressForUser("eq.$userEmail")

                val prefs = getPrefs(context)
                val editor = prefs.edit()

                for (record in remoteRecords) {
                    val date = record.date ?: continue
                    val totalSec = record.totalSeconds ?: 0

                    val localSec = prefs.getInt("study_sec_$date", 0)
                    if (totalSec > localSec) {
                        editor.putInt("study_sec_$date", totalSec)
                        editor.putInt("mod_sec_live_classes_$date", record.liveClassesSeconds ?: 0)
                        editor.putInt("mod_sec_course_syllabus_$date", record.courseSyllabusSeconds ?: 0)
                        editor.putInt("mod_sec_ai_coach_$date", record.aiCoachSeconds ?: 0)
                        editor.putInt("mod_sec_current_affairs_$date", record.currentAffairsSeconds ?: 0)
                        editor.putInt("mod_sec_test_series_$date", record.testSeriesSeconds ?: 0)
                        editor.putInt("mod_sec_previous_papers_$date", record.previousPapersSeconds ?: 0)
                        editor.putInt("mod_sec_exam_alerts_$date", record.examAlertsSeconds ?: 0)
                        editor.putInt("mod_sec_free_books_$date", record.freeBooksSeconds ?: 0)
                        editor.putInt("mod_sec_time_table_$date", record.timeTableSeconds ?: 0)
                        editor.putInt("mod_sec_study_websites_$date", record.studyWebsitesSeconds ?: 0)
                    }
                }
                editor.apply()
                Log.i(TAG, "[STUDY TRACKER] Supabase Sync: Merged ${remoteRecords.size} records from Supabase for User: $userEmail")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "[STUDY TRACKER] Could not fetch study progress from Supabase: ${e.message}")
            }
        }
    }

    data class Badge(
        val id: String,
        val title: String,
        val description: String,
        val icon: String,
        val isUnlocked: Boolean,
        val progressText: String = ""
    )

    fun getBadges(
        context: Context,
        totalVideosWatched: Int,
        totalTestsAttempted: Int,
        highestTestScore: Float
    ): List<Badge> {
        val streak = getStudyStreak(context)
        val todayMin = getTodayStudySeconds(context) / 60
        val goalMin = getDailyStudyGoalMinutes(context)
        val weeklyMin = getWeeklyStudyMinutes(context)

        return listOf(
            Badge(
                id = "early_bird",
                title = "Early Bird",
                description = "Start your very first study session",
                icon = "🌅",
                isUnlocked = todayMin > 0 || weeklyMin > 0,
                progressText = if (todayMin > 0 || weeklyMin > 0) "Completed" else "Study for 1+ min"
            ),
            Badge(
                id = "goal_crusher",
                title = "Goal Crusher",
                description = "Complete your daily study goal today",
                icon = "🎯",
                isUnlocked = todayMin >= goalMin && goalMin > 0,
                progressText = "$todayMin / $goalMin min"
            ),
            Badge(
                id = "streak_starter",
                title = "Streak Starter",
                description = "Achieve a 3-day consecutive study streak",
                icon = "🔥",
                isUnlocked = streak >= 3,
                progressText = "$streak / 3 days"
            ),
            Badge(
                id = "habit_builder",
                title = "Habit Builder",
                description = "Achieve a 7-day consecutive study streak",
                icon = "⚡",
                isUnlocked = streak >= 7,
                progressText = "$streak / 7 days"
            ),
            Badge(
                id = "video_seeker",
                title = "Video Seeker",
                description = "Watch your first video lecture",
                icon = "🎥",
                isUnlocked = totalVideosWatched > 0,
                progressText = "$totalVideosWatched / 1 video"
            ),
            Badge(
                id = "marathoner",
                title = "Study Marathoner",
                description = "Study for 120+ minutes in a single day",
                icon = "🏃",
                isUnlocked = todayMin >= 120,
                progressText = "$todayMin / 120 min"
            ),
            Badge(
                id = "test_conqueror",
                title = "Test Conqueror",
                description = "Attempt your first Mock Test in Test Series",
                icon = "🏆",
                isUnlocked = totalTestsAttempted > 0,
                progressText = "$totalTestsAttempted / 1 test"
            ),
            Badge(
                id = "accuracy_champ",
                title = "Accuracy Champion",
                description = "Score 90% or higher on any Mock Test",
                icon = "🌟",
                isUnlocked = highestTestScore >= 90f,
                progressText = if (highestTestScore > 0) "${highestTestScore.toInt()}% highest" else "No test yet"
            )
        )
    }
}
