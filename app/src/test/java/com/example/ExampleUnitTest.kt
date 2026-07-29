package com.example

import org.junit.Test
import org.junit.Assert.*
import com.example.ui.screens.detectVideoSourceType
import com.example.ui.screens.extractYouTubeVideoId
import com.example.ui.screens.convertDriveUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class ExampleUnitTest {
  @Test
  fun printEnv() {
    println("=== ENV VARIABLES ===")
    System.getenv().forEach { (k, v) ->
        val masked = if (v.length > 6) v.take(6) + "..." else "..."
        println("$k = $masked (length: ${v.length})")
    }
    println("=====================")
  }

  @Test
  fun testDetectVideoSourceType() {
    assertEquals("LOCAL", detectVideoSourceType("content://media/external/video/media/23849"))
    assertEquals("LOCAL", detectVideoSourceType("file:///sdcard/video.mp4"))
    assertEquals("YOUTUBE", detectVideoSourceType("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    assertEquals("YOUTUBE", detectVideoSourceType("https://youtu.be/dQw4w9WgXcQ"))
    assertEquals("GOOGLE_DRIVE", detectVideoSourceType("https://drive.google.com/file/d/1A2B3C4D5E/view"))
    assertEquals("SUPABASE", detectVideoSourceType("https://xyz.supabase.co/storage/v1/object/public/videos/intro.mp4"))
    assertEquals("MP4", detectVideoSourceType("https://www.w3schools.com/html/mov_bbb.mp4"))
    assertEquals("MP4", detectVideoSourceType("http://example.com/movie.mp4"))
  }

  @Test
  fun testExtractYouTubeVideoId() {
    assertEquals("dQw4w9WgXcQ", extractYouTubeVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    assertEquals("dQw4w9WgXcQ", extractYouTubeVideoId("https://youtu.be/dQw4w9WgXcQ?si=abc"))
    assertEquals("dQw4w9WgXcQ", extractYouTubeVideoId("https://www.youtube.com/embed/dQw4w9WgXcQ"))
    assertEquals("dQw4w9WgXcQ", extractYouTubeVideoId("dQw4w9WgXcQ"))
    assertNull(extractYouTubeVideoId("invalidurl"))
  }

  @Test
  fun testConvertDriveUrl() {
    val inputUrl = "https://drive.google.com/file/d/1A2B3C4D5E/view?usp=sharing"
    val expectedUrl = "https://drive.google.com/uc?export=download&id=1A2B3C4D5E"
    assertEquals(expectedUrl, convertDriveUrl(inputUrl))
    
    val otherInputUrl = "https://drive.google.com/open?id=1X2Y3Z"
    val otherExpectedUrl = "https://drive.google.com/uc?export=download&id=1X2Y3Z"
    assertEquals(otherExpectedUrl, convertDriveUrl(otherInputUrl))
  }

  @Test
  fun testYouTubeExtractionAndThumbnail() {
    val inputUrl = "https://youtu.be/EWAI1fi3k7Y"
    val extractedId = extractYouTubeVideoId(inputUrl)
    assertEquals("EWAI1fi3k7Y", extractedId)
    
    val generatedThumbnail = "https://img.youtube.com/vi/$extractedId/hqdefault.jpg"
    assertEquals("https://img.youtube.com/vi/EWAI1fi3k7Y/hqdefault.jpg", generatedThumbnail)
  }

  @Test
  fun testB2Connection() {
    val creds = com.example.api.BackblazeB2Manager.Credentials(
      b2BucketName = "lakshyaacademy",
      b2AccessKeyId = "0051b27e56190ee0000000002",
      b2SecretAccessKey = "K005uux3V6ogoTTUzRJ3eyPN2bwdde4",
      b2Endpoint = "s3.us-east-005.backblazeb2.com"
    )

    println("=== STARTING BACKBLAZE B2 VERIFICATION TEST ===")
    println("Bucket: ${creds.b2BucketName}")
    println("Endpoint: ${creds.b2Endpoint}")
    println("Key ID: ${creds.b2AccessKeyId}")

    val report = com.example.api.BackblazeB2Manager.verifyB2Connection(creds)

    println("=== B2 VERIFICATION REPORT ===")
    println("Connection Successful: ${report.connectionSuccess}")
    println("Bucket Access Successful: ${report.bucketAccessSuccess}")
    println("Read Permission Successful: ${report.readSuccess}")
    println("Write Permission (Upload temp) Successful: ${report.writeSuccess}")
    println("Delete Permission (Cleanup) Successful: ${report.deleteSuccess}")
    if (report.errorMessage != null) {
      println("Error Details: ${report.errorMessage}")
    }
    println("================================================")

    assertTrue("Connection verification failed: ${report.errorMessage}", report.connectionSuccess)
    assertTrue("Bucket access verification failed: ${report.errorMessage}", report.bucketAccessSuccess)
    assertTrue("Read permission verification failed: ${report.errorMessage}", report.readSuccess)
    assertTrue("Write permission verification failed: ${report.errorMessage}", report.writeSuccess)
    assertTrue("Delete permission verification failed: ${report.errorMessage}", report.deleteSuccess)
    assertTrue("Verification not fully successful", report.isFullySuccessful())

    // Let's test getPresignedUrl specifically
    val testKey = "b2_presigned_test_${System.currentTimeMillis()}.txt"
    val host = creds.host
    val bucket = creds.b2BucketName
    val region = creds.region
    val keyId = creds.b2AccessKeyId
    val secret = creds.b2SecretAccessKey

    val okClient = okhttp3.OkHttpClient.Builder()
      .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
      .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
      .build()

    println("=== TESTING PRESIGNED GET URL ===")
    
    // 1. Upload a temp test file
    val putUrl = "https://$host/$bucket/$testKey"
    val putCanonicalUri = "/$bucket/$testKey"
    val testContent = "Test content for presigned GET URL verification"
    val putBody = testContent.toRequestBody("text/plain".toMediaTypeOrNull())

    val putHeaders = com.example.api.BackblazeB2Manager.B2Signer.getSignatureHeaders(
      method = "PUT",
      host = host,
      canonicalUri = putCanonicalUri,
      queryParams = emptyMap(),
      accessKeyId = keyId,
      secretAccessKey = secret,
      region = region
    )

    val putRequest = okhttp3.Request.Builder()
      .url(putUrl)
      .put(putBody)
      .apply {
        putHeaders.forEach { (k, v) -> addHeader(k, v) }
      }
      .build()

    okClient.newCall(putRequest).execute().use { response ->
      assertTrue("Failed to upload test object for presigned URL test: ${response.code}", response.isSuccessful)
    }

    // 2. Generate a presigned GET URL using getPresignedUrl
    val presignedUrl = com.example.api.BackblazeB2Manager.getPresignedUrl(
      host = host,
      bucketName = bucket,
      objectKey = testKey,
      accessKeyId = keyId,
      secretAccessKey = secret,
      region = region,
      expiresInSeconds = 3600
    )

    println("Generated Presigned URL (passed to ExoPlayer):")
    println(presignedUrl)

    // 3. Test HTTP Status of generated presigned URL
    val getRequest = okhttp3.Request.Builder()
      .url(presignedUrl)
      .get()
      .build()

    var getStatusCode = -1
    var responseBody = ""
    okClient.newCall(getRequest).execute().use { response ->
      getStatusCode = response.code
      responseBody = response.body?.string() ?: ""
    }

    println("GET Request HTTP Status Code: $getStatusCode")
    if (getStatusCode != 200) {
      println("Response Content: $responseBody")
    }

    // 4. Cleanup/Delete
    val deleteUrl = "https://$host/$bucket/$testKey"
    val deleteCanonicalUri = "/$bucket/$testKey"
    val deleteHeaders = com.example.api.BackblazeB2Manager.B2Signer.getSignatureHeaders(
      method = "DELETE",
      host = host,
      canonicalUri = deleteCanonicalUri,
      queryParams = emptyMap(),
      accessKeyId = keyId,
      secretAccessKey = secret,
      region = region
    )
    val deleteRequest = okhttp3.Request.Builder()
      .url(deleteUrl)
      .delete()
      .apply {
        deleteHeaders.forEach { (k, v) -> addHeader(k, v) }
      }
      .build()
    okClient.newCall(deleteRequest).execute().use { response ->
      println("Temp object cleanup status: ${response.code}")
    }

    assertEquals("Presigned URL GET check failed with status $getStatusCode", 200, getStatusCode)
  }
}

