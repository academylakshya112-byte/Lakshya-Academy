const fs = require('fs');
let code = fs.readFileSync('app/src/main/java/com/example/ui/screens/MainAppScreen.kt', 'utf-8');

const targetStr = 'Image(painter = rememberAsyncImagePainter(banner.imageUrl), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)';
const replacementStr = 'val imageModel = if (banner.imageUrl.startsWith("/")) java.io.File(banner.imageUrl) else banner.imageUrl\n            Image(painter = rememberAsyncImagePainter(imageModel), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)';

code = code.replace(targetStr, replacementStr);
fs.writeFileSync('app/src/main/java/com/example/ui/screens/MainAppScreen.kt', code);
console.log("Updated BannerCarousel in MainAppScreen.kt to handle absolute paths.");
