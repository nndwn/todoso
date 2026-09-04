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

      item(
        text = MyBundle.message("todo.menu.refresh"),
        icon = AllIcons.Actions.Refresh,
        shortcut = CommonShortcuts.getRerun(),
      ) {
        handler.refreshTasks()
      }

      item(MyBundle.message("todo.menu.random"), AllIcons.Actions.Lightning) {
        handler.handleRandomTask()
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
        val status = handler.getSelectedTask()?.status
        status == MyProjectService.TaskStatus.TODO ||
          status == MyProjectService.TaskStatus.DOING ||
          status == MyProjectService.TaskStatus.DONE
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
        val status = handler.getSelectedTask()?.status
        status == MyProjectService.TaskStatus.TODO || status == MyProjectService.TaskStatus.DOING
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
      item(
        text = MyBundle.message("todo.menu.filter.all"),
        iconProvider = { if (settings.state.priorityFilterName == null) AllIcons.Actions.Checked else null },
      ) {
        handler.setPriorityFilter(null)
      }
      separator()
      MyProjectService.Priority.entries
        .filter { it != MyProjectService.Priority.NONE }
        .forEach { priority ->
          item(
            text = priority.label,
            iconProvider = {
              if (settings.state.priorityFilterName == priority.name) AllIcons.Actions.Checked else null
            },
          ) {
            handler.setPriorityFilter(priority)
          }
        }
    }

    subMenu(MyBundle.message("todo.menu.filter.status"), AllIcons.Actions.Diff) {
      item(
        text = MyBundle.message("todo.menu.filter.all"),
        iconProvider = { if (settings.state.statusFilterName == null) AllIcons.Actions.Checked else null },
      ) {
        handler.setStatusFilter(null)
      }
      separator()
      MyProjectService.TaskStatus.entries.forEach { status ->
        item(
          text = status.name.toTitleCase(),
          iconProvider = { if (settings.state.statusFilterName == status.name) AllIcons.Actions.Checked else null },
        ) {
          handler.setStatusFilter(status)
        }
      }
    }

    toggle(MyBundle.message("todo.menu.visual.mode"), AllIcons.Actions.Show, { settings.state.visualEnabled }) {
      settings.state.visualEnabled = it
      handler.refreshTasks()
    }
  }
}
