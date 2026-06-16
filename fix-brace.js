const fs = require('fs');
let code = fs.readFileSync('app/src/main/java/com/example/ui/viewmodel/AcademyViewModel.kt', 'utf-8');

// The class starts with `class AcademyViewModel(application: Application) : AndroidViewModel(application) {`
// Let's count open/close braces.
let openCount = 0;
let lines = code.split('\n');

// we can just remove the extra '}' before 'fun login'
let indexLogin = lines.findIndex(l => l.includes('fun login(email: String'));
// look backward from indexLogin and ensure there's exactly ONE '}' at indentation 4 spaces before the '// --- Authentication Actions ---'

// Actually, it's easier: 
// at 1262:             )
// at 1263:         }
// at 1264:         }
// at 1265:     }
// The correct sequence to close `if (bannerCheck...)` and `seedDatabaseIfEmpty()` is:
// 1263:         } // closes if (bannerCheck.isEmpty())
// 1264:     } // closes seedDatabase...
// So there's one extra `}`. We can just delete line 1264.

// Let's also check the bottom of the file (1953: Syntax error).
// It means maybe we're missing `}` at the very end of the file now, because we closed the class prematurely!
// So if I fix the premature closure, it will fix the whole file!
lines.splice(1263, 1);

fs.writeFileSync('app/src/main/java/com/example/ui/viewmodel/AcademyViewModel.kt', lines.join('\n'));
console.log("Fixed brace.");
