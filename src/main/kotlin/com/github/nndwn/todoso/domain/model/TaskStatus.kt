package com.github.nndwn.todoso.domain.model

enum class TaskStatus(val code: String) {
    DOING("/"),
    TODO(" "),
    DONE("x"),
    CANCELLED("-");

    companion object {
        private val LINE_START_STATUS_REGEX = Regex("""^\s*-\s*\[([\s/xX-])?]""")

        fun parseFromLineStart(rawLine: String): TaskStatus? {
            val matchResult = LINE_START_STATUS_REGEX.find(rawLine) ?: return null
            val code = matchResult.groupValues[1].ifEmpty { " " }

            val normalizedCode = if (code.equals("X", ignoreCase = true)) "x" else code
            return entries.find { it.code == normalizedCode } ?: TODO
        }

        fun isTaskLine(rawLine: String): Boolean {
            return LINE_START_STATUS_REGEX.containsMatchIn(rawLine)
        }
    }
}