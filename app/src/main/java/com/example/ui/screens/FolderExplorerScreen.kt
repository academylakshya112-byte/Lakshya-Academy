package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.FileItem
import com.example.data.FolderItem
import com.example.data.FolderModule
import com.example.ui.viewmodel.AcademyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderExplorerDialog(
    module: FolderModule,
    viewModel: AcademyViewModel,
    onDismiss: () -> Unit
) {
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
            color = Color(0xFFF8FAFC)
        ) {
            FolderExplorerContent(
                module = module,
                viewModel = viewModel,
                onClose = onDismiss
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderExplorerContent(
    module: FolderModule,
    viewModel: AcademyViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val isAdmin = (viewModel.currentUser?.role == "ADMIN")

    LaunchedEffect(module) {
        viewModel.refreshFolderModule(module)
    }

    val allFolders by when (module) {
        FolderModule.SYLLABUS -> viewModel.syllabusFolders.collectAsState()
        FolderModule.PREVIOUS_PAPERS -> viewModel.previousPaperFolders.collectAsState()
        FolderModule.FREE_BOOKS -> viewModel.freeBookFolders.collectAsState()
    }

    val allFiles by when (module) {
        FolderModule.SYLLABUS -> viewModel.syllabusFiles.collectAsState()
        FolderModule.PREVIOUS_PAPERS -> viewModel.previousPaperFiles.collectAsState()
        FolderModule.FREE_BOOKS -> viewModel.freeBookFiles.collectAsState()
    }

    // Navigation stack for subfolders
    var currentParentId by remember { mutableStateOf<Long?>(null) }
    val pathStack = remember { mutableStateListOf<FolderItem>() }

    // Search query
    var searchQuery by remember { mutableStateOf("") }

    // Selected file for PDF / Image viewing
    var activePdfFile by remember { mutableStateOf<FileItem?>(null) }
    var activeImageFile by remember { mutableStateOf<FileItem?>(null) }

    // Admin Dialog states
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderToEditCover by remember { mutableStateOf<FolderItem?>(null) }
    var folderToRename by remember { mutableStateOf<FolderItem?>(null) }
    var folderToMove by remember { mutableStateOf<FolderItem?>(null) }
    var folderToDelete by remember { mutableStateOf<FolderItem?>(null) }

    var fileToRename by remember { mutableStateOf<FileItem?>(null) }
    var fileToMove by remember { mutableStateOf<FileItem?>(null) }
    var fileToReplace by remember { mutableStateOf<FileItem?>(null) }
    var fileToDelete by remember { mutableStateOf<FileItem?>(null) }

    // File upload progress state
    var isUploadingFiles by remember { mutableStateOf(false) }
    var uploadProgressText by remember { mutableStateOf("") }
    var uploadProgressFloat by remember { mutableFloatStateOf(0f) }

    // Multi-file picker
    val multiFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty() && currentParentId != null) {
            isUploadingFiles = true
            viewModel.uploadFilesToFolder(
                module = module,
                folderId = currentParentId!!,
                fileUris = uris,
                context = context,
                onProgress = { current, total, progress ->
                    uploadProgressText = "Uploading file $current of $total..."
                    uploadProgressFloat = (current - 1 + progress) / total.toFloat()
                },
                onComplete = { success ->
                    isUploadingFiles = false
                    Toast.makeText(
                        context,
                        if (success) "Files uploaded successfully!" else "Some files failed to upload",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        } else if (currentParentId == null) {
            Toast.makeText(context, "Please open or create a folder first to upload files", Toast.LENGTH_SHORT).show()
        }
    }

    // Single Image Picker for Folder Cover
    val coverImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && folderToEditCover != null) {
            val targetFolder = folderToEditCover!!
            folderToEditCover = null
            Toast.makeText(context, "Uploading folder cover image...", Toast.LENGTH_SHORT).show()
            viewModel.updateFolderCoverImage(module, targetFolder, uri, context) { ok ->
                Toast.makeText(context, if (ok) "Cover image updated!" else "Failed to update cover image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Replace File Launcher
    val replaceFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && fileToReplace != null) {
            val targetFile = fileToReplace!!
            fileToReplace = null
            Toast.makeText(context, "Replacing file...", Toast.LENGTH_SHORT).show()
            viewModel.replaceFile(module, targetFile, uri, context) { ok ->
                Toast.makeText(context, if (ok) "File replaced!" else "Failed to replace file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Filter current folders & files
    val visibleFolders = remember(allFolders, currentParentId, searchQuery) {
        allFolders.filter { folder ->
            val matchParent = folder.parentId == currentParentId
            val matchSearch = searchQuery.isBlank() || folder.name.contains(searchQuery, ignoreCase = true)
            matchParent && matchSearch
        }
    }

    val visibleFiles = remember(allFiles, currentParentId, searchQuery) {
        if (currentParentId == null) emptyList()
        else allFiles.filter { file ->
            val matchFolder = file.folderId == currentParentId
            val matchSearch = searchQuery.isBlank() || file.fileName.contains(searchQuery, ignoreCase = true)
            matchFolder && matchSearch
        }
    }

    val currentFolderImages = remember(visibleFiles) {
        visibleFiles.filter { it.fileType == "image" }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // --- Top Bar ---
        Surface(
            color = Color.White,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = {
                        if (pathStack.isNotEmpty()) {
                            pathStack.removeAt(pathStack.size - 1)
                            currentParentId = pathStack.lastOrNull()?.id
                        } else {
                            onClose()
                        }
                    }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF1E293B)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = module.titleName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFF0F172A)
                        )
                        // Path Breadcrumbs
                        Text(
                            text = if (pathStack.isEmpty()) "Root Folder" else "Home > " + pathStack.joinToString(" > ") { it.name },
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (isAdmin) {
                        IconButton(onClick = { showCreateFolderDialog = true }) {
                            Icon(
                                Icons.Default.CreateNewFolder,
                                contentDescription = "Create Folder",
                                tint = Color(0xFF2563EB)
                            )
                        }
                        if (currentParentId != null) {
                            IconButton(onClick = { multiFilePicker.launch("*/*") }) {
                                Icon(
                                    Icons.Default.UploadFile,
                                    contentDescription = "Upload Files",
                                    tint = Color(0xFF16A34A)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search folders & files...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = Color.Gray)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFF1F5F9),
                        unfocusedContainerColor = Color(0xFFF1F5F9),
                        focusedBorderColor = Color(0xFF3B82F6),
                        unfocusedBorderColor = Color.Transparent
                    ),
                    singleLine = true
                )
            }
        }

        // --- Content Area ---
        Box(modifier = Modifier.weight(1f)) {
            if (visibleFolders.isEmpty() && visibleFiles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color(0xFFCBD5E1)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (currentParentId == null) "No folders created yet" else "This folder is empty",
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF64748B),
                            fontSize = 15.sp
                        )
                        if (isAdmin) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (currentParentId == null) showCreateFolderDialog = true
                                    else multiFilePicker.launch("*/*")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    if (currentParentId == null) Icons.Default.CreateNewFolder else Icons.Default.UploadFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (currentParentId == null) "+ Create First Folder" else "+ Upload Files")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // --- Folders Section ---
                    if (visibleFolders.isNotEmpty()) {
                        item {
                            Text(
                                text = "FOLDERS (${visibleFolders.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF475569),
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }

                        item {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 2000.dp)
                            ) {
                                items(visibleFolders, key = { it.id }) { folder ->
                                    val subFoldersCount = allFolders.count { it.parentId == folder.id }
                                    val filesCount = allFiles.count { it.folderId == folder.id }

                                    FolderCard(
                                        folder = folder,
                                        subFoldersCount = subFoldersCount,
                                        filesCount = filesCount,
                                        isAdmin = isAdmin,
                                        onClick = {
                                            pathStack.add(folder)
                                            currentParentId = folder.id
                                        },
                                        onChangeCover = { folderToEditCover = folder },
                                        onRename = { folderToRename = folder },
                                        onMove = { folderToMove = folder },
                                        onDelete = { folderToDelete = folder }
                                    )
                                }
                            }
                        }
                    }

                    // --- Files Section ---
                    if (visibleFiles.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "FILES (${visibleFiles.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF475569),
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }

                        items(visibleFiles, key = { it.id }) { fileItem ->
                            FileListItem(
                                fileItem = fileItem,
                                isAdmin = isAdmin,
                                onClick = {
                                    if (fileItem.fileType == "image") {
                                        activeImageFile = fileItem
                                    } else {
                                        activePdfFile = fileItem
                                    }
                                },
                                onRename = { fileToRename = fileItem },
                                onReplace = { fileToReplace = fileItem },
                                onMove = { fileToMove = fileItem },
                                onDelete = { fileToDelete = fileItem }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }

            // Floating Action Buttons for Admin
            if (isAdmin && currentParentId != null) {
                FloatingActionButton(
                    onClick = { multiFilePicker.launch("*/*") },
                    containerColor = Color(0xFF2563EB),
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = "Upload")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Upload File", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }

    // --- Dialogs ---

    // 1. Create Folder Dialog
    if (showCreateFolderDialog) {
        CreateFolderDialog(
            parentFolder = pathStack.lastOrNull(),
            onDismiss = { showCreateFolderDialog = false },
            onCreate = { folderName, coverUri ->
                showCreateFolderDialog = false
                Toast.makeText(context, "Creating folder...", Toast.LENGTH_SHORT).show()
                viewModel.createFolder(module, currentParentId, folderName, coverUri, context) { ok ->
                    Toast.makeText(context, if (ok) "Folder created!" else "Failed to create folder", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // 2. Rename Folder Dialog
    if (folderToRename != null) {
        val folder = folderToRename!!
        RenameDialog(
            initialName = folder.name,
            title = "Rename Folder",
            onDismiss = { folderToRename = null },
            onConfirm = { newName ->
                folderToRename = null
                viewModel.renameFolder(module, folder, newName) { ok ->
                    Toast.makeText(context, if (ok) "Folder renamed!" else "Failed to rename folder", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // 3. Move Folder Dialog
    if (folderToMove != null) {
        val folder = folderToMove!!
        val candidateFolders = remember(allFolders) {
            allFolders.filter { it.id != folder.id }
        }
        MoveDialog(
            title = "Move Folder: ${folder.name}",
            candidateFolders = candidateFolders,
            currentParentId = folder.parentId,
            onDismiss = { folderToMove = null },
            onConfirm = { newParentId ->
                folderToMove = null
                viewModel.moveFolder(module, folder, newParentId) { ok ->
                    Toast.makeText(context, if (ok) "Folder moved!" else "Failed to move folder", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // 4. Delete Folder Dialog
    if (folderToDelete != null) {
        val folder = folderToDelete!!
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text("Delete Folder?") },
            text = { Text("Are you sure you want to delete folder '${folder.name}' and all its files?") },
            confirmButton = {
                Button(
                    onClick = {
                        folderToDelete = null
                        viewModel.deleteFolder(module, folder.id) { ok ->
                            Toast.makeText(context, if (ok) "Folder deleted" else "Delete failed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 5. Rename File Dialog
    if (fileToRename != null) {
        val fileItem = fileToRename!!
        RenameDialog(
            initialName = fileItem.fileName,
            title = "Rename File",
            onDismiss = { fileToRename = null },
            onConfirm = { newName ->
                fileToRename = null
                viewModel.renameFile(module, fileItem, newName) { ok ->
                    Toast.makeText(context, if (ok) "File renamed!" else "Failed to rename file", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // 6. Move File Dialog
    if (fileToMove != null) {
        val fileItem = fileToMove!!
        MoveDialog(
            title = "Move File: ${fileItem.fileName}",
            candidateFolders = allFolders,
            currentParentId = fileItem.folderId,
            onDismiss = { fileToMove = null },
            onConfirm = { newFolderId ->
                if (newFolderId != null) {
                    fileToMove = null
                    viewModel.moveFile(module, fileItem, newFolderId) { ok ->
                        Toast.makeText(context, if (ok) "File moved!" else "Failed to move file", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Please select a target folder", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // 7. Delete File Dialog
    if (fileToDelete != null) {
        val fileItem = fileToDelete!!
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Delete File?") },
            text = { Text("Are you sure you want to delete '${fileItem.fileName}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        fileToDelete = null
                        viewModel.deleteFile(module, fileItem.id) { ok ->
                            Toast.makeText(context, if (ok) "File deleted" else "Delete failed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 8. Upload Progress Dialog
    if (isUploadingFiles) {
        Dialog(onDismissRequest = {}) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Color(0xFF2563EB))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        uploadProgressText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { uploadProgressFloat.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF2563EB)
                    )
                }
            }
        }
    }

    // 9. Active PDF Viewer
    if (activePdfFile != null) {
        InAppPdfViewerDialog(
            pdfFile = activePdfFile!!,
            onDismiss = { activePdfFile = null }
        )
    }

    // 10. Active Image Gallery Viewer
    if (activeImageFile != null) {
        InAppImageGalleryDialog(
            initialFile = activeImageFile!!,
            imageFiles = currentFolderImages,
            onDismiss = { activeImageFile = null }
        )
    }
}

// --- Folder Card Composable ---
@Composable
fun FolderCard(
    folder: FolderItem,
    subFoldersCount: Int,
    filesCount: Int,
    isAdmin: Boolean,
    onClick: () -> Unit,
    onChangeCover: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))
                        )
                    )
            ) {
                if (folder.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = folder.imageUrl,
                        contentDescription = folder.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(54.dp),
                            tint = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }

                if (isAdmin) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    ) {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Folder Menu",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("🖼️ Change Cover Image") },
                                onClick = { showMenu = false; onChangeCover() }
                            )
                            DropdownMenuItem(
                                text = { Text("✏️ Rename Folder") },
                                onClick = { showMenu = false; onRename() }
                            )
                            DropdownMenuItem(
                                text = { Text("📁 Move Folder") },
                                onClick = { showMenu = false; onMove() }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("🗑️ Delete Folder", color = Color.Red) },
                                onClick = { showMenu = false; onDelete() }
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = folder.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF0F172A),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$subFoldersCount Folders • $filesCount Files",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}

// --- File Item Composable ---
@Composable
fun FileListItem(
    fileItem: FileItem,
    isAdmin: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onReplace: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (fileItem.fileType == "image") Color(0xFFEFF6FF) else Color(0xFFFEF2F2)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (fileItem.fileType == "image") {
                    AsyncImage(
                        model = fileItem.storageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = "PDF",
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileItem.fileName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (fileItem.fileType == "image") Color(0xFFDBEAFE) else Color(0xFFFEE2E2),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = fileItem.fileType.uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (fileItem.fileType == "image") Color(0xFF1D4ED8) else Color(0xFFB91C1C),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    if (fileItem.fileSize.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(fileItem.fileSize, fontSize = 11.sp, color = Color(0xFF64748B))
                    }
                }
            }

            if (isAdmin) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "File Options", tint = Color.Gray)
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("✏️ Rename File") },
                            onClick = { showMenu = false; onRename() }
                        )
                        DropdownMenuItem(
                            text = { Text("🔄 Replace File") },
                            onClick = { showMenu = false; onReplace() }
                        )
                        DropdownMenuItem(
                            text = { Text("📁 Move File") },
                            onClick = { showMenu = false; onMove() }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("🗑️ Delete File", color = Color.Red) },
                            onClick = { showMenu = false; onDelete() }
                        )
                    }
                }
            } else {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Open",
                    tint = Color(0xFF94A3B8)
                )
            }
        }
    }
}

// --- Dialog: Create Folder ---
@Composable
fun CreateFolderDialog(
    parentFolder: FolderItem?,
    onDismiss: () -> Unit,
    onCreate: (folderName: String, coverUri: Uri?) -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedImageUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (parentFolder != null) "Create Subfolder inside '${parentFolder.name}'" else "Create New Folder",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { imagePicker.launch("image/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9), contentColor = Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (selectedImageUri != null) "Image Selected (Tap to change)" else "Select Folder Cover Image (Optional)")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (folderName.isNotBlank()) {
                        onCreate(folderName, selectedImageUri)
                    }
                },
                enabled = folderName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// --- Dialog: Rename ---
@Composable
fun RenameDialog(
    initialName: String,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (newName: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("New Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// --- Dialog: Move Item ---
@Composable
fun MoveDialog(
    title: String,
    candidateFolders: List<FolderItem>,
    currentParentId: Long?,
    onDismiss: () -> Unit,
    onConfirm: (targetParentId: Long?) -> Unit
) {
    var selectedTargetId by remember { mutableStateOf<Long?>(currentParentId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Select Target Parent Folder:", fontSize = 13.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTargetId = null }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = (selectedTargetId == null),
                                onClick = { selectedTargetId = null }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Root Directory (No parent)", fontWeight = FontWeight.Bold)
                        }
                    }

                    items(candidateFolders) { folder ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTargetId = folder.id }
                                .padding(vertical = 6.dp)
                        ) {
                            RadioButton(
                                selected = (selectedTargetId == folder.id),
                                onClick = { selectedTargetId = folder.id }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(folder.name, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedTargetId) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Text("Move Here")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// --- In-App PDF Viewer Dialog ---
@Composable
fun InAppPdfViewerDialog(
    pdfFile: FileItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var tempLocalFile by remember { mutableStateOf<File?>(null) }
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var fileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Pinch-to-zoom & Pan state
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

    LaunchedEffect(pdfFile) {
        withContext(Dispatchers.IO) {
            try {
                val url = pdfFile.storageUrl
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    val client = okhttp3.OkHttpClient()
                    val req = okhttp3.Request.Builder().url(url).build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful && resp.body != null) {
                            val cacheFile = File(context.cacheDir, "pdf_${pdfFile.id}_${System.currentTimeMillis()}.pdf")
                            resp.body!!.byteStream().use { input ->
                                FileOutputStream(cacheFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            tempLocalFile = cacheFile
                        } else {
                            errorMsg = "Failed to download PDF from storage."
                        }
                    }
                } else {
                    val uri = Uri.parse(url)
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    fileDescriptor = pfd
                }

                val pfdToUse = fileDescriptor ?: tempLocalFile?.let { ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY) }
                if (pfdToUse != null) {
                    fileDescriptor = pfdToUse
                    pdfRenderer = PdfRenderer(pfdToUse)
                    pageCount = pdfRenderer?.pageCount ?: 0
                } else if (errorMsg == null) {
                    errorMsg = "Could not open PDF file."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                errorMsg = "Error rendering PDF: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                pdfRenderer?.close()
                fileDescriptor?.close()
                tempLocalFile?.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header
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
                        Text(
                            pdfFile.fileName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (pageCount > 0) {
                            Text("$pageCount Pages • Pinch to zoom", fontSize = 11.sp, color = Color.Gray)
                        }
                    }

                    IconButton(onClick = {
                        try {
                            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                            val request = android.app.DownloadManager.Request(Uri.parse(pdfFile.storageUrl))
                                .setTitle(pdfFile.fileName)
                                .setDescription("Downloading PDF")
                                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, pdfFile.fileName)
                            dm.enqueue(request)
                            Toast.makeText(context, "Download started!", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "Download", tint = Color(0xFF2563EB))
                    }
                }
                HorizontalDivider()

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF2563EB))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Loading PDF...", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                } else if (errorMsg != null) {
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
                                SinglePdfPageItem(pdfRenderer = pdfRenderer, pageIndex = index)
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
                }
            }
        }
    }
}

@Composable
fun SinglePdfPageItem(pdfRenderer: PdfRenderer?, pageIndex: Int) {
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
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "PDF Page ${pageIndex + 1}",
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

// --- In-App Full-Screen Image Gallery Viewer Dialog ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InAppImageGalleryDialog(
    initialFile: FileItem,
    imageFiles: List<FileItem>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val filesList = remember(imageFiles, initialFile) {
        if (imageFiles.none { it.id == initialFile.id }) listOf(initialFile) else imageFiles
    }
    val initialIndex = remember(filesList, initialFile) {
        val idx = filesList.indexOfFirst { it.id == initialFile.id }
        if (idx != -1) idx else 0
    }

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = initialIndex,
        pageCount = { filesList.size }
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        val currentFile = filesList.getOrNull(pagerState.currentPage)
                        Text(
                            text = currentFile?.fileName ?: "Image Preview",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Image ${pagerState.currentPage + 1} of ${filesList.size}",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                    }

                    IconButton(onClick = {
                        val currentFile = filesList.getOrNull(pagerState.currentPage)
                        if (currentFile != null) {
                            try {
                                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                val request = android.app.DownloadManager.Request(Uri.parse(currentFile.storageUrl))
                                    .setTitle(currentFile.fileName)
                                    .setDescription("Downloading Image")
                                    .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                    .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, currentFile.fileName)
                                dm.enqueue(request)
                                Toast.makeText(context, "Download started!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "Download Image", tint = Color.White)
                    }
                }

                // Image Pager with pinch-to-zoom
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { page ->
                    val file = filesList[page]
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

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .transformable(state = transformState)
                            .clickable(enabled = scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = file.storageUrl,
                            contentDescription = file.fileName,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                ),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }
}
