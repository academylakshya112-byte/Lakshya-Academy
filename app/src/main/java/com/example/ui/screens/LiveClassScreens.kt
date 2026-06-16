package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.LiveClassEntity
import com.example.ui.viewmodel.AcademyViewModel

@Composable
fun LiveClassScreen(viewModel: AcademyViewModel) {
    val liveClasses by viewModel.allLiveClasses.collectAsStateWithLifecycle(emptyList())

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Live Classes", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))
        }
        items(liveClasses) { liveClass ->
            LiveClassItem(liveClass)
        }
    }
}

@Composable
fun LiveClassItem(liveClass: LiveClassEntity) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (liveClass.isLive) {
                    Icon(Icons.Default.LiveTv, contentDescription = null, tint = Color.Red)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("LIVE", color = Color.Red, fontWeight = FontWeight.Bold)
                } else {
                    Text("Scheduled", color = Color.Gray)
                }
            }
            Text(liveClass.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text("Subject: ${liveClass.subject} | Teacher: ${liveClass.teacherName}")
            val context = androidx.compose.ui.platform.LocalContext.current
            Button(onClick = { 
                if (liveClass.recordingUri.startsWith("http")) {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(liveClass.recordingUri))
                    context.startActivity(intent)
                }
            }, modifier = Modifier.padding(top = 8.dp)) {
                Text(if (liveClass.isLive) "Join Now" else "Set Reminder")
            }
        }
    }
}
