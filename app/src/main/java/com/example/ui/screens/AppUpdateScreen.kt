package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.AppUpdateEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.AcademyViewModel
import com.example.util.UpdateDownloadState
import java.io.File
import java.util.Locale

@Composable
fun AppUpdateSystemHandler(
    viewModel: AcademyViewModel,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val updateState by viewModel.appUpdateManager.downloadState.collectAsStateWithLifecycle()
    var isLaterDismissed by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Base App content
        content()

        when (val state = updateState) {
            is UpdateDownloadState.UpdateAvailable -> {
                if (state.isForce) {
                    // Full screen force update blocks the entire app
                    ForceUpdateScreen(
                        update = state.update,
                        downloadState = state,
                        onUpdateClick = { viewModel.appUpdateManager.startDownload(state.update) },
                        onCancelClick = { (context as? Activity)?.finish() } // Force exit if cancelled
                    )
                } else if (!isLaterDismissed) {
                    // Optional update dialog on top of the app
                    OptionalUpdateDialog(
                        update = state.update,
                        onUpdateClick = {
                            viewModel.appUpdateManager.startDownload(state.update)
                        },
                        onLaterClick = {
                            isLaterDismissed = true
                        }
                    )
                }
            }
            is UpdateDownloadState.Downloading -> {
                // Determine if this downloading is force or optional
                // We default to displaying download progress
                // If it is a force download we show full screen, otherwise we show a dialog
                val isForce = (updateState as? UpdateDownloadState.UpdateAvailable)?.isForce == true
                if (isForce || !isLaterDismissed) {
                    DownloadProgressOverlay(
                        percentage = state.percentage,
                        speedKbps = state.speedKbps,
                        remainingSeconds = state.remainingSeconds,
                        isForce = isForce,
                        onCancelClick = {
                            viewModel.appUpdateManager.cancelDownload()
                            if (isForce) {
                                (context as? Activity)?.finish()
                            } else {
                                isLaterDismissed = true
                            }
                        }
                    )
                }
            }
            is UpdateDownloadState.ReadyToInstall -> {
                // Show installation prompt
                LaunchedEffect(state.apkFile) {
                    viewModel.appUpdateManager.installApk(state.apkFile)
                }
                InstallationOverlay(
                    onInstallClick = { viewModel.appUpdateManager.installApk(state.apkFile) },
                    onCancelClick = {
                        viewModel.appUpdateManager.cleanup()
                        viewModel.appUpdateManager.setIdle()
                        isLaterDismissed = true
                    }
                )
            }
            is UpdateDownloadState.Error -> {
                // Error screen or dialog
                ErrorDialog(
                    message = state.message,
                    onRetryClick = {
                        viewModel.checkForUpdates()
                    },
                    onDismissClick = {
                        viewModel.appUpdateManager.setIdle()
                        isLaterDismissed = true
                    }
                )
            }
            else -> {
                // Do nothing if Idle or NoUpdate
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
    AlertDialog(
        onDismissRequest = { /* Prevent dismiss on outside touch */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "Update Available",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Lakshya Academy",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Latest Version: ${update.latestVersion}", fontWeight = FontWeight.Medium)
                }

                if (update.releaseNotes.isNotBlank()) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(text = "What's New:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = update.releaseNotes,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdateClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Update Now")
            }
        },
        dismissButton = {
            TextButton(onClick = onLaterClick) {
                Text("Later", color = MaterialTheme.colorScheme.outline)
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun ForceUpdateScreen(
    update: AppUpdateEntity,
    downloadState: UpdateDownloadState,
    onUpdateClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    // Disable Back Button fully during force update
    BackHandler(enabled = true) { /* Do nothing */ }

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            MaterialTheme.colorScheme.background
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBrush)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Logo / Update Icon
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(54.dp)
                )
            }

            Text(
                text = "Lakshya Academy",
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Critical Update Required",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text = "To ensure maximum security, app stability, and access to new premium academic features, you must update to version ${update.latestVersion}.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            if (update.releaseNotes.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Release Notes:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = update.releaseNotes,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onUpdateClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                    Text("Update Now", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            TextButton(
                onClick = onCancelClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Exit Application", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun DownloadProgressOverlay(
    percentage: Int,
    speedKbps: Double,
    remainingSeconds: Long,
    isForce: Boolean,
    onCancelClick: () -> Unit
) {
    if (isForce) {
        BackHandler(enabled = true) { /* Do nothing */ }
    }

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    progress = percentage / 100f,
                    modifier = Modifier.size(72.dp),
                    strokeWidth = 6.dp,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Downloading Update...",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "$percentage%",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )

                LinearProgressIndicator(
                    progress = percentage / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val speedText = if (speedKbps > 1024) {
                        String.format(Locale.getDefault(), "%.1f MB/s", speedKbps / 1024.0)
                    } else {
                        String.format(Locale.getDefault(), "%.0f KB/s", speedKbps)
                    }
                    Text(
                        text = "Speed: $speedText",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val timeText = if (remainingSeconds == 999L) {
                        "Estimating..."
                    } else if (remainingSeconds > 60) {
                        "${remainingSeconds / 60}m ${remainingSeconds % 60}s"
                    } else {
                        "${remainingSeconds}s remaining"
                    }
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedButton(
                    onClick = onCancelClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Cancel Download")
                }
            }
        }
    }
}

@Composable
fun InstallationOverlay(
    onInstallClick: () -> Unit,
    onCancelClick: () -> Unit
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
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp)
                )
                Text("Update Ready", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Text("The update package has been downloaded successfully. Click install to update your app.")
        },
        confirmButton = {
            Button(onClick = onInstallClick) {
                Text("Install Now")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelClick) {
                Text("Cancel", color = MaterialTheme.colorScheme.outline)
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
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
                    modifier = Modifier.size(30.dp)
                )
                Text("Update Failed", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            }
        },
        text = {
            Text(message)
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
        shape = RoundedCornerShape(16.dp)
    )
}
