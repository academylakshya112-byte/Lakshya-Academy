package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.theme.BrandBluePrimary
import com.example.ui.theme.BrandBlueSecondary
import com.example.ui.viewmodel.AcademyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AuthScreen(
    viewModel: AcademyViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isSignUpMode by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }

    // Form inputs
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }

    // Profile photo state
    var profilePhotoUri by remember { mutableStateOf<Uri?>(null) }
    var showPhotoOptionsDialog by remember { mutableStateOf(false) }
    var isUploadingPhoto by remember { mutableStateOf(false) }

    // Password visibility toggles
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }

    // Reset password state
    var resetEmail by remember { mutableStateOf("") }

    // Temporary files for camera and gallery crop processing
    val tempCamFile = remember { java.io.File(context.cacheDir, "registration_cam.jpg") }
    val tempCamUri = remember {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempCamFile)
    }

    // 1:1 Crop Launcher
    val cropImageLauncher = rememberLauncherForActivityResult(com.canhub.cropper.CropImageContract()) { result ->
        if (result.isSuccessful) {
            val croppedUri = result.uriContent
            if (croppedUri != null) {
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val compressedFile = compressImageFile(context, croppedUri)
                        withContext(Dispatchers.Main) {
                            profilePhotoUri = Uri.fromFile(compressedFile)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AuthScreen", "Error compressing cropped image", e)
                        withContext(Dispatchers.Main) {
                            profilePhotoUri = croppedUri
                        }
                    }
                }
            }
        } else {
            result.error?.let { err ->
                android.util.Log.e("AuthScreen", "Crop error: ${err.message}", err)
                Toast.makeText(context, "Crop failed: ${err.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Camera Launcher
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempCamFile.exists()) {
            val options = com.canhub.cropper.CropImageContractOptions(
                uri = Uri.fromFile(tempCamFile),
                cropImageOptions = com.canhub.cropper.CropImageOptions(
                    guidelines = com.canhub.cropper.CropImageView.Guidelines.ON,
                    aspectRatioX = 1,
                    aspectRatioY = 1,
                    fixAspectRatio = true,
                    cropShape = com.canhub.cropper.CropImageView.CropShape.RECTANGLE,
                    showCropOverlay = true,
                    showProgressBar = true,
                    cropMenuCropButtonTitle = "DONE",
                    activityTitle = "Crop Profile Photo",
                    activityMenuIconColor = android.graphics.Color.WHITE
                )
            )
            cropImageLauncher.launch(options)
        }
    }

    // Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            try {
                cameraLauncher.launch(tempCamUri)
            } catch (e: Exception) {
                android.util.Log.e("AuthScreen", "Error launching camera", e)
            }
        } else {
            Toast.makeText(context, "Camera permission is required to capture photo", Toast.LENGTH_SHORT).show()
        }
    }

    // Gallery Launcher
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val tempFile = copyUriToCacheFile(context, uri, "registration_gallery.jpg")
                if (tempFile != null && tempFile.exists()) {
                    val options = com.canhub.cropper.CropImageContractOptions(
                        uri = Uri.fromFile(tempFile),
                        cropImageOptions = com.canhub.cropper.CropImageOptions(
                            guidelines = com.canhub.cropper.CropImageView.Guidelines.ON,
                            aspectRatioX = 1,
                            aspectRatioY = 1,
                            fixAspectRatio = true,
                            cropShape = com.canhub.cropper.CropImageView.CropShape.RECTANGLE,
                            showCropOverlay = true,
                            showProgressBar = true,
                            cropMenuCropButtonTitle = "DONE",
                            activityTitle = "Crop Profile Photo",
                            activityMenuIconColor = android.graphics.Color.WHITE
                        )
                    )
                    cropImageLauncher.launch(options)
                }
            } catch (e: Exception) {
                android.util.Log.e("AuthScreen", "Error picking image from gallery", e)
            }
        }
    }

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(BrandBluePrimary, BrandBlueSecondary)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Welcoming Header Card (Matches Premium Aesthetic)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(gradientBrush),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(BorderStroke(2.dp, Color(0xFFFFD700)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = R.drawable.lakshya_ghazipur_hd_1785220211284,
                        contentDescription = "Lakshya Logo",
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(1.4f),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Lakshya Academy",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Ghazipur • Competitive Prep Center",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isSignUpMode) "Create New Account" else "Email & Password Login",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("auth_title")
                )
                Text(
                    text = if (isSignUpMode) "Register to start your learning journey" else "Access your Student or Director account",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                // Error feedback block
                if (viewModel.authError != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error icon",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = viewModel.authError ?: "",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Success feedback block
                if (viewModel.authSuccessMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Success icon",
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = viewModel.authSuccessMessage ?: "",
                                color = Color(0xFF1B5E20),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                if (viewModel.isAuthLoading || isUploadingPhoto) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 24.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.testTag("auth_progress")
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isUploadingPhoto) "Uploading profile photo..." else "Processing registration...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (isSignUpMode) {
                            // --- 1. PROFILE PHOTO SECTION ---
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        .clickable { showPhotoOptionsDialog = true }
                                        .testTag("profile_photo_container"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (profilePhotoUri != null) {
                                        AsyncImage(
                                            model = profilePhotoUri,
                                            contentDescription = "Profile Photo Preview",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AddAPhoto,
                                                contentDescription = "Add Profile Photo",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(32.dp)
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Add Photo",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    // Action Badge Overlay
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (profilePhotoUri != null) Icons.Default.Edit else Icons.Default.CameraAlt,
                                            contentDescription = "Photo action badge",
                                            tint = Color.White,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { showPhotoOptionsDialog = true },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (profilePhotoUri != null) "Change Photo" else "Upload Profile Photo",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    if (profilePhotoUri != null) {
                                        TextButton(
                                            onClick = { profilePhotoUri = null },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                        ) {
                                            Text(
                                                text = "Remove",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }

                            // --- 2. FULL NAME INPUT ---
                            OutlinedTextField(
                                value = fullName,
                                onValueChange = { fullName = it },
                                label = { Text("Full Name") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("name_input"),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                        }

                        // --- 3. EMAIL INPUT ---
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email Address") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("email_input"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        // --- 4. PASSWORD INPUT ---
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.Star else Icons.Default.Lock,
                                        contentDescription = "Toggle password visibility"
                                    )
                                }
                            },
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("password_input"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        if (isSignUpMode) {
                            // --- 5. CONFIRM PASSWORD INPUT ---
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it },
                                label = { Text("Confirm Password") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }) {
                                        Icon(
                                            imageVector = if (isConfirmPasswordVisible) Icons.Default.Star else Icons.Default.Lock,
                                            contentDescription = "Toggle password visibility"
                                        )
                                    }
                                },
                                visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("confirm_password_input"),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                        }

                        if (!isSignUpMode) {
                            // Forgot Password Link aligned right
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                TextButton(
                                    onClick = { showForgotPasswordDialog = true },
                                    modifier = Modifier.testTag("forgot_password_button")
                                ) {
                                    Text(
                                        text = "Forgot Password?",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Main Submit Button
                        Button(
                            onClick = {
                                if (isSignUpMode) {
                                    coroutineScope.launch {
                                        var photoUrl = ""
                                        if (profilePhotoUri != null) {
                                            try {
                                                isUploadingPhoto = true
                                                photoUrl = viewModel.uploadProfilePhoto(context, profilePhotoUri!!)
                                            } catch (e: Exception) {
                                                android.util.Log.e("AuthScreen", "Error uploading profile photo to Supabase", e)
                                            } finally {
                                                isUploadingPhoto = false
                                            }
                                        }
                                        viewModel.signUpWithEmailAndPassword(
                                            email = email,
                                            password = password,
                                            confirmPassword = confirmPassword,
                                            name = fullName,
                                            photoUrl = photoUrl
                                        )
                                    }
                                } else {
                                    viewModel.signInWithEmailAndPassword(email, password)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("submit_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(
                                text = if (isSignUpMode) "Create New Account" else "Secure Login",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Toggle Mode Link
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isSignUpMode) "Already have an account?" else "New student at Lakshya?",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            TextButton(
                                onClick = {
                                    isSignUpMode = !isSignUpMode
                                    // Reset fields
                                    email = ""
                                    password = ""
                                    confirmPassword = ""
                                    fullName = ""
                                    profilePhotoUri = null
                                },
                                modifier = Modifier.testTag("toggle_mode_button")
                            ) {
                                Text(
                                    text = if (isSignUpMode) "Log In" else "Sign Up Now",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    // Photo Source Choice Dialog (Camera / Gallery / Remove)
    if (showPhotoOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoOptionsDialog = false },
            title = {
                Text(
                    text = "Profile Photo",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Choose how you want to add your profile photo:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Option 1: Camera
                    OutlinedButton(
                        onClick = {
                            showPhotoOptionsDialog = false
                            val hasCamPermission = ContextCompat.checkSelfPermission(
                                context, android.Manifest.permission.CAMERA
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                            if (hasCamPermission) {
                                try {
                                    cameraLauncher.launch(tempCamUri)
                                } catch (e: Exception) {
                                    android.util.Log.e("AuthScreen", "Failed launching camera", e)
                                }
                            } else {
                                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Take Photo (Camera)", fontSize = 14.sp)
                    }

                    // Option 2: Gallery
                    OutlinedButton(
                        onClick = {
                            showPhotoOptionsDialog = false
                            galleryLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Choose from Gallery", fontSize = 14.sp)
                    }

                    // Option 3: Remove photo (if currently set)
                    if (profilePhotoUri != null) {
                        OutlinedButton(
                            onClick = {
                                profilePhotoUri = null
                                showPhotoOptionsDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Remove Photo", fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPhotoOptionsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Forgot Password Dialog
    if (showForgotPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showForgotPasswordDialog = false },
            title = {
                Text(
                    text = "Reset Password",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Enter your registered email address below. We will send you instructions to securely reset your password via Supabase.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        label = { Text("Email Address") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reset_email_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.forgotPassword(resetEmail)
                        showForgotPasswordDialog = false
                    },
                    modifier = Modifier.testTag("send_reset_button")
                ) {
                    Text("Send Instructions")
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotPasswordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun copyUriToCacheFile(context: android.content.Context, uri: Uri, fileName: String): java.io.File? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val file = java.io.File(context.cacheDir, fileName)
        java.io.FileOutputStream(file).use { output ->
            inputStream.copyTo(output)
        }
        inputStream.close()
        file
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun compressImageFile(context: android.content.Context, inputUri: Uri, maxDimension: Int = 512, quality: Int = 85): java.io.File {
    val inputStream = context.contentResolver.openInputStream(inputUri)
    val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
    inputStream?.close()

    if (originalBitmap == null) throw Exception("Failed to decode image stream")

    val width = originalBitmap.width
    val height = originalBitmap.height

    val bitmapToCompress = if (width > maxDimension || height > maxDimension) {
        val aspectRatio = width.toFloat() / height.toFloat()
        val targetWidth: Int
        val targetHeight: Int
        if (width >= height) {
            targetWidth = maxDimension
            targetHeight = (maxDimension / aspectRatio).toInt().coerceAtLeast(1)
        } else {
            targetHeight = maxDimension
            targetWidth = (maxDimension * aspectRatio).toInt().coerceAtLeast(1)
        }
        android.graphics.Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true)
    } else {
        originalBitmap
    }

    val outputFile = java.io.File(context.cacheDir, "compressed_avatar_${System.currentTimeMillis()}.jpg")
    java.io.FileOutputStream(outputFile).use { out ->
        bitmapToCompress.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
    }

    if (bitmapToCompress != originalBitmap) {
        bitmapToCompress.recycle()
    }
    originalBitmap.recycle()

    return outputFile
}

