package com.github.nndwn.todoso.toolWindow

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
      buildStatusSubMenu()
      buildEditSubMenu()
      buildPrioritySubMenu()

      separator()

      item("Refresh", AllIcons.Actions.Refresh, CommonShortcuts.getRerun()) {
        handler.refreshTasks()
      }

      buildTagsSubMenu()

      item("Challenge Task", AllIcons.Actions.Lightning) {}

      separator()

      buildViewSubMenu()
    }
  }

  private fun TodoMenuBuilder.buildStatusSubMenu() {
    subMenu("Status", AllIcons.Actions.Diff) {
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
  }

  private fun TodoMenuBuilder.buildEditSubMenu() {
    subMenu("Edit", AllIcons.Actions.Edit) {
      item(
        text = "Edit Text",
        icon = AllIcons.Actions.EditSource,
        isEnabled = {
          val selected = handler.getSelectedTask()
          selected != null &&
            (selected.status == MyProjectService.TaskStatus.TODO ||
              selected.status == MyProjectService.TaskStatus.DOING)
        },
      ) {
        handler.getSelectedTask()?.let {
          handler.handleCancelEdit()
          handler.setEditMode(true, it.description)
        }
      }
      item("Delete", AllIcons.General.Remove) { handler.handleDeleteAction() }
    }
  }

  private fun TodoMenuBuilder.buildPrioritySubMenu() {
    subMenu("Priority", AllIcons.General.Filter) {
      MyProjectService.Priority.entries
        .filter { it != MyProjectService.Priority.NONE }
        .forEach { priority ->
          item(priority.label) {}
        }
    }
  }

  private fun TodoMenuBuilder.buildTagsSubMenu() {
    subMenu("Tags", AllIcons.Nodes.Tag) {
      item("Add Tag", AllIcons.General.Add) {}
      item("Remove Tag") {}
    }
  }

  private fun TodoMenuBuilder.buildViewSubMenu() {
    subMenu("Filter Priority", AllIcons.General.Filter) {
      item("Show All") {}
      separator()
      MyProjectService.Priority.entries
        .filter { it != MyProjectService.Priority.NONE }
        .forEach { priority ->
          item(priority.label) {}
        }
    }
    toggle("Visual Mode", AllIcons.Actions.Show, { settings.state.visualEnabled }) {
      settings.state.visualEnabled = it
      handler.refreshTasks()
    }
  }
}
