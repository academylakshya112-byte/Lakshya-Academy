package com.example.api

import android.util.Log
import com.example.data.GenerateContentRequest
import com.example.data.GenerateContentResponse
import com.example.data.ListModelsResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface GeminiApiService {
    @POST("{fullPath}")
    suspend fun generateContent(
        @Path(value = "fullPath", encoded = true) fullPath: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
    
    @GET("{version}/models")
    suspend fun listModels(
        @Path(value = "version", encoded = true) version: String,
        @Query("key") apiKey: String
    ): ListModelsResponse
}

class DebugNetworkInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val tag = "GeminiDebug"
        
        Log.d(tag, "=================== OUTGOING REQUEST ===================")
        
        // Extract original URL and API key
        val originalUrl = request.url
        val rawApiKey = originalUrl.queryParameter("key") ?: ""
        
        // API Key Audit & Sanitization
        var cleanedApiKey = rawApiKey.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        val keyExists = cleanedApiKey.isNotEmpty() && cleanedApiKey != "YOUR_GEMINI_API_KEY" && cleanedApiKey != "placeholder" && cleanedApiKey != "null"
        val keyLength = if (keyExists) cleanedApiKey.length else 0
        val first6 = if (keyExists && cleanedApiKey.length >= 6) cleanedApiKey.take(6) else if (keyExists) cleanedApiKey else ""
        val maskedKey = if (keyExists) "$first6${"*".repeat((keyLength - 6).coerceAtLeast(0))}" else "N/A"
        
        Log.d(tag, "=================== API KEY AUDIT ===================")
        Log.d(tag, "Secret exists: $keyExists")
        Log.d(tag, "Key length: $keyLength")
        Log.d(tag, "First 6 characters: $maskedKey")
        Log.d(tag, "=====================================================")
        
        // Stop request if API key is missing or set to placeholder
        if (!keyExists) {
            throw java.io.IOException("Missing API Key: The Gemini API Key is missing or set to the default placeholder value. Please enter your valid Gemini API key in the Secrets panel in AI Studio to activate.")
        }
        
        // Extract and audit Model Name
        val endpoint = originalUrl.encodedPath
        val hasModelInPath = endpoint.contains("/models/")
        val modelRegex = Regex("models/([^/:]+)")
        val modelMatch = modelRegex.find(endpoint)
        val modelName = if (hasModelInPath) (modelMatch?.groupValues?.get(1) ?: "") else ""
        
        if (hasModelInPath && modelName.isNotEmpty()) {
            Log.d(tag, "[3] Requested Model Name: $modelName")
        }
        
        // Log the API version explicitly
        val apiVersion = if (endpoint.contains("v1beta")) "v1beta" else "v1"
        Log.d(tag, "[15] API Version used: $apiVersion")
        
        var finalUrl = originalUrl
        if (hasModelInPath && (modelName == "gemini-3.5-flash" || modelName.isBlank())) {
            val fallbackModel = "gemini-2.5-flash"
            val newPath = endpoint.replace(modelName, fallbackModel)
            finalUrl = originalUrl.newBuilder()
                .encodedPath(newPath)
                .build()
            Log.w(tag, "AUTO-REPLACE MODEL: Replaced invalid/unsupported model '$modelName' with supported fallback model '$fallbackModel'. New endpoint path: $newPath")
        }
        
        // Rebuild full URL with sanitized API key and model name
        val rebuiltUrl = finalUrl.newBuilder()
            .setQueryParameter("key", cleanedApiKey)
            .build()
        
        val finalRequest = request.newBuilder()
            .url(rebuiltUrl)
            .build()
            
        // Log the FULL Request URL (with masked API key)
        val fullUrl = finalRequest.url.toString()
        val maskedUrl = fullUrl.replace(Regex("key=[^&]+"), "key=[MASKED_API_KEY]")
        Log.d(tag, "[1] FULL Request URL: $maskedUrl")
        Log.d(tag, "[4] Request Endpoint: ${finalRequest.url.encodedPath}")
        
        // Print all HTTP Headers (except hide API key / authorization)
        Log.d(tag, "[5] HTTP Headers:")
        val headers = finalRequest.headers
        for (i in 0 until headers.size) {
            val name = headers.name(i)
            val value = headers.value(i)
            if (name.equals("Authorization", ignoreCase = true) || name.equals("key", ignoreCase = true) || name.equals("x-goog-api-key", ignoreCase = true)) {
                Log.d(tag, "    $name: [MASKED]")
            } else {
                Log.d(tag, "    $name: $value")
            }
        }
        
        // Print the FULL Request JSON Body
        val requestBody = finalRequest.body
        var requestBodyString = ""
        if (requestBody != null) {
            try {
                val buffer = Buffer()
                requestBody.writeTo(buffer)
                requestBodyString = buffer.readUtf8()
                Log.d(tag, "[2] FULL Request JSON Body:\n$requestBodyString")
            } catch (e: Exception) {
                Log.e(tag, "Failed to read request body: ${e.message}", e)
            }
        } else {
            Log.d(tag, "[2] Request body is null")
        }
        
        // Validate request body and fields
        if (requestBodyString.isNotBlank() && finalRequest.url.encodedPath.contains("generateContent")) {
            var isValidJson = true
            try {
                val json = JSONObject(requestBodyString)
                
                // Print every field being sent to the API
                Log.d(tag, "[14] Fields present in JSON request body:")
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    Log.d(tag, "    Field: $key")
                }
                
                // Print whether contents/messages array is empty
                val contents = json.optJSONArray("contents")
                if (contents == null) {
                    Log.w(tag, "[13] Contents/messages array is empty: TRUE (Missing 'contents' field)")
                } else if (contents.length() == 0) {
                    Log.w(tag, "[13] Contents/messages array is empty: TRUE (Empty Array)")
                } else {
                    Log.d(tag, "[13] Contents/messages array is empty: FALSE (Size: ${contents.length()})")
                    
                    for (i in 0 until contents.length()) {
                        val contentObj = contents.optJSONObject(i) ?: continue
                        val parts = contentObj.optJSONArray("parts")
                        if (parts == null || parts.length() == 0) {
                            Log.w(tag, "INVALID FIELD: 'parts' inside contents[$i] is missing or empty.")
                        } else {
                            for (j in 0 until parts.length()) {
                                val partObj = parts.optJSONObject(j) ?: continue
                                val text = partObj.optString("text", null)
                                Log.d(tag, "[11] Part text (contents[$i].parts[$j]): isNullOrEmpty = ${text.isNullOrBlank()}")
                                if (text != null) {
                                    Log.d(tag, "    Text Value: $text")
                                }
                                
                                val inlineData = partObj.optJSONObject("inline_data")
                                if (inlineData != null) {
                                    val mimeType = inlineData.optString("mime_type", "unknown")
                                    val data = inlineData.optString("data", "")
                                    
                                    Log.d(tag, "[10] Inline Data detected before upload:")
                                    Log.d(tag, "    MIME type: $mimeType")
                                    
                                    if (data.isBlank()) {
                                        Log.w(tag, "[12] Inline data bytes (base64 string) is null/empty: TRUE")
                                    } else {
                                        Log.d(tag, "[12] Inline data bytes (base64 string) is null/empty: FALSE")
                                        try {
                                            val decodedBytes = android.util.Base64.decode(data, android.util.Base64.DEFAULT)
                                            Log.d(tag, "    Decoded image size: ${decodedBytes.size} bytes")
                                        } catch (bex: Exception) {
                                            Log.e(tag, "    Failed to base64 decode inline data: ${bex.message}")
                                        }
                                    }
                                } else {
                                    Log.d(tag, "[12] Inline Data (image bytes) is null: TRUE (No inline_data in this part)")
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                isValidJson = false
                Log.e(tag, "JSON format is invalid: ${e.message}")
            }
        }
        
        // Execute request
        val response: Response
        try {
            response = chain.proceed(finalRequest)
        } catch (e: Exception) {
            Log.e(tag, "[8] Complete Exception Stack Trace on connection/execution:", e)
            throw e
        }
        
        Log.d(tag, "=================== INCOMING RESPONSE ===================")
        
        // Print the complete HTTP Status Code
        val statusCode = response.code
        Log.d(tag, "[6] HTTP Status Code: $statusCode")
        
        // Print the FULL Response Body from the server
        var responseBodyString = ""
        val responseBody = response.body
        if (responseBody != null) {
            try {
                val source = responseBody.source()
                source.request(Long.MAX_VALUE)
                val buffer = source.buffer()
                responseBodyString = buffer.clone().readUtf8()
                Log.d(tag, "[7] FULL Response JSON:\n$responseBodyString")
            } catch (e: Exception) {
                Log.e(tag, "Failed to read response body: ${e.message}", e)
            }
        } else {
            Log.d(tag, "[7] Response body is null")
        }
        
        // Parse and print exact error message if not success
        if (statusCode >= 400) {
            var extractedServerMessage = ""
            try {
                val errJson = JSONObject(responseBodyString)
                val errObj = errJson.optJSONObject("error")
                if (errObj != null) {
                    extractedServerMessage = errObj.optString("message", "")
                }
            } catch (e: Exception) {
                // Ignore parsing errors
            }
            if (extractedServerMessage.isBlank()) {
                extractedServerMessage = responseBodyString.ifBlank { "HTTP $statusCode Error" }
            }
            
            Log.e(tag, "=========================================================")
            Log.e(tag, " CRITICAL: HTTP $statusCode ERROR RETURNED BY GEMINI API")
            Log.e(tag, " Exact Server Reason: $extractedServerMessage")
            Log.e(tag, "=========================================================")
        }
        
        return response
    }
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(DebugNetworkInterceptor())
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    val service: GeminiApiService by lazy {
        retrofit.create(GeminiApiService::class.java)
    }
}
