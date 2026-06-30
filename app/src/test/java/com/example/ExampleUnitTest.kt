package com.example

import org.junit.Test
import org.junit.Assert.*
import com.example.ui.screens.detectVideoSourceType
import com.example.ui.screens.extractYouTubeVideoId
import com.example.ui.screens.convertDriveUrl

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
    val inputUrl = "https://youtu.be/dQw4w9WgXcQ"
    val extractedId = extractYouTubeVideoId(inputUrl)
    assertEquals("dQw4w9WgXcQ", extractedId)
    
    val generatedThumbnail = "https://img.youtube.com/vi/$extractedId/hqdefault.jpg"
    assertEquals("https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg", generatedThumbnail)
  }
}

