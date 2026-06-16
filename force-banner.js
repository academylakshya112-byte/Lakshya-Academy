const fs = require('fs');

let code = fs.readFileSync('app/src/main/java/com/example/ui/viewmodel/AcademyViewModel.kt', 'utf-8');

// The block is:
/*
        // Seed default banners if empty
        val bannerCheck = repository.allBanners.first()
        if (bannerCheck.isEmpty()) {
            repository.insertBanner(
                BannerEntity(
*/

const oldBlock = `        // Seed default banners if empty
        val bannerCheck = repository.allBanners.first()
        if (bannerCheck.isEmpty()) {`;

const newBlock = `        // Seed default banners if empty
        val bannerCheck = repository.allBanners.first()
        if (!bannerCheck.any { it.title.contains("Lakshya Batch") }) {
             repository.insertBanner(
                BannerEntity(
                    title = "लक्ष्य बैच (Lakshya Batch) 2024-25 - YouTube पर पहली बार FREE!",
                    imageUrl = "android.resource://com.aistudio.lakshya_academy.gzkvpm/drawable/lakshya_batch_banner_1781437391844",
                    linkUrl = "COURSES",
                    buttonText = "Watch Now",
                    description = "Features: Live classes, notes, test series, 100% preparation by Pankaj sir, Kamlesh sir, Dushyant sir."
                )
            )
        }
        if (bannerCheck.isEmpty()) {`;

code = code.replace(oldBlock, newBlock);
fs.writeFileSync('app/src/main/java/com/example/ui/viewmodel/AcademyViewModel.kt', code);
console.log("Updated ViewModel to force-insert the banner");
