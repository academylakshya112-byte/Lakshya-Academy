package com.example.ui.screens

import android.content.ContentValues
import android.graphics.Paint
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import java.io.OutputStream
import androidx.compose.foundation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.theme.*
import com.example.ui.viewmodel.AcademyViewModel

@Composable
fun AnimatedAppsIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "apps_icon_anim")
    
    // Scale breathing effect
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Breathing glow alpha for the gradient blocks
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = Modifier
            .size(24.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.5.dp)) {
                // Top-Left Block (Deep Blue)
                Box(
                    modifier = Modifier
                        .size(9.5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF2563EB), Color(0xFF1E40AF))
                            )
                        )
                )
                // Top-Right Block (Indigo)
                Box(
                    modifier = Modifier
                        .size(9.5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF4F46E5), Color(0xFF3730A3))
                            )
                        )
                        .graphicsLayer { alpha = glowAlpha }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.5.dp)) {
                // Bottom-Left Block (Teal/Cyan)
                Box(
                    modifier = Modifier
                        .size(9.5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF06B6D4), Color(0xFF0891B2))
                            )
                        )
                        .graphicsLayer { alpha = glowAlpha }
                )
                // Bottom-Right Block (Pink/Red Accent)
                Box(
                    modifier = Modifier
                        .size(9.5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFFEC4899), Color(0xFFE11D48))
                            )
                        )
                )
            }
        }
    }
}

