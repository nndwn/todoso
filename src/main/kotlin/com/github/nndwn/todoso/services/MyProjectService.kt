package com.github.nndwn.todoso.services

import com.github.nndwn.todoso.MyBundle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.JBColor
import java.awt.Color
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service(Service.Level.PROJECT)
class MyProjectService(private val project: Project) {

  private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

  companion object {
    private const val DATE_PATTERN = """\d{4}-\d{2}-\d{2}(?:\s+\d{2}:\d{2})?"""
    private const val REASON_REGEX = """//\s*reason:.*"""
    private const val START_DATE_REGEX = """🛫\s*$DATE_PATTERN"""
    private const val DONE_DATE_REGEX = """✅\s*$DATE_PATTERN"""
    private const val CANCEL_DATE_REGEX = """❌\s*$DATE_PATTERN"""
  }

  enum class Priority(val emojis: List<String>, val code: String, val label: String, val color: Color) {
    HIGHEST(listOf("🔺"), "HH", "Highest", JBColor.MAGENTA),
    HIGH(listOf("⏫", "H"), "H", "High", JBColor.RED),
    MEDIUM(listOf("🔼", "M"), "M", "Medium", JBColor.ORANGE),
    LOW(listOf("🔽", "L"), "L", "Low", JBColor.BLUE),
    LOWEST(listOf("⏬"), "LL", "Lowest", JBColor.CYAN),
    NONE(emptyList(), "", "None", JBColor.GRAY);

