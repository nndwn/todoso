package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.MyBundle
import com.github.nndwn.todoso.services.MyProjectService
import com.github.nndwn.todoso.services.MyProjectSettingsService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.awt.datatransfer.StringSelection

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

    fun setPriorityFilter(priority: MyProjectService.Priority?)

    fun setStatusFilter(status: MyProjectService.TaskStatus?)

    fun setTagFilter(tag: String?)
  }

  fun getSelectedTask() = view.getSelectedTask()

  fun setEditMode(enabled: Boolean, text: String = "") = view.setEditMode(enabled, text)

  fun refreshTasks() = view.refreshTasks()

  fun setPriorityFilter(priority: MyProjectService.Priority?) = view.setPriorityFilter(priority)

  fun setStatusFilter(status: MyProjectService.TaskStatus?) = view.setStatusFilter(status)

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
        MyBundle.message("todo.action.delete.confirm.message"),
        MyBundle.message("todo.action.delete.confirm.title"),
        Messages.getQuestionIcon(),
      )
    if (result == Messages.YES) {
      service.deleteTask(selected)
      ApplicationManager.getApplication().invokeLater { view.refreshTasks() }
    }
  }

  fun handleCopyContext() {
    val selected = view.getSelectedTask() ?: return
    CopyPasteManager.getInstance().setContents(StringSelection(selected.rawText))
  }

  fun handleCancelAction(noted: String) {
    val task = view.getSelectedTask() ?: return
    val trimmedNoted = noted.trim()

    if (trimmedNoted.isEmpty()) {
      NotificationGroupManager.getInstance()
        .getNotificationGroup("com.github.nndwn.todoso.notifications")
        .createNotification(
          MyBundle.message("plugin.name"),
          MyBundle.message("todo.action.cancel.noted.required"),
          NotificationType.WARNING,
        )
        .notify(project)
      return
    }

    service.updateTaskStatus(task, MyProjectService.TaskStatus.CANCELLED, trimmedNoted)
    view.setEditMode(false)
    ApplicationManager.getApplication().invokeLater { view.refreshTasks() }
  }

  fun updateTaskStatus(task: MyProjectService.TodoTask, status: MyProjectService.TaskStatus) {
    service.updateTaskStatus(task, status)
    ApplicationManager.getApplication().invokeLater { view.refreshTasks() }
  }

  fun handleUpdatePriority(task: MyProjectService.TodoTask, priority: MyProjectService.Priority) {
    service.updateTaskPriority(task, priority)
    ApplicationManager.getApplication().invokeLater { view.refreshTasks() }
  }

  fun handleToggleTag(task: MyProjectService.TodoTask, tag: String, exclusiveWith: List<String> = emptyList()) {
    service.toggleTaskTag(task, tag, exclusiveWith)
    ApplicationManager.getApplication().invokeLater { view.refreshTasks() }
  }

  fun handleRandomTask() {
    val todoTasks = service.getTodoTasks().filter { it.status == MyProjectService.TaskStatus.TODO }
    if (todoTasks.isEmpty()) {
      NotificationGroupManager.getInstance()
        .getNotificationGroup("com.github.nndwn.todoso.notifications")
        .createNotification(
          MyBundle.message("plugin.name"),
          MyBundle.message("todo.action.random.no_tasks"),
          NotificationType.INFORMATION,
        )
        .notify(project)
      return
    }
    val randomTask = todoTasks.random()
    service.updateTaskStatus(randomTask, MyProjectService.TaskStatus.DOING)
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
