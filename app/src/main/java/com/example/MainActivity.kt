package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.MainAppScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AcademyViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Proactively create WebView Code Cache directories to prevent Chromium from logging "No such file or directory" opendir errors.
    try {
      val jsDir = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/js")
      val wasmDir = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm")
      if (!jsDir.exists()) {
        jsDir.mkdirs()
      }
      if (!wasmDir.exists()) {
        wasmDir.mkdirs()
      }
    } catch (e: Exception) {
      android.util.Log.e("WebViewSetup", "Error creating WebView cache dirs: ${e.message}")
    }

    enableEdgeToEdge()
    setContent {
      val viewModel: AcademyViewModel = viewModel()
      MainAppScreen(viewModel = viewModel)
    }
  }
}

