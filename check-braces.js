const fs = require('fs');
let code = fs.readFileSync('app/src/main/java/com/example/ui/viewmodel/AcademyViewModel.kt', 'utf-8');
let lines = code.split('\n');

let depth = 0;
for (let i = 0; i < lines.length; i++) {
    let line = lines[i];
    // Simple rough counting. Let's ignore strings for a moment and just count { and }
    // A better approach is to just remove strings and comments.
    let cleanLine = line.replace(/".*?"/g, "").replace(/\/\/.*$/g, "");
    for (let char of cleanLine) {
        if (char === '{') depth++;
        if (char === '}') depth--;
    }
    if (depth === 0 && i > 50) {
        console.log(`Depth reached 0 at line ${i+1}: ${line}`);
        break;
    }
}
