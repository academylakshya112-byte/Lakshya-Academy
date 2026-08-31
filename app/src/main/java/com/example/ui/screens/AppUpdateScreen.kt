package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    val currentVersion = remember(context) {
        try {
            com.example.BuildConfig.VERSION_NAME.ifBlank {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
            }
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    var isDownloading by remember { mutableStateOf(false) }
    var currentProgress by remember { mutableStateOf(0.75f) }

    // Define Shimmer for "SHADOW X RAHUL"
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF3B82F6),
            Color(0xFF8B5CF6),
            Color(0xFFEC4899),
            Color(0xFF3B82F6)
        ),
        start = Offset(shimmerOffset, 0f),
        end = Offset(shimmerOffset + 250f, 250f)
    )

    Dialog(
        onDismissRequest = { /* Prevent outside dismiss */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0F121D), // ultra premium dark background matching user's image
            border = BorderStroke(1.dp, Color(0xFF1E293B)),
            tonalElevation = 8.dp,
            shadowElevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Top Right Close Icon
                Box(
                    modifier = Modifier
                        .padding(16.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B).copy(alpha = 0.6f))
                        .clickable(enabled = !isDownloading) { onLaterClick() }
                        .align(Alignment.TopEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Dialog",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Pulling Man & Gear Custom Canvas Animation
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        PullingManGearAnimation(progress = currentProgress)
                        
                        // Centered inside the gear
                        Text(
                            text = "${(currentProgress * 100).toInt()}%",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            modifier = Modifier.align(BiasAlignment(horizontalBias = 0.48f, verticalBias = 0f))
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Animated brand header
                    Text(
                        text = "SHADOW X RAHUL",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        letterSpacing = 2.5.sp,
                        style = androidx.compose.ui.text.TextStyle(brush = shimmerBrush),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    // Dialog title
                    Text(
                        text = "App Update",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Subtitle
                    Text(
                        text = "A new version is here with exciting features and performance improvements.",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // "What's New" Container Box
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B2C)),
                        border = BorderStroke(1.dp, Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF2563EB)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "What's New",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                val bulletPoints = remember(update.safeReleaseNotes) {
                                    update.safeReleaseNotes.lines()
                                        .map { it.trim().removePrefix("-").removePrefix("•").trim() }
                                        .filter { Point -> Point.isNotBlank() }
                                        .ifEmpty {
                                            listOf(
                                                "New & improved design",
                                                "Faster performance",
                                                "Bug fixes and stability improvements"
                                            )
                                        }
                                }

                                bulletPoints.take(3).forEach { point ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 1.5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color(0xFF3B82F6),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = point,
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Progress Details
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isDownloading) "Updating..." else "Ready to update...",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${(currentProgress * 100).toInt()}%",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Glowing Progress Bar Track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(currentProgress)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF3B82F6), Color(0xFF6366F1), Color(0xFFEC4899))
                                    )
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Primary Action Button (Gradient)
                    Button(
                        onClick = {
                            if (!isDownloading) {
                                isDownloading = true
                                scope.launch {
                                    // Start download simulation anim from 75% to 100%
                                    val startVal = currentProgress
                                    val steps = 25
                                    for (i in 1..steps) {
                                        delay(80)
                                        currentProgress = startVal + (1f - startVal) * (i.toFloat() / steps)
                                    }
                                    onUpdateClick()
                                    isDownloading = false
                                }
                            }
                        },
                        enabled = !isDownloading,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            disabledContainerColor = Color(0xFF1E293B).copy(alpha = 0.5f)
                        ),
                        contentPadding = PaddingValues(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (isDownloading) {
                                        Brush.horizontalGradient(listOf(Color(0xFF1E293B), Color(0xFF1E293B)))
                                    } else {
                                        Brush.horizontalGradient(listOf(Color(0xFF2563EB), Color(0xFF4F46E5)))
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isDownloading) "Updating..." else "Updating...",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Later/Cancel Text Button
                    TextButton(
                        onClick = onLaterClick,
                        enabled = !isDownloading
                    ) {
                        Text(
                            text = "Cancel",
                            color = Color(0xFF64748B),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
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
    val scope = rememberCoroutineScope()
    val currentVersion = remember(context) {
        try {
            com.example.BuildConfig.VERSION_NAME.ifBlank {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
            }
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    var isDownloading by remember { mutableStateOf(false) }
    var currentProgress by remember { mutableStateOf(0.75f) }

    // Define Shimmer for "SHADOW X RAHUL"
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer_force")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffsetForce"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFFEF4444),
            Color(0xFFF97316),
            Color(0xFFEC4899),
            Color(0xFFEF4444)
        ),
        start = Offset(shimmerOffset, 0f),
        end = Offset(shimmerOffset + 250f, 250f)
    )

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
                .background(Color.Black.copy(alpha = 0.94f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF0F121D), // consistent premium midnight dark theme
                border = BorderStroke(1.5.dp, Color(0xFFEF4444).copy(alpha = 0.5f)), // glowing red border for critical update
                tonalElevation = 10.dp,
                shadowElevation = 24.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Pulling Man & Gear Custom Canvas Animation
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        PullingManGearAnimation(progress = currentProgress)
                        
                        // Centered inside the gear
                        Text(
                            text = "${(currentProgress * 100).toInt()}%",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            modifier = Modifier.align(BiasAlignment(horizontalBias = 0.48f, verticalBias = 0f))
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Animated brand header
                    Text(
                        text = "SHADOW X RAHUL",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        letterSpacing = 2.5.sp,
                        style = androidx.compose.ui.text.TextStyle(brush = shimmerBrush),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    // Dialog title
                    Text(
                        text = "Critical Update Required",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Subtitle
                    Text(
                        text = "A required system update is available. You must update to the latest version to continue using the application.",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // "What's New" Container Box
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B2C)),
                        border = BorderStroke(1.dp, Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFEF4444)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "What's New",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                val bulletPoints = remember(update.safeReleaseNotes) {
                                    update.safeReleaseNotes.lines()
                                        .map { Point -> Point.trim().removePrefix("-").removePrefix("•").trim() }
                                        .filter { Point -> Point.isNotBlank() }
                                        .ifEmpty {
                                            listOf(
                                                "Critical security enhancements",
                                                "Important bug fixes & stability",
                                                "Optimized database sync engine"
                                            )
                                        }
                                }

                                bulletPoints.take(3).forEach { point ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 1.5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = point,
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Progress Details
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isDownloading) "Updating..." else "Mandatory update setup...",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${(currentProgress * 100).toInt()}%",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Glowing Progress Bar Track (Red gradient for force update)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(currentProgress)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFFEF4444), Color(0xFFF97316), Color(0xFFEC4899))
                                    )
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Primary Action Button (Gradient)
                    Button(
                        onClick = {
                            if (!isDownloading) {
                                isDownloading = true
                                scope.launch {
                                    // Start download simulation anim from 75% to 100%
                                    val startVal = currentProgress
                                    val steps = 25
                                    for (i in 1..steps) {
                                        delay(80)
                                        currentProgress = startVal + (1f - startVal) * (i.toFloat() / steps)
                                    }
                                    onUpdateClick()
                                    isDownloading = false
                                }
                            }
                        },
                        enabled = !isDownloading,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            disabledContainerColor = Color(0xFF1E293B).copy(alpha = 0.5f)
                        ),
                        contentPadding = PaddingValues(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (isDownloading) {
                                        Brush.horizontalGradient(listOf(Color(0xFF1E293B), Color(0xFF1E293B)))
                                    } else {
                                        Brush.horizontalGradient(listOf(Color(0xFFEF4444), Color(0xFFF97316)))
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Update Now via Telegram",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    if (update.safeApkUrl.isBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "⚠️ Update link is missing. Please contact support.",
                            fontSize = 11.sp,
                            color = Color(0xFFEF4444),
                            textAlign = TextAlign.Center
                        )
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

@Composable
fun PullingManGearAnimation(
    modifier: Modifier = Modifier,
    progress: Float = 0.75f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulling")
    
    // Rhythmic pulling sway for the man
    val pullSway by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pullSway"
    )
    
    // Continuous rotation for the gear
    val gearRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gearRotation"
    )
    
    // Energy flow phase for the glowing rope
    val energyFlow by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "energyFlow"
    )

    val sparkPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkPulse"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
    ) {
        val width = size.width
        val height = size.height
        
        // Draw elegant futuristic grid/gradient background
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF070913), Color(0xFF101424))
            )
        )
        
        // Let's define centers
        val gearX = width * 0.74f
        val gearY = height * 0.5f
        val gearRadius = height * 0.32f
        
        val manX = width * 0.24f + pullSway * 1.5f
        val manY = height * 0.55f // slightly lower
        
        // --- DRAW SPARKLES/EMBERS AROUND THE GEAR ---
        val sparkCount = 8
        for (i in 0 until sparkCount) {
            val angleDeg = (i * (360f / sparkCount) + gearRotation * 0.4f) % 360f
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val sparkDistance = gearRadius * (1.18f + 0.12f * kotlin.math.sin(angleRad * 2 + sparkPulse))
            val sX = gearX + (sparkDistance * kotlin.math.cos(angleRad)).toFloat()
            val sY = gearY + (sparkDistance * kotlin.math.sin(angleRad)).toFloat()
            drawCircle(
                color = Color(0xFFF59E0B).copy(alpha = 0.55f * sparkPulse),
                radius = 3f + (i % 3),
                center = Offset(sX, sY)
            )
        }

        // --- DRAW THE GLOWING ROPE/CABLE ---
        val ropeStart = Offset(manX + 18f, manY - 20f)
        val ropeEnd = Offset(gearX - 15f, gearY)
        val controlPoint = Offset(
            (ropeStart.x + ropeEnd.x) / 2f,
            (ropeStart.y + ropeEnd.y) / 2f + 20f + pullSway * 0.8f // sags slightly
        )
        
        val ropePath = Path().apply {
            moveTo(ropeStart.x, ropeStart.y)
            quadraticTo(controlPoint.x, controlPoint.y, ropeEnd.x, ropeEnd.y)
        }
        
        // Draw glowing layers of the rope
        // Layer 1: Broad outer cyan glow
        drawPath(
            path = ropePath,
            color = Color(0xFF3B82F6).copy(alpha = 0.25f),
            style = Stroke(
                width = 12f,
                cap = StrokeCap.Round
            )
        )
        // Layer 2: Core blue/purple energy
        drawPath(
            path = ropePath,
            color = Color(0xFF6366F1).copy(alpha = 0.6f),
            style = Stroke(
                width = 6f,
                cap = StrokeCap.Round
            )
        )
        // Layer 3: Central white/cyan line
        drawPath(
            path = ropePath,
            color = Color(0xFFE0F2FE),
            style = Stroke(
                width = 2.5f,
                cap = StrokeCap.Round
            )
        )
        
        // Draw energy flow particles travelling along the rope
        val steps = 15
        for (i in 0..steps) {
            val t = (i.toFloat() / steps + energyFlow) % 1.0f
            val u = 1f - t
            val pX = u * u * ropeStart.x + 2 * u * t * controlPoint.x + t * t * ropeEnd.x
            val pY = u * u * ropeStart.y + 2 * u * t * controlPoint.y + t * t * ropeEnd.y
            
            drawCircle(
                color = Color(0xFF60A5FA).copy(alpha = 0.8f * (1f - t)),
                radius = 4f * (1f - t * 0.5f),
                center = Offset(pX, pY)
            )
        }

        // --- DRAW THE ROTATING GEAR ---
        val toothCount = 10
        val toothDepth = 15f
        val innerGearRadius = gearRadius - toothDepth
        val gearPath = Path()
        
        for (i in 0 until toothCount) {
            val angleDeg = i * (360f / toothCount) + gearRotation
            val angleRad = Math.toRadians(angleDeg.toDouble())
            
            val nextAngleDeg = (i + 1) * (360f / toothCount) + gearRotation
            val halfToothSpan = (360f / toothCount) * 0.45f
            
            val t1Rad = Math.toRadians((angleDeg - halfToothSpan).toDouble())
            val t2Rad = Math.toRadians((angleDeg - halfToothSpan * 0.55).toDouble())
            val t3Rad = Math.toRadians((angleDeg + halfToothSpan * 0.55).toDouble())
            val t4Rad = Math.toRadians((angleDeg + halfToothSpan).toDouble())
            
            val p1X = gearX + (innerGearRadius * kotlin.math.cos(t1Rad)).toFloat()
            val p1Y = gearY + (innerGearRadius * kotlin.math.sin(t1Rad)).toFloat()
            
            val p2X = gearX + (gearRadius * kotlin.math.cos(t2Rad)).toFloat()
            val p2Y = gearY + (gearRadius * kotlin.math.sin(t2Rad)).toFloat()
            
            val p3X = gearX + (gearRadius * kotlin.math.cos(t3Rad)).toFloat()
            val p3Y = gearY + (gearRadius * kotlin.math.sin(t3Rad)).toFloat()
            
            val p4X = gearX + (innerGearRadius * kotlin.math.cos(t4Rad)).toFloat()
            val p4Y = gearY + (innerGearRadius * kotlin.math.sin(t4Rad)).toFloat()
            
            if (i == 0) {
                gearPath.moveTo(p1X, p1Y)
            } else {
                gearPath.lineTo(p1X, p1Y)
            }
            gearPath.lineTo(p2X, p2Y)
            gearPath.lineTo(p3X, p3Y)
            gearPath.lineTo(p4X, p4Y)
        }
        gearPath.close()
        
        // Draw outer gear silhouette with a deep professional gradient
        drawPath(
            path = gearPath,
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF2563EB), Color(0xFF1E3A8A), Color(0xFF0F172A)),
                center = Offset(gearX, gearY),
                radius = gearRadius
            )
        )
        
        // Draw gear outline
        drawPath(
            path = gearPath,
            color = Color(0xFF3B82F6).copy(alpha = 0.5f),
            style = Stroke(width = 3f)
        )

        // Draw inner track circle
        drawCircle(
            color = Color(0xFF0A0C16),
            radius = innerGearRadius * 0.75f,
            center = Offset(gearX, gearY)
        )
        
        // Draw progress circular arc track
        drawCircle(
            color = Color(0xFF13172E),
            radius = innerGearRadius * 0.62f,
            center = Offset(gearX, gearY),
            style = Stroke(width = 8f)
        )
        
        // Draw progress circular arc filled
        drawArc(
            color = Color(0xFF3B82F6),
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            topLeft = Offset(gearX - innerGearRadius * 0.62f, gearY - innerGearRadius * 0.62f),
            size = Size(innerGearRadius * 1.24f, innerGearRadius * 1.24f),
            style = Stroke(
                width = 8f,
                cap = StrokeCap.Round
            )
        )

        // Draw glowing inner hub circle
        drawCircle(
            color = Color(0xFF1D4ED8).copy(alpha = 0.35f),
            radius = innerGearRadius * 0.42f,
            center = Offset(gearX, gearY)
        )

        // --- DRAW THE PULLING MAN ---
        // Stylized vector lines of a man leaning back and pulling
        val headRadius = 11f
        val headCenter = Offset(manX - 22f, manY - 68f)
        
        val shoulder = Offset(manX - 18f, manY - 54f)
        val hip = Offset(manX - 42f, manY - 14f)
        
        val leftKnee = Offset(manX - 66f, manY - 10f)
        val leftFoot = Offset(manX - 82f, manY + 16f)
        
        val rightKnee = Offset(manX - 24f, manY)
        val rightFoot = Offset(manX - 4f, manY + 18f)
        
        val hand = Offset(manX + 15f, manY - 21f)
        
        drawCircle(
            color = Color.White,
            radius = headRadius,
            center = headCenter
        )
        drawCircle(
            color = Color(0xFF60A5FA).copy(alpha = 0.5f),
            radius = headRadius + 4f,
            center = headCenter,
            style = Stroke(width = 2f)
        )
        
        val bodyLinePath = Path().apply {
            moveTo(shoulder.x, shoulder.y)
            lineTo(hip.x, hip.y)
            lineTo(leftKnee.x, leftKnee.y)
            lineTo(leftFoot.x, leftFoot.y)
            moveTo(hip.x, hip.y)
            lineTo(rightKnee.x, rightKnee.y)
            lineTo(rightFoot.x, rightFoot.y)
            moveTo(shoulder.x, shoulder.y)
            lineTo(hand.x, hand.y)
        }
        
        drawPath(
            path = bodyLinePath,
            color = Color(0xFF60A5FA).copy(alpha = 0.4f),
            style = Stroke(
                width = 10f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        drawPath(
            path = bodyLinePath,
            color = Color.White,
            style = Stroke(
                width = 3.5f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        
        drawCircle(
            color = Color(0xFF3B82F6),
            radius = 6f,
            center = hand
        )
        drawCircle(
            color = Color.White,
            radius = 3f,
            center = hand
        )
    }
}

