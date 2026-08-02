package com.example.service

import android.util.Log

object SmartQuestionParser {
    private const val TAG = "SmartQuestionParser"

    fun parsePastedQuestions(
        rawText: String,
        defaultSubject: String = "General",
        defaultDifficulty: String = "Medium"
    ): List<MockTestGenerator20.GeneratedQuestion> {
        if (rawText.isBlank()) return emptyList()

        val parsedQuestions = mutableListOf<MockTestGenerator20.GeneratedQuestion>()
        
        // Normalize newlines and trim text
        val cleanedText = rawText.replace("\r\n", "\n").replace("\r", "\n").trim()
        
        // Split by double newlines or question number headers
        val blocks = splitIntoQuestionBlocks(cleanedText)

        for (block in blocks) {
            val q = parseSingleBlock(block, defaultSubject, defaultDifficulty)
            if (q != null) {
                parsedQuestions.add(q)
            }
        }

        Log.d(TAG, "Parsed ${parsedQuestions.size} questions from raw text.")
        return parsedQuestions
    }

    private fun splitIntoQuestionBlocks(text: String): List<String> {
        val lines = text.split("\n")
        val blocks = mutableListOf<String>()
        var currentBlock = StringBuilder()

        // Regex for question starters like: "1.", "1)", "Q1.", "Q.1", "Q1)", "1 -", "[1]"
        val questionStartRegex = Regex("""^(?:\bQ(?:uestion)?\.?\s*\d+|\b\d{1,3}\b\s*[.\-)]|\[\d{1,3}\])\s+.*""", RegexOption.IGNORE_CASE)

        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isBlank()) {
                if (currentBlock.isNotEmpty()) {
                    currentBlock.append("\n")
                }
                continue
            }

            if (questionStartRegex.matches(trimmedLine) && currentBlock.isNotEmpty() && containsOptionsOrAnswer(currentBlock.toString())) {
                blocks.add(currentBlock.toString().trim())
                currentBlock = StringBuilder()
            }

