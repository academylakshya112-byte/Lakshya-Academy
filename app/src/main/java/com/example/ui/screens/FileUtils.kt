package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

fun getFileNameFromUri(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor.use {
            if (it != null && it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = it.getString(index)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "file"
}

fun getFileSizeFromUri(context: Context, uri: Uri): String {
    var result: Long = 0
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor.use {
            if (it != null && it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.SIZE)
                if (index != -1) {
                    result = it.getLong(index)
                }
            }
        }
    }
    if (result <= 0) return "Unknown Size"
    val kb = result / 1024.0
    val mb = kb / 1024.0
    return if (mb > 1.0) String.format("%.2f MB", mb) else String.format("%.2f KB", kb)
}
