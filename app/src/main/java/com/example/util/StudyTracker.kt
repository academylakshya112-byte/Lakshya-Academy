package com.example.util

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.*

object StudyTracker {
    private const val PREFS_NAME = "lakshya_study_tracker"
    private const val KEY_GOAL_MINUTES = "study_goal_minutes"
    private const val DEFAULT_GOAL_MINUTES = 45

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Increment study time for today by a given number of seconds.
     */
    fun addStudyTime(context: Context, seconds: Int) {
        if (seconds <= 0) return
        val prefs = getPrefs(context)
        val todayStr = dateFormat.format(Date())
        val currentSec = prefs.getInt("study_sec_$todayStr", 0)
        prefs.edit().putInt("study_sec_$todayStr", currentSec + seconds).apply()
    }

    /**
     * Get study time in seconds for a specific date (yyyy-MM-dd).
     */
    fun getStudySecondsForDate(context: Context, dateStr: String): Int {
        return getPrefs(context).getInt("study_sec_$dateStr", 0)
    }

    /**
     * Get today's study time in seconds.
     */
    fun getTodayStudySeconds(context: Context): Int {
        val todayStr = dateFormat.format(Date())
        return getStudySecondsForDate(context, todayStr)
    }

    /**
     * Get the user's daily study goal in minutes.
     */
    fun getDailyStudyGoalMinutes(context: Context): Int {
        return getPrefs(context).getInt(KEY_GOAL_MINUTES, DEFAULT_GOAL_MINUTES)
    }

    /**
     * Set the user's daily study goal in minutes.
     */
    fun setDailyStudyGoalMinutes(context: Context, minutes: Int) {
        val finalMinutes = minutes.coerceAtLeast(5)
        getPrefs(context).edit().putInt(KEY_GOAL_MINUTES, finalMinutes).apply()
    }

    /**
     * Get study time for the last 7 days (including today), returned as a list of Pairs of (DayLabel, Minutes).
     * E.g., [("Mon", 12), ("Tue", 45), ...]
     */
    fun getLast7DaysStudyMinutes(context: Context): List<Pair<String, Int>> {
        val result = mutableListOf<Pair<String, Int>>()
        val cal = Calendar.getInstance()
        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        
        // Go 6 days back to today
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

    /**
     * Get study time in minutes for the current week (last 7 days sum).
     */
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

    /**
     * Get study time in minutes for the current month (last 30 days sum).
     */
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

    /**
     * Calculates the current consecutive day study streak.
     * Starts from today (or yesterday if today is 0), and goes backwards as long as study time > 0.
     */
    fun getStudyStreak(context: Context): Int {
        val cal = Calendar.getInstance()
        val todayStr = dateFormat.format(cal.time)
        val todaySec = getStudySecondsForDate(context, todayStr)
        
        var streak = 0
        
        // If today has study time, start counting from today
        // If today is 0, start counting from yesterday (streak is still alive but hasn't increased today yet)
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
            // Check yesterday
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

    /**
     * Data class representing a Badge or Achievement.
     */
    data class Badge(
        val id: String,
        val title: String,
        val description: String,
        val icon: String, // e.g. emoji or icon identifier
        val isUnlocked: Boolean,
        val progressText: String = ""
    )

    /**
     * Get a list of all badges and their unlock status for the student.
     */
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
