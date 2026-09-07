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
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service(Service.Level.PROJECT)
class TodosoService(private val project: Project) {

    private val instructionHeader: String
        get() = TodosoBundle.message("todo.instruction.inject", TodosoConstants.GITHUB_REPO_URL)

    private val settings = TodosoSettingsService.getInstance(project)

    private fun sanitizeInputText(input: String): String {
        return input.replace("\r\n", " ")
            .replace("\n", " ")
            .replace("\r", " ")
            .replace(Regex("""[ \t]+"""), " ")
            .trim()
    }
    fun getTodoFile(): VirtualFile? {
        val projectDir = project.guessProjectDir() ?: return null
        val path = settings.state.todoFilePath.trim().ifBlank { TodosoConstants.FILENAME }
        return projectDir.findFileByRelativePath(path)
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

    private fun getOrCreateTodoFile(): VirtualFile?{
        getTodoFile()?.let { return it }
        val projectDir = project.guessProjectDir() ?: return null
        val filePath = settings.state.todoFilePath.trim().ifBlank { TodosoConstants.FILENAME }
        return try {
            projectDir.createChildData(this, filePath)
        } catch (_: Exception){
            null
        }
    }
    fun loadTask(): List<TodoTask> {

        val todoFile = getTodoFile() ?: return emptyList()

        val content = try {
            VfsUtil.loadText(todoFile)
        } catch (_: Exception) {
            return emptyList()
        }

        if (content.isBlank()) return emptyList()

        val usedIds = mutableSetOf<String>()
        val tasks = mutableListOf<TodoTask>()

        content.lines().forEachIndexed { index, rawLine ->
            val task = TodoTaskParser.parseLine(
                rawLine = rawLine,
                lineNumber = index + 1,
                usedIds = usedIds
            )
            if (task != null) {
                tasks.add(task)
            }
        }

        return tasks
    }

    fun getRecentVersions(limit: Int = 3): List<String> {
        val currentTasks = loadTask()
        return currentTasks
            .asSequence()
            .flatMap { it.tags }
            .filter { it.startsWith("v") || it.startsWith("#v") }
            .map { if (it.startsWith("#")) it else "#$it" }
            .distinct()
            .sortedDescending()
            .take(limit)
            .toList()
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
        // 1. Jalankan sanitasi teks input
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

            // 2. Bersihkan baris kosong di ekor file
            val existingLines = currentContent.lines()
                .map { it.trimEnd() }
                .dropLastWhile { it.isBlank() }

            // 3. Susun baris baru secara simetris
            val newLines = existingLines + formattedTaskLine
            val newContent = newLines.joinToString("\n") + "\n"

            VfsUtil.saveText(todoFile, newContent)
            VfsUtil.markDirtyAndRefresh(false, true, true, todoFile)
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

        WriteCommandAction.runWriteCommandAction(project, "Modify Todo Task", null, Runnable {
            val content = try {
                VfsUtil.loadText(todoFile)
            } catch (_: Exception) {
                return@Runnable
            }

            val lines = content.lines().toMutableList()
            val targetIndex = task.lineNumber - 1
            if (targetIndex !in lines.indices) return@Runnable

            val updatedLine = action(task)
            if (updatedLine == null) {
                lines.removeAt(targetIndex)
            } else {
                lines[targetIndex] = updatedLine.replace("\n", "").trimEnd()
            }

            val cleanedLines = lines.dropLastWhile { it.isBlank() }
            val newContent = if (cleanedLines.isNotEmpty()) {
                cleanedLines.joinToString("\n") + "\n"
            } else {
                ""
            }

            VfsUtil.saveText(todoFile, newContent)
            VfsUtil.markDirtyAndRefresh(false, true, true, todoFile)
        })
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