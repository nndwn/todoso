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
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service(Service.Level.PROJECT)
class MyProjectService(private val project: Project) {

  private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

  companion object {
    private const val DATE_PATTERN = """\d{4}-\d{2}-\d{2}(?:\s+\d{2}:\d{2})?"""
    private const val NOTED_REGEX = """//\s*noted:.*"""
    private const val START_DATE_REGEX = """🛫\s*$DATE_PATTERN"""
    private const val DONE_DATE_REGEX = """✅\s*$DATE_PATTERN"""
    private const val CANCEL_DATE_REGEX = """❌\s*$DATE_PATTERN"""

    const val GITHUB_URL = "https://github.com/nndwn/todoso"
    private const val INSTRUCTIONS_HEADER = "<!-- For instructions, read more here: $GITHUB_URL -->"
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

  enum class DefaultLabel(val tag: String, val exclusiveWith: List<String> = emptyList()) {
    FEATURE("#feature", listOf("#issue")),
    ISSUE("#issue", listOf("#feature"));

    val tagName: String = tag.removePrefix("#")
  }

  data class TodoTask(
    val id: String,
    val isPersistentId: Boolean,
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

    val taskRegex = Regex("""^- \[([ x/-]?)] (.*)$""")
    val tagRegex = Regex("""(?<=\s|^)#([\w/#.-]*[\w/#-])""")
    val idRegex = Regex("""🆔\s*([a-zA-Z0-9]+)""")
    val dateEmojis = listOf("🛫", "📅", "⏳", "✅", "➕", "❌")
    val usedIds = mutableSetOf<String>()

    return VfsUtil.loadText(todoFile)
      .lines()
      .asSequence()
      .withIndex()
      .mapNotNull { (index, line) ->
        val trimmed = line.trim()
        val match = taskRegex.find(trimmed) ?: return@mapNotNull null

        val statusCode = match.groupValues[1].let { it.ifEmpty { " " } }
        var rawContent = match.groupValues[2].trim()

        val idMatch = idRegex.find(rawContent)
        val extractedId = idMatch?.groupValues?.get(1)
        var isPersistent = false
        val id =
          if (extractedId != null && usedIds.add(extractedId)) {
            rawContent = rawContent.replace(idMatch.value, "").trim()
            isPersistent = true
            extractedId
          } else {
            var newId = generateUniqueId()
            while (!usedIds.add(newId)) {
              newId = generateUniqueId()
            }
            newId
          }

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

        TodoTask(id, isPersistent, line, rawContent, TaskStatus.fromCode(statusCode), priority, tags, dates, index)
      }
      .toList()
      .sortedWith(compareBy({ it.status.ordinal }, { it.priority.ordinal }))
  }

  private fun generateUniqueId(): String {
    val chars = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    return (1..6).map { chars.random() }.joinToString("")
  }

  fun getRecentVersions(limit: Int = 3): List<String> {
    val projectDir = project.guessProjectDir() ?: return emptyList()
    val settings = MyProjectSettingsService.getInstance(project)
    val todoFile =
      projectDir.children.find { it.name.equals(settings.state.todoFileName, ignoreCase = true) } ?: return emptyList()

    val versionRegex = Regex("""(?<=\s|^)#v(\d+[\w.]*)""")
    return VfsUtil.loadText(todoFile)
      .lines()
      .asSequence()
      .flatMap { line -> versionRegex.findAll(line).map { it.value } }
      .distinct()
      .toList()
      .sortedDescending()
      .take(limit)
  }

  fun getTagCounts(tasks: List<TodoTask>): Map<String, Int> {
    return tasks.flatMap { it.tags }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }.toMap()
  }

  fun calculateDuration(task: TodoTask): String? {
    val startStr = task.dates["🛫"] ?: return null
    val endStr = task.dates["✅"] ?: return null

    return try {
      val start = parseFlexibleDateTime(startStr)
      val end = parseFlexibleDateTime(endStr)
      val duration = Duration.between(start, end)

      val hours = duration.toHours()
      val minutes = duration.toMinutes() % 60

      if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    } catch (_: Exception) {
      null
    }
  }

