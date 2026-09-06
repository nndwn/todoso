package com.github.nndwn.todoso.listeners

import com.github.nndwn.todoso.services.TodoService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

class TodosoVfsListener(private val project: Project) : BulkFileListener {

    override fun after(events: List<VFileEvent>) {
        val service = project.service<TodoService>()
        val targetFile = service.getTodoFile() ?: return

        // Only trigger reload if the content of our specific todo file actually changed
        val isTargetFileContentChanged = events.any { event ->
            event is VFileContentChangeEvent && event.file.path == targetFile.path
        }

        if (isTargetFileContentChanged) {
            // Reload tasks in background thread
            service.loadTask()
        }
    }
}