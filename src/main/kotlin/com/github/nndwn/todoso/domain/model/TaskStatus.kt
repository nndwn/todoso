package com.github.nndwn.todoso.domain.model

import com.github.nndwn.todoso.TodosoBundle
import com.github.nndwn.todoso.TodosoIcons
import javax.swing.Icon

enum class TaskStatus(val code: String, val icon: Icon) {
  DOING("/", TodosoIcons.TaskDoing),
  TODO(" ", TodosoIcons.TaskTodo),
  DONE("x", TodosoIcons.TaskDone),
  CANCELLED("-", TodosoIcons.TaskCancelled);

  val displayName: String
    get() = TodosoBundle.message("status.${name.lowercase()}")

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