@Composable
fun Academic3x3GridDashboard(onTabSelect: (String) -> Unit) {
    val items = listOf(
        Triple("Live Classes", Icons.Default.LiveTv, "live_classes"),
        Triple("Course Syllabus", Icons.Default.LibraryBooks, "syllabus"),
        Triple("Lakshya AI Coach", Icons.Default.AutoAwesome, "doubt_solver"),
        Triple("Current Affairs", Icons.Default.Newspaper, "current_affairs"),
        Triple("Test Series", Icons.Default.Quiz, "TESTS"),
        Triple("Previous Papers", Icons.Default.HistoryEdu, "previous_papers"),
        Triple("Exam Alerts", Icons.Default.NotificationsActive, "ALERTS"),
        Triple("My Progress", Icons.Default.Leaderboard, "DASHBOARD"),
        Triple("Free Books", Icons.Default.AutoStories, "books"),
        Triple("Time Table", Icons.Default.CalendarMonth, "timetable"),
        Triple("Study Apps", Icons.Default.Apps, "study_websites")
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val rows = items.chunked(3)
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { (label, icon, route) ->
                    val isStudyApps = route == "study_websites"

                    Card(
                        onClick = { onTabSelect(route) },
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .then(
                                if (isStudyApps) {
                                    Modifier.border(
                                        width = 1.dp,
                                        brush = Brush.linearGradient(
                                            colors = listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6))
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                } else Modifier
                            ),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isStudyApps) Color(0xFFF8FAFC) else Color.White
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isStudyApps) 2.dp else 1.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isStudyApps) Color(0xFFEEF2F6) else BrandBluePrimary.copy(alpha = 0.08f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isStudyApps) {
                                    AnimatedAppsIcon()
                                } else {
                                    Icon(icon, contentDescription = label, tint = BrandBluePrimary, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                lineHeight = 12.sp,
                                modifier = Modifier.padding(horizontal = 4.dp),
                                color = if (isStudyApps) Color(0xFF1E293B) else Color.Unspecified
                            )
                        }
                    }
                }
                if (rowItems.size < 3) {
                    repeat(3 - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@android.annotation.SuppressLint("NewApi")
fun generateAndDownloadPdfFromText(context: android.content.Context, pdfName: String, pages: List<String>) {
    try {
        val pdfDocument = PdfDocument()
        
        val paint = Paint().apply {
            textSize = 14f
            color = AndroidColor.BLACK
            isAntiAlias = true
        }
        
        val titlePaint = Paint().apply {
            textSize = 18f
            color = AndroidColor.BLACK
            isFakeBoldText = true
            isAntiAlias = true
        }

        val pageWidth = 595
        val pageHeight = 842

        for (i in pages.indices) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, i + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            canvas.drawColor(AndroidColor.WHITE)

            var yOffset = 50f
            
            if (i == 0) {
                canvas.drawText(pdfName, 40f, yOffset, titlePaint)
                yOffset += 40f
                canvas.drawLine(40f, yOffset - 15f, (pageWidth - 40).toFloat(), yOffset - 15f, Paint().apply { 
                    strokeWidth = 1f
                    color = AndroidColor.GRAY
                })
            }

            val text = pages[i]
            val lines = text.split("\n")
            for (line in lines) {
                val words = line.split(" ")
                var currentLine = StringBuilder()
                for (word in words) {
                    val testString = currentLine.toString() + (if (currentLine.isEmpty()) "" else " ") + word
                    val width = paint.measureText(testString)
                    if (width < (pageWidth - 80)) {
                        currentLine.append(if (currentLine.isEmpty()) "" else " ").append(word)
                    } else {
                        canvas.drawText(currentLine.toString(), 40f, yOffset, paint)
                        yOffset += 24f
                        currentLine = StringBuilder(word)
                        if (yOffset > pageHeight - 60) {
                            break
                        }
                    }
                }
                if (currentLine.isNotEmpty() && yOffset <= pageHeight - 60) {
                    canvas.drawText(currentLine.toString(), 40f, yOffset, paint)
                    yOffset += 24f
                }
                
                if (yOffset > pageHeight - 60) {
                    break
                }
            }
            
            val pageNumStr = "Page ${i + 1} of ${pages.size}"
            val pageNumPaint = Paint().apply {
                textSize = 10f
                color = AndroidColor.GRAY
                isAntiAlias = true
            }
            canvas.drawText(pageNumStr, (pageWidth - 40 - pageNumPaint.measureText(pageNumStr)), (pageHeight - 30).toFloat(), pageNumPaint)

            pdfDocument.finishPage(page)
        }

        val sanitizedName = pdfName.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
        val fileName = if (sanitizedName.endsWith(".pdf", ignoreCase = true)) sanitizedName else "$sanitizedName.pdf"

        val contentResolver = context.contentResolver
        val downloadUri: android.net.Uri? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val file = java.io.File(downloadsDir, fileName)
            android.net.Uri.fromFile(file)
        }

        if (downloadUri == null) {
            Toast.makeText(context, "Failed to create Download file reference", Toast.LENGTH_SHORT).show()
            pdfDocument.close()
            return
        }

        val outputStream: OutputStream? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            contentResolver.openOutputStream(downloadUri)
        } else {
            java.io.FileOutputStream(java.io.File(downloadUri.path ?: ""))
        }

        if (outputStream == null) {
            Toast.makeText(context, "Failed to open output stream", Toast.LENGTH_SHORT).show()
            pdfDocument.close()
            return
        }

        outputStream.use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        Toast.makeText(context, "PDF generated & downloaded: $fileName", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Generation/Download failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun MockPdfViewerScreen(
    pdfName: String,
    customContent: String = "",
    fileSize: String = "",
    onDismiss: () -> Unit
) {
    val pdfContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000L)
            com.example.util.StudyTracker.addStudyTime(pdfContext, 1)
        }
    }

    val pages = remember(customContent) {
        if (customContent.isBlank()) {
           val text = "Mock Study Notes Database\n\nTitle: $pdfName\n\nThis is a mock PDF document. In a real environment, this screen will render the actual pages of the PDF file you uploaded. Currently, no actual file content was provided or matched, so we are displaying this placeholder text. Please ensure you select a real PDF file when uploading."
           val list = mutableListOf<String>()
           var remaining = text
           while (remaining.isNotEmpty()) {
               if (remaining.length <= 550) {
                   list.add(remaining)
                   break
               } else {
                   var splitIndex = remaining.lastIndexOf(' ', 550)
                   if (splitIndex < 400) splitIndex = 500
                   if (splitIndex >= remaining.length) splitIndex = remaining.length - 1
                   list.add(remaining.substring(0, splitIndex))
                   remaining = remaining.substring(splitIndex).trim()
               }
           }
           list
        } else {
            val list = mutableListOf<String>()
            var remaining = customContent
            while (remaining.isNotEmpty()) {
                if (remaining.length <= 550) {
                    list.add(remaining)
                    break
                } else {
                    var splitIndex = remaining.lastIndexOf(' ', 550)
                    if (splitIndex < 400) splitIndex = 500
                    if (splitIndex >= remaining.length) splitIndex = remaining.length - 1
                    list.add(remaining.substring(0, splitIndex))
                    remaining = remaining.substring(splitIndex).trim()
                }
            }
            list.ifEmpty { listOf(customContent) }
        }
    }
    
    var currentPage by remember { mutableIntStateOf(1) }
    val totalPages = pages.size

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        if (scale > 1f) {
            offset = Offset(offset.x + offsetChange.x, offset.y + offsetChange.y)
        } else {
            offset = Offset.Zero
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogWindow = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        LaunchedEffect(Unit) {
            dialogWindow?.let { window ->
                window.setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE))
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = null) }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(pdfName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(fileSize, fontSize = 11.sp, color = Color.Gray)
                    }
                    val context = androidx.compose.ui.platform.LocalContext.current
                    IconButton(onClick = {
                        generateAndDownloadPdfFromText(context, pdfName, pages)
                    }) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Download PDF",
                            tint = Color(0xFF6366F1)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFFF1F5F9))
                        .transformable(state = state)
                        .clickable(enabled = scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        }
                        .padding(16.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                    ) {
                        Text(pages.getOrElse(currentPage - 1) { "" }, color = Color.DarkGray, fontSize = 15.sp, lineHeight = 24.sp)
                    }
                    if (scale > 1f) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                        ) {
                            Text(
                                "Tap to Reset Zoom",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { if (currentPage > 1) { currentPage--; scale = 1f; offset = Offset.Zero } }, enabled = currentPage > 1, shape = RoundedCornerShape(8.dp)) {
                        Text("Prev Page", fontSize = 12.sp)
                    }
                    Text("Page $currentPage of $totalPages", color = Color.Black, fontSize = 13.sp)
                    Button(onClick = { if (currentPage < totalPages) { currentPage++; scale = 1f; offset = Offset.Zero } }, enabled = currentPage < totalPages, shape = RoundedCornerShape(8.dp)) {
                        Text("Next Page", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
