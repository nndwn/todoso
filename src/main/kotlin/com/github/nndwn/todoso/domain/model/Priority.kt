package com.github.nndwn.todoso.domain.model

enum class Priority(
    val emoji: String,
    val code: String,
    val label: String
) {
    HIGHEST("🔺", "HH", "Highest"),
    HIGH("⏫", "H", "High"),
    MEDIUM("🔼", "M", "Medium"),
    LOW("🔽", "L", "Low"),
    LOWEST("⏬", "LL", "Lowest"),
    NONE("", "", "None");

    companion object {
        private val STRICT_PRIORITY_REGEX = Regex(
            """^\s*-\s*\[[\s/xX-]?]\s*(?:\[\s*([a-zA-Z]+)\s*]|([🔺⏫🔼🔽⏬]))"""
        )

        fun parseFromLine(rawLine: String): Priority {
            if (rawLine.isBlank()) return NONE

            val matchResult = STRICT_PRIORITY_REGEX.find(rawLine) ?: return NONE

            val bracketText = matchResult.groupValues[1]
            if (bracketText.isNotEmpty()) {
                val cleanText = bracketText.trim()
                return entries.find { priority ->
                    priority.code.equals(cleanText, ignoreCase = true) ||
                            priority.label.equals(cleanText, ignoreCase = true)
                } ?: NONE
            }

            val emojiText = matchResult.groupValues[2]
            if (emojiText.isNotEmpty()) {
                return entries.find { it.emoji == emojiText } ?: NONE
            }

            return NONE
        }
    }
}
