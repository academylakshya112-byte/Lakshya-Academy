let fs = require('fs');
let lines = fs.readFileSync('app/src/main/java/com/example/ui/screens/MainAppScreen.kt', 'utf8').split('\n');

let startIndex = lines.findIndex(l => l.includes('fun StudentTestHub(viewModel: AcademyViewModel) {'));
if (startIndex !== -1) {
    let endIndex = startIndex + 1;
    while(lines[endIndex] !== '}') {
        endIndex++;
    }
    
    let injectedComponent = `
@Composable
fun StudentTestHub(viewModel: AcademyViewModel) {
    val activeTestProgress = viewModel.activeTestProgress
    
    if (activeTestProgress != null) {
        if (activeTestProgress.isSubmitted) {
            TestResultScreen(viewModel = viewModel)
        } else {
            ActiveTestScreen(viewModel = viewModel)
        }
    } else {
        StudentTestHubMain(viewModel = viewModel)
    }
}

@Composable
fun StudentTestHubMain(viewModel: AcademyViewModel) {
    LaunchedEffect(Unit) {
        viewModel.generateWeeklyMockTests()
    }

    val tests by viewModel.allTests.collectAsStateWithLifecycle()
    val scores by viewModel.allScores.collectAsStateWithLifecycle()
    val userScoreMap = scores.filter { it.userEmail == viewModel.currentUser?.email }.associateBy { it.testId }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF6366F1), RoundedCornerShape(12.dp)).padding(16.dp)) {
            Column {
                Text("Mock Test & Test Series Hub", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Auto-generated weekly mock tests for Class 5th to 12th now available!", color = Color.White.copy(alpha=0.8f), fontSize = 13.sp)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        if (tests.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading or no tests available...", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(tests) { test ->
                    val userScore = userScoreMap[test.id]
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(test.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Timer, null, modifier = Modifier.size(16.dp), tint=Color.Gray)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("\${test.durationMinutes} mins | \${test.type}", color = Color.Gray, fontSize=12.sp)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            if (userScore != null) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Score: \${userScore.score} (Correct: \${userScore.correctAnswers})", color = Color(0xFF10B981), fontWeight = FontWeight.SemiBold)
                                    Button(onClick = { viewModel.startTest(test) }, shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray)) {
                                        Text("View Result", color=Color.Black)
                                    }
                                }
                            } else {
                                Button(onClick = { viewModel.startTest(test) }, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Text("Start Test")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveTestScreen(viewModel: AcademyViewModel) {
    val progress = viewModel.activeTestProgress ?: return
    val currentQuestion = progress.questions.getOrNull(progress.currentQuestionIndex)
    
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(progress.test.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize=16.sp) },
            navigationIcon = {
                IconButton(onClick = { viewModel.exitTest() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit Test")
                }
            },
            actions = {
                Text(
                    text = String.format("%02d:%02d", progress.secondsRemaining / 60, progress.secondsRemaining % 60),
                    modifier = Modifier.padding(end = 16.dp),
                    fontWeight = FontWeight.Bold,
                    color = Color.Red
                )
            }
        )
        LinearProgressIndicator(
            progress = { (progress.currentQuestionIndex + 1) / progress.questions.size.toFloat() },
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF6366F1)
        )
        
        if (currentQuestion != null) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                Text("Question \${progress.currentQuestionIndex + 1} of \${progress.questions.size}", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(currentQuestion.questionText, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(24.dp))
                
                val options = listOf(currentQuestion.optionA, currentQuestion.optionB, currentQuestion.optionC, currentQuestion.optionD)
                options.forEachIndexed { index, optionText ->
                    val isSelected = progress.selectedAnswers[currentQuestion.id] == index
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                            viewModel.selectTestAnswer(currentQuestion.id, index)
                        },
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFEEF2FF) else Color.White),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF6366F1) else Color.LightGray)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = isSelected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor=Color(0xFF6366F1)))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(optionText)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(
                        onClick = { viewModel.activeTestProgress = progress.copy(currentQuestionIndex = progress.currentQuestionIndex - 1) },
                        enabled = progress.currentQuestionIndex > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray, contentColor = Color.Black)
                    ) { Text("Previous") }
                    
                    if (progress.currentQuestionIndex < progress.questions.size - 1) {
                        Button(onClick = { viewModel.activeTestProgress = progress.copy(currentQuestionIndex = progress.currentQuestionIndex + 1) }) { Text("Next") }
                    } else {
                        Button(
                            onClick = { viewModel.submitActiveTest() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) { Text("Submit Test") }
                    }
                }
            }
        }
    }
}

@Composable
fun TestResultScreen(viewModel: AcademyViewModel) {
    val progress = viewModel.activeTestProgress ?: return
    val score = progress.testScore ?: return // Must have score to view result
    
    // We need moshi or similar to parse selectedAnswersJson?
    // Wait, progress object ALREADY has selectedAnswers map locally populated if they just finished.
    // If they re-entered "View Result", does startTest() populate selectedAnswers?
    // Let's modify startTest to check score and map.
    
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Performance Report") }, navigationIcon = { IconButton(onClick = { viewModel.exitTest() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } })
        
        Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB))) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total Score: \${score.score}", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color(0xFF6366F1))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Correct: \${score.correctAnswers}  |  Wrong: \${score.wrongAnswers}  |  Unattempted: \${score.totalQuestions - (score.correctAnswers + score.wrongAnswers)}", color = Color.Gray, fontSize=14.sp)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("Detailed Question Review:", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(16.dp))
            
            progress.questions.forEachIndexed { i, q ->
                val selectedIdx = progress.selectedAnswers[q.id]
                val isCorrect = selectedIdx == q.correctIndex
                
                Card(modifier = Modifier.fillMaxWidth().padding(vertical=4.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color.LightGray)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Q\${i+1}. \${q.questionText}", fontWeight=FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val ops = listOf(q.optionA, q.optionB, q.optionC, q.optionD)
                        ops.forEachIndexed { opIdx, opTxt ->
                            val isCorrectAnswer = (q.correctIndex == opIdx)
                            val isUserSelected = (selectedIdx == opIdx)
                            
                            val bgColor = when {
                                isCorrectAnswer -> Color(0xFFD1FAE5) // Green background for the actual correct answer
                                isUserSelected && !isCorrectAnswer -> Color(0xFFFEE2E2) // Red background if user selected wrong
                                else -> Color.Transparent
                            }
                            
                            Row(modifier = Modifier.fillMaxWidth().background(bgColor, RoundedCornerShape(4.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                val ic = when {
                                    isCorrectAnswer -> Icons.Default.CheckCircle
                                    isUserSelected -> Icons.Default.Cancel
                                    else -> Icons.Default.RadioButtonUnchecked
                                }
                                val cColor = when {
                                    isCorrectAnswer -> Color(0xFF10B981)
                                    isUserSelected -> Color.Red
                                    else -> Color.Gray
                                }
                                Icon(ic, null, modifier = Modifier.size(16.dp), tint = cColor)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(opTxt, color = if(isCorrectAnswer || isUserSelected) Color.Black else Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}
`;
    
    // Using splice to remove the old StudentTestHub and insert the new ones
    lines.splice(startIndex, endIndex - startIndex + 1, injectedComponent.trim());
    
    // Now we must ensure imports.
    // Like Timer, CheckCircle, Cancel, RadioButtonUnchecked
    const extraImports = [
        "import androidx.compose.material.icons.filled.Timer",
        "import androidx.compose.material.icons.filled.CheckCircle",
        "import androidx.compose.material.icons.filled.Cancel",
        "import androidx.compose.material.icons.filled.RadioButtonUnchecked",
        "import androidx.compose.material3.RadioButton",
        "import androidx.compose.material3.RadioButtonDefaults",
        "import androidx.compose.material3.LinearProgressIndicator",
        "import androidx.compose.foundation.border"
    ];
    let importIndex = lines.findIndex(l => l.startsWith('import'));
    if (importIndex !== -1) {
        extraImports.forEach(imp => {
            if (!lines.includes(imp)) {
                lines.splice(importIndex, 0, imp);
            }
        });
    }
    
    fs.writeFileSync('app/src/main/java/com/example/ui/screens/MainAppScreen.kt', lines.join('\n'));
    console.log("Success");
} else {
    console.log("Failed to find StudentTestHub");
}
