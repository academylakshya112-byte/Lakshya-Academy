package com.example.data

object AutoQuestionPool {
    data class SimpleQuestion(
        val questionText: String,
        val optionA: String,
        val optionB: String,
        val optionC: String,
        val optionD: String,
        val correctIndex: Int
    )

    // A collection of 50 beautifully curated bilingual questions spanning Science, Match, Social Science, Hindi Grammar & English Grammar.
    val bilingualMockQuestions = listOf(
        // === SCIENCE / विज्ञान (10 Questions) ===
        SimpleQuestion(
            questionText = "Q1. What is the standard SI unit of physical Force? / बल का SI मात्रक क्या होता है?",
            optionA = "Joule / जूल",
            optionB = "Newton / न्यूटन",
            optionC = "Pascal / पास्कल",
            optionD = "Watt / वाट",
            correctIndex = 1
        ),
        SimpleQuestion(
            questionText = "Q2. Which vitamin prevents the disease known as Rickets? / सूखा रोग (रिकेट्स) से बचाव के लिए कौन सा विटामिन आवश्यक है?",
            optionA = "Vitamin A / विटामिन ए",
            optionB = "Vitamin B / विटामिन बी",
            optionC = "Vitamin C / विटामिन सी",
            optionD = "Vitamin D / विटामिन डी",
            correctIndex = 3
        ),
        SimpleQuestion(
            questionText = "Q3. What is the chemical formula of common baking soda? / खाने वाले सोडा (बेकिंग सोडा) का रासायनिक सूत्र क्या है?",
            optionA = "NaHCO₃",
            optionB = "Na₂CO₃",
            optionC = "NaOH",
            optionD = "NaCl",
            correctIndex = 0
        ),
        SimpleQuestion(
            questionText = "Q4. Which gas do green plants absorb during photosynthesis? / प्रकाश संश्लेषण के दौरान हरे पौधे कौन सी गैस अवशोषित करते हैं?",
            optionA = "Oxygen / ऑक्सीजन",
            optionB = "Nitrogen / नाइट्रोजन",
            optionC = "Carbon dioxide / कार्बन डाइऑक्साइड",
            optionD = "Hydrogen / हाइड्रोजन",
            correctIndex = 2
        ),
        SimpleQuestion(
            questionText = "Q5. What is the approximate speed of sound in dry air under normal conditions? / सामान्य परिस्थितियों में शुष्क हवा में ध्वनि की गति कितनी होती है?",
            optionA = "150 m/s / १५० मी/से",
            optionB = "343 m/s / ३४३ मी/से",
            optionC = "1500 m/s / १५०० मी/से",
            optionD = "3 * 10⁸ m/s",
            correctIndex = 1
        ),
        SimpleQuestion(
            questionText = "Q6. Which metallic element remains in liquid state at room temperature? / कौन सा धातु तत्व सामान्य तापमान पर द्रव अवस्था में रहता है?",
            optionA = "Sodium / सोडियम",
            optionB = "Mercury (Para) / पारा (मरकरी)",
            optionC = "Gallium / गैलियम",
            optionD = "Bromine / ब्रोमीन",
            correctIndex = 1
        ),
        SimpleQuestion(
            questionText = "Q7. What is the average value of acceleration due to gravity on Earth? / पृथ्वी पर गुरुत्वीय त्वरण (g) का औसत मान क्या है?",
            optionA = "9.8 m/s²",
            optionB = "10.5 m/s²",
            optionC = "8.9 m/s²",
            optionD = "11.2 m/s²",
            correctIndex = 0
        ),
        SimpleQuestion(
            questionText = "Q8. Which blood cells are responsible for defense against infections? / संक्रमण के खिलाफ शरीर की रक्षा के लिए कौन सी रक्त कोशिकाएं जिम्मेदार हैं?",
            optionA = "Red Blood Cells / लाल रक्त कोशिकाएं (RBC)",
            optionB = "White Blood Cells / श्वेत रक्त कोशिकाएं (WBC)",
            optionC = "Platelets / प्लेटलेट्स",
            optionD = "Plasma / प्लाज्मा",
            correctIndex = 1
        ),
        SimpleQuestion(
            questionText = "Q9. What is the pH value of absolutely pure water? / पूर्णतः शुद्ध उदासीन जल का pH मान कितना होता है?",
            optionA = "5.5",
            optionB = "7.0",
            optionC = "8.5",
            optionD = "6.0",
            correctIndex = 1
        ),
        SimpleQuestion(
            questionText = "Q10. Who discovered the basic laws of planetary motion? / ग्रहों की गति के नियमों की खोज किसने की थी?",
            optionA = "Johannes Kepler / जोहान्स केपलर",
            optionB = "Isaac Newton / आइजैक न्यूटन",
            optionC = "Albert Einstein / अल्बर्ट आइंस्टीन",
            optionD = "Galileo Galilei / गैलीलियो गैलीली",
            correctIndex = 0
        ),

        // === MATHEMATICS / गणित (10 Questions) ===
        SimpleQuestion(
            questionText = "Q11. What is the general solution for (x + y)²? / (x + y)² का मान क्या है?",
            optionA = "x² + y²",
            optionB = "x² + y² + 2xy",
            optionC = "x² + y² - 2xy",
            optionD = "x² - y²",
            correctIndex = 1
        ),
        SimpleQuestion(
            questionText = "Q12. What is the dynamic sum of the interior angles of a quadrilateral? / एक चतुर्भुज के चारों अंतः कोणों का योग कितना होता है?",
            optionA = "180 degrees / १८० अंश",
            optionB = "270 degrees / २७० अंश",
            optionC = "360 degrees / ३६० अंश",
            optionD = "90 degrees / ९० अंश",
            correctIndex = 2
        ),
        SimpleQuestion(
            questionText = "Q13. If length remains 12m and width is 5m, what is the area of a rectangle? / यदि लंबाई १२ मीटर और चौड़ाई ५ मीटर है, तो आयत का क्षेत्रफल ज्ञात करें?",
            optionA = "34 m² / ३४ वर्ग मीटर",
            optionB = "60 m² / ६० वर्ग मीटर",
            optionC = "17 m² / १७ वर्ग मीटर",
            optionD = "120 m² / १२० वर्ग मीटर",
            correctIndex = 1
        ),
        SimpleQuestion(
            questionText = "Q14. Solve the equation: 3x - 7 = 14. What is the value of x? / समीकरण हल करें: 3x - 7 = 14. x का मान क्या होगा?",
            optionA = "5",
            optionB = "6",
            optionC = "7",
            optionD = "8",
            correctIndex = 2
        ),
        SimpleQuestion(
            questionText = "Q15. What is the approximate mathematical ratio value of Pi (π)? / पाई (π) का लगभग गणितीय मान क्या है?",
            optionA = "3.14159 (or 22/7)",
            optionB = "2.718",
            optionC = "1.414",
            optionD = "1.732",
            correctIndex = 0
        ),
        SimpleQuestion(
            questionText = "Q16. Find the mean of numbers: 4, 8, 12, 16. / संख्याओं ४, ८, १२, १६ का माध्य (Mean) क्या है?",
            optionA = "8",
            optionB = "10",
            optionC = "12",
            optionD = "9",
            correctIndex = 1
        ),
        SimpleQuestion(
            questionText = "Q17. What is the highest common factor (HCF) of 24 and 36? / २४ और ३६ का महत्तम समापवर्तक (HCF / म.स.) क्या है?",
            optionA = "6",
            optionB = "12",
            optionC = "8",
            optionD = "18",
            correctIndex = 1
        ),
        SimpleQuestion(
            questionText = "Q18. If radius is 7 cm, what is the circumference of the circle? (Take π = 22/7) / यदि त्रिज्या ७ सेमी है, तो वृत्त की परिधि क्या होगी?",
            optionA = "44 cm / ४४ सेमी",
            optionB = "154 cm / १५४ सेमी",
            optionC = "22 cm / २२ सेमी",
            optionD = "88 cm / ८८ सेमी",
            correctIndex = 0
        ),
        SimpleQuestion(
            questionText = "Q19. What is the logarithmic value of 100 to the base 10? / आधार १० पर १०० का लघुगणक (log₁₀ 100) मान क्या होगा?",
            optionA = "1",
            optionB = "2",
            optionC = "3",
            optionD = "10",
            correctIndex = 1
        ),
        SimpleQuestion(
            questionText = "Q20. What is the value of sin(30 degrees)? / sin(30 डिग्री) का मान कितना होता है?",
            optionA = "0",
            optionB = "1",
            optionC = "1/2",
            optionD = "√3/2",
            correctIndex = 2
        ),

        // === SOCIAL SCIENCE / सामाजिक विज्ञान (10 Questions) ===
        SimpleQuestion(
            questionText = "Q21. Who was the founding Emperor of the Mughal Sultanate in India? / भारत में मुगल साम्राज्य की नींव किसने रखी थी?",
            optionA = "Akbar / अकबर",
            optionB = "Babur / बाबर",
            optionC = "Humayun / हुमायूँ",
            optionD = "Sher Shah Suri / शेर शाह सूरी",
            correctIndex = 1
        ),
        SimpleQuestion(
            questionText = "Q22. Which fundamental rights protector institution compiles the Indian Constitution? / भारतीय संविधान के निर्माता या मुख्य वास्तुकार किसे माना जाता है?",
            optionA = "Dr. B.R. Ambedkar / डॉ. बी.आर. अम्बेडकर",
            optionB = "Mahatma Gandhi / महात्मा गांधी",
            optionC = "Jawaharlal Nehru / जवाहरलाल नेहरू",
            optionD = "Dr. Rajendra Prasad / डॉ. राजेंद्र Prasad",
            correctIndex = 0
        ),
        SimpleQuestion(
            questionText = "Q23. In which historic year did Mahatma Gandhi start the Salt Dandi March? / महात्मा गांधी ने ऐतिहासिक नमक सत्याग्रह 'दांडी मार्च' किस वर्ष शुरू किया था?",
            optionA = "1920",
            optionB = "1930",
            optionC = "1942",
            optionD = "1915",
            correctIndex = 1
        ),
        SimpleQuestion(
            questionText = "Q24. Which is the longest flowing river completely within India? / भारत की सीमाओं के भीतर बहने वाली सबसे लंबी नदी कौन सी है?",
            optionA = "Goddavari / गोदावरी",
            optionB = "Narmada / नर्मदा",
            optionC = "Ganga / गंगा",
            optionD = "Yamuna / यमुना",
            correctIndex = 2
        ),
        SimpleQuestion(
            questionText = "Q25. Which continent is designated as the largest on Earth by geography? / विश्व का सबसे बड़ा महाद्वीप कौन सा है?",
            optionA = "Africa / अफ्रीका",
            optionB = "Europe / यूरोप",
            optionC = "North America / उत्तरी अमेरिका",
            optionD = "Asia / एशिया",
            correctIndex = 3
        ),
        SimpleQuestion(
            questionText = "Q26. Who was the first female President of the Indian National Congress? / भारतीय राष्ट्रीय कांग्रेस की प्रथम महिला अध्यक्ष कौन थीं?",
            optionA = "Sarojini Naidu / सरोजिनी नायडू",
            optionB = "Annie Besant / एनी बेसेंट",
            optionC = "Indira Gandhi / इंदिरा गांधी",
            optionD = "Sucheta Kripalani / सुचेता कृपलानी",
            correctIndex = 1
        ),
        SimpleQuestion(
            questionText = "Q27. In which state is the ancient Harappan archaeological site 'Lothal' located? / प्राचीन हड़प्पा स्थल 'लोथल' वर्तमान भारतीय राज्य में कहाँ स्थित है?",
            optionA = "Rajasthan / राजस्थान",
            optionB = "Punjab / पंजाब",
            optionC = "Gujarat / गुजरात",
            optionD = "Haryana / हरियाणा",
            correctIndex = 2
        ),
        SimpleQuestion(
            questionText = "Q28. Under which schedule is the official languages list stored? / हमारे संविधान की किस अनुसूची के तहत आधिकारिक भारतीय भाषाओं को मान्यता दी गई है?",
            optionA = "7th Schedule / ७वीं अनुसूची",
            optionB = "8th Schedule / ८वीं अनुसूची",
            optionC = "9th Schedule / ९वीं अनुसूची",
            optionD = "10th Schedule / १०वीं अनुसूची",
            correctIndex = 1
        ),
        SimpleQuestion(
            questionText = "Q29. What is the minimum eligible voting age for General Assembly elections in India? / भारत में लोक सभा और विधान सभा चुनावों में मतदान की न्यूनतम आयु कितनी है?",
            optionA = "18 years / १८ वर्ष",
            optionB = "21 years / २१ वर्ष",
            optionC = "25 years / २५ वर्ष",
            optionD = "16 years / १६ वर्ष",
            correctIndex = 0
        ),
        SimpleQuestion(
            questionText = "Q30. Which planet of our solar system is famously called the Earth's Twin? / हमारे सौरमंडल के किस ग्रह को पृथ्वी का जुड़वां ग्रह कहा जाता है?",
            optionA = "Mars / मंगल",
            optionB = "Venus / शुक्र",
            optionC = "Mercury / बुध",
            optionD = "Jupiter / बृहस्पति",
            correctIndex = 1
        ),

        // === HINDI GRAMMAR / हिंदी व्याकरण (10 Questions) ===
        SimpleQuestion(
            questionText = "Q31. Which of the following is correct Sandhi of 'Shivalay'? / 'शिवालय' शब्द का सही संधि विच्छेद क्या होगा?",
            optionA = "शिव + लय",
            optionB = "शिवा + आलय",
            optionC = "शिव + आलय",
            optionD = "शिव + वालय",
            correctIndex = 2
        ),
        SimpleQuestion(
            questionText = "Q32. What type of Samas is 'Yathashakti'? / 'यथाशक्ति' शब्द में कौन सा समास है?",
            optionA = "Avyayibhava Samas / अव्ययीभाव समास",
            optionB = "Tatpurusha Samas / तत्पुरुष समास",
            optionC = "Dvandva Samas / द्वंद्व समास",
            optionD = "Bahuvrihi Samas / बहुव्रीहि समास",
            correctIndex = 0
        ),
        SimpleQuestion(
            questionText = "Q33. What is the synonym of 'Prithvi' (Earth) from options? / निम्नलिखित में से 'पृथ्वी' का पर्यायवाची शब्द क्या है?",
            optionA = "पावक / Pavak",
            optionB = "वसुंधरा / Vasundhara",
            optionC = "गगन / Gagan",
            optionD = "किरण / Kiran",
            correctIndex = 1
        ),
        SimpleQuestion(
            questionText = "Q34. What is the exact antonym of 'Uday' (Rise)? / 'उदय' शब्द का सही विलोम (Antonym) शब्द क्या है?",
            optionA = "अस्त / Ast",
            optionB = "अंत / Ant",
            optionC = "नाश / Nash",
            optionD = "पतन / Patan",
            correctIndex = 0
        ),
        SimpleQuestion(
            questionText = "Q35. What does the Muhavra 'Nau-Do-Gyarah Hona' mean? / 'नौ दो ग्यारह होना' मुहावरे का सही अर्थ क्या है?",
            optionA = "भाग जाना / To run away",
            optionB = "गणना करना / To calculate",
            optionC = "मदद करना / To assist",
            optionD = "विरोध करना / To oppose",
            correctIndex = 0
        ),
        SimpleQuestion(
            questionText = "Q36. Find the feminine gender for the noun 'Nayyak' (Hero). / 'नायक' शब्द का स्त्रीलिंग रूप क्या होगा?",
            optionA = "नायिका / Nayika",
            optionB = "नायिकी / Nayiki",
            optionC = "नायकिनी / Nayikini",
            optionD = "नायकी / Nayaki",
            correctIndex = 0
        ),
        SimpleQuestion(
            questionText = "Q37. Identify the type of sentence: 'वाह! कितना सुंदर दृश्य है।' / 'वाह! कितना सुंदर दृश्य है।' यह किस प्रकार का वाक्य है?",
            optionA = "विधानवाचक / Declarative",
            optionB = "विस्मयादिबोधक / Exclamatory",
            optionC = "निषेधवाचक / Negative",
            optionD = "प्रश्नवाचक / Interrogative",
            correctIndex = 1
        ),
        SimpleQuestion(
            questionText = "Q38. How many kinds of 'Karak' (Cases) exist in Hindi Grammar? / हिंदी व्याकरण में कारक (Karak) के कुल कितने भेद होते हैं?",
            optionA = "६ / Six",
            optionB = "७ / Seven",
            optionC = "८ / Eight",
            optionD = "१० / Ten",
            correctIndex = 2
        ),
        SimpleQuestion(
            questionText = "Q39. In the sentence 'वह पुस्तक पढ़ता है', which is the Verb (Kriya)? / 'वह पुस्तक पढ़ता है' वाक्य में क्रिया (Verb) पद कौन सा है?",
            optionA = "वह / He",
            optionB = "पुस्तक / Book",
            optionC = "पढ़ता है / Reads",
            optionD = "है / Is",
            correctIndex = 2
        ),
        SimpleQuestion(
            questionText = "Q40. What is the core emotion (Sthayi Bhava) of 'Vira Rasa' (Heroic emotion)? / 'वीर रस' का स्थायी भाव (Sthayi Bhava) क्या होता है?",
            optionA = "उत्साह / Enthusiasm (Utsah)",
            optionB = "क्रोध / Anger",
            optionC = "भय / Fear",
            optionD = "शोक / Sorrow",
            correctIndex = 0
        ),

        // === ENGLISH GRAMMAR / अंग्रेजी व्याकरण (10 Questions) ===
        SimpleQuestion(
            questionText = "Q41. Choose the correct preposition: 'She has been studying ___ morning.' / रिक्त स्थान भरें: 'She has been studying ___ morning.'",
            optionA = "for",
            optionB = "since",
            optionC = "from",
            optionD = "by",
            correctIndex = 1
        ),
        SimpleQuestion(
            questionText = "Q42. Identify the Synonym of 'ABUNDANT'. / 'ABUNDANT' शब्द का समानार्थी शब्द (Synonym) चुनें।",
            optionA = "Sparse / अल्प",
            optionB = "Plentiful / प्रचुर",
            optionC = "Scarce / दुर्लभ",
            optionD = "Heavy / भारी",
            correctIndex = 1
        ),
        SimpleQuestion(
            questionText = "Q43. Select the Antonym of the word 'HOSTILE'. / 'HOSTILE' शब्द का विलोम (Antonym) शब्द चुनें।",
            optionA = "Aggressive / आक्रामक",
            optionB = "Friendly / मैत्रीपूर्ण",
            optionC = "Distant / दूर का",
            optionD = "Bitter / कड़वा",
            correctIndex = 1
        ),
        SimpleQuestion(
            questionText = "Q44. What is the plural form of the noun 'CRITERION'? / संज्ञा शब्द 'CRITERION' का बहुवचन (Plural) रूप क्या है?",
            optionA = "Criterions",
            optionB = "Criteria",
            optionC = "Criterias",
            optionD = "Criterios",
            correctIndex = 1
        ),
        SimpleQuestion(
            questionText = "Q45. Fill in the blank with correct auxiliary verb: 'Either Ram or his friends ___ coming today.' / रिक्त स्थान भरें: 'Either Ram or his friends ___ coming today.'",
            optionA = "is",
            optionB = "are",
            optionC = "was",
            optionD = "has",
            correctIndex = 1
        ),
        SimpleQuestion(
            questionText = "Q46. Which of the following words is correctly spelled? / निम्नलिखित में से किस शब्द की वर्तनी (Spelling) सही है?",
            optionA = "Receive",
            optionB = "Recieve",
            optionC = "Receve",
            optionD = "Riceive",
            correctIndex = 0
        ),
        SimpleQuestion(
            questionText = "Q47. Identify the tense form in: 'By next year, I will have finished my college.' / 'By next year, I will have finished my college.' में कौन सा लकार/काल है?",
            optionA = "Simple Future / सामान्य भविष्यकाल",
            optionB = "Future Continuous / अपूर्ण भविष्यकाल",
            optionC = "Future Perfect / पूर्ण भविष्यकाल",
            optionD = "Future Perfect Continuous / पूर्ण-तत भविष्यकाल",
            correctIndex = 2
        ),
        SimpleQuestion(
            questionText = "Q48. Select the correct pronoun: 'One must do ___ duty sincerely.' / रिक्त स्थान के लिए उपयुक्त सर्वनाम चुनें: 'One must do ___ duty sincerely.'",
            optionA = "his",
            optionB = "her",
            optionC = "one's",
            optionD = "their",
            correctIndex = 2
        ),
        SimpleQuestion(
            questionText = "Q49. What does the idiom 'Spill the beans' mean? / अंग्रेजी मुहावरे 'Spill the beans' का सही अर्थ क्या है?",
            optionA = "To plant seeds / बीज बोना",
            optionB = "To reveal a secret / रहस्य उजागर करना",
            optionC = "To make a mistake / गलती करना",
            optionD = "To work hard / कठिन परिश्रम करना",
            correctIndex = 1
        ),
        SimpleQuestion(
            questionText = "Q50. Change the sentence to Passive voice: 'He killed a snake.' / 'He killed a snake' को कर्मवाच्य (Passive voice) में परिवर्तित करें।",
            optionA = "A snake is killed by him.",
            optionB = "A snake was killed by him.",
            optionC = "A snake had been killed by him.",
            optionD = "A snake was being killed by him.",
            correctIndex = 1
        )
    )
}
