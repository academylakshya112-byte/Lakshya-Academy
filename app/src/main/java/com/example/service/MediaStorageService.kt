package com.example.service

import android.content.Context
import android.util.Log

interface MediaStorageService {
    fun resolveMediaUrl(sourceUrl: String): String
    fun getStorageType(): String
}

class DefaultMediaStorageService(private val context: Context) : MediaStorageService {
    override fun resolveMediaUrl(sourceUrl: String): String {
        if (sourceUrl.isBlank()) return ""
        val trimmed = sourceUrl.trim()
        
        // 0. Complete audit of incoming URL
        Log.d("MediaStorageService", "[RESOLVE_START] Input Source URL: '$trimmed'")
        
        // 1. Direct HTTPS or HTTP Urls
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            // Check specifically for Supabase public storage URLs as requested
            if (trimmed.contains("supabase.co/storage/v1/object/public/", ignoreCase = true)) {
                Log.i("MediaStorageService", "[SUPABASE_DIRECT] Detected valid public Supabase URL, using as-is: $trimmed")
                return trimmed
            }
            
            if (trimmed.contains("supabase", ignoreCase = true) && (trimmed.contains("banners", ignoreCase = true) || trimmed.contains("videos", ignoreCase = true) || trimmed.contains("courses", ignoreCase = true))) {
                Log.i("MediaStorageService", "[IMAGE_LOAD] Loading media from Supabase Storage: $trimmed")
            } else {
                Log.d("MediaStorageService", "Loading direct URL: $trimmed")
            }
            return trimmed
        }
        
        // 2. Cloudflare R2 URLs
        if (trimmed.startsWith("r2://", ignoreCase = true)) {
            val fileKey = trimmed.substring(5) // remove "r2://"
            // Retrieve custom domain from preferences or default to a configurable fallback
            val sharedPrefs = context.getSharedPreferences("r2_storage_config", Context.MODE_PRIVATE)
            val customDomain = sharedPrefs.getString("r2_public_domain", "https://pub-lakshya.r2.dev") ?: "https://pub-lakshya.r2.dev"
            val resolved = if (customDomain.endsWith("/")) "$customDomain$fileKey" else "$customDomain/$fileKey"
            Log.d("MediaStorageService", "Resolved Cloudflare R2 Key: $trimmed -> $resolved")
            return resolved
        }

        // 2.5 Backblaze B2 Private Bucket URLs
        if (trimmed.startsWith("b2://", ignoreCase = true)) {
            val combined = trimmed.substring(5) // remove "b2://"
            val slashIdx = combined.indexOf('/')
            val bucketName = if (slashIdx != -1) combined.substring(0, slashIdx) else ""
            val fileKey = if (slashIdx != -1) combined.substring(slashIdx + 1) else combined

            val creds = com.example.api.BackblazeB2Manager.getCredentials(context)
            val resolved = if (creds.isValid()) {
                com.example.api.BackblazeB2Manager.getPresignedUrl(
                    host = creds.host,
                    bucketName = bucketName.ifBlank { creds.b2BucketName },
                    objectKey = fileKey,
                    accessKeyId = creds.b2AccessKeyId,
                    secretAccessKey = creds.b2SecretAccessKey,
                    region = creds.region
                )
            } else {
                ""
            }
            Log.d("MediaStorageService", "Resolved Backblaze B2 Key: $trimmed -> $resolved")
            return resolved
        }
        
        // 3. Supabase Storage paths
        if (trimmed.startsWith("supabase://", ignoreCase = true)) {
            val path = trimmed.substring(11) // remove "supabase://"
            val creds = com.example.api.R2SupabaseManager.getCredentials(context)
            val baseUrl = if (creds.supabaseUrl.isNotBlank()) creds.supabaseUrl else "https://dummy.supabase.co"
            val cleanBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val resolved = "${cleanBaseUrl}storage/v1/object/public/$path"
            Log.d("MediaStorageService", "Resolved Supabase Storage path: $trimmed -> $resolved")
            return resolved
        }
        
        // 4. Firebase Storage URIs
        if (trimmed.startsWith("gs://", ignoreCase = true)) {
            val path = trimmed.substring(5) // remove "gs://"
            // Can resolve using Firebase HTTP API pattern or return as is if SDK is ready
            val resolved = "https://firebasestorage.googleapis.com/v0/b/$path"
            Log.d("MediaStorageService", "Resolved Firebase Storage URI: $trimmed -> $resolved")
            return resolved
        }
        
        return trimmed
    }

    override fun getStorageType(): String {
        val sharedPrefs = context.getSharedPreferences("r2_storage_config", Context.MODE_PRIVATE)
        val defaultProvider = com.example.BuildConfig.STORAGE_PROVIDER.ifBlank { "BACKBLAZE_B2" }
        return sharedPrefs.getString("active_provider", defaultProvider) ?: defaultProvider
    }
}

object MediaStorageServiceFactory {
    @Volatile
    private var instance: MediaStorageService? = null

    fun getService(context: Context): MediaStorageService {
        return instance ?: synchronized(this) {
            val current = instance ?: DefaultMediaStorageService(context.applicationContext)
            instance = current
            current
        }
    }
}
