package com.example.service

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.api.RetrofitClient
import com.example.api.R2SupabaseManager
import com.example.api.SupabaseApi
import com.example.data.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Calendar
import java.util.concurrent.TimeUnit

object WeeklyMockTestGenerator {
    private const val TAG = "WeeklyMockTestGen"
    private const val PREFS_NAME = "weekly_mock_test_prefs"
    
    // Coroutine scope for running scheduler & background generator
    private val generatorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var schedulerJob: Job? = null
    private var activeGenerationJob: Job? = null

    // Ordered sequence for single-target sequential generation (Feature 9)
    val SUPPORTED_CLASSES = (1..12).map { "Class $it" }
    val SUPPORTED_EXAMS = listOf(
        "Army GD", "Army Nursing Assistant", "SSC GD", 
        "UP Police", "NDA", "CUET", "NEET", "JEE"
    )

    val WEEKLY_ROTATION_SEQUENCE = listOf(
        "Class 1", "Class 2", "Class 3", "Class 4", "Class 5",
        "Class 6", "Class 7", "Class 8", "Class 9", "Class 10",
        "Class 11 Science", "Class 11 Arts", "Class 11 Commerce",
        "Class 12 Science", "Class 12 Arts", "Class 12 Commerce",
        "Army GD", "Army Nursing Assistant", "SSC GD", "UP Police",
        "NEET (Medical)", "JEE (Engineering)", "CUET (UG)"
    )

    fun startScheduler(context: Context, repository: AcademyRepository) {
        if (schedulerJob != null) return
        logMessage(context, "Weekly Scheduler Started")
        
        schedulerJob = generatorScope.launch {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val isGenerating = prefs.getBoolean("is_generating", false)
            val autoEnabled = prefs.getBoolean("auto_generation_enabled", true)
            
            if (isGenerating && autoEnabled) {
                logMessage(context, "Recovery Triggered - Resuming interrupted weekly test generation...")
                startBackgroundGeneration(context, repository)
            }
            
            while (isActive) {
                try {
                    val calendar = Calendar.getInstance()
                    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                    val hour = calendar.get(Calendar.HOUR_OF_DAY)
                    val currentWeekNum = calendar.get(Calendar.WEEK_OF_YEAR)
                    val currentYear = calendar.get(Calendar.YEAR)
                    val weekKey = "$currentYear-W$currentWeekNum"
                    
                    val autoGenEnabled = prefs.getBoolean("auto_generation_enabled", true)
                    val lastCompletedWeek = prefs.getString("last_completed_week", "")
                    
                    // Trigger on Sunday at 2:00 AM or later (if not already completed for this week)
                    if (autoGenEnabled && dayOfWeek == Calendar.SUNDAY && hour >= 2) {
                        if (lastCompletedWeek != weekKey && !prefs.getBoolean("is_generating", false)) {
                            logMessage(context, "Scheduled time reached (Sunday 2:00 AM) for week $weekKey. Starting single sequential generation.")
                            startBackgroundGeneration(context, repository)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in scheduler loop", e)
                }
                delay(15 * 60 * 1000L) // Check every 15 minutes
            }
        }
    }

    fun isGenerating(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("is_generating", false)
    }

    fun isAutoGenerationEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("auto_generation_enabled", true)
    }

    fun setAutoGenerationEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("auto_generation_enabled", enabled)
            .apply()
        logMessage(context, "Weekly Auto Generation set to: $enabled")
    }

    fun getLogs(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("generation_logs", "No logs recorded yet.\n") ?: ""
    }

    fun clearLogs(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("generation_logs", "").apply()
    }

    fun triggerManualGeneration(context: Context, repository: AcademyRepository, targetSingle: String? = null, regenerateCurrentWeek: Boolean = false) {
        if (isGenerating(context)) {
            logMessage(context, "Cannot start generation: A generation process is already running.")
            return
        }
        logMessage(context, "Manual trigger requested. Target: ${targetSingle ?: "Sequential Rotation"}. Regenerate: $regenerateCurrentWeek")
        
        if (regenerateCurrentWeek) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString("last_completed_week", "").apply()
        }
        
