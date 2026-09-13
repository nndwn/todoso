package com.github.nndwn.todoso.services

import com.github.nndwn.todoso.TodosoBundle
import com.github.nndwn.todoso.TodosoConstants
import com.github.nndwn.todoso.domain.model.Priority
import com.github.nndwn.todoso.domain.model.TaskStatus
import com.github.nndwn.todoso.domain.model.TodoTask
import com.github.nndwn.todoso.domain.model.TodoTaskBuilder
import com.github.nndwn.todoso.domain.parser.TaskIdParser
import com.github.nndwn.todoso.domain.parser.TodoTaskParser
import com.github.nndwn.todoso.domain.parser.TodoValidator
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun interface TodosoDataChangeListener {
  fun onDataChanged()

  companion object {
    val TOPIC =
      com.intellij.util.messages.Topic.create(
        "Todoso Data Change",
        TodosoDataChangeListener::class.java,
      )
  }
}

@Service(Service.Level.PROJECT)
class TodosoService(private val project: Project) {

  private val instructionHeader: String
    get() = TodosoBundle.message("todo.instruction.inject", TodosoConstants.GITHUB_REPO_URL)

  private val settings = TodosoSettingsService.getInstance(project)
  private var cachedTasks: List<TodoTask> = emptyList()
  private var tasksById: Map<String, TodoTask> = emptyMap()
  private var isCacheDirty = true
  private var isInternalWriting = false

  private var lastLoadedPath: String? = null

  private fun sanitizeInputText(input: String): String = TodoTaskBuilder.sanitize(input)

  fun getTodoFile(): VirtualFile? {
    val path = settings.state.todoFilePath.trim().ifBlank { TodosoConstants.FILENAME }
    val projectDir = project.guessProjectDir()

    val relativeFile = projectDir?.findFileByRelativePath(path)
    if (relativeFile != null) return relativeFile

    val ioFile = File(path)
    if (ioFile.isAbsolute && ioFile.exists()) {
      return VfsUtil.findFileByIoFile(ioFile, true)
    }

    return projectDir?.children?.find { it.name.equals(path, ignoreCase = true) }
  }

  private fun getOrCreateTodoFile(): VirtualFile? {
    getTodoFile()?.let {
      return it
    }

    val path = settings.state.todoFilePath.trim().ifBlank { TodosoConstants.FILENAME }
    val projectDir = project.guessProjectDir()

    val ioFile = File(path)
    if (ioFile.isAbsolute) {
      return try {
        if (!ioFile.exists()) {
          ioFile.parentFile?.mkdirs()
          ioFile.createNewFile()
        }
        VfsUtil.findFileByIoFile(ioFile, true)
      } catch (_: Exception) {
        null
      }
    }

    return try {
      projectDir?.createChildData(this, path)
    } catch (_: Exception) {
      null
    }
  }

  fun injectInstructionsIfNeeded() {
    val todoFile = getTodoFile() ?: return

    val content =
      try {
        VfsUtil.loadText(todoFile)
      } catch (_: Exception) {
        return
      }

    val firstLine = content.lines().firstOrNull()?.trim() ?: ""
    if (!firstLine.contains(TodosoConstants.GITHUB_REPO_URL) && !firstLine.startsWith("<!--")) {
      runWriteCommandAction(
        project,
        "Inject Todo Instructions Header",
        null,
        {
          val newContent =
            if (content.isBlank()) {
              instructionHeader
            } else {
              "$instructionHeader\n\n${content.trimStart()}"
            }

          VfsUtil.saveText(todoFile, newContent)
          VfsUtil.markDirtyAndRefresh(false, true, true, todoFile)
        },
      )
    }
  }

  fun markCacheDirty() {
    isCacheDirty = true
  }

  fun getCachedTasks(): List<TodoTask> = cachedTasks

  fun loadTask(): List<TodoTask> {
    val currentPath = settings.state.todoFilePath

    if (!isCacheDirty && currentPath == lastLoadedPath) return cachedTasks

    val todoFile =
      getTodoFile()
        ?: run {
          cachedTasks = emptyList()
          lastLoadedPath = currentPath
          return emptyList()
        }

    todoFile.refresh(false, false)
    if (!isCacheDirty && currentPath == lastLoadedPath) return cachedTasks
    val content =
      try {
        VfsUtil.loadText(todoFile)
      } catch (_: Exception) {
        cachedTasks = emptyList()
        return emptyList()
      }

    if (content.isBlank()) {
      cachedTasks = emptyList()
      return emptyList()
    }

    val usedIds = mutableSetOf<String>()
    val tasks = mutableListOf<TodoTask>()

    content.lineSequence().forEachIndexed { index, rawLine ->
      val task =
        TodoTaskParser.parseLine(
          rawLine = rawLine,
          lineNumber = index + 1,
          usedIds = usedIds,
        )
      if (task != null) {
        tasks.add(task)
      }
    }

    cachedTasks = tasks
    tasksById = tasks.associateBy { it.id }
    isCacheDirty = false
    lastLoadedPath = currentPath

    if (tasks.any { !it.isPersistentId }) {
        ApplicationManager.getApplication().invokeLater {
            persistMissingIds(todoFile, tasks)
        }
    }
    
    return cachedTasks
  }

