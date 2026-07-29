package com.example.service

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.api.RetrofitClient
import com.example.api.R2SupabaseManager
import com.example.api.SupabaseApi
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit

object MockTestGenerator20 {
    private const val TAG = "MockTestGen20"

    val ALL_EXAMS_2_0 = listOf(
        "Class 1", "Class 2", "Class 3", "Class 4", "Class 5",
        "Class 6", "Class 7", "Class 8", "Class 9", "Class 10",
        "Class 11 Science", "Class 11 Arts", "Class 11 Commerce",
        "Class 12 Science", "Class 12 Arts", "Class 12 Commerce",
        "Army GD", "Army Technical", "Army Nursing Assistant",
        "Air Force (Group X & Y)", "Navy (SSR / MR)", "SSC GD", "UP Police Constable", "UP SI",
        "Delhi Police Constable", "CISF Head Constable", "CRPF Constable", "BSF Tradesman", "ITBP Constable", "SSB Constable",
        "Railway Group D", "RRB NTPC", "Railway Junior Engineer (JE)",
        "NEET (Medical)", "JEE (Engineering)", "CUET (UG)", "CTET", "UPTET", "UPSSSC PET"
    )

    data class SyllabusSubject(
        val subjectName: String,
        val chapters: List<String>
    )

    data class GeneratedQuestion(
        var questionText: String,
        var optionA: String,
        var optionB: String,
        var optionC: String,
        var optionD: String,
        var correctIndex: Int,
        var explanation: String,
        var subject: String,
        var chapter: String,
        var difficulty: String
    )

