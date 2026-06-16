const fs = require('fs');
let code = fs.readFileSync('app/src/main/java/com/example/ui/viewmodel/AcademyViewModel.kt', 'utf-8');
let lines = code.split('\n');

let depth = 0;
for (let i = 0; i < lines.length; i++) {
    let line = lines[i];
    let cleanLine = line.replace(/".*?"/g, "").replace(/\/\/.*$/g, "");
    let oldDepth = depth;
    for (let char of cleanLine) {
        if (char === '{') depth++;
        if (char === '}') depth--;
    }
    if (depth < oldDepth) {
        console.log(`Line ${i+1}: ${line} (Depth decreased to ${depth})`);
    }
}
