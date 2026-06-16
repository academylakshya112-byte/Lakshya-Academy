const fs = require('fs');
let code = fs.readFileSync('app/src/main/java/com/example/ui/screens/AiTeacherScreens.kt', 'utf-8');

const targetStr = `        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(
                onClick = { photoPicker.launch("image/*") },`;

const replacementStr = `        // Additional Tooling
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = { viewModel.solveDoubtText("Generate detailed short notes for my current topic.") }) {
                Icon(Icons.Default.MenuBook, contentDescription = null, tint = Color(0xFF6366F1))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Notes", color = Color(0xFF6366F1))
            }
            TextButton(onClick = { viewModel.solveDoubtText("Generate 5 MCQs for my current topic for a practice test.") }) {
                Icon(Icons.Default.BorderColor, contentDescription = null, tint = Color(0xFF6366F1))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Quiz", color = Color(0xFF6366F1))
            }
            TextButton(onClick = { viewModel.solveDoubtText("Explain the important points and summarize this chapter.") }) {
                Icon(Icons.Default.Summarize, contentDescription = null, tint = Color(0xFF6366F1))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Summary", color = Color(0xFF6366F1))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(
                onClick = { photoPicker.launch("image/*") },`;

code = code.replace(targetStr, replacementStr);
fs.writeFileSync('app/src/main/java/com/example/ui/screens/AiTeacherScreens.kt', code);
console.log("Added Notes and Quiz generation buttons.");
