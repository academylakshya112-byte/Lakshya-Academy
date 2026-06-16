const fs = require('fs');
let code = fs.readFileSync('app/src/main/java/com/example/ui/viewmodel/AcademyViewModel.kt', 'utf-8');
let lines = code.split('\n');

let depth = 0;
for (let i = 0; i < lines.length; i++) {
    let line = lines[i];
    let cleanLine = line.replace(/".*?"/g, "").replace(/\/\/.*$/g, "");
    for (let char of cleanLine) {
        if (char === '{') depth++;
        if (char === '}') depth--;
    }
    if (i === 1145) console.log(`Depth at 1146: ${depth}`);
    if (i === 1152) console.log(`Depth at 1153: ${depth}`);
    if (i === 1230) console.log(`Depth at 1231: ${depth}`);
}
