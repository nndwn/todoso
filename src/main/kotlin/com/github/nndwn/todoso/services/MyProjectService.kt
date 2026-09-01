package com.github.nndwn.todoso.services

import com.github.nndwn.todoso.MyBundle
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.JBColor
import java.awt.Color

@Service(Service.Level.PROJECT)
class MyProjectService(private val project: Project) {

  enum class Priority(val emojis: List<String>, val code: String, val label: String, val color: Color) {
    HIGHEST(listOf("🔺"), "HH", "Highest", JBColor.MAGENTA),
    HIGH(listOf("⏫"), "H", "High", JBColor.RED),
    MEDIUM(listOf("🔼"), "M", "Medium", JBColor.ORANGE),
    LOW(listOf("🔽"), "L", "Low", JBColor.BLUE),
    LOWEST(listOf("⏬"), "LL", "Lowest", JBColor.CYAN),
    NONE(emptyList(), "", "None", JBColor.GRAY);

    companion object {
      /** Mencari prioritas dan mengembalikan pair (Priority, StringYangCocok) */
      fun findPriority(text: String): Pair<Priority, String?> {
        // 1. Cek Legacy Bracket [HH], [H], [M], [L], [LL]
        val bracketMatch = Regex("""\[(HH|H|M|LL|L)]""").find(text)
        if (bracketMatch != null) {
          val code = bracketMatch.groupValues[1]
          val p = entries.find { it.code == code } ?: NONE
          return p to bracketMatch.value
        }

        // 2. Cek Emoji Obsidian
        entries.forEach { p ->
          p.emojis.forEach { emoji ->
            if (text.contains(emoji)) return p to emoji
          }
        }
        return NONE to null
      }
    }
  }

  enum class TaskStatus(val code: String) {
    DOING("/"),
    TODO(" "),
    DONE("x"),
    CANCELLED("-");

    companion object {
      fun fromCode(code: String): TaskStatus = entries.find { it.code == code } ?: TODO
    }
  }

  data class TodoTask(
    val rawText: String,
    val description: String,
    val status: TaskStatus,
    val priority: Priority,
    val tags: List<String>,
    val dates: Map<String, String>,
  )

  init {
    thisLogger().info(MyBundle["projectService", project.name])
  }

  fun getTodoTasks(): List<TodoTask> {
    val projectDir = project.guessProjectDir() ?: return emptyList()
    val settings = MyProjectSettingsService.getInstance(project)
    val todoFile =
      projectDir.children.find { it.name.equals(settings.state.todoFileName, ignoreCase = true) } ?: return emptyList()

    val taskRegex = Regex("""^- \[([ x/-])] (.*)$""")
    val tagRegex = Regex("""#([\w.-]+)""")
    val dateEmojis = listOf("🛫", "📅", "⏳", "✅", "➕")

    return VfsUtil.loadText(todoFile)
      .lines()
      .mapNotNull { line ->
        val trimmed = line.trim()
        val match = taskRegex.find(trimmed) ?: return@mapNotNull null

        val statusCode = match.groupValues[1]
        var rawContent = match.groupValues[2].trim()

        // 1. Ekstrak Priority secara bedah (surgical)
        val (priority, matchedPriorityText) = Priority.findPriority(rawContent)
        if (matchedPriorityText != null) {
          // Hanya hapus SATU kemunculan pertama yang dianggap sebagai penanda
          rawContent = rawContent.replaceFirst(matchedPriorityText, "").trim()
        }

        // 2. Ekstrak Dates (Obsidian style)
        val dates = mutableMapOf<String, String>()
        dateEmojis.forEach { emoji ->
          // Mendukung YYYY-MM-DD dan opsional HH:mm atau HH:mm:ss
          val dateRegex = Regex("""$emoji\s*(\d{4}-\d{2}-\d{2}(?:\s+\d{2}:\d{2}(?::\d{2})?)?)""")
          dateRegex.find(rawContent)?.let {
            dates[emoji] = it.groupValues[1]
            rawContent = rawContent.replace(it.value, "").trim()
          }
        }

        // 3. Fallback Legacy // started: ...
        val timeSeparatorIndex = rawContent.lastIndexOf(" // ")
        if (timeSeparatorIndex != -1) {
          val legacyTime = rawContent.substring(timeSeparatorIndex + 4).trim()
          dates["//"] = legacyTime
          rawContent = rawContent.substring(0, timeSeparatorIndex).trim()
        }

        // 4. Ekstrak Tags
        val tags = tagRegex.findAll(rawContent).map { it.groupValues[1] }.toList()

        TodoTask(
          line,
          rawContent,
          TaskStatus.fromCode(statusCode),
          priority,
          tags,
          dates,
        )
      }
      .sortedWith(compareBy({ it.status.ordinal }, { it.priority.ordinal }))
  }

  /** Mendapatkan semua tag unik yang ada di todo.md */
  fun getAllTags(): Set<String> {
    return getTodoTasks().flatMap { it.tags }.toSet()
  }
}
