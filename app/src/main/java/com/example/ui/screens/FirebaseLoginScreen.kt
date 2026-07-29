package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class AppUser(
    val displayName: String?,
    val email: String?,
    val uid: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirebaseLoginScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Safely check if Firebase has been initialized
    val isFirebaseAvailable = remember {
        try {
            FirebaseApp.getInstance()
            true
        } catch (e: Exception) {
            false
        }
    }

    var user by remember {
        mutableStateOf<AppUser?>(
            if (isFirebaseAvailable) {
                try {
                    FirebaseAuth.getInstance().currentUser?.let {
                        AppUser(it.displayName, it.email, it.uid)
                    }
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        )
    }

    var statusMessage by remember {
        mutableStateOf(
            if (isFirebaseAvailable) "Ready to authenticate via Google Services"
            else "Running in Offline Simulation Mode (Firebase is not initialized)"
        )
    }

    var showSimulationDialog by remember { mutableStateOf(false) }
    var simName by remember { mutableStateOf("Lakshya Student") }
    var simEmail by remember { mutableStateOf("student@lakshya.com") }

    // GSO client (only created if Firebase config/Google Services is potentially active)
    val googleSignInClient = remember {
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken("YOUR_WEB_CLIENT_ID") // Must be replaced with real client ID
                .requestEmail()
                .build()
            GoogleSignIn.getClient(context, gso)
        } catch (e: Exception) {
            null
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && googleSignInClient != null) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                scope.launch {
                    try {
                        if (isFirebaseAvailable) {
                            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                            val authResult = FirebaseAuth.getInstance().signInWithCredential(credential).await()
                            authResult.user?.let { u ->
                                user = AppUser(u.displayName, u.email, u.uid)
                                statusMessage = "Signed in as ${u.displayName}"
                                
                                // Save user to Firestore
                                val db = FirebaseFirestore.getInstance()
                                val userData = hashMapOf(
                                    "name" to u.displayName,
                                    "email" to u.email,
                                    "uid" to u.uid
                                )
                                db.collection("users").document(u.uid).set(userData).await()
                            }
                        }
                    } catch (e: Exception) {
                        statusMessage = "Firebase Auth failed: ${e.message}"
                    }
                }
            } catch (e: ApiException) {
                statusMessage = "Google sign in failed: ${e.message}"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Authentication Portal") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Notice Card for Firebase Status
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isFirebaseAvailable) Color(0xFFF0FDF4) else Color(0xFFFEF3C7)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Config status",
                        tint = if (isFirebaseAvailable) Color(0xFF16A34A) else Color(0xFFD97706)
                    )
                    Column {
                        Text(
                            text = if (isFirebaseAvailable) "Firebase Active" else "Firebase Offline Mode",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isFirebaseAvailable) Color(0xFF166534) else Color(0xFF92400E)
                        )
                        Text(
                            text = if (isFirebaseAvailable) "Connected successfully to Google Cloud Services." 
                                   else "No google-services.json detected. App running safely in full sandbox simulation mode.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isFirebaseAvailable) Color(0xFF15803D) else Color(0xFFB45309)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.2f))

            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(100.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = statusMessage,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (user == null) {
                Button(
                    onClick = {
                        if (isFirebaseAvailable && googleSignInClient != null) {
                            launcher.launch(googleSignInClient.signInIntent)
                        } else {
                            // Show simulation configuration dialog
                            showSimulationDialog = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(25.dp)
                ) {
                    Text(if (isFirebaseAvailable) "Sign in with Google" else "Simulate Google Auth")
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Profile Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Name: ${user?.displayName ?: "N/A"}")
                        Text("Email: ${user?.email ?: "N/A"}")
                        Text("UID: ${user?.uid ?: "N/A"}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (isFirebaseAvailable && googleSignInClient != null) {
                            try {
                                FirebaseAuth.getInstance().signOut()
                                googleSignInClient.signOut()
                            } catch (e: Exception) {
                                // Safe catch
                            }
                        }
                        user = null
                        statusMessage = if (isFirebaseAvailable) "Signed out" else "Simulated logout complete"
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sign out")
                }
            }

            Spacer(modifier = Modifier.weight(0.8f))

            Text(
                text = "Note: Real production integration requires a valid google-services.json from Firebase Console inside the 'app' folder.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }

    // Google Sign-In Simulator Dialog
    if (showSimulationDialog) {
        AlertDialog(
            onDismissRequest = { showSimulationDialog = false },
            title = { Text("Simulated Google Sign-In") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Select a pre-configured Google account to test the portal features, or enter custom details:")
                    
                    // Preset Quick-Select Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SuggestionChip(
                            onClick = {
                                simName = "Lakshya Student"
                                simEmail = "student@lakshya.com"
                            },
                            label = { Text("Student") }
                        )
                        SuggestionChip(
                            onClick = {
                                simName = "Dr. Lakshya Instructor"
                                simEmail = "instructor@lakshya.com"
                            },
                            label = { Text("Instructor") }
                        )
                    }

                    OutlinedTextField(
                        value = simName,
                        onValueChange = { simName = it },
                        label = { Text("Display Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = simEmail,
                        onValueChange = { simEmail = it },
                        label = { Text("Email Address") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        user = AppUser(
                            displayName = simName,
                            email = simEmail,
                            uid = "simulated_user_id_${simName.hashCode()}"
                        )
                        statusMessage = "Signed in as $simName (Simulation)"
                        showSimulationDialog = false
                    }
                ) {
                    Text("Sign In")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSimulationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
