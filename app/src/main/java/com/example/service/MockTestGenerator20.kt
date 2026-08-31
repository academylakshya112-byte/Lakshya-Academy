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
        var difficulty: String,
        var imageUrl: String = ""
    )

    fun calculateAutoDuration(count: Int): Int {
        return when {
            count <= 40 -> count.coerceAtLeast(5)
            count <= 60 -> 60
            count <= 80 -> 90
            count <= 100 -> 120
            count <= 150 -> 180
            count <= 200 -> 240
            else -> (count * 1.2).toInt()
        }
    }

    fun calculateAutoMarks(count: Int): Int {
        return count.coerceAtLeast(1)
    }

    private suspend fun callGemini(prompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.LAKSHYA_GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY" || apiKey == "YOUR_LAKSHYA_GEMINI_API_KEY" || apiKey == "placeholder") {
            throw Exception("Valid Gemini API Key is missing. Please configure LAKSHYA_GEMINI_API_KEY in the Secrets panel.")
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
        existingQuestions: List<GeneratedQuestion>,
        onProgressUpdate: ((current: Int, target: Int, status: String) -> Unit)? = null
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
        var outerAttempts = 0
        val maxChunk = 25 // Request in chunks of max 25 questions per prompt

        while (accumulated.size < count && outerAttempts < 15) {
            outerAttempts++
            val neededTotal = count - accumulated.size
            val currentRequestCount = neededTotal.coerceAtMost(maxChunk)
            val currentExisting = existingQuestions + accumulated
            val existingTexts = currentExisting.map { it.questionText }.takeLast(25).joinToString("\n---\n")

            onProgressUpdate?.invoke(accumulated.size, count, "Generating questions batch (${accumulated.size + 1} to ${accumulated.size + currentRequestCount} of $count)...")

            val prompt = """
                You are an expert MCQ question generator for the exam "$examName" at "$difficulty" difficulty level.
                The detected official syllabus is:
                $syllabusJson

                Generate exactly $currentRequestCount multiple-choice questions aligning strictly with this syllabus.
                Mix the questions evenly across the detected subjects and chapters.

                CRITICAL DEDUPLICATION AND QUALITY REQUIREMENTS:
                1. Do NOT generate any question similar in wording, meaning, or concept to the following:
                $existingTexts
                2. Every question MUST contain:
                   - "question": Question text (bilingual English and Hindi separated strictly by " / ")
                   - "option_a": Option A text
                   - "option_b": Option B text
                   - "option_c": Option C text
                   - "option_d": Option D text
                   - "correct_index": Integer 0, 1, 2, or 3 corresponding to Option A, B, C, or D respectively.
                   - "explanation": Comprehensive step-by-step solution / explanation in bilingual English / Hindi.
                   - "subject": Subject Name
                   - "chapter": Chapter Name
                3. The 4 options MUST be completely distinct from each other.
                4. Return a valid RAW JSON array of objects. Do not include markdown or wrapping.

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

                var batchAdded = 0
                for (i in 0 until jsonArray.length()) {
                    if (accumulated.size >= count) break
                    val item = jsonArray.getJSONObject(i)
                    val candidate = GeneratedQuestion(
                        questionText = item.getString("question"),
                        optionA = item.getString("option_a"),
                        optionB = item.getString("option_b"),
                        optionC = item.getString("option_c"),
                        optionD = item.getString("option_d"),
                        correctIndex = item.optInt("correct_index", 0),
                        explanation = item.optString("explanation", "Bilingual explanation / द्विभाषी विवरण"),
                        subject = item.optString("subject", "General"),
                        chapter = item.optString("chapter", "General"),
                        difficulty = difficulty
                    )

                    if (!QuestionDeduplicator.isDuplicateAgainstList(candidate, existingQuestions + accumulated)) {
                        accumulated.add(candidate)
                        batchAdded++
                    } else {
                        Log.d(TAG, "Discarded duplicate/invalid question item: ${candidate.questionText.take(40)}")
                    }
                }

                if (batchAdded == 0) {
                    delay(1000L)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating batch attempt $outerAttempts", e)
                delay(1200L)
            }
        }

        onProgressUpdate?.invoke(accumulated.size, count, "Completed generating ${accumulated.size} unique questions.")
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
                1. Do NOT generate any question similar in wording, meaning, or concept to:
                $existingTexts
                2. The 4 options MUST be completely distinct from each other.
                3. Every single text parameter MUST be bilingual: English and Hindi separated strictly by " / ".
                4. Return a valid RAW JSON object.

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
                    correctIndex = item.optInt("correct_index", 0),
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
        title: String,
        examName: String,
        difficulty: String,
        questions: List<GeneratedQuestion>,
        durationMinutes: Int = calculateAutoDuration(questions.size),
        marksPerCorrect: Int = 1,
        marksPerWrong: Float = 0f,
        hasNegativeMarking: Boolean = false,
        isDraft: Boolean = false,
        instructions: String = "",
        subject: String = "",
        targetClass: String = "",
        examCategory: String = "",
        testCategory: String = "Competitive Mock Test",
        onStepUpdate: (String) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "[PUBLISH] Starting publication: '$title' (${questions.size} questions, ${durationMinutes}m duration)")
            onStepUpdate("Connecting to Supabase production database...")
            val supabaseApi = getSupabaseApiDirect(context)
                ?: return@withContext Result.failure(Exception("Supabase credentials are not configured properly."))

            // Check Duplicate Test in Supabase (FEATURE 10: Upsert duplicate protection)
            onStepUpdate("Checking duplicate protection for test: '$title'...")
            var existingTestId: Int? = null
            try {
                val foundList = supabaseApi.getTestByTitle("eq.$title")
                if (foundList.isNotEmpty()) {
                    existingTestId = foundList.first().id
                    Log.d(TAG, "[PUBLISH] Duplicate protection triggered. Found existing test with ID: $existingTestId. Will update via UPSERT.")
                    onStepUpdate("Existing test found (ID: $existingTestId). Clearing old questions for update...")
                    supabaseApi.deleteQuestionsForTest("eq.$existingTestId")
                }
            } catch (e: Exception) {
                Log.d(TAG, "[PUBLISH] Duplicate check query info: ${e.message}")
            }

            // Prepare TestEntity metadata
            val actualType = if (isDraft) "Draft - $testCategory" else testCategory
            val testEntity = TestEntity(
                id = existingTestId ?: 0,
                title = title,
                type = actualType,
                durationMinutes = durationMinutes,
                hasNegativeMarking = hasNegativeMarking,
                marksPerCorrect = marksPerCorrect,
                marksPerWrong = marksPerWrong
            )

            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val jsonAdapter = moshi.adapter(TestEntity::class.java)
            val jsonStr = jsonAdapter.toJson(testEntity)
            val jsonObject = JSONObject(jsonStr)

            val insertedTest: TestEntity
            if (existingTestId != null) {
                // Update existing test metadata
                jsonObject.put("id", existingTestId)
                val requestBody = jsonObject.toString().toRequestBody("application/json".toMediaTypeOrNull())
                supabaseApi.updateTest("eq.$existingTestId", requestBody)
                insertedTest = testEntity.copy(id = existingTestId)
                Log.d(TAG, "[PUBLISH] Successfully updated existing test metadata for ID: $existingTestId")
            } else {
                // Insert new test metadata
                jsonObject.remove("id") // Let Supabase auto-assign ID
                val requestBody = jsonObject.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val responseList = supabaseApi.insertTest(requestBody)
                insertedTest = responseList.firstOrNull()
                    ?: return@withContext Result.failure(Exception("Failed to save test metadata (empty response from Supabase)."))
                Log.d(TAG, "[PUBLISH] Created new test metadata on Supabase. Assigned ID: ${insertedTest.id}")
            }

            onStepUpdate("Test metadata saved (ID: ${insertedTest.id}). Now saving ${questions.size} questions...")

            // Format and Save Questions safely in database
            val questionAdapter = moshi.adapter(QuestionEntity::class.java)
            val savedQuestions = mutableListOf<QuestionEntity>()

            for (i in questions.indices) {
                val q = questions[i]
                if ((i + 1) % 10 == 0 || i == questions.lastIndex) {
                    onStepUpdate("Saving questions ${i + 1}/${questions.size} to Supabase...")
                }

                val metadata = JSONObject().apply {
                    put("explanation", q.explanation)
                    put("subject", q.subject.ifBlank { subject })
                    put("chapter", q.chapter)
                    put("difficulty", q.difficulty.ifBlank { difficulty })
                    put("imageUrl", q.imageUrl)
                    put("instructions", instructions)
                }
                val questionWithMetadata = "${q.questionText}\n\n---METADATA---\n${metadata}"

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
                    remove("id")
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
                        lastErr = e.localizedMessage ?: e.message ?: "Unknown database error"
                        delay(600L)
                    }
                }

                if (!saveSuccess) {
                    Log.e(TAG, "[PUBLISH] Failed to save question ${i + 1}/${questions.size}. Detailed error: $lastErr")
                    return@withContext Result.failure(Exception("Failed to save question ${i + 1}/${questions.size}. Error: $lastErr"))
                }
                savedQuestions.add(questionEntity)
            }

            // Save to local database (Room)
            onStepUpdate("Publishing test locally to Student App...")
            try {
                val localDb = AcademyDatabase.getDatabase(context).academyDao()
                localDb.insertTest(insertedTest)
                savedQuestions.forEach { localDb.insertQuestion(it) }
                Log.d(TAG, "[PUBLISH] Test ID ${insertedTest.id} and ${savedQuestions.size} questions saved locally.")
            } catch (e: Exception) {
                Log.e(TAG, "[PUBLISH] Local cache insert error: ${e.message}")
            }

            Log.d(TAG, "[PUBLISH] SUCCESS! Test '${title}' (ID: ${insertedTest.id}) is now fully published.")
            onStepUpdate("Mock Test is now fully published and LIVE!")
            return@withContext Result.success(insertedTest.id)
        } catch (e: Exception) {
            Log.e(TAG, "[PUBLISH] Exception in publishMockTest: ${e.localizedMessage ?: e.message}", e)
            return@withContext Result.failure(e)
        }
    }
}
