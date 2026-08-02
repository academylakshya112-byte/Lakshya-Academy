package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.*
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.StudyWebsiteEntity
import com.example.ui.viewmodel.AcademyViewModel
import com.example.api.R2SupabaseManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentStudyWebsitesScreen(
    viewModel: AcademyViewModel,
    onOpenWebsite: (String) -> Unit,
    onBack: () -> Unit
) {
    val websites by viewModel.allStudyWebsites.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Study Websites", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (websites.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No study websites available",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(websites) { website ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("website_card_${website.id}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Banner Image
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .background(Color.LightGray)
                            ) {
                                if (website.imageUrl.isNotBlank()) {
                                    val resolvedUrl = com.example.service.MediaStorageServiceFactory.getService(context).resolveMediaUrl(website.imageUrl)
                                    AsyncImage(
                                        model = resolvedUrl,
                                        contentDescription = "${website.name} Banner",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Language,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = Color.Gray
                                        )
                                    }
                                }
                            }

                            // Website Info & Study Button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1.2f)) {
                                    Text(
                                        text = website.name,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = { onOpenWebsite(website.websiteUrl) },
                                    modifier = Modifier
                                        .weight(0.8f)
                                        .testTag("study_now_button_${website.id}"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text(
                                        text = "Study",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun openInExternalBrowser(context: Context, rawUrl: String) {
    if (rawUrl.isBlank()) return
    val formattedUrl = if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
        "https://$rawUrl"
    } else {
        rawUrl
    }
    val uri = Uri.parse(formattedUrl)

    // Attempt 1: Try Google Chrome directly
    val chromeIntent = Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage("com.android.chrome")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(chromeIntent)
    } catch (e: Exception) {
        // Attempt 2: Fallback to default browser
        val defaultIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(defaultIntent)
        } catch (ex: Exception) {
            Toast.makeText(context, "Unable to open browser", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun StudentStudyWebsitesSection(
    viewModel: AcademyViewModel,
    onOpenWebsite: (String) -> Unit
) {
    val websites by viewModel.allStudyWebsites.collectAsStateWithLifecycle()
    val context = LocalContext.current

    if (websites.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            SectionHeader(title = "Study Websites (अध्ययन वेबसाइटें)")
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                websites.forEach { website ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("website_card_${website.id}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Banner Image
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .background(Color.LightGray)
                            ) {
                                if (website.imageUrl.isNotBlank()) {
                                    val resolvedUrl = com.example.service.MediaStorageServiceFactory.getService(context).resolveMediaUrl(website.imageUrl)
                                    AsyncImage(
                                        model = resolvedUrl,
                                        contentDescription = "${website.name} Banner",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Language,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = Color.Gray
                                        )
                                    }
                                }
                            }

                            // Website Info & Study Button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1.2f)) {
                                    Text(
                                        text = website.name,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = { onOpenWebsite(website.websiteUrl) },
                                    modifier = Modifier
                                        .weight(0.8f)
                                        .testTag("study_now_button_${website.id}"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text(
                                        text = "Study",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminStudyWebsiteScreen(
    viewModel: AcademyViewModel
) {
    val websites by viewModel.allStudyWebsites.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedWebsiteId by remember { mutableStateOf<String?>(null) }
    var websiteName by remember { mutableStateOf("") }
    var websiteUrl by remember { mutableStateOf("") }
    var websiteImageUrl by remember { mutableStateOf("") }
    var localImageUri by remember { mutableStateOf<Uri?>(null) }
    var isUploadingImage by remember { mutableStateOf(false) }
    var fullErrorMessage by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            localImageUri = uri
            fullErrorMessage = null
            // Do NOT overwrite websiteImageUrl with uri.toString() to prevent displaying content:// URI as text
        }
    }

    if (fullErrorMessage != null) {
        AlertDialog(
            onDismissRequest = { fullErrorMessage = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Storage / Save Error", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = fullErrorMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { fullErrorMessage = null }) {
                    Text("Dismiss")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text(
                text = "Manage Study Websites",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Website Name Input
            OutlinedTextField(
                value = websiteName,
                onValueChange = { websiteName = it },
                label = { Text("Website Name") },
                modifier = Modifier.fillMaxWidth()
            )

            // Website Banner Row with Upload Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = if (localImageUri != null) "" else websiteImageUrl,
                    onValueChange = { input ->
                        websiteImageUrl = input
                        if (input.isNotBlank()) {
                            localImageUri = null
                        }
                    },
                    label = { Text("Banner Image URL") },
                    placeholder = {
                        if (localImageUri != null) {
                            Text("Image selected from device")
                        } else {
                            Text("https://example.com/banner.jpg")
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (isUploadingImage) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                } else {
                    Button(onClick = { launcher.launch("image/*") }) {
                        Text(if (localImageUri != null) "Change\nBanner" else "Select\nBanner", textAlign = TextAlign.Center, fontSize = 11.sp)
                    }
                }
            }

            // Thumbnail Preview (Show only selected image preview, never display content:// URI text)
            if (localImageUri != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Banner Preview:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                AsyncImage(
                    model = localImageUri,
                    contentDescription = "Selected Local Image Preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.LightGray),
                    contentScale = ContentScale.Crop
                )
            } else if (websiteImageUrl.isNotBlank() && !websiteImageUrl.startsWith("content://") && !websiteImageUrl.startsWith("file://")) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Banner Preview:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                val resolvedPreview = com.example.service.MediaStorageServiceFactory.getService(context).resolveMediaUrl(websiteImageUrl)
                AsyncImage(
                    model = resolvedPreview,
                    contentDescription = "Selected Website Image Preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.LightGray),
                    contentScale = ContentScale.Crop
                )
            }

            // Website URL Input
            OutlinedTextField(
                value = websiteUrl,
                onValueChange = { websiteUrl = it },
                label = { Text("Website URL") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            // Inline Error Banner if present
            if (fullErrorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Error saving website:",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SelectionContainer {
                            Text(
                                text = fullErrorMessage ?: "",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Save Buttons / Cancel Buttons
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                    if (selectedWebsiteId != null) {
                        TextButton(onClick = {
                            selectedWebsiteId = null
                            websiteName = ""
                            websiteUrl = ""
                            websiteImageUrl = ""
                            localImageUri = null
                            fullErrorMessage = null
                        }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Button(
                        enabled = !isUploadingImage,
                        onClick = {
                            if (websiteName.isNotBlank() && websiteUrl.isNotBlank()) {
                                scope.launch {
                                    isUploadingImage = true
                                    fullErrorMessage = null
                                    try {
                                        var finalImageUrl = websiteImageUrl
                                        val selectedUri = localImageUri
                                        if (selectedUri != null) {
                                            // Step 1: Upload the selected banner image
                                            val publicUrl = viewModel.uploadStudyWebsiteBanner(context, selectedUri)
                                            
                                            // Step 2: Delete old image if editing and replacing image
                                            val targetId = selectedWebsiteId
                                            if (targetId != null) {
                                                val existingWebsite = websites.find { it.id == targetId }
                                                if (existingWebsite != null && existingWebsite.imageUrl.isNotBlank() && existingWebsite.imageUrl != publicUrl) {
                                                    try {
                                                        R2SupabaseManager.deleteFileFromSupabaseStorage(context, existingWebsite.imageUrl, "study-websites")
                                                    } catch (ex: Exception) {
                                                        android.util.Log.e("AdminStudyWebsiteScreen", "Failed to delete old image", ex)
                                                    }
                                                }
                                            }
                                            
                                            finalImageUrl = publicUrl
                                            websiteImageUrl = publicUrl
                                        }

                                        if (finalImageUrl.isBlank()) {
                                            throw Exception("Banner image is required! Please select an image or enter a valid Image URL.")
                                        }

                                        val targetId = selectedWebsiteId
                                        if (targetId != null) {
                                            viewModel.adminUpdateStudyWebsite(
                                                id = targetId,
                                                name = websiteName,
                                                imageUrl = finalImageUrl,
                                                websiteUrl = websiteUrl
                                            )
                                            Toast.makeText(context, "Website updated successfully!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            viewModel.adminAddStudyWebsite(
                                                name = websiteName,
                                                imageUrl = finalImageUrl,
                                                websiteUrl = websiteUrl
                                            )
                                            Toast.makeText(context, "Website added successfully!", Toast.LENGTH_SHORT).show()
                                        }

                                        selectedWebsiteId = null
                                        websiteName = ""
                                        websiteUrl = ""
                                        websiteImageUrl = ""
                                        localImageUri = null
                                        fullErrorMessage = null
                                    } catch (e: Exception) {
                                        val fullErr = e.message ?: e.toString()
                                        fullErrorMessage = fullErr
                                        android.util.Log.e("AdminStudyWebsiteScreen", "Operation failed: $fullErr", e)
                                    } finally {
                                        isUploadingImage = false
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Name and Website URL are required!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        if (isUploadingImage) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Saving...")
                        } else {
                            Text(if (selectedWebsiteId != null) "Update Website" else "Save Website")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Current Study Websites (${websites.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (websites.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No Study Websites configured yet.", color = Color.Gray, fontWeight = FontWeight.Medium)
                    }
                }
            }
        } else {
            items(websites) { website ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (website.imageUrl.isNotBlank()) {
                            val resolvedImg = com.example.service.MediaStorageServiceFactory.getService(context).resolveMediaUrl(website.imageUrl)
                            AsyncImage(
                                model = resolvedImg,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.LightGray),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Language, contentDescription = null, tint = Color.Gray)
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = website.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                text = website.websiteUrl,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(onClick = {
                            selectedWebsiteId = website.id
                            websiteName = website.name
                            websiteUrl = website.websiteUrl
                            websiteImageUrl = website.imageUrl
                            localImageUri = null
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Website", tint = MaterialTheme.colorScheme.primary)
                        }

                        IconButton(onClick = {
                            scope.launch {
                                try {
                                    viewModel.adminDeleteStudyWebsite(website.id, website.imageUrl)
                                    Toast.makeText(context, "Website deleted successfully!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Delete failed: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Website", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

class WebTab(
    val id: String = java.util.UUID.randomUUID().toString(),
    initialUrl: String,
    initialTitle: String = "New Tab"
) {
    var url by mutableStateOf(initialUrl)
    var title by mutableStateOf(initialTitle)
    var favicon by mutableStateOf<Bitmap?>(null)
    var webView by mutableStateOf<WebView?>(null)
    var isLoading by mutableStateOf(true)
    var progress by mutableStateOf(0)
    var hasError by mutableStateOf(false)
    var errorMessage by mutableStateOf("Please check your internet connection or the URL provided by the Academy.")
}

data class WebTabClosedInfo(val url: String, val title: String)

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun StudyWebsiteWebViewScreen(
    url: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("study_website_prefs", Context.MODE_PRIVATE) }
    var isDesktopSite by remember { mutableStateOf(prefs.getBoolean("desktop_site_enabled", false)) }
    var defaultUserAgent by remember { mutableStateOf("") }
    val desktopUserAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    val formattedInitialUrl = remember(url) {
        if (url.isBlank()) "https://google.com"
        else if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("about:")) url
        else "https://$url"
    }
    val initialTab = remember(formattedInitialUrl) { WebTab(initialUrl = formattedInitialUrl, initialTitle = "Study Website") }
    val tabs = remember { mutableStateListOf(initialTab) }
    var activeTabId by remember { mutableStateOf(initialTab.id) }
    val lastClosedTabs = remember { mutableStateListOf<WebTabClosedInfo>() }
    var isTabManagerOpen by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showTabManagerMenu by remember { mutableStateOf(false) }

    var customView by remember { mutableStateOf<android.view.View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val uris = if (data?.clipData != null) {
                val count = data.clipData!!.itemCount
                (0 until count).map { data.clipData!!.getItemAt(it).uri }.toTypedArray()
            } else if (data?.data != null) {
                arrayOf(data.data!!)
            } else {
                null
            }
            filePathCallback?.onReceiveValue(uris)
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    val activeTab = tabs.find { it.id == activeTabId } ?: tabs.firstOrNull() ?: initialTab

    // Create tab webview helper
    fun createWebViewForTab(ctx: Context, tab: WebTab): WebView {
        return WebView(ctx).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            tab.webView = this

            setBackgroundColor(android.graphics.Color.WHITE)
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            setInitialScale(0)

            isVerticalScrollBarEnabled = true
            isHorizontalScrollBarEnabled = true
            isScrollbarFadingEnabled = true
            isNestedScrollingEnabled = true
            overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS

            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(true)
                useWideViewPort = true
                loadWithOverviewMode = isDesktopSite
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = true
                allowContentAccess = true
                setGeolocationEnabled(true)
                cacheMode = WebSettings.LOAD_DEFAULT
                textZoom = 100

                if (isDesktopSite) {
                    userAgentString = desktopUserAgent
                } else {
                    userAgentString = null
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }

                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = true
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = true
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                WebView.setWebContentsDebuggingEnabled(true)
            }

            setDownloadListener { downloadUrl, _, _, _, _ ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                    ctx.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(ctx, "Unable to open download URL: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, pageUrl, favicon)
                    tab.isLoading = true
                    if (pageUrl != null) tab.url = pageUrl
                    if (favicon != null) tab.favicon = favicon
                }

                override fun onPageFinished(view: WebView?, pageUrl: String?) {
                    super.onPageFinished(view, pageUrl)
                    tab.isLoading = false
                    if (pageUrl != null) tab.url = pageUrl
                    if (view?.title?.isNotBlank() == true) {
                        tab.title = view.title!!
                    }
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        val errorCode = error?.errorCode ?: 0
                        if (errorCode == ERROR_HOST_LOOKUP || errorCode == ERROR_CONNECT || errorCode == ERROR_TIMEOUT || errorCode == ERROR_FAILED_SSL_HANDSHAKE) {
                            tab.hasError = true
                            tab.errorMessage = "Unable to connect (${error?.errorCode}): ${error?.description}"
                        }
                    }
                }

                override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                    super.onReceivedHttpError(view, request, errorResponse)
                }

                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    view?.destroy()
                    tab.webView = null
                    tab.hasError = true
                    tab.errorMessage = "WebView process stopped unexpectedly. Please tap Retry to reload."
                    return true
                }

                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                    handler?.proceed()
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val currentUrl = request?.url?.toString() ?: return false
                    if (currentUrl.startsWith("http://") || currentUrl.startsWith("https://") ||
                        currentUrl.startsWith("blob:") || currentUrl.startsWith("data:") || currentUrl.startsWith("about:")) {
                        return false
                    }
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                        ctx.startActivity(intent)
                        return true
                    } catch (e: Exception) {
                        return false
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    tab.progress = newProgress
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    if (!title.isNullOrBlank()) {
                        tab.title = title
                    }
                }

                override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                    super.onReceivedIcon(view, icon)
                    if (icon != null) {
                        tab.favicon = icon
                    }
                }

                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: android.os.Message?
                ): Boolean {
                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                    if (transport != null && view != null) {
                        val tempWebView = WebView(view.context)
                        tempWebView.webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(v: WebView?, req: WebResourceRequest?): Boolean {
                                val urlStr = req?.url?.toString()
                                if (urlStr != null) {
                                    view.loadUrl(urlStr)
                                }
                                return true
                            }
                        }
                        transport.webView = tempWebView
                        resultMsg.sendToTarget()
                    }
                    return true
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    cb: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = cb
                    try {
                        val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }
                        filePickerLauncher.launch(intent)
                    } catch (e: Exception) {
                        filePathCallback = null
                        return false
                    }
                    return true
                }

                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.grant(request.resources)
                }

                override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                    callback?.invoke(origin, true, false)
                }

                override fun onShowCustomView(view: android.view.View?, callback: CustomViewCallback?) {
                    super.onShowCustomView(view, callback)
                    customView = view
                    customViewCallback = callback
                }

                override fun onHideCustomView() {
                    super.onHideCustomView()
                    customView = null
                    customViewCallback?.onCustomViewHidden()
                    customViewCallback = null
                }

                override fun getDefaultVideoPoster(): Bitmap? {
                    return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                }
            }

            loadUrl(tab.url)
        }
    }

    fun createNewTab(targetUrl: String = url) {
        val newTab = WebTab(initialUrl = targetUrl, initialTitle = "New Tab")
        tabs.add(newTab)
        activeTabId = newTab.id
        isTabManagerOpen = false
    }

    fun closeTab(tabToClose: WebTab) {
        lastClosedTabs.add(WebTabClosedInfo(tabToClose.url, tabToClose.title))
        tabToClose.webView?.destroy()
        tabToClose.webView = null
        tabs.remove(tabToClose)

        if (tabs.isEmpty()) {
            val defaultTab = WebTab(initialUrl = url, initialTitle = "Study Website")
            tabs.add(defaultTab)
            activeTabId = defaultTab.id
        } else if (activeTabId == tabToClose.id) {
            activeTabId = tabs.last().id
        }
    }

    fun closeAllTabs() {
        tabs.forEach {
            lastClosedTabs.add(WebTabClosedInfo(it.url, it.title))
            it.webView?.destroy()
            it.webView = null
        }
        tabs.clear()
        val defaultTab = WebTab(initialUrl = url, initialTitle = "Study Website")
        tabs.add(defaultTab)
        activeTabId = defaultTab.id
        isTabManagerOpen = false
    }

    fun restoreLastClosedTab() {
        if (lastClosedTabs.isNotEmpty()) {
            val lastClosed = lastClosedTabs.removeAt(lastClosedTabs.size - 1)
            val restoredTab = WebTab(initialUrl = lastClosed.url, initialTitle = lastClosed.title)
            tabs.add(restoredTab)
            activeTabId = restoredTab.id
            isTabManagerOpen = false
        }
    }

    // Android Hardware Back Button Handling
    androidx.activity.compose.BackHandler(enabled = true) {
        if (customView != null) {
            customViewCallback?.onCustomViewHidden()
            customView = null
        } else if (isTabManagerOpen) {
            isTabManagerOpen = false
        } else {
            val currentWebView = activeTab.webView
            if (currentWebView?.canGoBack() == true) {
                currentWebView.goBack()
            } else if (tabs.size > 1) {
                closeTab(activeTab)
            } else {
                onBack()
            }
        }
    }

    // Immersive Mode
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        if (window != null) {
            androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (window != null) {
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                    show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Fullscreen Video Custom View Overlay
        if (customView != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .zIndex(100f)
            ) {
                AndroidView(
                    factory = { customView!! },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (isTabManagerOpen) {
            // Chrome Tab Manager Overlay View
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF202124))
                    .statusBarsPadding()
                    .zIndex(50f)
            ) {
                // Tab Manager Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${tabs.size} open tabs",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(onClick = { createNewTab() }) {
                        Icon(Icons.Default.Add, contentDescription = "New Tab", tint = Color.White)
                    }

                    Box {
                        IconButton(onClick = { showTabManagerMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Tab Options", tint = Color.White)
                        }

                        DropdownMenu(
                            expanded = showTabManagerMenu,
                            onDismissRequest = { showTabManagerMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("New Tab") },
                                onClick = {
                                    showTabManagerMenu = false
                                    createNewTab()
                                }
                            )
                            if (lastClosedTabs.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Restore Last Closed Tab") },
                                    onClick = {
                                        showTabManagerMenu = false
                                        restoreLastClosedTab()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Close All Tabs") },
                                onClick = {
                                    showTabManagerMenu = false
                                    closeAllTabs()
                                }
                            )
                        }
                    }

                    IconButton(onClick = { isTabManagerOpen = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close Tab Manager", tint = Color.White)
                    }
                }

                Divider(color = Color.DarkGray)

                // Tab Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .weight(1f)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(tabs, key = { it.id }) { tab ->
                        val isSelected = tab.id == activeTab.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clickable {
                                    activeTabId = tab.id
                                    isTabManagerOpen = false
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFF35363A) else Color(0xFF2B2C2F)
                            ),
                            border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Card Header
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF1F2023))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (tab.favicon != null) {
                                        Image(
                                            bitmap = tab.favicon!!.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Language,
                                            contentDescription = null,
                                            tint = Color.Gray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    Text(
                                        text = tab.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )

                                    IconButton(
                                        onClick = { closeTab(tab) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close Tab",
                                            tint = Color.LightGray,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                // Card Content Preview Area
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = try { Uri.parse(tab.url).host ?: tab.url } catch (e: Exception) { tab.url },
                                            fontSize = 11.sp,
                                            color = Color.LightGray,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = tab.title,
                                            fontSize = 12.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Tab Manager Bottom Action Bar
                Surface(
                    color = Color(0xFF292A2D),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (lastClosedTabs.isNotEmpty()) {
                            TextButton(onClick = { restoreLastClosedTab() }) {
                                Icon(Icons.Default.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Restore Closed Tab", color = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        Button(
                            onClick = { createNewTab() },
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New Tab")
                        }
                    }
                }
            }
        } else {
            // Standard Web View Container with Chrome Controls Header
            Column(modifier = Modifier.fillMaxSize()) {
                // Chrome Top Bar
                Surface(
                    color = Color(0xFF202124),
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .zIndex(10f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Back Navigation Button
                        IconButton(
                            onClick = {
                                activeTab.webView?.let {
                                    if (it.canGoBack()) it.goBack() else if (tabs.size > 1) closeTab(activeTab) else onBack()
                                } ?: onBack()
                            }
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Chrome Tab Counter Icon Button
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.5.dp, Color.White, RoundedCornerShape(8.dp))
                                .clickable { isTabManagerOpen = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${tabs.size}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Options Menu
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.White)
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("New Tab") },
                                    onClick = {
                                        showMenu = false
                                        createNewTab()
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Checkbox(
                                                checked = isDesktopSite,
                                                onCheckedChange = null
                                            )
                                            Text("Desktop Site")
                                        }
                                    },
                                    onClick = {
                                        val newState = !isDesktopSite
                                        isDesktopSite = newState
                                        prefs.edit().putBoolean("desktop_site_enabled", newState).apply()
                                        showMenu = false

                                        activeTab.webView?.let { webView ->
                                            webView.settings.apply {
                                                useWideViewPort = true
                                                loadWithOverviewMode = newState
                                                javaScriptEnabled = true
                                                if (newState) {
                                                    userAgentString = desktopUserAgent
                                                } else {
                                                    userAgentString = if (defaultUserAgent.isNotBlank()) defaultUserAgent else null
                                                }
                                            }
                                            activeTab.hasError = false
                                            webView.reload()
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Refresh") },
                                    onClick = {
                                        showMenu = false
                                        activeTab.hasError = false
                                        activeTab.webView?.reload()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Close Tab") },
                                    onClick = {
                                        showMenu = false
                                        closeTab(activeTab)
                                    }
                                )
                                if (lastClosedTabs.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Restore Last Closed Tab") },
                                        onClick = {
                                            showMenu = false
                                            restoreLastClosedTab()
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Close All Tabs") },
                                    onClick = {
                                        showMenu = false
                                        closeAllTabs()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Exit") },
                                    onClick = {
                                        showMenu = false
                                        onBack()
                                    }
                                )
                            }
                        }
                    }

                    // Progress Bar
                    if (activeTab.isLoading && activeTab.progress < 100) {
                        LinearProgressIndicator(
                            progress = { activeTab.progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // WebView Container / Error Screen
                Box(modifier = Modifier.weight(1f)) {
                    if (activeTab.hasError) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = "Offline",
                                modifier = Modifier.size(72.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Unable to load page",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = activeTab.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { closeTab(activeTab) }
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Close Tab")
                                }
                                Button(
                                    onClick = {
                                        activeTab.hasError = false
                                        activeTab.errorMessage = "Please check your internet connection or the URL provided by the Academy."
                                        activeTab.webView?.loadUrl(activeTab.url)
                                    }
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Retry")
                                }
                            }
                        }
                    } else {
                        key(activeTab.id) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    val wv = activeTab.webView ?: createWebViewForTab(ctx, activeTab)
                                    (wv.parent as? android.view.ViewGroup)?.removeView(wv)
                                    wv.layoutParams = android.view.ViewGroup.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    wv
                                },
                                update = { webView ->
                                    webView.layoutParams = android.view.ViewGroup.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
