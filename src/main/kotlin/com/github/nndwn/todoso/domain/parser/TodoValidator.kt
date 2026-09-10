package com.github.nndwn.todoso.domain.parser

import com.github.nndwn.todoso.domain.model.Metadata
import com.github.nndwn.todoso.domain.model.Priority

object TodoValidator {

  /**
   * Memeriksa apakah sebuah teks (baik raw line atau hanya deskripsi) memiliki konten nyata
   * setelah semua metadata (tags, priority, dates, id) dibersihkan.
   */
  fun isContentValid(text: String?): Boolean {
    if (text.isNullOrBlank()) return false

    // 1. Bersihkan komentar (//) jika ada
    val contentOnly = stripComment(text)
    if (contentOnly.isBlank()) return false

    // 2. Hapus Prefix Checkbox jika ada (misal: - [ ])
    var clean = contentOnly.replaceFirst(Regex("""^\s*-\s*\[[\s/xX-]?]"""), "")

    // 3. Hapus Tags
    clean = clean.replace(TagParser.TAG_REGEX, "")

    // 4. Hapus Task ID (🆔)
    clean = clean.replace(TaskIdParser.TASK_ID_REGEX, "")

    // 5. Hapus Date Emojis dan nilainya secara dinamis
    val dateEmojis = Metadata.DATE_EMOJIS.joinToString("")
    clean = clean.replace(Regex("""[$dateEmojis](\s*\d{4}-\d{2}-\d{2}(\s\d{2}:\d{2})?)?"""), "")

    // 6. Hapus Priority (Emoji dan Bracket Code) secara dinamis
    val priorityEmojis = Priority.entries.mapNotNull { it.emoji.takeIf { e -> e.isNotEmpty() } }.joinToString("")
    val priorityCodes = Priority.entries.mapNotNull { it.code.takeIf { c -> c.isNotEmpty() } }
    val priorityLabels = Priority.entries.mapNotNull { it.label.takeIf { l -> l.isNotEmpty() } }
    val combinedPriorityText = (priorityCodes + priorityLabels).joinToString("|")

    // Hapus format [H], [High], dll
    clean = clean.replace(Regex("""\[\s*($combinedPriorityText)\s*]""", RegexOption.IGNORE_CASE), "")
    // Hapus emoji 🔺, dll
    if (priorityEmojis.isNotEmpty()) {
      clean = clean.replace(Regex("[$priorityEmojis]"), "")
    }

    // Jika setelah dikuliti masih ada karakter selain spasi, maka valid
    return clean.trim().isNotEmpty()
  }
}
