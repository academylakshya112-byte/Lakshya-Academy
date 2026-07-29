import re

with open('app/src/main/java/com/example/api/R2SupabaseManager.kt', 'r') as f:
    content = f.read()

bad_block = r'''        val jsonBody = """
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
              "visibility": $visibility" + (if (pdfUrl != null) ",\n              \"pdf_url\": \"${escapeJson(pdfUrl)}\"" else "") + """
              \"pdf_url\": \"${escapeJson(pdfUrl)}\"" else ""}
            }
        """.trimIndent()'''

good_block = r'''        val pdfJson = if (pdfUrl != null) ",\n              \"pdf_url\": \"${escapeJson(pdfUrl)}\"" else ""
        val jsonBody = """
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
              "visibility": $visibility$pdfJson
            }
        """.trimIndent()'''

content = content.replace(bad_block, good_block)

with open('app/src/main/java/com/example/api/R2SupabaseManager.kt', 'w') as f:
    f.write(content)
