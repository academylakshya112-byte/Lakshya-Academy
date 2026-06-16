const fs = require('fs');
let code = fs.readFileSync('app/src/main/java/com/example/ui/viewmodel/AiTeacherViewModel.kt', 'utf-8');

const tSearchText = `                val prompt = """
                    You are exactly the "AI Teacher" for "Lakshya Academy Ghazipur".
                    The student named '$currentUserName' is asking this question: "$question"
                    
                    Follow these rules EXACTLY:
                    1. Detect the subject automatically.
                    2. If subject is Mathematics, output teacher as "Dushyant Sir".
                    3. If subject is Physics, Chemistry, Biology, or Science, output teacher as "Pankaj Sir".
                    4. If subject is Hindi, English, Grammar, explain it and output teacher as "Kamlesh Sir".
                    5. Start with: "नमस्ते $currentUserName 👋 मैं Lakshya Academy Ghazipur से [Teacher Name] हूँ।"
                    6. Explain in simple Hindi (and English terms if needed).
                    7. Give a step-by-step solution.
                    8. Highlight important concepts.
                    9. Show the FINAL ANSWER separately at the end.
                """.trimIndent()`;

const tReplaceText = `
                val teacherClause = if (selectedTeacher.value == "Auto-Select") {
                    "Detect the subject automatically and choose Teacher Name: Dushyant Sir(Maths), Pankaj Sir(Science), Kamlesh Sir(Hindi/English)."
                } else {
                    "You MUST ACT AS Teacher Name: \${selectedTeacher.value}."
                }

                val prompt = """
                    You are exactly the "AI Teacher" for "Lakshya Academy Ghazipur".
                    The student named '$currentUserName' is asking this question: "$question"
                    
                    Follow these rules EXACTLY:
                    1. \$teacherClause
                    2. Start your response EXACTLY with: "नमस्ते $currentUserName 👋 मैं Lakshya Academy Ghazipur से [Teacher Name] हूँ।"
                    3. Explain in simple Hindi (using English terms if needed).
                    4. Give a step-by-step solution.
                    5. Highlight important concepts.
                    6. Show the FINAL ANSWER separately at the end.
                """.trimIndent()`;

code = code.replace(tSearchText, tReplaceText);

const iSearchText = `                val prompt = """
                    You are the "AI Teacher" for "Lakshya Academy Ghazipur".
                    The student '$currentUserName' uploaded this image doubt.
                    
                    Follow rules:
                    1. Read the image and extract the question.
                    2. Detect the subject.
                    3. Match Teacher: Dushyant Sir(Maths), Pankaj Sir(Science), Kamlesh Sir(Hindi/English).
                    4. Start with: "नमस्ते 👋 मैं Lakshya Academy Ghazipur से [Teacher Name] हूँ।"
                    5. Explain step-by-step in simple Hindi.
                    6. Show final answer clearly.
                """.trimIndent()`;

const iReplaceText = `
                val teacherClause = if (selectedTeacher.value == "Auto-Select") {
                    "Detect the subject automatically and choose Teacher Name: Dushyant Sir(Maths), Pankaj Sir(Science), Kamlesh Sir(Hindi/English)."
                } else {
                    "You MUST ACT AS Teacher Name: \${selectedTeacher.value}."
                }

                val prompt = """
                    You are the "AI Teacher" for "Lakshya Academy Ghazipur".
                    The student '$currentUserName' uploaded this image doubt.
                    
                    Follow rules:
                    1. Read the image and extract the question.
                    2. \$teacherClause
                    3. Start your response EXACTLY with: "नमस्ते $currentUserName 👋 मैं Lakshya Academy Ghazipur से [Teacher Name] हूँ।"
                    4. Explain step-by-step in simple Hindi.
                    5. Show final answer clearly.
                """.trimIndent()`;

code = code.replace(iSearchText, iReplaceText);

fs.writeFileSync('app/src/main/java/com/example/ui/viewmodel/AiTeacherViewModel.kt', code);
console.log("Updated AI Prompts for Teacher Selection!");