        startBackgroundGeneration(context, repository, targetSingle)
    }

    private fun startBackgroundGeneration(context: Context, repository: AcademyRepository, targetSingle: String? = null) {
        activeGenerationJob?.cancel()
        activeGenerationJob = generatorScope.launch {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean("is_generating", true).apply()
            
            try {
                val calendar = Calendar.getInstance()
                val currentWeekNum = calendar.get(Calendar.WEEK_OF_YEAR)
                val currentYear = calendar.get(Calendar.YEAR)
                val weekKey = "$currentYear-W$currentWeekNum"

                val rotationIndex = prefs.getInt("weekly_rotation_index", 0) % WEEKLY_ROTATION_SEQUENCE.size
                val target = targetSingle ?: WEEKLY_ROTATION_SEQUENCE[rotationIndex]

                logMessage(context, "Sequential Weekly Generation Started -> Target: '$target' (Rotation Index #$rotationIndex of ${WEEKLY_ROTATION_SEQUENCE.size})")
                prefs.edit().putString("current_generation_target", target).apply()

                val title = "Weekly Mock Test - $target - Week $currentWeekNum ($currentYear)"

                // Generate and publish mock test for this single sequential target
                val success = generateAndPublishMockTestForTarget(context, repository, target, title)

                if (success) {
                    if (targetSingle == null) {
                        val nextIndex = (rotationIndex + 1) % WEEKLY_ROTATION_SEQUENCE.size
                        prefs.edit()
                            .putInt("weekly_rotation_index", nextIndex)
                            .putString("last_completed_week", weekKey)
                            .apply()
                        logMessage(context, "Weekly Mock Test successfully created for $target. Next week rotation set to index #$nextIndex (${WEEKLY_ROTATION_SEQUENCE[nextIndex]}).")
                    } else {
                        logMessage(context, "Single target Weekly Mock Test successfully created for $target.")
                    }
                } else {
                    logMessage(context, "Failed to generate Weekly Mock Test for $target.")
                }
            } catch (e: Exception) {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                logMessage(context, "Exception during weekly generation: ${e.message}\nStack Trace:\n$sw")
            } finally {
                prefs.edit()
                    .putBoolean("is_generating", false)
                    .putString("current_generation_target", "")
                    .apply()
            }
        }
    }

    private suspend fun generateAndPublishMockTestForTarget(
        context: Context,
        repository: AcademyRepository,
        target: String,
        title: String
    ): Boolean {
        logMessage(context, "AI Generation Started for target: $target")
        
        val supabaseApi = getSupabaseApiDirect(context)
        if (supabaseApi == null) {
            logMessage(context, "FAILED_STEP: Supabase API Initialization - Error: Supabase API credentials null.")
            return false
        }

        // Generate 50 questions using AI
        val subjects = getSubjectsForTarget(target)
        val questionsNeeded = 50

        logMessage(context, "Generating 50 AI questions for $target...")
        val generatedQuestions = mutableListOf<MockTestGenerator20.GeneratedQuestion>()

        try {
            val syllabus = listOf(MockTestGenerator20.SyllabusSubject(target, subjects))
            val batchResult = MockTestGenerator20.generateQuestionBatch(
                context = context,
                examName = target,
                difficulty = "Medium",
                syllabus = syllabus,
                count = questionsNeeded,
                existingQuestions = emptyList()
            )
            generatedQuestions.addAll(batchResult)
        } catch (e: Exception) {
            logMessage(context, "Error generating question batch for $target: ${e.localizedMessage ?: e.message}")
            return false
        }

        if (generatedQuestions.size < 10) {
            logMessage(context, "FAILED_STEP: Insufficient questions generated (${generatedQuestions.size}/$questionsNeeded).")
            return false
        }

        // Publish Mock Test with duplicate protection (UPSERT)
        logMessage(context, "Publishing test '$title' (${generatedQuestions.size} questions) with UPSERT duplicate protection...")
        var pubStatus = ""
        val publishResult = MockTestGenerator20.publishMockTest(
            context = context,
            repository = repository,
            title = title,
            examName = target,
            difficulty = "Medium",
            questions = generatedQuestions,
            durationMinutes = MockTestGenerator20.calculateAutoDuration(generatedQuestions.size),
            marksPerCorrect = 1,
            marksPerWrong = 0f,
            hasNegativeMarking = false,
            isDraft = false,
            instructions = "Weekly Auto Mock Test for $target. Answer all questions within the time limit.",
            subject = target,
            targetClass = target,
            examCategory = "Weekly Auto Tests",
            onStepUpdate = { step ->
                pubStatus = step
                logMessage(context, "Publish Step: $step")
            }
        )

        return if (publishResult.isSuccess) {
            logMessage(context, "SUCCESS: Weekly Mock Test for $target published cleanly!")
            true
        } else {
            val err = publishResult.exceptionOrNull()?.localizedMessage ?: "Unknown publish error"
            logMessage(context, "FAILED_STEP: Publish Mock Test failed for $target - $err")
            false
        }
    }

    private fun getSubjectsForTarget(target: String): List<String> {
        return when {
            target.contains("Class 12 PCB", ignoreCase = true) -> 
                listOf("Physics/भौतिकी", "Chemistry/रसायन शास्त्र", "Biology/जीव विज्ञान")
            target.contains("Class 12 PCM", ignoreCase = true) -> 
                listOf("Physics/भौतिकी", "Chemistry/रसायन शास्त्र", "Mathematics/गणित")
            target.contains("Class 11") || target.contains("Class 12") -> 
                listOf("Physics/भौतिकी", "Chemistry/रसायन शास्त्र", "Mathematics/गणित", "Biology/जीव विज्ञान", "English/अंग्रेजी")
            target.contains("Class 9") || target.contains("Class 10") -> 
                listOf("Mathematics/गणित", "Science/विज्ञान", "Social Science/सामाजिक विज्ञान", "English/अंग्रेजी", "Hindi/हिन्दी", "Computer/कंप्यूटर", "GK/सामान्य ज्ञान")
            target.startsWith("Class") -> 
                listOf("Mathematics/गणित", "Science/विज्ञान", "Social Science/सामाजिक विज्ञान", "English/अंग्रेजी", "Hindi/हिन्दी", "GK/सामान्य ज्ञान", "Computer/कंप्यूटर")
            target.equals("Army GD", ignoreCase = true) -> 
                listOf("GK/सामान्य ज्ञान", "General Science/सामान्य विज्ञान", "Mathematics/गणित", "Logical Reasoning/तार्किक विचार")
            target.equals("Army Nursing Assistant", ignoreCase = true) -> 
                listOf("Chemistry/रसायन शास्त्र", "Biology/जीव विज्ञान", "Physics/भौतिकी", "GK/सामान्य ज्ञान", "Mathematics/गणित")
            target.equals("SSC GD", ignoreCase = true) -> 
                listOf("General Intelligence & Reasoning/सामान्य बुद्धि और तर्क", "GK/सामान्य ज्ञान", "Mathematics/गणित", "English/अंग्रेजी", "Hindi/हिन्दी")
            target.equals("UP Police", ignoreCase = true) -> 
                listOf("General Knowledge/सामान्य ज्ञान", "General Hindi/सामान्य हिन्दी", "Numerical & Mental Ability/संख्यात्मक और मानसिक क्षमता", "Mental Aptitude/IQ/मानसिक योग्यता")
            target.equals("NEET (Medical)", ignoreCase = true) || target.equals("NEET", ignoreCase = true) -> 
                listOf("Physics/भौतिकी", "Chemistry/रसायन शास्त्र", "Biology/जीव विज्ञान")
            target.equals("JEE (Engineering)", ignoreCase = true) || target.equals("JEE", ignoreCase = true) -> 
                listOf("Physics/भौतिकी", "Chemistry/रसायन शास्त्र", "Mathematics/गणित")
            else -> 
                listOf("General Knowledge/सामान्य ज्ञान", "Current Affairs/सामयिक विषय", "Quantitative Aptitude/मात्रात्मक योग्यता", "Reasoning/तर्क")
        }
    }

    private fun getSupabaseApiDirect(context: Context): SupabaseApi? {
        val creds = R2SupabaseManager.getCredentials(context)
        if (creds.supabaseUrl.isBlank() || creds.supabaseAnonKey.isBlank()) {
            return null
        }
        val rawUrl = creds.cleanBaseUrl
        val baseUrl = if (rawUrl.endsWith("/")) rawUrl else "$rawUrl/"

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("apikey", creds.supabaseAnonKey)
                    .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
                    .addHeader("Prefer", "return=representation")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SupabaseApi::class.java)
    }

    private fun logMessage(context: Context, msg: String) {
        val formattedMsg = "[${Calendar.getInstance().time}] $msg\n"
        Log.d(TAG, "LMS Logcat - $msg")
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentLogs = prefs.getString("generation_logs", "") ?: ""
        val newLogs = if (currentLogs.length > 50000) {
            currentLogs.takeLast(30000) + formattedMsg
        } else {
            currentLogs + formattedMsg
        }
        prefs.edit().putString("generation_logs", newLogs).apply()
    }
}

data class QuestionMetadata(
    val explanation: String = "",
    val subject: String = "",
    val chapter: String = "",
    val difficulty: String = "",
    val level: String = ""
)

fun parseQuestionMetadata(questionText: String): QuestionMetadata {
    if (!questionText.contains("---METADATA---")) {
        return QuestionMetadata(explanation = "No explanation available.")
    }
    return try {
        val jsonStr = questionText.substringAfter("---METADATA---").trim()
        val json = JSONObject(jsonStr)
        QuestionMetadata(
            explanation = json.optString("explanation", "Bilingual explanation / द्विभाषी विवरण"),
            subject = json.optString("subject", "General"),
            chapter = json.optString("chapter", "General"),
            difficulty = json.optString("difficulty", "Medium"),
            level = json.optString("level", "General")
        )
    } catch (e: Exception) {
        QuestionMetadata(explanation = "No explanation available.")
    }
}
