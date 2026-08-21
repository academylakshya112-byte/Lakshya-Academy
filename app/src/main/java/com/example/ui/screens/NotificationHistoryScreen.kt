package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.NotificationEntity
import com.example.ui.theme.BrandBluePrimary
import com.example.ui.viewmodel.AcademyViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationHistoryScreen(
    viewModel: AcademyViewModel,
    onBack: () -> Unit
) {
    com.example.util.TrackStudyModule(com.example.util.StudyTracker.MODULE_EXAM_ALERTS)

    val context = LocalContext.current
    val notifications by viewModel.allNotifications.collectAsStateWithLifecycle()
    val prefs = remember { context.getSharedPreferences("alert_system_prefs", Context.MODE_PRIVATE) }
    
    // Store read IDs
    var lastReadId by remember { mutableIntStateOf(prefs.getInt("last_read_notification_id", 0)) }

    // When opening history page, mark latest notification as read
    LaunchedEffect(notifications) {
        val maxId = notifications.maxOfOrNull { it.id } ?: 0
        if (maxId > lastReadId) {
            lastReadId = maxId
            prefs.edit().putInt("last_read_notification_id", maxId).apply()
        }
    }

    val sortedNotifications = remember(notifications) {
        notifications.sortedByDescending { it.timestamp }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Notification History",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
        ) {
            if (sortedNotifications.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(BrandBluePrimary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = BrandBluePrimary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Notifications Yet",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Official exam alerts and important announcements will appear here.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sortedNotifications, key = { it.id }) { notif ->
                        val isUnread = notif.id > lastReadId
                        NotificationItemCard(
                            notification = notif,
                            isUnread = isUnread,
                            onClick = {
                                if (notif.id > lastReadId) {
                                    lastReadId = notif.id
                                    prefs.edit().putInt("last_read_notification_id", notif.id).apply()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationItemCard(
    notification: NotificationEntity,
    isUnread: Boolean,
    onClick: () -> Unit
) {
    val formattedTime = remember(notification.timestamp) {
        try {
            val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            sdf.format(Date(notification.timestamp))
        } catch (e: Exception) {
            ""
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnread) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isUnread) 3.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isUnread) Brush.linearGradient(
                            listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                        )
                        else Brush.linearGradient(
                            listOf(Color(0xFF94A3B8), Color(0xFF64748B))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Campaign,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notification.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isUnread) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFEF4444)
                        ) {
                            Text(
                                text = "NEW",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = notification.message,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (formattedTime.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formattedTime,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}
