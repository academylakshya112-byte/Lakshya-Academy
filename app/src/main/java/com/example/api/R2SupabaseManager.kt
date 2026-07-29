package com.example.api

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

data class SupabaseVideo(
    val id: Int = 0,
    val title: String,
    val classText: String,
    val subject: String,
    val chapter: String,
    val description: String,
    val videoUrl: String,
    val thumbnailUrl: String = "",
    val uploadDate: String,
    val resourceType: String = "VIDEO",
    val duration: String = "",
    val orderNumber: Int = 0,
    val visibility: Boolean = true,
    val pdfUrl: String? = null
) {
    companion object {
        fun fromJson(jsonStr: String): List<SupabaseVideo> {
            val list = mutableListOf<SupabaseVideo>()
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        SupabaseVideo(
                            id = obj.optInt("id", 0),
                            title = obj.optString("title", ""),
                            classText = obj.optString("class", ""),
                            subject = obj.optString("subject", ""),
                            chapter = obj.optString("chapter", ""),
                            description = obj.optString("description", ""),
                            videoUrl = obj.optString("video_url", ""),
                            thumbnailUrl = obj.optString("thumbnail_url", ""),
                            uploadDate = obj.optString("upload_date", ""),
                            resourceType = obj.optString("resource_type", "VIDEO"),
                            duration = obj.optString("duration", ""),
                            orderNumber = obj.optInt("order_number", 0),
                            visibility = obj.optBoolean("visibility", true),
                            pdfUrl = if (!obj.isNull("pdf_url")) obj.optString("pdf_url") else null
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("SupabaseVideo", "Error parsing Supabase json", e)
            }
            return list
        }
    }
}

object R2SupabaseManager {
    private const val TAG = "R2SupabaseManager"
    private const val PREFS_NAME = "r2_supabase_config"

    // Storage configuration flag (Default: "SUPABASE", Future: "CLOUDFLARE_R2")
    const val STORAGE_PROVIDER = "SUPABASE"

    data class Credentials(
        val supabaseUrl: String,
        val supabaseAnonKey: String,
        val supabaseTable: String = "videos",
        val r2AccountId: String,
        val r2BucketName: String,
        val r2AccessKeyId: String,
        val r2SecretAccessKey: String,
        val r2PublicUrl: String
    ) {
        fun isValid(): Boolean {
            return supabaseUrl.isNotBlank() &&
                   supabaseAnonKey.isNotBlank()
        }
        
        val cleanBaseUrl: String
            get() = supabaseUrl.trim().removeSuffix("/").removeSuffix("/rest/v1").removeSuffix("/")
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.MINUTES)
        .writeTimeout(120, TimeUnit.MINUTES) // 2 hours for massive video uploads
        .readTimeout(15, TimeUnit.MINUTES)
        .build()

    private fun getDefaultValue(value: String, placeholder: String): String {
        val trimmed = value.trim()
        return if (trimmed.isBlank() || trimmed == placeholder || trimmed.startsWith("YOUR_")) {
            ""
        } else {
            trimmed
        }
    }

    fun getCredentials(context: Context): Credentials {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        val savedUrl = prefs.getString("supabase_url", "") ?: ""
        val savedAnonKey = prefs.getString("supabase_anon_key", "") ?: ""
        val savedTable = prefs.getString("supabase_table", "") ?: ""
        val savedR2AccountId = prefs.getString("r2_account_id", "") ?: ""
        val savedR2BucketName = prefs.getString("r2_bucket_name", "") ?: ""
        val savedR2AccessKeyId = prefs.getString("r2_access_key_id", "") ?: ""
        val savedR2SecretAccessKey = prefs.getString("r2_secret_access_key", "") ?: ""
        val savedR2PublicUrl = prefs.getString("r2_public_url", "") ?: ""

        val defaultUrl = getDefaultValue(com.example.BuildConfig.SUPABASE_URL, "YOUR_SUPABASE_URL")
        val defaultAnonKey = getDefaultValue(com.example.BuildConfig.SUPABASE_ANON_KEY, "YOUR_SUPABASE_ANON_KEY")
        val defaultTable = getDefaultValue(com.example.BuildConfig.SUPABASE_TABLE, "videos").ifBlank { "videos" }
        val defaultR2AccountId = getDefaultValue(com.example.BuildConfig.R2_ACCOUNT_ID, "YOUR_R2_ACCOUNT_ID")
        val defaultR2BucketName = getDefaultValue(com.example.BuildConfig.R2_BUCKET_NAME, "YOUR_R2_BUCKET_NAME")
        val defaultR2AccessKeyId = getDefaultValue(com.example.BuildConfig.R2_ACCESS_KEY_ID, "YOUR_R2_ACCESS_KEY_ID")
        val defaultR2SecretAccessKey = getDefaultValue(com.example.BuildConfig.R2_SECRET_ACCESS_KEY, "YOUR_R2_SECRET_ACCESS_KEY")
        val defaultR2PublicUrl = getDefaultValue(com.example.BuildConfig.R2_PUBLIC_URL, "YOUR_R2_PUBLIC_URL")

        return Credentials(
            supabaseUrl = savedUrl.ifBlank { defaultUrl },
            supabaseAnonKey = savedAnonKey.ifBlank { defaultAnonKey },
            supabaseTable = savedTable.ifBlank { defaultTable },
            r2AccountId = savedR2AccountId.ifBlank { defaultR2AccountId },
            r2BucketName = savedR2BucketName.ifBlank { defaultR2BucketName },
            r2AccessKeyId = savedR2AccessKeyId.ifBlank { defaultR2AccessKeyId },
            r2SecretAccessKey = savedR2SecretAccessKey.ifBlank { defaultR2SecretAccessKey },
            r2PublicUrl = savedR2PublicUrl.ifBlank { defaultR2PublicUrl }
        )
    }

