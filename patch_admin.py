import re

with open('app/src/main/java/com/example/ui/screens/AdminR2UploadScreen.kt', 'r') as f:
    content = f.read()

# Add variables
var_injection = r'''    var selectedVideoSizeStr by remember { mutableStateOf("") }
    
    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var selectedPdfName by remember { mutableStateOf("") }
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedPdfUri = uri
        uri?.let {
            val (name, _) = R2SupabaseManager.getFileInfo(context, uri)
            selectedPdfName = name
        }
    }'''
content = content.replace('    var selectedVideoSizeStr by remember { mutableStateOf("") }', var_injection)

# Add PDF UI before Action Button
pdf_ui = r'''                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // PDF Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { pdfPickerLauncher.launch("application/pdf") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isUploading
                    ) {
                        Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (selectedPdfUri == null) "Attach PDF" else "Change PDF")
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        if (selectedPdfUri != null) {
                            Text(
                                text = selectedPdfName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        } else {
                            Text(
                                text = "No PDF selected",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                // Main Action Button'''
content = content.replace('''                }
                
                Spacer(modifier = Modifier.height(16.dp))

                // Main Action Button''', pdf_ui)

# Update upload call
call_injection = r'''                                R2SupabaseManager.uploadVideoToR2(
                                    context = context,
                                    videoUri = fileUri,
                                    pdfUri = selectedPdfUri,'''
content = content.replace('''                                R2SupabaseManager.uploadVideoToR2(
                                    context = context,
                                    videoUri = fileUri,''', call_injection)

# Reset state
reset_injection = r'''                                            selectedVideoName = ""
                                            selectedVideoSizeStr = ""
                                            selectedPdfUri = null
                                            selectedPdfName = ""'''
content = content.replace('''                                            selectedVideoName = ""
                                            selectedVideoSizeStr = ""''', reset_injection)

with open('app/src/main/java/com/example/ui/screens/AdminR2UploadScreen.kt', 'w') as f:
    f.write(content)
