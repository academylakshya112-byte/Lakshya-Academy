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
fun Academic3x3GridDashboard(onTabSelect: (String) -> Unit) {
    val items = listOf(
        Triple("Course Syllabus", Icons.Default.LibraryBooks, "COURSES"),
        Triple("Current Affairs", Icons.Default.Newspaper, "current_affairs"),
        Triple("Test Series", Icons.Default.Quiz, "TESTS"),
        Triple("Previous Papers", Icons.Default.HistoryEdu, "previous_papers"),
        Triple("Exam Alerts", Icons.Default.NotificationsActive, "ALERTS"),
        Triple("My Progress", Icons.Default.Leaderboard, "DASHBOARD"),
        Triple("Free Books", Icons.Default.AutoStories, "books"),
        Triple("Time Table", Icons.Default.CalendarMonth, "timetable"),
        Triple("Lakshya AI 5.0 Ultra", Icons.Default.Psychology, "doubt_solver"),
        Triple("Account (Auth)", Icons.Default.AccountCircle, "firebase_auth")
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false
    ) {
        items(items) { (label, icon, route) ->
            Card(
                onClick = { onTabSelect(route) },
                modifier = Modifier.aspectRatio(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                            .background(BrandBluePrimary.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = label, tint = BrandBluePrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        lineHeight = 12.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

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

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
        }

        val contentResolver = context.contentResolver
        val downloadUri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (downloadUri == null) {
            Toast.makeText(context, "Failed to create Download file reference", Toast.LENGTH_SHORT).show()
            pdfDocument.close()
            return
        }

        val outputStream: OutputStream? = contentResolver.openOutputStream(downloadUri)
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
