package com.github.nndwn.todoso.domain.model

import com.intellij.ui.JBColor
import java.awt.Color

enum class Priority(
    val emoji: String,
    val code: String,
    val label: String,
    val color : Color
) {
    HIGHEST("🔺", "HH", "Highest", JBColor.MAGENTA),
    HIGH("⏫", "H", "High", JBColor.RED),
    MEDIUM("🔼", "M", "Medium",JBColor.ORANGE),
    LOW("🔽", "L", "Low", JBColor.BLUE),
    LOWEST("⏬", "LL", "Lowest", JBColor.CYAN),
    NONE("", "", "None",JBColor.GRAY);

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