  private fun parseFlexibleDateTime(text: String): LocalDateTime {
    return try {
      LocalDateTime.parse(text, dateFormatter)
    } catch (_: Exception) {
      LocalDateTime.parse("$text 00:00", dateFormatter)
    }
  }

  fun addTask(description: String) {
    val projectDir = project.guessProjectDir() ?: return
    val settings = MyProjectSettingsService.getInstance(project)
    val fileName = settings.state.todoFileName
    val todoFile = projectDir.findChild(fileName)

    val newTaskLine = formatNewTaskLine(description)

    WriteCommandAction.runWriteCommandAction(project) {
      val finalFile = todoFile ?: projectDir.createChildData(this, fileName)
      val currentContent = VfsUtil.loadText(finalFile)

      val contentWithInstructions =
        if (!currentContent.contains(GITHUB_URL)) {
          if (currentContent.trim().isEmpty()) INSTRUCTIONS_HEADER else INSTRUCTIONS_HEADER + "\n\n" + currentContent
        } else {
          currentContent
        }

      val finalContent =
        if (contentWithInstructions.isEmpty() || contentWithInstructions.endsWith("\n")) {
          contentWithInstructions + newTaskLine
        } else {
          contentWithInstructions + "\n" + newTaskLine
        }
      VfsUtil.saveText(finalFile, finalContent)
      VfsUtil.markDirtyAndRefresh(false, true, true, finalFile)
    }
  }

  private fun formatNewTaskLine(description: String): String {
    val trimmedInput = description.trim()
    val newId = generateUniqueId()
    return if (trimmedInput.startsWith("- [") && trimmedInput.contains("] ")) {
      if (trimmedInput.contains("🆔")) trimmedInput else "$trimmedInput 🆔 $newId"
    } else {
      "- [ ] $trimmedInput 🆔 $newId"
    }
  }

  private fun modifyTaskLine(task: TodoTask, action: (String) -> String?) {
    val projectDir = project.guessProjectDir() ?: return
    val settings = MyProjectSettingsService.getInstance(project)
    val todoFile = projectDir.children.find { it.name.equals(settings.state.todoFileName, ignoreCase = true) } ?: return

    WriteCommandAction.runWriteCommandAction(project) {
      VfsUtil.markDirtyAndRefresh(false, true, true, todoFile)
      val lines = VfsUtil.loadText(todoFile).lines().toMutableList()
      var lineIndex = lines.indexOf(task.rawText)
      if (lineIndex == -1) {
        lineIndex = lines.indexOfFirst { it.contains(task.description) && it.startsWith("- [") }
      }
      if (lineIndex == -1) return@runWriteCommandAction

      val newLine = action(lines[lineIndex])
      if (newLine == null) {
        lines.removeAt(lineIndex)
      } else {
        lines[lineIndex] = newLine
      }

      VfsUtil.saveText(todoFile, lines.joinToString("\n"))
      todoFile.refresh(false, false)
    }
  }

  fun updateTaskStatus(task: TodoTask, newStatus: TaskStatus, noted: String? = null) {
    modifyTaskLine(task) { line ->
      var lineContent = line.replaceFirst(Regex("""\[([ x/-]?)]"""), "[${newStatus.code}]")
      val now = LocalDateTime.now().format(dateFormatter)

      if (!lineContent.contains("🆔")) {
        lineContent = "$lineContent 🆔 ${task.id}"
      }

      applyStatusMetadata(lineContent, newStatus, now, noted)
    }
  }

