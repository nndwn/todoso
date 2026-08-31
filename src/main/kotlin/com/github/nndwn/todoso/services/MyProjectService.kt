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

  enum class Priority(val code: String, val label: String, val color: Color) {
    HIGH("H", "High", JBColor.RED),
    MEDIUM("M", "Medium", JBColor.ORANGE),
    LOW("L", "Low", JBColor.BLUE),
    NONE("", "None", JBColor.GRAY);

    companion object {
      fun fromCode(code: String?): Priority = entries.find { it.code == code } ?: NONE
    }
  }

  data class TodoTask(
    val rawText: String,
    val description: String,
    val isDone: Boolean,
    val priority: Priority,
    val tags: List<String>,
  )

  init {
    thisLogger().info(MyBundle["projectService", project.name])
  }

  fun getTodoTasks(): List<TodoTask> {
    val projectDir = project.guessProjectDir() ?: return emptyList()
    val todoFile = projectDir.children.find { it.name.equals("todo.md", ignoreCase = true) } ?: return emptyList()

    val taskRegex = Regex("""^- \[([ x])] (?:\[([HML])] )?(.*)$""")
    val tagRegex = Regex("""#([\w.-]+)""")

    return VfsUtil.loadText(todoFile)
      .lines()
      .mapNotNull { line ->
        val trimmed = line.trim()
        val match = taskRegex.find(trimmed)
        if (match != null) {
          val isDone = match.groupValues[1] == "x"
          val priorityCode = match.groupValues[2]
          val fullDesc = match.groupValues[3].trim()

          val tags = tagRegex.findAll(fullDesc).map { it.groupValues[1] }.toList()

          TodoTask(line, fullDesc, isDone, Priority.fromCode(priorityCode), tags)
        } else if (trimmed.startsWith("- ")) {
          val fullDesc = trimmed.removePrefix("- ").trim()
          if (fullDesc.isEmpty()) return@mapNotNull null

          val tags = tagRegex.findAll(fullDesc).map { it.groupValues[1] }.toList()

          TodoTask(line, fullDesc, false, Priority.NONE, tags)
        } else {
          null
        }
      }
      .sortedWith(compareBy({ it.isDone }, { it.priority.ordinal }))
  }

  /** Mendapatkan semua tag unik yang ada di todo.md */
  fun getAllTags(): Set<String> {
    return getTodoTasks().flatMap { it.tags }.toSet()
  }
}
