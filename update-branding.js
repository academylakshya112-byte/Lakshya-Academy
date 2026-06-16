const fs = require('fs');
let code = fs.readFileSync('app/src/main/java/com/example/ui/screens/AiTeacherScreens.kt', 'utf-8');

const tStr = `        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("नमस्ते विद्यार्थी 👋", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("मैं Lakshya Academy Ghazipur AI Teacher हूँ।\\nआप अपना सवाल टाइप करें या फोटो अपलोड करें।\\nमैं आपको आसान हिन्दी में समझाऊँगा।", fontSize = 14.sp, color = Color.DarkGray)
            }
        }`;

const rStr = `        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("नमस्ते विद्यार्थी 👋", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Developed by Rahul", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.align(Alignment.Bottom))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("मैं Lakshya Academy Ghazipur AI Teacher हूँ।\\nआप अपना सवाल टाइप करें या फोटो अपलोड करें।\\nमैं आपको आसान हिन्दी में समझाऊँगा।", fontSize = 14.sp, color = Color.DarkGray)
            }
        }`;

code = code.replace(tStr, rStr);
fs.writeFileSync('app/src/main/java/com/example/ui/screens/AiTeacherScreens.kt', code);
console.log("Added branding.");