    companion object {
      fun findPriority(text: String): Pair<Priority, String?> {
        val bracketMatch = Regex("""\[(HH|H|M|LL|L)]""").find(text)
        if (bracketMatch != null) {
          val code = bracketMatch.groupValues[1]
          val p = entries.find { it.code == code } ?: NONE
          return p to bracketMatch.value
        }
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
    val lineNumber: Int,
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
      .asSequence()
      .withIndex()
      .mapNotNull { (index, line) ->
        val trimmed = line.trim()
        val match = taskRegex.find(trimmed) ?: return@mapNotNull null

        val statusCode = match.groupValues[1]
        var rawContent = match.groupValues[2].trim()

        val (priority, matchedPriorityText) = Priority.findPriority(rawContent)
        if (matchedPriorityText != null) {
          rawContent = rawContent.replaceFirst(matchedPriorityText, "").trim()
        }

        val dates = mutableMapOf<String, String>()
        dateEmojis.forEach { emoji ->
          val regex = Regex("""$emoji\s*($DATE_PATTERN)""")
          regex.find(rawContent)?.let {
            dates[emoji] = it.groupValues[1]
            rawContent = rawContent.replace(it.value, "").trim()
          }
        }

        val timeSeparatorIndex = rawContent.lastIndexOf(" // ")
        if (timeSeparatorIndex != -1) {
          val legacyTime = rawContent.substring(timeSeparatorIndex + 4).trim()
          dates["//"] = legacyTime
          rawContent = rawContent.substring(0, timeSeparatorIndex).trim()
        }

        val tags = tagRegex.findAll(rawContent).map { it.groupValues[1] }.toList()

        TodoTask(line, rawContent, TaskStatus.fromCode(statusCode), priority, tags, dates, index)
      }
      .toList()
      .sortedWith(compareBy({ it.status.ordinal }, { it.priority.ordinal }))
  }

  fun getAllTags(): Set<String> {
    return getTodoTasks().flatMap { it.tags }.toSet()
  }

  fun calculateDuration(task: TodoTask): String? {
    val startStr = task.dates["🛫"] ?: return null
    val endStr = task.dates["✅"] ?: return null

    return try {
      val start = parseFlexibleDateTime(startStr)
      val end = parseFlexibleDateTime(endStr)
      val duration = java.time.Duration.between(start, end)

      val hours = duration.toHours()
      val minutes = duration.toMinutes() % 60

      if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    } catch (e: Exception) {
      null
    }
  }

  private fun parseFlexibleDateTime(text: String): LocalDateTime {
    return try {
      LocalDateTime.parse(text, dateFormatter)
    } catch (e: Exception) {
      LocalDateTime.parse("$text 00:00", dateFormatter)
    }
  }

  fun addTask(description: String) {
    val projectDir = project.guessProjectDir() ?: return
    val settings = MyProjectSettingsService.getInstance(project)
    val todoFile = projectDir.children.find { it.name.equals(settings.state.todoFileName, ignoreCase = true) } ?: return

    val content = VfsUtil.loadText(todoFile)
    val newTaskLine = "- [ ] $description"
    val finalContent =
      if (content.isEmpty() || content.endsWith("\n")) {
        content + newTaskLine
      } else {
        content + "\n" + newTaskLine
      }

    WriteCommandAction.runWriteCommandAction(project) {
      VfsUtil.saveText(todoFile, finalContent)
    }
  }

  fun updateTaskStatus(task: TodoTask, newStatus: TaskStatus, reason: String? = null) {
    val projectDir = project.guessProjectDir() ?: return
    val settings = MyProjectSettingsService.getInstance(project)
    val todoFile = projectDir.children.find { it.name.equals(settings.state.todoFileName, ignoreCase = true) } ?: return
    VfsUtil.markDirtyAndRefresh(false, true, true, todoFile)

    val lines = VfsUtil.loadText(todoFile).lines().toMutableList()
    var lineIndex = lines.indexOf(task.rawText)
    if (lineIndex == -1) {
      lineIndex = lines.indexOfFirst { it.contains(task.description) && it.startsWith("- [") }
    }
    if (lineIndex == -1) return

    val now = LocalDateTime.now().format(dateFormatter)
    var lineContent = lines[lineIndex]

    lineContent = lineContent.replaceFirst(Regex("""\[([ x/-])]"""), "[${newStatus.code}]")

    when (newStatus) {
      TaskStatus.TODO -> {
        lineContent = lineContent.replace(Regex(START_DATE_REGEX), "").trim()
        lineContent = lineContent.replace(Regex(DONE_DATE_REGEX), "").trim()
        lineContent = lineContent.replace(Regex(CANCEL_DATE_REGEX), "").trim()
        lineContent = lineContent.replace(Regex(REASON_REGEX), "").trim()
      }
      TaskStatus.DOING -> {
        lineContent = lineContent.replace(Regex(DONE_DATE_REGEX), "").trim()
        lineContent = lineContent.replace(Regex(CANCEL_DATE_REGEX), "").trim()
        lineContent = lineContent.replace(Regex(REASON_REGEX), "").trim()
        if (!lineContent.contains("🛫")) {
          lineContent = "$lineContent 🛫 $now"
        }
      }
      TaskStatus.DONE -> {
        lineContent = lineContent.replace(Regex(CANCEL_DATE_REGEX), "").trim()
        lineContent = lineContent.replace(Regex(REASON_REGEX), "").trim()
        if (!lineContent.contains("✅")) {
          lineContent = "$lineContent ✅ $now"
        }
      }
      TaskStatus.CANCELLED -> {
        lineContent = lineContent.replace(Regex(START_DATE_REGEX), "").trim()
        lineContent = lineContent.replace(Regex(DONE_DATE_REGEX), "").trim()
        if (!lineContent.contains("❌")) {
          lineContent = "$lineContent ❌ $now"
        }
        if (!reason.isNullOrBlank()) {
          lineContent = lineContent.replace(Regex(REASON_REGEX), "").trim()
          lineContent = "$lineContent // reason: $reason"
        }
      }
    }

    lines[lineIndex] = lineContent
    WriteCommandAction.runWriteCommandAction(project) {
      VfsUtil.saveText(todoFile, lines.joinToString("\n"))
      todoFile.refresh(false, false)
    }
  }

  fun editTask(task: TodoTask, newContent: String) {
    val projectDir = project.guessProjectDir() ?: return
    val settings = MyProjectSettingsService.getInstance(project)
    val todoFile = projectDir.children.find { it.name.equals(settings.state.todoFileName, ignoreCase = true) } ?: return
    VfsUtil.markDirtyAndRefresh(false, true, true, todoFile)

    val lines = VfsUtil.loadText(todoFile).lines().toMutableList()
    var lineIndex = lines.indexOf(task.rawText)
    if (lineIndex == -1) {
      lineIndex = lines.indexOfFirst { it.contains(task.description) && it.startsWith("- [") }
    }
    if (lineIndex == -1) return

    val statusPart = "[${task.status.code}]"
    val priorityPart =
      if (task.priority != Priority.NONE) {
        if (task.priority.emojis.isNotEmpty()) task.priority.emojis[0] else "[${task.priority.code}]"
      } else ""

    val datesPart = task.dates.filter { it.key != "//" }.map { "${it.key} ${it.value}" }.joinToString(" ")
    val metadataPart = task.dates["//"]?.let { " // $it" } ?: ""

    val newLine = buildString {
      append("- ")
      append(statusPart)
      append(" ")
      if (priorityPart.isNotEmpty()) {
        append(priorityPart)
        append(" ")
      }
      append(newContent.trim())
      if (datesPart.isNotEmpty()) {
        append(" ")
        append(datesPart)
      }
      append(metadataPart)
    }

    lines[lineIndex] = newLine
    WriteCommandAction.runWriteCommandAction(project) {
      VfsUtil.saveText(todoFile, lines.joinToString("\n"))
      todoFile.refresh(false, false)
    }
  }

  fun deleteTask(task: TodoTask) {
    val projectDir = project.guessProjectDir() ?: return
    val settings = MyProjectSettingsService.getInstance(project)
    val todoFile = projectDir.children.find { it.name.equals(settings.state.todoFileName, ignoreCase = true) } ?: return
    VfsUtil.markDirtyAndRefresh(false, true, true, todoFile)

    val lines = VfsUtil.loadText(todoFile).lines().toMutableList()
    var lineIndex = lines.indexOf(task.rawText)
    if (lineIndex == -1) {
      lineIndex = lines.indexOfFirst { it.contains(task.description) && it.startsWith("- [") }
    }
    if (lineIndex == -1) return

    lines.removeAt(lineIndex)
    WriteCommandAction.runWriteCommandAction(project) {
      VfsUtil.saveText(todoFile, lines.joinToString("\n"))
      todoFile.refresh(false, false)
    }
  }
}