  private fun persistMissingIds(file: VirtualFile, tasks: List<TodoTask>) {
    runWriteCommandAction(project, "Persist Missing Task IDs", null, {
      val content = VfsUtil.loadText(file)
      val lines = content.lines().toMutableList()
      var modified = false

      tasks.filter { !it.isPersistentId }.forEach { task ->
        val index = findTaskIndex(lines, task)
        if (index != null) {
          val updatedTask = task.copy(isPersistentId = true)
          lines[index] = TodoTaskBuilder.rebuildTaskLine(updatedTask)
          modified = true
        }
      }

      if (modified) {
        saveContent(file, lines)
        markCacheDirty()
      }
    })
  }

  fun findTaskById(id: String): TodoTask? {
      loadTask()
      return tasksById[id]
  }

  fun updateTaskStatus(task: TodoTask, newStatus: TaskStatus, note: String? = null) {
    val updatedMeta = if (!note.isNullOrBlank()) task.metadata.copy(notes = note) else task.metadata
    val updatedTask = task.copy(status = newStatus, metadata = updatedMeta, isPersistentId = true)
    updateTaskInMemory(updatedTask)
    modifyTaskLine(task) { updatedTask.let { TodoTaskBuilder.rebuildTaskLine(it) } }
  }

  fun updateTaskNote(task: TodoTask, note: String) {
    val updatedTask = task.copy(
      metadata = task.metadata.copy(notes = note.removePrefix("//").trim()),
      isPersistentId = true
    )
    updateTaskInMemory(updatedTask)
    modifyTaskLine(task) { TodoTaskBuilder.rebuildTaskLine(updatedTask) }
  }

  private fun updateTaskInMemory(updatedTask: TodoTask) {
    val index = cachedTasks.indexOfFirst { it.id == updatedTask.id }
    if (index != -1) {
        val newTasks = cachedTasks.toMutableList()
        newTasks[index] = updatedTask
        cachedTasks = newTasks
        tasksById = cachedTasks.associateBy { it.id }
        // Beritahu UI tanpa menandai cache dirty
        project.messageBus.syncPublisher(TodosoDataChangeListener.TOPIC).onDataChanged()
    }
  }

  fun addTask(rawInputText: String): TodoTask? {
    val cleanInput = sanitizeInputText(rawInputText)
    if (cleanInput.isBlank()) return null

    val formattedTaskLine = formatNewTaskLine(cleanInput) ?: return null
    val newTask = TodoTaskParser.parseLine(formattedTaskLine, -1) ?: return null

    runWriteCommandAction(
      project,
      "Add Task",
      null,
      Runnable {
        val todoFile = getOrCreateTodoFile() ?: return@Runnable
        val currentContent =
          try {
            VfsUtil.loadText(todoFile)
          } catch (_: Exception) {
            ""
          }

        val existingLines = currentContent.lines().map { it.trimEnd() }.dropLastWhile { it.isBlank() }

        val newLines = existingLines + formattedTaskLine
        val newContent = newLines.joinToString("\n") + "\n"

        VfsUtil.saveText(todoFile, newContent)
        VfsUtil.markDirtyAndRefresh(false, true, true, todoFile)
        markCacheDirty()
      },
    )
    return newTask
  }

  internal fun formatNewTaskLine(input: String): String? {
    val dummyLine = "- [ ] $input"

    val parsedTask =
      TodoTaskParser.parseLine(
        rawLine = dummyLine,
        lineNumber = 1,
        ignoreId = true,
      )

    if (parsedTask == null) return null

    return if (TodoValidator.isContentValid(parsedTask.description)) {
      val generatedId = TaskIdParser.parseId(null).id
      val nowFormatted = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
      val updatedMetadata = parsedTask.metadata.copy(createdDate = nowFormatted)
      val newTask =
        parsedTask.copy(
          id = generatedId,
          isPersistentId = true,
          metadata = updatedMetadata,
        )
      TodoTaskBuilder.rebuildTaskLine(newTask)
    } else {
      null
    }
  }

