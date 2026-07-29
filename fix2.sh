sed -i 's/\${if (pdfUrl != null).*/" + (if (pdfUrl != null) ",\\n              \\"pdf_url\\": \\"${escapeJson(pdfUrl)}\\"" else "") + """/' app/src/main/java/com/example/api/R2SupabaseManager.kt
