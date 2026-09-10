package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.TodosoBundle
import com.github.nndwn.todoso.TodosoConstants
import com.github.nndwn.todoso.domain.model.Priority
import com.github.nndwn.todoso.domain.model.TaskStatus
import com.github.nndwn.todoso.domain.model.TodoTask
import com.github.nndwn.todoso.services.TodosoService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.awt.datatransfer.StringSelection

class TodosoActionHandler(
  private val project: Project,
  private val service: TodosoService,
  private val view: TodoViewActions,
) {
  interface TodoViewActions {
    fun refreshTasks()

    fun setEditMode(enabled: Boolean, text: String = "")

    fun getSelectedTask(): TodoTask?

    fun updateButtonStates()

    fun setPriorityFilter(priority: Priority?)

    fun setStatusFilter(status: TaskStatus?)

    fun setTagFilter(tag: String?)

    fun setCancelMode(enabled: Boolean)

    fun getInputText(): String

    fun clearInputText()
  }

  private var pendingCancelTask: TodoTask? = null

  fun getSelectedTask() = view.getSelectedTask()

  fun setEditMode(enabled: Boolean, text: String = "") = view.setEditMode(enabled, text)

  fun refreshTasks() = view.refreshTasks()

  fun setPriorityFilter(priority: Priority?) = view.setPriorityFilter(priority)

  fun setStatusFilter(status: TaskStatus?) = view.setStatusFilter(status)

  fun handleAddTask(text: String) {
    service.addTask(text.trim())
    view.setEditMode(false)
    ApplicationManager.getApplication().invokeLater { view.refreshTasks() }
  }

  fun handleUpdateTask(text: String) {
    val selected = view.getSelectedTask() ?: return
    service.editTask(selected, text.trim())
    view.setEditMode(false)
    view.updateButtonStates()
    ApplicationManager.getApplication().invokeLater { view.refreshTasks() }
  }

  fun handleCancelEdit() {
    view.setEditMode(false)
    view.setCancelMode(false)
    pendingCancelTask = null
    view.updateButtonStates()
  }

  fun handleDeleteAction() {
    val selected = view.getSelectedTask() ?: return
    val result =
      Messages.showYesNoDialog(
        project,
        TodosoBundle.message("todo.action.delete.confirm.message"),
        TodosoBundle.message("todo.action.delete.confirm.title"),
        Messages.getQuestionIcon(),
      )
    if (result == Messages.YES) {
      service.deleteTask(selected)
      ApplicationManager.getApplication().invokeLater { view.refreshTasks() }
    }
  }

  fun handleErrorNotification(message: String) {
    NotificationGroupManager.getInstance()
      .getNotificationGroup("com.github.nndwn.todoso.notifications")
      .createNotification(
        TodosoBundle.message("todo.action.error"),
        message,
        NotificationType.WARNING,
      )
      .notify(project)
  }

  fun handleCopyContext() {
    val selected = view.getSelectedTask() ?: return
    CopyPasteManager.getInstance().setContents(StringSelection(selected.rawText))
  }

  fun updateTaskStatus(task: TodoTask, status: TaskStatus) {
    if (status == TaskStatus.CANCELLED) {
      val noted = view.getInputText().trim()

      if (noted.isEmpty()) {
        pendingCancelTask = task
        view.setCancelMode(true)
        return
      }
      service.updateTaskStatus(task, status, noted)
      view.clearInputText()
    } else {
      service.updateTaskStatus(task, status)
    }
    ApplicationManager.getApplication().invokeLater { view.refreshTasks() }
  }

  fun handleConfirmCancel(note: String) {
    val task = pendingCancelTask ?: return
    service.updateTaskStatus(task, TaskStatus.CANCELLED, note.trim())
    pendingCancelTask = null
    view.setCancelMode(false)
    view.clearInputText()
    ApplicationManager.getApplication().invokeLater { view.refreshTasks() }
  }

  fun handleUpdatePriority(task: TodoTask, priority: Priority) {
    service.updateTaskPriority(task, priority)
    ApplicationManager.getApplication().invokeLater { view.refreshTasks() }
  }

  fun handleToggleTag(task: TodoTask, tag: String, exclusiveWith: List<String> = emptyList()) {
    service.applyTaskTag(task, tag, exclusiveWith)
    ApplicationManager.getApplication().invokeLater { view.refreshTasks() }
  }

  fun handleRandomTask() {
    val tasks = service.loadTask()
    val todoTasks = tasks.filter { it.status == TaskStatus.TODO }

    if (todoTasks.isEmpty()) {
      NotificationGroupManager.getInstance()
        .getNotificationGroup("com.github.nndwn.todoso.notifications")
        .createNotification(
          TodosoConstants.PLUGIN_NAME,
          TodosoBundle.message("todo.action.random.no_tasks"),
          NotificationType.INFORMATION,
        )
        .notify(project)
    }

    val randomTask = todoTasks.random()
    service.updateTaskStatus(randomTask, TaskStatus.DOING)
    ApplicationManager.getApplication().invokeLater { view.refreshTasks() }
  }

  fun canTransitionTo(task: TodoTask?, newStatus: TaskStatus): Boolean {
    if (task == null) return false
    if (task.status == newStatus) return false
    return when (newStatus) {
      TaskStatus.DONE -> task.status == TaskStatus.DOING
      TaskStatus.CANCELLED -> task.status != TaskStatus.DONE
      else -> true
    }
  }
}
