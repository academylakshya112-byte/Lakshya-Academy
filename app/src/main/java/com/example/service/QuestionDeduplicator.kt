package com.example.service

import com.example.data.QuestionEntity

object QuestionDeduplicator {

    data class NormalizedQuestion(
        val rawQuestionText: String,
        val englishStem: String,
        val hindiStem: String,
        val englishTokens: Set<String>,
        val hindiTokens: Set<String>,
        val options: List<String>,
        val normalizedOptions: List<String>
    )

    private val STOP_WORDS = setOf(
        "what", "is", "the", "of", "in", "and", "a", "an", "which", "following", "for", "to", "are", "from",
        "by", "with", "as", "on", "at", "it", "or", "who", "when", "where", "how", "why", "can", "be", "has", "have",
        "select", "correct", "statement", "find", "option", "given", "below", "following", "state", "true", "false",
        "ka", "ki", "ke", "hai", "kya", "ko", "se", "mein", "par", "aur", "ya", "kon", "konsa", "konsi", "kab", "kahan", "hun", "ho", "hain"
    )

    fun cleanQuestionText(text: String): String {
        return if (text.contains("---METADATA---")) {
            text.substringBefore("---METADATA---").trim()
        } else {
            text.trim()
        }
    }

    fun normalizeText(text: String): String {
        val clean = cleanQuestionText(text)
        return clean.lowercase()
            .replace(Regex("[^a-z0-9\\u0900-\\u097F\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun extractParts(text: String): Pair<String, String> {
        val clean = cleanQuestionText(text)
        if (!clean.contains(" / ")) {
            return Pair(clean, clean)
        }
        val parts = clean.split(" / ", limit = 2)
        val eng = parts[0].trim()
        val hin = parts.getOrNull(1)?.trim() ?: eng
        return Pair(eng, hin)
    }

    fun extractTokens(text: String): Set<String> {
        val normalized = normalizeText(text)
        if (normalized.isBlank()) return emptySet()
        return normalized.split(" ")
            .filter { it.length > 1 && !STOP_WORDS.contains(it) }
            .toSet()
    }

    fun toNormalizedQuestion(
        questionText: String,
        optionA: String,
        optionB: String,
        optionC: String,
        optionD: String
    ): NormalizedQuestion {
        val (eng, hin) = extractParts(questionText)
        val opts = listOf(optionA, optionB, optionC, optionD)
        val normOpts = opts.map { normalizeText(it) }
        return NormalizedQuestion(
            rawQuestionText = questionText,
            englishStem = normalizeText(eng),
            hindiStem = normalizeText(hin),
            englishTokens = extractTokens(eng),
            hindiTokens = extractTokens(hin),
            options = opts,
            normalizedOptions = normOpts
        )
    }

    fun toNormalizedQuestion(q: MockTestGenerator20.GeneratedQuestion): NormalizedQuestion {
        return toNormalizedQuestion(q.questionText, q.optionA, q.optionB, q.optionC, q.optionD)
    }

    fun toNormalizedQuestion(q: QuestionEntity): NormalizedQuestion {
        return toNormalizedQuestion(q.questionText, q.optionA, q.optionB, q.optionC, q.optionD)
    }

    /**
     * Rule 8 & Internal Validity Check:
     * - Question text must not be empty or too short.
     * - Options A, B, C, D must all be distinct from each other within a question.
     */
    fun isInternalValid(q: NormalizedQuestion): Boolean {
        if (q.englishStem.length < 3 && q.hindiStem.length < 3) return false
        if (q.normalizedOptions.any { it.isBlank() }) return false
        // Ensure options A, B, C, D are all distinct
        if (q.normalizedOptions.distinct().size < 4) return false
        return true
    }

    /**
     * Compare candidate question against existing question.
     * Returns true if candidate is duplicate or semantically identical.
     */
    fun isDuplicate(candidate: NormalizedQuestion, existing: NormalizedQuestion): Boolean {
        if (!isInternalValid(candidate)) return true

        // 1. Exact English stem equality
        if (candidate.englishStem.isNotEmpty() && candidate.englishStem == existing.englishStem) return true

        // 2. Exact Hindi stem equality
        if (candidate.hindiStem.isNotEmpty() && candidate.hindiStem == existing.hindiStem) return true

        // 3. Cross-language stem equality
        if (candidate.englishStem.isNotEmpty() && candidate.englishStem == existing.hindiStem) return true
        if (candidate.hindiStem.isNotEmpty() && candidate.hindiStem == existing.englishStem) return true

        // 4. Token Overlap Similarity (English)
        if (candidate.englishTokens.isNotEmpty() && existing.englishTokens.isNotEmpty()) {
            val intersection = candidate.englishTokens.intersect(existing.englishTokens).size
            val minSize = minOf(candidate.englishTokens.size, existing.englishTokens.size)
            if (minSize > 0) {
                val ratio = intersection.toDouble() / minSize.toDouble()
                if (ratio >= 0.70) return true
            }
        }

        // 5. Token Overlap Similarity (Hindi)
        if (candidate.hindiTokens.isNotEmpty() && existing.hindiTokens.isNotEmpty()) {
            val intersection = candidate.hindiTokens.intersect(existing.hindiTokens).size
            val minSize = minOf(candidate.hindiTokens.size, existing.hindiTokens.size)
            if (minSize > 0) {
                val ratio = intersection.toDouble() / minSize.toDouble()
                if (ratio >= 0.70) return true
            }
        }

        // 6. Option set overlap + moderate stem overlap
        val optionOverlap = candidate.normalizedOptions.intersect(existing.normalizedOptions.toSet()).size
        if (optionOverlap >= 3) {
            val engRatio = if (candidate.englishTokens.isNotEmpty() && existing.englishTokens.isNotEmpty()) {
                candidate.englishTokens.intersect(existing.englishTokens).size.toDouble() / minOf(candidate.englishTokens.size, existing.englishTokens.size)
            } else 0.0
            val hinRatio = if (candidate.hindiTokens.isNotEmpty() && existing.hindiTokens.isNotEmpty()) {
                candidate.hindiTokens.intersect(existing.hindiTokens).size.toDouble() / minOf(candidate.hindiTokens.size, existing.hindiTokens.size)
            } else 0.0
            if (engRatio >= 0.50 || hinRatio >= 0.50) return true
        }

        return false
    }

    fun isDuplicateAgainstList(
        candidate: NormalizedQuestion,
        existingList: List<NormalizedQuestion>
    ): Boolean {
        if (!isInternalValid(candidate)) return true
        return existingList.any { isDuplicate(candidate, it) }
    }

    fun isDuplicateAgainstList(
        q: MockTestGenerator20.GeneratedQuestion,
        existingList: List<MockTestGenerator20.GeneratedQuestion>
    ): Boolean {
        val candNorm = toNormalizedQuestion(q)
        val existingNorm = existingList.map { toNormalizedQuestion(it) }
        return isDuplicateAgainstList(candNorm, existingNorm)
    }

    fun isDuplicateAgainstList(
        q: QuestionEntity,
        existingList: List<QuestionEntity>
    ): Boolean {
        val candNorm = toNormalizedQuestion(q)
        val existingNorm = existingList.map { toNormalizedQuestion(it) }
        return isDuplicateAgainstList(candNorm, existingNorm)
    }
}
