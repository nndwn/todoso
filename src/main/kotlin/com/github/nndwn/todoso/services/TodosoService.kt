package com.github.nndwn.todoso.services

import com.github.nndwn.todoso.TodosoBundle
import com.github.nndwn.todoso.TodosoConstants
import com.github.nndwn.todoso.domain.model.Priority
import com.github.nndwn.todoso.domain.model.TaskStatus
import com.github.nndwn.todoso.domain.model.TodoTask
import com.github.nndwn.todoso.domain.model.TodoTaskBuilder
import com.github.nndwn.todoso.domain.parser.TagParser
import com.github.nndwn.todoso.domain.parser.TaskIdParser
import com.github.nndwn.todoso.domain.parser.TodoTaskParser
import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
import com.intellij.openapi.components.Service
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
        val TOPIC = com.intellij.util.messages.Topic.create(
            "Todoso Data Change",
            TodosoDataChangeListener::class.java
        )
    }
}

@Service(Service.Level.PROJECT)
class TodosoService(private val project: Project) {

    private val instructionHeader: String
        get() = TodosoBundle.message("todo.instruction.inject", TodosoConstants.GITHUB_REPO_URL)

    private val settings = TodosoSettingsService.getInstance(project)
    private var cachedTasks: List<TodoTask> = emptyList()
    private var isCacheDirty = true

    private var lastLoadedPath: String? = null
    private fun sanitizeInputText(input: String): String {
        return input.replace("\r\n", " ")
            .replace("\n", " ")
            .replace("\r", " ")
            .replace(Regex("""[ \t]+"""), " ")
            .trim()
    }
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
        getTodoFile()?.let { return it }

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

        val content = try {
            VfsUtil.loadText(todoFile)
        } catch (_: Exception) {
            return
        }