    private suspend fun callGemini(prompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY" || apiKey == "placeholder") {
            throw Exception("Valid Gemini API Key is missing. Please configure it in the Secrets panel.")
        }

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = prompt)
                    )
                )
            )
        )

        val response = RetrofitClient.service.generateContent(
            "v1beta/models/gemini-3.5-flash:generateContent",
            apiKey,
            request
        )

        val responseText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("Gemini returned empty candidate content")

        return@withContext responseText.trim()
    }

    suspend fun detectSyllabus(context: Context, examName: String): List<SyllabusSubject> {
        val prompt = """
            You are an expert exam syllabus analyzer. For the exam "$examName", detect the complete official syllabus.
            Return a JSON object with a single key "subjects" which contains an array of objects.
            Each object must have "subject_name" (bilingual English/Hindi like "Mathematics / गणित") and "chapters" (an array of strings of chapters in bilingual format).

            JSON format:
            {
              "subjects": [
                {
                  "subject_name": "Subject Name / विषय का नाम",
                  "chapters": [
                    "Chapter 1 / अध्याय 1",
                    "Chapter 2 / अध्याय 2"
                  ]
                }
              ]
            }
            
            IMPORTANT: Return only the RAW JSON object. Do not include markdown formatting like ```json ... ```.
        """.trimIndent()

        val responseText = callGemini(prompt)
        val cleanResponse = responseText.trim().removeSurrounding("```json", "```").trim()
        
        val subjectsList = mutableListOf<SyllabusSubject>()
        try {
            val json = JSONObject(cleanResponse)
            val subjectsArray = json.getJSONArray("subjects")
            for (i in 0 until subjectsArray.length()) {
                val subObj = subjectsArray.getJSONObject(i)
                val subName = subObj.getString("subject_name")
                val chaptersArray = subObj.getJSONArray("chapters")
                val chaptersList = mutableListOf<String>()
                for (j in 0 until chaptersArray.length()) {
                    chaptersList.add(chaptersArray.getString(j))
                }
                subjectsList.add(SyllabusSubject(subName, chaptersList))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse syllabus response", e)
            // Fallback default syllabus based on exam name if JSON parsing fails
            subjectsList.add(SyllabusSubject("General Knowledge / सामान्य ज्ञान", listOf("General Awareness / सामान्य जागरूकता", "Current Affairs / सामयिक विषय")))
            subjectsList.add(SyllabusSubject("General Studies / सामान्य अध्ययन", listOf("Core Topics / मुख्य विषय")))
        }
        return subjectsList
    }

    suspend fun generateQuestionBatch(
        context: Context,
        examName: String,
        difficulty: String,
        syllabus: List<SyllabusSubject>,
        count: Int,
        existingQuestions: List<GeneratedQuestion>
    ): List<GeneratedQuestion> {
        val syllabusJson = JSONArray().apply {
            syllabus.forEach { sub ->
                put(JSONObject().apply {
                    put("subject_name", sub.subjectName)
                    put("chapters", JSONArray(sub.chapters))
                })
            }
        }.toString()

        val accumulated = mutableListOf<GeneratedQuestion>()
        var attempts = 0

        while (accumulated.size < count && attempts < 3) {
            attempts++
            val needed = count - accumulated.size
            val currentExisting = existingQuestions + accumulated
            val existingTexts = currentExisting.map { it.questionText }.takeLast(30).joinToString("\n---\n")

            val prompt = """
                You are an expert MCQ question generator for the exam "$examName" at "$difficulty" difficulty level.
                The detected official syllabus is:
                $syllabusJson

                Generate exactly $needed multiple-choice questions aligning with this syllabus and difficulty level.
                Mix the questions evenly across the detected subjects and chapters.

                CRITICAL DEDUPLICATION REQUIREMENTS:
                1. Do NOT generate any question similar in wording, meaning, or concept to the following previously generated questions:
                $existingTexts
                2. Every question must have 4 COMPLETELY DISTINCT options (option_a, option_b, option_c, option_d). Never repeat option choices within a question.
                3. Every single text parameter (question text, options, explanation) MUST be bilingual: English and Hindi, separated strictly by " / " (e.g. "What is the capital of India? / भारत की राजधानी क्या है?").
                4. Return a valid JSON array of objects. Do not include markdown formatting.

                JSON schema:
                [
                  {
                    "question": "Question text in English / हिन्दी में प्रश्न",
                    "option_a": "Option A in English / हिन्दी में विकल्प A",
                    "option_b": "Option B in English / हिन्दी में विकल्प B",
                    "option_c": "Option C in English / हिन्दी में विकल्प C",
                    "option_d": "Option D in English / हिन्दी में विकल्प D",
                    "correct_index": 0,
                    "explanation": "Detailed explanation / विस्तृत विवरण",
                    "subject": "Name of Subject",
                    "chapter": "Name of Chapter"
                  }
                ]
            """.trimIndent()

            try {
                val responseText = callGemini(prompt)
                val cleanResponse = responseText.trim().removeSurrounding("```json", "```").trim()
                val jsonArray = JSONArray(cleanResponse)

                for (i in 0 until jsonArray.length()) {
                    if (accumulated.size >= count) break
                    val item = jsonArray.getJSONObject(i)
                    val candidate = GeneratedQuestion(
                        questionText = item.getString("question"),
                        optionA = item.getString("option_a"),
                        optionB = item.getString("option_b"),
                        optionC = item.getString("option_c"),
                        optionD = item.getString("option_d"),
                        correctIndex = item.getInt("correct_index"),
                        explanation = item.optString("explanation", "Bilingual explanation / द्विभाषी विवरण"),
                        subject = item.optString("subject", "General"),
                        chapter = item.optString("chapter", "General"),
                        difficulty = difficulty
                    )

                    if (!QuestionDeduplicator.isDuplicateAgainstList(candidate, existingQuestions + accumulated)) {
                        accumulated.add(candidate)
                    } else {
                        Log.d(TAG, "Discarded duplicate/invalid question batch item: ${candidate.questionText.take(40)}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating batch attempt $attempts", e)
                delay(1000L)
            }
        }
        return accumulated
    }

    suspend fun regenerateSingleQuestion(
        context: Context,
        examName: String,
        difficulty: String,
        currentQuestion: GeneratedQuestion,
        existingQuestions: List<GeneratedQuestion>
    ): GeneratedQuestion {
        var attempts = 0
        while (attempts < 10) {
            attempts++
            val existingTexts = existingQuestions.map { it.questionText }.takeLast(30).joinToString("\n---\n")

            val prompt = """
                You are an expert MCQ question generator. Generate a SINGLE high-quality, syllabus-aligned multiple choice question for the exam "$examName" at "$difficulty" difficulty level.
                
                The question must specifically be for:
                - Subject: ${currentQuestion.subject}
                - Chapter: ${currentQuestion.chapter}

                CRITICAL DEDUPLICATION REQUIREMENTS:
                1. Do NOT generate any question similar in wording, meaning, or concept to the following previously generated questions:
                $existingTexts
                2. The 4 options MUST be completely distinct from each other.
                3. Every single text parameter (question text, options, explanation) MUST be bilingual: English and Hindi, separated strictly by " / " (e.g. "What is the capital of India? / भारत की राजधानी क्या है?").
                4. Return a valid RAW JSON object. Do not include markdown formatting.

                JSON schema:
                {
                  "question": "Question text in English / हिन्दी में प्रश्न",
                  "option_a": "Option A in English / हिन्दी में विकल्प A",
                  "option_b": "Option B in English / हिन्दी में विकल्प B",
                  "option_c": "Option C in English / हिन्दी में विकल्प C",
                  "option_d": "Option D in English / हिन्दी में विकल्प D",
                  "correct_index": 0,
                  "explanation": "Detailed explanation / विस्तृत विवरण"
                }
            """.trimIndent()

            try {
                val responseText = callGemini(prompt)
                val cleanResponse = responseText.trim().removeSurrounding("```json", "```").trim()
                val item = JSONObject(cleanResponse)
                
                val candidate = GeneratedQuestion(
                    questionText = item.getString("question"),
                    optionA = item.getString("option_a"),
                    optionB = item.getString("option_b"),
                    optionC = item.getString("option_c"),
                    optionD = item.getString("option_d"),
                    correctIndex = item.getInt("correct_index"),
                    explanation = item.optString("explanation", "Bilingual explanation / द्विभाषी विवरण"),
                    subject = currentQuestion.subject,
                    chapter = currentQuestion.chapter,
                    difficulty = difficulty
                )

                if (!QuestionDeduplicator.isDuplicateAgainstList(candidate, existingQuestions)) {
                    return candidate
                } else {
                    Log.d(TAG, "Regenerated question attempt $attempts was duplicate, retrying...")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Regeneration attempt $attempts failed", e)
                delay(1000L)
            }
        }
        
        throw Exception("Failed to generate a unique question after 10 attempts.")
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

    suspend fun publishMockTest(
        context: Context,
        repository: AcademyRepository,
        examName: String,
        difficulty: String,
        questions: List<GeneratedQuestion>,
        onStepUpdate: (String) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Log.d("MockTestGenerator20", "[PUBLISH] Starting publication of custom Mock Test for Exam: $examName, Difficulty: $difficulty")
            onStepUpdate("Initializing Supabase connection...")
            val supabaseApi = getSupabaseApiDirect(context)
                ?: return@withContext Result.failure(Exception("Supabase credentials are not configured properly."))

            // 1. Create TestEntity
            val title = "AI 2.0 Mock Test - $examName ($difficulty) - ${System.currentTimeMillis() % 10000}"
            val testEntity = TestEntity(
                title = title,
                type = "Mock Test",
                durationMinutes = 60,
                hasNegativeMarking = true,
                marksPerCorrect = 2,
                marksPerWrong = -0.5f
            )
            Log.d("MockTestGenerator20", "[PUBLISH] Created TestEntity in memory with title: '$title'")

            onStepUpdate("Saving mock test metadata to Supabase...")
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val jsonAdapter = moshi.adapter(TestEntity::class.java)
            val jsonStr = jsonAdapter.toJson(testEntity)
            val jsonObject = JSONObject(jsonStr).apply {
                remove("id") // Let Supabase autogenerate ID
            }
            
            val requestBody = jsonObject.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val responseList = supabaseApi.insertTest(requestBody)
            val insertedTest = responseList.firstOrNull()
                ?: return@withContext Result.failure(Exception("Failed to save test metadata (empty response from Supabase)."))

            Log.d("MockTestGenerator20", "[PUBLISH] Inserted test metadata on Supabase successfully. Assigned ID: ${insertedTest.id}")
            onStepUpdate("Test metadata saved successfully. Assigned ID: ${insertedTest.id}. Now saving 50 questions...")

            // 2. Format and Save Questions
            val questionAdapter = moshi.adapter(QuestionEntity::class.java)
            val savedQuestions = mutableListOf<QuestionEntity>()

            Log.d("MockTestGenerator20", "[PUBLISH] Performing pre-save uniqueness validation for 50 questions...")
            onStepUpdate("Validating question uniqueness before saving...")
            val finalValidatedQuestions = mutableListOf<GeneratedQuestion>()
            for (i in questions.indices) {
                val q = questions[i]
                if (!QuestionDeduplicator.isDuplicateAgainstList(q, finalValidatedQuestions)) {
                    finalValidatedQuestions.add(q)
                } else {
                    Log.d("MockTestGenerator20", "[PUBLISH] Pre-save validation detected duplicate at position ${i + 1}. Regenerating replacement...")
                    onStepUpdate("Pre-save check detected duplicate question at position ${i + 1}. Regenerating replacement...")
                    var replacement = q
                    var repAttempts = 0
                    var isStillDup = true
                    while (isStillDup && repAttempts < 10) {
                        repAttempts++
                        try {
                            replacement = regenerateSingleQuestion(
                                context = context,
                                examName = examName,
                                difficulty = difficulty,
                                currentQuestion = q,
                                existingQuestions = finalValidatedQuestions
                            )
                            if (!QuestionDeduplicator.isDuplicateAgainstList(replacement, finalValidatedQuestions)) {
                                isStillDup = false
                            }
                        } catch (e: Exception) {
                            delay(1000L)
                        }
                    }
                    finalValidatedQuestions.add(replacement)
                }
            }

            if (finalValidatedQuestions.size != 50) {
                return@withContext Result.failure(Exception("Pre-save validation failed: Expected exactly 50 unique questions, got ${finalValidatedQuestions.size}."))
            }

            Log.d("MockTestGenerator20", "[PUBLISH] Pre-save validation passed. Formatting and saving 50 unique questions on Supabase...")
            for (i in finalValidatedQuestions.indices) {
                val q = finalValidatedQuestions[i]
                onStepUpdate("Saving question ${i + 1}/50 to Supabase...")
                
                val metadata = JSONObject().apply {
                    put("explanation", q.explanation)
                    put("subject", q.subject)
                    put("chapter", q.chapter)
                    put("difficulty", q.difficulty)
                    put("level", "$examName Level")
                }
                val questionWithMetadata = "${q.questionText}\n\n---METADATA---\n${metadata.toString()}"

                val questionEntity = QuestionEntity(
                    testId = insertedTest.id,
                    questionText = questionWithMetadata,
                    optionA = q.optionA,
                    optionB = q.optionB,
                    optionC = q.optionC,
                    optionD = q.optionD,
                    correctIndex = q.correctIndex
                )

                val qJsonStr = questionAdapter.toJson(questionEntity)
                val qJsonObject = JSONObject(qJsonStr).apply {
                    remove("id") // Let Supabase autogenerate ID
                }
                val qRequestBody = qJsonObject.toString().toRequestBody("application/json".toMediaTypeOrNull())
                
                var saveAttempt = 0
                var saveSuccess = false
                var lastErr = ""
                while (saveAttempt < 3 && !saveSuccess) {
                    saveAttempt++
                    try {
                        supabaseApi.insertQuestion(qRequestBody)
                        saveSuccess = true
                    } catch (e: Exception) {
                        lastErr = e.localizedMessage ?: e.message ?: "Unknown error"
                        delay(1000L)
                    }
                }
                if (!saveSuccess) {
                    Log.e("MockTestGenerator20", "[PUBLISH] Failed to save question ${i + 1}/50 after 3 attempts. Clean up starting...")
                    // Try to clean up orphaned test metadata
                    try {
                        supabaseApi.deleteTestById("eq.${insertedTest.id}")
                    } catch (ex: Exception) {}
                    return@withContext Result.failure(Exception("Failed to save question ${i + 1}/50 after 3 attempts. Error: $lastErr. Cleaned up orphaned test metadata."))
                }
                savedQuestions.add(questionEntity)
                Log.d("MockTestGenerator20", "[PUBLISH] Saved question ${i + 1}/50 to Supabase successfully.")
            }

            // 3. Verify successful storage via SELECT query
            Log.d("MockTestGenerator20", "[PUBLISH] Starting verification of stored test questions on remote database...")
            onStepUpdate("Verifying successful storage on remote Supabase database...")
            var verificationSuccess = false
            try {
                val fetchedQuestions = supabaseApi.getQuestionsForTest("eq.${insertedTest.id}")
                if (fetchedQuestions.size == 50) {
                    verificationSuccess = true
                    Log.d("MockTestGenerator20", "[PUBLISH] Verification check passed. Remote database has exactly 50 questions stored for Test ID: ${insertedTest.id}")
                } else {
                    Log.e("MockTestGenerator20", "[PUBLISH] Verification failed: Remote database has ${fetchedQuestions.size} questions stored for this test, expected exactly 50.")
                    return@withContext Result.failure(Exception("Verification failed: Remote database has ${fetchedQuestions.size} questions stored for this test, expected exactly 50."))
                }
            } catch (e: Exception) {
                Log.e("MockTestGenerator20", "[PUBLISH] Verification query failed. Error: ${e.localizedMessage ?: e.message}")
                return@withContext Result.failure(Exception("Verification query failed. Error: ${e.localizedMessage ?: e.message}"))
            }

            // 4. Save to local database (Room) so it is instantly published
            Log.d("MockTestGenerator20", "[PUBLISH] Inserting test with ID ${insertedTest.id} and 50 questions into local Room database...")
            onStepUpdate("Publishing test locally to Student App...")
            try {
                val localDb = AcademyDatabase.getDatabase(context).academyDao()
                localDb.insertTest(insertedTest)
                savedQuestions.forEach { localDb.insertQuestion(it) }
                Log.d("MockTestGenerator20", "[PUBLISH] Inserted test metadata and 50 questions locally successfully. Local cache is now up to date.")
            } catch (e: Exception) {
                Log.e("MockTestGenerator20", "[PUBLISH] Saved on cloud, but local database publication failed. Error: ${e.localizedMessage ?: e.message}")
                return@withContext Result.failure(Exception("Saved on cloud, but local database publication failed. Error: ${e.localizedMessage ?: e.message}"))
            }

            Log.d("MockTestGenerator20", "[PUBLISH] Success! Custom Mock Test with ID ${insertedTest.id} is now fully published and LIVE!")
            onStepUpdate("Mock Test is now fully published and LIVE!")
            return@withContext Result.success(insertedTest.id)
        } catch (e: Exception) {
            Log.e("MockTestGenerator20", "[PUBLISH] Exception inside publishMockTest: ${e.localizedMessage ?: e.message}", e)
            return@withContext Result.failure(e)
        }
    }
}
