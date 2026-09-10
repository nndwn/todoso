package com.github.nndwn.todoso.domain.model

import com.intellij.ui.JBColor
import java.awt.Color

enum class Priority(
  val emoji: String,
  val code: String,
  val label: String,
  val color: Color,
) {
  HIGHEST("🔺", "HH", "Highest", JBColor(0xA626A4, 0xDA70D6)),
  HIGH("⏫", "H", "High", JBColor(0xE45649, 0xFF6B68)),
  MEDIUM("🔼", "M", "Medium", JBColor(0x986801, 0xD19A66)),
  LOW("🔽", "L", "Low", JBColor(0x4078F2, 0x61AFEF)),
  LOWEST("⏬", "LL", "Lowest", JBColor(0x0184BC, 0x56B6C2)),
  NONE("", "", "None", JBColor(0xABB2BF, 0x5C6370));

  companion object {
    private val STRICT_PRIORITY_REGEX = Regex("""^\s*-\s*\[[\s/xX-]?]\s*(?:\[\s*([a-zA-Z]+)\s*]|([🔺⏫🔼🔽⏬]))""")

    fun parseFromLine(rawLine: String): Priority {
      if (rawLine.isBlank()) return NONE

      val matchResult = STRICT_PRIORITY_REGEX.find(rawLine) ?: return NONE

      val bracketText = matchResult.groupValues[1]
      if (bracketText.isNotEmpty()) {
        val cleanText = bracketText.trim()
        return entries.find { priority ->
          priority.code.equals(cleanText, ignoreCase = true) || priority.label.equals(cleanText, ignoreCase = true)
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
