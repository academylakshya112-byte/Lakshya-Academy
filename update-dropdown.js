const fs = require('fs');
let code = fs.readFileSync('app/src/main/java/com/example/ui/screens/AiTeacherScreens.kt', 'utf-8');

const tSearch = `        Spacer(modifier = Modifier.height(16.dp))

        // Input Area`;

const tReplace = `        Spacer(modifier = Modifier.height(12.dp))

        // Teacher Selection
        val selectedTeacher by viewModel.selectedTeacher.collectAsState()
        var expanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedTeacher,
                onValueChange = {},
                readOnly = true,
                label = { Text("Select Teacher (Auto / Manual)") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                viewModel.teachers.forEach { teacher ->
                    DropdownMenuItem(
                        text = { Text(teacher) },
                        onClick = {
                            viewModel.selectedTeacher.value = teacher
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Input Area`;

code = code.replace(tSearch, tReplace);
fs.writeFileSync('app/src/main/java/com/example/ui/screens/AiTeacherScreens.kt', code);
console.log("Added Dropdown for Teacher selection.");
