package com.example.api

import android.content.Context
import android.net.Uri
import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONArray
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

object BackblazeB2Manager {
    private const val TAG = "BackblazeB2Manager"
    private const val PREFS_NAME = "b2_storage_config"

    private fun getDefaultValue(value: String, placeholder: String): String {
        val trimmed = value.trim()
        return if (trimmed.isBlank() || trimmed == placeholder || trimmed.startsWith("YOUR_")) {
            ""
        } else {
            trimmed
        }
    }

    data class Credentials(
        val b2BucketName: String = "",
        val b2AccessKeyId: String = "",
        val b2SecretAccessKey: String = "",
        val b2Endpoint: String = "" // e.g. s3.us-west-004.backblazeb2.com
    ) {
        fun isValid(): Boolean {
            return b2BucketName.isNotBlank() &&
                   b2AccessKeyId.isNotBlank() &&
                   b2SecretAccessKey.isNotBlank() &&
                   b2Endpoint.isNotBlank()
        }

        val region: String
            get() {
                var cleaned = b2Endpoint.trim().lowercase(Locale.US)
                if (cleaned.startsWith("https://")) {
                    cleaned = cleaned.substring(8)
                } else if (cleaned.startsWith("http://")) {
                    cleaned = cleaned.substring(7)
                }
                if (cleaned.startsWith("s3.")) {
                    val parts = cleaned.split(".")
                    if (parts.size >= 2) {
                        return parts[1]
                    }
                }
                return "us-west-004" // Fallback standard
            }

        val host: String
            get() {
                var h = b2Endpoint.trim()
                if (h.startsWith("https://", ignoreCase = true)) {
                    h = h.substring(8)
                } else if (h.startsWith("http://", ignoreCase = true)) {
                    h = h.substring(7)
                }
                if (h.endsWith("/")) {
                    h = h.substring(0, h.length - 1)
                }
                return h
            }
    }

    fun getCredentials(context: Context): Credentials {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedBucket = prefs.getString("b2_bucket_name", "") ?: ""
        val savedKeyId = prefs.getString("b2_access_key_id", "") ?: ""
        val savedSecret = prefs.getString("b2_secret_access_key", "") ?: ""
        val savedEndpoint = prefs.getString("b2_endpoint", "") ?: ""

        val defaultBucket = getDefaultValue(com.example.BuildConfig.B2_BUCKET_NAME, "YOUR_B2_BUCKET_NAME").ifBlank {
            getDefaultValue(com.example.BuildConfig.R2_BUCKET_NAME, "YOUR_R2_BUCKET_NAME")
        }
        val defaultKeyId = getDefaultValue(com.example.BuildConfig.B2_APPLICATION_KEY_ID, "YOUR_B2_APPLICATION_KEY_ID").ifBlank {
            getDefaultValue(com.example.BuildConfig.R2_ACCESS_KEY_ID, "YOUR_R2_ACCESS_KEY_ID")
        }
        val defaultSecret = getDefaultValue(com.example.BuildConfig.B2_APPLICATION_KEY, "YOUR_B2_APPLICATION_KEY").ifBlank {
            getDefaultValue(com.example.BuildConfig.R2_SECRET_ACCESS_KEY, "YOUR_R2_SECRET_ACCESS_KEY")
        }
        val defaultEndpoint = getDefaultValue(com.example.BuildConfig.B2_ENDPOINT, "s3.us-west-004.backblazeb2.com").ifBlank {
            "s3.us-west-004.backblazeb2.com"
        }

        return Credentials(
            b2BucketName = savedBucket.ifBlank { defaultBucket },
            b2AccessKeyId = savedKeyId.ifBlank { defaultKeyId },
            b2SecretAccessKey = savedSecret.ifBlank { defaultSecret },
            b2Endpoint = savedEndpoint.ifBlank { defaultEndpoint }
        )
    }

