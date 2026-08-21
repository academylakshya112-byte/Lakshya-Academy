package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppUpdateEntity
import com.example.ui.viewmodel.AcademyViewModel
import com.example.util.UpdateDownloadState

@Composable
fun AppUpdateSystemHandler(
    viewModel: AcademyViewModel,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val updateState by viewModel.appUpdateManager.downloadState.collectAsStateWithLifecycle()
    var isLaterDismissed by remember { mutableStateOf(false) }

    // Re-verify update status on app resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkForUpdates(forceRecheck = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Base App Content
        content()

        when (val state = updateState) {
            is UpdateDownloadState.UpdateAvailable -> {
                if (state.isForce) {
                    // Non-dismissible Force Update Overlay / Screen
                    ForceUpdateOverlay(
                        update = state.update,
                        onUpdateClick = {
                            viewModel.appUpdateManager.openUpdateLink(state.update)
                        }
                    )
                } else if (!isLaterDismissed) {
                    // Premium Optional Update Dialog
                    OptionalUpdateDialog(
                        update = state.update,
                        onUpdateClick = {
                            viewModel.appUpdateManager.openUpdateLink(state.update)
                        },
                        onLaterClick = {
                            isLaterDismissed = true
                        }
                    )
                }
            }
            is UpdateDownloadState.Error -> {
                ErrorDialog(
                    message = state.message,
                    onRetryClick = {
                        viewModel.checkForUpdates(forceRecheck = true)
                    },
                    onDismissClick = {
                        viewModel.appUpdateManager.setIdle()
                        isLaterDismissed = true
                    }
                )
            }
            else -> {
                // Idle or NoUpdate
            }
        }
    }
}

@Composable
fun OptionalUpdateDialog(
    update: AppUpdateEntity,
    onUpdateClick: () -> Unit,
    onLaterClick: () -> Unit
) {
    val context = LocalContext.current
    val currentVersion = remember(context) {
        try {
            com.example.BuildConfig.VERSION_NAME.ifBlank {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
            }
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    Dialog(
        onDismissRequest = { /* Prevent outside dismiss */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Premium Badge Header
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF6366F1),
                                    Color(0xFF8B5CF6),
                                    Color(0xFFEC4899)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.RocketLaunch,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // App Title
                Text(
                    text = "SHADOW X RAHUL",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.5.sp
                )

                Text(
                    text = "New Update Available!",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Version Comparison Box
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Current",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "v$currentVersion",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Latest",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "v${update.safeLatestVersion}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Release Notes Section
                if (update.safeReleaseNotes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(14.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.NewReleases,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "What's New:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = update.safeReleaseNotes,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Link missing warning if applicable
                if (update.safeApkUrl.isBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "⚠️ Update link is currently unavailable.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onLaterClick,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Later")
                    }

                    Button(
                        onClick = onUpdateClick,
                        modifier = Modifier.weight(1.3f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Update Now", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ForceUpdateOverlay(
    update: AppUpdateEntity,
    onUpdateClick: () -> Unit
) {
    // Strictly disable back press for force update
    BackHandler(enabled = true) { /* Block back action */ }

    val context = LocalContext.current
    val currentVersion = remember(context) {
        try {
            com.example.BuildConfig.VERSION_NAME.ifBlank {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
            }
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Alert Icon Badge
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFEF4444),
                                        Color(0xFFF97316)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "SHADOW X RAHUL",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.5.sp
                    )

                    Text(
                        text = "Critical Update Required",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Text(
                        text = "A required system update is available. You must update to the latest version to continue using the application.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Version Badges
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Current Version", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("v$currentVersion", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }

                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Required Version", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                            Text("v${update.safeLatestVersion}", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.error)
                        }
                    }

                    // Release Notes Section
                    if (update.safeReleaseNotes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Release Notes:",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = update.safeReleaseNotes,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Warning if link is missing
                    if (update.safeApkUrl.isBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "⚠️ Update link is missing in database. Please contact support.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Mandatory Update Button
                    Button(
                        onClick = onUpdateClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Launch, contentDescription = null)
                            Text(
                                text = "Update Now via Telegram",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorDialog(
    message: String,
    onRetryClick: () -> Unit,
    onDismissClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
                Text("Update Error", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            }
        },
        text = {
            Text(message, fontSize = 14.sp)
        },
        confirmButton = {
            Button(
                onClick = onRetryClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Retry")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissClick) {
                Text("Close")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