        val firstLine = content.lines().firstOrNull()?.trim() ?: ""
        if (!firstLine.contains(TodosoConstants.GITHUB_REPO_URL) && !firstLine.startsWith("<!--")) {
            runWriteCommandAction(project, "Inject Todo Instructions Header", null,  {
                val newContent = if (content.isBlank()) {
                    instructionHeader
                } else {
                    "$instructionHeader\n\n${content.trimStart()}"
                }

                VfsUtil.saveText(todoFile, newContent)
                VfsUtil.markDirtyAndRefresh(false, true, true, todoFile)
            })
        }
    }
    fun markCacheDirty() {
        isCacheDirty = true
    }

    fun loadTask(): List<TodoTask> {
        val currentPath = settings.state.todoFilePath

        if (!isCacheDirty && currentPath == lastLoadedPath) return cachedTasks

        val todoFile = getTodoFile() ?: run {
            cachedTasks = emptyList()
            lastLoadedPath = currentPath
            return emptyList()
        }

        todoFile.refresh(false, false)

        val content = try {
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
            val task = TodoTaskParser.parseLine(
                rawLine = rawLine,
                lineNumber = index + 1,
                usedIds = usedIds
            )
            if (task != null) {
                tasks.add(task)
            }
        }

        cachedTasks = tasks
        isCacheDirty = false
        lastLoadedPath = currentPath
        return cachedTasks
    }



    fun updateTaskStatus(task: TodoTask, newStatus: TaskStatus, note: String? = null) {
        modifyTaskLine(task) { currentTask ->
            val updatedMeta = if (!note.isNullOrBlank()) {
                currentTask.metadata.copy(notes = note)
            } else {
                currentTask.metadata
            }

            val updatedTask = currentTask.copy(
                status = newStatus,
                metadata = updatedMeta,
                isPersistentId = true
            )

            TodoTaskBuilder.rebuildTaskLine(updatedTask)
        }
    }

    fun addTask(rawInputText: String) {
        val cleanInput = sanitizeInputText(rawInputText)
        if (cleanInput.isBlank()) return

        val formattedTaskLine = formatNewTaskLine(cleanInput) ?: return

        runWriteCommandAction(project, "Add Task", null, Runnable {
            val todoFile = getOrCreateTodoFile() ?: return@Runnable
            val currentContent = try {
                VfsUtil.loadText(todoFile)
            } catch (_: Exception) {
                ""
            }

            val existingLines = currentContent.lines()
                .map { it.trimEnd() }
                .dropLastWhile { it.isBlank() }

            val newLines = existingLines + formattedTaskLine
            val newContent = newLines.joinToString("\n") + "\n"

            VfsUtil.saveText(todoFile, newContent)
            VfsUtil.markDirtyAndRefresh(false, true, true, todoFile)
            markCacheDirty()
        })
    }
    internal fun formatNewTaskLine(input: String): String? {
        val dummyLine = "- [ ] $input"

        val parsedTask = TodoTaskParser.parseLine(
            rawLine = dummyLine,
            lineNumber = 1,
            ignoreId = true
        )

        if (parsedTask == null) return null

        val cleanForCheck = parsedTask.description
            .replace(TagParser.TAG_REGEX, "")
            .replace(TaskIdParser.TASK_ID_REGEX, "")
            .replace(Regex("""[🛫📅✅❌➕]"""), "")
            .trim()

        return if (cleanForCheck.isNotBlank()) {
            val generatedId = TaskIdParser.parseId(null).id
            val nowFormatted = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            val updatedMetadata = parsedTask.metadata.copy(createdDate = nowFormatted)
            val newTask = parsedTask.copy(
                id = generatedId,
                isPersistentId = true,
                metadata = updatedMetadata
            )
            TodoTaskBuilder.rebuildTaskLine(newTask)
        } else {
            null
        }
    }
    fun editTask(task: TodoTask, rawInputText: String) {
        val trimmedInput = rawInputText.trim()
        if (trimmedInput.isBlank()) return

        modifyTaskLine(task) { currentTask ->
            val dummyLine = "- [${currentTask.status.code}] $trimmedInput"

            val parsedTask = TodoTaskParser.parseLine(
                rawLine = dummyLine,
                lineNumber = currentTask.lineNumber
            )

            val updatedTask = if (parsedTask != null) {
                currentTask.copy(
                    description = parsedTask.description,
                    tags = parsedTask.tags,
                    isPersistentId = true
                )
            } else {
                currentTask.copy(
                    description = trimmedInput,
                    isPersistentId = true
                )
            }
            TodoTaskBuilder.rebuildTaskLine(updatedTask)
        }
    }

    private fun modifyTaskLine(task: TodoTask, action: (TodoTask) -> String?) {
        val todoFile = getTodoFile() ?: return

        runWriteCommandAction(project, "Modify Todo Task", null, Runnable {
            val content = try {
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
        })
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
        val newContent = if (cleanedLines.isNotEmpty()) {
            cleanedLines.joinToString("\n") + "\n"
        } else {
            ""
        }

        VfsUtil.saveText(file, newContent)
        VfsUtil.markDirtyAndRefresh(false, true, true, file)
    }

    fun deleteTask(task: TodoTask) {
        modifyTaskLine(task) { null }
    }

    fun updateTaskPriority(task: TodoTask, newPriority: Priority) {
        modifyTaskLine(task) { currentTask ->
            val updatedTask = currentTask.copy(
                priority = newPriority,
                isPersistentId = true
            )
            TodoTaskBuilder.rebuildTaskLine(updatedTask)
        }
    }

    fun applyTaskTag(task: TodoTask, tag: String, exclusiveWith: List<String> = emptyList()) {
        modifyTaskLine(task) { currentTask ->
            val cleanTargetTag = tag.trim().removePrefix("#")
            val cleanExclusiveTags = exclusiveWith.map { it.trim().removePrefix("#") }
            val hasTag = currentTask.tags.any { it.equals(cleanTargetTag, ignoreCase = true) }
            val updatedTags = if (hasTag) {
                currentTask.tags.filterNot { it.equals(cleanTargetTag, ignoreCase = true) }
            } else {
                val filteredTags = currentTask.tags.filterNot { existingTag ->
                    cleanExclusiveTags.any { ex -> existingTag.equals(ex, ignoreCase = true) }
                }
                filteredTags + cleanTargetTag
            }
            val updatedTask = currentTask.copy(
                tags = updatedTags.distinct(),
                isPersistentId = true
            )
            TodoTaskBuilder.rebuildTaskLine(updatedTask)
        }
    }
}