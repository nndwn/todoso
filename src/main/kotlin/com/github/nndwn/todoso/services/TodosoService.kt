package com.github.nndwn.todoso.services

import com.github.nndwn.todoso.TodosoBundle
import com.github.nndwn.todoso.TodosoConstants
import com.github.nndwn.todoso.domain.model.Priority
import com.github.nndwn.todoso.domain.model.TaskStatus
import com.github.nndwn.todoso.domain.model.TodoTask
import com.github.nndwn.todoso.domain.model.TodoTaskBuilder
import com.github.nndwn.todoso.domain.parser.TaskIdParser
import com.github.nndwn.todoso.domain.parser.TodoTaskParser
import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

@Service(Service.Level.PROJECT)
class TodoService(private val project: Project) {

    private val instructionHeader: String
        get() = TodosoBundle.message("todo.instruction.inject", TodosoConstants.GITHUB_REPO_URL)

    private val settings = TodosoSettingsService.getInstance(project)
    fun getTodoFile(): VirtualFile? {
        val projectDir = project.guessProjectDir() ?: return null
        val path = TodosoSettingsService.getInstance(project).state.todoFilePath.trim()

        return if (path.isNotBlank()) {
            projectDir.findFileByRelativePath(path)
        } else {
            projectDir.children.find { it.name.equals(TodosoConstants.FILENAME, ignoreCase = true) }
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
        val trimmedInput = rawInputText.trim()
        if (trimmedInput.isBlank()) return
        val formattedTaskLine = formatNewTaskLine(rawInputText)
        runWriteCommandAction(project, "Add Task", null, Runnable {
            val todoFile = getOrCreateTodoFile() ?: return@Runnable
            val currentContent = try {
                VfsUtil.loadText(todoFile)
            } catch (_: Exception) {
                ""
            }

            val newContent = if (currentContent.isEmpty() || currentContent.endsWith("\n")){
                currentContent + formattedTaskLine
            } else {
                "$currentContent\n$formattedTaskLine"
            }
            VfsUtil.saveText(todoFile, newContent)
            VfsUtil.markDirtyAndRefresh(false, true, true, todoFile)
        })
    }
    private fun formatNewTaskLine(input: String): String {
        val dummyLine = if (input.startsWith("- [")) {
            input
        } else {
            "- [ ] $input"
        }

        val parsedTask = TodoTaskParser.parseLine(
            rawLine = dummyLine,
            lineNumber = 1
        )

        return if (parsedTask != null) {
            val newTask = parsedTask.copy(isPersistentId = true)
            TodoTaskBuilder.rebuildTaskLine(newTask)
        } else {
            val generatedId = TaskIdParser.parseId(null).id
            "- [ ] $input 🆔 $generatedId"
        }
    }
    fun editTask(task: TodoTask, rawInputText: String) {
        val trimmedInput = rawInputText.trim()
        if (trimmedInput.isBlank()) return

        modifyTaskLine(task) { currentTask ->
            val hasExplicitStatus = trimmedInput.startsWith("- [")
            val hasExplicitPriority = Priority.parseFromLine("- [ ] $trimmedInput") != Priority.NONE

            val dummyLine = if (hasExplicitStatus) {
                trimmedInput
            } else {
                "- [${currentTask.status.code}] $trimmedInput"
            }

            val parsedTask = TodoTaskParser.parseLine(
                rawLine = dummyLine,
                lineNumber = currentTask.lineNumber
            )

            val updatedTask = if (parsedTask != null) {
                if (hasExplicitPriority || hasExplicitStatus) {
                    currentTask.copy(
                        priority = parsedTask.priority,
                        description = parsedTask.description,
                        tags = parsedTask.tags,
                        metadata = if (parsedTask.metadata.notes.isNotBlank()) {
                            currentTask.metadata.copy(notes = parsedTask.metadata.notes)
                        } else {
                            currentTask.metadata
                        },
                        isPersistentId = true
                    )
                } else {
                    currentTask.copy(
                        description = parsedTask.description,
                        tags = parsedTask.tags,
                        metadata = if (parsedTask.metadata.notes.isNotBlank()) {
                            currentTask.metadata.copy(notes = parsedTask.metadata.notes)
                        } else {
                            currentTask.metadata
                        },
                        isPersistentId = true
                    )
                }
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
            val targetIndex = task.lineNumber - 1
            if (targetIndex !in lines.indices) return@Runnable
            val updatedLine = action(task)
            if (updatedLine == null) {
                lines.removeAt(targetIndex)
            } else {
                lines[targetIndex] = updatedLine
            }

            val newContent = lines.joinToString("\n")
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