package com.example.service

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.api.GeminiApiService
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

    // Supported classes and default exams
    val SUPPORTED_CLASSES = (1..12).map { "Class $it" }
    val SUPPORTED_EXAMS = listOf(
        "Army GD", "Army Nursing Assistant", "SSC GD", 
        "UP Police", "NDA", "CUET", "NEET", "JEE"
    )

    fun startScheduler(context: Context, repository: AcademyRepository) {
        if (schedulerJob != null) return
        logMessage(context, "Weekly Scheduler Started")
        
        schedulerJob = generatorScope.launch {
            // Upon app startup, check if we need to recover an interrupted generation
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
                            logMessage(context, "Scheduled time reached (Sunday 2:00 AM) for week $weekKey. Starting generation.")
                            startBackgroundGeneration(context, repository)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in scheduler loop", e)
                }
                // Check once every 15 minutes
                delay(15 * 60 * 1000L)
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
        logMessage(context, "Manual trigger requested. Single target: ${targetSingle ?: "ALL"}. Regenerate: $regenerateCurrentWeek")
        
        if (regenerateCurrentWeek) {
            // Reset completed state for current week to force regeneration
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
                // Determine current week key
                val calendar = Calendar.getInstance()
                val currentWeekNum = calendar.get(Calendar.WEEK_OF_YEAR)
                val currentYear = calendar.get(Calendar.YEAR)
                val weekKey = "$currentYear-W$currentWeekNum"
                
                // Collect dynamic exams from courses
                val dynamicExams = mutableSetOf<String>()
                dynamicExams.addAll(SUPPORTED_EXAMS)
                try {
                    val courses = repository.allCourses.first()
                    courses.forEach { course ->
                        if (course.category.isNotBlank() && !course.category.contains("Class", ignoreCase = true)) {
                            dynamicExams.add(course.category.trim())
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read courses for dynamic exams", e)
                }

                // Sequence of all targets to process
                val allTargets = if (targetSingle != null) {
                    listOf(targetSingle)
                } else {
                    SUPPORTED_CLASSES + dynamicExams.toList()
                }

                // Check where we were interrupted
                val lastFinishedTarget = prefs.getString("last_finished_target", "") ?: ""
                var startIndex = 0
                if (targetSingle == null && lastFinishedTarget.isNotEmpty()) {
                    val idx = allTargets.indexOf(lastFinishedTarget)
                    if (idx != -1 && idx < allTargets.size - 1) {
                        startIndex = idx + 1
                        logMessage(context, "Resuming Weekly Test Generation. Last finished target: $lastFinishedTarget. Resuming from: ${allTargets[startIndex]}")
                    } else if (idx == allTargets.size - 1) {
                        logMessage(context, "All targets were already completed for the last session. Restarting full sequence.")
                    }
                }

                for (i in startIndex until allTargets.size) {
                    val target = allTargets[i]
                    logMessage(context, "Current Class/Exam processing: $target")
                    prefs.edit().putString("current_generation_target", target).apply()
                    
                    val title = "Weekly Mock Test - $target - Week $currentWeekNum ($currentYear)"
                    
                    // Prevent duplicate Weekly Mock Tests
                    val existsInSupabase = checkIfTestExistsInSupabase(context, title)
                    if (existsInSupabase) {
                        logMessage(context, "Skipping $target: Weekly Mock Test '$title' already exists in Supabase.")
                        prefs.edit().putString("last_finished_target", target).apply()
                        continue
                    }

                    // Generate ONE Mock Test
                    val success = generateAndPublishMockTestForTarget(context, repository, target, title)
                    if (success) {
                        logMessage(context, "Moving To Next Class/Exam")
                        prefs.edit().putString("last_finished_target", target).apply()
                    } else {
                        logMessage(context, "Failed to generate Weekly Mock Test for $target. Will skip and retry later.")
                    }
                    
                    // Delay between targets to prevent heavy continuous load
                    delay(2000L)
                }
                
                // If we finished everything successfully for the whole run
                if (targetSingle == null) {
                    prefs.edit()
                        .putString("last_completed_week", weekKey)
                        .putString("last_finished_target", "")
                        .apply()
                }
                
                logMessage(context, "Generation Completed successfully.")
            } catch (e: Exception) {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                logMessage(context, "Complete Exception inside generation run: ${e.message}\nComplete Stack Trace:\n$sw")
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
        logMessage(context, "AI Generation Started for $target")
        
        val supabaseApi = getSupabaseApiDirect(context)
        if (supabaseApi == null) {
            logMessage(context, "FAILED_STEP: Supabase API Initialization - Error: Supabase API is null (invalid credentials).")
            return false
        }

        // 1. Save Mock Test Metadata to Supabase
        val testEntity = TestEntity(
            title = title,
            type = "Weekly Auto Test",
            durationMinutes = 60,
            hasNegativeMarking = false,
            marksPerCorrect = 1,
            marksPerWrong = 0f
        )

        val insertedTest: TestEntity
        try {
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val jsonAdapter = moshi.adapter(TestEntity::class.java)
            val jsonStr = jsonAdapter.toJson(testEntity)
            val jsonObject = JSONObject(jsonStr)
            jsonObject.remove("id") // Let Supabase autogenerate ID
            val payloadStr = jsonObject.toString()
            
            val requestBody = payloadStr.toRequestBody("application/json".toMediaTypeOrNull())
            logMessage(context, "Saving metadata to Supabase. Payload: $payloadStr")
            
            val responseList = supabaseApi.insertTest(requestBody)
            insertedTest = responseList.firstOrNull() ?: throw Exception("Empty response returned from Supabase insertTest")
            logMessage(context, "Supabase Insert Success for test metadata. ID assigned: ${insertedTest.id}")
        } catch (e: Exception) {
            logMessage(context, "FAILED_STEP: Mock Test Metadata Save - Error: ${e.localizedMessage ?: e.message}")
            logMessage(context, "Full error details: ${e.stackTraceToString()}")
            return false
        }

        // 2. AI generate all 50 questions
        val subjects = getSubjectsForTarget(target)
        val questions = mutableListOf<QuestionEntity>()
        var batchIndex = 0
        val questionsNeeded = 50
        val batchSize = 5
        var generationFailed = false
        var generationErrorMsg = ""
        var attemptsTotal = 0

        while (questions.size < questionsNeeded && attemptsTotal < 30) {
            attemptsTotal++
            val currentBatchNeeded = (questionsNeeded - questions.size).coerceAtMost(batchSize)
            logMessage(context, "AI Question generation batch ${batchIndex + 1} started. Current total: ${questions.size}/50. Requesting $currentBatchNeeded questions...")
            
            var attempt = 0
            var batchSuccess = false
            while (attempt < 3 && !batchSuccess) {
                attempt++
                if (attempt > 1) {
                    logMessage(context, "Retry Started for question batch. Attempt $attempt/3...")
                }
                try {
                    val batchQuestions = generateQuestionsWithGemini(
                        context = context,
                        target = target,
                        subjects = subjects,
                        count = currentBatchNeeded,
                        testId = insertedTest.id,
                        startingIndex = questions.size + 1,
                        existingQuestions = questions
                    )
                    
                    var addedCount = 0
                    for (cand in batchQuestions) {
                        if (questions.size >= questionsNeeded) break
                        if (!QuestionDeduplicator.isDuplicateAgainstList(cand, questions)) {
                            questions.add(cand)
                            addedCount++
                        } else {
                            logMessage(context, "Discarded duplicate/invalid question from Gemini batch: ${QuestionDeduplicator.cleanQuestionText(cand.questionText).take(40)}")
                        }
                    }

                    if (addedCount > 0) {
                        batchSuccess = true
                        logMessage(context, "Batch processed. Added $addedCount new unique questions. Total unique questions now: ${questions.size}/50")
                    } else {
                        val warningMsg = "Gemini returned no new unique questions in this batch attempt."
                        logMessage(context, "Warning: $warningMsg Retrying...")
                        generationErrorMsg = warningMsg
                        delay(1000L)
                    }
                } catch (e: Exception) {
                    generationErrorMsg = e.localizedMessage ?: e.message ?: "Unknown error"
                    logMessage(context, "Error in AI generation attempt $attempt: $generationErrorMsg")
                    delay(2000L)
                }
            }
            
            batchIndex++
            delay(1000L) // Safe padding between model requests
        }

        if (questions.size != questionsNeeded) {
            val finalErr = if (generationErrorMsg.isNotEmpty()) generationErrorMsg else "Failed to generate exactly 50 unique questions (only generated ${questions.size})."
            logMessage(context, "FAILED_STEP: AI Question Generation - Error: $finalErr")
            // Clean up the partial test metadata from Supabase
            try {
                supabaseApi.deleteTestById("eq.${insertedTest.id}")
                logMessage(context, "Cleaned up orphaned test metadata on Supabase ID: ${insertedTest.id}")
            } catch (ex: Exception) {
                Log.e(TAG, "Cleanup failed", ex)
            }
            return false
        }

        // Pre-save Uniqueness Validation Pass
        logMessage(context, "Performing Pre-save Uniqueness Validation for all 50 questions...")
        val finalValidatedQuestions = mutableListOf<QuestionEntity>()
        for (i in questions.indices) {
            val q = questions[i]
            if (!QuestionDeduplicator.isDuplicateAgainstList(q, finalValidatedQuestions)) {
                finalValidatedQuestions.add(q)
            } else {
                logMessage(context, "Pre-save check detected duplicate at position ${i + 1}. Regenerating replacement...")
                var replacement: QuestionEntity? = null
                var repAttempts = 0
                while (replacement == null && repAttempts < 10) {
                    repAttempts++
                    try {
                        val singleBatch = generateQuestionsWithGemini(
                            context = context,
                            target = target,
                            subjects = subjects,
                            count = 1,
                            testId = insertedTest.id,
                            startingIndex = finalValidatedQuestions.size + 1,
                            existingQuestions = finalValidatedQuestions
                        )
                        val cand = singleBatch.firstOrNull()
                        if (cand != null && !QuestionDeduplicator.isDuplicateAgainstList(cand, finalValidatedQuestions)) {
                            replacement = cand
                        }
                    } catch (e: Exception) {
                        delay(1000L)
                    }
                }
                if (replacement != null) {
                    finalValidatedQuestions.add(replacement)
                    logMessage(context, "Successfully replaced question ${i + 1} with a unique question.")
                } else {
                    logMessage(context, "FAILED_STEP: Pre-save validation replacement failed for question ${i + 1}.")
                    try {
                        supabaseApi.deleteTestById("eq.${insertedTest.id}")
                    } catch (ex: Exception) {}
                    return false
                }
            }
        }

        if (finalValidatedQuestions.size != 50) {
            logMessage(context, "FAILED_STEP: Pre-save validation failed. Final count is ${finalValidatedQuestions.size}, expected 50.")
            try {
                supabaseApi.deleteTestById("eq.${insertedTest.id}")
            } catch (ex: Exception) {}
            return false
        }

        // 3. Save all questions into Supabase
        logMessage(context, "All 50 unique questions validated successfully. Committing to Supabase...")
        var commitSuccess = true
        var lastCommitError = ""
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val questionAdapter = moshi.adapter(QuestionEntity::class.java)
        
        var questionIndex = 0
        for (q in finalValidatedQuestions) {
            questionIndex++
            var saveAttempt = 0
            var saveSuccess = false
            val jsonStr = questionAdapter.toJson(q)
            val jsonObject = JSONObject(jsonStr).apply {
                remove("id") // Let Supabase autogenerate ID
            }
            val payloadStr = jsonObject.toString()
            
            while (saveAttempt < 3 && !saveSuccess) {
                saveAttempt++
                try {
                    val requestBody = payloadStr.toRequestBody("application/json".toMediaTypeOrNull())
                    supabaseApi.insertQuestion(requestBody)
                    saveSuccess = true
                    Log.d(TAG, "Question $questionIndex commit success: ${q.questionText.take(30)}")
                } catch (e: Exception) {
                    lastCommitError = e.localizedMessage ?: e.message ?: "Unknown database error"
                    logMessage(context, "WARNING: Failed to save question $questionIndex, attempt $saveAttempt/3. Error: $lastCommitError. Payload: $payloadStr")
                    Log.e(TAG, "Failed to save question $questionIndex, attempt $saveAttempt", e)
                    delay(1000L)
                }
            }
            if (!saveSuccess) {
                commitSuccess = false
                logMessage(context, "ERROR: Question $questionIndex save failed after 3 attempts.")
                break
            }
        }

        if (!commitSuccess) {
            logMessage(context, "FAILED_STEP: Supabase Questions Save - Error: $lastCommitError. Cleaning up...")
            try {
                supabaseApi.deleteTestById("eq.${insertedTest.id}")
            } catch (ex: Exception) {
                Log.e(TAG, "Cleanup failed", ex)
            }
            return false
        }

        logMessage(context, "Verify Save Success: All 50 questions saved successfully to Supabase.")
        
        // 4. Verify the saved Mock Test exists in Supabase via SELECT query
        logMessage(context, "Verifying Mock Test existence in Supabase via SELECT query...")
        var verificationSuccess = false
        var verificationError = "Mock Test was not found in Supabase via verification SELECT query."
        try {
            val testsInSupabase = supabaseApi.getAllTests()
            if (testsInSupabase.any { it.id == insertedTest.id }) {
                verificationSuccess = true
                logMessage(context, "Verification Success: Mock Test ${insertedTest.id} exists in Supabase.")
            }
        } catch (e: Exception) {
            verificationError = e.localizedMessage ?: e.message ?: "Unknown network error"
        }
        
        if (!verificationSuccess) {
            logMessage(context, "FAILED_STEP: Mock Test Supabase Verification SELECT - Error: $verificationError")
            try {
                supabaseApi.deleteTestById("eq.${insertedTest.id}")
            } catch (ex: Exception) {}
            return false
        }

        // 5. Publish the Weekly Mock Test!
        logMessage(context, "Publishing Weekly Mock: '$title'")
        try {
            // Insert test and questions locally so it doesn't require delay/manual refresh
            val localDb = AcademyDatabase.getDatabase(context).academyDao()
            localDb.insertTest(insertedTest)
            questions.forEach { localDb.insertQuestion(it) }
            
            logMessage(context, "Publishing Success: '$title' is now fully published and live!")
        } catch (e: Exception) {
            val pubErr = e.localizedMessage ?: e.message ?: "Unknown local database error"
            logMessage(context, "FAILED_STEP: Mock Test Publication - Error: $pubErr")
            return false 
        }
        
        // 6. Verify Student App can fetch it (sync from remote)
        logMessage(context, "Verifying Student App can fetch the newly created Weekly Mock Test...")
        try {
            // Fetching via Supabase API simulates the student app fetching from remote
            val fetchedTests = supabaseApi.getAllTests()
            val fetchedQuestions = supabaseApi.getQuestionsForTest("eq.${insertedTest.id}")
            
            if (fetchedTests.any { it.id == insertedTest.id } && fetchedQuestions.size == 50) {
                 logMessage(context, "Weekly Mock Test Generated Successfully")
                 return true
            } else {
                 val fetchErr = "Fetched ${fetchedQuestions.size} questions from remote, expected 50."
                 logMessage(context, "FAILED_STEP: Student App Fetch Verification - Error: $fetchErr")
                 return false
            }
        } catch (e: Exception) {
             val fetchErr = e.localizedMessage ?: e.message ?: "Unknown fetch error"
             logMessage(context, "FAILED_STEP: Student App Fetch Verification - Error: $fetchErr")
             return false
        }
    }

    private suspend fun generateQuestionsWithGemini(
        context: Context,
        target: String,
        subjects: List<String>,
        count: Int,
        testId: Int,
        startingIndex: Int,
        existingQuestions: List<QuestionEntity> = emptyList()
    ): List<QuestionEntity> {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY" || apiKey == "placeholder") {
            throw Exception("Valid Gemini API Key is missing. Please configure it in the Secrets panel.")
        }

        val subjectSelection = (startingIndex until (startingIndex + count)).map { idx ->
            subjects[idx % subjects.size]
        }

        val existingTexts = existingQuestions.map { QuestionDeduplicator.cleanQuestionText(it.questionText) }.takeLast(30).joinToString("\n---\n")

        val prompt = """
            Generate exactly $count multiple-choice questions for $target.
            The list of questions must follow these specific subjects in order: ${subjectSelection.joinToString(", ")}.
            
            Every question must contain:
            1. An extremely clear question text.
            2. Four highly plausible multiple-choice options.
            3. Correct Answer Index (0 for A, 1 for B, 2 for C, 3 for D).
            4. Detailed step-by-step explanation.
            5. Subject and Chapter name.
            6. Difficulty Level ("Easy", "Medium", "Hard").
            7. Board/Exam Level ("$target Level").

            CRITICAL DEDUPLICATION REQUIREMENTS:
            1. Do NOT generate any question similar in wording, meaning, or concept to the following previously generated questions:
            $existingTexts
            2. Every question must have 4 COMPLETELY DISTINCT options (option_a, option_b, option_c, option_d). Never repeat option choices within a question.
            
            CRITICAL FORMAT REQUIREMENT:
            - Every single text parameter (question text, options, explanation) MUST be bilingual: both English and Hindi, separated strictly by " / " (e.g., "What is the capital of India? / भारत की राजधानी क्या है?").
            - The response must be a valid, raw JSON array of objects. Do not include markdown wraps like ```json ... ```.
            
            JSON schema for each question object:
            {
              "question": "Question text in English / हिन्दी में प्रश्न",
              "option_a": "Option A in English / हिन्दी में विकल्प A",
              "option_b": "Option B in English / हिन्दी में विकल्प B",
              "option_c": "Option C in English / हिन्दी में विकल्प C",
              "option_d": "Option D in English / हिन्दी में विकल्प D",
              "correct_index": 0,
              "explanation": "Detailed explanation / विस्तृत विवरण",
              "subject": "Name of Subject",
              "chapter": "Name of Chapter",
              "difficulty": "Easy/Medium/Hard"
            }
            
            Generate high-quality questions following the latest syllabus. No duplicate questions or options. No incorrect answers.
        """.trimIndent()

        val request = com.example.data.GenerateContentRequest(
            contents = listOf(
                com.example.data.Content(
                    parts = listOf(
                        com.example.data.Part(text = prompt)
                    )
                )
            )
        )

        val response = withContext(Dispatchers.IO) {
            RetrofitClient.service.generateContent(
                "v1beta/models/gemini-2.5-flash:generateContent",
                apiKey,
                request
            )
        }

        val responseText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text 
            ?: throw Exception("Gemini returned empty candidate content")

        val cleanResponse = responseText.trim().removeSurrounding("```json", "```").trim()
        val jsonArray = JSONArray(cleanResponse)
        val generated = mutableListOf<QuestionEntity>()

        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            val qText = item.getString("question")
            val optA = item.getString("option_a")
            val optB = item.getString("option_b")
            val optC = item.getString("option_c")
            val optD = item.getString("option_d")
            val correctIdx = item.getInt("correct_index")
            
            val metadata = JSONObject().apply {
                put("explanation", item.optString("explanation", "Bilingual explanation / द्विभाषी विवरण"))
                put("subject", item.optString("subject", "General"))
                put("chapter", item.optString("chapter", "General"))
                put("difficulty", item.optString("difficulty", "Medium"))
                put("level", "$target Level")
            }
            
            // Encode explanation and other metadata safely inside the question text
            val questionWithMetadata = "$qText\n\n---METADATA---\n${metadata.toString()}"

            generated.add(
                QuestionEntity(
                    testId = testId,
                    questionText = questionWithMetadata,
                    optionA = optA,
                    optionB = optB,
                    optionC = optC,
                    optionD = optD,
                    correctIndex = correctIdx
                )
            )
            Log.d(TAG, "Question Generated: ${qText.take(40)}")
        }

        return generated
    }

    private suspend fun checkIfTestExistsInSupabase(context: Context, title: String): Boolean {
        return try {
            val api = getSupabaseApiDirect(context) ?: return false
            val tests = api.getAllTests()
            tests.any { it.title.trim().equals(title.trim(), ignoreCase = true) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check test existence on Supabase", e)
            false
        }
    }

    private fun getSubjectsForTarget(target: String): List<String> {
        return when {
            target.contains("Class 12 PCB") || target.equals("Class 12 PCB", ignoreCase = true) -> 
                listOf("Physics/भौतिकी", "Chemistry/रसायन शास्त्र", "Biology/जीव विज्ञान")
            target.contains("Class 12 PCM") || target.equals("Class 12 PCM", ignoreCase = true) -> 
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
            target.equals("NDA", ignoreCase = true) -> 
                listOf("Mathematics/गणित", "English/अंग्रेजी", "Physics/भौतिकी", "Chemistry/रसायन शास्त्र", "History & Geography/इतिहास और भूगोल", "GK/सामान्य ज्ञान")
            target.equals("NEET", ignoreCase = true) -> 
                listOf("Physics/भौतिकी", "Chemistry/रसायन शास्त्र", "Biology/जीव विज्ञान")
            target.equals("JEE", ignoreCase = true) -> 
                listOf("Physics/भौतिकी", "Chemistry/रसायन शास्त्र", "Mathematics/गणित")
            else -> 
                listOf("General Knowledge/सामान्य ज्ञान", "Current Affairs/सामयिक विषय", "Quantitative Aptitude/मात्रात्मक योग्यता", "Reasoning/तर्क")
        }
    }

    private fun getSupabaseApiDirect(context: Context): SupabaseApi? {
        val creds = R2SupabaseManager.getCredentials(context)
        if (creds.supabaseUrl.isBlank() || creds.supabaseAnonKey.isBlank()) {
            logMessage(context, "ERROR: Supabase credentials are not configured!")
            Log.e(TAG, "Supabase credentials are not configured!")
            return null
        }
        val rawUrl = creds.cleanBaseUrl
        val baseUrl = if (rawUrl.endsWith("/")) rawUrl else "$rawUrl/"
        logMessage(context, "Initializing Supabase API. Base URL: $baseUrl, Key Present: ${creds.supabaseAnonKey.isNotBlank()}")

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
        
        // Save to SharedPreferences log
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentLogs = prefs.getString("generation_logs", "") ?: ""
        // Keep logs bounded to last 50,000 characters to prevent pref bloat
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

