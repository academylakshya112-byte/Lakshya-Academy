import re

with open('app/src/main/java/com/example/api/R2SupabaseManager.kt', 'r') as f:
    content = f.read()

# Add pdfUrl param to saveMetadataToSupabase
content = re.sub(
    r'private fun saveMetadataToSupabase\([\s\S]*?visibility: Boolean = true,\n\s*onSuccess: \(videoUrl: String\) -> Unit',
    r'private fun saveMetadataToSupabase(\n        creds: Credentials,\n        videoUrl: String,\n        title: String,\n        classText: String,\n        subject: String,\n        chapter: String,\n        description: String,\n        resourceType: String = "VIDEO",\n        duration: String = "",\n        orderNumber: Int = 0,\n        visibility: Boolean = true,\n        pdfUrl: String? = null,\n        onSuccess: (videoUrl: String) -> Unit',
    content
)

# Add pdf_url to json payload
json_body_replacement = r'''val jsonBody = """
            {
              "title": "${escapeJson(title)}",
              "class": "${escapeJson(classText)}",
              "subject": "${escapeJson(subject)}",
              "chapter": "${escapeJson(chapter)}",
              "description": "${escapeJson(description)}",
              "video_url": "${escapeJson(videoUrl)}",
              "thumbnail_url": "",
              "upload_date": "$uploadDate",
              "resource_type": "$resourceType",
              "duration": "$duration",
              "order_number": $orderNumber,
              "visibility": $visibility${if (pdfUrl != null) ",\n              \"pdf_url\": \"${escapeJson(pdfUrl)}\"" else ""}
            }
        """.trimIndent()'''
content = re.sub(
    r'val jsonBody = """[\s\S]*?"visibility": \$visibility\n\s*}[\s\S]*?""".trimIndent\(\)',
    json_body_replacement,
    content
)

# Add pdfUri to uploadVideoToR2
content = re.sub(
    r'fun uploadVideoToR2\([\s\S]*?visibility: Boolean = true,\n\s*onProgress',
    r'fun uploadVideoToR2(\n        context: Context,\n        videoUri: Uri,\n        pdfUri: Uri? = null,\n        title: String,\n        classText: String,\n        subject: String,\n        chapter: String,\n        description: String,\n        resourceType: String = "VIDEO",\n        duration: String = "",\n        orderNumber: Int = 0,\n        visibility: Boolean = true,\n        onProgress',
    content
)

# Modify upload logic to also upload PDF if present
save_metadata_call = r'''
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
'''
content = re.sub(
    r'// Save metadata to Supabase[\s\S]*?onError = onError\n\s*\)',
    save_metadata_call,
    content
)

with open('app/src/main/java/com/example/api/R2SupabaseManager.kt', 'w') as f:
    f.write(content)
