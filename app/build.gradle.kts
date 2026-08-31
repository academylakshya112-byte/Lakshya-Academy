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
    versionCode = 5
    versionName = "1.4"

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

// Keys we want to synchronize from system environment to .env
val varsToSync = listOf(
    "LAKSHYA_GEMINI_API_KEY",
    "SUPABASE_URL",
    "SUPABASE_ANON_KEY",
    "SUPABASE_TABLE",
    "R2_ACCOUNT_ID",
    "R2_BUCKET_NAME",
    "R2_ACCESS_KEY_ID",
    "R2_SECRET_ACCESS_KEY",
    "R2_PUBLIC_URL",
    "STORAGE_PROVIDER",
    "B2_ENDPOINT",
    "B2_BUCKET_NAME",
    "B2_APPLICATION_KEY_ID",
    "B2_APPLICATION_KEY",
    "GOOGLE_WEB_CLIENT_ID",
    "YOUTUBE_API_KEY"
)

val resolvedVars = mutableMapOf<String, String>()

// Pre-fill with placeholders or defaults
varsToSync.forEach { key ->
    resolvedVars[key] = when (key) {
        "LAKSHYA_GEMINI_API_KEY" -> "YOUR_LAKSHYA_GEMINI_API_KEY"
        "SUPABASE_URL" -> "YOUR_SUPABASE_URL"
        "SUPABASE_ANON_KEY" -> "YOUR_SUPABASE_ANON_KEY"
        "SUPABASE_TABLE" -> "videos"
        "R2_ACCOUNT_ID" -> "YOUR_R2_ACCOUNT_ID"
        "R2_BUCKET_NAME" -> "YOUR_R2_BUCKET_NAME"
        "R2_ACCESS_KEY_ID" -> "YOUR_R2_ACCESS_KEY_ID"
        "R2_SECRET_ACCESS_KEY" -> "YOUR_R2_SECRET_ACCESS_KEY"
        "R2_PUBLIC_URL" -> "YOUR_R2_PUBLIC_URL"
        "STORAGE_PROVIDER" -> "BACKBLAZE_B2"
        "B2_ENDPOINT" -> "s3.us-east-005.backblazeb2.com"
        "B2_BUCKET_NAME" -> "lakshyaacademy"
        "B2_APPLICATION_KEY_ID" -> "0051b27e56190ee0000000002"
        "B2_APPLICATION_KEY" -> "K005uux3V6ogoTTUzRJ3eyPN2bwdde4"
        "GOOGLE_WEB_CLIENT_ID" -> "YOUR_GOOGLE_WEB_CLIENT_ID"
        "YOUTUBE_API_KEY" -> "YOUR_YOUTUBE_API_KEY"
        else -> ""
    }
}

// 1. Try reading from existing .env file
if (envFile.exists()) {
    envFile.readLines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.contains("=") && !trimmed.startsWith("#")) {
            val parts = trimmed.split("=", limit = 2)
            val k = parts[0].trim()
            val v = parts[1].trim().removeSurrounding("\"").removeSurrounding("'").trim()
            if (varsToSync.contains(k) && v.isNotBlank()) {
                resolvedVars[k] = v
            }
        }
    }
}

// 2. Try reading from environment variables to override or supplement
varsToSync.forEach { key ->
    val envValue = System.getenv(key) ?: ""
    val cleanedValue = envValue.trim().removeSurrounding("\"").removeSurrounding("'").trim()
    if (cleanedValue.isNotBlank() && cleanedValue != "YOUR_${key}" && cleanedValue != "YOUR_GEMINI_API_KEY" && cleanedValue != "YOUR_LAKSHYA_GEMINI_API_KEY") {
        resolvedVars[key] = cleanedValue
    }
}

// Special check for older/alternate names
val envGeminiKey = System.getenv("GEMINI_API_KEY") ?: ""
val cleanedGeminiKey = envGeminiKey.trim().removeSurrounding("\"").removeSurrounding("'").trim()
if (cleanedGeminiKey.isNotBlank() && (resolvedVars["LAKSHYA_GEMINI_API_KEY"] == "YOUR_LAKSHYA_GEMINI_API_KEY" || resolvedVars["LAKSHYA_GEMINI_API_KEY"].isNullOrBlank())) {
    resolvedVars["LAKSHYA_GEMINI_API_KEY"] = cleanedGeminiKey
}

val envMyKey = System.getenv("MY_API_KEY") ?: ""
val cleanedMyKey = envMyKey.trim().removeSurrounding("\"").removeSurrounding("'").trim()
if (cleanedMyKey.isNotBlank() && (resolvedVars["LAKSHYA_GEMINI_API_KEY"] == "YOUR_LAKSHYA_GEMINI_API_KEY" || resolvedVars["LAKSHYA_GEMINI_API_KEY"].isNullOrBlank())) {
    resolvedVars["LAKSHYA_GEMINI_API_KEY"] = cleanedMyKey
}

// Write everything back to .env
val sb = StringBuilder()
resolvedVars.forEach { (k, v) ->
    sb.append("$k=$v\n")
}
envFile.writeText(sb.toString())

logger.lifecycle("BUILD CONFIG SYNC: Successfully wrote environment variables to .env")
resolvedVars.forEach { (k, v) ->
    val masked = if (v.length > 6) v.take(4) + "..." else v
    logger.lifecycle("BUILD CONFIG SYNC: $k = $masked")
}

secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
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
  implementation(libs.androidx.core.splashscreen)
  implementation("androidx.security:security-crypto:1.1.0-alpha06")
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.ui)
  implementation(libs.androidx.media3.common)
  implementation(libs.image.cropper)
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