  private fun applyStatusMetadata(line: String, status: TaskStatus, now: String, noted: String?): String {
    var content = line
    when (status) {
      TaskStatus.TODO -> {
        content = content.cleanMetadata(START_DATE_REGEX, DONE_DATE_REGEX, CANCEL_DATE_REGEX, NOTED_REGEX)
      }
      TaskStatus.DOING -> {
        content = content.cleanMetadata(DONE_DATE_REGEX, CANCEL_DATE_REGEX, NOTED_REGEX)
        if (!content.contains("🛫")) content = "$content 🛫 $now"
      }
      TaskStatus.DONE -> {
        content = content.cleanMetadata(CANCEL_DATE_REGEX, NOTED_REGEX)
        if (!content.contains("✅")) content = "$content ✅ $now"
      }
      TaskStatus.CANCELLED -> {
        content = content.cleanMetadata(START_DATE_REGEX, DONE_DATE_REGEX)
        if (!content.contains("❌")) content = "$content ❌ $now"
        if (!noted.isNullOrBlank()) {
          content = content.cleanMetadata(NOTED_REGEX)
          content = "$content // noted: $noted"
        }
      }
    }
    return content
  }

  private fun String.cleanMetadata(vararg patterns: String): String {
    var result = this
    patterns.forEach { result = result.replace(Regex(it), "").trim() }
    return result
  }

  fun injectInstructionsIfNeeded() {
    val settings = MyProjectSettingsService.getInstance(project)
    val projectDir = project.guessProjectDir() ?: return
    val todoFile = projectDir.findChild(settings.state.todoFileName) ?: return

    val content = VfsUtil.loadText(todoFile)
    if (!content.contains(GITHUB_URL)) {
      WriteCommandAction.runWriteCommandAction(project) {
        val newContent =
          if (content.trim().isEmpty()) {
            INSTRUCTIONS_HEADER
          } else {
            INSTRUCTIONS_HEADER + "\n\n" + content
          }
        VfsUtil.saveText(todoFile, newContent)
        VfsUtil.markDirtyAndRefresh(false, true, true, todoFile)
      }
    }
  }

  fun editTask(task: TodoTask, newContent: String) {
    modifyTaskLine(task) { _ ->
      rebuildTaskLine(task, newContent)
    }
  }

  fun deleteTask(task: TodoTask) {
    modifyTaskLine(task) { null }
  }

  fun updateTaskPriority(task: TodoTask, newPriority: Priority) {
    modifyTaskLine(task) { _ ->
      rebuildTaskLine(task.copy(priority = newPriority), task.description)
    }
  }

  fun toggleTaskTag(task: TodoTask, tag: String, exclusiveWith: List<String> = emptyList()) {
    modifyTaskLine(task) { _ ->
      var newDescription = task.description

      val tagWithHash = if (tag.startsWith("#")) tag else "#$tag"
      val hasTag = task.tags.any { "#$it" == tagWithHash }

      if (hasTag) {
        // Remove tag
        newDescription = newDescription.replace(Regex("""(?<=\s|^)$tagWithHash(?=\s|$)"""), "").trim()
      } else {
        // Remove exclusive tags first
        exclusiveWith.forEach { exTag ->
          val exWithHash = if (exTag.startsWith("#")) exTag else "#$exTag"
          newDescription = newDescription.replace(Regex("""(?<=\s|^)$exWithHash(?=\s|$)"""), "").trim()
        }
        // Add new tag
        newDescription = "$newDescription $tagWithHash".trim()
      }

      rebuildTaskLine(task, newDescription)
    }
  }

  private fun rebuildTaskLine(task: TodoTask, description: String): String {
    val statusPart = "[${task.status.code}]"
    val priorityPart =
      if (task.priority != Priority.NONE) {
        task.priority.emojis.firstOrNull() ?: "[${task.priority.code}]"
      } else ""

    val datesPart = task.dates.filter { it.key != "//" }.map { "${it.key} ${it.value}" }.joinToString(" ")
    val metadataPart = task.dates["//"]?.let { " // $it" } ?: ""

    return buildString {
      append("- ").append(statusPart).append(" ")
      if (priorityPart.isNotEmpty()) append(priorityPart).append(" ")
      append(description.trim())
      if (datesPart.isNotEmpty()) append(" ").append(datesPart)
      append(" 🆔 ").append(task.id)
      append(metadataPart)
    }
  }
}
