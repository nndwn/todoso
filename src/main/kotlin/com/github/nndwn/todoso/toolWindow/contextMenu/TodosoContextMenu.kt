package com.github.nndwn.todoso.toolWindow.contextMenu

import com.github.nndwn.todoso.TodosoBundle
import com.github.nndwn.todoso.TodosoConstants
import com.github.nndwn.todoso.domain.model.Priority
import com.github.nndwn.todoso.domain.model.TaskStatus
import com.github.nndwn.todoso.domain.model.TodoTask
import com.github.nndwn.todoso.domain.parser.TagParser
import com.github.nndwn.todoso.services.TodosoService
import com.github.nndwn.todoso.services.TodosoSettingsService
import com.github.nndwn.todoso.toolWindow.SortOption
import com.github.nndwn.todoso.toolWindow.TodosoActionHandler
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.CommonShortcuts

class TodosoContextMenu(
  private val service: TodosoService,
  private val settings: TodosoSettingsService,
  private val handler: TodosoActionHandler,
) {
  fun build(): List<TodosoMenuElement> {
    val selected = handler.getSelectedTask()

    return buildTodosoMenu {
      if (selected != null) {
        editorTask(selected)
        separator()
      }
      toolTask()
      separator()
      visualTask()
      separator()
      sortTask()
    }
  }

  private fun TodoMenuBuilder.editorTask(initialTask: TodoTask) {
    val taskId = initialTask.id

    item(
      text = TodosoBundle.message("todo.menu.edit.task"),
      icon = AllIcons.Actions.Edit,
      onAction = {
        service.findTaskById(taskId)?.let { handler.setEditMode(true, it.description) }
      },
    )

    item(
      text = TodosoBundle.message("todo.menu.add.note"),
      icon = AllIcons.Actions.EditSource,
      onAction = {
        service.findTaskById(taskId)?.let { handler.setNoteMode(true, it.metadata.notes) }
      },
    )

    subMenu(TodosoBundle.message("todo.menu.change.status"), AllIcons.Actions.Diff) {
      TaskStatus.entries.forEach { status ->
        item(
          text = status.displayName,
          icon = status.icon,
          isEnabled = {
            service.findTaskById(taskId)?.let { handler.canTransitionTo(it, status) } ?: false
          },
        ) {
          service.findTaskById(taskId)?.let { handler.updateTaskStatus(it, status) }
        }
      }
    }

    subMenu(TodosoBundle.message("todo.menu.change.priority"), AllIcons.General.ChevronUp) {
      Priority.entries.forEach { priority ->
        item(
          text = priority.displayName,
          icon = priority.icon,
          isEnabled = {
            service.findTaskById(taskId)?.priority != priority
          },
          onAction = {
            service.findTaskById(taskId)?.let { handler.handleUpdatePriority(it, priority) }
          },
        )
      }
    }

    subMenu(TodosoBundle.message("todo.menu.manage.tags"), AllIcons.Nodes.Tag) {
      val taskData = service.loadTask()
      val exclusiveRelations = TodosoConstants.EXCLUSIVE_RELATIONS
      val exclusiveTags = exclusiveRelations.flatten()

      // 1. Exclusive Tag Groups
      exclusiveRelations.forEach { group ->
        group.forEach { tag ->
          toggle(
            text = TagParser.formatTagWithCount(tag, group),
            isSelected = {
              service.findTaskById(taskId)?.tags?.contains(tag) ?: false
            },
            onToggle = {
              service.findTaskById(taskId)?.let { handler.handleToggleTag(it, tag) }
            },
          )
        }
        separator()
      }

      // 3. Recent Versions
      val recentVersions = TagParser.getRecentVersions(tasks = taskData)

      // 2. Popular Tags (Excluding Exclusives and Recent Versions)
      val popularTags = TagParser.getPopularTags(taskData)
        .filter { it !in exclusiveTags && it !in recentVersions }

      if (popularTags.isNotEmpty()) {
        subMenu(TodosoBundle.message("todo.suggestion.popular.tags")) {
          popularTags.forEach { tag ->
            toggle(
              text = TagParser.formatTagWithCount(tag, popularTags, isTruncated = true),
              isSelected = {
                service.findTaskById(taskId)?.tags?.contains(tag) ?: false
              },
              onToggle = {
                service.findTaskById(taskId)?.let { handler.handleToggleTag(it, tag) }
              },
            )
          }
        }
      }

      if (recentVersions.isNotEmpty()) {
        subMenu(TodosoBundle.message("todo.filter.group.versions")) {
          recentVersions.forEach { tag ->
            toggle(
              text = TagParser.formatTagWithCount(tag, recentVersions),
              isSelected = {
                service.findTaskById(taskId)?.tags?.contains(tag) ?: false
              },
              onToggle = {
                service.findTaskById(taskId)?.let { handler.handleToggleTag(it, tag) }
              },
            )
          }
        }
      }
    }

    separator()

    item(
      text = TodosoBundle.message("todo.menu.copy.context"),
      icon = AllIcons.Actions.Copy,
      shortcut = CommonShortcuts.getCopy(),
      onAction = {
        service.findTaskById(taskId)?.let { handler.handleCopyContext() }
      },
    )

    item(
      text = TodosoBundle.message("todo.menu.delete"),
      icon = AllIcons.Actions.GC,
      shortcut = CommonShortcuts.getDelete(),
      onAction = {
        service.findTaskById(taskId)?.let { handler.handleDeleteAction() }
      },
    )
  }

  private fun TodoMenuBuilder.toolTask() {
    item(
      text = TodosoBundle.message("todo.menu.refresh"),
      icon = AllIcons.Actions.Refresh,
      onAction = { handler.refreshTasks() },
    )
    item(
      text = TodosoBundle.message("todo.menu.random"),
      icon = AllIcons.Actions.Lightning,
      onAction = { handler.handleRandomTask() },
    )
    item(
      text = TodosoBundle.message("todo.menu.navigate.lane"),
      icon = AllIcons.Actions.ShowCode,
      onAction = { handler.getSelectedTask()?.let { handler.handleNavigateToTask(it) } },
    )
    item(
      text = TodosoBundle.message("todo.filter.search"),
      icon = AllIcons.Actions.Find,
      shortcut = CommonShortcuts.getFind(),
      onAction = { handler.handleToggleSearch() },
    )
  }

  private fun TodoMenuBuilder.visualTask() {
    toggle(
      text = TodosoBundle.message("todo.view.visual.mode"),
      icon = AllIcons.Actions.Show,
      isSelected = { settings.state.visualEnabled },
      onToggle = {
        settings.state.visualEnabled = it
        handler.refreshTasks()
      },
    )
  }

  private fun TodoMenuBuilder.sortTask() {
    subMenu(TodosoBundle.message("todo.common.sort"), AllIcons.Actions.GroupBy) {
      item(
        text = TodosoBundle.message("todo.common.default"),
        onAction = {
          settings.state.sortOption = ""
          handler.refreshTasks()
        },
      )
      separator()
      SortOption.entries.forEach { option ->
        val label =
          when (option) {
            SortOption.PRIORITY -> TodosoBundle.message("todo.sort.by.priority")
            SortOption.STATUS -> TodosoBundle.message("todo.sort.by.status")
            SortOption.DATE -> TodosoBundle.message("todo.sort.by.date")
          }
        toggle(
          text = label,
          isSelected = { settings.state.sortOption.split(",").contains(option.key) },
          onToggle = { active ->
            val current = settings.state.sortOption.split(",").filter { it.isNotBlank() }.toMutableList()
            if (active) current.add(option.key) else current.remove(option.key)
            settings.state.sortOption = current.distinct().joinToString(",")
            handler.refreshTasks()
          },
        )
      }
    }
  }
}
