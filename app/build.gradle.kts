plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.example"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.aistudio.lakshya_academy.gzkvpm"
    minSdk = 24
    targetSdk = 35
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  kotlinOptions {
    jvmTarget = "11"
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
// Proactively generate .env from environment variables if present
val envFile = rootProject.file(".env")
var finalKey = ""

// 1. Try reading from existing .env file
if (envFile.exists()) {
    val lines = envFile.readLines()
    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("GEMINI_API_KEY=")) {
            val value = trimmed.substringAfter("GEMINI_API_KEY=").trim().removeSurrounding("\"").removeSurrounding("'").trim()
            if (value.isNotBlank() && value != "YOUR_GEMINI_API_KEY") {
                finalKey = value
            }
        } else if (trimmed.startsWith("MY_API_KEY=")) {
            val value = trimmed.substringAfter("MY_API_KEY=").trim().removeSurrounding("\"").removeSurrounding("'").trim()
            if (value.isNotBlank() && finalKey.isBlank()) {
                finalKey = value
            }
        }
    }
}

// 2. Try reading from environment variables (overrides or supplements .env)
val envGemini = System.getenv("GEMINI_API_KEY") ?: ""
val envMyKey = System.getenv("MY_API_KEY") ?: ""
if (envGemini.isNotBlank() && envGemini != "YOUR_GEMINI_API_KEY") {
    finalKey = envGemini.trim().removeSurrounding("\"").removeSurrounding("'").trim()
} else if (envMyKey.isNotBlank() && finalKey.isBlank()) {
    finalKey = envMyKey.trim().removeSurrounding("\"").removeSurrounding("'").trim()
}

// Ensure the final API key is set and cleaned
finalKey = finalKey.trim().removeSurrounding("\"").removeSurrounding("'").trim()

if (finalKey.isNotBlank() && finalKey != "YOUR_GEMINI_API_KEY") {
    // Read other variables in existing .env to preserve them
    val otherProperties = mutableMapOf<String, String>()
    if (envFile.exists()) {
        envFile.readLines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.contains("=") && !trimmed.startsWith("#")) {
                val parts = trimmed.split("=", limit = 2)
                val key = parts[0].trim()
                val value = parts[1].trim()
                if (key != "GEMINI_API_KEY") {
                    otherProperties[key] = value
                }
            }
        }
    }
    
    // Write everything back, ensuring GEMINI_API_KEY is present
    val sb = StringBuilder()
    sb.append("GEMINI_API_KEY=$finalKey\n")
    otherProperties.forEach { (k, v) ->
        sb.append("$k=$v\n")
    }
    envFile.writeText(sb.toString())
    logger.lifecycle("GEMINI DEBUG: Resolved GEMINI_API_KEY successfully. Key prefix: ${finalKey.take(4)}")
} else {
    logger.lifecycle("GEMINI DEBUG: GEMINI_API_KEY could not be resolved from environment or .env file.")
}

secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(libs.android.youtube.player.core)
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation("androidx.security:security-crypto:1.1.0-alpha06")
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.ui)
  implementation(libs.androidx.media3.common)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.firebase.auth)
  implementation(libs.firebase.firestore)
  implementation(libs.play.services.auth)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  implementation(libs.logging.interceptor)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
