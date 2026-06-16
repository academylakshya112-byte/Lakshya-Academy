const fs = require('fs');
let code = fs.readFileSync('app/src/main/java/com/example/ui/viewmodel/AcademyViewModel.kt', 'utf-8');
let lines = code.split('\n');

const replacement = `                TempQuestion(
                    "Which endocrine gland is commonly known as the master gland? / किस अंतःस्रावी ग्रंथि को 'मास्टर ग्रंथि' कहा जाता है?",
                    "Thyroid / थायराइड", "Pituitary / पीयूष ग्रंथि", "Adrenal / एड्रेनल", "Pancreas / अग्न्याशय", 1
                ),
                TempQuestion(
                    "Which non-metal is liquid at room temperature? / कौन सी अधातु कमरे के तापमान पर तरल होती है?",
                    "Phosphorus / फास्फोरस", "Carbon / कार्बन", "Helium / हीलियम", "Bromine / ब्रोमीन", 3`;

let startIndex = lines.findIndex(l => l.includes("Which endocrine gland is commonly known as the master gland?"));
let endIndex = lines.findIndex(l => l.includes("fun login(email: String, name: String, role: String, isSignUp: Boolean = false) {"));

if (startIndex !== -1 && endIndex !== -1) {
    lines.splice(startIndex - 1, endIndex - startIndex + 2, replacement);
    fs.writeFileSync('app/src/main/java/com/example/ui/viewmodel/AcademyViewModel.kt', lines.join('\n'));
    console.log("Success replacing 1071-1108");
} else {
    console.log("Not found: start=" + startIndex + " end=" + endIndex);
}