  fun editTask(task: TodoTask, rawInputText: String) {
    val cleanInput = sanitizeInputText(rawInputText)
    if (cleanInput.isBlank()) return

    val dummyLine = "- [${task.status.code}] $cleanInput"
    val parsedTask = TodoTaskParser.parseLine(dummyLine, task.lineNumber)
    
    val nowFormatted = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    val updatedTask = if (parsedTask != null) {
      task.copy(
        description = parsedTask.description,
        tags = parsedTask.tags,
        isPersistentId = true,
        metadata = task.metadata.copy(editedDate = nowFormatted, notes = parsedTask.metadata.notes),
      )
    } else {
      task.copy(
        description = cleanInput,
        isPersistentId = true,
        metadata = task.metadata.copy(editedDate = nowFormatted),
      )
    }

    updateTaskInMemory(updatedTask)
    modifyTaskLine(task) { TodoTaskBuilder.rebuildTaskLine(updatedTask) }
  }

  private fun modifyTaskLine(task: TodoTask, action: (TodoTask) -> String?) {
    val todoFile = getTodoFile() ?: return

    runWriteCommandAction(
      project,
      "Modify Todo Task",
      null,
      Runnable {
        val content =
          try {
            VfsUtil.loadText(todoFile)
          } catch (_: Exception) {
            return@Runnable
          }

        val lines = content.lines().toMutableList()
        val targetIndex = findTaskIndex(lines, task) ?: return@Runnable

        val updatedLine = action(task)
        updateTaskAt(lines, targetIndex, updatedLine)

        saveContent(todoFile, lines)
        markCacheDirty()
      },
    )
  }

  private fun findTaskIndex(lines: List<String>, task: TodoTask): Int? {
    val initialIndex = task.lineNumber - 1
    if (initialIndex !in lines.indices) return null

    if (!task.isPersistentId || task.id.isBlank()) return initialIndex

    val currentLineAtTarget = lines[initialIndex]
    if (currentLineAtTarget.contains(task.id)) return initialIndex

    val actualIndex = lines.indexOfFirst { it.contains("🆔 ${task.id}") }
    return if (actualIndex != -1) actualIndex else initialIndex
  }

  private fun updateTaskAt(lines: MutableList<String>, index: Int, updatedLine: String?) {
    if (updatedLine == null) {
      lines.removeAt(index)
    } else {
      lines[index] = updatedLine.replace("\n", "").trimEnd()
    }
  }

  private fun saveContent(file: VirtualFile, lines: List<String>) {
    val cleanedLines = lines.dropLastWhile { it.isBlank() }
    val newContent =
      if (cleanedLines.isNotEmpty()) {
        cleanedLines.joinToString("\n") + "\n"
      } else {
        ""
      }

    isInternalWriting = true
    try {
        VfsUtil.saveText(file, newContent)
        VfsUtil.markDirtyAndRefresh(false, true, true, file)
    } finally {
        // Berikan sedikit jeda agar event VFS selesai diproses
        ApplicationManager.getApplication().executeOnPooledThread {
            Thread.sleep(500)
            isInternalWriting = false
        }
    }
  }

  fun isWritingInternal(): Boolean = isInternalWriting


  fun deleteTask(task: TodoTask) {
    modifyTaskLine(task) { "<!-- ${task.rawText} -->" }
  }

  fun updateTaskPriority(task: TodoTask, newPriority: Priority) {
    modifyTaskLine(task) { currentTask ->
      val updatedTask =
        currentTask.copy(
          priority = newPriority,
          isPersistentId = true,
        )
      TodoTaskBuilder.rebuildTaskLine(updatedTask)
    }
  }

  fun applyTaskTag(task: TodoTask, tag: String, exclusiveWith: List<String> = emptyList()) {
    val cleanTargetTag = tag.trim().removePrefix("#")
    val hasTag = task.tags.contains(cleanTargetTag)

    var newDescription = task.description
    val newTags = task.tags.toMutableList()

    if (hasTag) {
      newDescription = newDescription.replace(Regex("""\s*#$cleanTargetTag\b"""), "").trim()
      newTags.remove(cleanTargetTag)
    } else {
      val exclusives = exclusiveWith.ifEmpty {
        TodosoConstants.EXCLUSIVE_TAG_GROUPS[cleanTargetTag.lowercase()] ?: emptyList()
      }
      exclusives.forEach { ex ->
        val cleanEx = ex.removePrefix("#")
        newDescription = newDescription.replace(Regex("""\s*#$cleanEx\b"""), "").trim()
        newTags.remove(cleanEx)
      }
      newDescription = "$newDescription #$cleanTargetTag"
      newTags.add(cleanTargetTag)
    }

    val updatedTask = task.copy(
      description = newDescription,
      tags = newTags.distinct(),
      isPersistentId = true
    )

    updateTaskInMemory(updatedTask)
    modifyTaskLine(task) { TodoTaskBuilder.rebuildTaskLine(updatedTask) }
  }
}
