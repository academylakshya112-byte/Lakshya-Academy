const fs = require('fs');
let code = fs.readFileSync('app/src/main/java/com/example/ui/viewmodel/AcademyViewModel.kt', 'utf-8');
const lines = code.split('\n');

const replacement = `                TempQuestion(
                    "Which planet is designated as the largest in our solar system? / हमारे सौरमंडल का सबसे बड़ा ग्रह किसे नामित किया गया है?",
                    "Earth / पृथ्वी", "Jupiter / बृहस्पति", "Saturn / शनि", "Mars / मंगल", 1
                )
            )

            // 4. Seed Support Materials
            repository.insertMaterial(
                MaterialEntity(
                    type = "Book",
                    title = "Objective Indian Polity (Full Syllabus M3 Version)",
                    description = "Laxmikanth complete Indian Polity book with simplified explanations.",
                    fileSize = "14.5 MB"
                )
            )
            repository.insertMaterial(
                MaterialEntity(
                    type = "Syllabus",
                    title = "UPSC 2026 Prelims civil services official syllabus",
                    description = "Detailed syllabus PDF for Civil Services GS and CSAT.",
                    fileSize = "2.1 MB"
                )
            )
            repository.insertMaterial(
                MaterialEntity(
                    type = "Timetable",
                    title = "Lakshya Academy June 2026 Live online batches",
                    description = "Schedule for IAS Daily interactive, mock discussion and doubts sessions.",
                    fileSize = "0.8 MB"
                )
            )
            repository.insertMaterial(
                MaterialEntity(
                    type = "Previous Year Paper",
                    title = "UPSC GS Prelims 2025 Paper solved answers key",
                    description = "Fully annotated UPSC paper 1 answers keys with brief explanation notes.",
                    fileSize = "4.2 MB"
                )
            )
            repository.insertMaterial(
                MaterialEntity(
                    type = "Current Affairs",
                    title = "Daily GK capsule 12 June 2026 (Editorials Special)",
                    description = "Compiled editorial briefs for Ghazipur online exam pupils.",
                    fileSize = "1.2 MB"
                )
            )

            // 5. Seed Support doubts
            repository.insertDoubt(
                DoubtEntity(
                    userEmail = "anand@yadav.com",
                    userName = "Anand Yadav",
                    subject = "Polity Query",
                    questionText = "Can fundamental rights be suspended during a National Emergency? If yes, which articles are exceptions?",
                    replyText = "Yes Anand, during National Emergency under Article 352, standard rights are suspended, EXCEPT Article 20 and Article 21, which guarantee protection in respect of conviction for offenses and protection of life/liberty respectively. This is per 44th Amendment Act.",
                    answeredBy = "Chief Faculty (Polity)"
                )
            )
            
            // 6. Seed Push Notifications
            repository.insertNotification(
                NotificationEntity(
                    title = "Welcome to Lakshya Academy!",
                    message = "Your gateway to competitive online coaching in Ghazipur. Learn from the best teachers, participate in weekly Live Tests series, and track your daily progress."
                )
            )

            // 7. Seed support chat starter messages
            repository.insertChatMessage(
                ChatMessageEntity(
                    senderName = "Academy Helper Bot",
                    senderEmail = "support@lakshya.academy",
                    text = "Welcome to Lakshya Academy Chat Support. Ask any question regarding exam guides, fees, online tests or offline batches. We are here to help you!",
                    isAdminReply = true
                )
            )
        }

        // Seed default banners if empty
        val bannerCheck = repository.allBanners.first()
        if (bannerCheck.isEmpty()) {
            repository.insertBanner(
                BannerEntity(
                    title = "Follow Our Instagram for Daily GK Reels! 📲",
                    imageUrl = "https://images.unsplash.com/photo-1611262588024-d12430b98920?w=600&auto=format&fit=crop&q=60",
                    linkUrl = "https://instagram.com/academylakshya",
                    buttonText = "FOLLOW US",
                    description = "Get short tricks, current affairs quiz and exam notification reels directly on Instagram!"
                )
            )
            repository.insertBanner(
                BannerEntity(
                    title = "Join Official Telegram Study Channel! 💎",
                    imageUrl = "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=600&auto=format&fit=crop&q=60",
                    linkUrl = "https://t.me/lakshya_academy",
                    buttonText = "JOIN NOW",
                    description = "Download free PDFs, class notes, schedules & interactive discussion worksheets instantly."
                )
            )
            repository.insertBanner(
                BannerEntity(
                    title = "Lakshya All-Subject Special Batch Starting! 🔴",
                    imageUrl = "https://images.unsplash.com/photo-1434030216411-0b793f4b4173?w=600&auto=format&fit=crop&q=60",
                    linkUrl = "COURSES",
                    buttonText = "ENROLL",
                    description = "A new high-yield mock-test batch containing All-Subject lessons starts this Monday. Secure your rank now!"
                )
            )
        }`;

lines.splice(1153, 26, replacement);
fs.writeFileSync('app/src/main/java/com/example/ui/viewmodel/AcademyViewModel.kt', lines.join('\n'));
console.log("Done");