    fun saveCredentials(context: Context, creds: Credentials) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString("supabase_url", creds.supabaseUrl.trim())
            .putString("supabase_anon_key", creds.supabaseAnonKey.trim())
            .putString("supabase_table", creds.supabaseTable.trim())
            .putString("r2_account_id", creds.r2AccountId.trim())
            .putString("r2_bucket_name", creds.r2BucketName.trim())
            .putString("r2_access_key_id", creds.r2AccessKeyId.trim())
            .putString("r2_secret_access_key", creds.r2SecretAccessKey.trim())
            .putString("r2_public_url", creds.r2PublicUrl.trim())
            .apply()
    }

    fun getFileInfo(context: Context, uri: Uri): Pair<String, Long> {
        var name = "video_${System.currentTimeMillis()}.mp4"
        var size = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        val n = cursor.getString(nameIndex)
                        if (!n.isNullOrBlank()) name = n
                    }
                    if (sizeIndex != -1) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying file info", e)
        }
        if (size == 0L) {
            try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    size = afd.length
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fallback file descriptor size", e)
            }
        }
        return Pair(name, size)
    }

    // OkHttp progress request body with dynamic streaming to allow retries/re-opening
    class ProgressRequestBody(
        private val context: Context,
        private val fileUri: Uri,
        private val contentType: String,
        private val contentLength: Long,
        private val onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit
    ) : RequestBody() {
        override fun contentType() = contentType.toMediaTypeOrNull()
        override fun contentLength() = contentLength

        override fun writeTo(sink: BufferedSink) {
            val buffer = ByteArray(65536) // 64KB chunks for higher performance on larger files
            var bytesWritten = 0L
            val inputStream = context.contentResolver.openInputStream(fileUri)
                ?: throw java.io.IOException("Cannot open input stream for URI: $fileUri")
            try {
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    sink.write(buffer, 0, read)
                    bytesWritten += read
                    onProgress(bytesWritten, contentLength)
                }
            } finally {
                try {
                    inputStream.close()
                } catch (e: Exception) {
                    Log.e("ProgressRequestBody", "Error closing input stream", e)
                }
            }
        }
    }

    // S3 Signature V4 calculation helper
    private object R2Signer {
        private fun bytesToHex(bytes: ByteArray): String {
            val hexChars = CharArray(bytes.size * 2)
            val chars = "0123456789abcdef".toCharArray()
            for (i in bytes.indices) {
                val v = bytes[i].toInt() and 0xFF
                hexChars[i * 2] = chars[v ushr 4]
                hexChars[i * 2 + 1] = chars[v and 0x0F]
            }
            return String(hexChars)
        }

        private fun hmacSHA256(data: ByteArray, key: ByteArray): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            return mac.doFinal(data)
        }

        private fun hmacSHA256(data: String, key: ByteArray): ByteArray {
            return hmacSHA256(data.toByteArray(Charsets.UTF_8), key)
        }

        private fun sha256Hex(data: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(data.toByteArray(Charsets.UTF_8))
            return bytesToHex(hash)
        }

        fun getSignatureHeaders(
            method: String,
            host: String,
            canonicalUri: String,
            queryParams: Map<String, String> = emptyMap(),
            accessKeyId: String,
            secretAccessKey: String,
            region: String = "auto",
            service: String = "s3"
        ): Map<String, String> {
            val isoFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val dateStampFormat = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val now = Date()
            val amzDate = isoFormat.format(now)
            val dateStamp = dateStampFormat.format(now)

            val canonicalHeaders = "host:$host\nx-amz-content-sha256:UNSIGNED-PAYLOAD\nx-amz-date:$amzDate\n"
            val signedHeaders = "host;x-amz-content-sha256;x-amz-date"
            
            val canonicalQueryString = queryParams.entries
                .sortedBy { it.key }
                .joinToString("&") { entry ->
                    val encodedKey = java.net.URLEncoder.encode(entry.key, "UTF-8")
                        .replace("+", "%20")
                        .replace("*", "%2A")
                        .replace("%7E", "~")
                    val encodedVal = java.net.URLEncoder.encode(entry.value, "UTF-8")
                        .replace("+", "%20")
                        .replace("*", "%2A")
                        .replace("%7E", "~")
                    if (entry.value.isEmpty() && !entry.key.contains("=")) {
                        "$encodedKey="
                    } else {
                        "$encodedKey=$encodedVal"
                    }
                }

            val canonicalRequest = "$method\n$canonicalUri\n$canonicalQueryString\n$canonicalHeaders\n$signedHeaders\nUNSIGNED-PAYLOAD"
            val canonicalRequestHash = sha256Hex(canonicalRequest)

            val credentialScope = "$dateStamp/$region/$service/aws4_request"
            val stringToSign = "AWS4-HMAC-SHA256\n$amzDate\n$credentialScope\n$canonicalRequestHash"

            val kDate = hmacSHA256(dateStamp, "AWS4$secretAccessKey".toByteArray(Charsets.UTF_8))
            val kRegion = hmacSHA256(region, kDate)
            val kService = hmacSHA256(service, kRegion)
            val kSigning = hmacSHA256("aws4_request", kService)

            val signature = bytesToHex(hmacSHA256(stringToSign, kSigning))
            val authorizationHeader = "AWS4-HMAC-SHA256 Credential=$accessKeyId/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"

            return mapOf(
                "Authorization" to authorizationHeader,
                "x-amz-content-sha256" to "UNSIGNED-PAYLOAD",
                "x-amz-date" to amzDate
            )
        }
    }

    // High performance R2 direct S3-compatible PUT upload
    fun uploadVideoToR2(
        context: Context,
        videoUri: Uri,
        pdfUri: Uri? = null,
        title: String,
        classText: String,
        subject: String,
        chapter: String,
        description: String,
        resourceType: String = "VIDEO",
        duration: String = "",
        orderNumber: Int = 0,
        visibility: Boolean = true,
        onProgress: (progress: Float) -> Unit,
        onSuccess: (videoUrl: String) -> Unit,
        onError: (error: String) -> Unit
    ) {
        val provider = getActiveStorageProvider(context)
        provider.uploadVideo(
            context = context,
            videoUri = videoUri,
            pdfUri = pdfUri,
            title = title,
            classText = classText,
            subject = subject,
            chapter = chapter,
            description = description,
            resourceType = resourceType,
            duration = duration,
            orderNumber = orderNumber,
            visibility = visibility,
            onProgress = onProgress,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    private fun getActiveStorageProvider(context: Context): StorageProvider {
        val sharedPrefs = context.getSharedPreferences("r2_storage_config", Context.MODE_PRIVATE)
        val defaultProvider = com.example.BuildConfig.STORAGE_PROVIDER.ifBlank { "BACKBLAZE_B2" }
        val activeProvider = sharedPrefs.getString("active_provider", defaultProvider) ?: defaultProvider
        return if (activeProvider == "BACKBLAZE_B2") {
            com.example.api.BackblazeB2StorageProvider()
        } else {
            CloudflareR2StorageProvider()
        }
    }

    internal fun uploadVideoToSupabaseInternal(
        context: Context,
        videoUri: Uri,
        pdfUri: Uri? = null,
        title: String,
        classText: String,
        subject: String,
        chapter: String,
        description: String,
        resourceType: String = "VIDEO",
        duration: String = "",
        orderNumber: Int = 0,
        visibility: Boolean = true,
        onProgress: (progress: Float) -> Unit,
        onSuccess: (videoUrl: String) -> Unit,
        onError: (error: String) -> Unit
    ) {
        val creds = getCredentials(context)
        if (!creds.isValid()) {
            onError("Supabase credentials are not configured! Please configure them in the Settings section.")
            return
        }

        val (originalName, size) = getFileInfo(context, videoUri)
        // Clean originalName or generate unique name to prevent collisions
        val extension = originalName.substringAfterLast(".", "mp4")
        val uniqueName = "lms_${System.currentTimeMillis()}.$extension"

        try {
            context.contentResolver.openInputStream(videoUri)?.use { }
                ?: throw Exception("Input stream is null")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to verify input stream for Uri $videoUri", e)
            onError("Cannot read selected file: ${e.localizedMessage}")
            return
        }

        val supabaseUrl = creds.cleanBaseUrl
        val bucketName = creds.r2BucketName.ifBlank { "videos" }
        val endpointUrl = "$supabaseUrl/storage/v1/object/$bucketName/$uniqueName"

        Log.d(TAG, "Uploading file to Supabase: $endpointUrl (size: $size bytes)")

        val finalContentType = if (extension.lowercase(Locale.US) == "pdf") "application/pdf" else "video/$extension"
        val requestBody = ProgressRequestBody(
            context = context,
            fileUri = videoUri,
            contentType = finalContentType,
            contentLength = size,
            onProgress = { bytesWritten, totalBytes ->
                val progress = if (totalBytes > 0) bytesWritten.toFloat() / totalBytes else 0f
                onProgress(progress)
            }
        )

        val request = Request.Builder()
            .url(endpointUrl)
            .post(requestBody)
            .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
            .addHeader("apikey", creds.supabaseAnonKey)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e(TAG, "=== UPLOAD NETWORK FAILURE ===")
                Log.e(TAG, "Request URL: ${request.url}")
                Log.e(TAG, "HTTP Method: ${request.method}")
                Log.e(TAG, "Headers: ${request.headers}")
                Log.e(TAG, "Content-Type: ${request.body?.contentType()}")
                Log.e(TAG, "Content-Length: ${request.body?.contentLength()}")
                Log.e(TAG, "Exception Localized Message: ${e.localizedMessage}")
                Log.e(TAG, "Exception Stack Trace:", e)
                Log.e(TAG, "===============================")
                onError("Upload network error: ${e.localizedMessage}")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val errBody = resp.body?.string() ?: ""
                        Log.e(TAG, "=== UPLOAD FAILURE DETAILS ===")
                        Log.e(TAG, "Request URL: ${request.url}")
                        Log.e(TAG, "HTTP Method: ${request.method}")
                        Log.e(TAG, "Headers: ${request.headers}")
                        Log.e(TAG, "Content-Type: ${request.body?.contentType()}")
                        Log.e(TAG, "Content-Length: ${request.body?.contentLength()}")
                        Log.e(TAG, "Response Code: ${resp.code}")
                        Log.e(TAG, "Response Body: $errBody")
                        Log.e(TAG, "===============================")
                        onError("Server rejected upload (Code ${resp.code}): $errBody")
                        return
                    }

                    Log.d(TAG, "Upload succeeded! Committing metadata to Supabase...")
                    
                    val playbackUrl = "$supabaseUrl/storage/v1/object/public/$bucketName/$uniqueName"

                    
                    // Save metadata to Supabase
                    if (pdfUri != null) {
                        uploadFileOnlyToR2(context, creds, pdfUri, { }, { pdfUrl ->
                            saveMetadataToSupabase(
                                creds = creds,
                                videoUrl = playbackUrl,
                                title = title,
                                classText = classText,
                                subject = subject,
                                chapter = chapter,
                                description = description,
                                resourceType = resourceType,
                                duration = duration,
                                orderNumber = orderNumber,
                                visibility = visibility,
                                pdfUrl = pdfUrl,
                                onSuccess = onSuccess,
                                onError = onError
                            )
                        }, onError)
                    } else {
                        saveMetadataToSupabase(
                            creds = creds,
                            videoUrl = playbackUrl,
                            title = title,
                            classText = classText,
                            subject = subject,
                            chapter = chapter,
                            description = description,
                            resourceType = resourceType,
                            duration = duration,
                            orderNumber = orderNumber,
                            visibility = visibility,
                            pdfUrl = null,
                            onSuccess = onSuccess,
                            onError = onError
                        )
                    }

                }
            }
        })
    }

    private fun readChunkBytes(context: Context, uri: Uri, offset: Long, size: Int): ByteArray {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw java.io.IOException("Cannot open input stream for URI: $uri")
        try {
            var skipped = 0L
            while (skipped < offset) {
                val s = inputStream.skip(offset - skipped)
                if (s == 0L) {
                    if (inputStream.read() == -1) break
                    skipped++
                } else {
                    skipped += s
                }
            }
            val buffer = ByteArray(size)
            var totalRead = 0
            while (totalRead < size) {
                val r = inputStream.read(buffer, totalRead, size - totalRead)
                if (r == -1) break
                totalRead += r
            }
            return if (totalRead == size) {
                buffer
            } else {
                buffer.copyOf(totalRead)
            }
        } finally {
            try {
                inputStream.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing input stream for chunk", e)
            }
        }
    }

    fun uploadFileOnlyToCloudflareR2(
        context: Context,
        creds: Credentials,
        fileUri: Uri,
        onProgress: (Float) -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val safeProgress = { p: Float -> mainHandler.post { onProgress(p) } }
        val safeSuccess = { url: String -> mainHandler.post { onSuccess(url) } }
        val safeError = { err: String -> mainHandler.post { onError(err) } }

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                if (creds.r2AccountId.isBlank() || creds.r2BucketName.isBlank() ||
                    creds.r2AccessKeyId.isBlank() || creds.r2SecretAccessKey.isBlank()) {
                    throw Exception("Cloudflare R2 credentials are not fully configured!")
                }

                val (originalName, size) = getFileInfo(context, fileUri)
                val extension = originalName.substringAfterLast(".", "mp4")
                val uniqueName = "lms_${System.currentTimeMillis()}.$extension"

                val host = "${creds.r2AccountId}.r2.cloudflarestorage.com"
                val canonicalUri = "/${creds.r2BucketName}/$uniqueName"
                
                // Determine public playback URL
                val publicUrl = if (creds.r2PublicUrl.isNotBlank()) {
                    val base = creds.r2PublicUrl.trim()
                    if (base.endsWith("/")) "$base$uniqueName" else "$base/$uniqueName"
                } else {
                    "https://$host/${creds.r2BucketName}/$uniqueName"
                }

                Log.d(TAG, "Starting Cloudflare R2 Upload for $originalName ($size bytes)")
                Log.d(TAG, "Unique object key: $uniqueName")
                Log.d(TAG, "R2 host: $host")
                Log.d(TAG, "Expected Public URL: $publicUrl")

                // If file is small (<= 10MB), upload in a single PUT request
                val tenMB = 10L * 1024 * 1024
                if (size <= tenMB) {
                    Log.d(TAG, "File size is <= 10MB ($size bytes). Doing standard S3 PUT upload.")
                    val uploadUrl = "https://$host/${creds.r2BucketName}/$uniqueName"
                    val sigHeaders = R2Signer.getSignatureHeaders(
                        method = "PUT",
                        host = host,
                        canonicalUri = canonicalUri,
                        queryParams = emptyMap(),
                        accessKeyId = creds.r2AccessKeyId,
                        secretAccessKey = creds.r2SecretAccessKey
                    )

                    val requestBody = ProgressRequestBody(
                        context = context,
                        fileUri = fileUri,
                        contentType = "video/mp4",
                        contentLength = size,
                        onProgress = { bytesWritten, totalBytes ->
                            val progress = if (totalBytes > 0) bytesWritten.toFloat() / totalBytes else 0f
                            safeProgress(progress)
                        }
                    )

                    val requestBuilder = Request.Builder()
                        .url(uploadUrl)
                        .put(requestBody)
                    
                    sigHeaders.forEach { (k, v) ->
                        requestBuilder.addHeader(k, v)
                    }

                    client.newCall(requestBuilder.build()).execute().use { response ->
                        if (!response.isSuccessful) {
                            val errBody = response.body?.string() ?: ""
                            Log.e(TAG, "R2 Standard PUT Upload failed (Code ${response.code}): $errBody")
                            throw Exception("Cloudflare R2 standard upload error (Code ${response.code}): $errBody")
                        }
                        Log.d(TAG, "R2 Standard PUT Upload succeeded!")
                        safeSuccess(publicUrl)
                    }
                } else {
                    // File > 10MB, use S3 Multipart Upload!
                    Log.d(TAG, "File size is > 10MB ($size bytes). Using production-grade S3 Multipart Upload.")
                    
                    // Step 1: Initiate Multipart Upload
                    val initUrl = "https://$host/${creds.r2BucketName}/$uniqueName?uploads"
                    val initSigHeaders = R2Signer.getSignatureHeaders(
                        method = "POST",
                        host = host,
                        canonicalUri = canonicalUri,
                        queryParams = mapOf("uploads" to ""),
                        accessKeyId = creds.r2AccessKeyId,
                        secretAccessKey = creds.r2SecretAccessKey
                    )

                    val initBuilder = Request.Builder()
                        .url(initUrl)
                        .post(ByteArray(0).toRequestBody("application/xml".toMediaTypeOrNull()))
                    
                    initSigHeaders.forEach { (k, v) ->
                        initBuilder.addHeader(k, v)
                    }

                    var uploadId = ""
                    client.newCall(initBuilder.build()).execute().use { response ->
                        val responseBody = response.body?.string() ?: ""
                        if (!response.isSuccessful) {
                            Log.e(TAG, "R2 Multipart Initiation failed (Code ${response.code}): $responseBody")
                            throw Exception("Failed to initiate R2 multipart upload (Code ${response.code}): $responseBody")
                        }
                        
                        uploadId = "<UploadId>(.*?)</UploadId>".toRegex().find(responseBody)?.groupValues?.get(1) ?: ""
                        if (uploadId.isBlank()) {
                            throw Exception("Failed to parse UploadId from R2 response: $responseBody")
                        }
                    }

                    Log.d(TAG, "R2 Multipart Upload initiated. Upload ID: $uploadId")

                    val chunkSize = 10L * 1024 * 1024 // 10MB chunk size
                    val totalParts = ((size + chunkSize - 1) / chunkSize).toInt()
                    val partEtags = mutableListOf<Pair<Int, String>>()
                    var bytesUploaded = 0L

                    for (partNumber in 1..totalParts) {
                        val partOffset = (partNumber - 1) * chunkSize
                        val partSize = kotlin.math.min(chunkSize, size - partOffset).toInt()

                        var partSuccess = false
                        var retryCount = 0
                        var lastError: Exception? = null

                        while (!partSuccess && retryCount < 5) {
                            try {
                                Log.d(TAG, "Uploading Part $partNumber / $totalParts (Offset: $partOffset, Size: $partSize, Attempt: ${retryCount + 1})")
                                val partUrl = "https://$host/${creds.r2BucketName}/$uniqueName?uploadId=$uploadId&partNumber=$partNumber"
                                val partSigHeaders = R2Signer.getSignatureHeaders(
                                    method = "PUT",
                                    host = host,
                                    canonicalUri = canonicalUri,
                                    queryParams = mapOf("uploadId" to uploadId, "partNumber" to partNumber.toString()),
                                    accessKeyId = creds.r2AccessKeyId,
                                    secretAccessKey = creds.r2SecretAccessKey
                                )

                                val chunkData = readChunkBytes(context, fileUri, partOffset, partSize)
                                val chunkRequestBody = chunkData.toRequestBody("application/octet-stream".toMediaTypeOrNull())

                                val partRequestBuilder = Request.Builder()
                                    .url(partUrl)
                                    .put(chunkRequestBody)
                                
                                partSigHeaders.forEach { (k, v) ->
                                    partRequestBuilder.addHeader(k, v)
                                }

                                client.newCall(partRequestBuilder.build()).execute().use { partResponse ->
                                    val responseBody = partResponse.body?.string() ?: ""
                                    if (!partResponse.isSuccessful) {
                                        throw Exception("Part $partNumber upload failed (Code ${partResponse.code}): $responseBody")
                                    }
                                    val etag = partResponse.header("ETag")
                                        ?: throw Exception("Part $partNumber response missing ETag header")
                                    
                                    val cleanEtag = if (etag.startsWith("\"") && etag.endsWith("\"")) etag else "\"$etag\""
                                    synchronized(partEtags) {
                                        partEtags.add(Pair(partNumber, cleanEtag))
                                    }
                                    bytesUploaded += partSize
                                    val overallProgress = bytesUploaded.toFloat() / size
                                    safeProgress(overallProgress)
                                    partSuccess = true
                                }
                            } catch (e: Exception) {
                                retryCount++
                                lastError = e
                                Log.e(TAG, "Error uploading Part $partNumber (Attempt $retryCount/5): ${e.message}")
                                kotlinx.coroutines.delay(2000L * retryCount)
                            }
                        }

                        if (!partSuccess) {
                            throw lastError ?: Exception("Failed to upload part $partNumber after 5 attempts")
                        }
                    }

                    // Step 3: Complete Multipart Upload
                    Log.d(TAG, "All parts uploaded. Completing R2 Multipart Upload...")
                    partEtags.sortBy { it.first }

                    val partsXml = partEtags.joinToString("\n") { (num, etag) ->
                        "  <Part>\n    <PartNumber>$num</PartNumber>\n    <ETag>$etag</ETag>\n  </Part>"
                    }
                    val completeXml = "<CompleteMultipartUpload>\n$partsXml\n</CompleteMultipartUpload>"

                    val completeUrl = "https://$host/${creds.r2BucketName}/$uniqueName?uploadId=$uploadId"
                    val completeSigHeaders = R2Signer.getSignatureHeaders(
                        method = "POST",
                        host = host,
                        canonicalUri = canonicalUri,
                        queryParams = mapOf("uploadId" to uploadId),
                        accessKeyId = creds.r2AccessKeyId,
                        secretAccessKey = creds.r2SecretAccessKey
                    )

                    val completeBuilder = Request.Builder()
                        .url(completeUrl)
                        .post(completeXml.toRequestBody("application/xml".toMediaTypeOrNull()))
                    
                    completeSigHeaders.forEach { (k, v) ->
                        completeBuilder.addHeader(k, v)
                    }

                    client.newCall(completeBuilder.build()).execute().use { response ->
                        val responseBody = response.body?.string() ?: ""
                        if (!response.isSuccessful) {
                            Log.e(TAG, "R2 Multipart Completion failed (Code ${response.code}): $responseBody")
                            throw Exception("Failed to complete R2 multipart upload (Code ${response.code}): $responseBody")
                        }
                        Log.d(TAG, "R2 Multipart Upload completed successfully!")
                        safeSuccess(publicUrl)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "R2 Upload failed completely: ${e.message}", e)
                safeError(e.message ?: "Unknown Cloudflare R2 upload error")
            }
        }
    }

    fun uploadFileOnlyToSupabase(
        context: Context,
        creds: Credentials,
        fileUri: Uri,
        bucketName: String = "videos",
        onProgress: (Float) -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val safeProgress = { p: Float -> mainHandler.post { onProgress(p) } }
        val safeSuccess = { url: String -> mainHandler.post { onSuccess(url) } }
        val safeError = { err: String -> mainHandler.post { onError(err) } }

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                ensureBucketExists(creds, bucketName)

                val (originalName, size) = getFileInfo(context, fileUri)
                val extension = originalName.substringAfterLast(".", "")
                val finalExtension = if (extension.isBlank()) {
                    if (fileUri.toString().contains(".pdf", ignoreCase = true)) "pdf" else "jpg"
                } else {
                    extension
                }
                val uniqueName = "lms_${System.currentTimeMillis()}.$finalExtension"

                val supabaseUrl = creds.cleanBaseUrl
                val endpointUrl = "$supabaseUrl/storage/v1/object/$bucketName/$uniqueName"

                val extLower = finalExtension.lowercase(java.util.Locale.US)
                val finalContentType = when (extLower) {
                    "pdf" -> "application/pdf"
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    "gif" -> "image/gif"
                    else -> "application/octet-stream"
                }

                val requestBody = ProgressRequestBody(
                    context = context,
                    fileUri = fileUri,
                    contentType = finalContentType,
                    contentLength = size,
                    onProgress = { bytesWritten, totalBytes ->
                        val progress = if (totalBytes > 0) bytesWritten.toFloat() / totalBytes else 0f
                        safeProgress(progress)
                    }
                )

                val request = Request.Builder()
                    .url(endpointUrl)
                    .post(requestBody)
                    .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
                    .addHeader("apikey", creds.supabaseAnonKey)
                    .build()

                client.newCall(request).enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                        Log.e(TAG, "=== SUPABASE UPLOAD NETWORK FAILURE ===")
                        Log.e(TAG, "Request URL: ${request.url}")
                        Log.e(TAG, "Exception: ${e.localizedMessage}")
                        Log.e(TAG, "=======================================")
                        safeError("Upload network error: ${e.localizedMessage}")
                    }

                    override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                        response.use { resp ->
                            try {
                                if (!resp.isSuccessful) {
                                    val bodyErr = resp.body?.string() ?: ""
                                    Log.e(TAG, "=== SUPABASE UPLOAD FAILURE DETAILS ===")
                                    Log.e(TAG, "Response Code: ${resp.code}")
                                    Log.e(TAG, "Response Body: $bodyErr")
                                    Log.e(TAG, "=======================================")
                                    safeError("Server rejected upload (Code ${resp.code}): $bodyErr")
                                    return
                                }
                                val playbackUrl = "$supabaseUrl/storage/v1/object/public/$bucketName/$uniqueName"
                                safeSuccess(playbackUrl)
                            } catch (ex: Exception) {
                                safeError("Upload response parsing error: ${ex.localizedMessage}")
                            }
                        }
                    }
                })
            } catch (e: Exception) {
                safeError("Failed to start upload: ${e.localizedMessage}")
            }
        }
    }

    private fun escapeJson(value: String): String {
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }


    private fun uploadFileOnlyToR2(
        context: Context,
        creds: Credentials,
        fileUri: Uri,
        onProgress: (Float) -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val (originalName, size) = getFileInfo(context, fileUri)
            val extension = originalName.substringAfterLast(".", "")
            val finalExtension = if (extension.isBlank()) {
                if (fileUri.toString().contains(".pdf", ignoreCase = true)) "pdf" else "mp4"
            } else {
                extension
            }
            val uniqueName = "lms_${System.currentTimeMillis()}.$finalExtension"

            try {
                context.contentResolver.openInputStream(fileUri)?.use { }
                    ?: throw Exception("Input stream is null")
            } catch (e: Exception) {
                onError("Cannot read selected file: ${e.localizedMessage}")
                return
            }

            val supabaseUrl = creds.cleanBaseUrl
            val bucketName = creds.r2BucketName.ifBlank { "videos" }
            val endpointUrl = "$supabaseUrl/storage/v1/object/$bucketName/$uniqueName"

            val extLower = finalExtension.lowercase(java.util.Locale.US)
            val finalContentType = when (extLower) {
                "pdf" -> "application/pdf"
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                else -> "video/$finalExtension"
            }

            val requestBody = ProgressRequestBody(
                context = context,
                fileUri = fileUri,
                contentType = finalContentType,
                contentLength = size,
                onProgress = { bytesWritten, totalBytes ->
                    val progress = if (totalBytes > 0) bytesWritten.toFloat() / totalBytes else 0f
                    onProgress(progress)
                }
            )

            val request = Request.Builder()
                .url(endpointUrl)
                .post(requestBody)
                .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
                .addHeader("apikey", creds.supabaseAnonKey)
                .build()

            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    Log.e(TAG, "=== UPLOAD FILE NETWORK FAILURE ===")
                    Log.e(TAG, "Request URL: ${request.url}")
                    Log.e(TAG, "HTTP Method: ${request.method}")
                    Log.e(TAG, "Headers: ${request.headers}")
                    Log.e(TAG, "Content-Type: ${request.body?.contentType()}")
                    Log.e(TAG, "Content-Length: ${request.body?.contentLength()}")
                    Log.e(TAG, "Exception Localized Message: ${e.localizedMessage}")
                    Log.e(TAG, "Exception Stack Trace:", e)
                    Log.e(TAG, "===================================")
                    onError("Upload network error: ${e.localizedMessage}")
                }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    response.use { resp ->
                        try {
                            if (!resp.isSuccessful) {
                                val bodyErr = resp.body?.string() ?: ""
                                Log.e(TAG, "=== UPLOAD FILE FAILURE DETAILS ===")
                                Log.e(TAG, "Request URL: ${request.url}")
                                Log.e(TAG, "HTTP Method: ${request.method}")
                                Log.e(TAG, "Headers: ${request.headers}")
                                Log.e(TAG, "Content-Type: ${request.body?.contentType()}")
                                Log.e(TAG, "Content-Length: ${request.body?.contentLength()}")
                                Log.e(TAG, "Response Code: ${resp.code}")
                                Log.e(TAG, "Response Body: $bodyErr")
                                Log.e(TAG, "===================================")
                                onError("Server rejected upload (Code ${resp.code}): $bodyErr")
                                return
                            }
                            val playbackUrl = "$supabaseUrl/storage/v1/object/public/$bucketName/$uniqueName"
                            onSuccess(playbackUrl)
                        } catch (ex: Exception) {
                            onError("Upload response parsing error: ${ex.localizedMessage}")
                        }
                    }
                }
            })
        } catch (e: java.io.FileNotFoundException) {
            onError("File not found: ${e.localizedMessage}")
        } catch (e: OutOfMemoryError) {
            System.gc()
            onError("Out of memory error while preparing file upload!")
        } catch (e: Exception) {
            onError("Failed to start upload: ${e.localizedMessage}")
        }
    }

    internal fun saveMetadataToSupabase(

        creds: Credentials,
        videoUrl: String,
        title: String,
        classText: String,
        subject: String,
        chapter: String,
        description: String,
        resourceType: String = "VIDEO",
        duration: String = "",
        orderNumber: Int = 0,
        visibility: Boolean = true,
        pdfUrl: String? = null,
        thumbnailUrl: String = "",
        onSuccess: (videoUrl: String) -> Unit,
        onError: (error: String) -> Unit
    ) {
        val supabaseUrl = creds.cleanBaseUrl
        val insertUrl = "$supabaseUrl/rest/v1/${creds.supabaseTable}"

        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val uploadDate = isoFormat.format(Date())

        android.util.Log.i("R2SupabaseManager", "[DATABASE UPDATE] Preparing to save metadata. thumbnail_url value BEFORE saving: $thumbnailUrl")

        val jsonBody = """
            {
              "title": "${escapeJson(title)}",
              "class": "${escapeJson(classText)}",
              "subject": "${escapeJson(subject)}",
              "chapter": "${escapeJson(chapter)}",
              "description": "${escapeJson(description)}",
              "video_url": "${escapeJson(videoUrl)}",
              "thumbnail_url": "${escapeJson(thumbnailUrl)}",
              "upload_date": "$uploadDate",
              "resource_type": "$resourceType",
              "duration": "$duration",
              "order_number": $orderNumber,
              "visibility": $visibility
            }
        """.trimIndent()

        val request = Request.Builder()
            .url(insertUrl)
            .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
            .addHeader("apikey", creds.supabaseAnonKey)
            .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=representation")
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e(TAG, "Supabase metadata save failed network", e)
                onError("Saved to R2, but Supabase register failed: ${e.localizedMessage}")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val errBody = resp.body?.string() ?: ""
                        Log.e(TAG, "Supabase metadata save rejected: Code ${resp.code}. Body: $errBody")
                        onError("Video uploaded but metadata registration failed on Supabase (Code ${resp.code})")
                    } else {
                        Log.d(TAG, "Supabase metadata save succeeded!")
                        android.util.Log.i("R2SupabaseManager", "[DATABASE UPDATE SUCCESS] Metadata successfully saved to Supabase. thumbnail_url value AFTER saving: $thumbnailUrl")
                        
                        // Automatically link to the corresponding lesson in the lessons table
                        getOrCreateCourseIdForBatch(creds, classText, subject) { courseId ->
                            createOrUpdateLessonInSupabase(
                                creds = creds,
                                courseId = courseId,
                                title = title,
                                subject = subject,
                                chapter = chapter,
                                videoUrl = videoUrl,
                                pdfUrl = pdfUrl
                            )
                        }
                        
                        onSuccess(videoUrl)
                    }
                }
            }
        })
    }

    internal fun getOrCreateCourseIdForBatch(
        creds: Credentials,
        classText: String,
        subject: String,
        onResult: (Int) -> Unit
    ) {
        val supabaseUrl = creds.cleanBaseUrl
        val coursesUrl = "$supabaseUrl/rest/v1/courses?select=*"
        
        val request = Request.Builder()
            .url(coursesUrl)
            .get()
            .addHeader("apikey", creds.supabaseAnonKey)
            .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
            .build()
            
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e(TAG, "Failed to fetch courses, using fallback courseId 1", e)
                onResult(1)
            }
            
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        Log.e(TAG, "Courses request not successful, using fallback courseId 1")
                        onResult(1)
                        return
                    }
                    val json = resp.body?.string() ?: "[]"
                    try {
                        val array = JSONArray(json)
                        var matchedId = -1
                        // 1. Precise match
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            val title = obj.optString("title", "")
                            if (title.trim().equals(classText.trim(), ignoreCase = true)) {
                                matchedId = obj.optInt("id", -1)
                                break
                            }
                        }
                        // 2. Contains match
                        if (matchedId == -1) {
                            for (i in 0 until array.length()) {
                                val obj = array.getJSONObject(i)
                                val title = obj.optString("title", "")
                                if (title.trim().contains(classText.trim(), ignoreCase = true) || 
                                    classText.trim().contains(title.trim(), ignoreCase = true)) {
                                    matchedId = obj.optInt("id", -1)
                                    break
                                }
                            }
                        }
                        
                        if (matchedId != -1) {
                            onResult(matchedId)
                        } else {
                            createCourseForBatch(creds, classText, subject, onResult)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing courses list", e)
                        onResult(1)
                    }
                }
            }
        })
    }

    private fun createCourseForBatch(
        creds: Credentials,
        classText: String,
        subject: String,
        onResult: (Int) -> Unit
    ) {
        val supabaseUrl = creds.cleanBaseUrl
        val insertCourseUrl = "$supabaseUrl/rest/v1/courses"
        
        val jsonBody = """
            {
              "title": "${escapeJson(classText)}",
              "category": "Target Batch",
              "subject": "${escapeJson(subject)}",
              "description": "LMS Course for ${escapeJson(classText)}",
              "isFree": false,
              "price": 0.0,
              "totalLessons": 1
            }
        """.trimIndent()
        
        val request = Request.Builder()
            .url(insertCourseUrl)
            .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
            .addHeader("apikey", creds.supabaseAnonKey)
            .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=representation")
            .build()
            
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e(TAG, "Failed to create course, using fallback courseId 1", e)
                onResult(1)
            }
            
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        Log.e(TAG, "Create course rejected: ${resp.code}, using fallback courseId 1")
                        onResult(1)
                        return
                    }
                    val json = resp.body?.string() ?: "[]"
                    try {
                        val array = JSONArray(json)
                        if (array.length() > 0) {
                            val id = array.getJSONObject(0).optInt("id", 1)
                            onResult(id)
                        } else {
                            onResult(1)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing created course response", e)
                        onResult(1)
                    }
                }
            }
        })
    }

    internal fun createOrUpdateLessonInSupabase(
        creds: Credentials,
        courseId: Int,
        title: String,
        subject: String,
        chapter: String,
        videoUrl: String,
        pdfUrl: String?
    ) {
        val supabaseUrl = creds.cleanBaseUrl
        val queryUrl = "$supabaseUrl/rest/v1/lessons".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("courseId", "eq.$courseId")
            ?.addQueryParameter("chapterName", "eq.${subject.trim()}")
            ?.addQueryParameter("folder", "eq.${chapter.trim()}")
            ?.addQueryParameter("title", "eq.${title.trim()}")
            ?.build()?.toString() ?: ""

        val request = Request.Builder()
            .url(queryUrl)
            .get()
            .addHeader("apikey", creds.supabaseAnonKey)
            .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e(TAG, "Failed to check existing lessons", e)
                insertNewLesson(creds, courseId, title, subject, chapter, videoUrl, pdfUrl)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        Log.e(TAG, "Existing lesson check failed: ${resp.code}")
                        insertNewLesson(creds, courseId, title, subject, chapter, videoUrl, pdfUrl)
                        return
                    }
                    val json = resp.body?.string() ?: "[]"
                    try {
                        val array = JSONArray(json)
                        if (array.length() > 0) {
                            val id = array.getJSONObject(0).optInt("id", -1)
                            if (id != -1) {
                                updateExistingLesson(creds, id, videoUrl, pdfUrl)
                            }
                        } else {
                            insertNewLesson(creds, courseId, title, subject, chapter, videoUrl, pdfUrl)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing existing lesson check", e)
                        insertNewLesson(creds, courseId, title, subject, chapter, videoUrl, pdfUrl)
                    }
                }
            }
        })
    }

    private fun insertNewLesson(
        creds: Credentials,
        courseId: Int,
        title: String,
        subject: String,
        chapter: String,
        videoUrl: String,
        pdfUrl: String?
    ) {
        val supabaseUrl = creds.cleanBaseUrl
        val insertUrl = "$supabaseUrl/rest/v1/lessons"

        val jsonBody = """
            {
              "courseId": $courseId,
              "chapterName": "${escapeJson(subject)}",
              "title": "${escapeJson(title)}",
              "videoUrl": "${escapeJson(videoUrl)}",
              "folder": "${escapeJson(chapter)}",
              "pdfUrl": "${escapeJson(pdfUrl ?: "")}",
              "pdfName": "${if (pdfUrl != null) "Notes" else ""}",
              "videoSourceType": "MP4"
            }
        """.trimIndent()

        val request = Request.Builder()
            .url(insertUrl)
            .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
            .addHeader("apikey", creds.supabaseAnonKey)
            .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e(TAG, "Failed to insert new lesson", e)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        Log.e(TAG, "Insert new lesson rejected: ${resp.code}. Body: $body")
                    } else {
                        Log.d(TAG, "Successfully created corresponding lesson in lessons table")
                    }
                }
            }
        })
    }

    private fun updateExistingLesson(
        creds: Credentials,
        lessonId: Int,
        videoUrl: String,
        pdfUrl: String?
    ) {
        val supabaseUrl = creds.cleanBaseUrl
        val updateUrl = "$supabaseUrl/rest/v1/lessons?id=eq.$lessonId"

        val jsonBody = """
            {
              "videoUrl": "${escapeJson(videoUrl)}",
              "pdfUrl": "${escapeJson(pdfUrl ?: "")}",
              "pdfName": "${if (pdfUrl != null) "Notes" else ""}"
            }
        """.trimIndent()

        val request = Request.Builder()
            .url(updateUrl)
            .patch(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
            .addHeader("apikey", creds.supabaseAnonKey)
            .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e(TAG, "Failed to update existing lesson", e)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        Log.e(TAG, "Update existing lesson rejected: ${resp.code}. Body: $body")
                    } else {
                        Log.d(TAG, "Successfully updated corresponding lesson in lessons table")
                    }
                }
            }
        })
    }

    fun listSupabaseStorageFiles(
        context: Context,
        bucketName: String = "videos",
        callback: (List<String>?, String?) -> Unit
    ) {
        val creds = getCredentials(context)
        if (!creds.isValid()) {
            callback(null, "Credentials not configured")
            return
        }
        val supabaseUrl = creds.cleanBaseUrl
        val listUrl = "$supabaseUrl/storage/v1/object/list/$bucketName"

        val jsonBody = """
            {
              "limit": 100,
              "offset": 0,
              "sortBy": {
                "column": "name",
                "order": "asc"
              }
            }
        """.trimIndent()

        val request = Request.Builder()
            .url(listUrl)
            .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
            .addHeader("apikey", creds.supabaseAnonKey)
            .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e(TAG, "[EXISTING THUMBNAIL MIGRATION] Failed to list storage files", e)
                callback(null, e.localizedMessage)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        Log.e(TAG, "[EXISTING THUMBNAIL MIGRATION] List files rejected: ${resp.code}. Body: $body")
                        callback(null, "Server error: ${resp.code}")
                    } else {
                        val jsonStr = resp.body?.string() ?: "[]"
                        val fileNames = mutableListOf<String>()
                        try {
                            val arr = JSONArray(jsonStr)
                            for (i in 0 until arr.length()) {
                                val obj = arr.getJSONObject(i)
                                val name = obj.optString("name", "")
                                if (name.isNotEmpty()) {
                                    fileNames.add(name)
                                }
                            }
                            callback(fileNames, null)
                        } catch (e: Exception) {
                            Log.e(TAG, "[EXISTING THUMBNAIL MIGRATION] Error parsing file list", e)
                            callback(null, e.localizedMessage)
                        }
                    }
                }
            }
        })
    }

    fun fetchAllVideosWithAllColumns(
        context: Context,
        callback: (List<SupabaseVideo>?, String?) -> Unit
    ) {
        val creds = getCredentials(context)
        if (!creds.isValid()) {
            callback(null, "Credentials not configured")
            return
        }
        val supabaseUrl = creds.cleanBaseUrl
        val queryUrl = "$supabaseUrl/rest/v1/${creds.supabaseTable}?select=*"

        val request = Request.Builder()
            .url(queryUrl)
            .get()
            .addHeader("apikey", creds.supabaseAnonKey)
            .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e(TAG, "[EXISTING THUMBNAIL MIGRATION] Fetch all videos failed", e)
                callback(null, e.localizedMessage)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        Log.e(TAG, "[EXISTING THUMBNAIL MIGRATION] Fetch all videos rejected: ${resp.code}. Body: $body")
                        callback(null, "Server error: ${resp.code}")
                    } else {
                        val json = resp.body?.string() ?: "[]"
                        val list = SupabaseVideo.fromJson(json)
                        callback(list, null)
                    }
                }
            }
        })
    }

    fun updateVideoThumbnailUrlInDatabase(
        context: Context,
        videoId: Int,
        thumbnailUrl: String,
        callback: (Boolean) -> Unit
    ) {
        val creds = getCredentials(context)
        if (!creds.isValid()) {
            callback(false)
            return
        }
        val supabaseUrl = creds.cleanBaseUrl
        val updateUrl = "$supabaseUrl/rest/v1/${creds.supabaseTable}?id=eq.$videoId"

        val jsonBody = """
            {
              "thumbnail_url": "${escapeJson(thumbnailUrl)}"
            }
        """.trimIndent()

        val request = Request.Builder()
            .url(updateUrl)
            .patch(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
            .addHeader("apikey", creds.supabaseAnonKey)
            .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e(TAG, "[DATABASE UPDATE FAILURE] Failed to update video $videoId thumbnail_url", e)
                callback(false)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        Log.e(TAG, "[DATABASE UPDATE FAILURE] Update video $videoId rejected: ${resp.code}. Body: $body")
                        callback(false)
                    } else {
                        callback(true)
                    }
                }
            }
        })
    }

    private fun extractTimestampFromFilename(fileName: String): Long {
        val regex = Regex("""lms_(\d+)""")
        val match = regex.find(fileName)
        return match?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    }

    private fun extractTimestampFromVideoUrl(videoUrl: String): Long {
        val regex = Regex("""lms_(\d+)""")
        val match = regex.find(videoUrl)
        return match?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    }

    private fun parseUploadDateToTimestamp(uploadDateStr: String): Long {
        if (uploadDateStr.isBlank()) return 0L
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss.SSS",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.US)
                if (format.contains("Z")) {
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                }
                val date = sdf.parse(uploadDateStr)
                if (date != null) {
                    return date.time
                }
            } catch (e: Exception) {
                // ignore
            }
        }
        return 0L
    }

    fun repairMissingThumbnails(context: Context, onComplete: () -> Unit = {}) {
        val creds = getCredentials(context)
        if (!creds.isValid()) {
            onComplete()
            return
        }
        val supabaseUrl = creds.cleanBaseUrl
        
        Log.i(TAG, "[EXISTING THUMBNAIL MIGRATION] Starting automatic existing thumbnail migration/repair check...")

        listSupabaseStorageFiles(context, "videos") { fileNames, storageError ->
            if (storageError != null || fileNames == null) {
                Log.e(TAG, "[EXISTING THUMBNAIL MIGRATION] Aborting repair due to storage list error: $storageError")
                onComplete()
                return@listSupabaseStorageFiles
            }

            Log.i(TAG, "[EXISTING THUMBNAIL MIGRATION] Found ${fileNames.size} files in storage bucket 'videos'")

            val imageExtensions = listOf("jpg", "jpeg", "png", "webp", "gif")
            val thumbnailFiles = fileNames.filter { fileName ->
                val ext = fileName.substringAfterLast(".", "").lowercase(Locale.US)
                fileName.startsWith("lms_") && ext in imageExtensions
            }.sorted()

            Log.i(TAG, "[EXISTING THUMBNAIL MIGRATION] Filtered ${thumbnailFiles.size} thumbnail image files from storage: $thumbnailFiles")

            fetchAllVideosWithAllColumns(context) { videos, dbError ->
                if (dbError != null || videos == null) {
                    Log.e(TAG, "[EXISTING THUMBNAIL MIGRATION] Aborting repair due to database fetch error: $dbError")
                    onComplete()
                    return@fetchAllVideosWithAllColumns
                }

                Log.i(TAG, "[EXISTING THUMBNAIL MIGRATION] Found ${videos.size} total videos in database")

                val emptyThumbnailVideos = videos.filter { it.thumbnailUrl.isBlank() }
                Log.i(TAG, "[EXISTING THUMBNAIL MIGRATION] Found ${emptyThumbnailVideos.size} videos in database with EMPTY thumbnail_url")

                if (emptyThumbnailVideos.isEmpty() || thumbnailFiles.isEmpty()) {
                    Log.i(TAG, "[EXISTING THUMBNAIL MIGRATION] Nothing to repair. Empty videos: ${emptyThumbnailVideos.size}, Thumbnail files: ${thumbnailFiles.size}")
                    onComplete()
                    return@fetchAllVideosWithAllColumns
                }

                val parsedThumbnails = thumbnailFiles.map { fileName ->
                    val timestamp = extractTimestampFromFilename(fileName)
                    fileName to timestamp
                }.filter { it.second > 0L }.sortedBy { it.second }

                Log.i(TAG, "[EXISTING THUMBNAIL MIGRATION] Parsed ${parsedThumbnails.size} thumbnails with valid timestamps")

                var repairedCount = 0
                val videosToRepair = emptyThumbnailVideos.sortedBy { it.id }

                if (videosToRepair.isEmpty() || parsedThumbnails.isEmpty()) {
                    Log.i(TAG, "[EXISTING THUMBNAIL MIGRATION] No valid videos or parsed thumbnails to process")
                    onComplete()
                    return@fetchAllVideosWithAllColumns
                }

                val totalToAttempt = videosToRepair.size
                var completedAttempts = 0

                for (video in videosToRepair) {
                    val videoUrlTimestamp = extractTimestampFromVideoUrl(video.videoUrl)
                    val uploadDateTimestamp = parseUploadDateToTimestamp(video.uploadDate)
                    val videoRefTimestamp = if (videoUrlTimestamp > 0L) videoUrlTimestamp else uploadDateTimestamp

                    Log.i(TAG, "[EXISTING THUMBNAIL MIGRATION] Video ID: ${video.id}, Title: '${video.title}', VideoUrl: '${video.videoUrl}', UploadDate: '${video.uploadDate}', RefTimestamp: $videoRefTimestamp")

                    // Find the thumbnail file with the largest timestamp that is less than videoRefTimestamp
                    val matchedThumb = parsedThumbnails.filter { it.second < videoRefTimestamp }
                        .maxByOrNull { it.second }

                    if (matchedThumb != null) {
                        val thumbnailFile = matchedThumb.first
                        val publicUrl = "$supabaseUrl/storage/v1/object/public/videos/$thumbnailFile"

                        Log.i(TAG, "[EXISTING THUMBNAIL MIGRATION] Matched Video ID: ${video.id} with Thumbnail: '$thumbnailFile' (Timestamp: ${matchedThumb.second})")
                        Log.i(TAG, "[GENERATED PUBLIC URL] Video ID: ${video.id} -> '$publicUrl'")

                        updateVideoThumbnailUrlInDatabase(context, video.id, publicUrl) { success ->
                            if (success) {
                                Log.i(TAG, "[DATABASE UPDATE SUCCESS] Successfully repaired video ID: ${video.id} with thumbnail_url: '$publicUrl'")
                                repairedCount++
                            } else {
                                Log.e(TAG, "[DATABASE UPDATE FAILURE] Failed to update video ID: ${video.id} with thumbnail_url: '$publicUrl'")
                            }
                            completedAttempts++
                            if (completedAttempts == totalToAttempt) {
                                Log.i(TAG, "[EXISTING THUMBNAIL MIGRATION] Repair process finished! Successfully repaired $repairedCount out of $totalToAttempt videos.")
                                onComplete()
                            }
                        }
                    } else {
                        // Fallback: match by index
                        val fallbackIndex = videosToRepair.indexOf(video)
                        if (fallbackIndex in parsedThumbnails.indices) {
                            val thumbnailFile = parsedThumbnails[fallbackIndex].first
                            val publicUrl = "$supabaseUrl/storage/v1/object/public/videos/$thumbnailFile"

                            Log.w(TAG, "[EXISTING THUMBNAIL MIGRATION] No exact older thumbnail found for Video ID: ${video.id}. Using fallback index $fallbackIndex matched with: '$thumbnailFile'")
                            Log.i(TAG, "[GENERATED PUBLIC URL] (Fallback) Video ID: ${video.id} -> '$publicUrl'")

                            updateVideoThumbnailUrlInDatabase(context, video.id, publicUrl) { success ->
                                if (success) {
                                    Log.i(TAG, "[DATABASE UPDATE SUCCESS] Successfully repaired (fallback) video ID: ${video.id} with thumbnail_url: '$publicUrl'")
                                    repairedCount++
                                } else {
                                    Log.e(TAG, "[DATABASE UPDATE FAILURE] Failed to update (fallback) video ID: ${video.id} with thumbnail_url: '$publicUrl'")
                                }
                                completedAttempts++
                                if (completedAttempts == totalToAttempt) {
                                    Log.i(TAG, "[EXISTING THUMBNAIL MIGRATION] Repair process finished! Successfully repaired $repairedCount out of $totalToAttempt videos.")
                                    onComplete()
                                }
                            }
                        } else {
                            Log.e(TAG, "[EXISTING THUMBNAIL MIGRATION] Could not find any matching thumbnail (and fallback index out of bounds) for Video ID: ${video.id}")
                            completedAttempts++
                            if (completedAttempts == totalToAttempt) {
                                Log.i(TAG, "[EXISTING THUMBNAIL MIGRATION] Repair process finished! Successfully repaired $repairedCount out of $totalToAttempt videos.")
                                onComplete()
                            }
                        }
                    }
                }
            }
        }
    }

    fun fetchVideosForChapter(
        context: Context,
        classText: String,
        subject: String,
        chapter: String,
        callback: (List<SupabaseVideo>?, String?) -> Unit
    ) {
        // Trigger background migration check to repair any empty thumbnail URLs automatically
        repairMissingThumbnails(context)

        val creds = getCredentials(context)
        if (!creds.isValid()) {
            callback(null, "Credentials not configured")
            return
        }

        val supabaseUrl = creds.cleanBaseUrl
        val cleanClass = classText.trim().removePrefix("Class ").trim()
        val queryUrl = "$supabaseUrl/rest/v1/${creds.supabaseTable}".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("select", "*")
            ?.addQueryParameter("class", "ilike.%$cleanClass%")
            ?.addQueryParameter("subject", "ilike.${subject.trim()}")
            ?.addQueryParameter("chapter", "ilike.${chapter.trim()}")
            ?.addQueryParameter("order", "order_number.asc")
            ?.build()?.toString() ?: ""

        Log.d(TAG, "LMS Logcat - Selected Class: $classText")
        Log.d(TAG, "LMS Logcat - Selected Batch: $classText")
        Log.d(TAG, "LMS Logcat - Selected Subject: $subject")
        Log.d(TAG, "LMS Logcat - Selected Chapter: $chapter")
        Log.d(TAG, "LMS Logcat - Query sent to Supabase: $queryUrl")

        val request = Request.Builder()
            .url(queryUrl)
            .get()
            .addHeader("apikey", creds.supabaseAnonKey)
            .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e(TAG, "LMS Logcat - Fetch videos failed", e)
                callback(null, e.localizedMessage)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        Log.e(TAG, "LMS Logcat - Server error: Code ${resp.code}")
                        callback(null, "Server error (Code ${resp.code})")
                    } else {
                        val json = resp.body?.string() ?: "[]"
                        Log.d(TAG, "LMS Logcat - Complete JSON response from Supabase: $json")
                        val list = SupabaseVideo.fromJson(json)
                        Log.d(TAG, "LMS Logcat - Number of rows returned: ${list.size}")
                        
                        if (list.isEmpty()) {
                            callback(list, null)
                            return
                        }
                        
                        // Let's fetch lessons to map PDF URLs
                        val lessonsUrl = "$supabaseUrl/rest/v1/lessons" +
                                "?chapterName=eq.${Uri.encode(subject.trim())}" +
                                "&folder=eq.${Uri.encode(chapter.trim())}" +
                                "&select=title,videoUrl,pdfUrl"
                                
                        val lessonsRequest = Request.Builder()
                            .url(lessonsUrl)
                            .get()
                            .addHeader("apikey", creds.supabaseAnonKey)
                            .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
                            .build()
                            
                        client.newCall(lessonsRequest).enqueue(object : okhttp3.Callback {
                            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                                Log.e(TAG, "LMS Logcat - Fetch lessons for PDF mapping failed", e)
                                callback(list, null) // fallback
                            }
                            override fun onResponse(call: okhttp3.Call, lessonsResponse: okhttp3.Response) {
                                lessonsResponse.use { lResp ->
                                    if (!lResp.isSuccessful) {
                                        Log.e(TAG, "LMS Logcat - Lessons response failed: Code ${lResp.code}")
                                        callback(list, null) // fallback
                                        return
                                    }
                                    val lJson = lResp.body?.string() ?: "[]"
                                    
                                    val lessonsMapByUrl = mutableMapOf<String, String>()
                                    val lessonsMapByTitle = mutableMapOf<String, String>()
                                    try {
                                        val lessonsArray = JSONArray(lJson)
                                        for (j in 0 until lessonsArray.length()) {
                                            val lessonObj = lessonsArray.getJSONObject(j)
                                            val vUrl = lessonObj.optString("videoUrl", "").trim()
                                            val pUrl = lessonObj.optString("pdfUrl", "").trim()
                                            val lTitle = lessonObj.optString("title", "").trim()
                                            if (pUrl.isNotEmpty()) {
                                                if (vUrl.isNotEmpty()) {
                                                    lessonsMapByUrl[vUrl] = pUrl
                                                }
                                                if (lTitle.isNotEmpty()) {
                                                    lessonsMapByTitle[lTitle] = pUrl
                                                }
                                            }
                                        }
                                    } catch (ex: Exception) {
                                        Log.e(TAG, "Error parsing lessons for PDF mapping", ex)
                                    }
                                    
                                    val updatedList = list.map { video ->
                                        val matchedPdf = lessonsMapByUrl[video.videoUrl.trim()]
                                            ?: lessonsMapByTitle[video.title.trim()]
                                            ?: ""
                                        if (matchedPdf.isNotEmpty()) {
                                            video.copy(pdfUrl = matchedPdf)
                                        } else {
                                            video
                                        }
                                    }
                                    callback(updatedList, null)
                                }
                            }
                        })
                    }
                }
            }
        })
    }
    fun fetchVideos(context: Context, callback: (List<SupabaseVideo>?, String?) -> Unit) {
        // Trigger background migration check to repair any empty thumbnail URLs automatically
        repairMissingThumbnails(context)

        val creds = getCredentials(context)
        if (!creds.isValid()) {
            callback(null, "Credentials not configured")
            return
        }

        val supabaseUrl = creds.cleanBaseUrl
        val queryUrl = "$supabaseUrl/rest/v1/${creds.supabaseTable}?select=class,subject,chapter,upload_date&order=upload_date.desc"

        val request = Request.Builder()
            .url(queryUrl)
            .get()
            .addHeader("apikey", creds.supabaseAnonKey)
            .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e(TAG, "Supabase fetch videos failed network", e)
                callback(null, e.localizedMessage)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val errBody = resp.body?.string() ?: ""
                        Log.e(TAG, "Supabase fetch rejected: Code ${resp.code}. Body: $errBody")
                        callback(null, "Server error (Code ${resp.code})")
                    } else {
                        val json = resp.body?.string() ?: "[]"
                        val list = SupabaseVideo.fromJson(json)
                        callback(list, null)
                    }
                }
            }
        })
    }

    fun deleteVideo(context: Context, videoId: Int, callback: (Boolean, String?) -> Unit) {
        val creds = getCredentials(context)
        if (!creds.isValid()) {
            callback(false, "Credentials not configured")
            return
        }
        val supabaseUrl = creds.cleanBaseUrl
        val deleteUrl = "$supabaseUrl/rest/v1/${creds.supabaseTable}?id=eq.$videoId"
        
        val request = Request.Builder()
            .url(deleteUrl)
            .delete()
            .addHeader("apikey", creds.supabaseAnonKey)
            .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
            .build()
            
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                callback(false, e.localizedMessage)
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { resp ->
                    if (resp.isSuccessful) {
                        callback(true, null)
                    } else {
                        callback(false, "Failed: ${resp.code}")
                    }
                }
            }
        })
    }

    fun updateVideo(
        context: Context,
        id: Int,
        title: String,
        description: String,
        duration: String,
        orderNumber: Int,
        visibility: Boolean,
        callback: (Boolean, String?) -> Unit
    ) {
        val creds = getCredentials(context)
        if (!creds.isValid()) {
            callback(false, "Credentials not configured")
            return
        }
        val supabaseUrl = creds.cleanBaseUrl
        val updateUrl = "$supabaseUrl/rest/v1/${creds.supabaseTable}?id=eq.$id"
        
        val jsonBody = """
            {
              "title": "${escapeJson(title)}",
              "description": "${escapeJson(description)}",
              "duration": "${escapeJson(duration)}",
              "order_number": $orderNumber,
              "visibility": $visibility
            }
        """.trimIndent()
        
        val request = Request.Builder()
            .url(updateUrl)
            .patch(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
            .addHeader("apikey", creds.supabaseAnonKey)
            .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
            .addHeader("Content-Type", "application/json")
            .build()
            
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                callback(false, e.localizedMessage)
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { resp ->
                    if (resp.isSuccessful) {
                        callback(true, null)
                    } else {
                        callback(false, "Failed: ${resp.code}")
                    }
                }
            }
        })
    }

    fun renameSubject(
        context: Context,
        batch: String,
        oldSubject: String,
        newSubject: String,
        callback: (Boolean, String?) -> Unit
    ) {
        val creds = getCredentials(context)
        val supabaseUrl = creds.cleanBaseUrl
        
        val updateVideosUrl = "$supabaseUrl/rest/v1/${creds.supabaseTable}?class=eq.${escapeJson(batch)}&subject=eq.${escapeJson(oldSubject)}"
        val bodyVideos = """{"subject": "${escapeJson(newSubject)}"}"""
        
        val req1 = Request.Builder()
            .url(updateVideosUrl)
            .patch(bodyVideos.toRequestBody("application/json".toMediaTypeOrNull()))
            .addHeader("apikey", creds.supabaseAnonKey)
            .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
            .addHeader("Content-Type", "application/json")
            .build()
            
        client.newCall(req1).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                callback(false, e.localizedMessage)
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { resp ->
                    if (resp.isSuccessful) {
                        getOrCreateCourseIdForBatch(creds, batch, oldSubject) { courseId ->
                            val updateLessonsUrl = "$supabaseUrl/rest/v1/lessons?courseId=eq.$courseId&chapterName=eq.${escapeJson(oldSubject)}"
                            val bodyLessons = """{"chapterName": "${escapeJson(newSubject)}"}"""
                            val req2 = Request.Builder()
                                .url(updateLessonsUrl)
                                .patch(bodyLessons.toRequestBody("application/json".toMediaTypeOrNull()))
                                .addHeader("apikey", creds.supabaseAnonKey)
                                .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
                                .addHeader("Content-Type", "application/json")
                                .build()
                            client.newCall(req2).enqueue(object : okhttp3.Callback {
                                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                                    callback(true, null)
                                }
                                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                                    response.use { callback(true, null) }
                                }
                            })
                        }
                    } else {
                        callback(false, "Failed to update videos table: ${resp.code}")
                    }
                }
            }
        })
    }

    fun deleteSubject(
        context: Context,
        batch: String,
        subjectName: String,
        callback: (Boolean, String?) -> Unit
    ) {
        val creds = getCredentials(context)
        val supabaseUrl = creds.cleanBaseUrl
        
        val deleteVideosUrl = "$supabaseUrl/rest/v1/${creds.supabaseTable}?class=eq.${escapeJson(batch)}&subject=eq.${escapeJson(subjectName)}"
        val req1 = Request.Builder()
            .url(deleteVideosUrl)
            .delete()
            .addHeader("apikey", creds.supabaseAnonKey)
            .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
            .build()
            
        client.newCall(req1).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                callback(false, e.localizedMessage)
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { resp ->
                    if (resp.isSuccessful) {
                        getOrCreateCourseIdForBatch(creds, batch, subjectName) { courseId ->
                            val deleteLessonsUrl = "$supabaseUrl/rest/v1/lessons?courseId=eq.$courseId&chapterName=eq.${escapeJson(subjectName)}"
                            val req2 = Request.Builder()
                                .url(deleteLessonsUrl)
                                .delete()
                                .addHeader("apikey", creds.supabaseAnonKey)
                                .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
                                .build()
                            client.newCall(req2).enqueue(object : okhttp3.Callback {
                                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                                    callback(true, null)
                                }
                                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                                    response.use { callback(true, null) }
                                }
                            })
                        }
                    } else {
                        callback(false, "Failed: ${resp.code}")
                    }
                }
            }
        })
    }

    fun renameChapter(
        context: Context,
        batch: String,
        subject: String,
        oldChapter: String,
        newChapter: String,
        callback: (Boolean, String?) -> Unit
    ) {
        val creds = getCredentials(context)
        val supabaseUrl = creds.cleanBaseUrl
        
        val updateVideosUrl = "$supabaseUrl/rest/v1/${creds.supabaseTable}?class=eq.${escapeJson(batch)}&subject=eq.${escapeJson(subject)}&chapter=eq.${escapeJson(oldChapter)}"
        val bodyVideos = """{"chapter": "${escapeJson(newChapter)}"}"""
        
        val req1 = Request.Builder()
            .url(updateVideosUrl)
            .patch(bodyVideos.toRequestBody("application/json".toMediaTypeOrNull()))
            .addHeader("apikey", creds.supabaseAnonKey)
            .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
            .addHeader("Content-Type", "application/json")
            .build()
            
        client.newCall(req1).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                callback(false, e.localizedMessage)
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { resp ->
                    if (resp.isSuccessful) {
                        getOrCreateCourseIdForBatch(creds, batch, subject) { courseId ->
                            val updateLessonsUrl = "$supabaseUrl/rest/v1/lessons?courseId=eq.$courseId&chapterName=eq.${escapeJson(subject)}&folder=eq.${escapeJson(oldChapter)}"
                            val bodyLessons = """{"folder": "${escapeJson(newChapter)}"}"""
                            val req2 = Request.Builder()
                                .url(updateLessonsUrl)
                                .patch(bodyLessons.toRequestBody("application/json".toMediaTypeOrNull()))
                                .addHeader("apikey", creds.supabaseAnonKey)
                                .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
                                .addHeader("Content-Type", "application/json")
                                .build()
                            client.newCall(req2).enqueue(object : okhttp3.Callback {
                                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                                    callback(true, null)
                                }
                                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                                    response.use { callback(true, null) }
                                }
                            })
                        }
                    } else {
                        callback(false, "Failed: ${resp.code}")
                    }
                }
            }
        })
    }

    fun deleteChapter(
        context: Context,
        batch: String,
        subject: String,
        chapter: String,
        callback: (Boolean, String?) -> Unit
    ) {
        val creds = getCredentials(context)
        val supabaseUrl = creds.cleanBaseUrl
        
        val deleteVideosUrl = "$supabaseUrl/rest/v1/${creds.supabaseTable}?class=eq.${escapeJson(batch)}&subject=eq.${escapeJson(subject)}&chapter=eq.${escapeJson(chapter)}"
        val req1 = Request.Builder()
            .url(deleteVideosUrl)
            .delete()
            .addHeader("apikey", creds.supabaseAnonKey)
            .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
            .build()
            
        client.newCall(req1).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                callback(false, e.localizedMessage)
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { resp ->
                    if (resp.isSuccessful) {
                        getOrCreateCourseIdForBatch(creds, batch, subject) { courseId ->
                            val deleteLessonsUrl = "$supabaseUrl/rest/v1/lessons?courseId=eq.$courseId&chapterName=eq.${escapeJson(subject)}&folder=eq.${escapeJson(chapter)}"
                            val req2 = Request.Builder()
                                .url(deleteLessonsUrl)
                                .delete()
                                .addHeader("apikey", creds.supabaseAnonKey)
                                .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
                                .build()
                            client.newCall(req2).enqueue(object : okhttp3.Callback {
                                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                                    callback(true, null)
                                }
                                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                                    response.use { callback(true, null) }
                                }
                            })
                        }
                    } else {
                        callback(false, "Failed: ${resp.code}")
                    }
                }
            }
        })
    }

    fun publishContentUnified(
        context: Context,
        videoUri: Uri?,
        videoUrlInput: String,
        thumbnailUri: Uri?,
        pdf1Uri: Uri?,
        pdf2Uri: Uri?,
        title: String,
        classText: String,
        subject: String,
        chapter: String,
        description: String,
        duration: String,
        orderNumber: Int,
        visibility: Boolean,
        onProgress: (String) -> Unit,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val safeProgress = { msg: String ->
            mainHandler.post {
                try {
                    onProgress(msg)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in onProgress callback", e)
                }
            }
        }
        val safeSuccess = {
            mainHandler.post {
                try {
                    onSuccess()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in onSuccess callback", e)
                }
            }
        }
        val safeError = { err: String ->
            mainHandler.post {
                try {
                    onError(err)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in onError callback", e)
                }
            }
        }

        try {
            // 4. Validate fields
            if (classText.isBlank()) {
                safeError("Batch is a required field. Please select a batch!")
                return
            }
            if (subject.isBlank()) {
                safeError("Subject is a required field. Please select a subject!")
                return
            }
            if (chapter.isBlank()) {
                safeError("Chapter is a required field. Please select a chapter!")
                return
            }
            if (videoUri == null && videoUrlInput.isBlank()) {
                safeError("Video is a required field. Please pick a video file or enter a web video URL!")
                return
            }
            val isYouTube = com.example.ui.screens.isYouTubeUrl(videoUrlInput)
            if (thumbnailUri == null && !isYouTube) {
                safeError("Thumbnail is a required field. Please pick a thumbnail image file!")
                return
            }

            // 5. Check that all selected files exist before uploading
            val fileExists = { uri: Uri ->
                try {
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
                } catch (e: Exception) {
                    false
                }
            }

            if (videoUri != null && !fileExists(videoUri)) {
                safeError("Selected Video file cannot be accessed or does not exist.")
                return
            }
            if (thumbnailUri != null && !fileExists(thumbnailUri)) {
                safeError("Selected Thumbnail file cannot be accessed or does not exist.")
                return
            }
            if (pdf1Uri != null && !fileExists(pdf1Uri)) {
                safeError("Selected PDF 1 file cannot be accessed or does not exist.")
                return
            }
            if (pdf2Uri != null && !fileExists(pdf2Uri)) {
                safeError("Selected PDF 2 file cannot be accessed or does not exist.")
                return
            }

            val creds = getCredentials(context)
            if (!creds.isValid()) {
                safeError("Credentials not configured")
                return
            }

            var finalVideoUrl = videoUrlInput.trim()
            var finalThumbnailUrl = ""
            var pdf1Url = ""
            var pdf2Url = ""

            // Forward declarations of sequential steps
            var step1_UploadThumbnail: (() -> Unit)? = null
            var step2_UploadVideo: (() -> Unit)? = null
            var step3_UploadPdf1: (() -> Unit)? = null
            var step4_UploadPdf2: (() -> Unit)? = null
            var step5_SaveMetadata: (() -> Unit)? = null

            step5_SaveMetadata = {
                try {
                    safeProgress("Saving metadata to Supabase...")
                    val finalPdfField = when {
                        pdf1Url.isNotEmpty() && pdf2Url.isNotEmpty() -> "$pdf1Url|$pdf2Url"
                        pdf1Url.isNotEmpty() -> pdf1Url
                        pdf2Url.isNotEmpty() -> pdf2Url
                        else -> ""
                    }
                    
                    val supabaseUrl = creds.cleanBaseUrl
                    val insertUrl = "$supabaseUrl/rest/v1/${creds.supabaseTable}"

                    if (finalThumbnailUrl.isBlank() && com.example.ui.screens.isYouTubeUrl(finalVideoUrl)) {
                        finalThumbnailUrl = com.example.ui.screens.getYouTubeThumbnailUrl(finalVideoUrl)
                    }
                    
                    val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                    val uploadDate = isoFormat.format(Date())
                    
                    val jsonBody = """
                        {
                          "title": "${escapeJson(title)}",
                          "class": "${escapeJson(classText)}",
                          "subject": "${escapeJson(subject)}",
                          "chapter": "${escapeJson(chapter)}",
                          "description": "${escapeJson(description)}",
                          "video_url": "${escapeJson(finalVideoUrl)}",
                          "thumbnail_url": "${escapeJson(finalThumbnailUrl)}",
                          "upload_date": "$uploadDate",
                          "resource_type": "VIDEO",
                          "duration": "$duration",
                          "order_number": $orderNumber,
                          "visibility": $visibility
                        }
                    """.trimIndent()
                    
                    val request = Request.Builder()
                        .url(insertUrl)
                        .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
                        .addHeader("apikey", creds.supabaseAnonKey)
                        .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=representation")
                        .build()
                        
                    // Print complete HTTP POST request details
                    val reqUrl = request.url.toString()
                    val reqHeaders = request.headers.toString().trim()
                    Log.d(TAG, "LMS Logcat - --- HTTP POST Request ---")
                    Log.d(TAG, "LMS Logcat - Method: ${request.method}")
                    Log.d(TAG, "LMS Logcat - URL: $reqUrl")
                    Log.d(TAG, "LMS Logcat - Table Name: ${creds.supabaseTable}")
                    Log.d(TAG, "LMS Logcat - Headers:\n$reqHeaders")
                    Log.d(TAG, "LMS Logcat - JSON Body:\n$jsonBody")
                    Log.d(TAG, "LMS Logcat - Fields Sent: title, class, subject, chapter, description, video_url, thumbnail_url, upload_date, resource_type, duration, order_number, visibility" + if (finalPdfField.isNotEmpty()) ", pdf_url" else "")
                    
                    android.util.Log.i("R2SupabaseManager", "[DATABASE UPDATE] Preparing to save metadata via publishContentUnified. thumbnail_url value BEFORE saving: $finalThumbnailUrl")
                        
                    client.newCall(request).enqueue(object : okhttp3.Callback {
                        override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                            Log.e(TAG, "LMS Logcat - Saved files, but Supabase register failed network: ${e.localizedMessage}", e)
                            safeError("Saved files, but Supabase register failed: ${e.localizedMessage}")
                        }
                        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                            response.use { resp ->
                                try {
                                    if (!resp.isSuccessful) {
                                        val err = resp.body?.string() ?: ""
                                        Log.e(TAG, "LMS Logcat - --- HTTP ${resp.code} Response Body ---")
                                        Log.e(TAG, "LMS Logcat - Response: $err")
                                        
                                        val detailedError = """
                                            Metadata registration failed: Code ${resp.code}
                                            
                                            URL: $reqUrl
                                            Table: ${creds.supabaseTable}
                                            
                                            Fields Sent:
                                            - title: "$title"
                                            - class: "$classText"
                                            - subject: "$subject"
                                            - chapter: "$chapter"
                                            - description: "$description"
                                            - video_url: "$finalVideoUrl"
                                            - thumbnail_url: "$finalThumbnailUrl"
                                            - upload_date: "$uploadDate"
                                            - resource_type: "VIDEO"
                                            - duration: "$duration"
                                            - order_number: $orderNumber
                                            - visibility: $visibility
                                            ${if (finalPdfField.isNotEmpty()) "- pdf_url: \"$finalPdfField\"" else ""}
                                            
                                            Response Details:
                                            $err
                                        """.trimIndent()
                                        
                                        safeError(detailedError)
                                    } else {
                                        android.util.Log.i("R2SupabaseManager", "[DATABASE UPDATE SUCCESS] Metadata successfully saved to Supabase via publishContentUnified. thumbnail_url value AFTER saving: $finalThumbnailUrl")
                                        getOrCreateCourseIdForBatch(creds, classText, subject) { courseId ->
                                            try {
                                                createOrUpdateLessonInSupabase(
                                                    creds = creds,
                                                    courseId = courseId,
                                                    title = title,
                                                    subject = subject,
                                                    chapter = chapter,
                                                    videoUrl = finalVideoUrl,
                                                    pdfUrl = finalPdfField
                                                )
                                            } catch (ex: Exception) {
                                                Log.e(TAG, "Error registering course/lesson", ex)
                                            }
                                        }
                                        safeSuccess()
                                    }
                                } catch (ex: Exception) {
                                    safeError("Error handling database response: ${ex.localizedMessage}")
                                }
                            }
                        }
                    })
                } catch (e: Exception) {
                    safeError("Failed during Metadata Save: ${e.localizedMessage}")
                }
            }

            step4_UploadPdf2 = {
                try {
                    if (pdf2Uri != null) {
                        safeProgress("Uploading PDF 2 (Board Notes)...")
                        uploadFileOnlyToSupabase(context, creds, pdf2Uri, "videos", { }, { url ->
                            pdf2Url = url
                            step5_SaveMetadata?.invoke()
                        }, { err ->
                            safeError("PDF 2 upload failed: $err")
                        })
                    } else {
                        step5_SaveMetadata?.invoke()
                    }
                } catch (e: Exception) {
                    safeError("Failed during PDF 2 Upload: ${e.localizedMessage}")
                }
            }

            step3_UploadPdf1 = {
                try {
                    if (pdf1Uri != null) {
                        safeProgress("Uploading PDF 1 (Handwritten Notes)...")
                        uploadFileOnlyToSupabase(context, creds, pdf1Uri, "videos", { }, { url ->
                            pdf1Url = url
                            step4_UploadPdf2?.invoke()
                        }, { err ->
                            safeError("PDF 1 upload failed: $err")
                        })
                    } else {
                        step4_UploadPdf2?.invoke()
                    }
                } catch (e: Exception) {
                    safeError("Failed during PDF 1 Upload: ${e.localizedMessage}")
                }
            }

            step2_UploadVideo = {
                try {
                    if (videoUri != null) {
                        safeProgress("Uploading Lecture Video...")
                        uploadFileOnlyToCloudflareR2(context, creds, videoUri, { progress ->
                            safeProgress("Uploading Lecture Video (${(progress * 100).toInt()}%)...")
                        }, { url ->
                            finalVideoUrl = url
                            step3_UploadPdf1?.invoke()
                        }, { err ->
                            safeError("Video upload failed: $err")
                        })
                    } else {
                        if (finalVideoUrl.isEmpty()) {
                            safeError("Please select a video file or enter a Video Link!")
                        } else {
                            step3_UploadPdf1?.invoke()
                        }
                    }
                } catch (e: Exception) {
                    safeError("Failed during Video Upload: ${e.localizedMessage}")
                }
            }

            step1_UploadThumbnail = {
                try {
                    if (thumbnailUri != null) {
                        safeProgress("Uploading Video Thumbnail...")
                        uploadFileOnlyToSupabase(context, creds, thumbnailUri, "videos", { }, { url ->
                            android.util.Log.i("R2SupabaseManager", "[THUMBNAIL UPLOAD SUCCESS] Uploaded thumbnail to Supabase. Generated public URL: $url")
                            finalThumbnailUrl = url
                            step2_UploadVideo?.invoke()
                        }, { err ->
                            safeError("Thumbnail upload failed: $err")
                        })
                    } else {
                        step2_UploadVideo?.invoke()
                    }
                } catch (e: Exception) {
                    safeError("Failed during Thumbnail Upload: ${e.localizedMessage}")
                }
            }

            // Start the sequential workflow!
            step1_UploadThumbnail()

        } catch (e: Exception) {
            safeError("Unexpected upload initiation error: ${e.localizedMessage}")
        }
    }

    suspend fun uploadFile(
        context: Context,
        fileUri: Uri,
        bucketName: String = "videos"
    ): String {
        val provider = STORAGE_PROVIDER
        return if (provider == "CLOUDFLARE_R2") {
            val extension = getFileInfo(context, fileUri).first.substringAfterLast(".", "jpg")
            val name = "banner_${System.currentTimeMillis()}.$extension"
            "r2://$bucketName/$name"
        } else if (provider == "FIREBASE") {
            val extension = getFileInfo(context, fileUri).first.substringAfterLast(".", "jpg")
            val name = "banner_${System.currentTimeMillis()}.$extension"
            "gs://$bucketName/$name"
        } else {
            uploadFileToSupabaseStorage(context, fileUri, bucketName)
        }
    }

    suspend fun uploadFileToSupabaseStorage(
        context: Context,
        fileUri: Uri,
        bucketName: String = "videos",
        folderPath: String = ""
    ): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val creds = getCredentials(context)
        if (!creds.isValid()) {
            throw Exception("Supabase credentials are not configured!")
        }
        
        // Ensure bucket exists
        try {
            ensureBucketExists(creds, bucketName)
        } catch (e: Exception) {
            Log.e(TAG, "Bucket check/creation failed: ${e.message}")
        }

        val (originalName, size) = getFileInfo(context, fileUri)
        val mimeType = context.contentResolver.getType(fileUri) ?: "image/jpeg"
        
        Log.d(TAG, "=== BANNER/VIDEO UPLOAD DEBUG ===")
        Log.d(TAG, "Selected Image/Video: $fileUri")
        Log.d(TAG, "Bucket Name: $bucketName")
        Log.d(TAG, "Content Type: $mimeType")
        Log.d(TAG, "Upload Started, Size: $size bytes")

        val extension = when {
            mimeType.contains("png") -> "png"
            mimeType.contains("jpeg") || mimeType.contains("jpg") -> "jpg"
            mimeType.contains("webp") -> "webp"
            mimeType.contains("pdf") -> "pdf"
            mimeType.contains("mp4") -> "mp4"
            else -> originalName.substringAfterLast(".", "jpg")
        }
        
        val prefix = if (bucketName == "videos") "lms" else "banner"
        val baseName = "${prefix}_${System.currentTimeMillis()}.$extension"
        val uniqueName = if (folderPath.isNotEmpty()) "${folderPath.trimEnd('/')}/$baseName" else baseName

        val supabaseUrl = creds.cleanBaseUrl
        val publicUrl = "$supabaseUrl/storage/v1/object/public/$bucketName/$uniqueName"

        if (size > 6 * 1024 * 1024) { // Files > 6MB use TUS chunked upload
            Log.d(TAG, "File is large ($size bytes), using TUS resumable upload")
            val createUrl = "$supabaseUrl/storage/v1/upload/resumable"
            
            val bucketNameBase64 = android.util.Base64.encodeToString(bucketName.toByteArray(), android.util.Base64.NO_WRAP)
            val objectNameBase64 = android.util.Base64.encodeToString(uniqueName.toByteArray(), android.util.Base64.NO_WRAP)
            val contentTypeBase64 = android.util.Base64.encodeToString(mimeType.toByteArray(), android.util.Base64.NO_WRAP)
            
            val createRequest = Request.Builder()
                .url(createUrl)
                .post(ByteArray(0).toRequestBody(null))
                .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
                .addHeader("apikey", creds.supabaseAnonKey)
                .addHeader("Tus-Resumable", "1.0.0")
                .addHeader("Upload-Length", size.toString())
                .addHeader("Upload-Metadata", "bucketName $bucketNameBase64, objectName $objectNameBase64, contentType $contentTypeBase64")
                .build()

            var uploadUrl = ""
            client.newCall(createRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.e(TAG, "TUS Create Failed (Code ${response.code}): $errBody")
                    throw Exception("Supabase TUS create error (Code ${response.code}): $errBody")
                }
                uploadUrl = response.header("Location") ?: throw Exception("No Location header in TUS response")
                if (uploadUrl.startsWith("/")) {
                    val url = java.net.URL(supabaseUrl)
                    uploadUrl = "${url.protocol}://${url.host}${if (url.port != -1) ":" + url.port else ""}$uploadUrl"
                }
            }

            Log.d(TAG, "TUS Session Created: $uploadUrl")

            val chunkSize = 6L * 1024 * 1024 // 6MB chunk size per Supabase recommendation
            var offset = 0L

            while (offset < size) {
                val currentChunkSize = kotlin.math.min(chunkSize, size - offset)
                Log.d(TAG, "Uploading chunk: offset=$offset, size=$currentChunkSize")
                
                var success = false
                var retryCount = 0
                while (!success && retryCount < 3) {
                    try {
                        val chunkRequestBody = object : RequestBody() {
                            override fun contentType() = "application/offset+octet-stream".toMediaTypeOrNull()
                            override fun contentLength() = currentChunkSize
                            override fun writeTo(sink: okio.BufferedSink) {
                                val inputStream = context.contentResolver.openInputStream(fileUri)
                                    ?: throw java.io.IOException("Cannot open input stream for URI: $fileUri")
                                try {
                                    var skipped = 0L
                                    while (skipped < offset) {
                                        val s = inputStream.skip(offset - skipped)
                                        if (s == 0L) {
                                            if (inputStream.read() == -1) break
                                            skipped++
                                        } else {
                                            skipped += s
                                        }
                                    }
                                    val buffer = ByteArray(65536)
                                    var bytesToRead = currentChunkSize
                                    while (bytesToRead > 0) {
                                        val readLen = kotlin.math.min(buffer.size.toLong(), bytesToRead).toInt()
                                        val read = inputStream.read(buffer, 0, readLen)
                                        if (read == -1) break
                                        sink.write(buffer, 0, read)
                                        bytesToRead -= read
                                    }
                                } finally {
                                    try {
                                        inputStream.close()
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error closing stream", e)
                                    }
                                }
                            }
                        }

                        val patchRequest = Request.Builder()
                            .url(uploadUrl)
                            .patch(chunkRequestBody)
                            .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
                            .addHeader("apikey", creds.supabaseAnonKey)
                            .addHeader("Tus-Resumable", "1.0.0")
                            .addHeader("Upload-Offset", offset.toString())
                            .build()

                        client.newCall(patchRequest).execute().use { response ->
                            if (!response.isSuccessful) {
                                val errBody = response.body?.string() ?: ""
                                Log.e(TAG, "TUS Patch Failed (Code ${response.code}): $errBody")
                                throw Exception("TUS patch error (Code ${response.code}): $errBody")
                            }
                            val newOffsetStr = response.header("Upload-Offset")
                            if (newOffsetStr != null) {
                                offset = newOffsetStr.toLong()
                            } else {
                                offset += currentChunkSize
                            }
                            success = true
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Chunk upload failed (retry $retryCount): ${e.message}")
                        retryCount++
                        if (retryCount >= 3) throw e
                        kotlinx.coroutines.delay(2000)
                    }
                }
            }
            Log.d(TAG, "TUS Upload Success")
            return@withContext publicUrl

        } else {
            // Standard upload for smaller files
            val endpointUrl = "$supabaseUrl/storage/v1/object/$bucketName/$uniqueName"
            val requestBody = ProgressRequestBody(
                context = context,
                fileUri = fileUri,
                contentType = mimeType,
                contentLength = size,
                onProgress = { _, _ -> }
            )

            val request = Request.Builder()
                .url(endpointUrl)
                .post(requestBody)
                .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
                .addHeader("apikey", creds.supabaseAnonKey)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.e(TAG, "Upload Failed (Server Code ${response.code}): $errBody")
                    throw Exception("Supabase storage error (Code ${response.code}): $errBody")
                }
                Log.d(TAG, "Upload Success")
                return@withContext publicUrl
            }
        }
    }

    private suspend fun ensureBucketExists(creds: Credentials, bucketName: String) {
        val supabaseUrl = creds.cleanBaseUrl
        val checkUrl = "$supabaseUrl/storage/v1/bucket/$bucketName"
        
        val checkRequest = Request.Builder()
            .url(checkUrl)
            .get()
            .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
            .addHeader("apikey", creds.supabaseAnonKey)
            .build()
            
        val exists = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                client.newCall(checkRequest).execute().use { it.isSuccessful }
            } catch (e: Exception) {
                false
            }
        }
        
        if (!exists) {
            Log.d(TAG, "Bucket $bucketName not found, attempting to create...")
            val createUrl = "$supabaseUrl/storage/v1/bucket"
            val jsonBody = """{"id": "$bucketName", "name": "$bucketName", "public": true, "file_size_limit": 53687091200}"""
            val createRequest = Request.Builder()
                .url(createUrl)
                .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
                .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
                .addHeader("apikey", creds.supabaseAnonKey)
                .build()
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    client.newCall(createRequest).execute().use { resp ->
                        Log.d(TAG, "Bucket creation response: ${resp.code} ${resp.message}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create bucket $bucketName", e)
                }
            }
        } else {
            // Update existing bucket to ensure it has large size limit
            val updateUrl = "$supabaseUrl/storage/v1/bucket/$bucketName"
            val jsonBody = """{"public": true, "file_size_limit": 53687091200}"""
            val updateRequest = Request.Builder()
                .url(updateUrl)
                .put(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
                .addHeader("Authorization", "Bearer ${creds.supabaseAnonKey}")
                .addHeader("apikey", creds.supabaseAnonKey)
                .build()
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    client.newCall(updateRequest).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            Log.d(TAG, "Bucket update limit response: ${resp.code} ${resp.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update bucket limit $bucketName", e)
                }
            }
        }
    }
}