            if (currentBlock.isNotEmpty()) {
                currentBlock.append("\n")
            }
            currentBlock.append(trimmedLine)
        }

        if (currentBlock.isNotEmpty()) {
            blocks.add(currentBlock.toString().trim())
        }

        return blocks
    }

    private fun containsOptionsOrAnswer(block: String): Boolean {
        val lower = block.lowercase()
        return lower.contains("a.") || lower.contains("a)") || lower.contains("(a)") ||
               lower.contains("ans") || lower.contains("answer") || lower.contains("उत्तर")
    }

    private fun parseSingleBlock(
        block: String,
        subject: String,
        difficulty: String
    ): MockTestGenerator20.GeneratedQuestion? {
        val lines = block.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        val questionTextBuilder = StringBuilder()
        var optionA = ""
        var optionB = ""
        var optionC = ""
        var optionD = ""
        var correctIndex = -1
        var explanation = ""
        var imageUrl = ""

        // Regex patterns for options
        val optionARegex = Regex("""^(?:[Aa][.\-)]|\([Aa]\)|[Aa]\s*[:\-])\s*(.*)""")
        val optionBRegex = Regex("""^(?:[Bb][.\-)]|\([Bb]\)|[Bb]\s*[:\-])\s*(.*)""")
        val optionCRegex = Regex("""^(?:[Cc][.\-)]|\([Cc]\)|[Cc]\s*[:\-])\s*(.*)""")
        val optionDRegex = Regex("""^(?:[Dd][.\-)]|\([Dd]\)|[Dd]\s*[:\-])\s*(.*)""")

        // Regex pattern for numeric options 1., 2., 3., 4. if letters are not found
        val opt1Regex = Regex("""^(?:1[.\-)]|\(1\)|1\s*[:\-])\s*(.*)""")
        val opt2Regex = Regex("""^(?:2[.\-)]|\(2\)|2\s*[:\-])\s*(.*)""")
        val opt3Regex = Regex("""^(?:3[.\-)]|\(3\)|3\s*[:\-])\s*(.*)""")
        val opt4Regex = Regex("""^(?:4[.\-)]|\(4\)|4\s*[:\-])\s*(.*)""")

        // Regex patterns for Answer and Explanation
        val answerRegex = Regex("""^(?:ans(?:wer)?|correct\s*ans(?:wer)?|उत्तर|सही\s*उत्तर|option)\s*[:\-\s=]+\s*([A-Da-d1-4])(?:\s*.*)?$""", RegexOption.IGNORE_CASE)
        val expRegex = Regex("""^(?:exp(?:lanation)?|व्याख्या|reason|detail)\s*[:\-\s=]+\s*(.*)$""", RegexOption.IGNORE_CASE)
        val imgRegex = Regex("""^(?:image|img|pic|url)\s*[:\-\s=]+\s*(https?://[^\s]+)""", RegexOption.IGNORE_CASE)

        var parsingPhase = 0 // 0: Question, 1: Options/Answer/Exp

        for (line in lines) {
            val imgMatch = imgRegex.find(line)
            if (imgMatch != null) {
                imageUrl = imgMatch.groupValues[1].trim()
                continue
            }

            val ansMatch = answerRegex.find(line)
            if (ansMatch != null) {
                parsingPhase = 1
                val valStr = ansMatch.groupValues[1].uppercase()
                correctIndex = when (valStr) {
                    "A", "1" -> 0
                    "B", "2" -> 1
                    "C", "3" -> 2
                    "D", "4" -> 3
                    else -> -1
                }
                continue
            }

            val expMatch = expRegex.find(line)
            if (expMatch != null) {
                parsingPhase = 1
                explanation = expMatch.groupValues[1].trim()
                continue
            }

            val matchA = optionARegex.matchEntire(line) ?: opt1Regex.matchEntire(line)
            if (matchA != null) {
                parsingPhase = 1
                optionA = matchA.groupValues[1].trim()
                continue
            }

            val matchB = optionBRegex.matchEntire(line) ?: opt2Regex.matchEntire(line)
            if (matchB != null) {
                parsingPhase = 1
                optionB = matchB.groupValues[1].trim()
                continue
            }

            val matchC = optionCRegex.matchEntire(line) ?: opt3Regex.matchEntire(line)
            if (matchC != null) {
                parsingPhase = 1
                optionC = matchC.groupValues[1].trim()
                continue
            }

            val matchD = optionDRegex.matchEntire(line) ?: opt4Regex.matchEntire(line)
            if (matchD != null) {
                parsingPhase = 1
                optionD = matchD.groupValues[1].trim()
                continue
            }

            if (parsingPhase == 0) {
                if (questionTextBuilder.isNotEmpty()) questionTextBuilder.append("\n")
                questionTextBuilder.append(line)
            } else if (explanation.isNotBlank()) {
                // Additional explanation lines
                explanation += "\n" + line
            }
        }

        var rawQuestionText = questionTextBuilder.toString().trim()
        
        // Strip question number prefix like "1. ", "Q1) ", etc.
        val cleanQRegex = Regex("""^(?:\bQ(?:uestion)?\.?\s*\d+|\b\d{1,3}\b\s*[.\-)]|\[\d{1,3}\])\s*""", RegexOption.IGNORE_CASE)
        rawQuestionText = cleanQRegex.replace(rawQuestionText, "").trim()

        // Fallback default answer if not found
        if (correctIndex == -1) {
            correctIndex = 0 // Default to option A
        }

        // Validate minimum criteria
        if (rawQuestionText.isBlank() || optionA.isBlank() || optionB.isBlank()) {
            return null
        }

        // Fill empty options C or D with placeholder if missing
        if (optionC.isBlank()) optionC = "None of the above / उपरोक्त में से कोई नहीं"
        if (optionD.isBlank()) optionD = "All of the above / उपरोक्त सभी"

        return MockTestGenerator20.GeneratedQuestion(
            questionText = rawQuestionText,
            optionA = optionA,
            optionB = optionB,
            optionC = optionC,
            optionD = optionD,
            correctIndex = correctIndex,
            explanation = if (explanation.isBlank()) "Standard solution / मानक हल" else explanation,
            subject = subject,
            chapter = "General",
            difficulty = difficulty,
            imageUrl = imageUrl
        )
    }
}
