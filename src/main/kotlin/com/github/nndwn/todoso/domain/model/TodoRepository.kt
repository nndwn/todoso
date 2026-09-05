package com.github.nndwn.todoso.domain.model

enum class TaskStatus(val code: String) {
    DOING("/"),
    TODO(" "),
    DONE("x"),
    CANCELLED("-");

    companion object {

        private val LINE_START_STATUS_REGEX = Regex("""^\s*-\s*\[([\s/xX-])?]""")

        /**
         * Parses the TaskStatus from a raw line.
         * Returns null if the line is not a valid task line (e.g., plain text or description text).
         */
        fun parseFromLineStart(rawLine: String): TaskStatus? {
            val matchResult = LINE_START_STATUS_REGEX.find(rawLine) ?: return null
            val code = matchResult.groupValues[1].ifEmpty { " " }

            // Map 'X' to 'x' so it resolves to TaskStatus.DONE
            val normalizedCode = if (code.equals("X", ignoreCase = true)) "x" else code
            return entries.find { it.code == normalizedCode } ?: TODO
        }

        /**
         * Checks whether the given line is a valid Markdown task line.
         */
        fun isTaskLine(rawLine: String): Boolean {
            return LINE_START_STATUS_REGEX.containsMatchIn(rawLine)
        }
    }
}

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
        // Regex to match priority brackets immediately following the status marker.
        // Captures inside brackets, allowing flexible spacing e.g., [  h  ], [HIGHEST], [HH]
        private val PRIORITY_BRACKET_REGEX = Regex("""^\s*-\s*\[[\s/xX-]?]\s*\[\s*([a-zA-Z]+)\s*]""")

        /**
         * Parses priority from a raw line.
         * Must be positioned strictly right after the task status prefix.
         */
        fun parseFromLine(rawLine: String): Priority {
            if (rawLine.isBlank()) return NONE

            // 1. Check for Obsidian Priority Emojis first
            entries.forEach { priority ->
                if (priority.emoji.isNotEmpty() && rawLine.contains(priority.emoji)) {
                    return priority
                }
            }

            val matchResult = PRIORITY_BRACKET_REGEX.find(rawLine)
            if (matchResult != null) {
                val innerText = matchResult.groupValues[1].trim()

                return entries.find { priority ->
                    priority.code.equals(innerText, ignoreCase = true) ||
                            priority.label.equals(innerText, ignoreCase = true)
                } ?: NONE
            }

            return NONE
        }
    }
}


data class TodoTask(
    val id : String,
    val isPersistentId : Boolean,
    val rawText : String,
    val description : String,
    val status : TaskStatus,
    val priority : Priority,
    val tags : List<String>,
    val dates : Map<String, String>,
    val lineNumber : Int
)