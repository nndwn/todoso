package com.github.nndwn.todoso.services

import com.github.nndwn.todoso.domain.model.TodoTask
import com.github.nndwn.todoso.domain.parser.TodoTaskParser
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Service(Service.Level.PROJECT)
class TodoService(private val project: Project) {

    fun getTodoFile(): VirtualFile? {
        val projectDir = project.guessProjectDir() ?: return null
        val path = TodosoSettingsService.getInstance(project).state.todoFilePath.trim()

        return if (path.isNotBlank()) {
            projectDir.findFileByRelativePath(path)
        } else {
            projectDir.children.find { it.name.equals("todo.md", ignoreCase = true) }
        }
    }

    fun loadTask(): List<TodoTask> {
        val todoFile = getTodoFile() ?: return emptyList()
        val content = runCatching { VfsUtil.loadText(todoFile) }.getOrNull()

        if (content.isNullOrBlank()) return emptyList()

        val usedIds = mutableSetOf<String>()
        return content.lines().mapIndexedNotNull { index, rawLine ->
            TodoTaskParser.parseLine(rawLine, index + 1, usedIds)
        }
    }
}