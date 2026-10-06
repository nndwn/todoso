package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.TodosoBundle
import com.github.nndwn.todoso.TodosoConstants
import com.github.nndwn.todoso.domain.model.Priority
import com.github.nndwn.todoso.domain.model.TaskStatus
import com.github.nndwn.todoso.domain.model.TodoTask
import com.github.nndwn.todoso.services.TodosoService
import com.github.nndwn.todoso.toolWindow.contextMenu.TodosoBookmarkHelper
import com.github.nndwn.todoso.toolWindow.inputWindow.InputMode
import com.intellij.ide.bookmark.Bookmark
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
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

    fun refreshUiState()

    fun getSortOption(): Set<SortOption>

    fun setSortOption(options: Set<SortOption>)

    fun setEditMode(enabled: Boolean, text: String = "")

    fun getSelectedTask(): TodoTask?

    fun updateButtonStates()

    fun setPriorityFilter(priority: Priority?)

    fun setStatusFilter(status: TaskStatus?)

    fun setDateFilter(filter: String?)

    fun setTagFilter(tag: String?)

    fun setCancelMode(enabled: Boolean)

    fun setNoteMode(enabled: Boolean, text: String = "")

    fun getCurrentMode(): InputMode

    fun getInputText(): String

    fun setInputText(text: String)

    fun clearInputText()

    fun requestFocusToInput()

    fun setSelectedTask(task: TodoTask?)

    fun toggleSearch()
  }

  private var pendingCancelTask: TodoTask? = null
  private var savedDraftTask: String? = null

  fun getSelectedTask() = view.getSelectedTask()

  fun setEditMode(enabled: Boolean, text: String = "") {
    if (enabled && getCurrentMode() is InputMode.Normal) {
      savedDraftTask = view.getInputText()
    }
    view.setEditMode(enabled, text)
  }

  fun setNoteMode(enabled: Boolean, text: String = "") {
    if (enabled && getCurrentMode() is InputMode.Normal) {
      savedDraftTask = view.getInputText()
    }
    view.setNoteMode(enabled, text)
  }

  fun getCurrentMode() = view.getCurrentMode()

  fun refreshTasks() = view.refreshTasks()

  fun refreshUiState() = view.refreshUiState()

  fun getSortOption(): Set<SortOption> = view.getSortOption()

  fun setSortOption(options: Set<SortOption>) = view.setSortOption(options)

  fun setPriorityFilter(priority: Priority?) = view.setPriorityFilter(priority)

  fun setStatusFilter(status: TaskStatus?) = view.setStatusFilter(status)

  fun handleAddTask(text: String) {
    val newTask = service.addTask(text.trim())
    view.setEditMode(false)
    ApplicationManager.getApplication().invokeLater {
      view.setSelectedTask(newTask)
      view.refreshTasks()
    }
  }

  fun handleUpdateTask(text: String) {
    val selected = view.getSelectedTask() ?: return
    service.editTask(selected, text.trim())
    view.setEditMode(false)
    restoreSavedDraft()
    view.updateButtonStates()
    ApplicationManager.getApplication().invokeLater { view.refreshTasks() }
  }

  fun handleUpdateNote(note: String) {
    val selected = view.getSelectedTask() ?: return
    service.updateTaskNote(selected, note)
    view.setNoteMode(false)
    restoreSavedDraft()
    view.updateButtonStates()
    ApplicationManager.getApplication().invokeLater { view.refreshTasks() }
  }

  fun handleCancelEdit() {
    view.setEditMode(false)
    view.setCancelMode(false)
    view.setNoteMode(false)
    pendingCancelTask = null
    restoreSavedDraft()
    view.updateButtonStates()
  }

  private fun restoreSavedDraft() {
    val draft = savedDraftTask
    savedDraftTask = null
    if (!draft.isNullOrEmpty()) {
      view.setInputText(draft)
    } else {
      view.clearInputText()
    }
  }

  fun handleDeleteAction() {
    val selected = view.getSelectedTask() ?: return
    val desc = selected.id
    val result =
      Messages.showYesNoDialog(
        project,
        TodosoBundle.message("todo.action.delete.confirm.message", desc),
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

  fun handleAddToChangelog() {
    val selected = view.getSelectedTask() ?: return
    val success = service.addToChangelog(selected)
    if (success) {
      NotificationGroupManager.getInstance()
        .getNotificationGroup("com.github.nndwn.todoso.notifications")
        .createNotification(
          TodosoConstants.PLUGIN_NAME,
          TodosoBundle.message("todo.action.add_to_changelog.success"),
          NotificationType.INFORMATION,
        )
        .notify(project)
    } else {
      handleErrorNotification(TodosoBundle.message("todo.action.add_to_changelog.error"))
    }
  }

  fun updateTaskStatus(task: TodoTask, status: TaskStatus) {
    if (status == TaskStatus.CANCELLED) {
      if (getCurrentMode() is InputMode.Normal) {
        savedDraftTask = view.getInputText()
      }
      pendingCancelTask = task
      view.setCancelMode(true)
      return
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
    restoreSavedDraft()
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
      return
    }

    val randomTask = todoTasks.random()
    service.updateTaskStatus(randomTask, TaskStatus.DOING)
    ApplicationManager.getApplication().invokeLater { view.refreshTasks() }
  }

  fun handleNavigateToTask(task: TodoTask) {
    val file = service.getTodoFile() ?: return
    val descriptor = OpenFileDescriptor(project, file, task.lineNumber - 1, 0)
    if (descriptor.canNavigate()) {
      descriptor.navigate(true)
    }
  }

  fun handleToggleSearch() = view.toggleSearch()

  fun handleAttachBookmark(task: TodoTask, bookmark: Bookmark) {
    TodosoBookmarkHelper.attachBookmark(task, bookmark, project, service)
    ApplicationManager.getApplication().invokeLater { view.refreshTasks() }
  }

  fun handleGoToBookmark(task: TodoTask) {
    val success = TodosoBookmarkHelper.navigateToBookmark(task, project, service)
    if (!success) {
      handleErrorNotification(TodosoBundle.message("todo.menu.bookmark.not_found"))
    }
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
