const fs = require('fs');
let code = fs.readFileSync('app/src/main/java/com/example/ui/viewmodel/AcademyViewModel.kt', 'utf-8');

const newBanner = `            repository.insertBanner(
                BannerEntity(
                    title = "लक्ष्य बैच (Lakshya Batch) 2024-25 - YouTube पर पहली बार FREE!",
                    imageUrl = "https://images.unsplash.com/photo-1524178232363-1fb2b075b655?w=600&auto=format&fit=crop&q=60",
                    linkUrl = "COURSES",
                    buttonText = "Watch Now",
                    description = "Features: Live classes, notes, test series, 100% preparation by Pankaj sir, Kamlesh sir, Dushyant sir."
                )
            )
`;

code = code.replace(
    'repository.insertBanner(\n                BannerEntity(\n                    title = "Lakshya All-Subject Special Batch',
    newBanner + '            repository.insertBanner(\n                BannerEntity(\n                    title = "Lakshya All-Subject Special Batch'
);

fs.writeFileSync('app/src/main/java/com/example/ui/viewmodel/AcademyViewModel.kt', code);
console.log("Banner added.");
