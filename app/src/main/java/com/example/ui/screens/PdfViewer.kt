package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun NativePdfViewerScreen(
    pdfUri: String,
    pdfName: String,
    fileSize: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var fileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    DisposableEffect(pdfUri) {
        try {
            val uri = Uri.parse(pdfUri)
            fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            if (fileDescriptor != null) {
                pdfRenderer = PdfRenderer(fileDescriptor!!)
                pageCount = pdfRenderer?.pageCount ?: 0
            } else {
                errorMsg = "Could not load PDF. File descriptor is null."
            }
        } catch (e: Exception) {
            e.printStackTrace()
            errorMsg = "Error opening PDF: ${e.message}"
        }
        onDispose {
            pdfRenderer?.close()
            fileDescriptor?.close()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = null) }
                    Column {
                        Text(pdfName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(fileSize, fontSize = 11.sp, color = Color.Gray)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                if (errorMsg != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(errorMsg!!, color = Color.Red, fontSize = 14.sp)
                    }
                } else if (pageCount > 0) {
                    LazyColumn(modifier = Modifier.fillMaxSize().background(Color(0xFFE2E8F0))) {
                        items(pageCount) { index ->
                            PdfPageRenderer(pdfRenderer = pdfRenderer, pageIndex = index)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
fun PdfPageRenderer(pdfRenderer: PdfRenderer?, pageIndex: Int) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pdfRenderer, pageIndex) {
        withContext(Dispatchers.IO) {
            try {
                if (pdfRenderer != null) {
                    synchronized(pdfRenderer) {
                        val page = pdfRenderer.openPage(pageIndex)
                        val density = context.resources.displayMetrics.density
                        // Determine target width and scale height proportionally
                        val targetWidth = (context.resources.displayMetrics.widthPixels) - (if (density > 2) 100 else 60)
                        val scale = targetWidth.toFloat() / page.width
                        val targetHeight = (page.height * scale).toInt()

                        val newBitmap = Bitmap.createBitmap(
                            targetWidth.coerceAtLeast(1),
                            targetHeight.coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        )
                        newBitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(newBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        bitmap = newBitmap
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "PDF Page ${pageIndex + 1}",
            modifier = Modifier.fillMaxWidth().background(Color.White),
            contentScale = ContentScale.FillWidth
        )
    } else {
        Box(modifier = Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
