const fs = require('fs');

const text = `
@Composable
fun AdminAiSettingsScreen(viewModel: AiTeacherViewModel) {
    val context = LocalContext.current
    val isEnabled = remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("AI Teacher Settings", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF6366F1))
        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Enable AI Teacher for Students", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Switch(checked = isEnabled.value, onCheckedChange = { isEnabled.value = it })
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Teacher Avatar Uploads:", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        val pPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> }
        
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp).clickable { pPicker.launch("image/*") }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Dushyant Sir (Maths) Photo")
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFF6366F1))
            }
        }
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp).clickable { pPicker.launch("image/*") }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Pankaj Sir (Science) Photo")
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFF6366F1))
            }
        }
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp).clickable { pPicker.launch("image/*") }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Kamlesh Sir (Hindi/English) Photo")
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFF6366F1))
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("AI Telemetry & Stats", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Total Doubts Solved by AI: 142")
                Text("Total Video Explanations Generated: 38")
            }
        }
    }
}
`;

fs.appendFileSync('app/src/main/java/com/example/ui/screens/AiTeacherScreens.kt', text);
