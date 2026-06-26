package com.example.util

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecureStorage {
    private const val PREF_NAME = "secure_prefs"
    private const val KEY_API_KEY = "gemini_api_key"

    private fun getEncryptedPrefs(context: Context): android.content.SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            PREF_NAME,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e("SecureStorage", "Failed to initialize EncryptedSharedPreferences, falling back to standard prefs", e)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveApiKey(context: Context, apiKey: String) {
        try {
            getEncryptedPrefs(context).edit().putString(KEY_API_KEY, apiKey).apply()
        } catch (e: Exception) {
            Log.e("SecureStorage", "Failed to save API key", e)
            try {
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putString(KEY_API_KEY, apiKey).apply()
            } catch (ex: Exception) {
                Log.e("SecureStorage", "Backup storage also failed", ex)
            }
        }
    }

    fun getApiKey(context: Context): String? {
        return try {
            getEncryptedPrefs(context).getString(KEY_API_KEY, null)
        } catch (e: Exception) {
            Log.e("SecureStorage", "Failed to get API key", e)
            try {
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_API_KEY, null)
            } catch (ex: Exception) {
                Log.e("SecureStorage", "Backup retrieval also failed", ex)
                null
            }
        }
    }
}