interface StorageManager {
    fun uploadVideo(
        context: Context,
        videoUri: Uri,
        pdfUri: Uri?,
        title: String,
        classText: String,
        subject: String,
        chapter: String,
        description: String,
        resourceType: String,
        duration: String,
        orderNumber: Int,
        visibility: Boolean,
        onProgress: (progress: Float) -> Unit,
        onSuccess: (videoUrl: String) -> Unit,
        onError: (error: String) -> Unit
    )
}

interface StorageProvider : StorageManager

class SupabaseStorageProvider : StorageProvider {
    override fun uploadVideo(
        context: Context,
        videoUri: Uri,
        pdfUri: Uri?,
        title: String,
        classText: String,
        subject: String,
        chapter: String,
        description: String,
        resourceType: String,
        duration: String,
        orderNumber: Int,
        visibility: Boolean,
        onProgress: (progress: Float) -> Unit,
        onSuccess: (videoUrl: String) -> Unit,
        onError: (error: String) -> Unit
    ) {
        R2SupabaseManager.uploadVideoToSupabaseInternal(
            context = context,
            videoUri = videoUri,
            pdfUri = pdfUri,
            title = title,
            classText = classText,
            subject = subject,
            chapter = chapter,
            description = description,
            resourceType = resourceType,
            duration = duration,
            orderNumber = orderNumber,
            visibility = visibility,
            onProgress = onProgress,
            onSuccess = onSuccess,
            onError = onError
        )
    }
}

