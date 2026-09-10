package com.github.nndwn.todoso.listeners

import com.github.nndwn.todoso.services.TodosoDataChangeListener
import com.github.nndwn.todoso.services.TodosoService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

class TodosoVfsListener(private val project: Project) : BulkFileListener {

  override fun after(events: List<VFileEvent>) {
    val service = project.service<TodosoService>()
    val targetFile = service.getTodoFile() ?: return

    val isTargetFileContentChanged = events.any { event ->
      event is VFileContentChangeEvent && event.file.path == targetFile.path
    }

    if (isTargetFileContentChanged) {
      service.markCacheDirty()
      service.loadTask()
      project.messageBus.syncPublisher(TodosoDataChangeListener.TOPIC).onDataChanged()
    }
  }
}