    fun saveCredentials(context: Context, creds: Credentials) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString("b2_bucket_name", creds.b2BucketName.trim())
            .putString("b2_access_key_id", creds.b2AccessKeyId.trim())
            .putString("b2_secret_access_key", creds.b2SecretAccessKey.trim())
            .putString("b2_endpoint", creds.b2Endpoint.trim())
            .apply()
    }

    data class VerificationReport(
        val connectionSuccess: Boolean,
        val bucketAccessSuccess: Boolean,
        val readSuccess: Boolean,
        val writeSuccess: Boolean,
        val deleteSuccess: Boolean,
        val errorMessage: String? = null
    ) {
        fun isFullySuccessful(): Boolean {
            return connectionSuccess && bucketAccessSuccess && readSuccess && writeSuccess && deleteSuccess
        }
    }

    fun verifyB2Connection(creds: Credentials): VerificationReport {
        val host = creds.host
        val region = creds.region
        val bucket = creds.b2BucketName
        val keyId = creds.b2AccessKeyId
        val secret = creds.b2SecretAccessKey

        var connectionSuccess = false
        var bucketAccessSuccess = false
        var readSuccess = false
        var writeSuccess = false
        var deleteSuccess = false
        var finalError: String? = null

        val testClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        try {
            // 1. Connection & Bucket Access & Read Check via Listing Bucket
            val listUrl = "https://$host/$bucket/?max-keys=1"
            val listCanonicalUri = "/$bucket/"
            val listHeaders = B2Signer.getSignatureHeaders(
                method = "GET",
                host = host,
                canonicalUri = listCanonicalUri,
                queryParams = mapOf("max-keys" to "1"),
                accessKeyId = keyId,
                secretAccessKey = secret,
                region = region
            )

            val listRequest = Request.Builder()
                .url(listUrl)
                .get()
                .apply {
                    listHeaders.forEach { (k, v) -> addHeader(k, v) }
                }
                .build()

            testClient.newCall(listRequest).execute().use { response ->
                if (response.isSuccessful) {
                    connectionSuccess = true
                    bucketAccessSuccess = true
                    readSuccess = true
                } else {
                    val err = response.body?.string() ?: ""
                    throw Exception("Bucket access / read check failed (Code ${response.code}): $err")
                }
            }

            // 2. Write Permission Check (Upload temporary object)
            val tempKey = "b2_verify_temp_test_${System.currentTimeMillis()}.txt"
            val putUrl = "https://$host/$bucket/$tempKey"
            val putCanonicalUri = "/$bucket/$tempKey"
            val testContent = "Backblaze B2 verification test object"
            val putBody = testContent.toRequestBody("text/plain".toMediaTypeOrNull())

            val putHeaders = B2Signer.getSignatureHeaders(
                method = "PUT",
                host = host,
                canonicalUri = putCanonicalUri,
                queryParams = emptyMap(),
                accessKeyId = keyId,
                secretAccessKey = secret,
                region = region
            )

            val putRequest = Request.Builder()
                .url(putUrl)
                .put(putBody)
                .apply {
                    putHeaders.forEach { (k, v) -> addHeader(k, v) }
                }
                .build()

            testClient.newCall(putRequest).execute().use { response ->
                if (response.isSuccessful) {
                    writeSuccess = true
                } else {
                    val err = response.body?.string() ?: ""
                    throw Exception("Write verification failed (Code ${response.code}): $err")
                }
            }

            // 3. Delete Permission Check (Delete temporary object)
            val deleteUrl = "https://$host/$bucket/$tempKey"
            val deleteCanonicalUri = "/$bucket/$tempKey"

            val deleteHeaders = B2Signer.getSignatureHeaders(
                method = "DELETE",
                host = host,
                canonicalUri = deleteCanonicalUri,
                queryParams = emptyMap(),
                accessKeyId = keyId,
                secretAccessKey = secret,
                region = region
            )

            val deleteRequest = Request.Builder()
                .url(deleteUrl)
                .delete()
                .apply {
                    deleteHeaders.forEach { (k, v) -> addHeader(k, v) }
                }
                .build()

            testClient.newCall(deleteRequest).execute().use { response ->
                if (response.isSuccessful) {
                    deleteSuccess = true
                } else {
                    val err = response.body?.string() ?: ""
                    throw Exception("Delete verification failed (Code ${response.code}): $err")
                }
            }

        } catch (e: Exception) {
            finalError = e.message ?: e.toString()
            Log.e(TAG, "B2 verification failed: $finalError", e)
        }

        return VerificationReport(
            connectionSuccess = connectionSuccess,
            bucketAccessSuccess = bucketAccessSuccess,
            readSuccess = readSuccess,
            writeSuccess = writeSuccess,
            deleteSuccess = deleteSuccess,
            errorMessage = finalError
        )
    }

    // Custom HTTP client for uploading heavy files
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // --------------------------------------------------------
    // S3 Signature V4 Signer Helpers
    // --------------------------------------------------------
    object B2Signer {
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
            region: String,
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

        // Generates an S3 presigned GET URL for private bucket downloads
        fun getPresignedUrl(
            host: String,
            bucketName: String,
            objectKey: String,
            accessKeyId: String,
            secretAccessKey: String,
            region: String,
            expiresInSeconds: Long = 7200
        ): String {
            val isoFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val dateStampFormat = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val now = Date()
            val amzDate = isoFormat.format(now)
            val dateStamp = dateStampFormat.format(now)

            val credentialScope = "$dateStamp/$region/s3/aws4_request"
            val canonicalUri = "/$bucketName/$objectKey"

            val algo = "AWS4-HMAC-SHA256"
            val signedHeaders = "host"

            val fullCredential = "$accessKeyId/$credentialScope"
            val encodedCredential = java.net.URLEncoder.encode(fullCredential, "UTF-8")
                .replace("+", "%20").replace("*", "%2A").replace("%7E", "~")

            val canonicalQueryString = "X-Amz-Algorithm=$algo" +
                    "&X-Amz-Credential=$encodedCredential" +
                    "&X-Amz-Date=$amzDate" +
                    "&X-Amz-Expires=$expiresInSeconds" +
                    "&X-Amz-SignedHeaders=$signedHeaders"

            val canonicalHeaders = "host:$host\n"
            val canonicalRequest = "GET\n$canonicalUri\n$canonicalQueryString\n$canonicalHeaders\n$signedHeaders\nUNSIGNED-PAYLOAD"
            val canonicalRequestHash = sha256Hex(canonicalRequest)

            val stringToSign = "AWS4-HMAC-SHA256\n$amzDate\n$credentialScope\n$canonicalRequestHash"

            val kDate = hmacSHA256(dateStamp, "AWS4$secretAccessKey".toByteArray(Charsets.UTF_8))
            val kRegion = hmacSHA256(region, kDate)
            val kService = hmacSHA256("s3", kRegion)
            val kSigning = hmacSHA256("aws4_request", kService)

            val signature = bytesToHex(hmacSHA256(stringToSign, kSigning))

            return "https://$host$canonicalUri?$canonicalQueryString&X-Amz-Signature=$signature"
        }
    }

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
            val buffer = ByteArray(65536)
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

    fun getPresignedUrl(
        host: String,
        bucketName: String,
        objectKey: String,
        accessKeyId: String,
        secretAccessKey: String,
        region: String,
        expiresInSeconds: Long = 7200
    ): String {
        return B2Signer.getPresignedUrl(host, bucketName, objectKey, accessKeyId, secretAccessKey, region, expiresInSeconds)
    }

    fun uploadFileOnlyToBackblazeB2(
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
                if (!creds.isValid()) {
                    throw Exception("Backblaze B2 credentials are not fully configured!")
                }

                val (originalName, size) = R2SupabaseManager.getFileInfo(context, fileUri)
                val extension = originalName.substringAfterLast(".", "mp4")
                val uniqueName = "lms_${System.currentTimeMillis()}.$extension"

                val host = creds.host
                val canonicalUri = "/${creds.b2BucketName}/$uniqueName"

                // Save URL with schema prefix "b2://" for unified player resolving
                val b2StorageUrl = "b2://${creds.b2BucketName}/$uniqueName"

                Log.d(TAG, "Starting Backblaze B2 Upload for $originalName ($size bytes)")
                Log.d(TAG, "Unique object key: $uniqueName")
                Log.d(TAG, "B2 host: $host")
                Log.d(TAG, "Saved B2 URL scheme: $b2StorageUrl")

                val tenMB = 10L * 1024 * 1024
                if (size <= tenMB) {
                    Log.d(TAG, "File size is <= 10MB. S3 PUT upload.")
                    val uploadUrl = "https://$host/${creds.b2BucketName}/$uniqueName"
                    val sigHeaders = B2Signer.getSignatureHeaders(
                        method = "PUT",
                        host = host,
                        canonicalUri = canonicalUri,
                        queryParams = emptyMap(),
                        accessKeyId = creds.b2AccessKeyId,
                        secretAccessKey = creds.b2SecretAccessKey,
                        region = creds.region
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
                            throw Exception("Backblaze B2 upload error (Code ${response.code}): $errBody")
                        }
                        Log.d(TAG, "Backblaze B2 single PUT upload succeeded!")
                        safeSuccess(b2StorageUrl)
                    }
                } else {
                    Log.d(TAG, "File size is > 10MB. S3 Multipart Upload.")

                    // Step 1: Initiate Multipart Upload
                    val initUrl = "https://$host/${creds.b2BucketName}/$uniqueName?uploads"
                    val initSigHeaders = B2Signer.getSignatureHeaders(
                        method = "POST",
                        host = host,
                        canonicalUri = canonicalUri,
                        queryParams = mapOf("uploads" to ""),
                        accessKeyId = creds.b2AccessKeyId,
                        secretAccessKey = creds.b2SecretAccessKey,
                        region = creds.region
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
                            throw Exception("B2 Multipart Initiation failed (Code ${response.code}): $responseBody")
                        }
                        uploadId = "<UploadId>(.*?)</UploadId>".toRegex().find(responseBody)?.groupValues?.get(1) ?: ""
                        if (uploadId.isBlank()) {
                            throw Exception("Failed to parse UploadId from B2 response: $responseBody")
                        }
                    }

                    Log.d(TAG, "B2 Multipart Upload initiated. ID: $uploadId")

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
                                Log.d(TAG, "Uploading Part $partNumber / $totalParts (Offset: $partOffset, Size: $partSize)")
                                val partUrl = "https://$host/${creds.b2BucketName}/$uniqueName?uploadId=$uploadId&partNumber=$partNumber"
                                val partSigHeaders = B2Signer.getSignatureHeaders(
                                    method = "PUT",
                                    host = host,
                                    canonicalUri = canonicalUri,
                                    queryParams = mapOf("uploadId" to uploadId, "partNumber" to partNumber.toString()),
                                    accessKeyId = creds.b2AccessKeyId,
                                    secretAccessKey = creds.b2SecretAccessKey,
                                    region = creds.region
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
                                        ?: throw Exception("Part $partNumber missing ETag header")
                                    val cleanEtag = if (etag.startsWith("\"") && etag.endsWith("\"")) etag else "\"$etag\""
                                    synchronized(partEtags) {
                                        partEtags.add(Pair(partNumber, cleanEtag))
                                    }
                                    bytesUploaded += partSize
                                    safeProgress(bytesUploaded.toFloat() / size)
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
                            throw lastError ?: Exception("Failed to upload part $partNumber after Retries")
                        }
                    }

                    // Step 3: Complete Multipart Upload
                    Log.d(TAG, "All parts uploaded. Completing B2 Multipart Upload...")
                    partEtags.sortBy { it.first }

                    val completeXml = StringBuilder("<CompleteMultipartUpload>")
                    partEtags.forEach { (partNum, etag) ->
                        completeXml.append("<Part><PartNumber>$partNum</PartNumber><ETag>$etag</ETag></Part>")
                    }
                    completeXml.append("</CompleteMultipartUpload>")

                    val completeUrl = "https://$host/${creds.b2BucketName}/$uniqueName?uploadId=$uploadId"
                    val completeSigHeaders = B2Signer.getSignatureHeaders(
                        method = "POST",
                        host = host,
                        canonicalUri = canonicalUri,
                        queryParams = mapOf("uploadId" to uploadId),
                        accessKeyId = creds.b2AccessKeyId,
                        secretAccessKey = creds.b2SecretAccessKey,
                        region = creds.region
                    )

                    val completeBody = completeXml.toString().toRequestBody("application/xml".toMediaTypeOrNull())
                    val completeRequestBuilder = Request.Builder()
                        .url(completeUrl)
                        .post(completeBody)

                    completeSigHeaders.forEach { (k, v) ->
                        completeRequestBuilder.addHeader(k, v)
                    }

                    client.newCall(completeRequestBuilder.build()).execute().use { completeResponse ->
                        val completeResponseBody = completeResponse.body?.string() ?: ""
                        if (!completeResponse.isSuccessful) {
                            throw Exception("B2 Complete Multipart failed (Code ${completeResponse.code}): $completeResponseBody")
                        }
                        Log.d(TAG, "Backblaze B2 Multipart Upload completed successfully!")
                        safeSuccess(b2StorageUrl)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Backblaze B2 upload failed", e)
                safeError(e.localizedMessage ?: "Unknown error")
            }
        }
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
        val r2Creds = R2SupabaseManager.getCredentials(context)
        val b2Creds = getCredentials(context)

        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val safeProgress = { msg: String -> mainHandler.post { onProgress(msg) } }
        val safeSuccess = { mainHandler.post { onSuccess() } }
        val safeError = { err: String -> mainHandler.post { onError(err) } }

        try {
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

            if (!r2Creds.isValid()) {
                safeError("Supabase credentials are not configured!")
                return
            }
            if (videoUri != null && !b2Creds.isValid()) {
                safeError("Backblaze B2 credentials are not configured!")
                return
            }

            var finalVideoUrl = videoUrlInput.trim()
            var finalThumbnailUrl = ""
            var pdf1Url = ""
            var pdf2Url = ""

            var step1_UploadThumbnail: (() -> Unit)? = null
            var step2_UploadVideo: (() -> Unit)? = null
            var step3_UploadPdf1: (() -> Unit)? = null
            var step4_UploadPdf2: (() -> Unit)? = null
            var step5_SaveMetadata: (() -> Unit)? = null

            step5_SaveMetadata = {
                try {
                    safeProgress("Saving metadata to Supabase...")
                    if (finalThumbnailUrl.isBlank() && com.example.ui.screens.isYouTubeUrl(finalVideoUrl)) {
                        finalThumbnailUrl = com.example.ui.screens.getYouTubeThumbnailUrl(finalVideoUrl)
                    }
                    val finalPdfField = when {
                        pdf1Url.isNotEmpty() && pdf2Url.isNotEmpty() -> "$pdf1Url|$pdf2Url"
                        pdf1Url.isNotEmpty() -> pdf1Url
                        pdf2Url.isNotEmpty() -> pdf2Url
                        else -> ""
                    }

                    R2SupabaseManager.saveMetadataToSupabase(
                        creds = r2Creds,
                        videoUrl = finalVideoUrl,
                        title = title,
                        classText = classText,
                        subject = subject,
                        chapter = chapter,
                        description = description,
                        resourceType = "VIDEO",
                        duration = duration,
                        orderNumber = orderNumber,
                        visibility = visibility,
                        pdfUrl = finalPdfField.ifEmpty { null },
                        thumbnailUrl = finalThumbnailUrl,
                        onSuccess = {
                            safeSuccess()
                        },
                        onError = { err ->
                            safeError(err)
                        }
                    )
                } catch (e: Exception) {
                    safeError("Failed during Metadata Save: ${e.localizedMessage}")
                }
            }

            step4_UploadPdf2 = {
                try {
                    if (pdf2Uri != null) {
                        safeProgress("Uploading PDF 2 (Board Notes)...")
                        R2SupabaseManager.uploadFileOnlyToSupabase(context, r2Creds, pdf2Uri, "videos", { }, { url ->
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
                        R2SupabaseManager.uploadFileOnlyToSupabase(context, r2Creds, pdf1Uri, "videos", { }, { url ->
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
                        safeProgress("Uploading Lecture Video to Backblaze B2...")
                        uploadFileOnlyToBackblazeB2(context, b2Creds, videoUri, { progress ->
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
                        R2SupabaseManager.uploadFileOnlyToSupabase(context, r2Creds, thumbnailUri, "videos", { }, { url ->
                            android.util.Log.i("BackblazeB2Manager", "[THUMBNAIL UPLOAD SUCCESS] Uploaded thumbnail to Supabase. Generated public URL: $url")
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

            step1_UploadThumbnail()
        } catch (e: Exception) {
            safeError("Unexpected upload initiation error: ${e.localizedMessage}")
        }
    }
}

class BackblazeB2StorageProvider : StorageProvider {
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
        val b2Creds = BackblazeB2Manager.getCredentials(context)
        val r2Creds = R2SupabaseManager.getCredentials(context)

        if (!b2Creds.isValid()) {
            onError("Backblaze B2 credentials are not configured! Please configure them in Settings.")
            return
        }
        if (!r2Creds.isValid()) {
            onError("Supabase credentials are not configured! Please configure them in Settings.")
            return
        }

        BackblazeB2Manager.uploadFileOnlyToBackblazeB2(
            context = context,
            creds = b2Creds,
            fileUri = videoUri,
            onProgress = onProgress,
            onSuccess = { videoUrl ->
                if (pdfUri != null) {
                    R2SupabaseManager.uploadFileOnlyToSupabase(
                        context = context,
                        creds = r2Creds,
                        fileUri = pdfUri,
                        bucketName = "videos",
                        onProgress = {},
                        onSuccess = { pdfUrl ->
                            R2SupabaseManager.saveMetadataToSupabase(
                                creds = r2Creds,
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
                            onError("Video uploaded successfully to B2, but PDF upload to Supabase failed: $err")
                        }
                    )
                } else {
                    R2SupabaseManager.saveMetadataToSupabase(
                        creds = r2Creds,
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