class CloudflareR2StorageProvider : StorageProvider {
    override fun uploadVideo(
        context: Context,
        videoUri: Uri,
        pdfUri: Uri?,
        title: String,
        classText: String,
        subject: String,
        chapter: String,
        description: String,
        resourceType: String,
        duration: String,
        orderNumber: Int,
        visibility: Boolean,
        onProgress: (progress: Float) -> Unit,
        onSuccess: (videoUrl: String) -> Unit,
        onError: (error: String) -> Unit
    ) {
        val creds = R2SupabaseManager.getCredentials(context)
        if (!creds.isValid()) {
            onError("Cloudflare R2 and Supabase credentials are not fully configured! Please configure them in Settings.")
            return
        }

        // 1. Upload video only to Cloudflare R2
        R2SupabaseManager.uploadFileOnlyToCloudflareR2(
            context = context,
            creds = creds,
            fileUri = videoUri,
            onProgress = onProgress,
            onSuccess = { videoUrl ->
                // 2. Upload PDF to Supabase Storage (if selected)
                if (pdfUri != null) {
                    R2SupabaseManager.uploadFileOnlyToSupabase(
                        context = context,
                        creds = creds,
                        fileUri = pdfUri,
                        bucketName = "videos",
                        onProgress = {},
                        onSuccess = { pdfUrl ->
                            // 3. Save metadata to Supabase
                            R2SupabaseManager.saveMetadataToSupabase(
                                creds = creds,
                                videoUrl = videoUrl,
                                title = title,
                                classText = classText,
                                subject = subject,
                                chapter = chapter,
                                description = description,
                                resourceType = resourceType,
                                duration = duration,
                                orderNumber = orderNumber,
                                visibility = visibility,
                                pdfUrl = pdfUrl,
                                onSuccess = onSuccess,
                                onError = onError
                            )
                        },
                        onError = { err ->
                            onError("Video uploaded successfully to R2, but PDF upload to Supabase failed: $err")
                        }
                    )
                } else {
                    // No PDF, save metadata directly
                    R2SupabaseManager.saveMetadataToSupabase(
                        creds = creds,
                        videoUrl = videoUrl,
                        title = title,
                        classText = classText,
                        subject = subject,
                        chapter = chapter,
                        description = description,
                        resourceType = resourceType,
                        duration = duration,
                        orderNumber = orderNumber,
                        visibility = visibility,
                        pdfUrl = null,
                        onSuccess = onSuccess,
                        onError = onError
                    )
                }
            },
            onError = onError
        )
    }
}

