import re

with open('app/src/main/java/com/example/api/R2SupabaseManager.kt', 'r') as f:
    content = f.read()

upload_file_func = r'''
    private fun uploadFileOnlyToR2(
        context: Context,
        creds: Credentials,
        fileUri: Uri,
        onProgress: (Float) -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val (originalName, size) = getFileInfo(context, fileUri)
        val extension = originalName.substringAfterLast(".", "pdf")
        val uniqueName = "lms_${System.currentTimeMillis()}.$extension"

        val inputStream = context.contentResolver.openInputStream(fileUri)
        if (inputStream == null) {
            onError("Cannot read selected file: Input stream is null")
            return
        }

        val host = "${creds.r2AccountId}.r2.cloudflarestorage.com"
        val canonicalUri = "/${creds.r2BucketName}/$uniqueName"
        val endpointUrl = "https://$host$canonicalUri"

        val authHeaders = R2Signer.getSignatureHeaders(
            method = "PUT",
            host = host,
            canonicalUri = canonicalUri,
            accessKeyId = creds.r2AccessKeyId,
            secretAccessKey = creds.r2SecretAccessKey
        )

        val finalContentType = if (extension.lowercase(java.util.Locale.US) == "pdf") "application/pdf" else "video/$extension"
        val requestBody = ProgressRequestBody(
            inputStream = inputStream,
            contentType = finalContentType,
            contentLength = size,
            onProgress = { bytesWritten, totalBytes ->
                val progress = if (totalBytes > 0) bytesWritten.toFloat() / totalBytes else 0f
                onProgress(progress)
            }
        )

        val requestBuilder = Request.Builder().url(endpointUrl).put(requestBody)
        authHeaders.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
        val request = requestBuilder.build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                onError("Upload network error: ${e.localizedMessage}")
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        onError("Server rejected upload (Code ${resp.code})")
                        return
                    }
                    val playbackUrl = if (creds.r2PublicUrl.isNotBlank()) {
                        val base = creds.r2PublicUrl.removeSuffix("/")
                        "$base/$uniqueName"
                    } else endpointUrl
                    onSuccess(playbackUrl)
                }
            }
        })
    }

    private fun saveMetadataToSupabase(
'''
content = content.replace('    private fun saveMetadataToSupabase(', upload_file_func)

with open('app/src/main/java/com/example/api/R2SupabaseManager.kt', 'w') as f:
    f.write(content)
