package com.example.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

object ToastHelper {
    private var currentToast: Toast? = null

    fun showToast(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        val mainLooper = Looper.getMainLooper()
        if (Looper.myLooper() == mainLooper) {
            try {
                currentToast?.cancel()
            } catch (e: Exception) {
                // Ignore any potential exception on cancel
            }
            val toast = Toast.makeText(context.applicationContext, message, duration)
            currentToast = toast
            toast.show()
        } else {
            Handler(mainLooper).post {
                try {
                    currentToast?.cancel()
                } catch (e: Exception) {
                    // Ignore any potential exception on cancel
                }
                val toast = Toast.makeText(context.applicationContext, message, duration)
                currentToast = toast
                toast.show()
            }
        }
    }
}
