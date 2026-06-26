package com.example.ui.screens

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

fun downloadPdfFromUri(context: android.content.Context, pdfUriString: String, pdfName: String) {
    try {
        val uri = Uri.parse(pdfUriString)
        val contentResolver = context.contentResolver
        val inputStream: InputStream? = contentResolver.openInputStream(uri)
        if (inputStream == null) {
            Toast.makeText(context, "Cannot open PDF file source", Toast.LENGTH_SHORT).show()
            return
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

        val downloadUri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (downloadUri == null) {
            Toast.makeText(context, "Failed to create Download file reference", Toast.LENGTH_SHORT).show()
            inputStream.close()
            return
        }

        val outputStream: OutputStream? = contentResolver.openOutputStream(downloadUri)
        if (outputStream == null) {
            Toast.makeText(context, "Failed to open Download location output stream", Toast.LENGTH_SHORT).show()
            inputStream.close()
            return
        }

        inputStream.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }

        Toast.makeText(context, "PDF downloaded successfully: $fileName", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

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

    // Global pinch-to-zoom and pan/drag state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        if (scale > 1f) {
            offset = Offset(offset.x + offsetChange.x, offset.y + offsetChange.y)
        } else {
            offset = Offset.Zero
        }
    }

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
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) { 
                        Icon(Icons.Default.Close, contentDescription = "Close View") 
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(pdfName, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                        if (fileSize.isNotBlank()) {
                            Text(fileSize, fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                    IconButton(onClick = {
                        downloadPdfFromUri(context, pdfUri, pdfName)
                    }) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Download PDF",
                            tint = Color(0xFF6366F1)
                        )
                    }
                }
                HorizontalDivider()

                if (errorMsg != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(errorMsg!!, color = Color.Red, fontSize = 14.sp)
                    }
                } else if (pageCount > 0) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(0xFFE2E8F0))
                            .transformable(state = transformState)
                            .clickable(enabled = scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            }
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                )
                        ) {
                            items(pageCount) { index ->
                                PdfPageRenderer(pdfRenderer = pdfRenderer, pageIndex = index)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        if (scale > 1f) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
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
                        val targetWidth = context.resources.displayMetrics.widthPixels
                        val scaleFactor = targetWidth.toFloat() / page.width
                        val targetHeight = (page.height * scaleFactor).toInt()

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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(Color.White)
        ) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "PDF Page ${pageIndex + 1}",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}
