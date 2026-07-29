import re

with open('app/src/main/java/com/example/api/R2SupabaseManager.kt', 'r') as f:
    content = f.read()

bad_str = r'${if (pdfUrl != null) ",\n              \"pdf_url\": \"${escapeJson(pdfUrl)}\"" else ""}'
good_str = '\" + (if (pdfUrl != null) ",\\n              \\"pdf_url\\": \\"${escapeJson(pdfUrl)}\\"" else "") + \"\"\"'

content = content.replace(bad_str, good_str)

with open('app/src/main/java/com/example/api/R2SupabaseManager.kt', 'w') as f:
    f.write(content)
