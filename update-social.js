const fs = require('fs');

let code = fs.readFileSync('app/src/main/java/com/example/ui/viewmodel/AcademyViewModel.kt', 'utf-8');

const oldInstaUrl = 'https://instagram.com/academylakshya';
const newInstaUrl = 'https://www.instagram.com/lakshya_academy_sirgitha_gzpr?igsh=MXU5eHVicWRhNmgwag==';

code = code.replace(oldInstaUrl, newInstaUrl);

const checkFbBlock = `if (!bannerCheck.any { it.title.contains("Facebook") }) {
             repository.insertBanner(
                BannerEntity(
                    title = "Join Our Facebook Community! 👥",
                    imageUrl = "https://images.unsplash.com/photo-1543269865-cbf427effbad?w=600&auto=format&fit=crop&q=60",
                    linkUrl = "https://www.facebook.com/share/1Ld9zB8Khi/",
                    buttonText = "FOLLOW PAGE",
                    description = "Stay updated with announcements, class schedules and community discussions on our official Facebook Page."
                )
            )
        }`

const targetStr = `        if (!bannerCheck.any { it.title.contains("Lakshya Batch") }) {`;

if (!code.includes('Join Our Facebook Community!')) {
    code = code.replace(targetStr, checkFbBlock + '\n' + targetStr);
}

fs.writeFileSync('app/src/main/java/com/example/ui/viewmodel/AcademyViewModel.kt', code);
console.log("Updated social banners!");
