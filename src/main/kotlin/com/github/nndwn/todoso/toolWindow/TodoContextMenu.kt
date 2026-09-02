package com.github.nndwn.todoso.toolWindow

import com.github.nndwn.todoso.MyBundle
import com.github.nndwn.todoso.services.MyProjectService
import com.github.nndwn.todoso.services.MyProjectSettingsService
import com.github.nndwn.todoso.util.toTitleCase
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.CommonShortcuts

class TodoContextMenu(
  private val service: MyProjectService,
  private val settings: MyProjectSettingsService,
  private val handler: TodoActionHandler,
) {

  fun build(): List<TodoMenuElement> {
    return buildTodoMenu {
      editorTask()

      separator()

      item(MyBundle.message("todo.menu.refresh"), AllIcons.Actions.Refresh, CommonShortcuts.getRerun()) {
        handler.refreshTasks()
      }

      item(MyBundle.message("todo.menu.challenge"), AllIcons.Actions.Lightning) {
        handler.handleChallengeTask()
      }

      separator()

      buildViewSubMenu()
    }
  }

  private fun TodoMenuBuilder.editorTask() {
    subMenu(MyBundle.message("todo.menu.change.status"), AllIcons.Actions.Diff) {
      MyProjectService.TaskStatus.entries
        .filter { it != MyProjectService.TaskStatus.CANCELLED }
        .forEach { status ->
          item(
            text = status.name.toTitleCase(),
            isEnabled = { handler.canTransitionTo(handler.getSelectedTask(), status) },
          ) {
            handler.getSelectedTask()?.let { handler.updateTaskStatus(it, status) }
          }
        }
    }

    item(
      text = MyBundle.message("todo.menu.edit.task"),
      icon = AllIcons.Actions.EditSource,
      isEnabled = {
        val selected = handler.getSelectedTask()
        selected != null &&
          (selected.status == MyProjectService.TaskStatus.TODO || selected.status == MyProjectService.TaskStatus.DOING)
      },
    ) {
      handler.getSelectedTask()?.let {
        handler.handleCancelEdit()
        handler.setEditMode(true, it.description)
      }
    }

    subMenu(
      text = MyBundle.message("todo.menu.change.priority"),
      icon = AllIcons.General.ChevronUp,
      isEnabled = {
        val selected = handler.getSelectedTask()
        selected != null &&
          (selected.status == MyProjectService.TaskStatus.TODO || selected.status == MyProjectService.TaskStatus.DOING)
      },
    ) {
      MyProjectService.Priority.entries
        .filter { it != MyProjectService.Priority.NONE }
        .forEach { priority ->
          item(priority.label) {
            handler.getSelectedTask()?.let { handler.handleUpdatePriority(it, priority) }
          }
        }
    }

    item(MyBundle.message("todo.menu.delete"), AllIcons.General.Remove) { handler.handleDeleteAction() }
  }

  private fun TodoMenuBuilder.buildViewSubMenu() {
    subMenu(MyBundle.message("todo.menu.filter.priority"), AllIcons.General.Filter) {
      item(MyBundle.message("todo.menu.filter.all")) {}
      separator()
      MyProjectService.Priority.entries
        .filter { it != MyProjectService.Priority.NONE }
        .forEach { priority ->
          item(priority.label) {}
        }
    }
    toggle(MyBundle.message("todo.menu.visual.mode"), AllIcons.Actions.Show, { settings.state.visualEnabled }) {
      settings.state.visualEnabled = it
      handler.refreshTasks()
    }
  }
}
