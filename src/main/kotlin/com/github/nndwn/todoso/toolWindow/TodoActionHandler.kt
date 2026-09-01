package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.services.MyProjectService
import com.github.nndwn.todoso.services.MyProjectSettingsService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

class TodoActionHandler(
  private val project: Project,
  private val service: MyProjectService,
  private val settings: MyProjectSettingsService,
  private val view: TodoViewActions,
) {

  interface TodoViewActions {
    fun refreshTasks()

    fun setEditMode(enabled: Boolean, text: String = "")

    fun getSelectedTask(): MyProjectService.TodoTask?

    fun updateButtonStates()
  }

  fun getSelectedTask() = view.getSelectedTask()

  fun setEditMode(enabled: Boolean, text: String = "") = view.setEditMode(enabled, text)

  fun refreshTasks() = view.refreshTasks()

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
    view.updateButtonStates()
  }

  fun handleDeleteAction() {
    val selected = view.getSelectedTask() ?: return
    val result =
      Messages.showYesNoDialog(
        project,
        "Are you sure you want to delete this task?",
        "Delete Task",
        Messages.getQuestionIcon(),
      )
    if (result == Messages.YES) {
      service.deleteTask(selected)
      ApplicationManager.getApplication().invokeLater { view.refreshTasks() }
    }
  }

  fun handleCancelAction(reason: String) {
    val task = view.getSelectedTask() ?: return
    val trimmedReason = reason.trim()

    if (trimmedReason.isEmpty()) {
      NotificationGroupManager.getInstance()
        .getNotificationGroup("Todoso Notifications")
        .createNotification("Cancelled task must have a reason", NotificationType.WARNING)
        .notify(project)
      return
    }

    service.updateTaskStatus(task, MyProjectService.TaskStatus.CANCELLED, trimmedReason)
    view.setEditMode(false)
    ApplicationManager.getApplication().invokeLater { view.refreshTasks() }
  }

  fun updateTaskStatus(task: MyProjectService.TodoTask, status: MyProjectService.TaskStatus) {
    service.updateTaskStatus(task, status)
    ApplicationManager.getApplication().invokeLater { view.refreshTasks() }
  }

  fun canTransitionTo(task: MyProjectService.TodoTask?, newStatus: MyProjectService.TaskStatus): Boolean {
    if (task == null) return false
    if (task.status == newStatus) return false
    return when (newStatus) {
      MyProjectService.TaskStatus.DONE -> task.status == MyProjectService.TaskStatus.DOING
      MyProjectService.TaskStatus.CANCELLED -> task.status != MyProjectService.TaskStatus.DONE
      else -> true
    }
  }
}
