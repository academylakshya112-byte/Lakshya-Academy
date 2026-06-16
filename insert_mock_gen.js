let fs = require('fs');
let lines = fs.readFileSync('app/src/main/java/com/example/ui/viewmodel/AcademyViewModel.kt', 'utf8').split('\n');

let index = lines.findIndex(l => l.includes('fun adminCreateNewTest'));
if (index !== -1) {
    let codeToInsert = `
    fun generateWeeklyMockTests() {
        viewModelScope.launch {
            val user = currentUser ?: return@launch
            if (user.role == "ADMIN") return@launch // Only generate for students traversing or just let it generate? Wait, anyone hitting the hub can generate.
            val existingTests = repository.allTests.first()
            val classesToGenerate = listOf("Class 5", "Class 6", "Class 7", "Class 8", "Class 9", "Class 10", "Class 11", "Class 12")
            
            for (cls in classesToGenerate) {
                val title = "$cls Weekly Auto Mock Test 1"
                if (existingTests.none { it.title == title }) {
                    val tId = repository.insertTest(
                        TestEntity(
                            title = title,
                            type = "Test Series",
                            durationMinutes = 60,
                            hasNegativeMarking = true,
                            marksPerCorrect = 4,
                            marksPerWrong = -1f
                        )
                    )
                    for (i in 1..50) {
                        repository.insertQuestion(
                            QuestionEntity(
                                testId = tId,
                                questionText = "($cls) Sample Auto-Generated Question $i in Mock Test Series? / ($cls) सैंपल स्व-निर्मित प्रश्न $i?",
                                optionA = "Option A correctly generated",
                                optionB = "Option B generic choice",
                                optionC = "Option C another answer",
                                optionD = "Option D generic choice",
                                correctIndex = (0..3).random()
                            )
                        )
                    }
                }
            }
        }
    }
`;
    lines.splice(index, 0, codeToInsert.trim() + "\n");
    fs.writeFileSync('app/src/main/java/com/example/ui/viewmodel/AcademyViewModel.kt', lines.join('\n'));
    console.log("Done");
} else {
    console.log("Not found");
}
