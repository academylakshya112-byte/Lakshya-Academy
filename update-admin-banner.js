const fs = require('fs');

let code = fs.readFileSync('app/src/main/java/com/example/ui/screens/AdminScreens.kt', 'utf-8');

const imports = `import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import androidx.compose.ui.text.style.TextOverflow
`;

if (!code.includes('import androidx.activity.result.contract.ActivityResultContracts')) {
    code = code.replace('import androidx.compose.foundation.*', imports + 'import androidx.compose.foundation.*');
}

const oldComponent = `@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminBannerManager(viewModel: AcademyViewModel) {
    val banners by viewModel.allBanners.collectAsStateWithLifecycle()
    var bTitle by remember { mutableStateOf("") }
    var bUrl by remember { mutableStateOf("") }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Carousel Ad Banners", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            OutlinedTextField(value = bTitle, onValueChange = { bTitle = it }, label = { Text("Banner Title") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = bUrl, onValueChange = { bUrl = it }, label = { Text("Image URL") }, modifier = Modifier.fillMaxWidth())
            Box(modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { viewModel.adminAddBanner(bTitle, "Click to explore", bUrl, "COURSES") }, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Text("Add Banner")
                }
            }
        }
        items(banners) { banner ->
            ListItem(headlineContent = { Text(banner.title) }, trailingContent = {
                IconButton(onClick = { viewModel.adminDeleteBanner(banner.id) }) { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
            })
        }
    }
}`;

const newComponent = `@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminBannerManager(viewModel: AcademyViewModel) {
    val banners by viewModel.allBanners.collectAsStateWithLifecycle()
    var bTitle by remember { mutableStateOf("") }
    var bUrl by remember { mutableStateOf("") }
    var bLink by remember { mutableStateOf("") }
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val file = File(context.filesDir, "banner_" + System.currentTimeMillis() + ".jpg")
                val outputStream = FileOutputStream(file)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()
                bUrl = file.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Carousel Ad Banners", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = bTitle, onValueChange = { bTitle = it }, label = { Text("Banner Title") }, modifier = Modifier.fillMaxWidth())
            
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = bUrl, 
                    onValueChange = { bUrl = it }, 
                    label = { Text("Image Path/URL") }, 
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { launcher.launch("image/*") }) {
                    Text("Upload\\nImage", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            OutlinedTextField(value = bLink, onValueChange = { bLink = it }, label = { Text("Target Link (COURSES or URL)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            
            Box(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Button(
                    onClick = { 
                        if (bTitle.isNotBlank()) {
                            viewModel.adminAddBanner(bTitle, bUrl, if(bLink.isBlank()) "COURSES" else bLink, "VIEW NOW")
                            bTitle = ""
                            bUrl = ""
                            bLink = ""
                        }
                    }, 
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Text("Add Banner")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
        items(banners, key = { it.id }) { banner ->
            ListItem(
                headlineContent = { Text(banner.title) }, 
                supportingContent = { Text(banner.imageUrl, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingContent = {
                    IconButton(onClick = { viewModel.adminDeleteBanner(banner.id) }) { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
                }
            )
        }
    }
}`;

code = code.replace(oldComponent, newComponent);
fs.writeFileSync('app/src/main/java/com/example/ui/screens/AdminScreens.kt', code);
console.log("Updated AdminBannerManager to support image upload!");
